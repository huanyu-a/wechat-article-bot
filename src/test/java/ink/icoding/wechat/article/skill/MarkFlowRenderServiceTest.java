package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.settings.RenderConfigService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MarkFlowRenderService 纯单测：覆写 HTTP 方法桩掉真实网络（skills-agent-plan 8.1）。
 */
class MarkFlowRenderServiceTest {
    private static final RenderConfigService.RuntimeConfig ENABLED =
            new RenderConfigService.RuntimeConfig(true, "MARKFLOW", "https://render.example.com",
                    "token-1234", "https://site.example.com", 21600);

    private RenderConfigService.RuntimeConfig config = ENABLED;

    private MarkFlowRenderService service(String getResponse, String postResponse) {
        return new MarkFlowRenderService(null) {
            @Override
            public RenderConfigService.RuntimeConfig requireRuntime() {
                return config;
            }

            @Override
            String httpGet(String url, String token) {
                return getResponse;
            }

            @Override
            String httpPost(String url, String token, String jsonBody) {
                return postResponse;
            }
        };
    }

    private static final String GUIDE_JSON =
            "{\"ok\":true,\"guide\":\"# MarkFlow 语法指令 6815 字符\"}";
    private static final String RENDER_OK_JSON =
            "{\"ok\":true,\"html\":\"<p style='margin:0'>正文</p><script>alert(1)</script>\","
                    + "\"meta\":{\"title\":\"标题\",\"summary\":\"摘要\"},"
                    + "\"theme\":{\"accent\":\"#27ae60\",\"dark\":\"#1d8348\"},"
                    + "\"preview\":\"<!DOCTYPE html>大段预览页\"}";

    @Test
    void guideCacheHitWithinTtl() {
        AtomicInteger getCalls = new AtomicInteger();
        MarkFlowRenderService service = new MarkFlowRenderService(null) {
            @Override
            public RenderConfigService.RuntimeConfig requireRuntime() {
                return config;
            }

            @Override
            String httpGet(String url, String token) {
                getCalls.incrementAndGet();
                return GUIDE_JSON;
            }
        };
        assertThat(service.fetchSyntaxGuide()).contains("MarkFlow");
        assertThat(service.fetchSyntaxGuide()).contains("MarkFlow");
        assertThat(getCalls.get()).isEqualTo(1);
    }

    @Test
    void renderFailureWithSyntaxErrorInvalidatesGuideCache() {
        String[] getResponses = {GUIDE_JSON, GUIDE_JSON.replace("6815 字符", "6815 字符（刷新后）")};
        AtomicInteger calls = new AtomicInteger();
        MarkFlowRenderService service = new MarkFlowRenderService(null) {
            @Override
            public RenderConfigService.RuntimeConfig requireRuntime() {
                return config;
            }

            @Override
            String httpGet(String url, String token) {
                return getResponses[calls.getAndIncrement()];
            }

            @Override
            String httpPost(String url, String token, String jsonBody) {
                return "{\"ok\":false,\"error\":\"markdown 语法非法：未知容器\"}";
            }
        };
        service.fetchSyntaxGuide();
        assertThatThrownBy(() -> service.render("# t", null, null))
                .isInstanceOf(ink.icoding.wechat.article.common.BusinessException.class)
                .hasMessageContaining("渲染失败");
        String refreshed = service.fetchSyntaxGuide();
        assertThat(refreshed).endsWith("（刷新后）");
    }

    @Test
    void nonSyntaxRenderFailureKeepsGuideCache() {
        // 主题色非法等与语法无关的失败不应清缓存（方案 5.10.3 定向失效）
        AtomicInteger getCalls = new AtomicInteger();
        MarkFlowRenderService service = new MarkFlowRenderService(null) {
            @Override
            public RenderConfigService.RuntimeConfig requireRuntime() {
                return config;
            }

            @Override
            String httpGet(String url, String token) {
                getCalls.incrementAndGet();
                return GUIDE_JSON;
            }

            @Override
            String httpPost(String url, String token, String jsonBody) {
                return "{\"ok\":false,\"error\":\"accent 颜色格式非法\"}";
            }
        };
        service.fetchSyntaxGuide();
        assertThatThrownBy(() -> service.render("# t", "bad-color", null))
                .isInstanceOf(ink.icoding.wechat.article.common.BusinessException.class);
        service.fetchSyntaxGuide(); // 仍应命中缓存
        assertThat(getCalls.get()).isEqualTo(1);
    }

