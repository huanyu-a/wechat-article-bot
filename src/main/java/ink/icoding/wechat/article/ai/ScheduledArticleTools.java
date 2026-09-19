package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.article.ArticleContentPolicy;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.MarkFlowRenderService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server-side article drafting tools used by unattended scheduled agents.
 * 引擎感知（skills-agent-plan 5.10.4）：PROMPT 引擎行为与原实现完全一致；
 * MARKFLOW 引擎下 save_article_draft 只保存 Markdown（渲染延迟到交付前 renderBeforeDelivery），
 * read 返回 Markdown，LLM 上下文零 HTML。
 */
public final class ScheduledArticleTools {
    private static final Logger log = LoggerFactory.getLogger(ScheduledArticleTools.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * 定时链路渲染区段标记（{@code sched-rN}）的序号。与编辑器链路的 {@code rN} 前缀刻意不同：
     * {@code ArticleAiService.renderIdOf} 拿标记值查「当前会话」的渲染缓存，撞号会让别的会话
     * 把定时稿的区段错认成自己渲染的产物。
     */
    private static final java.util.concurrent.atomic.AtomicLong RENDER_ID_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong();

    private static String nextRenderId() {
        return "sched-r" + RENDER_ID_SEQUENCE.incrementAndGet();
    }

    /** 文章标题最大字数（公众号标题栏限制）；超长截断并记可见警告，不抛异常（见 save）。 */
    public static final int TITLE_MAX_LENGTH = 64;
    /** 文章摘要最大字数（公众号摘要栏限制）；超长截断并记可见警告，不抛异常（见 save）。 */
    public static final int DIGEST_MAX_LENGTH = 120;

    /**
     * 正文是否「像手写的公众号模板 HTML」。
     *
     * <p>MARKFLOW 任务的正文必须是 MarkFlow 语法 Markdown；出现 <code>&lt;div style="…"&gt;</code>
     * 这类**内联样式块**说明模型照着 PROMPT 引擎那份说明写了 HTML。渲染服务对 HTML 是原样透传：
     * 精排版式一次都没生效，颜色也写死在 HTML 里，换主题色（accent）同样不会有效果。
     * MarkFlow 组件标签（&lt;lead&gt;、&lt;p-title number="01"&gt; 等）不带 style 属性，不会被这个判据误伤。
     */
    private static final Pattern INLINE_STYLED_BLOCK = Pattern.compile(
            "<(div|section|p|h[1-6]|table|ul|ol)\\b[^>]*\\bstyle\\s*=", Pattern.CASE_INSENSITIVE);

    /** 包级可见以便单测：判据本身是上面那条正则，这里只把「可空」与「匹配」收成一个具名断言。 */
    static boolean looksLikeHandWrittenHtml(String body) {
        return body != null && INLINE_STYLED_BLOCK.matcher(body).find();
    }

    /**
     * 只以 `:::` 容器形式存在、**没有**标签形式的组件被写成了 XML 标签（如 `&lt;compare&gt;`）。
     *
     * <p>实测（2026-09-13 真实渲染 API，见 `target/probe/tag_vs_container_result.txt`）：
     * {@code <compare>} / {@code <reading-path>} / {@code <steps-horizontal>} / {@code <steps-vertical>} /
     * {@code <callout>} / {@code <code-block>} 这 6 个名字当标签写时渲染器不认，标签名会**原样留在正文里**
     * （产物形如 {@code <p style="…"><compare></p>}），HTTP 200、{@code ok:true}、上游无 warnings。
     *
     * <p>{@code hint} 是第八轮补进来的第 7 个（{@code target/probe/round8_na_discriminator.txt}）：
     * 容器写法 {@code :::hint} 会渲染成蓝色信息卡（与 {@code :::info} 同一套产物），
     * 但标签写法 {@code <hint>…</hint>} 的标签会原样留在正文里——正是「有容器、没标签」的一类。
     *
     * <p>反例（因此不能扩大名单）：{@code <slider images="…">} 会渲染出 SVG 轮播、{@code <align>} 会渲染成
     * 居中段落、{@code <breaking>} / {@code <steps>} / {@code <timeline>} / {@code <case-flow>} 都有标签形式
     * （后两个只是行格式另有要求）。把这 7 个之外的名字算进来会误报。
     */
    private static final Pattern CONTAINER_ONLY_AS_TAG = Pattern.compile(
            "<\\s*(compare|reading-path|steps-horizontal|steps-vertical|callout|code-block|hint)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@code layout-*} 家族：容器写法和标签写法**渲染器都不认**，语法会原样留在产物里。
     *
     * <p>实测（2026-09-13，`target/probe/round10_registry_closure.txt`）：上游组件注册表里共
     * **38 个** {@code layout-*} 名字（hero / toc / metrics / cards / part / label-title / infographic /
     * compare / steps / timeline / checklist / stat-row / verdict / myth-fact / image-annotate /
     * audience-fit … 全清单见该文件），容器与标签两种写法各打一次真实渲染 API，
     * **76 组全部残留**——容器式产物里留着 {@code :::layout-cards}，标签式留着 {@code <layout-cards>}，
     * 且一条 warnings 都不报。（第八轮先测了其中 16 个名字 / 32 组，第十轮补齐整族。）
     * 这一族来自 MarkFlow **Web 端另一套组件库**，服务端渲染器没有实现，所以按前缀整族拦下，
     * 不做逐个名字打补丁。
     *
     * <p>容器式的 {@code :::layout-*} 另有 {@link #SUPPORTED_CONTAINERS} 那条通用「不支持的容器」提示兜底，
     * 这里只管标签式——避免同一条写法被提示两遍。
     */
    private static final Pattern UNSUPPORTED_LAYOUT_TAG =
            Pattern.compile("<\\s*layout-[a-z][a-z-]*\\b", Pattern.CASE_INSENSITIVE);

    /**
     * 容器语法行（顶格）：`:::名称` 开启、单独 `:::` 收尾。
     *
     * <p>名字里允许出现连字符——{@code :::steps-horizontal}、{@code :::reading-path}、{@code :::case-flow}
     * 等组件名自带连字符，只认 {@code [A-Za-z]*} 会把它们截成 {@code steps} / {@code reading} / {@code case}，
     * 于是正确的写法被判成「不支持的容器」（实测踩过）。
     */
    private static final Pattern CONTAINER_LINE = Pattern.compile(":{3,}\\s*([A-Za-z][A-Za-z-]*|)");
    /** 带缩进或被引用块包住的容器行——渲染器要求顶格书写，这类写法不会被识别。 */
    private static final Pattern INDENTED_CONTAINER = Pattern.compile("^[ \\t]+:{3,}|^\\s*>\\s*:{3,}");
    /** 代码围栏：其中的语法示例是正文内容，不能当成真的容器/标签去校验。 */
    private static final Pattern CODE_FENCE_LINE = Pattern.compile("^\\s*```.*$");
    /** `:::steps` 容器块（顶格的 `:::steps …` 行 + 容器体），用于核对它的行格式。 */
    private static final Pattern STEPS_CONTAINER_BLOCK = Pattern.compile(
            "^:{3,}\\s*steps\\b[^\\n]*\\R(.*?)(?=^:{3,}\\s*$|\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    /** `:::case-flow` 容器块：每行必须写成 `- [标签] 标题`，行首少一个符号整块渲染为空。 */
    private static final Pattern CASE_FLOW_BLOCK = Pattern.compile(
            "^:{3,}\\s*case-flow\\b[^\\n]*\\R(.*?)(?=^:{3,}\\s*$|\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    /** `<case-flow>…</case-flow>` 标签形式：与容器写法是**同一条**行格式规则（实测两者产物逐字相同）。 */
    private static final Pattern CASE_FLOW_TAG_BLOCK =
            Pattern.compile("<case-flow\\b[^>]*>(.*?)</case-flow\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** `:::timeline` 容器块：每行必须三列，缺列的行会被整行忽略（两列时整块为空）。 */
    private static final Pattern TIMELINE_BLOCK = Pattern.compile(
            "^:{3,}\\s*timeline\\b[^\\n]*\\R(.*?)(?=^:{3,}\\s*$|\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    /** `<timeline>…</timeline>` 标签形式：与容器写法同一条行格式规则（实测 3 列有产物、2 列整块为空）。 */
    private static final Pattern TIMELINE_TAG_BLOCK =
            Pattern.compile("<timeline\\b[^>]*>(.*?)</timeline\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** `:::reading-path` 容器块：每一行都必须带 `-` 列表符号，否则整块产物 0 字符。 */
    private static final Pattern READING_PATH_BLOCK = Pattern.compile(
            "^:{3,}\\s*reading-path\\b[^\\n]*\\R(.*?)(?=^:{3,}\\s*$|\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    /**
     * `case-flow` 行的前缀：**行首**的 `-`，其后紧跟 `[标签]`。
     *
     * <p>实测（2026-09-13，`target/probe/round8_caseflow_bullet.txt`，容器与标签两种写法各打一遍）
     * 只有 `- [标签] …` 有产物（1032 / 1034 字符）。`* [标签] …`、`+ [标签] …`、`1. [标签] …`、
     * 裸 `[标签] …` 四种写法产物一律 **0 字符**（整块内容凭空消失、上游不报错也不给 warnings）；
     * 行首带两格缩进的 `  - [标签] …` 只出 547 字符、可见文字 9 个字（标签栏还在、内容丢了）。
     * 所以这里卡的是「行首的 `-`」，**不是**仅「有没有 `[标签]`」——旧判据把 `-` 写成可选，
     * 恰好把这四种会清空内容的写法全放过去了。
     */
    private static final Pattern CASE_FLOW_LABEL = Pattern.compile("^-\\s*\\[\\s*\\S");
    /** `reading-path` 行允许的列表符号：只认连字符 `-`（星号 `*` 与裸文本一样会让整块消失）。 */
    private static final Pattern READING_PATH_BULLET = Pattern.compile("^\\s*-\\s*\\S");
    /** `:::compare` 容器块：每行应为 3–4 列（实测 >4 列或 <3 列的行会被整行忽略并丢弃末列内容）。 */
    private static final Pattern COMPARE_BLOCK = Pattern.compile(
            "^:{3,}\\s*compare\\b[^\\n]*\\R(.*?)(?=^:{3,}\\s*$|\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    /**
     * `:::compare` 第 4 格（强调标记）**唯一**接受的取值。
     *
     * <p>实测（2026-09-13，`target/probe/compare_marker_probe.txt`）：第 4 格写 `accent` / `default`
     * 或留空（大小写不敏感）都不报；写别的——中文「强调」「高亮」，或英文 `highlight` / `strong`——
     * 那一行就被判坏。上游的告警文本说的是「列数不是「维度 | A方 | B方 | accent|default」」，
     * **但真正的触发条件是取值不在这个白名单里**，照着告警去数数会改错方向。
     */
    private static final Pattern COMPARE_MARKER_VALUES =
            Pattern.compile("accent|default", Pattern.CASE_INSENSITIVE);
    /**
     * `:::slider` 容器块（含开启行属性）：开启行必须给出 {@code images="图1,图2"}，
     * 否则渲染器原样输出一个灰底提示框「请提供图片URL列表」——文章里就挂着这么一句话。
     */
    private static final Pattern SLIDER_BLOCK = Pattern.compile(
            "^:{3,}\\s*slider\\b([^\\n]*)\\R(.*?)(?=^:{3,}\\s*$|\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    /** `images="…"` 属性；值里必须真的出现 URL（空串等同没写）。 */
    private static final Pattern SLIDER_IMAGES_ATTR = Pattern.compile("images\\s*=\\s*\"\\s*https?://", Pattern.CASE_INSENSITIVE);
    /** 标签式 `<slider …>` 的开头与收尾（判据见 {@link #sliderTagWithoutClosingTag}）。 */
    private static final Pattern SLIDER_TAG_OPEN = Pattern.compile("<slider\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLIDER_TAG_CLOSE = Pattern.compile("</slider\\s*>", Pattern.CASE_INSENSITIVE);
    /**
     * 开启行给了 {@code title="…"}、但渲染器**不输出这个标题**的容器。
     *
     * <p>实测（2026-09-13，真实渲染 API，见 {@code target/probe/attr_probe3.txt} / {@code attr_probe4.txt}）：
     * {@code :::table}（含 {@code style="card"}）、{@code :::timeline}、{@code :::slider}、{@code :::compare}
     * 的 {@code title=} 属性里的文字**在产物里一个字都找不到**，且 HTTP 200、{@code ok:true}、上游不报 warnings。
     *
     * <p>反例（因此不能扩大名单）：{@code :::callout type="tip" title="…"} 的标题会渲染成加粗小标题、
     * {@code :::steps-horizontal} 的 {@code label/title/hint} 三个都会渲染、{@code :::code-block lang="js" title="…"}
     * 的标题会渲染成代码块头栏——把这几个算进来会误报。
     */
    private static final Pattern CONTAINER_DROPPED_TITLE = Pattern.compile(
            "^:{3,}\\s*(?:table|timeline|slider|compare)\\b[^\\n]*\\btitle\\s*=\\s*\"",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    /** Markdown 表格行（以 `|` 开头的行）；guide 第六节第 10 条要求表格后必须空一行。 */
    private static final Pattern TABLE_ROW_LINE = Pattern.compile("^\\s*\\|");
    /** `<steps>…</steps>` 块，用于在块内单查 Markdown 小标题。 */
    private static final Pattern STEPS_BLOCK =
            Pattern.compile("<steps\\b[^>]*>(.*?)</steps\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** Markdown 小标题行（`#` / `##` / `###` …）；渲染器在 `<steps>` 里不解析它。 */
    private static final Pattern MARKDOWN_HEADING_LINE = Pattern.compile("^\\s{0,3}#{1,6}\\s+\\S", Pattern.MULTILINE);
    /**
     * 被写成「成对标签」的自闭合组件：实测（2026-09-12）`<badge type="tip">推荐</badge>` 的产物里
     * 徽章本身渲染正常，但**收尾标签原样留在正文**（产物文本 `tip推荐标签</badge>`）；
     * `<badge ...></badge>`（中间为空）同样会留下 `</badge>`。
     */
    private static final Pattern PAIRED_SELF_CLOSING =
            Pattern.compile("<(badge|icon)\\b[^>]*>[^<]*</\\1\\s*>", Pattern.CASE_INSENSITIVE);
    /** Markdown 二级小标题行（`## 标题`）。 */
    private static final Pattern MD_H2_LINE = Pattern.compile("^##\\s+(.+?)\\s*$");
    /** `<p-title>` 的 title 属性值（章节头组件）。 */
    private static final Pattern PTITLE_TITLE_ATTR = Pattern.compile("<p-title\\b[^>]*\\btitle=\"([^\"]+)\"");
    /** 结尾类组件（收尾卡片：engage-card / engage-label，全文只应出现一个）。 */
    private static final Pattern ENDING_COMPONENT = Pattern.compile("<engage-(?:card|label)\\b");
    /**
     * 渲染器认得的容器名（2026-09-12 逐种复核的真实产物，可反证）。
     *
     * <p>{@code compare} 是双栏对比；{@code tip/note/info/warning/caution/important}
     * 是与 {@code > [TIP]} 等价的**提示框**写法——两者产物里都是 {@code border-left:4px} 的同一套样式，
     * {@code :::tip 自定义标题} 还能覆盖默认标题。guide 第三节把这些类型列为「提示框可用类型」，
     * 第六节却只提了 {@code :::compare}，照着第六节写会以为 {@code :::tip} 不被支持。
     *
     * <p>{@code reading-path / steps-horizontal / steps-vertical / case-flow / slider / callout /
     * align / code-block} 来自 MarkFlow **Web 端组件库**：服务端的语法指令（guide）一个都没记载，
     * 但用注册表里的官方示例打真实渲染 API，每一种都产出了对应结构（阅读路线的编号圆点、
     * 轮播图的 {@code <svg>} 动画等）——换句话说 guide 漏写不等于渲染器不支持。
     * 少列一个就会把**正确写法误报成错误**，把模型从一条能让成品好看很多的写法上劝退。
     *
     * <p>反面证据：{@code :::danger}/{@code :::success} 不在列（产物里字面留着 {@code ::: danger}；
     * 需要危险样式请用 {@code :::callout type="danger"}，实测有效）。
     * {@code :::steps} **在列**——它确实能渲染，但行格式是「序号 | 步骤名 | 说明」三列，
     * 缺管道分隔会整块降级为普通段落，所以只在行格式不对时提醒改用 {@code <steps>}。
     */
    private static final java.util.Set<String> SUPPORTED_CONTAINERS =
            java.util.Set.of("compare", "tip", "note", "info", "warning", "caution", "important",
                    "breaking", "timeline", "table", "steps",
                    "reading-path", "steps-horizontal", "steps-vertical", "case-flow",
                    "slider", "callout", "align", "code-block");

    /**
     * 提交 MarkFlow 正文时的语法自检：把**渲染器一定识别不了**的写法在保存这一刻就告诉模型。
     *
     * <p>为什么要在保存时查而不是等渲染完再查：渲染发生在交付前（{@code renderBeforeDelivery}），
     * 那时智能体已经交卷、没有下一次修正的机会。而工具结果是模型一定会读到的下一段输入，
     * 把「容器没闭合 / 容器名不存在 / 没顶格 / 步骤行格式不对」写在这里，模型可以在同一轮里改好再覆盖保存。
     *
     * <p>实测依据（2026-09-12 对渲染 API 的逐种探针，产物可反证）：未闭合的 `:::compare` 产物里是
     * `&lt;p&gt;:::compare&lt;/p&gt;` 且后续整段内容被吞掉（产物 1219 → 405 字符）；
     * `:::` 不顶格或被包进引用块 → 字面 `:::tip` 留在正文；`:::danger` / `:::success` 不在容器名单里；
     * `:::steps` 缺管道分隔 → 整块降级为普通段落；`<steps>` 里的 `### 小标题` → 字面 `###` 留在正文；
     * 成对 `<badge>` → `</badge>` 留在正文；表格后不空行 → 尾随文字被当成表格注释；
     * `:::case-flow` 的行少了 `[标签]`、`:::timeline` 的行不足三列 → **整块产物为空**（内容一个字都不剩）；
     * `:::reading-path` 里只要有一行不带 `-` 列表符号（含写成 `*`）→ 整块导航同样是 0 字符、上游不报错；
     * `:::slider` 缺 `images` → 成稿里直接留一句「请提供图片URL列表」的灰框。
     */
    static List<String> markflowSyntaxHints(String markdown) {
        if (markdown == null || markdown.isBlank()) return List.of();
        StringBuilder body = new StringBuilder();
        boolean inFence = false;
        for (String line : markdown.split("\\R")) {
            if (CODE_FENCE_LINE.matcher(line).matches()) {
                inFence = !inFence;
                continue;
            }
            if (!inFence) body.append(line).append('\n');
        }
        List<String> hints = new ArrayList<>();
        java.util.LinkedHashSet<String> unknown = new java.util.LinkedHashSet<>();
        int openers = 0;
        int closers = 0;
        boolean indented = false;
        for (String line : body.toString().split("\\R")) {
            if (INDENTED_CONTAINER.matcher(line).find()) {
                indented = true;
                continue;
            }
            Matcher matcher = CONTAINER_LINE.matcher(line);
            if (!matcher.lookingAt()) continue;
            String name = matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            if (name.isEmpty()) closers++;
            else {
                openers++;
                if (!SUPPORTED_CONTAINERS.contains(name)) unknown.add(name);
            }
        }
        for (String name : unknown) {
            hints.add(":::" + name + " 不是渲染器支持的容器语法。可用容器：:::compare（双栏对比）、"
                    + ":::callout type=\"tip|note|info|warning|caution|important|danger\"（提示卡片，"
                    + "等价于 > [TIP] 且能自定义标题）、:::reading-path（章节导航）、"
                    + ":::steps-horizontal / :::steps-vertical（步骤卡）、:::case-flow（案例流）、"
                    + ":::timeline（时间线）、:::table（表格容器）、:::breaking（开头大卡）、"
                    + ":::slider（轮播图）、:::align（对齐容器）、:::code-block（代码块容器）");
        }
        if (openers > closers) {
            hints.add("有 " + (openers - closers) + " 个 ::: 容器缺少收尾的 :::；"
                    + "未闭合的容器会把后续整段内容吞进容器并字面输出，请补齐后重新保存");
        }
        if (closers > openers) hints.add("有 " + (closers - openers) + " 行多余的 ::: 收尾，请删掉");
        if (indented) hints.add("::: 容器与 <标签> 必须顶格书写（行首不能有空格缩进或 > 引用前缀），否则不会被识别");
        // 只以 `:::` 容器形式存在的组件被写成了 XML 标签（实测 2026-09-13：`<compare>` 渲染成
        // `<p …><compare></p>`，标签名原样留在正文里，上游不报错也不给 warnings）。guide 第六节第 6 条
        // 特意提醒过「双栏对比用 :::compare，不是 <compare> 标签」，但保存时没有任何提示，模型写错也看不到。
        // 名单只收「确实没有标签形式」的 6 个：实测 `<slider>` / `<align>` / `<breaking>` / `<steps>` /
        // `<timeline>` / `<case-flow>` 都能按标签渲染，把它们算进来会误报。
        if (containerWrittenAsTag(body)) {
            hints.add(":::compare / :::reading-path / :::steps-horizontal / :::steps-vertical / :::callout / "
                    + ":::code-block / :::hint 只有容器写法，没有 `<compare>` 这种标签写法：写成标签的话渲染器不认，"
                    + "标签名会原样留在正文里（如正文里出现一行 `<compare>`），请改成 `:::compare` + 收尾 `:::`");
        }
        // layout-* 家族（hero / toc / metrics / cards / steps / timeline …）来自上游 Web 端另一套组件库，
        // 服务端渲染器一个都没实现：注册表里共 38 个名字 × 容器/标签两种写法共 76 组实测全部残留语法
        // （target/probe/round10_registry_closure.txt；第八轮先测了 16 个名字 / 32 组）。
        // 容器式另有「不支持的容器」提示兜底，这里只补标签式。
        if (UNSUPPORTED_LAYOUT_TAG.matcher(body).find()) {
            hints.add("`<layout-…>` 这一族（layout-hero / layout-toc / layout-metrics / layout-cards / "
                    + "layout-steps / layout-timeline 等）渲染器一个都没实现：标签会原样留在正文里，"
                    + "上游不报错也不给 warnings。对应的版式请改用本项目已核实的写法——"
                    + "开篇标题区 `:::breaking`、章节导航 `:::reading-path`、步骤 `:::steps-horizontal` / "
                    + "`<steps>`、时间线 `:::timeline`、数据对照 `:::table` / `:::compare`");
        }
        // <steps> 的步骤里不要写 ### 小标题：渲染器不把标题当标题，而是原样当成步骤文字
        // （实测 2026-09-12：产物扁平文本是「1###第一步：准备2准备工作的正文。」，### 字面留在正文里），
        // 同时它还会把这一步拆成两个格子（4 步就变成 4 列宽 25%，而正常两段只有 2 格）。
        if (stepsBlockWithHeading(body)) {
            hints.add("<steps> 步骤里不要写 ### 小标题（渲染器不解析，会把「### …」原样当成步骤文字，"
                    + "并把这一步拆成两个格子）；每步写成一行 `- 名称 | 描述`");
        }
        // :::steps 能渲染，但行格式与 <steps> 不同：每行必须是「序号 | 步骤名 | 说明」三列。
        // 缺管道分隔的行会让整个容器降级成普通段落（实测产物 264 字符、0 个步骤节点），
        // 上游只在 meta.warnings 里报一句，模型看不到——所以在这里就地提醒换成 guide 记载的 <steps>。
        if (stepsContainerWithoutColumns(body)) {
            hints.add(":::steps 的每一行都要写成「序号 | 步骤名 | 说明」（用竖线分隔），否则整个容器会降级为"
                    + "普通段落、步骤版式全丢；步骤流建议直接用 guide 第六节记载的 <steps> 标签"
                    + "（每步一行 `- 名称 | 描述`，超过 3 步会自动竖排）");
        }
        // 表格后必须空一行（guide 第六节第 10 条）：紧接着的非表格行会被系统当成表格注释吞掉。
        if (tableRowWithoutTrailingBlank(body.toString())) {
            hints.add("表格后面必须空一行再写正文：紧接着表格的普通文字会被当成表格注释（渲染成小字灰色）");
        }
        // :::case-flow 每行必须 `- [标签] 标题`：行首少了 `-`（写成 `*` / `+` / `1.` / 裸 `[标签]`）
        // 整块**渲染为空**（内容凭空消失，实测产物 0 字符、上游不报错也不给 warnings）——这是最需要
        // 就地拦下的一种写法。判据只看行首的 `-`：`* [标签]` 与 `[标签]` 的产物同样是 0 字符。
        if (caseFlowWithoutLabel(body)) {
            hints.add(":::case-flow / <case-flow> 的每一行都要写成 **行首的 `-` + `[标签]`**"
                    + "（如 `- [案例 01] 从零搭建个人知识库`）：写成 `*` / `+` / `1.` 列表、或漏掉行首的 `-`、"
                    + "或漏掉 `[标签]`，整块都会渲染为空、内容一个字都不剩（实测四种写法产物都是 0 字符），"
                    + "请改掉后重新保存");
        }
        // :::timeline / <timeline> 每行三列：两列的行会被整行忽略，整块都缺列时产物为空（实测 0 字符）。
        if (timelineWithoutThreeColumns(body)) {
            hints.add("时间线的每一行都要写成「时间 | 标题 | 说明」三列，缺列的行会被整行忽略；"
                    + "写法可以是 :::timeline 容器或 <timeline> 标签，行格式相同");
        }
        // :::reading-path 每行必须是 `- 章节名 | 一句话说明`：只要有一行不带 `-`（哪怕其余行都规范，
        // 哪怕整块换了 `*` 列表符号），**整块产物就是 0 字符**——实测 HTTP 200、ok:true、上游
        // 一条 warnings 都不报，只是页面里导航区凭空不见。
        if (readingPathWithoutBullets(body)) {
            hints.add(":::reading-path 的每一行都必须是 `- 章节名 | 一句话说明` 的列表行（只认 `-`，"
                    + "`*` 或裸文字都算不合格）：只要有一行不合格，整块导航会渲染成空、一个字都不剩，"
                    + "请改掉后重新保存");
        }
        // :::slider 没给 images：渲染器不报错，而是原样吐出一个灰底提示框「请提供图片URL列表」，
        // 那句话会直接出现在成稿里（实测产物 165 字符，HTML 就是那个提示框）。
        if (sliderWithoutImages(body)) {
            hints.add(":::slider 必须在开启行给出 `images=\"图1直链,图2直链\"`（http/https 直链）："
                    + "缺了它渲染器会输出一个写着「请提供图片URL列表」的灰框，那句话会留在成稿里");
        }
        // 标签式 <slider> 少了收尾的 </slider>：整行标签连同属性原样留在正文里，轮播图完全不出现
        // （实测 2026-09-13：自闭合 356 字符 / 只开不闭 320 字符，都是 0 个 <svg>；补上 </slider>
        // 就是 1141 字符的完整 SVG）。上游缺陷 R4 记的正是这条——技能提示一律要求容器式写法，
        // 但保存自检此前不看标签式，模型真写了也没有任何反馈，这条绕过等于只做了一半。
        if (sliderTagWithoutClosingTag(body)) {
            hints.add("标签式 `<slider …>` 少了收尾的 `</slider>`（自闭合的 `/` 不算数）：这样写整行标签会"
                    + "原样留在正文里、轮播图完全不出现（上游不报错也不给 warnings）。请改写成本项目统一使用的"
                    + "`:::slider images=\"图1直链,图2直链\" interval=\"3\"` + 收尾 `:::`；"
                    + "确实要用标签式的话必须补上 `</slider>`");
        }
        // :::compare 的每行只接受 3–4 列（实测 2026-09-13）：5 列及以上、2 列及以下的行会被整行忽略，
        // 少列时末尾那一方的整列内容一起丢（2 列时对比方乙的文字在产物里完全找不到）；
        // 上游只在 meta.warnings 里说一句，而渲染发生在交付前，模型届时已经没机会改。
        // 第 4 格的取值另有一条白名单（见 COMPARE_MARKER_VALUES）——run#81/#82 连续两轮的降级警告都出在这里。
        if (compareRowsWithBadShape(body)) {
            hints.add(":::compare 的每一行写成 3 或 4 列「维度 | A方 | B方 | 标记」（标记列可留空）："
                    + "标记列**只认 `accent` 或 `default` 这两个英文词**，写成中文（如「强调」）或别的英文词"
                    + "（如 `highlight`）那一行会被整行忽略——上游的告警说「列数不对」，实际是取值不在白名单里；"
                    + "列数少于 3 或多于 4 同样整行忽略，列少的时候还会连整列对比内容一起丢掉");
        }
        // :::table / :::timeline / :::slider / :::compare 的 title= 属性渲染器不输出（实测 2026-09-13）：
        // 不会报错、产物里就是没有这段文字，run#76 因此背了一条「组件属性里的文字找不到」的降级告警。
        if (CONTAINER_DROPPED_TITLE.matcher(body).find()) {
            hints.add(":::table / :::timeline / :::slider / :::compare 的 `title=\"…\"` 属性渲染器**不输出**"
                    + "（实测产物里这段文字一个字都没有）：标题请改写成容器前面的一句正文，"
                    + "或改用会渲染标题的 `:::callout type=\"tip\" title=\"…\"` / `<p-title>`");
        }
        // <badge> / <icon> 必须自闭合：写成 <badge title="x">…</badge> 时徽章本身正常，
        // 但收尾标签会原样留在正文里（实测产物文本是「tip推荐标签</badge>」）。
        Matcher paired = PAIRED_SELF_CLOSING.matcher(body);
        if (paired.find()) {
            hints.add("<" + paired.group(1).toLowerCase(java.util.Locale.ROOT)
                    + "> 必须写成自闭合（`<Badge type=\"tip\" text=\"推荐\" />`）：写成成对标签会把收尾标签"
                    + "原样吐进正文，请改掉后重新保存");
        }
        // 小标题重复（run#17/文章 22 实证）：`## 标题` 后紧跟同题的 <p-title>——渲染器对**两者**都输出，
        // 成稿里同一个标题先是一条普通小标题、再一个章节头组件，用户看到的就是「小标题重复」。
        if (h2DuplicatedByPTitle(body)) {
            hints.add("小标题重复：有 `## 标题` 行后面紧跟着**同题**的 `<p-title>`（渲染器两个都会输出，"
                    + "成稿里同一标题出现两遍）。章节头请二选一：删掉 `##` 行只留 `<p-title>`（推荐），"
                    + "或去掉 `<p-title>` 保留 `##`");
        }
        // 结尾重复（同一篇实证）：<engage-card> 与 <engage-label> 叠放——成稿结尾连续出现多张收尾卡片。
        if (endingComponentsStacked(body)) {
            hints.add("结尾组件叠放：检测到不止一个收尾组件（<engage-card> / <engage-label>），"
                    + "成稿结尾会连续出现多张收尾卡。全文结尾请只保留一个收尾组件");
        }
        return hints;
    }

    /**
     * 是否存在「`## 标题` 的紧接着几行内出现**同题** `<p-title>`」——两者的产物都会渲染标题。
     * 配对窗口只看 h2 之后 4 行内的第一个 p-title；不匹配就不再往远配对
     * （远处的 p-title 是其它章节自己的头，与这个 h2 无关）。
     */
    static boolean h2DuplicatedByPTitle(CharSequence markdown) {
        String[] lines = markdown.toString().split("\\R");
        for (int i = 0; i < lines.length; i++) {
            Matcher h2 = MD_H2_LINE.matcher(lines[i].trim());
            if (!h2.matches()) continue;
            String h2Text = flattenTitle(h2.group(1));
            if (h2Text.isEmpty()) continue;
            for (int j = i + 1; j <= Math.min(i + 4, lines.length - 1); j++) {
                Matcher ptitle = PTITLE_TITLE_ATTR.matcher(lines[j]);
                if (ptitle.find()) {
                    String title = flattenTitle(ptitle.group(1));
                    if (!title.isEmpty() && (title.contains(h2Text) || h2Text.contains(title))) return true;
                    break;
                }
            }
        }
        return false;
    }

    /** 结尾组件（engage-card / engage-label）是否出现两次以上。 */
    static boolean endingComponentsStacked(CharSequence markdown) {
        Matcher matcher = ENDING_COMPONENT.matcher(markdown);
        int count = 0;
        while (matcher.find()) count++;
        return count >= 2;
    }

    /** 标题配对用的归一化：只压空白，保留原文用字（p-title 与 h2 通常是逐字同题）。 */
    private static String flattenTitle(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    /** 是否有「只有容器写法」的组件被写成了 XML 标签（判据与其反例见 {@link #CONTAINER_ONLY_AS_TAG}）。 */
    private static boolean containerWrittenAsTag(CharSequence markdown) {
        return CONTAINER_ONLY_AS_TAG.matcher(markdown).find();
    }

    /** `<steps>` 块内是否写了 Markdown 小标题（`###` 开头）。 */
    private static boolean stepsBlockWithHeading(CharSequence markdown) {
        Matcher block = STEPS_BLOCK.matcher(markdown);
        while (block.find()) {
            if (MARKDOWN_HEADING_LINE.matcher(block.group(1)).find()) return true;
        }
        return false;
    }

    /** `:::steps` 容器里是否存在**没有竖线分隔**的正文行（判据见上面调用处的说明）。 */
    private static boolean stepsContainerWithoutColumns(CharSequence markdown) {
        Matcher block = STEPS_CONTAINER_BLOCK.matcher(markdown);
        while (block.find()) {
            for (String line : block.group(1).split("\\R")) {
                if (!line.isBlank() && !line.contains("|")) return true;
            }
        }
        return false;
    }

    /** `case-flow`（容器式或标签式）里是否存在不以行首 `- [标签]` 开头的行（实测会让整块渲染为空）。 */
    private static boolean caseFlowWithoutLabel(CharSequence markdown) {
        return blockHasRowWithoutPrefix(CASE_FLOW_BLOCK, markdown, CASE_FLOW_LABEL)
                || blockHasRowWithoutPrefix(CASE_FLOW_TAG_BLOCK, markdown, CASE_FLOW_LABEL);
    }

    /** 某个块里是否存在不匹配 {@code prefix} 的行（空行不算）。 */
    private static boolean blockHasRowWithoutPrefix(Pattern block, CharSequence markdown, Pattern prefix) {
        Matcher matcher = block.matcher(markdown);
        while (matcher.find()) {
            for (String line : matcher.group(1).split("\\R")) {
                if (line.isBlank()) continue;
                if (!prefix.matcher(line).find()) return true;
            }
        }
        return false;
    }

    /** `:::reading-path` 里是否存在不带 `-` 列表符号的行（实测会让整块渲染为空）。 */
    private static boolean readingPathWithoutBullets(CharSequence markdown) {
        Matcher block = READING_PATH_BLOCK.matcher(markdown);
        while (block.find()) {
            for (String line : block.group(1).split("\\R")) {
                if (line.isBlank()) continue;
                if (!READING_PATH_BULLET.matcher(line).find()) return true;
            }
        }
        return false;
    }

    /**
     * `:::compare` 里是否存在形状不合法的行——列数不在 3–4 之间，或第 4 格取值不在
     * {@link #COMPARE_MARKER_VALUES} 白名单里（实测两类行都被整行忽略，少列时整列内容一起丢）。
     */
    private static boolean compareRowsWithBadShape(CharSequence markdown) {
        Matcher block = COMPARE_BLOCK.matcher(markdown);
        while (block.find()) {
            for (String line : block.group(1).split("\\R")) {
                if (line.isBlank()) continue;
                String[] cells = line.split("\\|", -1);
                if (cells.length < 3 || cells.length > 4) return true;
                if (cells.length == 4) {
                    String marker = cells[3].trim();
                    if (!marker.isEmpty() && !COMPARE_MARKER_VALUES.matcher(marker).matches()) return true;
                }
            }
        }
        return false;
    }

    /** `:::slider` 是否没给可用的图片直链（开启行属性与容器体里都找不到 http/https 直链）。 */
    private static boolean sliderWithoutImages(CharSequence markdown) {
        Matcher block = SLIDER_BLOCK.matcher(markdown);
        while (block.find()) {
            String openerAttrs = block.group(1) == null ? "" : block.group(1);
            String inner = block.group(2) == null ? "" : block.group(2);
            if (SLIDER_IMAGES_ATTR.matcher(openerAttrs).find()) continue;
            if (inner.contains("http://") || inner.contains("https://")) continue;
            return true;
        }
        return false;
    }

    /**
     * 标签式 `<slider …>` 有没有配对的 `</slider>`。
     *
     * <p>实测（2026-09-13 真实渲染 API，输入与产物原样落在 `target/probe/round6_r2r4.{json,md}`）：
     * 自闭合 `<slider … />` 产 356 字符、只开不闭 `<slider …>` 产 320 字符，都是 **0 个 `<svg>`**——
     * 整行标签连同属性原样留在 `<p>` 里，HTTP 200、`ok:true`、上游一条 warnings 都不报；
     * 同一个开启标签补上 `</slider>` 就变成 1141 字符的完整 SVG 轮播。
     *
     * <p>判据**只看有没有闭合标签**，不看自闭合斜杠：`<slider …/></slider>`（斜杠与闭合标签同时在）
     * 实测同样出 1141 字符，按斜杠判会把这条正确写法误报。
     */
    private static boolean sliderTagWithoutClosingTag(CharSequence markdown) {
        Matcher open = SLIDER_TAG_OPEN.matcher(markdown);
        while (open.find()) {
            if (!SLIDER_TAG_CLOSE.matcher(markdown).find(open.end())) return true;
        }
        return false;
    }

    /** `:::timeline` / `<timeline>` 里是否存在不足三列的行（实测缺列的行被整行忽略，整块缺列时产物为空）。 */
    private static boolean timelineWithoutThreeColumns(CharSequence markdown) {
        return blockHasRowWithoutColumns(TIMELINE_BLOCK, markdown, 3)
                || blockHasRowWithoutColumns(TIMELINE_TAG_BLOCK, markdown, 3);
    }

    /** 某个块里是否存在竖线列数不足 {@code columns} 的行。 */
    private static boolean blockHasRowWithoutColumns(Pattern block, CharSequence markdown, int columns) {
        Matcher matcher = block.matcher(markdown);
        while (matcher.find()) {
            for (String line : matcher.group(1).split("\\R")) {
                if (line.isBlank()) continue;
                if (line.split("\\|", -1).length < columns) return true;
            }
        }
        return false;
    }

    /**
     * 是否存在「表格行后面紧跟非空非表格行」的写法（guide 第六节第 10 条）。
     *
     * <p>判据只看紧邻的下一行：表格行以 {@code |} 开头，下一行仍是表格行说明表格还没完；
     * 下一行是 {@code :::} 收尾说明表格在容器里收尾——这两种都不算。
     */
    private static boolean tableRowWithoutTrailingBlank(String text) {
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index + 1 < lines.length; index++) {
            if (!TABLE_ROW_LINE.matcher(lines[index]).find()) continue;
            String next = lines[index + 1];
            if (next.isBlank() || TABLE_ROW_LINE.matcher(next).find() || next.trim().startsWith(":")) continue;
            return true;
        }
        return false;
    }

    private ScheduledArticleTools() {
    }

    public static List<Tool> all(DraftState state) {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ReadDraftTool(state));
        tools.add(state.layoutEngine() == LayoutEngine.MARKFLOW
                ? new SaveMarkflowDraftTool(state) : new SaveDraftTool(state));
        tools.add(new SetDraftCoverTool(state));
        return tools;
    }

    /** 只读草稿工具（DRAFT_READ 组单独授权时使用，方案 5.3 工具组拆分）。 */
    public static List<Tool> readOnly(DraftState state) {
        return List.of(new ReadDraftTool(state));
    }

    public static final class DraftState {
        private String title;
        private String author;
        private String digest;
        private String contentHtml;
        private String sourceUrl;
        private Long coverAssetId;
        private long documentVersion;
        private boolean saved;
        private LayoutEngine layoutEngine = LayoutEngine.PROMPT;
        private String contentMarkdown;
        private String saveAccent;
        private String saveDark;
        private boolean rendered;
        private List<String> renderWarnings = List.of();
        /**
         * 保存期降级警告（如标题/摘要超长被截断）。
         *
         * <p>为什么不复用 {@link #renderWarnings}：那个字段会在两处被**整体覆盖**——
         * {@code save} 里每次保存重置、{@code renderBeforeDelivery} 里交付前用渲染器返回的警告覆盖。
         * 保存期的提示写在里面，MARKFLOW 链路下必然丢失（PROMPT 链路因为不渲染反而能留下，
         * 同一个改动在两种引擎下行为还不一致）。两者语义也不同：渲染降级说的是「版式没复刻」，
         * 保存降级说的是「内容被改短了」，混在一起会让终态文案指错方向。
         */
        private List<String> saveWarnings = List.of();

        public DraftState(Long defaultCoverAssetId) {
            this(defaultCoverAssetId, LayoutEngine.PROMPT);
        }

        public DraftState(Long defaultCoverAssetId, LayoutEngine layoutEngine) {
            this.coverAssetId = defaultCoverAssetId;
            this.layoutEngine = layoutEngine == null ? LayoutEngine.PROMPT : layoutEngine;
        }

        public String getContentMarkdown() {
            return contentMarkdown;
        }

        public LayoutEngine layoutEngine() {
            return layoutEngine;
        }

        public synchronized boolean isSaved() {
            return saved;
        }

        public synchronized String title() {
            return title;
        }

        public synchronized String contentHtml() {
            return contentHtml;
        }

        private synchronized String read() {
            return json(view());
        }

        public synchronized String save(SaveDraftParam param) {
            return save(param.getTitle(), param.getAuthor(), param.getDigest(), param.getContentHtml(),
                    param.getSourceUrl(), param.getAccent(), param.getDark());
        }

        /** MARKFLOW 版的参数类：正文落在 contentMarkdown 上（工具说明与字段名都与 PROMPT 版区分）。 */
        public synchronized String save(SaveMarkflowDraftParam param) {
            return save(param.getTitle(), param.getAuthor(), param.getDigest(), param.getContentMarkdown(),
                    param.getSourceUrl(), param.getAccent(), param.getDark());
        }

        /**
         * 保存的公共实现：走哪条语义只由 {@link #layoutEngine} 决定，与调用方用了哪个参数类无关——
         * 否则一个参数类就能把 MARKFLOW 草稿写成 PROMPT 语义（引擎是任务级配置，不该被模型选择覆盖）。
         */
        private synchronized String save(String rawTitle, String rawAuthor, String rawDigest, String body,
                                         String rawSourceUrl, String rawAccent, String rawDark) {
            if (rawTitle == null || rawTitle.isBlank()) {
                throw new IllegalArgumentException("文章标题不能为空");
            }
            boolean markflow = layoutEngine == LayoutEngine.MARKFLOW;
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException(markflow ? "文章正文（MarkFlow 语法 Markdown）不能为空" : "文章正文不能为空");
            }
            // 标题/摘要超长改为**截断 + 可见警告**，不再抛异常：
            // 实测 run#85 / run#89（真实定时触发，task#4 SINGLE）前 40 次检索**全部成功**，
            // 却因为「文章摘要不能超过120字」连续两次失败、烧光收尾宽限，整篇文章作废。
            // 摘要只是列表页预览文案，截断的代价远小于丢掉整篇；且前端编辑器本来就是静默截断
            // （ArticleEditorView.vue 的 slice(0,120) / maxlength="120"），后端硬拒绝才是语义不一致的那一侧。
            List<String> lengthWarnings = new java.util.ArrayList<>();
            if (rawTitle.length() > TITLE_MAX_LENGTH) {
                lengthWarnings.add("标题 " + rawTitle.length() + " 字已截断为 " + TITLE_MAX_LENGTH + " 字");
                rawTitle = rawTitle.substring(0, TITLE_MAX_LENGTH);
            }
            if (rawDigest != null && rawDigest.length() > DIGEST_MAX_LENGTH) {
                lengthWarnings.add("摘要 " + rawDigest.length() + " 字已截断为 " + DIGEST_MAX_LENGTH + " 字");
                rawDigest = rawDigest.substring(0, DIGEST_MAX_LENGTH);
            }
            // 每次保存重置：与 renderWarnings 同理，上一版的截断警告不该留在这一版上
            saveWarnings = List.copyOf(lengthWarnings);
            if (!saveWarnings.isEmpty()) {
                log.warn("草稿保存降级 {} 处：{}", saveWarnings.size(), String.join("；", saveWarnings));
            }
            List<String> syntaxHints = List.of();
            if (markflow) {
                // MARKFLOW：正文是待渲染 Markdown，渲染延迟到交付前；不跑纯段落校验（组件属预期）
                if (looksLikeHandWrittenHtml(body)) {
                    log.warn("MARKFLOW 草稿正文疑似手写内联样式 HTML 而非 MarkFlow 语法 Markdown："
                            + "渲染服务对 HTML 原样透传，精排版式与主题色都不会生效");
                }
                // 渲染器识别不了的写法在这里就告诉模型，否则渲染发生在交付前、模型已无修正机会
                syntaxHints = markflowSyntaxHints(body);
                if (!syntaxHints.isEmpty()) {
                    log.warn("MARKFLOW 草稿疑似语法问题 {} 处：{}", syntaxHints.size(), String.join("；", syntaxHints));
                }
                saveAccent = blankToNull(rawAccent);
                saveDark = blankToNull(rawDark);
                contentMarkdown = body;
                rendered = false;
                renderWarnings = List.of();
                contentHtml = body;
            } else {
                ArticleContentPolicy.requireParagraphProse(body);
                contentMarkdown = null;
                saveAccent = null;
                saveDark = null;
            }
            title = rawTitle.trim();
            author = blankToNull(rawAuthor);
            digest = blankToNull(rawDigest);
            contentHtml = body;
            sourceUrl = blankToNull(rawSourceUrl);
            saved = true;
            documentVersion++;
            String message = markflow
                    ? "文章草稿已保存到本轮任务工作区（MarkFlow 语法 Markdown，交付前由系统渲染为公众号 HTML）"
                    : "文章草稿已保存到本轮任务工作区";
            if (!syntaxHints.isEmpty()) {
                message += "。注意：正文有 " + syntaxHints.size() + " 处语法问题会使版式渲染不出来（见 warnings），"
                        + "请按提示修正后**重新调用 save_article_draft 覆盖保存**。";
            }
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("message", message);
            result.put("engine", layoutEngine.name());
            result.put("rendered", rendered);
            result.put("draft", view());
            if (!syntaxHints.isEmpty()) result.put("warnings", syntaxHints);
            return json(result);
        }

        private synchronized String setCover(SetDraftCoverParam param) {
            if (param.getAssetId() == null) throw new IllegalArgumentException("封面素材ID不能为空");
            coverAssetId = param.getAssetId();
            documentVersion++;
            return json(Map.of("message", "文章封面已设置", "assetId", coverAssetId,
                    "documentVersion", documentVersion));
        }

        /** 交付前渲染：MARKFLOW 模式渲染成功才覆盖 contentHtml（失败抛错，Markdown 不丢）；PROMPT 模式无操作。 */
        public synchronized void renderBeforeDelivery(MarkFlowRenderService renderService) {
            if (layoutEngine != LayoutEngine.MARKFLOW || !saved || rendered) return;
            if (renderService == null) {
                throw new IllegalStateException("MARKFLOW 排版需要 MarkFlowRenderService，但当前上下文未提供");
            }
            MarkFlowRenderService.RenderResult result = renderService.render(contentMarkdown, saveAccent, saveDark);
            // 渲染区段标记（D53）：编辑器链路在渲染缓存写入时经 ArticleAiService.markRenderId 注入，
            // 定时交付链路此前漏了——任务产出的 MARKFLOW 稿落库后没有任何渲染区段锚点，
            // 编辑器往返虽能保留标记（PreservedRenderId），前提是落库时得有。
            // id 前缀用 sched-r 与编辑器会话的 rN 区分：renderIdOf 按值查会话缓存，
            // 撞号会让别的会话把定时稿的区段错认成自己渲染的产物。
            contentHtml = ArticleAiService.markRenderId(result.html(), nextRenderId());
            digest = digest == null || digest.isBlank() ? result.summary() : digest;
            // 渲染器回填的摘要同样受摘要栏长度约束，而它**绕过了保存期的截断**（模型没给 digest 时才会走到这里）。
            // 不在这里再截一次，就会在落库/同步公众号时暴露一个保存期已经修掉的同类问题。
            if (digest != null && digest.length() > DIGEST_MAX_LENGTH) {
                String extra = "摘要（渲染服务生成）" + digest.length() + " 字已截断为 " + DIGEST_MAX_LENGTH + " 字";
                digest = digest.substring(0, DIGEST_MAX_LENGTH);
                List<String> merged = new java.util.ArrayList<>(saveWarnings);
                merged.add(extra);
                saveWarnings = List.copyOf(merged);
                log.warn("草稿保存降级：{}", extra);
            }
            // 留存**实际生效**的主题色而非请求值：模型（或渲染服务）没显式给色时，请求值是 null，
            // 而渲染服务会按默认/派生色渲染——只存 null 的话，日后再用留存源文重排就会换成另一套配色。
            if (result.themeAccent() != null && !result.themeAccent().isBlank()) saveAccent = result.themeAccent();
            if (result.themeDark() != null && !result.themeDark().isBlank()) saveDark = result.themeDark();
            // 渲染降级警告（上游 meta.warnings + 本地扫出的未识别语法）必须能传到运行终态：
            // 产物里字面留着 <steps>/:::compare 就是「版式没有 100% 复刻」，静默入库等于把它藏起来。
            renderWarnings = result.warnings() == null ? List.of() : List.copyOf(result.warnings());
            rendered = true;
            documentVersion++;
        }

        /** 交付前渲染的降级警告（未渲染/无降级时为空）。 */
        public synchronized List<String> renderWarnings() {
            return renderWarnings;
        }

        /**
         * 保存期降级警告（标题/摘要超长被截断；无降级时为空）。
         *
         * <p>与 {@link #renderWarnings()} 分开：渲染降级说的是「版式没复刻」，本项说的是「内容被改短了」，
         * 混在一起会让运行终态的文案指错排查方向。
         */
        public synchronized List<String> saveWarnings() {
            return saveWarnings;
        }

        public synchronized Draft snapshot() {
            if (!saved) throw new IllegalStateException("智能体没有通过 save_article_draft 提交文章");
            return new Draft(title, author, digest, contentHtml, sourceUrl, coverAssetId, documentVersion,
                    layoutEngine, contentMarkdown, saveAccent, saveDark, rendered, renderWarnings, saveWarnings);
        }

        /** 采纳一次完整快照（单智能体执行器把 runScheduledAgent 的产出同步回共享工作区）。 */
        public synchronized void adopt(Draft draft) {
            if (draft == null) return;
            this.title = draft.title();
            this.author = draft.author();
            this.digest = draft.digest();
            this.contentHtml = draft.contentHtml();
            this.sourceUrl = draft.sourceUrl();
            this.coverAssetId = draft.coverAssetId();
            this.documentVersion = draft.documentVersion();
            this.layoutEngine = draft.layoutEngine() == null ? LayoutEngine.PROMPT : draft.layoutEngine();
            this.contentMarkdown = draft.contentMarkdown();
            this.saveAccent = draft.themeAccent();
            this.saveDark = draft.themeDark();
            this.rendered = draft.rendered();
            this.renderWarnings = draft.renderWarnings() == null ? List.of() : List.copyOf(draft.renderWarnings());
            this.saveWarnings = draft.saveWarnings() == null ? List.of() : List.copyOf(draft.saveWarnings());
            this.saved = true;
        }

        private Map<String, Object> view() {
            java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
            boolean markflow = layoutEngine == LayoutEngine.MARKFLOW;
            value.put("title", title == null ? "" : title);
            value.put("author", author == null ? "" : author);
            value.put("digest", digest == null ? "" : digest);
            value.put(markflow ? "contentMarkdown" : "contentHtml", contentHtml == null ? "" : contentHtml);
            value.put("sourceUrl", sourceUrl == null ? "" : sourceUrl);
            value.put("coverAssetId", coverAssetId);
            value.put("documentVersion", documentVersion);
            value.put("saved", saved);
            value.put("engine", layoutEngine.name());
            if (markflow) value.put("rendered", rendered);
            return value;
        }
    }

    @ToolInfo(name = "read_article_draft", description = "读取本次定时创作任务当前的文章草稿。需要检查或继续修改已保存草稿时使用。")
    public static class ReadDraftTool implements Tool<ReadDraftParam> {
        private final DraftState state;
        public ReadDraftTool(DraftState state) { this.state = state; }
        @Override public String execute(ReadDraftParam param) { return state.read(); }
    }

    @Data
    public static class ReadDraftParam extends ToolParam {
        @Param(required = false, description = "读取草稿的原因") private String reason;
    }

    @ToolInfo(name = "save_article_draft", description = "把完整文章保存到本次任务工作区。研究和整理完成后必须调用；再次调用会原子覆盖上一版草稿。正文必须使用系统提示中的公众号视觉模板生成完整内联样式HTML，以01、02等居中章节号、绿色短横线、居中章节标题和自然段组织内容，禁止使用ul、ol、dl或table；可引用素材工具返回的publicUrl插入图片。")
    public static class SaveDraftTool implements Tool<SaveDraftParam> {
        private final DraftState state;
        public SaveDraftTool(DraftState state) { this.state = state; }
        @Override public String execute(SaveDraftParam param) { return state.save(param); }
    }

    @Data
    public static class SaveDraftParam extends ToolParam {
        @Param(description = "完整文章标题，最多64字") private String title;
        @Param(required = false, description = "文章作者") private String author;
        @Param(required = false, description = "文章摘要，最多120字") private String digest;
        @Param(description = "完整文章正文HTML；严格使用系统提示中的公众号视觉模板及内联样式，使用居中章节号、章节标题和p自然段组织行文，不得包含项目符号列表、编号列表、定义列表或表格") private String contentHtml;
        @Param(required = false, description = "最主要的参考来源URL；多个来源应在正文末尾列出") private String sourceUrl;
        @Param(required = false, description = "主题主色（6位hex，仅渲染式排版且主题策略为自动时提供，依据系统提示中的主题对照表就近选择）") private String accent;
        @Param(required = false, description = "主题深色（6位hex，仅渲染式排版且主题策略为自动时提供；未提供时由渲染服务自动派生）") private String dark;
    }

    /**
     * 渲染式排版（MARKFLOW）的保存工具：正文是 MarkFlow 语法 Markdown 源文，不是 HTML。
     *
     * <p>工具名与 PROMPT 版相同（都是 save_article_draft，写作协议里就叫这个名字），但说明文案与参数名不同。
     * 这两份文案是模型实际遵循的那一份，必须与系统提示里的【MarkFlow 语法指令】同向——
     * 此前 MARKFLOW 任务复用了 PROMPT 版的文案（「生成完整内联样式HTML…禁止 ul/ol/dl/table」），
     * 比系统提示更具体，模型照它执行，于是把公众号模板 HTML 当正文提交：渲染服务对 HTML 原样透传，
     * 精排版式一次都没生效、主题色也换不动（颜色已写死在 HTML 里）。
     */
    @ToolInfo(name = "save_article_draft", description = "把完整文章保存到本次任务工作区。研究和整理完成后必须调用；再次调用会原子覆盖上一版草稿。本次排版引擎为渲染式（MarkFlow）：正文必须是 MarkFlow 语法 Markdown 源文，严格遵循系统提示中的【MarkFlow 语法指令】；不要手写 HTML 标签、内联样式、颜色或字体，版式与配色由渲染服务按主题色生成。正文图片用 Markdown 图片语法引用素材工具返回的 publicUrl。")
    public static class SaveMarkflowDraftTool implements Tool<SaveMarkflowDraftParam> {
        private final DraftState state;
        public SaveMarkflowDraftTool(DraftState state) { this.state = state; }
        @Override public String execute(SaveMarkflowDraftParam param) { return state.save(param); }
    }

    @Data
    public static class SaveMarkflowDraftParam extends ToolParam {
        @Param(description = "完整文章标题，最多64字") private String title;
        @Param(required = false, description = "文章作者") private String author;
        @Param(required = false, description = "文章摘要，最多120字") private String digest;
        @Param(description = "完整文章正文的 MarkFlow 语法 Markdown 源文（不是 HTML，不要内联样式）；可用 Markdown 段落、列表、加粗、引用、表格与 MarkFlow 组件标签，语法见系统提示中的【MarkFlow 语法指令】") private String contentMarkdown;
        @Param(required = false, description = "最主要的参考来源URL；多个来源应在正文末尾列出") private String sourceUrl;
        @Param(required = false, description = "主题主色（6位hex；主题策略为自动时依据系统提示中的主题对照表就近选择，交由渲染服务生成配色）") private String accent;
        @Param(required = false, description = "主题深色（6位hex；未提供时由渲染服务自动派生）") private String dark;
    }

    @ToolInfo(name = "set_article_draft_cover", description = "把素材库图片设置为本次定时创作文章的封面。assetId必须来自默认封面、素材库检索、网络图片导入或图片生成/编辑工具。")
    public static class SetDraftCoverTool implements Tool<SetDraftCoverParam> {
        private final DraftState state;
        public SetDraftCoverTool(DraftState state) { this.state = state; }
        @Override public String execute(SetDraftCoverParam param) { return state.setCover(param); }
    }

    @Data
    public static class SetDraftCoverParam extends ToolParam {
        @Param(description = "封面素材assetId") private Long assetId;
    }

    public record Draft(String title, String author, String digest, String contentHtml,
                        String sourceUrl, Long coverAssetId, long documentVersion,
                        LayoutEngine layoutEngine, String contentMarkdown, String themeAccent,
                        String themeDark, boolean rendered, List<String> renderWarnings,
                        List<String> saveWarnings) {
        /** 兼容构造：无渲染降级警告、无保存降级警告。 */
        public Draft(String title, String author, String digest, String contentHtml, String sourceUrl,
                     Long coverAssetId, long documentVersion, LayoutEngine layoutEngine, String contentMarkdown,
                     String themeAccent, String themeDark, boolean rendered) {
            this(title, author, digest, contentHtml, sourceUrl, coverAssetId, documentVersion, layoutEngine,
                    contentMarkdown, themeAccent, themeDark, rendered, List.of(), List.of());
        }

        /** 兼容构造：只有渲染降级警告（新增保存降级警告前的形状）。 */
        public Draft(String title, String author, String digest, String contentHtml, String sourceUrl,
                     Long coverAssetId, long documentVersion, LayoutEngine layoutEngine, String contentMarkdown,
                     String themeAccent, String themeDark, boolean rendered, List<String> renderWarnings) {
            this(title, author, digest, contentHtml, sourceUrl, coverAssetId, documentVersion, layoutEngine,
                    contentMarkdown, themeAccent, themeDark, rendered, renderWarnings, List.of());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("定时文章工具结果序列化失败", error);
        }
    }
}
