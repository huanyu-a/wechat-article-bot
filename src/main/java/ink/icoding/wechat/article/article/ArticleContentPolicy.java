package ink.icoding.wechat.article.article;

import ink.icoding.wechat.article.skill.LayoutEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class ArticleContentPolicy {
    private static final String FORBIDDEN_GENERATED_ELEMENTS = "ul, ol, dl, table";
    private static final String WECHAT_PARAGRAPH_SPACING = "margin-bottom: 16px;";

    private ArticleContentPolicy() {
    }

    public static void requireParagraphProse(String html) {
        requireParagraphProse(html, LayoutEngine.PROMPT);
    }

    /**
     * 引擎感知的内容校验（skills-agent-plan 5.10.4）。
     * PROMPT 引擎维持纯段落强校验（并拒绝 ::: 组件标记与 {{render: 占位符，防借 MarkFlow 语法绕过）；
     * MARKFLOW 引擎的正文是待渲染 Markdown，列表/表格/组件属预期，跳过纯段落校验。
     */
    public static void requireParagraphProse(String html, LayoutEngine engine) {
        if (html == null || html.isBlank()) return;
        // MARKFLOW：正文含渲染区段占位符 {{render:id}} 与组件语法均属预期，跳过纯段落校验。
        // 未解析占位符的拒绝发生在服务端占位替换之后（ArticleAiService.requestTool），此处不做。
        if (engine == LayoutEngine.MARKFLOW) return;
        if (html.contains(":::") || html.contains("{{render:")) {
            throw new IllegalArgumentException("当前排版为指令式，正文不要使用 MarkFlow 语法标记，请直接输出内联样式 HTML");
        }
        Document document = Jsoup.parseBodyFragment(html);
        if (!document.select(FORBIDDEN_GENERATED_ELEMENTS).isEmpty()) {
            throw new IllegalArgumentException("文章正文不要使用列表或表格，请改用标题和自然段连续表达");
        }
    }

    public static String formatForWechat(String html) {
        return formatForWechat(html, LayoutEngine.PROMPT);
    }

    /**
     * 交付微信前的正文整理（引擎感知）。
     *
     * <p>PROMPT 引擎：模型手写内联样式，段落间距由本方法统一补齐（当前行为不变）。
     *
     * <p>MARKFLOW 引擎：正文是渲染服务的产物，**已自带完整内联样式**，无条件追加段间距会破坏它——
     * 渲染器给组件内部段落（步骤卡、徽章、对比卡等）的间距会被这行 `margin-bottom: 16px;`
     * 覆盖掉（它排在 style 末尾，优先级更高），于是公众号侧的版式与渲染结果不一致，
     * 也就是「没有 100% 复刻 MarkFlow 渲染能力」的直接来源之一。
     * 因此这里只给**完全没有 margin 声明**的段落补默认间距：渲染器声明过的段落一律尊重其原值，
     * 未声明时仍保留安全网（不至于两个段落粘在一起）。
     */
    public static String formatForWechat(String html, LayoutEngine engine) {
        if (html == null || html.isBlank()) return "<p style=\"margin-bottom: 16px;\"></p>";
        boolean markflow = engine == LayoutEngine.MARKFLOW;
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        for (Element paragraph : document.select("p")) {
            String style = paragraph.attr("style").trim();
            if (markflow && declaresMargin(style)) continue;
            if (!style.isEmpty() && !style.endsWith(";")) style += ";";
            paragraph.attr("style", (style.isEmpty() ? "" : style + " ") + WECHAT_PARAGRAPH_SPACING);
        }
        return document.body().html();
    }

    /** 段落内联样式是否已声明间距（margin / margin-bottom / margin-top 任一）。 */
    private static boolean declaresMargin(String style) {
        return style != null && style.toLowerCase(java.util.Locale.ROOT).contains("margin");
    }
}
