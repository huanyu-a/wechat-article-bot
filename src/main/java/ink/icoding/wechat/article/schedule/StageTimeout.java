package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentSessionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * agent4j 会话硬超时执行器（PIPELINE / COORDINATOR / SINGLE 三条链路共用）。
 *
 * <p>为什么需要：agent4j 2.3.3 在「SSE 流被中途掐断，但既没有回调 onFailure/onClosed 也没有关闭连接」时
 * 不会完成 {@link AgentSessionResult} 的 Future，等待方永久阻塞——无人值守的定时任务会一直停在 RUNNING，
 * 之后手工与定时触发都被「任务正在执行，请勿重复启动」挡住。实测（线程栈）阻塞点是
 * {@code LLMResult.get(LLMResult.java:77)}，本机出网经 TUN 代理时必现。
 *
 * <p>为什么只能加在调用侧：agent4j 未提供取消接口——{@code AgentClientSession} 与
 * {@code AgentSessionResult} 上都没有 cancel/close，{@code LLMModel.create} 也只接受 4/5 个参数、
 * 不暴露读超时，因此无法在库层让流式读超时生效。
 *
 * <p><b>未解决</b>：超时后工作线程无法真正取消（无 API），只能置为中断并放弃。若该会话的流随后恢复，
 * 它仍可能继续执行工具并与下一次运行重叠（重复生图等付费副作用）。彻底解决需等 agent4j 提供取消接口，
 * 详见方案实施记录的遗留待办。
 */
public final class StageTimeout {
    private static final Logger log = LoggerFactory.getLogger(StageTimeout.class);

    /** 兜底超时（秒）：配置为 0/负数属配置错误，按此值执行而不是让每个阶段立刻失败。 */
    static final long FALLBACK_TIMEOUT_SECONDS = 300L;

    private StageTimeout() {
    }

    /**
     * 等待会话结束，最多等待 {@code timeoutSeconds} 秒。
     *
     * @param what 阶段名（如「【调研】」），仅用于日志与线程名
     * @throws StageTimeoutException 超时或被中断（可重试的「停滞」）
     * @throws IllegalStateException 会话本身失败（模型/工具错误，不可重试）
     */
    public static void await(AgentSessionResult result, long timeoutSeconds, String what) {
        long timeout = timeoutSeconds > 0 ? timeoutSeconds : FALLBACK_TIMEOUT_SECONDS;
        String stage = what == null || what.isBlank() ? "" : what;
        CompletableFuture<Void> finished = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                result.execute();
                finished.complete(null);
            } catch (Throwable failure) {
                finished.completeExceptionally(failure);
            }
        }, "agent-stage" + stage.replaceAll("[^\\p{L}\\p{N}]", ""));
        worker.setDaemon(true);
        worker.start();
        try {
            finished.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutError) {
            worker.interrupt();
            log.error("{}智能体会话超过 {} 秒未结束，判定阶段超时中止（agent4j 无取消接口，该线程只能放弃）",
                    stage, timeout);
            throw new StageTimeoutException("智能体会话超时（" + timeout + " 秒未结束）");
        } catch (InterruptedException interrupted) {
            // 等待线程被中断时同样要中断工作线程，否则会话会在无人等待的情况下继续跑
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new StageTimeoutException("智能体会话被中断", interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause() == null ? failed : failed.getCause();
            throw new IllegalStateException(cause.getMessage() == null
                    ? cause.getClass().getSimpleName() : cause.getMessage(), cause);
        }
    }
}
