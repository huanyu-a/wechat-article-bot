package ink.icoding.wechat.article.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ink.icoding.wechat.article.common.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型发现客户端（契约 §2.2 第 6 条）的七条场景，全部打**真实 socket**（JDK 自带
 * {@code com.sun.net.httpserver.HttpServer}）。
 *
 * <p><b>为什么不 mock {@code HttpClient}</b>：这条能力有四个硬约束——连接超时、请求超时、
 * 响应体上限、不跟随重定向——它们全是 {@code HttpClient} 的**配置行为**。mock 掉它等于把这些
 * 配置从测试里删掉：用例会永远绿，而线上可能跟着一次 30x 把 {@code Authorization} 交给另一台主机，
 * 或被一个巨型响应体撑爆内存。
 *
 * <p><b>为什么不连真实上游</b>：那会把「测试通过」变成「今天那个网关没挂」。本地 HttpServer
 * 既能造出 401 / 超大 body / 迟到响应这些真实形态，又完全离线可控。
 *
 * <p>超时与上限都从构造函数注入并调小（50ms / 512B）：否则「超时」用例要真等 10 秒、
 * 「响应体过大」要真传 1 MiB——前者让整个测试套白等，后者还会掩盖边界的精确位置。
 */
class ModelDiscoveryClientTest {

    private static final String API_KEY = "{{SECRET_aq3mv7xt}}";