    @Test
    void syntaxErrorDetectionKeywords() {
        assertThat(MarkFlowRenderService.looksLikeSyntaxError("markdown 语法非法：未知容器")).isTrue();
        assertThat(MarkFlowRenderService.looksLikeSyntaxError("unsupported component :::foo")).isTrue();
        assertThat(MarkFlowRenderService.looksLikeSyntaxError("")).isTrue(); // 空信息保守清缓存
        assertThat(MarkFlowRenderService.looksLikeSyntaxError("accent 颜色格式非法")).isFalse();
        assertThat(MarkFlowRenderService.looksLikeSyntaxError("dark 必须是 6 位 hex")).isFalse();
    }

    @Test
    void http200WithOkFalseThrows() {
        MarkFlowRenderService service = service(GUIDE_JSON,
                "{\"ok\":false,\"error\":\"渲染引擎内部错误\"}");
        assertThatThrownBy(() -> service.render("# t", null, null))
                .isInstanceOf(ink.icoding.wechat.article.common.BusinessException.class)
                .hasMessageContaining("渲染引擎内部错误");
    }

    @Test
    void renderReturnsSanitizedHtmlAndMeta() {
        MarkFlowRenderService service = service(GUIDE_JSON, RENDER_OK_JSON);
        MarkFlowRenderService.RenderResult result = service.render("# t", null, null);
        assertThat(result.html()).contains("正文").doesNotContain("script");
        assertThat(result.title()).isEqualTo("标题");
        assertThat(result.themeDark()).isEqualTo("#1d8348");
    }

    @Test
    void absoluteImageUrlsRewritesUploadsLinks() {
        MarkFlowRenderService service = service(GUIDE_JSON, RENDER_OK_JSON);
        String markdown = "# t\n\n![图](/uploads/a.png)\n\n<img src=\"/uploads/b.png\">";
        String result = service.absoluteImageUrls(markdown, "https://site.example.com");
        assertThat(result).contains("https://site.example.com/uploads/a.png");
        assertThat(result).contains("src=\"https://site.example.com/uploads/b.png\"");
        assertThat(result).doesNotContain("](/uploads/");
    }

    @Test
    void absoluteImageUrlsSkippedWithoutSiteBaseUrl() {
        MarkFlowRenderService service = service(GUIDE_JSON, RENDER_OK_JSON);
        assertThat(service.absoluteImageUrls("![图](/uploads/a.png)", null))
                .isEqualTo("![图](/uploads/a.png)");
    }

    @Test
    void sanitizeStripsDangerousElementsButKeepsUnknownTags() {
        MarkFlowRenderService service = service(GUIDE_JSON, RENDER_OK_JSON);
        String dirty = "<p onclick=\"steal()\">a</p><script>alert(1)</script>"
                + "<iframe src=\"//x\"></iframe><a href=\"javascript:evil()\">l</a>"
                + "<a href=javascript:evil()>无引号</a>"
                + "<unknown-tag>保留我</unknown-tag>";
        String clean = service.sanitizeHtml(dirty);
        assertThat(clean).doesNotContain("script").doesNotContain("iframe")
                .doesNotContain("onclick").doesNotContain("javascript:");
        assertThat(clean).contains("保留我").contains("<unknown-tag>");
    }

    /**
     * 渲染降级必须能被看出来：「识别到的组件都会被展开成内联样式节点」是这条判据的依据，
     * 产物里留着字面语法就说明渲染器没认出来——这正是「精排没有 100% 复刻」的可见形态。
     * 判据来自 2026-09-12 的真实探针（未闭合的 :::compare → `&lt;p&gt;:::compare&lt;/p&gt;`）。
     */
    @Test
    void detectsLeakedSyntaxInRenderedHtml() {
        assertThat(MarkFlowRenderService.detectLeakedSyntax(
                "<section style=\"margin:0\"><p style=\"font-size:16px\">正常正文</p></section>")).isEmpty();

        java.util.List<String> container = MarkFlowRenderService.detectLeakedSyntax(
                "<section style=\"margin:0\"><p style=\"margin:0\">:::compare</p></section>");
        assertThat(container).hasSize(1);
        assertThat(container.get(0)).contains(":::compare").contains("未被识别");

        java.util.List<String> component = MarkFlowRenderService.detectLeakedSyntax(
                "<p style=\"margin:0\"><steps></p><p style=\"margin:0\">第一步</p>");
        assertThat(component).hasSize(1);
        assertThat(component.get(0)).contains("<steps>");
    }

