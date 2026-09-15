package ink.icoding.wechat.article.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具调用预算单测：额度按阶段分档，且配置写错时不静默放开护栏。
 *
 * <p>背景见 {@link ToolCallBudget}：调研阶段用统一额度会让正常的宽口径调研撞上上限
 * （实测 run#46：25 次调用全部成功却因此让整轮 PIPELINE 失败）。
 */
class ToolCallBudgetTest {

    @Test
    void researchGetsAWiderBudgetThanOtherStages() {
        ToolCallBudget budget = ToolCallBudget.defaults();

        assertThat(budget.subAgentLimitFor("RESEARCH")).isEqualTo(ToolCallBudget.DEFAULT_RESEARCH);
        assertThat(budget.subAgentLimitFor("WRITING")).isEqualTo(ToolCallBudget.DEFAULT_STAGE);
        assertThat(budget.subAgentLimitFor("ILLUSTRATION")).isEqualTo(ToolCallBudget.DEFAULT_STAGE);
        assertThat(budget.subAgentLimitFor("REVIEW")).isEqualTo(ToolCallBudget.DEFAULT_STAGE);
        // 调研额度必须真的更宽，否则这个分档毫无意义
        assertThat(ToolCallBudget.DEFAULT_RESEARCH).isGreaterThan(ToolCallBudget.DEFAULT_STAGE);
    }

    @Test
    void stageCodeIsMatchedCaseInsensitivelyAndToleratesBlanksAndNull() {
        ToolCallBudget budget = ToolCallBudget.defaults();

        assertThat(budget.subAgentLimitFor("research")).isEqualTo(ToolCallBudget.DEFAULT_RESEARCH);
        assertThat(budget.subAgentLimitFor(" RESEARCH ")).isEqualTo(ToolCallBudget.DEFAULT_RESEARCH);
        assertThat(budget.subAgentLimitFor(null)).isEqualTo(ToolCallBudget.DEFAULT_STAGE);
        assertThat(budget.subAgentLimitFor("  ")).isEqualTo(ToolCallBudget.DEFAULT_STAGE);
    }

    @Test
    void nonPositiveConfigurationFallsBackInsteadOfDisablingTheGuard() {
        // AgentInvoker 把 maxToolCalls <= 0 当作「不限制」；配置写成 0/负数时必须回落到默认额度，
        // 否则一个笔误就会让预算护栏静默失效。
        ToolCallBudget budget = new ToolCallBudget(0, -1, 0, 0);

        assertThat(budget.subAgentLimitFor("RESEARCH")).isEqualTo(ToolCallBudget.DEFAULT_RESEARCH);
        assertThat(budget.subAgentLimitFor("WRITING")).isEqualTo(ToolCallBudget.DEFAULT_STAGE);
        assertThat(budget.chiefLimit()).isEqualTo(ToolCallBudget.DEFAULT_CHIEF);
        assertThat(budget.totalLimit()).isEqualTo(ToolCallBudget.DEFAULT_TOTAL);
    }

    @Test
    void configuredValuesAreHonoured() {
        ToolCallBudget budget = new ToolCallBudget(50, 30, 60, 150);

        assertThat(budget.subAgentLimitFor("RESEARCH")).isEqualTo(50);
        assertThat(budget.subAgentLimitFor("WRITING")).isEqualTo(30);
        assertThat(budget.chiefLimit()).isEqualTo(60);
        assertThat(budget.totalLimit()).isEqualTo(150);
    }

    @Test
    void defaultsStayAlignedWithTheDelegateToolConstants() {
        // DelegateTools 的常量是缺省值的单一来源；两处漂移会让「执行器按 40 跑、账按 24 记」。
        assertThat(ToolCallBudget.DEFAULT_STAGE)
                .isEqualTo(ink.icoding.wechat.article.ai.DelegateTools.MAX_SUB_AGENT_TOOL_CALLS);
        assertThat(ToolCallBudget.DEFAULT_CHIEF)
                .isEqualTo(ink.icoding.wechat.article.ai.DelegateTools.MAX_CHIEF_TOOL_CALLS);
        assertThat(ToolCallBudget.DEFAULT_TOTAL)
                .isEqualTo(ink.icoding.wechat.article.ai.DelegateTools.MAX_TOTAL_TOOL_CALLS);
    }

