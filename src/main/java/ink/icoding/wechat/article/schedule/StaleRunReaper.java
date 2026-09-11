package ink.icoding.wechat.article.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动自愈：清理上次进程非正常退出遗留的 RUNNING 运行（判定规则见 {@link StaleRunPolicy}）。
 *
 * <p>触发路径本身也会自愈（{@code TaskExecutionService.createRun} 撞到遗留行时就地中止），
 * 这里的扫描是为了让任务列表在重启后立刻显示正确状态、而不必等到用户下次点击触发。
 *
 * <p>@Order(40)：在种子（10/20/30）之后、Quartz 任务恢复（ApplicationReadyEvent）之前完成，
 * 避免遗留行在恢复出的定时触发上再撞一次闸门。
 */
@Component
@Order(40)
public class StaleRunReaper implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StaleRunReaper.class);

    private final TaskExecutionService executionService;
    private final long staleRunHours;
    private final long stageTimeoutSeconds;

    public StaleRunReaper(TaskExecutionService executionService,
                          @Value("${app.schedule.stale-run-hours:" + StaleRunPolicy.DEFAULT_STALE_HOURS + "}")
                          long staleRunHours,
                          @Value("${app.schedule.stage-timeout-seconds:300}") long stageTimeoutSeconds) {
        this.executionService = executionService;
        this.staleRunHours = staleRunHours;
        this.stageTimeoutSeconds = stageTimeoutSeconds;
    }

    @Override
    public void run(ApplicationArguments args) {
        warnIfThresholdTooSmall();
        try {
            int aborted = executionService.reapStaleRuns();
            if (aborted > 0) {
                log.warn("启动自愈：{} 条遗留 RUNNING 运行已置为 FAILED（上次进程未正常结束）", aborted);
            }
        } catch (Exception exception) {
            // 自愈失败不得阻断启动：任务仍可通过触发路径自愈
            log.warn("启动自愈扫描失败（跳过，不影响启动）", exception);
        }
    }

    /**
     * 阈值偏小会在长任务执行到一半时把它当作孤儿中止，并允许并发触发同一任务。
     * 这里只告警不阻断启动——单实例部署下按需调小阈值是合理运维选择。
     */
    private void warnIfThresholdTooSmall() {
        try {
            if (StaleRunPolicy.thresholdTooSmall(staleRunHours, stageTimeoutSeconds)) {
                log.warn("app.schedule.stale-run-hours={} 偏小：按阶段超时 {} 秒估算，一次合法运行最长约 {} 小时，"
                                + "超过 {} 小时仍运行的任务会被判定为孤儿中止。请调大 stale-run-hours（或调小 stage-timeout-seconds）。",
                        staleRunHours, stageTimeoutSeconds,
                        StaleRunPolicy.worstCaseRunSeconds(stageTimeoutSeconds) / 3600 + 1, staleRunHours);
            }
        } catch (Exception exception) {
            log.debug("阈值自检跳过", exception);
        }
    }
}
