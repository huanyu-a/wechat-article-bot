package ink.icoding.wechat.article.schedule;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskRunMapper extends SmartMapper<TaskRun> {
    default int finishRun(TaskRun changes) {
        TaskRun run = selectById(changes.getId());
        if (run == null) return 0;
        run.setStatus(changes.getStatus());
        run.setFetchedCount(changes.getFetchedCount());
        run.setGeneratedCount(changes.getGeneratedCount());
        run.setArticleId(changes.getArticleId());
        run.setToolCallCount(changes.getToolCallCount());
        run.setMode(changes.getMode());
        run.setStagesSummary(changes.getStagesSummary());
        run.setMessage(changes.getMessage());
        run.setExecutionLog(changes.getExecutionLog());
        run.setFinishedAt(LocalDateTime.now());
        return updateById(run);
    }

    default List<TaskRun> findRuns(Long taskId) {
        return select(Where.where(TaskRun::getTaskId).eq(taskId)
                .orderBy(TaskRun::getStartedAt).desc().limit(100));
    }

    default List<TaskRun> findRecentRuns(int limit) {
        return select(Where.where().orderBy(TaskRun::getStartedAt).desc().limit(Math.max(1, limit)));
    }

    default TaskRun findRunning(Long taskId) {
        List<TaskRun> runs = select(Where.where(TaskRun::getTaskId).eq(taskId)
                .and(TaskRun::getStatus).eq("RUNNING")
                .orderBy(TaskRun::getStartedAt).desc().limit(1));
        return runs.isEmpty() ? null : runs.get(0);
    }

    /**
     * 同一任务最早的一条 RUNNING 运行（多实例并发占位的仲裁依据：ID 单调递增，
     * 同时插入的两条运行里小 ID 者为唯一属主）。
     */
    default TaskRun findEarliestRunning(Long taskId) {
        List<TaskRun> runs = select(Where.where(TaskRun::getTaskId).eq(taskId)
                .and(TaskRun::getStatus).eq("RUNNING")
                .orderBy(TaskRun::getId).asc().limit(1));
        return runs.isEmpty() ? null : runs.get(0);
    }

    /** 全部仍在运行的行（启动自愈扫描用）；单次扫描量级 = 任务数，可全量取回。 */
    default List<TaskRun> findAllRunning() {
        return select(Where.where(TaskRun::getStatus).eq("RUNNING"));
    }

    /**
     * 定点中止一条遗留运行：只写 STATUS/MESSAGE/FINISHED_AT 三列，不再整实体回写
     * （整实体回写要求调用方先加载完整行，且一旦传入的是部分实体就会把其余列写成 null）。
     *
     * <p>{@code AND STATUS = 'RUNNING'} 让这次中止成为一次**比较并交换**：扫描到判定为孤儿之间，
     * 该运行若已被执行线程正常收尾（SUCCESS/FAILED），这里不会把它的终态覆盖成「孤儿中止」，
     * 返回 0 即表示已有属主处理过。
     *
     * <p>时间戳必须用 **JVM 时间**而不是数据库 {@code NOW(6)}：本列是 varchar，
     * {@code STARTED_AT} 与正常收尾的 {@code FINISHED_AT} 都由 smart-mybatis 写 JVM 的
     * {@code LocalDateTime}，而本机 MySQL 会话时区是 UTC（实测 {@code NOW()}=07:14 对本机 15:14），
     * 用 {@code NOW(6)} 会让被中止的运行出现**负时长**（如 STARTED_AT 13:03 / FINISHED_AT 05:19）。
     * 显式按 smart-mybatis 的存储格式格式化，既与其它写入源一致，又不依赖驱动对 LocalDateTime 的转换。
     */
    default int abortStale(Long id, String message, LocalDateTime finishedAt) {
        return executeSql("UPDATE TASK_RUN SET STATUS = ?, MESSAGE = ?, FINISHED_AT = ? "
                + "WHERE ID = ? AND STATUS = ?",
                "FAILED", message, formatTimestamp(finishedAt), id, "RUNNING");
    }

    /** smart-mybatis 写 datetime 列（varchar）时的存储格式，供定点 SQL 复用。 */
    static String formatTimestamp(LocalDateTime value) {
        return value == null ? null
                : value.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
    }

    /**
     * 运行中进度落库（I10）：周期刷新 MODE / EXECUTION_LOG / TOOL_CALL_COUNT 与心跳（I2）。
     * {@code AND STATUS = 'RUNNING'} 保证已收尾的行不被运行中的快照覆盖。
     */
    default int updateProgress(Long id, String mode, String executionLog, Integer toolCallCount,
                               LocalDateTime heartbeatAt) {
        return executeSql("UPDATE TASK_RUN SET MODE = ?, EXECUTION_LOG = ?, TOOL_CALL_COUNT = ?, "
                        + "HEARTBEAT_AT = ? WHERE ID = ? AND STATUS = ?",
                mode, executionLog, toolCallCount, formatTimestamp(heartbeatAt), id, "RUNNING");
    }

    /** 仅刷新心跳（I2）：用于进度无变化但仍需宣告属主存活的周期。 */
    default int updateHeartbeat(Long id, LocalDateTime heartbeatAt) {
        return executeSql("UPDATE TASK_RUN SET HEARTBEAT_AT = ? WHERE ID = ? AND STATUS = ?",
                formatTimestamp(heartbeatAt), id, "RUNNING");
    }
}
