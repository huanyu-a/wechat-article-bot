package ink.icoding.wechat.article.schedule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按阶段取会话硬超时（秒）。
 *
 * <p>为什么需要按阶段分档：{@code app.schedule.stage-timeout-seconds}（300s）是照「单个阶段会话是分钟级」
 * 定的，对**检索 / 配图 / 审核**成立，对**写作**不成立——写作阶段要一次生成 30k+ 字符的完整成稿，
 * 量级与「调研 + 写作 + 配图一次做完」的 SINGLE 会话（900s）相当。
 *
 * <p>实测依据（2026-09-18，task#2 PIPELINE 的 run#14 / run#18）：两次的写作阶段都**恰好停在 300s 上限**，
 * 而硬超时触发时「最后活动距今 0 秒」——会话一直在正常出字，只是成稿没写完就被墙钟砍掉；
 * 整轮因此 FAILED（{@code saved=false}）。同一阶段在 run#8 只用了 90.8s，
 * 说明它的真实耗时在 90s 与 &gt;300s 之间浮动，300s 正好卡在中间，是**误杀**而不是止损。
 *
 * <p>为什么不直接把全局 {@code stage-timeout-seconds} 抬到 900：那个值同时是
 * 「停滞止损速度」与 {@link StaleRunPolicy} 的孤儿判定基准，抬它会让检索/配图/审核三个阶段的
 * 卡死白等时间同步变长（300 → 900 意味着一次真卡死多等 10 分钟），并放大孤儿判定的估算窗口。
 * 按阶段分档只放宽真正需要的那个阶段，其余维持原样。
 *
 * <p>与既有两处分档的关系：SINGLE（{@code single-timeout-seconds}，900s）与
 * COORDINATOR 主编（{@code coordinator-timeout-seconds}，1800s）此前已各自独立；
 * 本类补齐「PIPELINE 里的写作阶段」这第三处，口径一致。
 */
@Component
public class StageTimeoutPolicy {
    /** 单阶段默认硬超时（秒），与 {@code app.schedule.stage-timeout-seconds} 的默认值同源。 */
    public static final long DEFAULT_STAGE_SECONDS = 300L;

    /**
     * 写作阶段默认硬超时（秒）。
     *
     * <p>900 取自与 SINGLE 同一量级（SINGLE 的一次会话覆盖调研 + 写作 + 配图，实测成功运行最长 785.2s）。
     * 写作单独一次成稿与之相当，故取同一个上限；仍远低于 COORDINATOR 主编的 1800s，
     * 且停滞会由 {@link AgentInvoker} 重试 / 换档案，不会白等满整个超时。
     */
    public static final long DEFAULT_WRITING_SECONDS = 900L;

    private final long stageSeconds;
    private final long writingSeconds;

    public StageTimeoutPolicy(@Value("${app.schedule.stage-timeout-seconds:" + DEFAULT_STAGE_SECONDS + "}")
                              long stageSeconds,
                              @Value("${app.schedule.writing-timeout-seconds:" + DEFAULT_WRITING_SECONDS + "}")
                              long writingSeconds) {
        // 非正数会让 StageTimeout 回落到它自己的兜底值，与调用方意图不符：配置写错时按默认档执行
        this.stageSeconds = stageSeconds > 0 ? stageSeconds : DEFAULT_STAGE_SECONDS;
        this.writingSeconds = writingSeconds > 0 ? writingSeconds : DEFAULT_WRITING_SECONDS;
    }

    /** 该阶段的会话硬超时（秒）。 */
    public long forStage(String stage) {
        return isWriting(stage) ? writingSeconds : stageSeconds;
    }

    /** 是否写作阶段（大小写与空白不敏感，与 {@code ToolCallBudget.subAgentLimitFor} 口径一致）。 */
    public static boolean isWriting(String stage) {
        return stage != null && "WRITING".equalsIgnoreCase(stripDelegatePrefix(stage));
    }

    /**
     * 去掉协调者委托阶段的前缀：委托链路里 {@code stages_summary} 记的是 {@code DELEGATE_WRITING}，
     * 而传给子会话运行器的 stage 是 {@code WRITING}。两种写法都认，免得「委托写作」因为
     * 调用方传了带前缀的那一个而静默退回 300 秒档——那正是本轮要修的缺陷 B 在委托链路上的形态。
     */
    private static String stripDelegatePrefix(String stage) {
        String trimmed = stage.trim();
        return trimmed.regionMatches(true, 0, "DELEGATE_", 0, "DELEGATE_".length())
                ? trimmed.substring("DELEGATE_".length()) : trimmed;
    }

    public long stageSeconds() {
        return stageSeconds;
    }

    public long writingSeconds() {
        return writingSeconds;
    }

    /** 默认分档（非 Spring 上下文构造执行器时用，如单测）。 */
    public static StageTimeoutPolicy defaults() {
        return new StageTimeoutPolicy(DEFAULT_STAGE_SECONDS, DEFAULT_WRITING_SECONDS);
    }
}
