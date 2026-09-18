package ink.icoding.wechat.article.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 按阶段取会话硬超时的分档单测（2026-09-18，run#14 / run#18 的写作阶段误杀修复）。
 *
 * <p>为什么必须分档：{@code app.schedule.stage-timeout-seconds}（300s）是照「单个阶段会话是分钟级」
 * 定的，对**检索 / 配图 / 审核**成立，对**写作**不成立——写作要一次生成 30k+ 字符的完整成稿，
 * 量级与 SINGLE（900s）相当。实测 run#14 / run#18 的写作阶段都**恰好停在 300s**，
 * 且硬超时触发时「最后活动距今 0 秒」（会话一直在正常出字，只是成稿没写完）——
 * 是误杀而不是止损，整轮因此 FAILED、文章没有落库。
 */
class StageTimeoutPolicyTest {

    @Test
    void writingStageGetsItsOwnLongerBudget() {
        StageTimeoutPolicy policy = StageTimeoutPolicy.defaults();

        assertThat(policy.forStage("WRITING"))
                .as("写作阶段要一次生成完整成稿，量级与 SINGLE 相当")
                .isEqualTo(StageTimeoutPolicy.DEFAULT_WRITING_SECONDS);
        assertThat(policy.forStage("RESEARCH")).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
        assertThat(policy.forStage("ILLUSTRATION")).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
        assertThat(policy.forStage("REVIEW")).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
        assertThat(policy.forStage("COORDINATE")).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
    }

    /** 写作档必须**严格长于**普通档，否则这次分档一点意义都没有。 */
    @Test
    void writingBudgetIsStrictlyLongerThanTheStageBudget() {
        assertThat(StageTimeoutPolicy.DEFAULT_WRITING_SECONDS)
                .isGreaterThan(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
    }

    /**
     * 委托链路的 {@code DELEGATE_WRITING} 必须认成写作阶段。
     *
     * <p>{@code stages_summary} 里记的是带前缀的 {@code DELEGATE_WRITING}，而
     * {@code DelegateTools} 传给子会话运行器的 stage 是 {@code WRITING}。两种写法都要认——
     * 只认其中一种的话，委托写作会静默退回 300 秒档，缺陷 B 在委托链路上照旧发作。
     */
    @Test
    void delegateWritingPrefixIsRecognizedAsWriting() {
        StageTimeoutPolicy policy = StageTimeoutPolicy.defaults();

        assertThat(StageTimeoutPolicy.isWriting("DELEGATE_WRITING")).isTrue();
        assertThat(policy.forStage("DELEGATE_WRITING"))
                .as("委托写作与 PIPELINE 的写作阶段是同一件事，必须同一档")
                .isEqualTo(StageTimeoutPolicy.DEFAULT_WRITING_SECONDS);
    }

    /** 大小写与空白不敏感（与 ToolCallBudget.subAgentLimitFor 口径一致）。 */
    @Test
    void stageMatchingIsCaseAndWhitespaceInsensitive() {
        StageTimeoutPolicy policy = StageTimeoutPolicy.defaults();

        assertThat(policy.forStage(" writing ")).isEqualTo(StageTimeoutPolicy.DEFAULT_WRITING_SECONDS);
        assertThat(policy.forStage("delegate_writing")).isEqualTo(StageTimeoutPolicy.DEFAULT_WRITING_SECONDS);
        assertThat(StageTimeoutPolicy.isWriting("writing")).isTrue();
        assertThat(StageTimeoutPolicy.isWriting("WRITING")).isTrue();
    }

    /** 非写作 / 空值一律走普通档，不能因为判据写漏而给检索阶段发 900 秒的额度。 */
    @Test
    void nonWritingAndNullStagesFallBackToTheStageBudget() {
        StageTimeoutPolicy policy = StageTimeoutPolicy.defaults();

        assertThat(policy.forStage(null)).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
        assertThat(policy.forStage("")).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
        assertThat(policy.forStage("   ")).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
        assertThat(policy.forStage("WRITING_EXTRA")).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
        assertThat(StageTimeoutPolicy.isWriting(null)).isFalse();
        assertThat(StageTimeoutPolicy.isWriting("")).isFalse();
    }

    /**
     * 配置写错（0 / 负数）时回落到默认档，而不是让该阶段立刻失败。
     *
     * <p>{@code StageTimeout} 对 {@code timeoutSeconds <= 0} 有自己的兜底值，与调用方意图不符；
     * 因此非正数必须在这里就被换成默认档（与 {@code ToolCallBudget.positiveOr} 同款处理）。
     */
    @Test
    void nonPositiveConfigurationFallsBackToDefaults() {
        StageTimeoutPolicy policy = new StageTimeoutPolicy(0, -1);

        assertThat(policy.stageSeconds()).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
        assertThat(policy.writingSeconds()).isEqualTo(StageTimeoutPolicy.DEFAULT_WRITING_SECONDS);
        assertThat(policy.forStage("WRITING")).isEqualTo(StageTimeoutPolicy.DEFAULT_WRITING_SECONDS);
        assertThat(policy.forStage("RESEARCH")).isEqualTo(StageTimeoutPolicy.DEFAULT_STAGE_SECONDS);
    }

    /** 显式配置的值按原样生效（写作档仍独立于普通档）。 */
    @Test
    void explicitConfigurationIsHonored() {
        StageTimeoutPolicy policy = new StageTimeoutPolicy(120, 600);

        assertThat(policy.forStage("RESEARCH")).isEqualTo(120);
        assertThat(policy.forStage("WRITING")).isEqualTo(600);
    }
}
