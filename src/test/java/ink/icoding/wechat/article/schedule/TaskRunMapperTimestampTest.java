package ink.icoding.wechat.article.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定点 SQL 的时间戳格式单测（2026-09-11 的「运行时长负数」修复）。
 *
 * <p>smart-mybatis 把 datetime 列建成 varchar 并写入 {@code yyyy-MM-dd HH:mm:ss.SSSSSS}，
 * {@code TaskRunMapper.abortStale} 是手写 UPDATE，必须用同一种格式写，否则字符串比较与展示都会错乱。
 * 此前它把时间交给 SQL 的 {@code NOW()}（MySQL 会话时区为 UTC，比 JVM 早 8 小时），
 * 而 STARTED_AT 是 JVM 时间——于是被中止的孤儿运行出现「结束时间早于开始时间」的负时长。
 * 改为传入 JVM 的 {@link LocalDateTime} 后，两端同源。
 */
class TaskRunMapperTimestampTest {

    @Test
    void formatsWithTheSamePatternSmartMybatisWrites() {
        LocalDateTime value = LocalDateTime.of(2026, 9, 11, 7, 14, 5, 123_000_000);

        assertThat(TaskRunMapper.formatTimestamp(value)).isEqualTo("2026-09-11 07:14:05.123000");
    }

    @Test
    void keepsMicrosecondPrecisionSoOrderingStaysUnambiguous() {
        LocalDateTime earlier = LocalDateTime.of(2026, 9, 11, 7, 14, 5, 1_000);
        LocalDateTime later = LocalDateTime.of(2026, 9, 11, 7, 14, 5, 2_000);

        assertThat(TaskRunMapper.formatTimestamp(earlier)).isEqualTo("2026-09-11 07:14:05.000001");
        assertThat(TaskRunMapper.formatTimestamp(later)).isEqualTo("2026-09-11 07:14:05.000002");
    }

    @Test
    void nullStaysNull() {
        assertThat(TaskRunMapper.formatTimestamp(null)).isNull();
    }
}
