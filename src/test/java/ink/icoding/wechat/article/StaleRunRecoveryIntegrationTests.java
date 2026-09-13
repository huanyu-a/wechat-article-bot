package ink.icoding.wechat.article;

import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.schedule.StaleRunPolicy;
import ink.icoding.wechat.article.schedule.TaskExecutionService;
import ink.icoding.wechat.article.schedule.TaskRun;
import ink.icoding.wechat.article.schedule.TaskRunMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 遗留运行自愈的集成验证（并发闸门 {@code findRunning} 被崩溃进程永久占住时的恢复路径）。
 *
 * <p>单测（{@code StaleRunPolicyTest}）只覆盖判定函数，这里补它落库之后的行为：中止真的写进 task_run、
 * 闸门真的释放 / 真的守住。这条路径此前完全没有测试覆盖，而它正是「进程崩溃后任务再也触发不了」的修复点。
 *
 * <p>断言闸门状态而不真的触发执行——{@code start()} 会异步调用模型，测试不应依赖外部服务；
 * 「闸门释放」等价于「下一次触发会被放行」，这正是修复的目标。
 */
@SpringBootTest
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class StaleRunRecoveryIntegrationTests {
    @Autowired
    private TaskExecutionService executionService;

    @Autowired
    private TaskRunMapper runMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long taskId;

    @AfterEach
    void cleanUp() {
        if (taskId != null) {
            jdbcTemplate.update("DELETE FROM TASK_RUN WHERE TASK_ID = ?", taskId);
            jdbcTemplate.update("DELETE FROM SCHEDULE_TASK WHERE ID = ?", taskId);
            taskId = null;
        }
    }

    @Test
    void reapsStaleRunAndReleasesGateButKeepsFreshRun() {
        Long staleId = insertRunning(LocalDateTime.now().minusHours(24));
        Long freshId = insertRunning(LocalDateTime.now().minusMinutes(1));

        int aborted = executionService.reapStaleRuns();

        assertThat(aborted).isGreaterThanOrEqualTo(1);
        TaskRun stale = runMapper.selectById(staleId);
        assertThat(stale.getStatus()).isEqualTo("FAILED");
        assertThat(stale.getMessage()).isEqualTo(StaleRunPolicy.ABORT_MESSAGE);
        assertThat(stale.getFinishedAt()).isNotNull();
        // 未超阈值的运行不得被误伤：集群部署下那可能是别的实例正在跑的任务
        TaskRun fresh = runMapper.selectById(freshId);
        assertThat(fresh.getStatus()).isEqualTo("RUNNING");
        assertThat(fresh.getMessage()).isNull();
    }

    @Test
    void freshRunningRunStillBlocksNewTrigger() {
        insertRunning(LocalDateTime.now());

        assertThatThrownBy(() -> executionService.start(taskId, "MANUAL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务正在执行");
    }

    @Test
    void staleRunningRunNoLongerBlocksTrigger() {
        Long staleId = insertRunning(LocalDateTime.now().minusHours(24));

        executionService.reapStaleRuns();

        assertThat(runMapper.selectById(staleId).getStatus()).isEqualTo("FAILED");
        // 闸门已释放：修复前这里会一直返回那条遗留 RUNNING 行，任务永久不可触发
        assertThat(runMapper.findRunning(taskId)).isNull();
    }

    @Test
    void stoppedHeartbeatReapsRunLongBeforeTheTimeThreshold() {
        // I2：开始仅 1 分钟（时间阈值 3h 远未到），但心跳已停 200 秒 → 属主失联，应即刻中止
        Long id = insertRunning(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusSeconds(200));

        assertThat(executionService.reapStaleRuns()).isGreaterThanOrEqualTo(1);

        assertThat(runMapper.selectById(id).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void liveHeartbeatProtectsLongRunningRunAcrossInstances() {
        // I2 的反例：开始于 24 小时前但心跳新鲜 = 属主实例还活着（多实例下的长任务），绝不能误杀
        Long id = insertRunning(LocalDateTime.now().minusHours(24), LocalDateTime.now());

        executionService.reapStaleRuns();

        assertThat(runMapper.selectById(id).getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void progressFlushPersistsLiveFieldsAndStopsAfterFinish() {
        // I10：RUNNING 期间也能读出 MODE/EXECUTION_LOG/TOOL_CALL_COUNT，而不是收尾前永远是默认值
        Long id = insertRunning(LocalDateTime.now());

        assertThat(runMapper.updateProgress(id, "PIPELINE", "【调研】启动智能体", 7, LocalDateTime.now()))
                .isEqualTo(1);
        TaskRun running = runMapper.selectById(id);
        assertThat(running.getMode()).isEqualTo("PIPELINE");
        assertThat(running.getExecutionLog()).isEqualTo("【调研】启动智能体");
        assertThat(running.getToolCallCount()).isEqualTo(7);
        assertThat(running.getHeartbeatAt()).isNotNull();

        TaskRun finished = runMapper.selectById(id);
        finished.setStatus("SUCCESS");
        runMapper.finishRun(finished);
        // 收尾后运行中的快照不得再覆盖终态（AND STATUS='RUNNING' 的 CAS）
        assertThat(runMapper.updateProgress(id, "SINGLE", "迟到的快照", 99, LocalDateTime.now())).isZero();
        assertThat(runMapper.selectById(id).getExecutionLog()).isEqualTo("【调研】启动智能体");
    }

    private Long insertRunning(LocalDateTime startedAt) {
        return insertRunning(startedAt, null);
    }

    private Long insertRunning(LocalDateTime startedAt, LocalDateTime heartbeatAt) {
        TaskRun run = new TaskRun();
        run.setTaskId(requiredTaskId());
        run.setTriggerType("MANUAL");
        run.setStatus("RUNNING");
        run.setFetchedCount(0);
        run.setGeneratedCount(0);
        run.setToolCallCount(0);
        run.setStartedAt(startedAt);
        run.setHeartbeatAt(heartbeatAt);
        runMapper.insert(run);
        return run.getId();
    }

    /** 每次用专属任务（ENABLED=0，不参与 Quartz 调度），避免依赖种子数据与其他用例残留。 */
    private Long requiredTaskId() {
        if (taskId == null) {
            jdbcTemplate.update("""
                    INSERT INTO SCHEDULE_TASK (NAME, CRON_EXPRESSION, TIMEZONE, AI_PROMPT, OUTPUT_MODE,
                                               ENABLED, CREATED_BY, CREATED_AT, UPDATED_AT)
                    VALUES ('遗留运行自愈测试', '0 0 9 * * ?', 'Asia/Shanghai', '测试', 'LOCAL_DRAFT',
                            0, 1, NOW(), NOW())
                    """);
            taskId = jdbcTemplate.queryForObject(
                    "SELECT ID FROM SCHEDULE_TASK ORDER BY ID DESC LIMIT 1", Long.class);
        }
        return taskId;
    }
}