    /**
     * 自闭合组件被写成成对标签时，只有**收尾标签**留在产物里（开标签被渲染器认掉了），
     * 光扫开标签的判据看不见它。实测产物文本：`tip推荐</badge>`。
     */
    @Test
    void detectsClosingTagsOfSelfClosingComponents() {
        java.util.List<String> leaked = MarkFlowRenderService.detectLeakedSyntax(
                "<p style=\"margin:0\"><span style=\"display:inline-block\">tip</span>推荐</badge></p>");
        assertThat(leaked).hasSize(1);
        assertThat(leaked.get(0)).contains("</badge>").contains("自闭合");

        // 正常产物里不该出现任何组件收尾标签（渲染器会把它们展开成 section/p）
        assertThat(MarkFlowRenderService.detectLeakedSyntax(
                "<section style=\"margin:0\"><p style=\"margin:0\">正文</p></section>")).isEmpty();
    }

    @Test
    void leakedSyntaxScanIgnoresCodeBlocks() {
        // 讲解 MarkFlow 语法的文章会在代码块里写字面语法——那是正文内容，不是渲染降级
        String html = "<pre style=\"background:#f6f8fa\"><code>:::compare\n&lt;steps&gt;</code></pre>"
                + "<p style=\"margin:0\">正文</p>";
        assertThat(MarkFlowRenderService.detectLeakedSyntax(html)).isEmpty();
    }

    @Test
    void upstreamWarningsAreParsedWhenPresentAndTolerateAbsence() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            assertThat(MarkFlowRenderService.parseWarnings(mapper.readTree(
                    "{\"title\":\"t\",\"warnings\":[\"容器未闭合\",\"缺列行被忽略\"]}")))
                    .containsExactly("容器未闭合", "缺列行被忽略");
            assertThat(MarkFlowRenderService.parseWarnings(mapper.readTree("{\"title\":\"t\"}"))).isEmpty();
            assertThat(MarkFlowRenderService.parseWarnings(mapper.readTree("{\"warnings\":\"字符串不是数组\"}"))).isEmpty();
            assertThat(MarkFlowRenderService.parseWarnings(null)).isEmpty();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    @Test
    void renderCarriesWarningsIntoTheResult() {
        // 无降级：warnings 为空（渲染正常时运行终态不该被误判成「有警告」）
        MarkFlowRenderService clean = service(GUIDE_JSON,
                "{\"ok\":true,\"html\":\"<p style='margin:0'>正文</p>\",\"meta\":{\"title\":\"t\"}}");
        assertThat(clean.render("# t", null, null).warnings()).isEmpty();

        // 上游 meta.warnings：照单收录
        MarkFlowRenderService upstream = service(GUIDE_JSON,
                "{\"ok\":true,\"html\":\"<p style='margin:0'>正文</p>\","
                        + "\"meta\":{\"title\":\"t\",\"warnings\":[\"容器未闭合\"]}}");
        assertThat(upstream.render("# t", null, null).warnings()).containsExactly("容器未闭合");

        // 上游没报（实测如此）但产物里留着未识别语法：本地扫描兜底
        MarkFlowRenderService leaked = service(GUIDE_JSON,
                "{\"ok\":true,\"html\":\"<p style='margin:0'>:::compare</p>\",\"meta\":{\"title\":\"t\"}}");
        assertThat(leaked.render("# t", null, null).warnings())
                .hasSize(1).allSatisfy(warning -> assertThat(warning).contains(":::compare"));
    }

    /**
     * 与 {@link #detectsLeakedSyntaxInRenderedHtml} 相反的一类降级：渲染器**认得**这个标签，
     * HTTP 200 + ok:true，却把它的内容整块丢掉。判据来自 2026-09-12 的四种写法探针——
     * {@code <timeline>} 产物长度恒为 0，与正文混排时前后正文都在、时间线的字一个字不剩。
     */
    @Test
    void detectsComponentContentThatSilentlyDisappeared() {
        java.util.List<String> dropped = MarkFlowRenderService.detectDroppedBlocks(
                "<timeline>\n2024年 | 第一件事\n</timeline>",
                "<section style=\"margin:0\"><p style=\"margin:0\">正文在前。正文在后。</p></section>");
        assertThat(dropped).hasSize(1);
        assertThat(dropped.get(0)).contains("<timeline>").contains("找不到");
    }

