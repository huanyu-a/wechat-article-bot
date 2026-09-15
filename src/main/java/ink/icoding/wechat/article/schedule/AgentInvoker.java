package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentResultHandler;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.entity.MemoryMultipartFile;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * agent4j 会话运行器（skills-agent-plan 5.5 / 8.1）：执行一次 AgentClient 会话，
 * 收集工具调用次数与执行日志（可带阶段前缀），供各执行策略复用。
 *
 * <p>阶段超时护栏：agent4j 的 SSE 通道在「流被中途掐断但未回调 onFailure/onClosed」时不会完成
 * Future，调用方会永久阻塞——定时任务因此长期停在 RUNNING，后续手工/定时触发都被
 * 「任务正在执行，请勿重复启动」挡住。这里给每个阶段加硬超时，超时即让任务以明确错误失败。
 *
 * <p>有界重试（2026-09-11 网关并发事故）：上游网关对并发配额有硬上限，打满时要么返回
 * {@code HTTP 429 concurrent limit exceeded}，要么整段不响应直到超时。两类都属于**外部瞬时原因**，
 * 因此这里做有界重试——但只在**本次尝试零工具调用**时重试：一旦已经调用过工具，
 * 就可能已经产生付费副作用（生图 / 委托子会话 / 草稿落库），重跑会重复计费与重复写入，
 * 此时宁可让运行失败，也不重试。
 *
 * <p>重试必须**重建会话**：失败的那次会话在 agent4j 里无法取消（无 cancel/close），
 * 复用它只会再次读到同一个已死的 Future；{@code agent.createSession()} 会新建 EventSource 与请求。
 *
 * <p>重试范围（2026-09-15 扩围）：原先只认 429 与阶段停滞，于是「连接被重置」「HTTP 5xx」
 * 「流中断」这些同样瞬时、且往往在**零工具调用**时就返回的错误一次都不重试——实测 run#8/#9/#39
 * 分别在 0/6/5 秒就失败，完全满足「无付费副作用」的重试前提却被直接判死。
 * 现在按「瞬时 / 永久」两分：永久类（鉴权、审查、模型未配置、余额不足）先被排除，
 * 其余瞬时类与 429 共用同一个退避预算（见 {@link #isPermanent} / {@link #isTransient}）。
 *
 * <p>停滞的重试次数**有意不与瞬时类同步放大**：一次停滞要白等一整个会话超时
 * （SINGLE 900s 下两次就是半小时），而模型报错通常在数秒内返回，两者代价差两个数量级。
 */
@Component
public class AgentInvoker extends AgentRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentInvoker.class);

    /**
     * 瞬时上游错误的退避重试次数与间隔：共 6 次尝试（1 + 5 次重试），退避合计 31 秒。
     *
     * <p>由 3 提到 5 的依据：429 与「连接重置 / 5xx / 流中断」合并进同一预算后，覆盖的是
     * 上游抖动与自身网络抖动叠加的场景，3 次在实测里不够用（#106 是 HTTP 500、
     * #107 是 HTTP 503，都发生在 30~40 次工具调用之后，重试成本远低于整轮重跑）。
     * 上限仍由「零工具调用」前提守住：重试次数再多也不会重复产生付费副作用。
     */
    static final int MAX_TRANSIENT_RETRIES = 5;
    static final long[] TRANSIENT_BACKOFF_MILLIS = {1_000L, 2_000L, 4_000L, 8_000L, 16_000L};
    /** 阶段停滞的重试次数：只重试 1 次（停滞多为上游瞬时，且每次要白等一个完整超时）。 */
    static final int MAX_STALL_RETRIES = 1;
    /** 退避间隔配置的默认值（逗号分隔毫秒）。 */
    static final String DEFAULT_BACKOFF_MILLIS = "1000,2000,4000,8000,16000";
    /**
     * 无进展检测的默认阈值（秒）：距上次有效事件超过该秒数即判停滞，不再白等硬超时。
     *
     * <p>180 的依据：正常会话的事件间隔是「一次模型响应」或「一次工具调用」的量级，
     * 实测正常的单阶段会话在分钟级完成、事件密集；而卡死的会话是**零事件**。
     * 180 秒足够长到不会误杀「正在生成长回复」的模型，又短到能把 SINGLE 的一次卡死
     * 从白等 900 秒降到 180 秒。
     *
     * <p><b>前提是「有效事件」覆盖了模型的所有输出通道</b>：本阈值曾误杀过正在正常出字的会话
     * （run#123/#124），原因是只把正文（{@code onMessage}）算作进展，而推理型模型的思维链走的是
     * 另一个回调（{@code onThink}）——实测 269 秒的生成里思维链增量 3322 条、正文仅 195 条，
     * 按「只算正文」计的空档达 254 秒。修复见 {@code attempt} 里的 {@code onThink} 实现：
     * **任何来自上游的数据都算进展**，判据才是「上游还活着吗」而不是「吐正文了吗」。
     */
    static final long DEFAULT_INACTIVITY_SECONDS = 180L;

    private final long stageTimeoutSeconds;
    private final int maxTransientRetries;
    private final long[] transientBackoffMillis;
    private final int maxStallRetries;
    private final long inactivitySeconds;

    /** 单测构造：沿用默认重试策略（次数与退避均为常量）。 */
    public AgentInvoker(long stageTimeoutSeconds) {
        this(stageTimeoutSeconds, MAX_TRANSIENT_RETRIES, DEFAULT_BACKOFF_MILLIS, MAX_STALL_RETRIES,
                DEFAULT_INACTIVITY_SECONDS);
    }

    /** 单测构造：自定次数/退避/停滞上限，无进展检测取默认值。 */
    public AgentInvoker(long stageTimeoutSeconds, int transientMax, String backoffMillis, int stallMax) {
        this(stageTimeoutSeconds, transientMax, backoffMillis, stallMax, DEFAULT_INACTIVITY_SECONDS);
    }

    /**
     * @param transientMax   瞬时错误的最大重试次数（&le;0 表示不重试瞬时错误）
     * @param backoffMillis  退避间隔（逗号分隔毫秒）；长度不足时按最后一项补齐
     * @param stallMax       阶段停滞的最大重试次数
     * @param inactivitySeconds 无进展检测阈值（秒，&le;0 表示关闭；硬超时始终生效）
     */
    @org.springframework.beans.factory.annotation.Autowired
    public AgentInvoker(@Value("${app.schedule.stage-timeout-seconds:300}") long stageTimeoutSeconds,
                        @Value("${app.schedule.retry.transient-max:" + MAX_TRANSIENT_RETRIES + "}")
                        int transientMax,
                        @Value("${app.schedule.retry.backoff-millis:" + DEFAULT_BACKOFF_MILLIS + "}")
                        String backoffMillis,
                        @Value("${app.schedule.retry.stall-max:" + MAX_STALL_RETRIES + "}") int stallMax,
                        @Value("${app.schedule.retry.inactivity-seconds:" + DEFAULT_INACTIVITY_SECONDS + "}")
                        long inactivitySeconds) {
        this.stageTimeoutSeconds = stageTimeoutSeconds;
        this.maxTransientRetries = Math.max(0, transientMax);
        this.transientBackoffMillis = parseBackoff(backoffMillis);
        this.maxStallRetries = Math.max(0, stallMax);
        this.inactivitySeconds = inactivitySeconds;
    }

    /**
     * 解析退避间隔。配置写错时回落到默认值而不是抛错——护栏配置错误不该让应用起不来，
     * 但也不能静默变成「零退避」（那会把重试变成对上游的连续冲击）。
     */
    static long[] parseBackoff(String configured) {
        if (configured == null || configured.isBlank()) return TRANSIENT_BACKOFF_MILLIS;
        String[] parts = configured.split(",");
        long[] parsed = new long[parts.length];
        try {
            for (int index = 0; index < parts.length; index++) {
                parsed[index] = Long.parseLong(parts[index].trim());
                if (parsed[index] < 0) return TRANSIENT_BACKOFF_MILLIS;
            }
        } catch (NumberFormatException malformed) {
            log.warn("app.schedule.retry.backoff-millis 配置无法解析（{}），回落到默认值", configured);
            return TRANSIENT_BACKOFF_MILLIS;
        }
        return parsed.length == 0 ? TRANSIENT_BACKOFF_MILLIS : parsed;
    }

    /** 第 n 次重试（从 0 计）的退避间隔；配置比次数短时沿用最后一项。 */
    private long backoffFor(int retryIndex) {
        int last = transientBackoffMillis.length - 1;
        return transientBackoffMillis[Math.min(retryIndex, last)];
    }

    @Override
    public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments) {
        return run(agent, command, attachments, null);
    }

    /** @param logPrefix 执行日志前缀（如「【调研】」），null 表示不加前缀。 */
    @Override
    public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments, String logPrefix) {
        return run(agent, command, attachments, logPrefix, 0, 0, null);
    }

    /** @param maxToolCalls 工具调用上限（&le;0 表示不限制）；超限抛异常中止会话（预算护栏）。 */
    @Override
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls) {
        return run(agent, command, attachments, logPrefix, maxToolCalls, 0, null);
    }

    /**
     * @param progress 进度回调：工具计数与日志行**实时**上报，失败路径因此也留得住（见 AgentRunner.ProgressListener）。
     */
    @Override
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls,
                                ProgressListener progress) {
        return run(agent, command, attachments, logPrefix, maxToolCalls, 0, progress);
    }

    /**
     * @param timeoutSeconds 会话硬超时覆盖（&le;0 表示沿用 {@code app.schedule.stage-timeout-seconds}）；
     *                       协调者主编的一次会话覆盖全部委托，需要比单阶段会话长得多的额度。
     */
    @Override
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls, long timeoutSeconds,
                                ProgressListener progress) {
        return run(agent, command, attachments, logPrefix, maxToolCalls, timeoutSeconds, progress);
    }

    private Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                        String logPrefix, int maxToolCalls, long timeoutSeconds, ProgressListener progress) {
        int transientRetries = 0;
        int stallRetries = 0;
        while (true) {
            Attempt attempt = attempt(agent, command, attachments, logPrefix, maxToolCalls, timeoutSeconds,
                    progress);
            if (attempt.outcome() != null) return attempt.outcome();
            IllegalStateException failure = attempt.failure();
            // 重试前置条件：本次尝试零工具调用（无副作用）。代码里显式写出来，避免将来「顺手」放开。
            boolean retryable = attempt.toolCalls() == 0;
            if (retryable && isTransient(failure) && transientRetries < maxTransientRetries) {
                long backoff = backoffFor(transientRetries);
                transientRetries++;
                log.warn("{}上游瞬时错误（第 {}/{} 次重试，{} ms 后重建会话）：{}",
                        prefix(logPrefix), transientRetries, maxTransientRetries, backoff, failure.getMessage());
                sleep(backoff);
                continue;
            }
            if (retryable && failure instanceof StageTimeoutException && stallRetries < maxStallRetries) {
                stallRetries++;
                log.warn("{}阶段停滞，重建会话重试一次：{}", prefix(logPrefix), failure.getMessage());
                continue;
            }
            throw failure;
        }
    }

    /**
     * 带**模型档案故障切换**的运行（Phase 1）：候选首个为主用，其余为退路（只差模型）。
     *
     * <p>为什么要独立于瞬时重试：{@code isPermanent} 把 {@code model_not_found} /
     * {@code no available channel} / {@code invalid_api_key} / {@code 401/403/404/402} 归为
     * 「重发同一个请求结果一定相同」，因此一次都不重试——**这个判断对同一个模型是对的，
     * 但换一个模型就可能治好**。实测上游下线 agnes-3.0-flash 后，唯一的恢复手段是手工改库。
     *
     * <p>切换的**前置条件与瞬时重试完全一致**（零工具调用）：一旦调用过工具，就可能已经产生
     * 付费副作用（生图 / 委托子会话 / 草稿落库），换模型重跑会重复计费与重复写入。
     *
     * <p>停滞也切换：零工具调用的停滞说明这个模型在本次请求上卡死了，换模型比原地重试更对症，
     * 也省下「再白等一整个会话超时」的时间（SINGLE 900s / COORDINATOR 1800s）。
     */
    @Override
    public Outcome runWithCandidates(List<Candidate> candidates, String command,
                                     List<MemoryMultipartFile> attachments, String logPrefix,
                                     int maxToolCalls, long timeoutSeconds, ProgressListener progress) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("候选智能体列表不能为空");
        }
        int transientRetries = 0;
        int stallRetries = 0;
        int switchIndex = 0;
        int switches = 0;
        // 用过的档案按序记下（含主用）：终态消息与 stages_summary 据此说明「这一轮换了几个模型」
        List<String> usedProfiles = new java.util.ArrayList<>();
        usedProfiles.add(candidates.get(0).label());
        // 实时上报主用档案：**失败路径**拿不到 Outcome，只能靠这条回调把「当时用的是哪个模型」
        // 留进工作区（实测 run#123/#124 停滞失败后 profilesUsed 为空，无法归因）。
        reportProfileUsed(progress, candidates.get(0).label());
        while (true) {
            Candidate current = candidates.get(switchIndex);
            Attempt attempt = attempt(current.agent(), command, attachments, logPrefix, maxToolCalls,
                    timeoutSeconds, progress, current.governor());
            if (attempt.outcome() != null) {
                Outcome outcome = attempt.outcome();
                if (switches == 0) return outcome;
                // 发生过切换：把档案链附在产出上，供 stages_summary 与终态消息使用
                return new Outcome(outcome.reply(), outcome.toolCalls(), outcome.executionLog(),
                        outcome.toolFailures(), List.copyOf(usedProfiles));
            }
            IllegalStateException failure = attempt.failure();
            boolean retryable = attempt.toolCalls() == 0;
            boolean hasNext = switchIndex + 1 < candidates.size();

            // ① 模型级错误 → 换下一个档案（换模型才可能治好的那几类）
            if (retryable && hasNext && isModelLevelFailure(failure)) {
                switchIndex++;
                switches++;
                Candidate next = candidates.get(switchIndex);
                usedProfiles.add(next.label());
                reportProfileUsed(progress, next.label());
                log.warn("{}模型档案不可用（第 {} 次切换）：{}；{} → {}",
                        prefix(logPrefix), switches, failure.getMessage(), current.label(), next.label());
                reportProgressOnly(progress, prefix(logPrefix) + "模型档案不可用（" + brief(failure) + "），切换："
                        + current.label() + " → " + next.label());
                continue;
            }
            // ② 停滞 → 优先换档案（若有），否则按原有预算原地重建一次
            if (retryable && failure instanceof StageTimeoutException) {
                if (hasNext) {
                    switchIndex++;
                    switches++;
                    Candidate next = candidates.get(switchIndex);
                    usedProfiles.add(next.label());
                    reportProfileUsed(progress, next.label());
                    log.warn("{}阶段停滞且无工具调用，换模型档案重试：{} → {}",
                            prefix(logPrefix), current.label(), next.label());
                    reportProgressOnly(progress, prefix(logPrefix) + "阶段停滞且未调用工具，换模型档案重试："
                            + current.label() + " → " + next.label());
                    continue;
                }
                if (stallRetries < maxStallRetries) {
                    stallRetries++;
                    log.warn("{}阶段停滞，重建会话重试一次：{}", prefix(logPrefix), failure.getMessage());
                    continue;
                }
            }
            // ③ 瞬时错误 → 同模型退避重试
            if (retryable && isTransient(failure) && transientRetries < maxTransientRetries) {
                long backoff = backoffFor(transientRetries);
                transientRetries++;
                log.warn("{}上游瞬时错误（第 {}/{} 次重试，{} ms 后重建会话）：{}",
                        prefix(logPrefix), transientRetries, maxTransientRetries, backoff, failure.getMessage());
                sleep(backoff);
                continue;
            }
            throw failure;
        }
    }

    /**
     * 模型级错误：**只有换模型才可能治好**的那几类，是 {@link #isPermanent} 的严格子集。
     *
     * <p>与 {@code isPermanent} 的关键区别（这些**不**触发切换）：
     * <ul>
     *   <li>{@code 451} / 内容审查——换个模型同样会被拦，白费一次切换；</li>
     *   <li>{@code 429} / {@code concurrent limit}——那是**账号级**并发配额（同一网关换模型无效），
     *       继续走原有的同模型退避重试；</li>
     *   <li>余额不足等账号级状态——所有档案共用同一个 key，换模型不解决。</li>
     * </ul>
     */
    static boolean isModelLevelFailure(Throwable failure) {
        return matchesAny(failure, "model_not_found", "no available channel",
                "invalid_api_key", "401", "402", "403", "404");
    }

    /** 失败原因的一句话摘要（用于执行日志；完整堆栈仍在 TASK_RUN.MESSAGE 里）。 */
    private static String brief(Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int newline = message.indexOf('\n');
        if (newline > 0) message = message.substring(0, newline);
        return message.length() > 120 ? message.substring(0, 120) + "…" : message;
    }

    /** 一次会话尝试：成功返回 outcome，失败返回异常（并带上本次尝试已发生的工具调用数）。 */
    private Attempt attempt(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                            String logPrefix, int maxToolCalls, long timeoutSeconds, ProgressListener progress) {
        return attempt(agent, command, attachments, logPrefix, maxToolCalls, timeoutSeconds, progress, null);
    }

    /**
     * @param governor 只读检索治理器（可空）：每次尝试开始时重置其预算计数，并在工具调用时同步计数，
     *                 使工具结果里的「剩余额度 N 次」提示与实际预算一致。
     */
    private Attempt attempt(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                            String logPrefix, int maxToolCalls, long timeoutSeconds, ProgressListener progress,
                            ToolCallGovernor governor) {
        if (governor != null) governor.beginSession(maxToolCalls);
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicInteger toolFailures = new AtomicInteger();
        // 超限被拒的次数（只统计**非收尾工具**）：兜底护栏按它判，而不是按总计数——
        // 总计数会把「预算用尽后放行的收尾工具」也算成超限，让正常的收尾变成阶段失败。
        AtomicInteger rejectedOverBudget = new AtomicInteger();
        // 预算用尽后被**放行**的收尾调用数，以及其中失败的次数。放行判据见
        // ToolCallBudget.allowsTerminalPastBudget：失败不占额度，但总次数仍有硬上限。
        AtomicInteger terminalGraceUsed = new AtomicInteger();
        AtomicInteger terminalGraceFailed = new AtomicInteger();
        // 本次会话是否**成功提交过成果**（收尾工具走到 COMPLETED）。预算用尽后有收尾工具放行的宽限，
        // 但「被拒过」与「成果交没交出来」是两件事：只要成果交出来了，这一阶段就不该记成中止。
        AtomicBoolean deliverableSubmitted = new AtomicBoolean();
        // 无进展循环的中止说明（检测到即记录，见下方 CALLING 分支）。与 rejectedOverBudget 同理：
        // 回调里抛出的异常可能被 agent4j 吞掉，因此这里按记录再判一次，保证一定能被调用方感知。
        AtomicReference<String> loopAbort = new AtomicReference<>();
        Set<String> countedCalls = ConcurrentHashMap.newKeySet();
        /**
         * 正在执行中的工具（call id）。**这是无进展检测的关键输入**：
         * 协调者主编委托子智能体时，子会话在工具内部同步运行，主编侧长时间收不到任何事件——
         * 若只按事件计时，180 秒阈值会把正常等待委托的主编误杀
         * （实测 run#41/42/47/48 的卡点全是「主编侧无事件、子智能体正在运行」）。
         *
         * <p>用集合而不是计数器：COMPLETED/onToolError 若因异常未成对回调，
         * 计数器会永远 &gt;0 从而**永久关闭**无进展检测（静默失去保护），
         * 而集合只会在「确有工具没回调结束」时保持非空——两者的失效方向一致但集合更容易发现。
         * 无论哪种，失效方向都是「回落到硬超时」，不会误杀。
         */
        Set<String> inFlightTools = ConcurrentHashMap.newKeySet();
        List<String> executionLog = java.util.Collections.synchronizedList(new ArrayList<>());
        StringBuilder assistantText = new StringBuilder();
        // 停滞诊断：记录「最后一次收到的有效事件」，超时时据此说明卡在哪一步，
        // 而不是只留一句「300 秒未结束」（调用级日志，方案第④期）。
        AtomicReference<String> lastActivity = new AtomicReference<>("会话已启动，等待模型首个响应");
        AtomicLong lastActivityAt = new AtomicLong(System.nanoTime());
        AgentClientSession session = agent.createSession();
        AgentSessionResult result = (attachments == null || attachments.isEmpty()
                ? session.command(command)
                : session.command(command, attachments))
                .then(new AgentResultHandler() {
                    @Override
                    public void onMessage(String message) {
                        if (message == null) return;
                        assistantText.append(message);
                        markActivity(lastActivity, lastActivityAt, "收到模型输出 " + message.length() + " 字符");
                    }

                    /**
                     * 思维链增量。**必须计为进展**：agent4j 把 {@code reasoning_content} /
                     * {@code reasoning} / {@code thinking} / {@code thinking_content} 四种字段
                     * 路由到这里（见 {@code OpenAIChatModel} 的 delta 分派），只有正文才走
                     * {@link #onMessage}。推理型模型会先连续输出几分钟思维链再吐正文，这段时间
                     * 上游一直在发数据、会话完全健康，但若不计入进展，无进展检测看到的就是
                     * 「一条事件都没有」。
                     *
                     * <p>实测（hy4-preview，269 秒的生成）：思维链增量 3322 条、正文增量仅 195 条，
                     * 按「只算正文」计的最大空档达 **254 秒**——超过 180 秒阈值，于是 run#123/#124
                     * 在 33/21 次工具调用后被判「停滞」中止，而线程其实一直在正常出字
                     * （01:02:33 判死，01:05:46 仍有草稿落库，01:14 仍有 MarkFlow 校验日志）。
                     *
                     * <p>只刷新活动时间、**不写入 {@code assistantText}**：思维链是模型的草稿纸，
                     * 不是交付内容，混进正文会污染草稿与终态消息。
                     */
                    @Override
                    public void onThink(String thought) {
                        if (thought == null) return;
                        markActivity(lastActivity, lastActivityAt, "收到思维链输出 " + thought.length() + " 字符");
                    }

                    @Override
                    public void onTool(ToolDescriptor tool, ToolStatus status) {
                        if (tool == null || status == ToolStatus.PREPARING) return;
                        String name = tool.getName() == null ? "unknown" : tool.getName();
                        String key = name + "\n" + safeCallId(tool);
                        if (status == ToolStatus.CALLING) {
                            inFlightTools.add(key);
                        } else if (status == ToolStatus.COMPLETED) {
                            // 工具失败走 onToolError（ToolStatus 没有 FAILED 常量），那里也摘一次
                            inFlightTools.remove(key);
                        }
                        if (status == ToolStatus.CALLING && countedCalls.add(key)) {
                            int count = toolCalls.incrementAndGet();
                            // 实时上报：会话随后若因超限/断流/超时抛异常，调用方仍拿得到已完成的部分计数
                            if (progress != null) progress.toolCallCounted(1);
                            // 治理器的预算计数与运行器保持同步：工具结果里的「剩余 N 次」提示取自它
                            if (governor != null) governor.countCall();
                            report(progress, executionLog, prefix(logPrefix) + "调用工具：" + name);
                            markActivity(lastActivity, lastActivityAt, "调用工具 " + name);
                            if (governor != null && governor.noteCall(name, tool.getInputParams())) {
                                // 无进展循环：同一参数重复到中止阈值。**在工具执行前**判，
                                // 因为此时才拦得住那次上游请求（工具一旦开始跑就已经花掉了）。
                                // 提示语必须可执行——实测模型读到「已中止」后会继续连调 7 次工具。
                                int repeats = governor.repeatCount(name, tool.getInputParams());
                                String abort = ToolCallGovernor.noProgressMessage(name, repeats);
                                loopAbort.compareAndSet(null, abort);
                                report(progress, executionLog, prefix(logPrefix) + "检测到无进展循环："
                                        + name + " 以相同参数被调用 " + repeats + " 次，中止会话");
                                throw new IllegalStateException(abort);
                            }
                            if (maxToolCalls > 0 && count > maxToolCalls) {
                                // 预算护栏：超出上限即中止会话（skills-agent-plan 5.5）。
                                // 例外是「交出成果」的收尾工具——预算拦的是失控检索，不是提交成果，
                                // 拦下它等于把前面几十次成功检索的产出全部作废（见 ToolCallBudget.TERMINAL_TOOLS）。
                                //
                                // 宽限只在收尾工具**失败**时消耗（见 onToolError），不在成功调用时消耗：
                                // 实测 run#85 的 4 次宽限里只有 1 次是真正的成果提交——一次成功的
                                // set_article_draft_cover、两次摘要超长的 save_article_draft 失败各占一格，
                                // 第 4 次 save_article_draft 因此被预算拒绝，前 40 次成功检索全部作废。
                                // 判据与上界见 ToolCallBudget.allowsTerminalPastBudget。
                                if (ToolCallBudget.TERMINAL_TOOLS.contains(name)
                                        && ToolCallBudget.allowsTerminalPastBudget(
                                                terminalGraceUsed.get(), terminalGraceFailed.get())) {
                                    terminalGraceUsed.incrementAndGet();
                                    report(progress, executionLog, prefix(logPrefix)
                                            + "预算已用尽，放行收尾工具：" + name);
                                } else {
                                    rejectedOverBudget.incrementAndGet();
                                    throw new IllegalStateException(budgetExceededMessage(maxToolCalls, name));
                                }
                            }
                        } else if (status == ToolStatus.COMPLETED) {
                            report(progress, executionLog, prefix(logPrefix) + "工具完成：" + name);
                            markActivity(lastActivity, lastActivityAt, "工具完成 " + name);
                            if (ToolCallBudget.TERMINAL_TOOLS.contains(name)) deliverableSubmitted.set(true);
                        }                    }

                    @Override
                    public void onToolError(ToolDescriptor tool, Exception error) {
                        toolFailures.incrementAndGet();
                        String name = tool == null ? "unknown" : tool.getName();
                        // 工具已结束（无论成败）：从「在飞集合」里摘掉，否则无进展检测会一直以为它在跑。
                        // onTool 的 FAILED 分支通常已经摘过，这里兜住「只回调 onToolError」的上游实现。
                        inFlightTools.remove(name + "\n" + safeCallId(tool));
                        // 失败的收尾调用换回一格额度（见 ToolCallBudget.allowsTerminalPastBudget）：
                        // 成功提交成果不该与「格式校验失败重试」争抢同一个额度。只对收尾工具计数，
                        // 普通检索失败不占宽限。
                        if (ToolCallBudget.TERMINAL_TOOLS.contains(name)) terminalGraceFailed.incrementAndGet();
                        String reason = error.getMessage() == null
                                ? error.getClass().getSimpleName() : error.getMessage();
                        // 参数 JSON 解析失败单独上报（Phase 4.4）：它是实测最常见的一类工具失败
                        // （模型把参数写成 Markdown 代码块或漏引号），混在「工具失败」总数里看不出趋势——
                        // 协议提示词补了完整 JSON 示例之后到底有没有变好，只能靠这个单独的数字回答。
                        // 计数落在工作区（运行级、可持久化），运行器自身不留局部副本。
                        if (isToolParamParseFailure(reason) && progress != null) {
                            progress.toolParamParseFailed(name);
                        }
                        report(progress, executionLog, prefix(logPrefix) + "工具失败：" + name + " - " + reason);
                        markActivity(lastActivity, lastActivityAt, "工具失败 " + name);
                    }
                });
        String response;
        try {
            // 停在 await（停滞）还是停在 get（模型/工具错误）都要走同一处归一化：
            // 停滞要补卡点信息，429 要能被 isRateLimited 识别出来。
            awaitStage(result, logPrefix, timeoutSeconds, lastActivityAt, inFlightTools);
            response = result.get();
        } catch (Exception exception) {
            // NPE 几乎只可能来自 agent4j 对未知工具名不判空（ToolDescriptor.fromTool 直接 tool.getClass()）。
            // 那条异常里**没有工具名**，事后完全查不出是哪个名字越界，所以把本次广告的工具清单记进日志——
            // 下次再遇到就能拿它去比对模型返回的工具名（实测 run#86 的整条运行日志只有一行「启动主编智能体」）。
            // 沿 cause 链判：异常常被 ExecutionException/IllegalStateException 包了几层。
            if (hasCause(exception, NullPointerException.class)) {
                report(progress, executionLog, prefix(logPrefix)
                        + "会话抛空指针（疑似模型返回了未广告的工具名）；本次广告的工具："
                        + advertisedToolNames(agent));
            }
            // 失败必须**返回**给调用循环（而不是就地抛出），否则 run() 里的有界重试永远不会生效
            return new Attempt(null, toolCalls.get(),
                    fail(exception, logPrefix, lastActivity, lastActivityAt, toolCalls.get(), toolFailures.get()));
        }
        // 兜底护栏：回调里抛出的异常可能被 agent4j 吞掉，这里按「被拒次数」再判一次，
        // 保证「子智能体工具调用超限」一定能被调用方感知（方案 5.5 预算护栏）。
        // 按被拒次数而非总计数：总计数含预算用尽后放行的收尾工具，会让正常收尾被判成超限。
        // 无进展循环的兜底护栏（同 rejectedOverBudget 的理由：回调里抛的异常可能被 agent4j 吞掉）。
        // 与预算不同，这里**不放行收尾工具**——循环中的收尾调用只会重复同一份内容
        // （save_research_notes 是追加语义，重复提交会刷满工作区），直接中止更干净。
        if (loopAbort.get() != null) {
            return new Attempt(null, toolCalls.get(), new IllegalStateException(loopAbort.get()));
        }
        if (rejectedOverBudget.get() > 0) {
            // 但「被拒过」不等于「这一阶段白干」：预算用尽后模型仍可用收尾工具交出成果（TERMINAL_TOOLS），
            // 成果已经交出来了就不该记成「阶段中止」——那会把一次成功的交付报成降级，
            // 排查的人会去找一个不存在的失败阶段（run#63/#68 都是靠收尾工具才把成果交出来的）。
            if (deliverableSubmitted.get()) {
                report(progress, executionLog, prefix(logPrefix) + "预算超限被拒 " + rejectedOverBudget.get()
                        + " 次，但收尾工具已提交成果，本次按已交付处理");
            } else {
                // 同上：交给调用循环决定（此时已调用过工具，必然不可重试）
                return new Attempt(null, toolCalls.get(),
                        new IllegalStateException(budgetExceededMessage(maxToolCalls, null)));
            }
        }
        String reply = response == null || response.isBlank() ? assistantText.toString().trim() : response.trim();
        return new Attempt(new Outcome(reply, toolCalls.get(), String.join("\n", executionLog), toolFailures.get()),
                toolCalls.get(), null);
    }

    /**
     * 执行会话并施加阶段硬超时与**无进展检测**；停滞抛 {@link StageTimeoutException}（可重试），
     * 模型错误抛 IllegalStateException。
     *
     * <p>无进展检测（Phase 3）：距上次有效事件超过 {@code inactivity-seconds} 即判停滞，
     * 不必等到硬超时——SINGLE 的一次卡死从「白等 900 秒」降到约 180 秒，
     * 且立刻触发 {@link #runWithCandidates} 的换档案路径。硬超时仍保留作为不依赖上报的兜底。
     *
     * @param timeoutSeconds 调用方覆盖的超时（&le;0 表示用本组件配置的 stage-timeout-seconds）
     * @param lastActivityAt 最后一次有效事件的纳秒时间戳（调用方在每次 onMessage/onTool 时刷新）
     * @param inFlightTools  正在执行中的工具集合：非空即视为「有进展」，
     *                       避免把「正在等子智能体委托返回」的主编误判为停滞
     */
    private void awaitStage(AgentSessionResult result, String logPrefix, long timeoutSeconds,
                            AtomicLong lastActivityAt, Set<String> inFlightTools) {
        long effective = timeoutSeconds > 0 ? timeoutSeconds : stageTimeoutSeconds;
        StageTimeout.await(result, effective, inactivitySeconds, prefix(logPrefix), lastActivityAt::get,
                () -> !inFlightTools.isEmpty());
    }

    /**
     * 把会话失败包装成带卡点信息的异常。
     *
     * <p>停滞（{@link StageTimeoutException}）额外说明「最后活动是什么、距今多久、已调用几次工具」——
     * 事故排查时这三项就能判定卡在第一轮响应还是某个工具之后，无需再靠复现。
     */
    private static IllegalStateException fail(Exception error, String logPrefix,
                                             AtomicReference<String> lastActivity, AtomicLong lastActivityAt,
                                             int toolCalls, int toolFailures) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof StageTimeoutException) {
            long stalledMillis = (System.nanoTime() - lastActivityAt.get()) / 1_000_000L;
            String detail = "；卡点：最后活动为「" + lastActivity.get() + "」，距今 "
                    + stalledMillis / 1000L + " 秒；已调用工具 " + toolCalls + " 次";
            return new StageTimeoutException(message + detail, cause);
        }
        return new IllegalStateException(message, cause);
    }

    private static void markActivity(AtomicReference<String> target, AtomicLong at, String what) {
        target.set(what);
        at.set(System.nanoTime());
    }

    /**
     * 超限消息：这段文字**会作为工具结果回给模型**（回调里抛出的异常经 agent4j 转成该工具的失败原因），
     * 所以必须是可执行的指令，而不是一句「已中止」。
     *
     * <p>实测（run#62）：模型读到「已中止」后并不明白该收手，又连调 7 次工具（4 次 search_web、
     * 3 次 save_research_notes），全部被同一句拒绝——既白烧 tokens，又错过了提交收尾工具的窗口。
     *
     * @param toolName 被拒的工具名；兜底路径不带具体工具名时传 null
     */
    private static String budgetExceededMessage(int maxToolCalls, String toolName) {
        return "子智能体工具调用超过上限 " + maxToolCalls + " 次，已中止"
                + (toolName == null ? "" : "工具：" + toolName)
                + "。预算已用尽，不要再检索、浏览或读取；请立即用收尾工具"
                + "（save_research_notes / save_article_draft / submit_review）提交已有成果，"
                + "或直接输出最终回复。";
    }

    /**
     * 是否是「工具参数 JSON 解析失败」。
     *
     * <p>为什么要识别它：这是实测最常见的一类工具失败——模型把参数写成 Markdown 代码块、
     * 漏了引号或用了中文标点，agent4j 就报 {@code Failed to parse tool param JSON}。
     * 它特别浪费：一次返工轮次可能就因为它白跑（{@code submit_review} 是收尾工具，失败要占额度宽限）。
     * {@code AgentProtocols.REVIEW} 已补完整 JSON 示例，本条计数用来验证「补了之后有没有变少」。
     */
    static boolean isToolParamParseFailure(String reason) {
        if (reason == null) return false;
        String lower = reason.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("parse tool param") || lower.contains("failed to parse tool")
                || lower.contains("cannot deserialize") || lower.contains("jsonmappingexception");
    }

    /**
     * 记一行执行日志：既进本次尝试的局部列表（成功路径经 {@link Outcome#executionLog()} 返回），
     * 也**立即**推给调用方——停滞/超时的那次尝试的局部列表会随异常丢弃，若只留在局部，
     * 运行历史里就查不到「卡在哪一步」（实测：停滞失败的运行 EXECUTION_LOG 为空）。
     */
    private static void report(ProgressListener progress, List<String> executionLog, String line) {
        executionLog.add(line);
        if (progress != null) progress.logLine(line);
    }

    /**
     * 只推给进度回调（没有局部日志列表的场合）：切换发生在 {@code attempt} 之外，
     * 那时局部列表已随失败的那次尝试一起作废，只有实时回调能把「换过模型」这件事留在运行历史里。
     */
    private static void reportProgressOnly(ProgressListener progress, String line) {
        if (progress != null) progress.logLine(line);
    }

    /** 实时上报一个实际用过的模型档案（失败路径靠它留下归因依据，见 ProgressListener#profileUsed）。 */
    private static void reportProfileUsed(ProgressListener progress, String label) {
        if (progress != null && label != null && !label.isBlank()) progress.profileUsed(label);
    }

    /** 上游网关并发超限：{@code HTTP 429} 或 {@code concurrent limit exceeded}。 */
    static boolean isRateLimited(Throwable failure) {
        return matchesAny(failure, "429", "concurrent limit", "too many requests");
    }

    /**
     * 永久错误：重试只会白烧配额与时间，必须**先于**瞬时判据检查。
     *
     * <p>清单来自实测的上游错误谱系（各 1~2 次）：
     * <ul>
     *   <li>{@code 401} 鉴权失败、{@code 403} API Key 不属于当前 Base URL / IP 不在白名单；</li>
     *   <li>{@code 404} 接口不存在、{@code 400} 缺 {@code ***.content}（回放守卫已覆盖，仍留兜底）；</li>
     *   <li>{@code 451} {@code censorship_blocked} 内容审查；</li>
     *   <li>{@code 402} 试用额度耗尽且未开启后付费（实测 run#117：跑满 59 次工具调用后被告知
     *       「free trial quota ... exhausted and postpaid billing is not enabled」）——
     *       这是账号级状态，重发一定还是 402；</li>
     *   <li>{@code model_not_found}（如 LongCat-2.0 无可用渠道）——模型/渠道配置错；</li>
     *   <li>余额不足、额度耗尽。</li>
     * </ul>
     * 这些错误的共同点是「重发同一个请求，结果一定相同」。
     */
    static boolean isPermanent(Throwable failure) {
        return matchesAny(failure, "401", "402", "403", "404", "451",
                "model_not_found", "no available channel", "insufficient", "余额不足", "额度",
                "quota exceeded", "free trial quota", "postpaid billing", "permission_error",
                "censorship", "content_filter", "invalid_api_key", "unauthorized");
    }

    /**
     * 瞬时错误：可重试。含 429 与「连接被重置 / 5xx / 流中断 / 网络超时」等上游抖动。
     *
     * <p>为什么 {@code 400} 不在其中：实测的 400 是「回放的 assistant 工具调用消息 content 为空」，
     * 属确定性请求错误（已由 {@code ToolCallArgumentGuard} 补占位空格修掉），重发不会变好。
     * 而 {@code 503} 有两种含义——渠道不可用（瞬时）与 {@code model_not_found}（永久），
     * 故先由 {@link #isPermanent} 拦下后者，这里才认 5xx。
     */
    static boolean isTransient(Throwable failure) {
        // 停滞有**独立且更小**的重试预算（每次要白等一整个会话超时，代价与秒级返回的模型报错
        // 差两个数量级）。这里显式排除，否则它会被当成本类的 5 次重试、把一次停滞拖成小时级。
        if (failure instanceof StageTimeoutException) return false;
        if (isPermanent(failure)) return false;
        if (isRateLimited(failure)) return true;
        // 零工具调用的 NPE：agent4j 的 ToolDescriptor.fromTool 对未知工具名不判空
        // （OpenAIChatModel.handleToolCallsAndContinue 用 toolMap.get 拿到 null 后直接传下去），
        // 重建会话后模型通常不会再返回那个越界的工具名。异常发生在工具执行之前，故无副作用。
        // 必须沿 cause 链找：fail() 会把它包成 IllegalStateException(message, npe)。
        if (hasCause(failure, NullPointerException.class)) return true;
        if (matchesAny(failure, "connection reset", "socket closed", "connection closed",
                "stream reset", "internal_error", "broken pipe", "unexpected end of stream",
                "premature eof", "timeout", "timed out")) {
            return true;
        }
        // 5xx：502/503/504 与 500。放在最后判，避免把 "500" 之类的数字误伤到别的语义上。
        return matchesAny(failure, "500", "502", "503", "504", "bad gateway", "service unavailable",
                "gateway timeout", "openai_error");
    }

    /** 沿 cause 链匹配任一关键字（大小写不敏感）。 */
    private static boolean matchesAny(Throwable failure, String... keywords) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 10; depth++) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                for (String keyword : keywords) {
                    if (lower.contains(keyword)) return true;
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /** cause 链上是否存在指定类型的异常（会话异常常被 ExecutionException/IllegalStateException 包几层）。 */
    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (type.isInstance(current)) return true;
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待重试时被中断", interrupted);
        }
    }

    private static String prefix(String logPrefix) {
        return logPrefix == null || logPrefix.isBlank() ? "" : logPrefix;
    }

    /**
     * 本次会话广告给模型的工具名（用于空指针现场定位，见 attempt 里的 NPE 分支）。
     *
     * <p>取的是 {@code ToolDescriptor} 的名字而非类名：模型看到并回传的是这个名字，
     * 排查时要拿它与报错里的工具名直接比对。逐个 try/catch——诊断信息取不到不能影响失败归一化。
     */
    private static String advertisedToolNames(AgentClient agent) {
        try {
            List<ink.icoding.llm.core.tool.Tool> tools = agent.getTools();
            if (tools == null || tools.isEmpty()) return "（无）";
            StringBuilder names = new StringBuilder();
            for (ink.icoding.llm.core.tool.Tool tool : tools) {
                if (names.length() > 0) names.append(", ");
                try {
                    ToolDescriptor descriptor = ToolDescriptor.fromTool(tool);
                    names.append(descriptor == null ? "<null>" : descriptor.getName());
                } catch (Exception broken) {
                    names.append("<无法解析>");
                }
            }
            return names.toString();
        } catch (Exception ignored) {
            return "（读取失败）";
        }
    }

    private static String safeCallId(ToolDescriptor descriptor) {
        return descriptor == null || descriptor.getCallId() == null ? "" : descriptor.getCallId();
    }

    /** 一次尝试的结果：成功带 outcome，失败带异常与本次已发生的工具调用/失败数。 */
    private record Attempt(Outcome outcome, int toolCalls, IllegalStateException failure) {
    }
}
