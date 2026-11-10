package ink.icoding.wechat.article.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.wechat.article.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 模型发现出站客户端（契约 §2.2）：用**供应商自己的端点与密钥**拉一次 {@code GET /v1/models}。
 *
 * <p>为什么是「用户显式点击」的一次性探测，而不是自动定时刷新（契约 §5）：模型列表只在
 * 「我刚填了端点，想确认能连上、能拿到哪些模型」这一刻有用；做成后台轮询等于给每个供应商
 * 定期发一次带密钥的请求，既费额度，也会在供应商临时故障时反复告警。
 *
 * <p>为什么单独一个类而不是塞进 {@link LlmProviderService}：HTTP 的四个硬约束（连接超时、
 * 请求超时、响应体上限、不跟随重定向）都是**可以单独证伪**的行为，放在 CRUD 服务里只能靠
 * mock 掉整条链路才测得到。这里抽出来之后，测试用本地 {@code HttpServer} 就能逐条钉住。
 *
 * <p><b>密钥边界</b>：明文 {@code apiKey} 只出现在请求头里。日志只记
 * 供应商 id / 端点 / HTTP 状态码 / 耗时，**不记请求头、不记响应体**
 * ——响应体里可能有上游的报错原文，而报错原文偶尔会回显 Authorization 头。
 */
@Component
public class ModelDiscoveryClient {
    private static final Logger log = LoggerFactory.getLogger(ModelDiscoveryClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 连接超时：端点写错时不该让用户对着按钮干等（契约 §2.2 第 4 条）。 */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 单次请求超时（契约 §2.2 第 4 条）。 */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    /** 响应体上限 1 MiB：模型列表是几十条 JSON，超过这个量级只可能是上游异常或攻击面。 */
    static final int MAX_BODY_BYTES = 1024 * 1024;
    /** Anthropic 的模型列表接口要求带版本头，缺了直接 400（契约 §2.2 第 3 条）。 */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final Duration requestTimeout;
    private final int maxBodyBytes;
    private final HttpClient httpClient;

    public ModelDiscoveryClient() {
        this(CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_BODY_BYTES);
    }

    /**
     * 测试用构造：超时与上限可缩短。
     *
     * <p>不缩短的话，「超时」用例要真等 10 秒、「响应体过大」用例要真传 1 MiB ——
     * 前者让整个测试套白等，后者既慢又掩盖了边界的精确位置。
     */
    ModelDiscoveryClient(Duration connectTimeout, Duration requestTimeout, int maxBodyBytes) {
        this.requestTimeout = requestTimeout;
        this.maxBodyBytes = maxBodyBytes;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                // 契约 §2.2 第 4 条：绝不跟随重定向。
                // 跟随重定向等于把 Authorization / x-api-key 交给重定向目标主机——那可能不是
                // 用户配置的供应商，而是一次 SSRF 或者一次被劫持的 30x。
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 拉取模型名列表（已去重、按字典序排序，顺序稳定便于前端复现）。
     *
     * <p>端点拼接在**归一化之后**：{@code LlmProfileService.normalizeBaseUrl} 已经剥掉了
     * {@code /v1} 与各种 {@code /v1/chat/completions} 后缀，这里只需接 {@code /v1/models}。
     * 颠倒顺序（先拼再归一化）会得到 {@code /v1/v1/models}，是最常见的 404 来源。
     *
     * @param providerId 只用于日志定位，不参与请求
     * @param protocol   {@code OPENAI_COMPATIBLE} / {@code OPENAI_RESPONSES} / {@code ANTHROPIC}
     * @param baseUrl    已归一化的端点基址（不含 {@code /v1}）
     * @param apiKey     解密后的明文 key（只在请求头出现，绝不进日志）
     * @return 非空模型名列表（空列表按失败处理：它对用户没有信息量）
     */
    public List<String> discover(Long providerId, String protocol, String baseUrl, String apiKey) {
        String url = baseUrl + "/v1/models";
        long startedAt = System.nanoTime();
        int status = -1;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET();
            if ("ANTHROPIC".equals(protocol)) {
                builder.header("x-api-key", apiKey).header("anthropic-version", ANTHROPIC_VERSION);
            } else {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<InputStream> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            status = response.statusCode();
            String body = readLimited(response.body());
            if (status < 200 || status >= 300) {
                throw new BusinessException("供应商返回错误（HTTP " + status
                        + "），未能获取模型列表。请检查 Base URL 与 API Key 是否正确");
            }
            List<String> models = parseModels(body);
            if (models.isEmpty()) {
                throw new BusinessException("该供应商没有返回任何模型，请确认 Base URL 指向的是支持 /v1/models 的端点");
            }
            return models;
        } catch (BusinessException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new BusinessException("获取模型列表超时（" + requestTimeout.toSeconds()
                    + " 秒），请稍后重试，或检查 Base URL 是否可达");
        } catch (InterruptedException exception) {
            // 恢复中断位：吞掉 InterruptedException 而不重置，会把中断信号永久丢掉
            Thread.currentThread().interrupt();
            throw new BusinessException("获取模型列表被中断，请重试");
        } catch (RuntimeException | IOException exception) {
            // 不把底层异常消息透给用户：它可能带上完整 URI 甚至请求头，而这里只需要一个可行动的提示
            throw new BusinessException("无法从供应商获取模型列表，请检查 Base URL 是否可达");
        } finally {
            log.info("模型发现：供应商 id={} 端点={} 状态码={} 耗时={}ms",
                    providerId, url, status, (System.nanoTime() - startedAt) / 1_000_000L);
        }
    }

    /** 带上限的读取（契约 §2.2 第 4 条）：超限即中止，不把上游的巨型 body 读进内存。 */
    private String readLimited(InputStream body) throws IOException {
        try (InputStream stream = body) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(chunk)) != -1) {
                total += read;
                if (total > maxBodyBytes) {
                    throw new BusinessException("供应商返回的模型列表过大（超过 " + maxBodyBytes
                            + " 字节），已中止读取");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * 解析两种常见形态：{@code {"data":[{"id":"gpt-4.1"}]}}（OpenAI）与
     * {@code {"models":[{"name":"claude-sonnet-4"}]}}（宽松兼容）。
     *
     * <p>用 {@link TreeSet} 收集：既去重（同一模型可能同时出现在两个数组里），
     * 又给出稳定顺序（上游的返回顺序不保证，前端下拉框每次刷新都变会很难用）。
     */
    private List<String> parseModels(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException("该供应商没有返回任何模型，请确认 Base URL 指向的是支持 /v1/models 的端点");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception exception) {
            throw new BusinessException("供应商返回的不是合法 JSON，无法解析模型列表");
        }
        if (root == null) {
            throw new BusinessException("供应商返回的不是合法 JSON，无法解析模型列表");
        }
        Set<String> models = new TreeSet<>();
        collectNames(root.path("data"), "id", models);
        collectNames(root.path("models"), "name", models);
        return new ArrayList<>(models);
    }

    private void collectNames(JsonNode array, String field, Set<String> out) {
        if (array == null || !array.isArray()) return;
        for (JsonNode node : array) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) out.add(value.trim());
        }
    }
}