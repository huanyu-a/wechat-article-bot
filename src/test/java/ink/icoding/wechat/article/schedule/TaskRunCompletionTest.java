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
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS");
        assertThat(completion.message()).isEqualTo("已生成《标题》");
    }

    @Test
    void toolFailuresDowngradeTheStatusAndExplainWhy() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(2, "已生成《标题》");

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message())
                .contains("有 2 次工具调用失败")
                .contains("交付内容可能不完整")
                .contains("已生成《标题》");
    }

    @Test
    void toolFailuresStillProduceAReadableMessageWhenAgentRepliesNothing() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(1, null);

        assertThat(completion.status()).isEqualTo("SUCCESS_WITH_WARNINGS");
        assertThat(completion.message()).contains("有 1 次工具调用失败");
    }

    @Test
    void blankReplyFallsBackToAPlaceholder() {
        TaskExecutionService.RunCompletion completion = TaskExecutionService.completion(0, "  ");

        assertThat(completion.status()).isEqualTo("SUCCESS");
        assertThat(completion.message()).isEqualTo("任务执行结束");
    }
}
