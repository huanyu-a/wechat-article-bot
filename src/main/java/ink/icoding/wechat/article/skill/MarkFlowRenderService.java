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
    /** 代码块：其中的字面 `:::` / 标签是**正文内容**，不是未识别的语法，扫描降级时要排除。 */
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "<(pre|code)\\b[^>]*>.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** 未被渲染器识别的组件标签（识别到的组件都会被展开成 <section>/<p>/<h3> 等内联样式节点）。 */
    private static final Pattern LEAKED_COMPONENT = Pattern.compile(
            "<(steps|timeline|compare|cta|badges|badge|statement|lead|engage-label|engage-card|engage"
                    + "|p-title|title|icon|breaking)\\b",
            Pattern.CASE_INSENSITIVE);
    /**
     * 自闭合组件被写成成对标签时**收尾标签**原样落进产物。
     *
     * <p>实测（2026-09-12）：{@code <badge type="tip">推荐</badge>} 的产物是
     * {@code …<span leaf="">tip</span>推荐</badge>}——徽章本身渲染正常，但 {@code </badge>} 成了正文文字。
     * 这种形态 {@link #LEAKED_COMPONENT} 抓不到（它匹配的是开标签），必须单独扫收尾标签。
     */
    private static final Pattern LEAKED_CLOSING =
            Pattern.compile("</(badge|icon|img|p-title|statement|lead|badges|cta|breaking)\\s*>",
                    Pattern.CASE_INSENSITIVE);
    /** 未被识别的容器语法（实测：未闭合的 :::compare 会把整段后续内容吞掉并字面输出）。 */
    private static final Pattern LEAKED_CONTAINER = Pattern.compile(":{3,}\\s*[A-Za-z]*");
    /** 组件块（成对标签）：用于核对它的文字有没有出现在渲染产物里。 */
    private static final Pattern COMPONENT_BLOCK = Pattern.compile(
            "<([a-z][a-z0-9-]*)\\b[^>]*>(.*?)</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /**
     * 内容由渲染器自带的组件：源文里写的文字**只是占位符**，产物里必然是上游固定文案。
     *
     * <p>实测（2026-09-12 探针，可反证）：{@code <engage type="DA02">感谢阅读</engage>} 的产物是
     * 「感谢你的阅读与支持！喜欢就互动一下吧～」，作者写的「感谢阅读」一个字都不出现——
     * 而语法指令六.8 / 七.5 恰恰**推荐**这样写（只给 type，不给内容），所以这不是「内容丢失」。
     */
    private static final java.util.Set<String> SELF_COPY_COMPONENTS =
            java.util.Set.of("engage", "engage-card", "engage-label");
    /**
     * 组件属性里承载的文字（{@code <p-title title="…">} 这类正文写在属性上的组件）。
     *
     * <p>只认 title/subtitle：实测这两个属性的文字会进产物（{@code <p-title>}、{@code <engage-card>} 均如此），
     * 而 {@code <breaking label="BREAKING">} 的 label 渲染器根本不输出（产物只剩正文「突发消息标题」），
     * 把它当成判据只会每篇都误报一次。
     */
    private static final Pattern TEXT_ATTRIBUTE = Pattern.compile(
            "\\b(?:title|subtitle)\\s*=\\s*\"([^\"]{2,})\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** 换行分隔（组件正文按行核对；跨行拼接会被渲染器插入的序号隔断）。 */
    private static final Pattern LINE_BREAK = Pattern.compile("\\R+");
    /** 一行里用 “|” 分隔的多值（如 {@code chips="A|B"}）：只查其中最长的一段。 */
    private static final Pattern MULTI_VALUE_SEPARATOR = Pattern.compile("[|、,;/]");
    /** 短于这个长度的文字不做核对：太短会在无关位置误命中，等于没有判据。 */
    private static final int MIN_PROBE_LENGTH = 4;

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

    /**
     * 渲染结果：渲染后 HTML 与元信息。LLM 上下文里只应有 Markdown，HTML 仅在此工具边界出现。
     *
     * <p>{@code warnings} 是**降级警告**：上游 {@code meta.warnings}（若本次响应带）与本地扫出的
     * 「语法没被识别」合并而成。非空意味着这篇的版式**没有 100% 复刻**——必须是可见的，不能再静默入库。
     */
    public record RenderResult(String html, String title, String summary, String themeAccent, String themeDark,
                               List<String> warnings) {
        /** 兼容构造：无警告。 */
        public RenderResult(String html, String title, String summary, String themeAccent, String themeDark) {
            this(html, title, summary, themeAccent, themeDark, List.of());
        }
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
            List<String> warnings = new java.util.ArrayList<>(parseWarnings(body.path("meta")));
            warnings.addAll(detectDroppedBlocks(prepared, html));
            warnings.addAll(detectLeakedSyntax(html));
            if (!warnings.isEmpty()) {
                log.warn("MarkFlow 渲染降级 {} 条（版式未完全复刻）：{}", warnings.size(), String.join("；", warnings));
            }
            return new RenderResult(html, body.path("meta").path("title").asText(null),
                    body.path("meta").path("summary").asText(null),
                    body.path("theme").path("accent").asText(null), body.path("theme").path("dark").asText(null),
                    List.copyOf(warnings));
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

    /**
     * 上游降级警告（{@code meta.warnings}）：容器未闭合、语法不符被降级为普通段落等。
     *
     * <p>⚠️ 实测（2026-09-12，两次刻意制造降级的探针：未闭合的 {@code :::compare}、{@code :::steps}）
     * 上游**都没有返回这个字段**，因此它只能算「有则更好」，不能当成唯一的降级信号——
     * 真正的兜底是 {@link #detectLeakedSyntax} 对产物的本地扫描。
     */
    static List<String> parseWarnings(JsonNode meta) {
        JsonNode warnings = meta == null ? null : meta.path("warnings");
        if (warnings == null || !warnings.isArray()) return List.of();
        List<String> result = new java.util.ArrayList<>();
        warnings.forEach(node -> {
            String text = node.asText("");
            if (!text.isBlank()) result.add(text.trim());
        });
        return result;
    }

    /**
     * 扫描渲染产物里**未被识别**的 MarkFlow 语法（这是「精排没有 100% 复刻」的可见形态）。
     *
     * <p>识别成功的组件都会被展开成带内联样式的普通节点，产物里不会留下 MarkFlow 的标签名或 {@code :::}；
     * 反过来，产物里出现字面的 {@code <steps>}、{@code :::compare}，就说明渲染器把它当普通文字输出了。
     * 实测（2026-09-12 探针，可反证）：未闭合的 {@code :::compare} 产物里是
     * {@code <p style="…">:::compare</p>}，且**后续整段内容被吞进容器**，产物从 1219 字符缩到 405。
     *
     * <p>代码块内的字面语法是正文内容（可能就是在讲 MarkFlow 语法），扫描前先剔除。
     */
    static List<String> detectLeakedSyntax(String html) {
        if (html == null || html.isBlank()) return List.of();
        String scannable = CODE_BLOCK.matcher(html).replaceAll(" ");
        List<String> found = new java.util.ArrayList<>();
        Matcher container = LEAKED_CONTAINER.matcher(scannable);
        if (container.find()) {
            found.add("容器语法 " + container.group().trim() + " 未被识别（多半是缺少收尾的 :::），已作为正文文字输出");
        }
        Matcher component = LEAKED_COMPONENT.matcher(scannable);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        while (component.find()) names.add("<" + component.group(1).toLowerCase(java.util.Locale.ROOT) + ">");
        if (!names.isEmpty()) {
            found.add("组件标签 " + String.join("/", names) + " 未被识别，已作为正文文字输出（请核对语法指令里的写法）");
        }
        // 收尾标签单独扫：`<badge …>文字</badge>` 的开标签会被渲染器认掉、只有 </badge> 留在产物里，
        // 光扫开标签（上面那条）看不见它。实测见 LEAKED_CLOSING 的说明。
        Matcher closing = LEAKED_CLOSING.matcher(scannable);
        java.util.LinkedHashSet<String> closingNames = new java.util.LinkedHashSet<>();
        while (closing.find()) closingNames.add("</" + closing.group(1).toLowerCase(java.util.Locale.ROOT) + ">");
        if (!closingNames.isEmpty()) {
            found.add("收尾标签 " + String.join("/", closingNames) + " 出现在正文里，"
                    + "说明该组件被写成了成对标签（应写成自闭合 `<badge … />`）");
        }
        return found;
    }

    /**
     * 组件内容**静默消失**的检测：渲染器认得这个标签，却把它的内容整块丢掉了。
     *
     * <p>为什么光有 {@link #detectLeakedSyntax} 不够：那种判据只覆盖「标签被当成普通文字输出」。
     * 存在相反的一类——**渲染成功、产物里却找不到这块内容**，HTTP 200 + {@code ok:true}，
     * 上游也可能不报 {@code meta.warnings}（实测：{@code <timeline>} 用两列行书写时，
     * 时间线的文字在产物里一个字都不剩）。
     *
     * <p>判据：把源文里每个组件的文字（含 {@code <p-title title="…">} 这类写在属性上的）拿去产物里找，
     * 找不到就说明这一块没渲染出来。比较前统一剥标签、去空白，避免渲染器插入 `<span>` 或换行造成假阳性。
     *
     * <p>三条按实测校准过的边界（2026-09-12，24 个组件的真实产物 + 线上文章 22 落库正文，要求零误报）：
     * <ul>
     *   <li>组件正文**按行**取探头，不跨行拼接：{@code <steps>} 产物是「1第一步内容。2第二步内容。」——
     *       渲染器会在条目之间插入序号，整块拼起来永远匹配不上（实测误报）。</li>
     *   <li>内容自带的组件见 {@link #SELF_COPY_COMPONENTS}。</li>
     *   <li>属性只认 {@link #TEXT_ATTRIBUTE} 里列的那两个。</li>
     * </ul>
     */
    static List<String> detectDroppedBlocks(String markdown, String html) {
        if (markdown == null || markdown.isBlank()) return List.of();
        String scannable = CODE_BLOCK.matcher(markdown).replaceAll(" ");
        String flatText = flatten(html, true);
        String flatRaw = flatten(html, false);
        List<String> dropped = new java.util.ArrayList<>();
        Matcher block = COMPONENT_BLOCK.matcher(scannable);
        while (block.find()) {
            if (dropped.size() >= 3) return dropped;
            String name = block.group(1).toLowerCase(java.util.Locale.ROOT);
            if (SELF_COPY_COMPONENTS.contains(name)) continue;
            String inner = ANY_TAG.matcher(block.group(2)).replaceAll("");
            String probe = longestLine(inner);
            if (probe == null || flatText.contains(probe)) continue;
            dropped.add("组件 <" + name + "> 的内容「" + abbreviate(probe)
                    + "」在渲染产物里找不到，这一块可能整个没渲染出来");
        }
        Matcher attribute = TEXT_ATTRIBUTE.matcher(scannable);
        while (attribute.find() && dropped.size() < 3) {
            String probe = longestToken(attribute.group(1));
            if (probe == null || flatRaw.contains(probe) || flatText.contains(probe)) continue;
            dropped.add("组件属性里的文字「" + abbreviate(probe) + "」在渲染产物里找不到：要么这一块没渲染出来，"
                    + "要么渲染器本来就不输出这个属性（`:::table` / `:::timeline` / `:::slider` / `:::compare` "
                    + "的 `title` 就是如此，2026-09-13 实测）");
        }
        return dropped;
    }

    /** 组件正文里最长的一行：逐行核对，而不是把多行拼成一串（拼接会被渲染器插入的序号隔断）。 */
    private static String longestLine(String inner) {
        if (inner == null || inner.isBlank()) return null;
        String best = null;
        for (String line : LINE_BREAK.split(inner)) {
            String candidate = longestToken(line);
            if (candidate != null && (best == null || candidate.length() > best.length())) best = candidate;
        }
        return best;
    }

    /** 剥掉标签（{@code text=true} 时连属性一起丢）、去空白后的扁平文本，用于稳健的包含判断。 */
    private static String flatten(String html, boolean text) {
        if (html == null || html.isBlank()) return "";
        String decoded = html.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return WHITESPACE.matcher(ANY_TAG.matcher(decoded).replaceAll(text ? "" : " ")).replaceAll("");
    }

    /**
     * 取一段文字里最长的「词」（按空白与常见分隔符切分）：短到无法作为判据时返回 null。
     *
     * <p>返回值**不含空白**：比较用的产物文本是去空白后的扁平串，带空格的探针永远匹配不上
     * （实测踩过：{@code <p-title subtitle="ONE SOURCE">} 因为探针里那个空格被判成「内容丢失」）。
     */
    private static String longestToken(String value) {
        if (value == null || value.isBlank()) return null;
        String best = null;
        for (String raw : MULTI_VALUE_SEPARATOR.split(decodeEntities(value).trim())) {
            String candidate = WHITESPACE.matcher(raw).replaceAll("");
            if (candidate.length() >= MIN_PROBE_LENGTH && (best == null || candidate.length() > best.length())) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 与 {@link #flatten} 同一套实体解码：探针取自**源码**（模型写出来的 Markdown），产物取自**渲染结果**，
     * 两边必须用同一种文本形态比较。
     *
     * <p>实测踩过（run#75，2026-09-13）：模型把副标题写成 {@code subtitle="关于&quot;pace the frontier&quot;"}，
     * 产物里那一块渲染得好好的（{@code "pace the frontier"} 就在页面上），但探针带着字面量 {@code &quot;}
     * 去比对已解码的产物，永远匹配不上——白报一次「组件属性里的文字找不到」。
     */
    private static String decodeEntities(String value) {
        if (value == null || value.indexOf('&') < 0) return value;
        return value.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
    }

    private static String abbreviate(String value) {
        return value.length() <= 24 ? value : value.substring(0, 24) + "…";
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
