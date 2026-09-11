package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentSessionResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 阶段硬超时单测（skills-agent-plan 8.1）。
 *
 * <p>{@code AgentSessionResult.execute()} 是阻塞式（线程栈实证阻塞在 {@code LLMResult.get}），
 * 因此用「execute 里睡很久」就能复刻线上那个「SSE 被静默掐断、Future 永不完成」的场景。
 * 用例分别钉住：超时一定抛 {@link StageTimeoutException}（可重试的停滞）、正常完成一定放行、
 * 会话自身失败要被透传、非正数超时走兜底值而不是立刻失败。
 */
class StageTimeoutTest {

    @Test
    void timesOutWhenSessionNeverCompletes() {
        // 模拟永不返回的会话：修复前调用方会永久阻塞，定时任务就一直停在 RUNNING
        AgentSessionResult hanging = new AgentSessionResult(self -> sleepQuietly(60_000));

        long start = System.nanoTime();
        // 必须抛 StageTimeoutException 而不只是 IllegalStateException：
        // AgentInvoker 靠这个类型区分「停滞（可重试）」与「模型报错（不可重试）」
        assertThatThrownBy(() -> StageTimeout.await(hanging, 1, "【测试】"))
                .isInstanceOf(StageTimeoutException.class)
                .hasMessageContaining("智能体会话超时")
                .hasMessageContaining("1 秒未结束");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    void nonPositiveTimeoutFallsBackToDefaultInsteadOfFailingInstantly() {
        // 配置写 0/负数属配置错误：按兜底值执行，而不是让每个阶段立刻失败
        AgentSessionResult quick = new AgentSessionResult(self -> self.complete("完成"));

        StageTimeout.await(quick, 0, "【测试】");

        assertThat(StageTimeout.FALLBACK_TIMEOUT_SECONDS).isEqualTo(300L);
    }

    @Test
    void completesNormallyWhenSessionFinishes() throws Exception {
        AgentSessionResult quick = new AgentSessionResult(self -> self.complete("完成"));

        StageTimeout.await(quick, 5, "【测试】");

        assertThat(quick.get()).isEqualTo("完成");
    }

    @Test
    void sessionFailureSurfacesOnGetAfterAwait() throws Exception {
        // 会话被标记失败时 execute() 正常返回（失败记在 result 自己的 Future 上），错误在 get() 处抛出。
        // 因此调用方契约是「await 保证会话已结束」——AgentInvoker 与定时链路都是 await 之后紧接着 get()。
        AgentSessionResult failing = new AgentSessionResult(
                self -> self.completeExceptionally(new IllegalStateException("上游 400")));

        StageTimeout.await(failing, 5, "【测试】");

        // AgentSessionResult.get() 会把失败包成 ExecutionException（agent4j 的既有行为）
        assertThatThrownBy(failing::get)
                .isInstanceOf(java.util.concurrent.ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上游 400");
    }

    @Test
    void propagatesExceptionThrownBySession() {
        // 另一种失败形态：execute() 自身抛异常，必须原样透传（含原始消息）
        AgentSessionResult throwing = new AgentSessionResult(self -> {
            throw new IllegalStateException("连接被重置");
        });

        assertThatThrownBy(() -> StageTimeout.await(throwing, 5, "【测试】"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("连接被重置");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
