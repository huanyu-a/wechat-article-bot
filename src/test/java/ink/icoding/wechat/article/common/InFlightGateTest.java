package ink.icoding.wechat.article.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void databaseLeaseSharesQuotaAcrossInstancesAndFreesOnRelease() {
        // I1：两个「实例」共用同一套租约表（同一 mock 模拟共享库），实例 A 占住唯一名额后，
        // 实例 B 必须被拒——这正是单实例信号量守不住的（N 个实例各放 4 个就冲破网关上限）。
        LlmLeaseMapper mapper = mock(LlmLeaseMapper.class);
        AtomicReference<String> ownerToken = new AtomicReference<>();
        when(mapper.tryOccupy(anyLong(), anyString(), any(), any(LocalDateTime.class)))
                .thenAnswer(call -> ownerToken.compareAndSet(null, call.getArgument(1)));
        when(mapper.releaseByToken(anyString()))
                .thenAnswer(call -> ownerToken.compareAndSet(call.getArgument(0), null) ? 1 : 0);
        when(mapper.reclaimExpired(any(LocalDateTime.class))).thenReturn(0);

        InFlightGate first = new InFlightGate(1, 0, mapper, 120);
        InFlightGate second = new InFlightGate(1, 0, mapper, 120);
        try {
            InFlightGate.Lease held = first.acquire("实例A");
            assertThat(held).isNotNull();
            assertThat(second.acquire("实例B")).isNull();

            held.close();
            // 释放后名额回到共享池，另一实例才拿得到
            InFlightGate.Lease retried = second.acquire("实例B 重试");
            assertThat(retried).isNotNull();
            retried.close();
            verify(mapper, atLeastOnce()).releaseByToken(anyString());
        } finally {
            first.shutdownHeartbeats();
            second.shutdownHeartbeats();
        }
    }

    @Test
    void transientDatabaseBlipIsRetriedInsteadOfDroppingToLocalMode() {
        // 单次抖动若立刻降级，后面的实例就不再受跨实例上限约束（可能超发）。重试一次后成功则应保持租约模式。
        LlmLeaseMapper mapper = mock(LlmLeaseMapper.class);
        // 首次调用抛一次瞬断，之后按「槽位是否已被占用」如实回答（只有一个槽位）
        AtomicBoolean blip = new AtomicBoolean(true);
        AtomicReference<String> owner = new AtomicReference<>();
        when(mapper.tryOccupy(anyLong(), anyString(), any(), any(LocalDateTime.class))).thenAnswer(call -> {
            if (blip.compareAndSet(true, false)) throw new RuntimeException("瞬断");
            return owner.compareAndSet(null, call.getArgument(1));
        });
        when(mapper.releaseByToken(anyString()))
                .thenAnswer(call -> owner.compareAndSet(call.getArgument(0), null) ? 1 : 0);
        when(mapper.reclaimExpired(any(LocalDateTime.class))).thenReturn(0);

        InFlightGate first = new InFlightGate(1, 0, mapper, 120);
        InFlightGate second = new InFlightGate(1, 0, mapper, 120);
        try {
            InFlightGate.Lease lease = first.acquire("实例A");
            assertThat(lease).isNotNull();
            // 仍是数据库租约模式：第二个实例拿不到名额
            assertThat(second.acquire("实例B")).isNull();
            lease.close();
        } finally {
            first.shutdownHeartbeats();
            second.shutdownHeartbeats();
        }
    }

    @Test
    void databaseFailureDegradesToInProcessGateRatherThanAdmittingUnbounded() {
        // 数据库不可用时不能「放行让所有请求裸奔」，而是降级为进程内闸门——至少守住本实例的上限
        LlmLeaseMapper mapper = mock(LlmLeaseMapper.class);
        when(mapper.tryOccupy(anyLong(), anyString(), any(), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("db down"));
        when(mapper.reclaimExpired(any(LocalDateTime.class))).thenThrow(new RuntimeException("db down"));

        InFlightGate gate = new InFlightGate(1, 0, mapper, 120);
        try {
            InFlightGate.Lease lease = gate.acquire("降级");
            assertThat(lease).isNotNull();
            assertThat(gate.acquire("降级后仍应被拒")).isNull();
            lease.close();
        } finally {
            gate.shutdownHeartbeats();
        }
    }

    /**
     * 上一轮已实证的窗口：实例崩溃占满槽位时租约的心跳还新鲜（早于 TTL），申请开始时的首次回收
     * 扫不出东西；旧实现于是在轮询里反复 {@code tryOccupy} 直到等满超时并明确失败，名额要等
     * 「TTL 已过之后的下一次申请」才归还。修好后等待期间周期性回收，心跳在本次等待内跨过
     * TTL 的崩溃租约被就地回收，**同一轮申请**就能拿到槽位。
     *
     * <p>TTL 取 5 秒、等待上限取 10 秒：崩溃心跳写于申请前 1 秒，正好在第 5 秒前后跨过 TTL，
     * 因而首次回收（申请开始）必然无功而返，只有等满 5 秒间隔后的那次在环内回收才起效。
     */
    @Test
    void crashedLeaseCrossingTtlDuringTheWaitIsReclaimedWithinTheSameAcquire() {
        // 按 LlmLeaseMapper 的真实语义复刻租约表：槽位被占用时 tryOccupy 返回 false；
        // reclaimExpired(before) 删除 HEARTBEAT_AT 早于 before 的行，之后槽位可被重新占用。
        AtomicReference<LocalDateTime> heartbeatAt = new AtomicReference<>();
        List<String> reclaimResults = new ArrayList<>();
        LlmLeaseMapper mapper = mock(LlmLeaseMapper.class);
        when(mapper.tryOccupy(anyLong(), anyString(), any(), any(LocalDateTime.class))).thenAnswer(call -> {
            if (heartbeatAt.get() != null) return false;
            heartbeatAt.set(call.getArgument(3));
            return true;
        });
        when(mapper.releaseByToken(anyString())).thenAnswer(call -> {
            heartbeatAt.set(null);
            return 1;
        });
        when(mapper.reclaimExpired(any(LocalDateTime.class))).thenAnswer(call -> {
            LocalDateTime before = call.getArgument(0);
            LocalDateTime current = heartbeatAt.get();
            if (current != null && current.isBefore(before)) {
                heartbeatAt.set(null);
                reclaimResults.add("reclaimed");
                return 1;
            }
            reclaimResults.add("kept");
            return 0;
        });
        when(mapper.heartbeat(anyString(), any(LocalDateTime.class))).thenAnswer(call -> {
            heartbeatAt.set(call.getArgument(1));
            return 1;
        });

        long ttlSeconds = 5L;
        int acquireTimeoutSeconds = 10;
        InFlightGate gate = new InFlightGate(1, acquireTimeoutSeconds, mapper, ttlSeconds);
        try {
            // 崩溃实例在申请开始前一瞬留下的心跳：此刻尚未过期（晚于 now - TTL，躲过首次回收）
            heartbeatAt.set(LocalDateTime.now().minusSeconds(1));

            long startedAt = System.nanoTime();
            InFlightGate.Lease lease = gate.acquire("崩溃后重试");
            long waited = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            // 名额在同一轮申请内归还并取得，而不是等满超时后明确失败（旧行为）
            assertThat(lease).isNotNull();
            assertThat(waited).isLessThan(TimeUnit.SECONDS.toMillis(acquireTimeoutSeconds));
            // 证据链：申请开始时的首次回收无功而返，名额是等待期间的周期性回收释放的
            assertThat(reclaimResults).containsExactly("kept", "reclaimed");
            lease.close();
            assertThat(gate.inFlight()).isZero();
        } finally {
            gate.shutdownHeartbeats();
        }
    }
}
