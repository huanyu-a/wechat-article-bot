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
}
