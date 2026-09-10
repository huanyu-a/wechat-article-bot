package ink.icoding.wechat.article.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.settings.RenderConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MarkFlow 渲染服务客户端（skills-agent-plan 5.10.3）。
 * 语法指令实时获取 + 进程内缓存（TTL 过期 / 渲染 400 失效 / 测试连接手动刷新），
 * 渲染前 /uploads/ 相对路径 URL 绝对化，渲染后危险元素定向剥离（黑名单语义，未知标签保留）。
 * 注意：java.net.http.HttpClient 不读取系统代理环境变量，需经代理出网的部署需在此显式配置 ProxySelector。
 */
@Service
public class MarkFlowRenderService {
    private static final Logger log = LoggerFactory.getLogger(MarkFlowRenderService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    // 注意：'(' 是正则元字符必须转义为 \\(；']' 非元字符无需转义
    private static final Pattern UPLOAD_MD_LINK = Pattern.compile("]\\(/uploads/");
    private static final Pattern UPLOAD_HTML_SRC = Pattern.compile("src=\"/uploads/");    private static final Pattern DANGEROUS_BLOCK = Pattern.compile(
            "<(script|iframe|object|embed)\\b[^>]*>.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DANGEROUS_SELF_CLOSING = Pattern.compile(
            "<(script|iframe|object|embed)\\b[^>]*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_ATTRIBUTE = Pattern.compile(
            "\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_URI = Pattern.compile(
            "(href|src|action)\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*'|\\s*javascript:[^\\s>]*)",
            Pattern.CASE_INSENSITIVE);

    private final RenderConfigService renderConfigService;
    private final HttpClient httpClient;
    private volatile String cachedGuide;
    private volatile long guideFetchedAtMillis;

    public MarkFlowRenderService(RenderConfigService renderConfigService) {
        this.renderConfigService = renderConfigService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /** 渲染服务的可用性前置校验：未启用、令牌缺失时给出明确中文错误（不静默回落 PROMPT 引擎）。 */
    public RenderConfigService.RuntimeConfig requireRuntime() {
        RenderConfigService.RuntimeConfig config = renderConfigService.runtime();
        if (!config.enabled()) {
            throw new BusinessException("排版技能需要 MarkFlow 渲染服务，请到系统设置 → 排版渲染服务启用并配置令牌，或改用指令式排版技能");
        }
        if (config.token() == null || config.token().isBlank()) {
            throw new BusinessException("MarkFlow 渲染服务未配置令牌，请到系统设置 → 排版渲染服务填写渲染令牌");
        }
        return config;
    }

    /** 获取 MarkFlow 扩展语法指令（实时获取从不内置副本；TTL 过期重取；与线上渲染引擎严格同步）。 */
    public String fetchSyntaxGuide() {
        RenderConfigService.RuntimeConfig config = requireRuntime();
        long ttlMillis = Math.max(60, config.syntaxCacheTtlSeconds()) * 1000L;
        String guide = cachedGuide;
        if (guide != null && System.currentTimeMillis() - guideFetchedAtMillis < ttlMillis) {
            return guide;
        }
        guide = httpGet(config.baseUrl() + "/__markflow_render", config.token());
        JsonNode body = parseBody(guide);
        if (!body.path("ok").asBoolean(false) || body.path("guide").asText("").isBlank()) {
            throw new BusinessException("获取 MarkFlow 语法指令失败：" + errorText(body));
        }
        cachedGuide = body.path("guide").asText();
        guideFetchedAtMillis = System.currentTimeMillis();
        return cachedGuide;
    }

    /** 渲染结果：渲染后 HTML 与元信息。LLM 上下文里只应有 Markdown，HTML 仅在此工具边界出现。 */
    public record RenderResult(String html, String title, String summary, String themeAccent, String themeDark) {
    }

    /**
     * 渲染 MarkFlow 语法 Markdown 为内联样式 HTML。
     * 先做 /uploads/ 相对路径绝对化（Spike 实测：上游对相对路径图片原样透传不补域名），
     * 再调用渲染 API，以 body.ok 为最终判据（HTTP 200 但 ok=false 同样视为失败），
     * 最后做危险元素定向剥离。
     */
    public RenderResult render(String markdown, String accent, String dark) {
        RenderConfigService.RuntimeConfig config = requireRuntime();
        if (markdown == null || markdown.isBlank()) {
            throw new BusinessException("渲染内容为空");
        }
        String prepared = absoluteImageUrls(markdown, config.siteBaseUrl());
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("markdown", prepared);
            if (accent != null && !accent.isBlank()) payload.put("accent", accent.trim());
            if (dark != null && !dark.isBlank()) payload.put("dark", dark.trim());
            String response = httpPost(config.baseUrl() + "/__markflow_render", config.token(),
                    MAPPER.writeValueAsString(payload));
            JsonNode body = parseBody(response);
            if (!body.path("ok").asBoolean(false)) {
                String error = errorText(body);
                // 定向失效语法缓存（方案 5.10.3 三级失效②）：只有错误疑似语法/组件问题时才失效——
                // 主题色非法、参数缺失等失败与语法无关，清缓存只会白白多一次外呼。
                if (looksLikeSyntaxError(error)) {
                    cachedGuide = null;
                }
                throw new BusinessException("MarkFlow 渲染失败：" + error);
            }
            String html = sanitizeHtml(body.path("html").asText(""));
            if (html.isBlank()) {
                throw new BusinessException("MarkFlow 渲染失败：服务返回了空内容");
            }
            return new RenderResult(html, body.path("meta").path("title").asText(null),
                    body.path("meta").path("summary").asText(null),
                    body.path("theme").path("accent").asText(null), body.path("theme").path("dark").asText(null));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            Throwable cause = exception;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            if (cause instanceof java.net.http.HttpTimeoutException) {
                throw new BusinessException("MarkFlow 渲染服务响应超时（30 秒），请稍后重试");
            }
            throw new BusinessException("MarkFlow 渲染服务不可达：" + rootMessage(exception));
        }
    }

    /** 测试连接：真实 GET 一次语法指令，顺带刷新语法缓存（上游组件库更新后的手动失效入口）。 */
    public TestResult testConnection() {
        try {
            String guide = fetchSyntaxGuide();
            return new TestResult(true, "连接正常，语法指令 " + guide.length() + " 字符", guide.length());
        } catch (BusinessException exception) {
            return new TestResult(false, exception.getMessage(), 0);
        }
    }

    /**
     * 错误文本是否疑似语法/组件问题（决定是否失效语法缓存，方案 5.10.3）。
     * 主题色、参数类错误不清缓存；空错误信息保守清缓存（宁可多拉一次语法）。
     */
    static boolean looksLikeSyntaxError(String error) {
        if (error == null || error.isBlank()) return true;
        String lower = error.toLowerCase(java.util.Locale.ROOT);
        for (String keyword : SYNTAX_ERROR_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private static final List<String> SYNTAX_ERROR_KEYWORDS = List.of(
            "语法", "syntax", "markdown", "容器", "container", "未知", "unknown",
            "unsupported", "不支持", "解析", "parse", "组件", "component", "block");

    public record TestResult(boolean ok, String message, int guideLength) {
    }

    /** 渲染前 URL 绝对化：/uploads/ 相对路径 → {siteBaseUrl}/uploads/；siteBaseUrl 未配置则跳过并告警。 */
    String absoluteImageUrls(String markdown, String siteBaseUrl) {
        if (markdown == null || !markdown.contains("/uploads/")) return markdown;
        if (siteBaseUrl == null || siteBaseUrl.isBlank()) {
            log.warn("MarkFlow 渲染前 URL 绝对化跳过：未配置站点公网地址 site_base_url，正文图片在渲染产物中可能无法访问");
            return markdown;
        }
        String base = siteBaseUrl.endsWith("/") ? siteBaseUrl.substring(0, siteBaseUrl.length() - 1) : siteBaseUrl;
        return UPLOAD_MD_LINK.matcher(UPLOAD_HTML_SRC.matcher(markdown)
                        .replaceAll("src=\"" + Matcher.quoteReplacement(base) + "/uploads/"))
                .replaceAll("](" + Matcher.quoteReplacement(base) + "/uploads/");
    }

    /** 危险元素定向剥离（黑名单语义而非严格标签白名单——上游新增组件标签不被误杀）。 */
    String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) return html;
        String result = DANGEROUS_BLOCK.matcher(html).replaceAll("");
        result = DANGEROUS_SELF_CLOSING.matcher(result).replaceAll("");
        result = EVENT_ATTRIBUTE.matcher(result).replaceAll("");
        result = JS_URI.matcher(result).replaceAll("$1=\"#\"");
        return result;
    }

    /** 包级可见以便单测覆写桩掉真实 HTTP。 */
    String httpGet(String url, String token) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("X-Render-Token", token)
                .GET().build();
        return send(request);
    }

