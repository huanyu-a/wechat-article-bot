package ink.icoding.wechat.article.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 轻量限流器单测：窗口内计数、超限 429、不同 key 互不影响、空闲清理。
 */
class RateLimiterTest {
    private final RateLimiter limiter = new RateLimiter();

    @Test
    void allowsUpToLimitThenRejects() {
        for (int i = 0; i < 3; i++) {
            assertThatCode(() -> limiter.check("k1", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> limiter.check("k1", 3, Duration.ofMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过于频繁");
    }

    @Test
    void separateKeysAreIndependent() {
        limiter.check("a", 1, Duration.ofMinutes(1));
        assertThatThrownBy(() -> limiter.check("a", 1, Duration.ofMinutes(1)))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> limiter.check("b", 1, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }

    @Test
    void windowResetsAfterExpiry() throws Exception {
        limiter.check("w", 1, Duration.ofMillis(50));
        assertThatThrownBy(() -> limiter.check("w", 1, Duration.ofMillis(50)))
                .isInstanceOf(BusinessException.class);
        Thread.sleep(80);
        assertThatCode(() -> limiter.check("w", 1, Duration.ofMillis(50))).doesNotThrowAnyException();
    }

    @Test
    void idleKeysAreEvicted() throws Exception {
        limiter.check("idle", 5, Duration.ofMillis(50));
        assertThat(limiter.trackedKeys()).isEqualTo(1);
        Thread.sleep(80);
        limiter.evictIdle(Duration.ofMillis(50));
        assertThat(limiter.trackedKeys()).isZero();
    }
}
