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

    /**
     * 无进展检测的轮询间隔（毫秒）。
     *
     * <p>不用「剩余时间一次算好再 get」是因为要反复比较「距上次有效事件多久」——
     * 但轮询太密会空转 CPU，1 秒对本场景足够（门槛以分钟计）。
     */
    static final long INACTIVITY_POLL_MILLIS = 1_000L;

    private StageTimeout() {
    }

    /**
     * 等待会话结束，最多等待 {@code timeoutSeconds} 秒（无进展检测关闭）。
     */
    public static void await(AgentSessionResult result, long timeoutSeconds, String what) {
        await(result, timeoutSeconds, 0L, what, null, null);
    }

    /**
     * 等待会话结束：硬超时 + **无进展检测**。
     *
     * <p>为什么需要无进展检测：{@code timeoutSeconds} 是**墙钟**上限，它只回答「总共等了多久」，
     * 不回答「上游还活着吗」。实测一次停滞要白等一整个超时——SINGLE 是 900 秒、COORDINATOR 是 1800 秒，
     * 期间没有任何事件，这段时间完全浪费；而模型真正卡死时，等到 900 秒和等到 180 秒的结论是一样的。
     * 加上本检测后，卡死的会话在 {@code inactivitySeconds} 秒内被判为停滞，
     * 立刻交给 {@link AgentInvoker} 走「换模型档案 / 重建会话」的重试路径。
     *
     * <p>为什么不能取代硬超时：无进展检测依赖调用方如实上报活动。
     * 只要有一条路径忘了上报，长任务就会被误杀；硬超时是**不依赖任何上报**的最后防线。
     * 两者叠加才是安全的：先由无进展检测快速止损，硬超时兜住一切漏报。
     *
     * @param inactivitySeconds   距上次有效事件超过该秒数即判停滞；&le;0 表示关闭该检测
     * @param lastActivityAtNanos 最后一次有效事件的 {@code System.nanoTime()}；为 null 时关闭该检测
     * @param activityInFlight    「当前有工具正在执行」的判据（可为 null）。
     *                            **正在跑的工具就是进展**：协调者主编的一次会话覆盖全部委托，
     *                            子智能体同步运行期间主编侧本就收不到任何事件，若只按事件计时，
     *                            180 秒的无进展阈值会把**正常等待委托**的主编误杀
     *                            （实测 run#41/42/47/48 的卡点全是「主编侧无事件、子智能体正在运行」）。
     *                            卡死场景（SSE 断流且无工具在跑）仍会被正常判停滞。
     * @throws StageTimeoutException 硬超时、无进展、或被中断（三者都是可重试的「停滞」）
     * @throws IllegalStateException 会话本身失败（模型/工具错误，不可重试）
     */
    public static void await(AgentSessionResult result, long timeoutSeconds, long inactivitySeconds,
                             String what, java.util.function.LongSupplier lastActivityAtNanos,
                             java.util.function.BooleanSupplier activityInFlight) {
        long timeout = timeoutSeconds > 0 ? timeoutSeconds : FALLBACK_TIMEOUT_SECONDS;
        boolean watchInactivity = inactivitySeconds > 0 && lastActivityAtNanos != null;
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
        long deadline = System.nanoTime() + timeout * 1_000_000_000L;
        while (true) {
            long now = System.nanoTime();
            long remainingHard = deadline - now;
            if (remainingHard <= 0) {
                throw hardTimeout(worker, stage, timeout);
            }
            long waitNanos = remainingHard;
            if (watchInactivity) {
                // 有工具在跑就当作「刚有进展」：见 activityInFlight 的说明
                boolean inFlight = activityInFlight != null && activityInFlight.getAsBoolean();
                long idleNanos = inFlight ? 0L : now - lastActivityAtNanos.getAsLong();
                long remainingIdle = inactivitySeconds * 1_000_000_000L - idleNanos;
                if (remainingIdle <= 0) {
                    throw inactivityTimeout(worker, stage, inactivitySeconds, idleNanos);
                }
                waitNanos = Math.min(waitNanos, remainingIdle);
            }
            // 轮询下限：不让无进展检测把 get 变成忙等（门槛以分钟计，1 秒粒度足够）
            waitNanos = Math.max(waitNanos, INACTIVITY_POLL_MILLIS * 1_000_000L);
            try {
                finished.get(Math.min(waitNanos, remainingHard), TimeUnit.NANOSECONDS);
                return;
            } catch (TimeoutException retry) {
                // 未结束：回到循环顶部重新比较硬超时与无进展两个判据
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

    private static StageTimeoutException hardTimeout(Thread worker, String stage, long timeout) {
        worker.interrupt();
        log.error("{}智能体会话超过 {} 秒未结束，判定阶段超时中止（agent4j 无取消接口，该线程只能放弃）",
                stage, timeout);
        return new StageTimeoutException("智能体会话超时（" + timeout + " 秒未结束）");
    }

    private static StageTimeoutException inactivityTimeout(Thread worker, String stage, long inactivitySeconds,
                                                          long idleNanos) {
        worker.interrupt();
        long idleSeconds = idleNanos / 1_000_000_000L;
        log.error("{}智能体会话已 {} 秒无任何事件（无进展阈值 {} 秒），判定停滞并中止"
                        + "（agent4j 无取消接口，该线程只能放弃）",
                stage, idleSeconds, inactivitySeconds);
        return new StageTimeoutException("智能体会话停滞（" + idleSeconds + " 秒无任何事件）");
    }
}
