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
}