    /** 包级可见以便单测覆写桩掉真实 HTTP。 */
    String httpPost(String url, String token, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("X-Render-Token", token)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        return send(request);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401) throw new BusinessException("MarkFlow 渲染服务鉴权失败（401），请检查渲染令牌是否正确");
            if (status == 413) throw new BusinessException("Markdown 内容超过渲染服务 2MB 上限（413），请精简文章");
            if (status >= 400) {
                String error = errorText(parseBody(response.body()));
                throw new BusinessException("MarkFlow 渲染服务返回错误（HTTP " + status + "）" +
                        (error.isBlank() ? "" : "：" + error));
            }
            return response.body();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            Throwable cause = exception;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            if (cause instanceof java.net.http.HttpTimeoutException) {
                throw new BusinessException("MarkFlow 渲染服务响应超时（30 秒），请稍后重试");
            }
            throw new BusinessException("MarkFlow 渲染服务不可达：" + rootMessage(exception));
        }
    }

    private JsonNode parseBody(String body) {
        try {
            return MAPPER.readTree(body == null ? "{}" : body);
        } catch (Exception exception) {
            throw new BusinessException("MarkFlow 渲染服务返回了无法解析的内容");
        }
    }

    private String errorText(JsonNode body) {
        String error = body.path("error").asText("");
        return error.isBlank() ? "未知错误" : error;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
