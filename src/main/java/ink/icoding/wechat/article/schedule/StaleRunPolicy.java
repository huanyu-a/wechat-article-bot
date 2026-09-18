package ink.icoding.wechat.article.schedule;

import java.time.LocalDateTime;

/**
 * 遗留运行判定策略（进程非正常退出后的自愈依据）。
 *
 * <p>背景：{@code task_run.findRunning} 是同一任务的并发闸门，运行中的行只有执行线程收尾时才会落终态。
 * 进程被杀/崩溃时收尾代码不执行，该行永久停留在 RUNNING，任务从此无法再被触发
 * （手工与定时触发都会撞上「任务正在执行，请勿重复启动」）。
 *
 * <p>为什么用「时间阈值」而不是「启动时清空全部 RUNNING」：Quartz 以
 * {@code org.quartz.jobStore.isClustered=true} 部署，多实例可能共用一套库，启动时无条件清空会把
 * 另一实例正在执行的运行误判为孤儿，反而打开并发窗口。因此只中止**开始时间已超过阈值**的运行——
 * 单阶段会话有硬超时（{@code app.schedule.stage-timeout-seconds}），正常运行不可能超过阈值。
 * 中止前无法证明其已废弃时一律不动（{@link #isStale} 对缺开始时间返回 false）。
 */
public final class StaleRunPolicy {
    private StaleRunPolicy() {
    }

    /** 遗留运行中止说明，写入 task_run.message。 */
    public static final String ABORT_MESSAGE = "进程重启中止（上次运行未正常结束，启动自愈）";

    /**
     * 默认阈值（小时）：须大于「阶段硬超时 × 阶段数上界」，否则会把仍在正常执行的运行误判为孤儿。
     *
     * <p>2026-09-11 由 12 下调为 3：阶段超时从 1800s 降到 300s 后，一次合法运行最长约
     * 300s × 19 ≈ 1.6h，原 12h 阈值会让真正的孤儿运行白占并发闸门十几个小时。
     * 两者是**同一个估算式**的两端，下调超时必须同步下调本值（启动时 {@link #thresholdTooSmall} 会告警）。
     */
    public static final long DEFAULT_STALE_HOURS = 3;

    /**
     * 单次运行的阶段执行次数上界：基础 4 阶段（调研/写作/配图/审核）
     * + 每轮返工最多 3 次（写作 + 配图 + 审核），返工轮次上限 5（ScheduleTaskService 归一为 0..5）。
     * 每阶段时长由 {@code app.schedule.stage-timeout-seconds} 约束，故这是「合法运行最长能有多久」的估算依据。
     *
     * <p><b>2026-09-18 起这是「保守下界」</b>：写作阶段改用独立的
     * {@code app.schedule.writing-timeout-seconds}（900s，见 {@link StageTimeoutPolicy}），
     * 不再受 {@code stage-timeout-seconds} 约束，因此真实上界高于本式算出的值——
     * 按「写作档 900、其余 300」重算约为 {@code 4×300 + 15×900 ≈ 1.85h}（原 19×300 ≈ 1.6h）。
     * 偏保守的后果只是**少告警**（{@link #thresholdTooSmall} 更不容易触发），不会误判孤儿，
     * 故保留本式不动；若将来继续抬高写作档或返工轮数，必须连本式与 {@code stale-run-hours} 一起重算。
     */
    public static final int MAX_STAGES_PER_RUN = 4 + 5 * 3;

    /** 合法运行的最长时长估算（秒，**保守下界**——不含写作档，见 {@link #MAX_STAGES_PER_RUN}）。 */
    public static long worstCaseRunSeconds(long stageTimeoutSeconds) {
        return Math.max(1L, stageTimeoutSeconds) * MAX_STAGES_PER_RUN;
    }

    /**
     * 心跳超时（秒）——I2 的第二判据：心跳停止即证明属主实例已崩溃，与「开始时间过阈值」无关。
     * 默认 180 秒 = 心跳间隔（15s）× 12，既能容忍数次抖动，又能把故障后的自愈窗口从小时级压到分钟级。
     */
    public static final long DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 180L;

    /**
     * 运行是否已可判定为遗留（孤儿），带心跳判据（I2）。
     *
     * <p>优先用心跳：{@code HEARTBEAT_AT} 由执行线程周期刷新，新鲜即证明属主实例还活着——
     * 即使运行开始时间已很久，也不能中止（多实例部署下不会误杀另一实例正在跑的长任务）。
     * 心跳早于 {@code heartbeatTimeoutSeconds} 才判定为孤儿。
     *
     * <p>升级前落库的旧运行没有心跳列，回退到「开始时间 + staleHours」的保守判据。
     *
     * @param staleHours             无心跳时使用的时间阈值（小时）
     * @param heartbeatTimeoutSeconds 心跳超时（秒）；&le;0 时用 {@link #DEFAULT_HEARTBEAT_TIMEOUT_SECONDS}
     */
    public static boolean isStale(TaskRun run, long staleHours, LocalDateTime now,
                                  long heartbeatTimeoutSeconds) {
        if (run == null || now == null) return false;
        if (run.getHeartbeatAt() != null) {
            long timeout = heartbeatTimeoutSeconds > 0
                    ? heartbeatTimeoutSeconds : DEFAULT_HEARTBEAT_TIMEOUT_SECONDS;
            return run.getHeartbeatAt().isBefore(now.minusSeconds(timeout));
        }
        return isStale(run, staleHours, now);
    }

    /**
     * 阈值是否偏小到可能误杀正常长任务（启动时据此告警）。
     * 当前默认 3h 对默认阶段超时（300s × 19 ≈ 1.6h）留有约一倍余量；调小阶段超时可相应放宽。
     */
    public static boolean thresholdTooSmall(long staleHours, long stageTimeoutSeconds) {
        return staleHours * 3600L <= worstCaseRunSeconds(stageTimeoutSeconds);
    }

    /**
     * 运行是否已可判定为遗留（孤儿）。
     *
     * @param run        待判定的运行，可为 null
     * @param staleHours 阈值小时数，小于 1 时按 1 小时兜底
     * @param now        当前时间
     * @return 开始时间早于 {@code now - staleHours} 时为 true；运行不存在或没有开始时间时为 false
     */
    public static boolean isStale(TaskRun run, long staleHours, LocalDateTime now) {
        if (run == null || run.getStartedAt() == null || now == null) return false;
        return run.getStartedAt().isBefore(now.minusHours(Math.max(1, staleHours)));
    }
}
