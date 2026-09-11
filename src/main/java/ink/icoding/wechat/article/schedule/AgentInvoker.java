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
 */
@Component
public class AgentInvoker extends AgentRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentInvoker.class);

    /** 429（并发超限）的退避重试次数与间隔：共 4 次尝试（1 + 3 次重试）。 */
    static final int MAX_RATE_LIMIT_RETRIES = 3;
    static final long[] RATE_LIMIT_BACKOFF_MILLIS = {1_000L, 2_000L, 4_000L};
    /** 阶段停滞的重试次数：只重试 1 次（停滞多为上游瞬时，且每次要白等一个完整超时）。 */
    static final int MAX_STALL_RETRIES = 1;

    private final long stageTimeoutSeconds;

    public AgentInvoker(@Value("${app.schedule.stage-timeout-seconds:300}") long stageTimeoutSeconds) {
        this.stageTimeoutSeconds = stageTimeoutSeconds;
    }

    @Override
    public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments) {
        return run(agent, command, attachments, null);
    }

    /** @param logPrefix 执行日志前缀（如「【调研】」），null 表示不加前缀。 */
    @Override
    public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments, String logPrefix) {
        return run(agent, command, attachments, logPrefix, 0, null);
    }

    /** @param maxToolCalls 工具调用上限（&le;0 表示不限制）；超限抛异常中止会话（预算护栏）。 */
    @Override
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls) {
        return run(agent, command, attachments, logPrefix, maxToolCalls, null);
    }

    /**
     * @param progress 进度回调：工具计数与日志行**实时**上报，失败路径因此也留得住（见 AgentRunner.ProgressListener）。
     */
    @Override
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls,
                                ProgressListener progress) {
        return run(agent, command, attachments, logPrefix, maxToolCalls, progress);
    }

    private Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                        String logPrefix, int maxToolCalls, ProgressListener progress) {
        int rateLimitRetries = 0;
        int stallRetries = 0;
        while (true) {
            Attempt attempt = attempt(agent, command, attachments, logPrefix, maxToolCalls, progress);
            if (attempt.outcome() != null) return attempt.outcome();
            IllegalStateException failure = attempt.failure();
            boolean rateLimited = isRateLimited(failure);
            // 重试前置条件：本次尝试零工具调用（无副作用）。代码里显式写出来，避免将来「顺手」放开。
            boolean retryable = attempt.toolCalls() == 0;
            if (retryable && rateLimited && rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                long backoff = RATE_LIMIT_BACKOFF_MILLIS[rateLimitRetries];
                rateLimitRetries++;
                log.warn("{}上游网关并发超限（第 {} 次重试，{} ms 后重建会话）：{}",
                        prefix(logPrefix), rateLimitRetries, backoff, failure.getMessage());
                sleep(backoff);
                continue;
            }
            if (retryable && failure instanceof StageTimeoutException && stallRetries < MAX_STALL_RETRIES) {
                stallRetries++;
                log.warn("{}阶段停滞，重建会话重试一次：{}", prefix(logPrefix), failure.getMessage());
                continue;
            }
            throw failure;
        }
    }

    /** 一次会话尝试：成功返回 outcome，失败返回异常（并带上本次尝试已发生的工具调用数）。 */
    private Attempt attempt(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                            String logPrefix, int maxToolCalls, ProgressListener progress) {
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicInteger toolFailures = new AtomicInteger();
        Set<String> countedCalls = ConcurrentHashMap.newKeySet();
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

                    @Override
                    public void onTool(ToolDescriptor tool, ToolStatus status) {
                        if (tool == null || status == ToolStatus.PREPARING) return;
                        String name = tool.getName() == null ? "unknown" : tool.getName();
                        String key = name + "\n" + safeCallId(tool);
                        if (status == ToolStatus.CALLING && countedCalls.add(key)) {
                            int count = toolCalls.incrementAndGet();
                            // 实时上报：会话随后若因超限/断流/超时抛异常，调用方仍拿得到已完成的部分计数
                            if (progress != null) progress.toolCallCounted(1);
                            report(progress, executionLog, prefix(logPrefix) + "调用工具：" + name);
                            markActivity(lastActivity, lastActivityAt, "调用工具 " + name);
                            if (maxToolCalls > 0 && count > maxToolCalls) {
                                // 预算护栏：超出上限即中止会话（skills-agent-plan 5.5）
                                throw new IllegalStateException("子智能体工具调用超过上限 " + maxToolCalls + " 次，已中止");
                            }
                        } else if (status == ToolStatus.COMPLETED) {
                            report(progress, executionLog, prefix(logPrefix) + "工具完成：" + name);
                            markActivity(lastActivity, lastActivityAt, "工具完成 " + name);
                        }
                    }

                    @Override
                    public void onToolError(ToolDescriptor tool, Exception error) {
                        toolFailures.incrementAndGet();
                        String name = tool == null ? "unknown" : tool.getName();
                        report(progress, executionLog, prefix(logPrefix) + "工具失败：" + name + " - "
                                + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                        markActivity(lastActivity, lastActivityAt, "工具失败 " + name);
                    }
                });
        String response;
        try {
            // 停在 await（停滞）还是停在 get（模型/工具错误）都要走同一处归一化：
            // 停滞要补卡点信息，429 要能被 isRateLimited 识别出来。
            awaitStage(result, logPrefix);
            response = result.get();
        } catch (Exception exception) {
            // 失败必须**返回**给调用循环（而不是就地抛出），否则 run() 里的有界重试永远不会生效
            return new Attempt(null, toolCalls.get(),
                    fail(exception, logPrefix, lastActivity, lastActivityAt, toolCalls.get(), toolFailures.get()));
        }
        // 兜底护栏：回调里抛出的异常可能被 agent4j 吞掉，这里按最终计数再判一次，
        // 保证「子智能体工具调用超限」一定能被调用方感知（方案 5.5 预算护栏）。
        if (maxToolCalls > 0 && toolCalls.get() > maxToolCalls) {
            // 同上：交给调用循环决定（此时已调用过工具，必然不可重试）
            return new Attempt(null, toolCalls.get(),
                    new IllegalStateException("子智能体工具调用超过上限 " + maxToolCalls + " 次，已中止"));
        }
        String reply = response == null || response.isBlank() ? assistantText.toString().trim() : response.trim();
        return new Attempt(new Outcome(reply, toolCalls.get(), String.join("\n", executionLog), toolFailures.get()),
                toolCalls.get(), null);
    }

    /** 执行会话并施加阶段硬超时；停滞抛 {@link StageTimeoutException}（可重试），模型错误抛 IllegalStateException。 */
    private void awaitStage(AgentSessionResult result, String logPrefix) {
        StageTimeout.await(result, stageTimeoutSeconds, prefix(logPrefix));
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
     * 记一行执行日志：既进本次尝试的局部列表（成功路径经 {@link Outcome#executionLog()} 返回），
     * 也**立即**推给调用方——停滞/超时的那次尝试的局部列表会随异常丢弃，若只留在局部，
     * 运行历史里就查不到「卡在哪一步」（实测：停滞失败的运行 EXECUTION_LOG 为空）。
     */
    private static void report(ProgressListener progress, List<String> executionLog, String line) {
        executionLog.add(line);
        if (progress != null) progress.logLine(line);
    }

    /** 上游网关并发超限：{@code HTTP 429} 或 {@code concurrent limit exceeded}。 */
    static boolean isRateLimited(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 10; depth++) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("429") || lower.contains("concurrent limit")) return true;
            }
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

    private static String safeCallId(ToolDescriptor descriptor) {
        return descriptor == null || descriptor.getCallId() == null ? "" : descriptor.getCallId();
    }

    /** 一次尝试的结果：成功带 outcome，失败带异常与本次已发生的工具调用/失败数。 */
    private record Attempt(Outcome outcome, int toolCalls, IllegalStateException failure) {
    }
}
