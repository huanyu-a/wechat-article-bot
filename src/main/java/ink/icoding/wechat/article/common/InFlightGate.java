package ink.icoding.wechat.article.common;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 在飞会话闸门：限制**同时进行**的智能体运行数，让本应用永远不会把上游网关的并发配额占满。
 *
 * <p>为什么需要（2026-09-11 事故）：上游网关 {@code nexus.bx9y.com.cn} 对 {@code agnes-3.0-flash}
 * 只允许 6 个并发请求（实测报文 {@code HTTP 429 concurrent limit exceeded: running=7 max=6}），
 * 而 agent4j 2.3.3 既无读超时也无取消接口（{@code LLMModel.create} 不暴露超时，
 * {@code OpenAIChatModel.client} 为 final 自建，{@code AgentSessionResult} 无 cancel/close）。
 * 于是一次「流被掐断但既不回调 onFailure 也不关闭」的停滞会永久占住一个名额，级联成
 * 「卡一个 → 少一个名额 → 下一个更易卡」，直到运维手工改库清场。前置闸门的价值在于：
 * **配额永远由本应用自己守住**，外部抖动最多让某次运行变慢或明确失败，不会级联。
 *
 * <p>为什么闸门加在「运行」而不是「会话」上（与方案初稿的差异，依据是字节码而非推断）：
 * {@code OpenAIChatModel.handleToolCallsAndContinue} 在执行完工具后**递归调用**
 * {@code executeAgentLoop} 发起新一轮请求（{@code javap} 偏移 231 执行工具、293 递归），
 * 即每一轮的 SSE 流都先正常收尾、再执行工具。所以一次运行任意时刻**最多只有一个在飞请求**，
 * 委托子智能体期间父会话的流已经关闭、并不占用配额。若按会话逐个取名额，父子同时持牌会
 * 把「父会话已不占配额」误算成占配额，且父等子取不到牌时形成自锁；
 * 按运行取一个名额则天然可重入（嵌套会话在同一个运行内），既无自锁又不高估。
 *
 * <p>代价（有意接受）：名额在整个运行期间持有，包含渲染/落库等非 LLM 阶段，
 * 因而略微保守。上限默认 4 与网关 6 之间的余量留给编辑器链路与手工触发。
 *
 * <p>跨实例（I1）：Spring 注入 {@link LlmLeaseMapper} 时改为**数据库租约**模式——固定槽位表
 * （{@link LlmLease}）保证多实例合计不超过上限；租约带 TTL 心跳，实例崩溃后其它实例可回收名额。
 * 回收不只发生在申请开始时：等待期间还会周期性回收，使心跳在本次等待内跨过 TTL 的崩溃名额
 * 能在同一轮申请里归还，而不是让这次申请白等满超时。
 * 无 Mapper（单测直接构造）时退回进程内信号量，行为与旧实现完全一致。
 */
@Component
public class InFlightGate {
    private static final Logger log = LoggerFactory.getLogger(InFlightGate.class);

    /** 配置缺失或非法时的上限（与 application.yaml 的默认值一致）。 */
    public static final int DEFAULT_LIMIT = 4;
    /** 配置缺失时的排队等待上限（秒）。 */
    public static final long DEFAULT_ACQUIRE_TIMEOUT_SECONDS = 60L;
    /** 配置缺失时的租约 TTL（秒）：心跳早于 TTL 的租约可被其它实例回收。 */
    public static final long DEFAULT_LEASE_TTL_SECONDS = 120L;
    /** DB 租约模式的排队轮询间隔（毫秒）。 */
    private static final long POLL_MILLIS = 250L;
    /**
     * DB 租约模式下，请求等待期间再次回收过期租约的最长间隔（毫秒）。
     *
     * <p>为什么需要：只在申请开始时回收一次存在一个窗口——崩溃实例刚死时它的心跳还新鲜
     * （早于 TTL），首次回收扫不出任何东西，于是这次申请只能在轮询里反复 {@code tryOccupy}
     * 直到等满 {@link #acquireTimeoutSeconds} 秒并明确失败。而崩溃租约的心跳会在**本次等待
     * 期间**跨过 TTL 阈值，名额本来有资格在同一轮申请内归还，而不是拖到下一次申请。
     *
     * <p>为什么取 5 秒：既要远小于默认等待上限 60 秒，让「跨过阈值 → 发现 → 占回槽位」留足
     * 余量；又不能小到每次轮询（250 毫秒）都打一条 DELETE——等待中的每个线程都在跑这条路径，
     * 一次 LLM 高峰会有多个实例多个线程同时轮询。5 秒与心跳的最小间隔
     * （{@code Math.max(5, ttl/3)}）同量级，属本类已认定为「可承受的周期性 DB tick」。
     */
    private static final long RECLAIM_INTERVAL_MILLIS = 5_000L;