    /**
     * 真实产物的形状与源文不一致时不能误报（2026-09-12 实测校准，详见 detectDroppedBlocks 注释）：
     * {@code <steps>} 会在条目间插入序号，{@code <engage>} 的内容由渲染器自带的固定文案替换。
     */
    @Test
    void droppedBlockScanToleratesRendererRewrittenContent() {
        assertThat(MarkFlowRenderService.detectDroppedBlocks(
                "<steps>\n第一步内容。\n\n第二步内容。\n</steps>",
                "<section><td><p><span leaf=\"\">1</span></p><p>第一步内容。</p></td>"
                        + "<td><p><span leaf=\"\">2</span></p><p>第二步内容。</p></td></section>"))
                .as("渲染器插入的条目序号不该被当成内容丢失").isEmpty();

        assertThat(MarkFlowRenderService.detectDroppedBlocks(
                "<engage type=\"DA02\">感谢阅读</engage>",
                "<section style=\"margin:24px 0\"><section>感谢你的阅读与支持！</section>"
                        + "<section>喜欢就互动一下吧～</section></section>"))
                .as("engage 的内容是渲染器自带的固定文案，作者写的只是占位符").isEmpty();
    }

    /**
     * 属性文字只在**确实会进产物**的属性上做判据：{@code <p-title>} 的 title/subtitle 会渲染成正文，
     * 而 {@code <breaking label="BREAKING">} 的 label 渲染器根本不输出（产物只剩正文）。
     */
    @Test
    void droppedBlockScanOnlyProbesAttributesThatReachTheOutput() {
        assertThat(MarkFlowRenderService.detectDroppedBlocks(
                "<breaking label=\"BREAKING\">突发消息标题</breaking>",
                "<section><section style=\"font-size:14px\">突发消息标题</section></section>"))
                .as("label 不输出不算内容丢失").isEmpty();

        assertThat(MarkFlowRenderService.detectDroppedBlocks(
                "<p-title number=\"01\" title=\"苹果终于折叠：iPhone Duo 15999元起\"></p-title>",
                "<p style=\"margin:0\">苹果终于折叠：iPhoneDuo15999元起</p>"))
                .as("产物去空白后能对上就不该报").isEmpty();

        java.util.List<String> dropped = MarkFlowRenderService.detectDroppedBlocks(
                "<p-title number=\"01\" title=\"苹果终于折叠：iPhone Duo 15999元起\"></p-title>",
                "<p style=\"margin:0\">完全不相干的正文</p>");
        assertThat(dropped).hasSize(1);
        assertThat(dropped.get(0)).contains("组件属性里的文字");
    }

    /**
     * 源码里的 HTML 实体与产物解码后的文字要能对上（run#75 真实误报，2026-09-13）。
     *
     * <p>模型把副标题写成 {@code subtitle="关于&quot;pace the frontier&quot;"}，那一块其实渲染得好好的，
     * 但探针带着字面量 {@code &quot} 去比对已解码的产物，白报一次「组件属性里的文字找不到」。
     */
    @Test
    void droppedBlockScanDecodesEntitiesInSourceProbe() {
        assertThat(MarkFlowRenderService.detectDroppedBlocks(
                "<p-title number=\"03\" title=\"Anthropic 提出安全平衡计划\" "
                        + "subtitle=\"关于&quot;pace the frontier&quot;\"></p-title>",
                "<p style=\"margin:0\">Anthropic提出安全平衡计划</p>"
                        + "<p style=\"letter-spacing:1.6px\">关于&quot;pace the frontier&quot;</p>"))
                .as("源码里的 &quot; 与产物里的引号是同一段文字").isEmpty();

        assertThat(MarkFlowRenderService.detectDroppedBlocks(
                "<p-title number=\"03\" title=\"Anthropic 提出安全平衡计划\" "
                        + "subtitle=\"关于&quot;pace the frontier&quot;\"></p-title>",
                "<p style=\"margin:0\">完全不相干的正文</p>"))
                .as("解码后仍然对不上时该报还是要报")
                .anySatisfy(warning -> assertThat(warning).contains("pacethefrontier"));
    }
}
