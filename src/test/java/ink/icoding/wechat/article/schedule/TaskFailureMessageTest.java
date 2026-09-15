package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 失败终态说明的可读性单测（Phase 5）。
 *
 * <p>规则来自排查成本：此前失败只写一行原始异常（如「子智能体工具调用超过上限 60 次，已中止。预算已用尽…」），
 * 用户看不出属于哪一类问题、该调什么。四类高频失败各要有一句人话解释。
 *
 * <p>同时钉住一条**不得越界**的性质：分类只做「加说明」，**原始信息必须原样保留**——
 * 它是唯一的现场证据（含上游返回的 code），覆盖掉它排查就失去依据。
 */
class TaskFailureMessageTest {

    @Test
    void stallIsExplainedAsAnUpstreamProblemNotAUserMistake() {
        String message = TaskExecutionService.failureMessage(
                new StageTimeoutException("【调研】阶段停滞（无进展 180 秒）：最后活动 检索，已调用工具 0 次"));

        assertThat(message)
                .contains("【会话停滞】")
                // 原始信息必须原样保留：它带着「无进展 180 秒」这个现场数值
                .contains("原始信息：")
                .contains("无进展 180 秒");
    }

    /**
     * 停滞说明**不得谎称已切换档案**——run#123/#124 的实测缺陷。
     *
     * <p>那两次是「已调用 33 / 21 次工具后停滞」，按安全边界（可能已生图/落库）**有意不切换**，
     * 于是 {@code switchedProfile=false}、{@code profilesUsed} 为空。原措辞却写「系统已自动尝试
     * 切换备用模型档案」，排查的人会去找一条不存在的切换记录，进而怀疑档案链本身有问题。
     */
    @Test
    void stallMessageDoesNotFalselyClaimAProfileSwitch() {
        String message = TaskExecutionService.failureMessage(
                new StageTimeoutException("智能体会话停滞（180 秒无任何事件）；已调用工具 33 次"));

        assertThat(message)
                .contains("【会话停滞】")
                .contains("不会")   // 明确说清「已调用工具时不切换」
                .contains("profilesUsed")
                .doesNotContain("已自动尝试切换");
    }

    /** 停滞常被包在 ExecutionException 里抛出，分类必须沿 cause 链找得到。 */
    @Test
    void stallIsDetectedThroughWrappingCauses() {
        Throwable wrapped = new ExecutionException(
                new IllegalStateException(new StageTimeoutException("阶段停滞")));

        assertThat(TaskExecutionService.failureMessage(wrapped)).contains("【会话停滞】");
    }

    @Test
    void budgetExhaustionPointsAtTheConfigKnob() {
        String message = TaskExecutionService.failureMessage(new IllegalStateException(
                "子智能体工具调用超过上限 60 次，已中止。预算已用尽，不要再检索、浏览或读取；请立即用收尾工具提交已有成果"));

        assertThat(message)
                .contains("【预算耗尽】")
                .contains("app.schedule.tool-calls.*")
                .contains("原始信息：");
    }

    @Test
    void noProgressLoopIsDistinguishedFromPlainBudgetExhaustion() {
        String message = TaskExecutionService.failureMessage(new IllegalStateException(
                ToolCallGovernor.noProgressMessage("search_web", 6)));

        assertThat(message)
                .contains("【重复调用】")
                .contains("完全相同的参数")
                // 这两类的中文解释不同，不能混为一谈：一个要调额度，一个要改提示词
                .doesNotContain("【预算耗尽】");
    }

    @Test
    void missingDraftExplainsWhatTheUserShouldCheck() {
        String message = TaskExecutionService.failureMessage(
                new IllegalStateException("写作阶段未提交草稿（save_article_draft 未被调用）"));

        assertThat(message).contains("【未提交草稿】").contains("save_article_draft");
    }

    @Test
    void coverOwnershipFailureIsCalledOutAsSuch() {
        String message = TaskExecutionService.failureMessage(
                new BusinessException("智能体选择的封面素材不属于任务目标公众号"));

        assertThat(message).contains("【封面归属】").contains("公众号隔离");
    }

    @Test
    void modelLevelFailureExplainsThatAllCandidatesAreGone() {
        String message = TaskExecutionService.failureMessage(
                new IllegalStateException("SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}"));

        assertThat(message).contains("【模型不可用】").contains("模型档案");
    }

    @Test
    void quotaExhaustionIsNotBlamedOnTheModel() {
        String message = TaskExecutionService.failureMessage(new IllegalStateException(
                "SSE connection failed: HTTP 402: {\"code\":\"401008\",\"type\":\"permission_error\"}"));

        assertThat(message).contains("【额度不足】");
    }

    /** 参数格式错是实测最常见的一类工具失败，单独归类才看得出「协议补示例后有没有变好」。 */
    @Test
    void toolParamParseFailureGetsItsOwnCategory() {
        String message = TaskExecutionService.failureMessage(
                new IllegalStateException("submit_review - Failed to parse tool param JSON"));

        assertThat(message).contains("【参数格式】").contains("合法 JSON");
    }

    /** 认不出的错误原样输出，不硬套一个类别——错误的分类比没有分类更误导。 */
    @Test
    void unknownFailuresArePassedThroughUntouched() {
        assertThat(TaskExecutionService.failureMessage(new IllegalStateException("数据库连接被拒绝")))
                .isEqualTo("数据库连接被拒绝");
        assertThat(TaskExecutionService.failureMessage(new IllegalStateException()))
                .isEqualTo("IllegalStateException");
        assertThat(TaskExecutionService.failureMessage(null)).isEqualTo("任务执行结束");
    }
}
