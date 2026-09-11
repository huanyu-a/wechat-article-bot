package ink.icoding.wechat.article.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
 * <p>边界：进程内信号量是**每实例**的。Quartz 以 {@code isClustered=true} 多实例部署时，
 * N 个实例合计仍可能超过网关配额，此时需要换成全局配额（Redis 等）。
 */
@Component
public class InFlightGate {
    private static final Logger log = LoggerFactory.getLogger(InFlightGate.class);

    /** 配置缺失或非法时的上限（与 application.yaml 的默认值一致）。 */
    public static final int DEFAULT_LIMIT = 4;
    /** 配置缺失时的排队等待上限（秒）。 */
    public static final long DEFAULT_ACQUIRE_TIMEOUT_SECONDS = 60L;

    private final int limit;
    private final long acquireTimeoutSeconds;
    private final Semaphore permits;
    private final AtomicInteger inFlight = new AtomicInteger();

    public InFlightGate(
            @Value("${app.llm.max-in-flight:" + DEFAULT_LIMIT + "}") int limit,
            @Value("${app.llm.acquire-timeout-seconds:" + DEFAULT_ACQUIRE_TIMEOUT_SECONDS + "}")
            long acquireTimeoutSeconds) {
        this.limit = limit <= 0 ? DEFAULT_LIMIT : limit;
        this.acquireTimeoutSeconds = Math.max(0L, acquireTimeoutSeconds);
        this.permits = new Semaphore(this.limit, true);
        log.info("LLM 在飞会话闸门上限 {}，排队等待上限 {} 秒", this.limit, this.acquireTimeoutSeconds);
    }

    public int limit() {
        return limit;
    }

    /** 排队等待上限（秒），失败信息里要把它讲清楚，否则用户不知道「等了多久」。 */
    public long acquireTimeoutSeconds() {
        return acquireTimeoutSeconds;
    }

    /** 当前在飞运行数（用于日志与验收观测）。 */
    public int inFlight() {
        return inFlight.get();
    }

    /**
     * 取得一个名额，最多等待 {@link #acquireTimeoutSeconds} 秒。
     *
     * @param what 申请方描述（如「任务 #12 的运行 #34」），仅用于日志
     * @return 名额租约；等待超时返回 {@code null}，由调用方给出**明确失败**（绝不静默卡住）
     */
    public Lease acquire(String what) {
        long startedAt = System.nanoTime();
        boolean acquired = false;
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
        long waitedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (waitedMillis >= 1_000L) {
            // 排队说明闸门确实在生效，且给出「排了多久」便于判断上游是否被别的实例/链路占满
            log.info("{}取得 LLM 并发名额（排队 {} ms，在飞 {}/{}）",
                    what == null ? "" : what + " ", waitedMillis, now, limit);
        }
        return new Lease(what);
    }

    /**
     * 名额租约。{@link #close()} 幂等——运行收尾路径（try/finally 与异常分支）可能重复释放，
     * 而多释放会让信号量凭空增加许可，使闸门失效。
     */
    public final class Lease implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();
        private final String what;

        private Lease(String what) {
            this.what = what;
        }

        @Override
        public void close() {
            if (!released.compareAndSet(false, true)) return;
            int now = inFlight.decrementAndGet();
            permits.release();
            if (now < 0) {
                // 只可能来自重复释放（已由上面的 CAS 拦住）或未持有就释放，属编程错误
                log.error("{}释放 LLM 并发名额后计数为负（{}），存在未配对释放", what == null ? "" : what + " ", now);
            }
        }
    }
}
