package ink.icoding.wechat.article.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 遗留运行判定策略单测：并发闸门（findRunning）被崩溃进程永久占住时的自愈依据。
 * 阈值必须只放行「可证明已废弃」的运行，Cluster 部署下不能误杀别的实例正在跑的任务。
 */
class StaleRunPolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 12, 0);

    @Test
    void nullRunIsNotStale() {
        assertThat(StaleRunPolicy.isStale(null, 6, NOW)).isFalse();
    }

    @Test
    void runWithoutStartedAtIsNotStale() {
        // 无法证明其已废弃时不动它（保守判定，避免误杀）
        assertThat(StaleRunPolicy.isStale(run(null), 6, NOW)).isFalse();
    }

    @Test
    void freshRunIsNotStale() {
        assertThat(StaleRunPolicy.isStale(run(NOW.minusHours(1)), 6, NOW)).isFalse();
    }

    @Test
    void oldRunIsStale() {
        assertThat(StaleRunPolicy.isStale(run(NOW.minusHours(7)), 6, NOW)).isTrue();
    }

    @Test
    void boundaryIsNotStale() {
        // 恰好等于阈值不算过期（isBefore 为严格小于）
        assertThat(StaleRunPolicy.isStale(run(NOW.minusHours(6)), 6, NOW)).isFalse();
    }

    @Test
    void subHourThresholdFallsBackToOneHour() {
        // 阈值配置小于 1 小时时按 1 小时兜底，防止把正常运行误判为孤儿
        assertThat(StaleRunPolicy.isStale(run(NOW.minusMinutes(30)), 0, NOW)).isFalse();
        assertThat(StaleRunPolicy.isStale(run(NOW.minusHours(2)), 0, NOW)).isTrue();
    }

    @Test
    void defaultThresholdExceedsWorstCaseRun() {
        // 默认阈值必须大于「阶段超时 × 阶段数上界」，否则正常长任务会被误杀
        long worst = StaleRunPolicy.worstCaseRunSeconds(300);
        assertThat(worst).isEqualTo(19L * 300L);
        assertThat(StaleRunPolicy.thresholdTooSmall(StaleRunPolicy.DEFAULT_STALE_HOURS, 300)).isFalse();
    }

    @Test
    void smallThresholdIsFlaggedAsTooSmall() {
        // 300s 阶段超时下，一次合法运行最长 5700s ≈ 1.6h：1h 阈值会误杀正常长任务，2h 起才安全
        assertThat(StaleRunPolicy.thresholdTooSmall(1, 300)).isTrue();
        assertThat(StaleRunPolicy.thresholdTooSmall(2, 300)).isFalse();
    }

    private static TaskRun run(LocalDateTime startedAt) {
        TaskRun run = new TaskRun();
        run.setId(1L);
        run.setTaskId(2L);
        run.setStatus("RUNNING");
        run.setStartedAt(startedAt);
        return run;
    }
}