    /**
     * 额度要留得住正常工作量，否则「预算用尽」会变成常态、护栏反而天天误伤。
     *
     * <p>实测（2026-09-11 的运行记录）：run#63 评审单会话 25 次调用、run#68 配图单会话 26 次，
     * 都在旧上限 24 之上；run#68 整轮 110 次，逼近旧上限 120。这就是提到 36 / 200 的依据。
     */
    @Test
    void defaultsLeaveHeadroomForMeasuredWorkloads() {
        assertThat(ToolCallBudget.DEFAULT_STAGE).isGreaterThanOrEqualTo(36);
        assertThat(ToolCallBudget.DEFAULT_TOTAL).isGreaterThanOrEqualTo(200);
        // 单阶段额度不能顶到整轮额度，否则总账永远先撞线，分档也就失去意义
        assertThat(ToolCallBudget.DEFAULT_STAGE).isLessThan(ToolCallBudget.DEFAULT_TOTAL);
    }

    /**
     * 收尾工具必须覆盖四个阶段各自的「交出成果」入口，且宽限次数有界。
     *
     * <p>漏掉任何一个，该阶段的产出就会在预算用尽时整段作废（run#62 的调研简报就是这么丢的）；
     * 宽限无界则 {@code save_research_notes}（追加语义）会把工作区刷满重复简报。
     */
    @Test
    void terminalToolsCoverEveryStageDeliverableAndStayBounded() {
        assertThat(ToolCallBudget.TERMINAL_TOOLS).containsExactlyInAnyOrder(
                "save_research_notes", "save_article_draft", "submit_review", "set_article_draft_cover");
        assertThat(ToolCallBudget.TERMINAL_GRACE).isPositive().isLessThanOrEqualTo(5);
    }

    /**
     * 宽限额度按「成功占额度、失败不占额度」结算——复刻 run#85 的调用序列。
     *
     * <p>run#85 的 4 次宽限里 3 次被烧掉，只有 1 次是真正的成果提交：成功的
     * {@code set_article_draft_cover}、两次因摘要超长失败的 {@code save_article_draft}，
     * 第 4 次 {@code save_article_draft} 直接被拒——前 40 次成功检索全部作废。
     * 修好之后，两次失败不占额度，第 4 次提交必须放行。
     *
     * <p>判据的代数形式很直白：{@code attempts = 成功 + 失败}，而失败换回的额度是
     * {@code min(失败, grace)}，代入后当失败不超过 grace 时失败项相消，
     * 条件等价于 **成功次数 &lt; TERMINAL_GRACE**——即「成功提交最多 grace 次，失败不额外收费」。
     * 失败超过 grace 后换回的额度不再增长，总量封顶 {@code 2 × grace}。
     */
    @Test
    void failedTerminalCallsEarnBackGraceButTotalStaysBounded() {
        int grace = ToolCallBudget.TERMINAL_GRACE;

        // 纯成功路径：连续 grace 次放行，第 grace+1 次收紧
        for (int used = 0; used < grace; used++) {
            assertThat(ToolCallBudget.allowsTerminalPastBudget(used, 0))
                    .as("第 %d 次成功提交仍在宽限内", used + 1).isTrue();
        }
        assertThat(ToolCallBudget.allowsTerminalPastBudget(grace, 0))
                .as("成功次数达到 grace 后不再放行").isFalse();

        // run#85 现场：3 次尝试里 1 成功 + 2 失败。旧判据（只数次数）到这里就关门了
        assertThat(ToolCallBudget.allowsTerminalPastBudget(grace, 2))
                .as("两次失败不占额度，第 4 次提交必须放行").isTrue();
        // 失败不占额度，成功仍然占：成功累计到 grace 次才关门
        assertThat(ToolCallBudget.allowsTerminalPastBudget(grace + 1, 2))
                .as("此时只成功了 2 次，仍在额度内").isTrue();
        assertThat(ToolCallBudget.allowsTerminalPastBudget(grace + 2, 2))
                .as("成功累计到 grace 次后收紧").isFalse();
        assertThat(ToolCallBudget.allowsTerminalPastBudget(grace + 2, 3))
                .as("多一次失败就多一格，重新放行").isTrue();

        // 上界：失败再多也只能把总量撑到 2 × grace，否则成功路径就没有上限了
        assertThat(ToolCallBudget.allowsTerminalPastBudget(grace * 2 - 1, 1_000)).isTrue();
        assertThat(ToolCallBudget.allowsTerminalPastBudget(grace * 2, 1_000))
                .as("失败换来的额度本身也必须封顶").isFalse();
        // 负数（理论上不该出现）不能把判据算成「已用超额」
        assertThat(ToolCallBudget.allowsTerminalPastBudget(0, -5)).isTrue();
    }
}