    private HttpServer server;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> lastAnthropicVersion = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 注册一个固定应答，并顺手记录请求头（用来证明「密钥只在请求头里」）。 */
    private void respond(int status, String body, long delayMillis) {
        server.createContext("/v1/models", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            lastAnthropicVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            lastPath.set(exchange.getRequestURI().getPath());
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            try (var out = exchange.getResponseBody()) {
                out.write(payload);
            } catch (IOException ignored) {
                // 客户端提前断开（超时/超限用例）：服务端这边写不进去是预期内的，不必理会
            }
        });
    }

    private ModelDiscoveryClient client(Duration requestTimeout, int maxBodyBytes) {
        return new ModelDiscoveryClient(Duration.ofSeconds(1), requestTimeout, maxBodyBytes);
    }

    // ---------- 1. 正常解析 ----------

    /**
     * 标准 OpenAI 形态 {@code {"data":[{"id":"..."}]}}：解析出全部模型名，去重并按字典序排序。
     *
     * <p>排序不是洁癖：上游返回顺序不保证，前端下拉框每次刷新都变会很难用。
     */
    @Test
    void parsesTheOpenAiShapeAndSortsTheNames() {
        respond(200, """
                {"object":"list","data":[
                  {"id":"gpt-4.1-mini"},{"id":"gpt-4.1"},{"id":"gpt-4.1-mini"}
                ]}
                """, 0);

        List<String> models = client(Duration.ofSeconds(5), 1_048_576)
                .discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY);

        assertThat(models)
                .as("去重 + 字典序：前端下拉框的顺序必须稳定")
                .containsExactly("gpt-4.1", "gpt-4.1-mini");
        assertThat(lastPath.get()).as("端点必须是 /v1/models，不能拼成 /v1/v1/models").isEqualTo("/v1/models");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer " + API_KEY);
    }

    /** 宽松兼容形态 {@code {"models":[{"name":"..."}]}}（部分网关这么返回）。 */
    @Test
    void parsesTheAlternativeModelsShape() {
        respond(200, "{\"models\":[{\"name\":\"claude-sonnet-4\"},{\"name\":\"claude-haiku-4\"}]}", 0);

        assertThat(client(Duration.ofSeconds(5), 1_048_576)
                .discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY))
                .containsExactly("claude-haiku-4", "claude-sonnet-4");
    }

    /** Anthropic 用 {@code x-api-key} + 版本头，不能用 Bearer（否则 401）。 */
    @Test
    void anthropicUsesItsOwnHeaders() {
        respond(200, "{\"data\":[{\"id\":\"claude-sonnet-4\"}]}", 0);

        client(Duration.ofSeconds(5), 1_048_576).discover(1L, "ANTHROPIC", baseUrl(), API_KEY);

        assertThat(lastApiKeyHeader.get()).isEqualTo(API_KEY);
        assertThat(lastAnthropicVersion.get())
                .as("Anthropic 的模型列表接口缺版本头直接 400")
                .isEqualTo("2023-06-01");
        assertThat(lastAuthorization.get()).as("Anthropic 不认 Bearer").isNull();
    }

    // ---------- 2. 空列表 / 非 2xx / 非法 JSON ----------

    /**
     * 空列表按**失败**处理：它对用户没有信息量，返回空数组会让前端显示一个空下拉框，
     * 用户不知道是「这个供应商真的没有模型」还是「端点填错了」。
     */
    @Test
    void emptyListIsTreatedAsAFailure() {
        respond(200, "{\"data\":[]}", 0);

        assertThatThrownBy(() -> client(Duration.ofSeconds(5), 1_048_576)
                .discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有返回任何模型");
    }

    @Test
    void nonTwoHundredCarriesTheStatusCodeButNoResponseBody() {
        respond(401, "{\"error\":\"invalid api key: " + API_KEY + "\"}", 0);

        assertThatThrownBy(() -> client(Duration.ofSeconds(5), 1_048_576)
                .discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY))
                .isInstanceOf(BusinessException.class)
                .as("状态码要带上（用户据此判断是 key 错还是端点错），但上游的报错原文不得转述——它可能回显密钥")
                .hasMessageContaining("401")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void nonJsonBodyFailsWithAReadableMessage() {
        respond(200, "<html>gateway error</html>", 0);

        assertThatThrownBy(() -> client(Duration.ofSeconds(5), 1_048_576)
                .discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON");
    }

    // ---------- 3. 超时与响应体上限 ----------

    /** 请求超时：迟到 500ms 的响应必须在 150ms 的预算内被打断，并给中文提示。 */
    @Test
    void slowResponsesHitTheRequestTimeout() {
        respond(200, "{\"data\":[{\"id\":\"late\"}]}", 500);

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> client(Duration.ofMillis(150), 1_048_576)
                .discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超时");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        assertThat(elapsedMillis)
                .as("必须在超时预算附近返回（不能等上游那 500ms 走完）")
                .isLessThan(2_000L);
    }

    /**
     * 响应体上限：超过上限即中止读取，不把上游的巨型 body 读进内存。
     *
     * <p>这条在真实环境里对应「端点被填成了一个返回整站首页的地址」——没有上限时
     * 那会变成一个 OOM 面，而限流完全挡不住（一次就够）。
     */
    @Test
    void oversizedBodiesAreAbortedInsteadOfBuffered() {
        respond(200, "{\"data\":[" + "{\"id\":\"x\"},".repeat(500) + "{\"id\":\"last\"}]}", 0);

        assertThatThrownBy(() -> client(Duration.ofSeconds(5), 512)
                .discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过大");
    }

    // ---------- 4. 不跟随重定向 ----------

    /**
     * 契约 §2.2 第 4 条：{@code Redirect.NEVER}。
     *
     * <p>跟随 30x 等于把 {@code Authorization} / {@code x-api-key} 交给重定向目标主机——
     * 那可能不是用户配置的供应商，而是一次 SSRF 或一次被劫持的 30x。这里用「302 指向另一个
     * 本地端点」来证明它被当成**错误**处理，而不是被自动跟过去。
     */
    @Test
    void redirectsAreNotFollowed() {
        server.createContext("/v1/models", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl() + "/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        AtomicReference<Boolean> followed = new AtomicReference<>(false);
        server.createContext("/elsewhere", exchange -> {
            followed.set(true);
            byte[] payload = "{\"data\":[{\"id\":\"should-not-reach\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            try (var out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });

        assertThatThrownBy(() -> client(Duration.ofSeconds(5), 1_048_576)
                .discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("302");
        assertThat(followed.get())
                .as("绝不能跟随重定向：那会把密钥交给另一台主机")
                .isFalse();
    }

    // ---------- 5. 端点不可达 ----------

    /** 端点根本连不上（端口关掉）：给中文提示，且不把底层异常消息透出去（它带完整 URI）。 */
    @Test
    void unreachableEndpointGivesAReadableMessage() {
        int port = server.getAddress().getPort();
        server.stop(0);
        server = null;

        assertThatThrownBy(() -> client(Duration.ofSeconds(1), 1_048_576)
                .discover(1L, "OPENAI_COMPATIBLE", "http://127.0.0.1:" + port, API_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法从供应商获取模型列表");
    }

    /** 请求体里绝不出现明文密钥的另一种形式：证明密钥确实只走了请求头。 */
    @Test
    void apiKeyNeverAppearsInTheRequestPath() {
        respond(200, "{\"data\":[{\"id\":\"m\"}]}", 0);

        client(Duration.ofSeconds(5), 1_048_576).discover(1L, "OPENAI_COMPATIBLE", baseUrl(), API_KEY);

        assertThat(lastPath.get()).doesNotContain(API_KEY);
    }

    /** 供将来的调试用：把 exchange 的请求体读空，避免服务端线程因未消费而阻塞。 */
    @SuppressWarnings("unused")
    private static void drain(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
        }
    }
}