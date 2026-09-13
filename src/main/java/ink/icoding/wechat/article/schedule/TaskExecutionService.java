package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TaskExecutionService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);
    /** 本 JVM 的调度实例标识（I2）；Quartz 多实例部署时用于区分运行属主。 */
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final ScheduleTaskMapper mapper;
    private final TaskRunMapper runMapper;
    private final ArticleAiService aiService;
    private final ArticleService articleService;
    private final ScheduledExecutionRouter router;
    private final ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService;
    private final ink.icoding.wechat.article.common.InFlightGate inFlightGate;
    private final long staleRunHours;
    /** 心跳超时（秒，I2）：心跳早于此值即判定属主实例失联，运行可被中止。 */
    private final long heartbeatTimeoutSeconds;
    /** 运行中进度落库间隔（秒，I10）；同时充当心跳间隔。 */
    private final long progressFlushSeconds;
    /**
     * 周期性刷新运行进度/心跳的调度器（I10/I2）。
     *
     * <p>为什么不是单线程：每个在飞运行各占一条周期任务，若共用一个线程，某次落库被数据库阻塞
     * （如连接超时 30 秒）会把其余运行的心跳一起卡住；心跳一旦超过
     * {@code heartbeat-timeout-seconds}，别的实例就会把这些**正常执行中**的运行误判为孤儿并中止，
     * 这是比「进度刷新延迟」严重得多的后果。线程数按并发运行上限再加一条余量，避免这种队头阻塞。
     */
    private final ScheduledExecutorService progressExecutor;
    private final Map<Long, Object> taskLocks = new ConcurrentHashMap<>();

    public TaskExecutionService(ScheduleTaskMapper mapper, TaskRunMapper runMapper,
                                ArticleAiService aiService, ArticleService articleService,
                                ScheduledExecutionRouter router,
                                ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService,
                                ink.icoding.wechat.article.common.InFlightGate inFlightGate,
                                @Value("${app.schedule.stale-run-hours:" + StaleRunPolicy.DEFAULT_STALE_HOURS + "}")
                                long staleRunHours,
                                @Value("${app.schedule.heartbeat-timeout-seconds:"
                                        + StaleRunPolicy.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS + "}")
                                long heartbeatTimeoutSeconds,
                                @Value("${app.schedule.progress-flush-seconds:15}")
                                long progressFlushSeconds) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.aiService = aiService;
        this.articleService = articleService;
        this.router = router;
        this.markFlowRenderService = markFlowRenderService;
        this.inFlightGate = inFlightGate;
        this.staleRunHours = staleRunHours;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        this.progressFlushSeconds = progressFlushSeconds;
        this.progressExecutor = Executors.newScheduledThreadPool(
                Math.max(2, inFlightGate.limit() + 1), task -> {
                    Thread thread = new Thread(task, "task-run-progress");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @jakarta.annotation.PreDestroy
    public void shutdownProgressExecutor() {
        progressExecutor.shutdownNow();
    }

    /** Starts a manual execution without keeping the HTTP request open for the entire agent run. */
    public TaskRun start(Long taskId, String triggerType) {
        ScheduleTask task = requiredTask(taskId);
        TaskRun run = createRun(taskId, triggerType);
        CompletableFuture.runAsync(() -> executeRun(task, run));
        return run;
    }

    /** Executes synchronously for Quartz, so DisallowConcurrentExecution remains effective. */
    public TaskRun execute(Long taskId, String triggerType) {
        ScheduleTask task = requiredTask(taskId);
        TaskRun run = createRun(taskId, triggerType);
        return executeRun(task, run);
    }

    private ScheduleTask requiredTask(Long taskId) {
        ScheduleTask task = mapper.findById(taskId);
        if (task == null) throw new BusinessException("定时任务不存在");
        return task;
    }

    private TaskRun createRun(Long taskId, String triggerType) {
        synchronized (taskLocks.computeIfAbsent(taskId, ignored -> new Object())) {
            TaskRun running = runMapper.findRunning(taskId);
            if (running != null) {
                if (!StaleRunPolicy.isStale(running, staleRunHours, LocalDateTime.now(),
                        heartbeatTimeoutSeconds)) {
                    throw new BusinessException("任务正在执行，请勿重复启动");
                }
                // 上次进程非正常退出遗留的 RUNNING 会永久占住并发闸门；超过阈值即可判定为孤儿，
                // 就地中止后允许本次触发继续（触发路径自愈，避免必须手工改库才能恢复）。
                abortStale(running);
            }
            TaskRun run = new TaskRun();
            run.setTaskId(taskId);
            run.setTriggerType(triggerType);
            run.setStatus("RUNNING");
            run.setFetchedCount(0);
            run.setGeneratedCount(0);
            run.setToolCallCount(0);
            run.setInstanceId(INSTANCE_ID);
            LocalDateTime startedAt = LocalDateTime.now();
            run.setStartedAt(startedAt);
            // 初始心跳 = 开始时间：即便执行线程还没进入周期刷新，别的实例也不会把这条新运行误判为孤儿（I2）
            run.setHeartbeatAt(startedAt);
            runMapper.insert(run);
            // taskLocks 只是 JVM 本地锁：Quartz 以 isClustered=true 部署时，另一个实例可能在同一瞬间
            // 也通过了上面的检查。插入后按「RUNNING 中 ID 最小者为唯一属主」复核——先插入再复核不需要
            // 任何跨实例锁，且 ID 单调递增保证两个并发插入里恰好有一个赢家；落败者撤回自己刚插入的行，
            // 与「未插入就抛错」的既有语义一致（历史里不留下被拒绝的空运行）。
            TaskRun owner = runMapper.findEarliestRunning(taskId);
            if (owner != null && !owner.getId().equals(run.getId())) {
                runMapper.deleteById(run.getId());
                log.warn("任务 #{} 的并发触发被另一实例占先（属主运行 #{}），本次运行 #{} 已撤回",
                        taskId, owner.getId(), run.getId());
                throw new BusinessException("任务正在执行，请勿重复启动");
            }
            return run;
        }
    }

    /** 启动自愈：中止上次进程未正常结束时遗留的 RUNNING 运行，返回中止条数。 */
    public int reapStaleRuns() {
        List<TaskRun> running = runMapper.findAllRunning();
        int aborted = 0;
        for (TaskRun run : running) {
            if (!StaleRunPolicy.isStale(run, staleRunHours, LocalDateTime.now(), heartbeatTimeoutSeconds)) {
                continue;
            }
            try {
                abortStale(run);
                aborted++;
            } catch (Exception exception) {
                log.warn("中止遗留运行 #{} 失败（跳过，不影响启动）", run.getId(), exception);
            }
        }
        return aborted;
    }

    private void abortStale(TaskRun run) {
        // 比较并交换：只有该行仍是 RUNNING 才写；返回 0 说明它已在扫描间隙被正常收尾，
        // 此时不再打「判定为孤儿」的日志，避免把一次正常完成误报成孤儿中止。
        if (runMapper.abortStale(run.getId(), StaleRunPolicy.ABORT_MESSAGE, LocalDateTime.now()) == 0) return;
        // 判据要说清楚：心跳判据（属主失联）与时间判据（旧行无心跳）的自愈窗口差着数量级，
        // 只写「超过 N 小时」会把分钟级的心跳回收误报成小时级，排查时对不上账。
        String reason = run.getHeartbeatAt() == null
                ? "开始于 " + run.getStartedAt() + " 已超过 " + staleRunHours + " 小时"
                : "心跳停止于 " + run.getHeartbeatAt() + "（超过 " + heartbeatTimeoutSeconds + " 秒）";
        log.warn("任务 #{} 的遗留运行 #{}（实例 {}，{}）仍为 RUNNING，判定为孤儿并中止",
                run.getTaskId(), run.getId(), run.getInstanceId(), reason);
    }

    private TaskRun executeRun(ScheduleTask task, TaskRun run) {
        String mode = task.getExecutionMode() == null || task.getExecutionMode().isBlank()
                ? "SINGLE" : task.getExecutionMode();
        run.setMode(mode);
        // 在飞闸门（见 InFlightGate）：一次运行只占一个名额，嵌套的子智能体会话在同一个运行内因而不重复占位。
        String gateLabel = "任务 #" + task.getId() + " 的运行 #" + run.getId();
        ink.icoding.wechat.article.common.InFlightGate.Lease lease = inFlightGate.acquire(gateLabel);
        if (lease == null) {
            // 取不到额度就**明确失败**（方案的硬要求：排队但不能静默卡住）。
            // 这里不走「任务正在执行」那类拒绝——运行行已经插入，必须留下可读的终态说明。
            run.setStatus("FAILED");
            run.setMessage("LLM 并发额度等待超时（上限 " + inFlightGate.limit() + "，已等待 "
                    + inFlightGate.acquireTimeoutSeconds() + " 秒）：同一时刻进行中的智能体运行占满了名额。"
                    + "请稍后重试，或调大 app.llm.max-in-flight（须低于上游网关的并发上限）。");
            runMapper.finishRun(run);
            mapper.touchRun(task.getId());
            return runMapper.selectById(run.getId());
        }
        TaskWorkspace workspace = null;
        // 周期把运行中的进度与心跳写回库（I10/I2）：定时线程读这里的最新工作区，故用引用持有。
        AtomicReference<TaskWorkspace> liveWorkspace = new AtomicReference<>();
        ScheduledFuture<?> progressFlush = scheduleProgressFlush(run, liveWorkspace);
        try {
            ArticleAiService.ScheduledAgentRequest request = new ArticleAiService.ScheduledAgentRequest(
                    task.getAccountId(), task.getCreatedBy(), task.getCoverAssetId(), task.getTimezone(),
                    task.getOutputMode(), task.getAiPrompt(),
                    ArticleAiService.parseSkillIds(task.getSkillIds()),
                    ScheduleTaskService.parseStageAgents(task.getStageAgents()),
                    task.getMaxRevisionRounds());
            ScheduledExecutionStrategy strategy = router.strategy(mode);
            // SINGLE 由 ArticleAiService 内部自建工作区状态（adopt 回填），无需提前解析引擎
            workspace = TaskWorkspace.create(task.getCoverAssetId(),
                    "SINGLE".equals(mode) ? null : resolveLayoutEngine(request));
            liveWorkspace.set(workspace);
            ArticleAiService.ScheduledAgentResult result = strategy.execute(request, workspace);
            renderAndSummarize(workspace, markFlowRenderService, run);
            ScheduledArticleTools.Draft draft = workspace.draftState().snapshot();
            ArticleService.ArticleRequest articleRequest = new ArticleService.ArticleRequest(task.getAccountId(),
                    draft.title(), draft.author(), draft.digest(), draft.contentHtml(),
                    draft.coverAssetId(), null, draft.sourceUrl(), null, null,
                    draft.layoutEngine() == null ? null : draft.layoutEngine().name(), draft.contentMarkdown(),
                    draft.themeAccent(), draft.themeDark());
            Article article = articleService.createForTask(articleRequest, task.getCreatedBy());
            run.setArticleId(article.getId());
            run.setGeneratedCount(1);
            // 工具调用数取工作区累计值：COORDINATOR 的子智能体调用量只有累计在工作区里才统计得到
            // （执行结果只带 chief 自身的计数），SINGLE/PIPELINE 两条链路与它逐次同步。
            run.setToolCallCount(workspace.toolCallCount());
            run.setExecutionLog(result.executionLog());

            if ("WECHAT_DRAFT".equals(task.getOutputMode())) articleService.syncDraft(article.getId());
            if ("AUTO_PUBLISH".equals(task.getOutputMode())) articleService.publish(article.getId());

            RunCompletion completion = completion(workspace.toolFailureCount(),
                    workspace.degradationCount(), workspace.draftState().renderWarnings(), result.message());
            run.setStatus(completion.status());
            run.setMessage(completion.message());
        } catch (Exception error) {
            run.setStatus("FAILED");
            // 失败也要留住已产生的分阶段日志：否则运行历史只剩一行错误，看不出卡在哪一阶段
            // （PIPELINE 的失败排查几乎只能依赖这份日志，例如「【调研】智能体会话超时」）
            if (workspace != null) {
                String partial = workspace.executionLogText();
                if (!partial.isBlank()) run.setExecutionLog(partial);
                // 失败也要留住已发生的工具调用数：运行历史里「调了几次工具」不该在失败时永远是 0
                run.setToolCallCount(workspace.toolCallCount());
                // 摘要同理：失败路径同样要有一份，否则「失败 + 无摘要」比「失败 + 半份摘要」更难排查
                run.setStagesSummary(workspaceSummary(workspace));
            }
            run.setMessage(trimMessage(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage()));
        } finally {
            // 先停心跳/进度刷写，再收尾：避免收尾后又被定时线程写回一行 RUNNING 快照
            if (progressFlush != null) progressFlush.cancel(false);
            try {
                // 先落终态再释放名额：反过来（先释放）会让这条运行在「已不占名额、却仍是 RUNNING」的窗口里
                // 被其它实例的扫描看到，若此刻进程被杀，运行历史里就多一条「孤儿中止」，而它其实已正常收尾。
                runMapper.finishRun(run);
                mapper.touchRun(task.getId());
            } finally {
                // 名额在整个运行期间持有（含渲染与落库）；放在 finally 里保证收尾写库失败时也一定归还
                lease.close();
            }
        }
        return runMapper.selectById(run.getId());
    }

    /**
     * 运行中进度周期落库（I10）与属主心跳（I2）。
     *
     * <p>此前仅在收尾时写一次 {@code execution_log}/{@code tool_call_count}：COORDINATOR 长任务（十几分钟）
     * 在运行期间历史里永远是「0 次工具调用、空日志」，前端看不到任何进展；进程被杀时这些进度更是全部丢失。
     * 现在每 {@code progress-flush-seconds} 秒刷一次，同时刷新心跳——心跳停止即证明属主实例已失联，
     * 其它实例可据此把孤儿自愈窗口从「开始时间 + 3h」压到分钟级。
     *
     * <p>工作区尚未建立（如布局引擎解析阶段）时只刷心跳：此时同样需要宣告属主存活。
     * 整个过程 best-effort——数据库抖动不能影响主执行流程。
     */
    private ScheduledFuture<?> scheduleProgressFlush(TaskRun run, AtomicReference<TaskWorkspace> liveWorkspace) {
        long interval = Math.max(1L, progressFlushSeconds);
        try {
            return progressExecutor.scheduleWithFixedDelay(() -> {
                try {
                    TaskWorkspace workspace = liveWorkspace.get();
                    LocalDateTime now = LocalDateTime.now();
                    if (workspace == null) {
                        runMapper.updateHeartbeat(run.getId(), now);
                    } else {
                        runMapper.updateProgress(run.getId(), run.getMode(), workspace.executionLogText(),
                                workspace.toolCallCount(), now);
                    }
                } catch (Exception exception) {
                    log.warn("刷新运行 #{} 的进度/心跳失败（跳过本次，不影响执行）", run.getId(), exception);
                }
            }, interval, interval, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("启动运行 #{} 的进度刷写失败（不影响执行）", run.getId(), exception);
            return null;
        }
    }

    /**
     * 交付前渲染 + 把工作区摘要写进运行记录。
     *
     * <p>MARKFLOW 延迟渲染：交付前统一渲染一次（skills-agent-plan 5.10.4）。SINGLE 链路已在
     * {@code runScheduledAgent} 内渲染（{@code rendered=true} 时幂等跳过），此处覆盖 PIPELINE/COORDINATOR。
     *
     * <p>两件事**必须按这个顺序**、且不可拆开调用：{@code stages_summary.renderWarnings} 正是渲染的产出
     * （{@code DraftState.renderWarnings}）。先取摘要再渲染会得到一个**结构性恒为 0** 的字段——
     * PIPELINE/COORDINATOR 从不提前渲染，于是 run#71 出现「摘要写着 0 处渲染降级、运行说明写着 1 处」
     * 的自相矛盾，排查时会先怀疑渲染服务，而不是这个字段本身。
     *
     * <p>独立成包级方法是为了让这条顺序被单测钉死：它只依赖渲染服务与工作区两个对象。
     */
    static void renderAndSummarize(TaskWorkspace workspace,
                                   ink.icoding.wechat.article.skill.MarkFlowRenderService renderService,
                                   TaskRun run) {
        workspace.draftState().renderBeforeDelivery(renderService);
        run.setStagesSummary(workspaceSummary(workspace));
    }

    /** 工作区摘要（best-effort）：写入 task_run.stages_summary。 */
    static String workspaceSummary(TaskWorkspace workspace) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(workspace.summary());
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 排版引擎解析（SINGLE 走 ArticleAiService 内部组装，其余执行器需要提前知道引擎以构造工作区）。
     * 不吞异常：MARKFLOW 技能绑定但渲染服务不可用时必须让任务以明确错误失败，
     * 而不是静默回落 PROMPT（静默换引擎会产出完全不同的版式，违背用户预期，方案 5.10.2）。
     */
    private ink.icoding.wechat.article.skill.LayoutEngine resolveLayoutEngine(
            ArticleAiService.ScheduledAgentRequest request) {
        return aiService.resolveLayoutEngine(request);
    }

    /**
     * 终态判定：有工具失败时不能记成干净的 SUCCESS。「配图工具失败但整次运行成功」会让用户
     * 以为交付物完整——实测 {@code generate_image} 因描述超长落库失败，配图没进文章却记了 SUCCESS。
     *
     * <p>「降级」与「工具失败」分开表述：降级是某个阶段整个没做完（如调研阶段会话中止，
     * 写作阶段仍据任务要求完成了文章），说成「N 次工具调用失败」与实情不符。
     * 两者都让终态变成 SUCCESS_WITH_WARNINGS——交付确实打了折扣，不该显示为干净的绿色对勾。
     *
     * <p>独立成函数是为了让这条规则可被单测钉住（{@code TaskRunCompletionTest}）：它只依赖
     * 「工具失败数 + 降级数 + 渲染警告 + 智能体回复」四个值，不依赖运行链路，因此不必为了验证它而跑一次真实会话。
     *
     * @param renderWarnings MarkFlow 渲染降级警告（{@code RenderResult.warnings}）：非空说明文章的版式
     *                       **没有完全复刻**（产物里留着未识别的 {@code <steps>}/{@code :::} 等）。
     *                       这类降级此前完全静默——渲染器把语法当普通文字输出，落库的 HTML 看起来「有样式」，
     *                       只有量过结构才知道版式没了。
     */
    static RunCompletion completion(int toolFailures, int degradations, java.util.List<String> renderWarnings,
                                    String agentMessage) {
        String message = agentMessage == null ? "" : agentMessage;
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (toolFailures > 0) warnings.add("有 " + toolFailures + " 次工具调用失败");
        if (degradations > 0) warnings.add("有 " + degradations + " 个阶段中止并已按现有产出继续");
        if (renderWarnings != null && !renderWarnings.isEmpty()) {
            warnings.add("有 " + renderWarnings.size() + " 处 MarkFlow 渲染降级（" + renderWarnings.get(0) + "）");
        }
        if (warnings.isEmpty()) return new RunCompletion("SUCCESS", trimMessage(message));
        return new RunCompletion("SUCCESS_WITH_WARNINGS",
                trimMessage(String.join("，", warnings) + "（详见执行日志），交付内容可能不完整。" + message));
    }

    /** 兼容重载：无渲染降级警告。 */
    static RunCompletion completion(int toolFailures, int degradations, String agentMessage) {
        return completion(toolFailures, degradations, java.util.List.of(), agentMessage);
    }

    private static String trimMessage(String value) {
        if (value == null || value.isBlank()) return "任务执行结束";
        return value.length() > 60_000 ? value.substring(0, 60_000) : value;
    }

    /** 运行终态（状态 + 说明）。 */
    record RunCompletion(String status, String message) {
    }
}
