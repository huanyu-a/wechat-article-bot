package ink.icoding.wechat.article.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 渲染区段标记单测（skills-agent-plan 5.10.4 / 第④期）：
 * 渲染产物注入 data-render-id，供读回识别；占位替换后不得残留 {{render:}}。
 */
class RenderPlaceholderTest {

    @Test
    void markRenderIdInjectsAttributeOnRootTag() {
        String marked = ArticleAiService.markRenderId("<section style=\"a\">内容</section>", "r1");
        assertThat(marked).startsWith("<section")
                .contains("data-render-id=\"r1\"")
                .contains("内容</section>");
    }

    @Test
    void markRenderIdWrapsFragmentWithoutRootTag() {
        String marked = ArticleAiService.markRenderId("裸文本", "r2");
        assertThat(marked).isEqualTo("<section data-render-id=\"r2\">裸文本</section>");
    }

    @Test
    void markRenderIdSkipsLeadingCommentAndDoctype() {
        // 产物以注释开头时，属性必须插到真正的开始标签上，而不是插进注释里
        String commented = ArticleAiService.markRenderId("<!-- generated --><section>内容</section>", "r5");
        assertThat(commented).startsWith("<!-- generated --><section")
                .contains("<section data-render-id=\"r5\">");
        assertThat(commented).doesNotContain("<!-- generated data-render-id");

        String doctype = ArticleAiService.markRenderId("<!DOCTYPE html><p>段落</p>", "r6");
        assertThat(doctype).contains("<p data-render-id=\"r6\">").contains("<!DOCTYPE html>");
    }

    @Test
    void markRenderIdHandlesBlankInput() {
        assertThat(ArticleAiService.markRenderId(null, "r1")).isNull();
        assertThat(ArticleAiService.markRenderId("", "r1")).isEmpty();
    }

    @Test
    void renderResultJsonNeverContainsHtml() {
        ink.icoding.wechat.article.skill.MarkFlowRenderService.RenderResult result =
                new ink.icoding.wechat.article.skill.MarkFlowRenderService.RenderResult(
                        "<section data-render-id=\"r3\"><p>大段 HTML</p></section>", "标题", "摘要",
                        "#27ae60", "#1e8449");
        String json = EditorServiceTools.renderResultJson(result, "r3");
        assertThat(json).contains("r3").contains("标题").doesNotContain("大段 HTML").doesNotContain("<section");
    }

    @Test
    void placeholderSummaryHasNoHtml() {
        String summary = EditorServiceTools.placeholderSummary("r4", 2048);
        assertThat(summary).contains("r4").contains("2048").doesNotContain("<");
    }
}
