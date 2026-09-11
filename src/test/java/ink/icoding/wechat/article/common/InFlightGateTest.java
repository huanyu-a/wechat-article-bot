package ink.icoding.wechat.article.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 在飞闸门单测（2026-09-11 网关并发事故修复）。
 *
 * <p>闸门要守两条线：**上限真的会拦住**（否则打满上游 429 配额），以及**等待有界且失败可读**
 * （否则要么静默卡死，要么排队线程永久堆积）。{@code close()} 的幂等性也在其中——运行收尾的
 * try/finally 与异常分支可能重复释放，多释放一次就会让信号量凭空多一个名额，闸门形同虚设。
 */
class InFlightGateTest {

    @Test
    void grantsUpToLimitThenRefusesInsteadOfBlockingForever() {
        InFlightGate gate = new InFlightGate(2, 0);

        assertThat(gate.acquire("A")).isNotNull();
        assertThat(gate.acquire("B")).isNotNull();
        assertThat(gate.inFlight()).isEqualTo(2);

        long startedAt = System.nanoTime();
        assertThat(gate.acquire("C")).isNull(); // 等待上限 0 秒：立即放弃并交给调用方明确失败
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(5));

        // 被拒的申请不得改变在飞计数
        assertThat(gate.inFlight()).isEqualTo(2);
    }

    @Test
    void releasedLeaseLetsAQueuedWaiterIn() throws Exception {
        InFlightGate gate = new InFlightGate(1, 10);
        InFlightGate.Lease held = gate.acquire("先到");

        AtomicReference<InFlightGate.Lease> queuedLease = new AtomicReference<>();
        CountDownLatch queuedThreadStarted = new CountDownLatch(1);
        Thread queued = new Thread(() -> {
            queuedThreadStarted.countDown();
            queuedLease.set(gate.acquire("排队"));
        });
        queued.start();
        assertThat(queuedThreadStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200); // 让排队线程真正进入信号量等待，而不是还没到 acquire

        assertThat(queuedLease.get()).isNull();

        held.close();
        queued.join(5_000);

        assertThat(queuedLease.get()).isNotNull();
        queuedLease.get().close();
        assertThat(gate.inFlight()).isZero();
    }

    @Test
    void leaseCloseIsIdempotentSoTheGateNeverWidensByAccident() {
        InFlightGate gate = new InFlightGate(1, 0);
        InFlightGate.Lease lease = gate.acquire("重复释放");

        lease.close();
        lease.close(); // 第二次释放必须是空操作

        assertThat(gate.inFlight()).isZero();
        // 幂等性真正要守的是许可总数：多释放一次就会凭空多出一个名额
        assertThat(gate.acquire("占位")).isNotNull();
        assertThat(gate.acquire("仍应被拒")).isNull();
    }

    @Test
    void illegalLimitFallsBackToDefaultAndNegativeWaitClampsToZero() {
        InFlightGate gate = new InFlightGate(0, -5);

        assertThat(gate.limit()).isEqualTo(InFlightGate.DEFAULT_LIMIT);
        assertThat(gate.acquireTimeoutSeconds()).isZero();
    }

    @Test
    void defaultsMatchApplicationConfigurationContract() {
        // 与 application.yaml 的 app.llm.max-in-flight / acquire-timeout-seconds 默认值同源：
        // 改这里必须同步改 YAML（上限须低于上游网关的并发上限 6）
        assertThat(InFlightGate.DEFAULT_LIMIT).isEqualTo(4);
        assertThat(InFlightGate.DEFAULT_ACQUIRE_TIMEOUT_SECONDS).isEqualTo(60L);
    }
}
