package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.MarkFlowRenderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 交付前「渲染 → 取摘要」的顺序：{@code stages_summary.renderWarnings} 是渲染的产出。
 *
 * <p>run#71（PIPELINE，2026-09-12）暴露过这对字段自相矛盾：摘要写着 {@code renderWarnings:0}，
 * 运行说明却写着「有 1 处 MarkFlow 渲染降级（compare 有 5 行列数不是…）」。根因是摘要在
 * {@code renderBeforeDelivery} **之前**取——PIPELINE/COORDINATOR 从不提前渲染，于是这个字段
 * 结构性恒为 0，任何真的版式降级在摘要里都看不见。
 */
class TaskExecutionFinishTest {
    private static final String MARKDOWN = ":::compare\n维度 | A | B | accent\n:::\n";

    private static TaskWorkspace markflowWorkspaceWithDraft() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.MARKFLOW);
        ScheduledArticleTools.SaveMarkflowDraftParam param = new ScheduledArticleTools.SaveMarkflowDraftParam();
        param.setTitle("测试文章");
        param.setContentMarkdown(MARKDOWN);
        workspace.draftState().save(param);
        return workspace;
    }

    /** 渲染出的降级警告必须出现在写进运行记录的摘要里，而不是停留在一个恒为 0 的计数上。 */
    @Test
    void summaryCarriesWarningsProducedByTheDeliveryRender() {
        MarkFlowRenderService renderService = mock(MarkFlowRenderService.class);
        when(renderService.render(any(), any(), any()))
                .thenReturn(new MarkFlowRenderService.RenderResult("<section>渲染产物</section>", "标题", "摘要",
                        "#0984e3", "#0652dd",
                        List.of("compare 有 5 行列数不是「维度 | A方 | B方 | accent|default」，多余或缺少的列已被忽略")));
        TaskWorkspace workspace = markflowWorkspaceWithDraft();
        TaskRun run = new TaskRun();

        TaskExecutionService.renderAndSummarize(workspace, renderService, run);

        assertThat(run.getStagesSummary())
                .as("摘要取的是渲染之后的草稿状态")
                .contains("\"renderWarnings\":1");
        assertThat(workspace.draftState().renderWarnings()).hasSize(1);
    }

    /** 没有降级时摘要记 0——判据要能反证，不能是「不管渲染成什么样都写 1」。 */
    @Test
    void summaryReportsZeroWhenTheRenderIsClean() {
        MarkFlowRenderService renderService = mock(MarkFlowRenderService.class);
        when(renderService.render(any(), any(), any()))
                .thenReturn(new MarkFlowRenderService.RenderResult("<section>渲染产物</section>", "标题", "摘要",
                        "#0984e3", "#0652dd"));
        TaskWorkspace workspace = markflowWorkspaceWithDraft();
        TaskRun run = new TaskRun();

        TaskExecutionService.renderAndSummarize(workspace, renderService, run);

        assertThat(run.getStagesSummary()).contains("\"renderWarnings\":0");
    }
}
