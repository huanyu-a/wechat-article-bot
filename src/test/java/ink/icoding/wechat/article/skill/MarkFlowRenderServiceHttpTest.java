package ink.icoding.wechat.article.skill;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.settings.RenderConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MarkFlowRenderService 真实 HTTP 集成测试（skills-agent-plan 8.1）。
 *
 * 与 {@link MarkFlowRenderServiceTest} 的分工：那个类覆写 httpGet/httpPost 桩掉传输层，验证的是解析与缓存逻辑；
 * 本类用 JDK 内置 HttpServer 起真实 socket，验证**传输层契约**——请求头（X-Render-Token / Content-Type）、
 * 请求体（绝对化后的 markdown + accent/dark）、以及 401/413/5xx/不可解析响应到中文提示的映射。
 * 这些正是单测覆写方法后**永远走不到**的分支（头写错、状态码漏映射在生产上才会暴露）。
 */
class MarkFlowRenderServiceHttpTest {
    private HttpServer server;
    private String baseUrl;
    private final Deque<Response> responses = new ArrayDeque<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/__markflow_render", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String token = exchange.getRequestHeaders().getFirst("X-Render-Token");
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), token,
                contentType, body));
        Response response = responses.poll();
        if (response == null) response = new Response(500, "{\"ok\":false,\"error\":\"测试未预置响应\"}");
        byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.status(), payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private MarkFlowRenderService service() {
        RenderConfigService.RuntimeConfig config = new RenderConfigService.RuntimeConfig(true, "MARKFLOW",
                baseUrl, "token-abc", "https://site.example.com", 21600);
        return new MarkFlowRenderService(null) {
            @Override
            public RenderConfigService.RuntimeConfig requireRuntime() {
                return config;
            }
        };
    }

    @Test
    void renderSendsTokenHeaderAndAbsolutizedMarkdownOverRealHttp() {
        responses.add(new Response(200, "{\"ok\":true,\"html\":\"<p>正文</p>\","
                + "\"meta\":{\"title\":\"标题\",\"summary\":\"摘要\"},"
                + "\"theme\":{\"accent\":\"#27ae60\",\"dark\":\"#1d8348\"},"
                + "\"preview\":\"<!DOCTYPE html>预览页\"}"));
        MarkFlowRenderService.RenderResult result = service()
                .render("![图](/uploads/a.png)", "#27ae60", null);

        assertThat(requests).hasSize(1);
        Request request = requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/__markflow_render");
        assertThat(request.tokenHeader()).isEqualTo("token-abc");
        assertThat(request.contentType()).contains("application/json");
        // 传输层拿到的必须是绝对化之后的正文（相对路径图片在渲染产物里会变坏图）
        assertThat(request.body()).contains("https://site.example.com/uploads/a.png")
                .doesNotContain("](/uploads/")
                .contains("#27ae60");
        assertThat(result.html()).isEqualTo("<p>正文</p>");
        assertThat(result.title()).isEqualTo("标题");
        assertThat(result.summary()).isEqualTo("摘要");
        assertThat(result.themeAccent()).isEqualTo("#27ae60");
        assertThat(result.themeDark()).isEqualTo("#1d8348");
    }

    @Test
    void fetchSyntaxGuideOverRealHttpUsesGetWithToken() {
        responses.add(new Response(200, "{\"ok\":true,\"guide\":\"# MarkFlow 语法\"}"));
        assertThat(service().fetchSyntaxGuide()).isEqualTo("# MarkFlow 语法");
        assertThat(requests.get(0).method()).isEqualTo("GET");
        assertThat(requests.get(0).tokenHeader()).isEqualTo("token-abc");
    }

    @Test
    void guideFailureOverRealHttpSurfacesUpstreamError() {
        responses.add(new Response(200, "{\"ok\":false,\"error\":\"guide 不可用\"}"));
        assertThatThrownBy(() -> service().fetchSyntaxGuide())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("获取 MarkFlow 语法指令失败")
                .hasMessageContaining("guide 不可用");
    }

    @Test
    void authenticationFailureMapsToReadableMessage() {
        responses.add(new Response(401, "{\"ok\":false,\"error\":\"invalid token\"}"));
        assertThatThrownBy(() -> service().render("# t", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("鉴权失败");
    }

    @Test
    void payloadTooLargeMapsToReadableMessage() {
        responses.add(new Response(413, "{\"ok\":false,\"error\":\"too large\"}"));
        assertThatThrownBy(() -> service().render("# t", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2MB");
    }

    @Test
    void serverErrorIncludesUpstreamErrorText() {
        responses.add(new Response(500, "{\"ok\":false,\"error\":\"渲染引擎内部错误\"}"));
        assertThatThrownBy(() -> service().render("# t", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining("渲染引擎内部错误");
    }

    @Test
    void unparseableResponseMapsToReadableMessage() {
        responses.add(new Response(200, "<html>不是 JSON</html>"));
        assertThatThrownBy(() -> service().render("# t", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法解析");
    }

    private record Response(int status, String body) {
    }

    private record Request(String method, String path, String tokenHeader, String contentType, String body) {
    }
}
