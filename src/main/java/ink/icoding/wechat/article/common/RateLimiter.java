package ink.icoding.wechat.article.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量进程内限流器（滑动窗口计数）。
 *
 * 用途：保护会触发外部出网调用的高成本接口（如 /api/skills/preview 在 MARKFLOW 技能下会拉取语法 guide）。
 * 单实例内存计数，多实例部署下为「每实例限流」——对本项目规模足够；如需全局配额应换成 Redis 计数器。
 *
 * 容量保护：窗口按 key（当前实现为「接口名:用户 id」）累积，规模本就有界；仍在每次检查时做一次
 * 廉价的机会式清理（仅当 key 数超过阈值时），避免长期运行下无界增长。
 */
@Component
public class RateLimiter {
    /** 超过该 key 数量才触发机会式清理，避免每次请求都遍历 map。 */
    private static final int EVICT_THRESHOLD = 512;
    /** 空闲超过该时长的窗口视为可回收。 */
    private static final Duration IDLE_TTL = Duration.ofMinutes(30);

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** 在 {@code window} 时间窗内最多允许 {@code maxRequests} 次；超限抛 BusinessException(429)。 */
    public void check(String key, int maxRequests, Duration window) {
        long windowMillis = window.toMillis();
        long now = System.currentTimeMillis();
        Window bucket = windows.computeIfAbsent(key, ignored -> new Window());
        synchronized (bucket) {
            if (now - bucket.windowStart.get() >= windowMillis) {
                bucket.windowStart.set(now);
                bucket.count.set(0);
            }
            if (bucket.count.incrementAndGet() > maxRequests) {
                throw new BusinessException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "操作过于频繁，请稍后再试");
            }
        }
        if (windows.size() > EVICT_THRESHOLD) evictIdle(IDLE_TTL);
    }

    /** 清理长时间未使用的窗口（避免 key 无限增长）。 */
    public void evictIdle(Duration idle) {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart.get() >= idle.toMillis());
    }

    public int trackedKeys() {
        return windows.size();
    }

    private static final class Window {
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong count = new AtomicLong();
    }
}