    private final int limit;
    private final long acquireTimeoutSeconds;
    /** 数据库租约模式的 Mapper；为 null 时退回进程内信号量。 */
    private final LlmLeaseMapper leaseMapper;
    private final long leaseTtlSeconds;
    private final Semaphore permits;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final ScheduledExecutorService heartbeats;

    /** 进程内模式（单测直接构造；Spring 不会用这个）。 */
    public InFlightGate(int limit, long acquireTimeoutSeconds) {
        this(limit, acquireTimeoutSeconds, null, DEFAULT_LEASE_TTL_SECONDS);
    }

    @Autowired
    public InFlightGate(
            @Value("${app.llm.max-in-flight:" + DEFAULT_LIMIT + "}") int limit,
            @Value("${app.llm.acquire-timeout-seconds:" + DEFAULT_ACQUIRE_TIMEOUT_SECONDS + "}")
            long acquireTimeoutSeconds,
            LlmLeaseMapper leaseMapper,
            @Value("${app.llm.lease-ttl-seconds:" + DEFAULT_LEASE_TTL_SECONDS + "}")
            long leaseTtlSeconds) {
        this.limit = limit <= 0 ? DEFAULT_LIMIT : limit;
        this.acquireTimeoutSeconds = Math.max(0L, acquireTimeoutSeconds);
        this.leaseMapper = leaseMapper;
        this.leaseTtlSeconds = leaseTtlSeconds <= 0 ? DEFAULT_LEASE_TTL_SECONDS : leaseTtlSeconds;
        this.permits = new Semaphore(this.limit, true);
        // 心跳用线程池而非单线程：DB 抖动（某次 UPDATE 阻塞）不能让所有租约的心跳排队延后——
        // 心跳一旦晚于 TTL，别的实例就会判定本实例已崩溃并回收名额，此时本实例仍在调用网关，
        // 两侧同时持牌即超发。两条线程足以让一次阻塞不拖累其余租约。
        this.heartbeats = leaseMapper == null ? null : Executors.newScheduledThreadPool(2, task -> {
            Thread thread = new Thread(task, "llm-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        log.info("LLM 在飞会话闸门上限 {}，排队等待上限 {} 秒，模式 {}，租约 TTL {} 秒",
                this.limit, this.acquireTimeoutSeconds, leaseMapper == null ? "进程内" : "数据库租约", this.leaseTtlSeconds);
    }

    public int limit() {
        return limit;
    }

    /** 排队等待上限（秒），失败信息里要把它讲清楚，否则用户不知道「等了多久」。 */
    public long acquireTimeoutSeconds() {
        return acquireTimeoutSeconds;
    }

    /** 当前实例在飞运行数（用于日志与验收观测）。 */
    public int inFlight() {
        return inFlight.get();
    }

    @PreDestroy
    public void shutdownHeartbeats() {
        if (heartbeats != null) heartbeats.shutdownNow();
    }

    /**
     * 取得一个名额，最多等待 {@link #acquireTimeoutSeconds} 秒。
     *
     * @param what 申请方描述（如「任务 #12 的运行 #34」），仅用于日志
     * @return 名额租约；等待超时返回 {@code null}，由调用方给出**明确失败**（绝不静默卡住）
     */
    public Lease acquire(String what) {
        return leaseMapper == null ? acquireLocal(what) : acquireShared(what);
    }

    /** 进程内信号量：单实例语义，与旧实现逐字一致。 */
    private Lease acquireLocal(String what) {
        long startedAt = System.nanoTime();
        boolean acquired;
        try {
            acquired = permits.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.warn("{}等待 LLM 并发名额时被中断", what == null ? "" : what + " ");
            return null;
        }
        if (!acquired) {
            log.warn("{}等待 LLM 并发名额超过 {} 秒仍未取得（上限 {}，在飞 {}），本次放弃",
                    what == null ? "" : what + " ", acquireTimeoutSeconds, limit, inFlight.get());
            return null;
        }
        int now = inFlight.incrementAndGet();
        logIfQueued(what, startedAt, now);
        return new Lease(what, null, null);
    }

    /**
     * 数据库租约：跨实例共享上限；实例失联后由 TTL 心跳回收（等待期间还会周期性回收，
     * 间隔见 {@code RECLAIM_INTERVAL_MILLIS}），等满 {@link #acquireTimeoutSeconds} 仍拿不到
     * 名额则明确失败。
     */
    private Lease acquireShared(String what) {
        String token = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        reclaimExpiredQuietly();
        long deadline = startedAt + TimeUnit.SECONDS.toNanos(acquireTimeoutSeconds);
        long reclaimIntervalNanos = TimeUnit.MILLISECONDS.toNanos(RECLAIM_INTERVAL_MILLIS);
        long lastReclaimAt = System.nanoTime();
        while (true) {
            long nowNanos = System.nanoTime();
            // 等待期间周期性回收（含到达 deadline 前的最后一次）：崩溃租约的心跳可能在本次
            // 等待内跨过 TTL 阈值，只在申请开始时回收一次的话，这段窗口里只能反复 tryOccupy
            // 然后等满超时失败。等满超时仍返回 null 的语义不变——这里只是给同一轮申请
            // 多一次就地回收名额的机会，绝不因此延长等待。
            if (nowNanos - lastReclaimAt >= reclaimIntervalNanos || nowNanos >= deadline) {
                reclaimExpiredQuietly();
                lastReclaimAt = nowNanos;
            }
            for (long slot = 1; slot <= limit; slot++) {
                boolean occupied;
                try {
                    occupied = leaseMapper.tryOccupy(slot, token, what, LocalDateTime.now());
                } catch (Exception firstFailure) {
                    // 抖动多为瞬时，先重试一次再谈降级：降级意味着本实例不再受跨实例上限约束
                    try {
                        occupied = leaseMapper.tryOccupy(slot, token, what, LocalDateTime.now());
                    } catch (Exception secondFailure) {
                        // 数据库不可用：降级为进程内闸门，至少守住本实例（否则 LLM 调用完全裸奔）。
                        // 代价必须说清楚：多实例部署下这段时间各实例会各自放行 limit 个，合计可能突破
                        // 上游网关的并发上限——比「完全无限流」轻，但已不是全局硬上限。
                        log.error("{}数据库租约闸门不可用，本次降级为进程内闸门：本实例暂不受跨实例上限约束，"
                                        + "多实例合计可能超过上游并发配额，请尽快排查数据库",
                                what == null ? "" : what + " ", secondFailure);
                        return acquireLocal(what);
                    }
                }
                if (occupied) {
                    Lease lease = new Lease(what, token, slot);
                    lease.heartbeatTask(scheduleHeartbeat(token, slot, what));
                    int now = inFlight.incrementAndGet();
                    logIfQueued(what, startedAt, now);
                    return lease;
                }
            }
            if (System.nanoTime() >= deadline) {
                log.warn("{}等待 LLM 并发名额超过 {} 秒仍未取得（上限 {}，本实例在飞 {}），本次放弃",
                        what == null ? "" : what + " ", acquireTimeoutSeconds, limit, inFlight.get());
                return null;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.warn("{}等待 LLM 并发名额时被中断", what == null ? "" : what + " ");
                return null;
            }
        }
    }

    private void reclaimExpiredQuietly() {
        try {
            int reclaimed = leaseMapper.reclaimExpired(LocalDateTime.now().minusSeconds(leaseTtlSeconds));
            if (reclaimed > 0) {
                log.warn("回收了 {} 个心跳超时（>{} 秒）的 LLM 名额租约：属主实例可能已崩溃", reclaimed, leaseTtlSeconds);
            }
        } catch (Exception exception) {
            log.warn("回收过期 LLM 名额租约失败（跳过，不影响本次申请）", exception);
        }
    }

    private ScheduledFuture<?> scheduleHeartbeat(String token, long slotNo, String what) {
        long interval = Math.max(5L, leaseTtlSeconds / 3L);
        return heartbeats.scheduleAtFixedRate(() -> {
            try {
                if (leaseMapper.heartbeat(token, LocalDateTime.now()) > 0) return;
                // 心跳晚于 TTL：本租约已被其它实例按「属主已崩溃」回收，而本实例其实还在调用网关。
                // 不夺回就等于跨实例超发，故尽力重占原槽位；夺不回则如实记错——真正的止血要取消在飞会话，
                // 而 agent4j 没有取消接口（U1）。
                if (leaseMapper.tryOccupy(slotNo, token, what, LocalDateTime.now())) {
                    log.warn("{}的名额租约曾被按 TTL 回收，已重新占回槽位 {}（数据库时延导致心跳迟到）",
                            what == null ? "" : what + " ", slotNo);
                } else {
                    log.error("{}的名额租约已被其它实例占用且槽位 {} 夺回失败：本实例可能在超发，请检查数据库时延",
                            what == null ? "" : what + " ", slotNo);
                }
            } catch (Exception exception) {
                log.warn("刷新 LLM 名额租约心跳失败：{}", token, exception);
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private void logIfQueued(String what, long startedAt, int now) {
        long waitedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (waitedMillis >= 1_000L) {
            // 排队说明闸门确实在生效，且给出「排了多久」便于判断上游是否被别的实例/链路占满
            log.info("{}取得 LLM 并发名额（排队 {} ms，本实例在飞 {}/{}）",
                    what == null ? "" : what + " ", waitedMillis, now, limit);
        }
    }

    /**
     * 名额租约。{@link #close()} 幂等——运行收尾路径（try/finally 与异常分支）可能重复释放，
     * 而多释放会让闸门凭空增加许可（或删到别人的租约），使闸门失效。
     */
    public final class Lease implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();
        private final String what;
        /** 数据库租约令牌；进程内模式为 null。 */
        private final String dbToken;
        private final Long slotNo;
        private volatile ScheduledFuture<?> heartbeatTask;

        private Lease(String what, String dbToken, Long slotNo) {
            this.what = what;
            this.dbToken = dbToken;
            this.slotNo = slotNo;
        }

        /** 记录心跳任务（数据库模式），释放时取消。 */
        private void heartbeatTask(ScheduledFuture<?> task) {
            this.heartbeatTask = task;
        }

        @Override
        public void close() {
            if (!released.compareAndSet(false, true)) return;
            ScheduledFuture<?> task = heartbeatTask;
            if (task != null) task.cancel(false);
            if (dbToken != null) {
                try {
                    leaseMapper.releaseByToken(dbToken);
                } catch (Exception exception) {
                    log.warn("{}释放 LLM 数据库名额失败（TTL 心跳过期后会自动回收）", what == null ? "" : what + " ", exception);
                }
            } else {
                permits.release();
            }
            int now = inFlight.decrementAndGet();
            if (now < 0) {
                // 只可能来自重复释放（已由上面的 CAS 拦住）或未持有就释放，属编程错误
                log.error("{}释放 LLM 并发名额后计数为负（{}），存在未配对释放", what == null ? "" : what + " ", now);
            }
        }

        @Override
        public String toString() {
            return "Lease{" + (dbToken == null ? "local" : "db slot=" + slotNo) + ", " + what + "}";
        }
    }
}
