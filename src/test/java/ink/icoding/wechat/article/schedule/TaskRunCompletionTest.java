package ink.icoding.wechat.article.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行终态判定单测。
 *
 * <p>规则来自一次真实事故：{@code generate_image} 因描述超长在落库处失败，配图没进文章，
 * 但整次运行仍被记成 SUCCESS——用户在运行历史里看到一个绿色对勾，交付物却缺图。
 * 因此「有工具失败」必须是可区分的终态（{@code SUCCESS_WITH_WARNINGS}，前端 TasksView 有对应图标与颜色）。
 */
class TaskRunCompletionTest {

    @Test
    void cleanRunIsSuccess() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, 0, "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS");
        assertThat(completion.message()).isEqualTo("已生成《标题》");
    }

    @Test
    void toolFailuresDowngradeTheStatusAndExplainWhy() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(2, 0, "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message())
                .contains("有 2 次工具调用失败")
                .contains("交付内容可能不完整")
                .contains("已生成《标题》");
    }

    @Test
    void toolFailuresStillProduceAReadableMessageWhenAgentRepliesNothing() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(1, 0, null);

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message()).contains("有 1 次工具调用失败");
    }

    /**
     * 参数格式失败要从「N 次工具调用失败」里**露出来**：它是实测最常见的一类工具失败，
     * 被总数吞掉之后用户看不出「失败集中在参数格式」这个可操作的信号
     * （该改提示词 / 该简化参数结构），也看不出协议补了 JSON 示例之后有没有变好。
     */
    @Test
    void toolParamParseFailuresAreCalledOutInsideTheFailureCount() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(
                3, 0, java.util.List.of(), java.util.List.of(), java.util.List.of(), 2, "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message())
                .contains("有 3 次工具调用失败")
                .contains("其中 2 次是工具参数不是合法 JSON");
    }

    /** 没有参数格式失败时不得多出那句话（运行说明是给人读的，不该有恒为 0 的噪声）。 */
    @Test
    void noParseFailureDetailWhenNoneOccurred() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(
                2, 0, java.util.List.of(), java.util.List.of(), java.util.List.of(), 0, "已生成《标题》");

        assertThat(completion.message()).doesNotContain("合法 JSON");
    }

    @Test
    void degradedStagesAreReportedAsSuchNotAsToolFailures() {
        // 「调研阶段整个没做完，写作仍完成了文章」与「某个工具报错」是两回事：
        // 说成「N 次工具调用失败」会让排查的人去日志里找一个根本不存在的失败工具。
        TaskExecutionService.RunCompletion completion =
                TaskExecutionService.completion(0, 1, "流水线执行完成：调研 → 写作");

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message())
                .contains("有 1 个阶段中止并已按现有产出继续")
                .doesNotContain("工具调用失败")
                .contains("流水线执行完成");
    }

    @Test
    void toolFailuresAndDegradationsAreBothListed() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(2, 1, "完成");

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message()).contains("有 2 次工具调用失败").contains("有 1 个阶段中止");
    }

    @Test
    void blankReplyFallsBackToAPlaceholder() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, 0, "  ");

        assertThat(completion.status()).isEqualTo("SUCCESS");
        assertThat(completion.message()).isEqualTo("任务执行结束");
    }

    /**
     * MarkFlow 渲染降级必须让终态不再是干净的 SUCCESS。
     *
     * <p>这类降级此前完全静默：渲染器把没认出来的语法当普通文字输出，落库的 HTML 依旧满是内联样式
     * （看起来「有排版」），用户只能从「版式怎么不对」倒推。实测（2026-09-12 探针）未闭合的
     * {@code :::compare} 产物里就是 {@code <p>:::compare</p>}，且后续整段内容被吞掉。
     */
    @Test
    void renderDegradationsDowngradeTheStatusWithTheirReason() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, 0,
                java.util.List.of("容器语法 :::compare 未被识别（多半是缺少收尾的 :::），已作为正文文字输出"),
                "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message())
                .contains("有 1 处 MarkFlow 渲染降级")
                .contains(":::compare")
                .contains("已生成《标题》");
    }

    @Test
    void noRenderWarningsKeepsCleanRunGreen() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, 0,
                java.util.List.of(), "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS");
        assertThat(completion.message()).isEqualTo("已生成《标题》");
    }

    /**
     * 保存降级（标题/摘要超长被截断）必须让终态不再是干净的 SUCCESS。
     *
     * <p>这类降级此前是**硬失败**：直接抛 {@code IllegalArgumentException}，实测 run#85 / run#89
     * 因此丢掉整篇文章（前 40 次检索全部成功）。改为截断后，交付确实打了折扣（摘要变短了），
     * 所以终态必须是「成功（有警告）」而不是绿色对勾——与渲染降级同一套「降级必须可见」的原则。
     */
    @Test
    void saveDegradationsDowngradeTheStatusWithTheirReason() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, 0,
                java.util.List.of(), java.util.List.of("摘要 187 字已截断为 120 字"), "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message())
                .contains("有 1 处保存降级")
                .contains("摘要 187 字已截断为 120 字")
                .contains("已生成《标题》");
    }

    /** 渲染降级与保存降级是两回事，同时出现时必须**分别**表述，否则排查会找错方向。 */
    @Test
    void renderAndSaveDegradationsAreReportedSeparately() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, 0,
                java.util.List.of("容器语法 :::compare 未被识别"), java.util.List.of("摘要 187 字已截断为 120 字"),
                "完成");

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message())
                .contains("有 1 处 MarkFlow 渲染降级")
                .contains("有 1 处保存降级");
    }

    @Test
    void noSaveWarningsKeepsCleanRunGreen() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, 0,
                java.util.List.of(), java.util.List.of(), "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS");
        assertThat(completion.message()).isEqualTo("已生成《标题》");
    }
}
