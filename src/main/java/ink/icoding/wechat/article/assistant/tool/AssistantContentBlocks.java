package ink.icoding.wechat.article.assistant.tool;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文章正文的「内容块」引擎（docs/dev/global-assistant-plan.md §4.4）。
 *
 * <p>为什么需要它：全局助手的写工具作用于**数据库里的文章**，没有 Tiptap 文档可以逐行改，
 * 只能拿到整段 {@code content_html}。块级编辑把这段 HTML 按**顶层元素**切开（标题、段落、列表、
 * 图片块各算一块），模型按块号读写，服务端再把改动拼回去——与编辑器那条链路的「逻辑行」同一语义
 * （{@code ArticleEditorTools} 的 read_blocks/insert_blocks/replace_blocks 是浏览器版的同一套模型）。
 *
 * <p>为什么不直接把 HTML 交给模型改：整篇正文动辄数万字符，改写时任何一个标签被模型抄错都会
 * 落库成坏版式，而按块编辑让「未触碰的部分」根本不经过模型的手。
 *
 * <p><b>未被改动的兄弟节点必须逐字原样保留</b>：重新序列化（{@code Jsoup} 再拼字符串）而不是
 * 取原始切片，是因为块号来自解析结果、原始切片需要额外维护偏移量；但只要在还原时用每个元素自己的
 * {@code outerHtml()} 并且关掉 prettyPrint，除了被编辑的那几块，其余节点的字节就与原文一致。
 * 若开着 prettyPrint，Jsoup 会插换行与缩进，一次「只改第 3 段」就会把整篇格式重排一遍。
 */
public final class AssistantContentBlocks {
    /**
     * 写进正文前一律拒绝的片段（比较时统一转小写）。
     *
     * <p>为什么在工具层再查一遍：{@code ArticleService.clean(...)} 落库前已经清洗过，
     * 但它对 {@code PROMPT} 引擎走的是 Jsoup 白名单——危险内容会被**静默剥掉**，模型看到
     * 「写入成功」，用户看到的是正文少了一段。这里提前拦下并给出中文原因，让模型知道要改的是什么。
     * 正文最终进入公众号 HTML，多一道显式拒绝的代价远小于一次静默消失。
     */
    private static final String[] FORBIDDEN_SNIPPETS = {"<script", "<iframe", "onload=", "onerror="};

    private AssistantContentBlocks() {
    }

    /** 一个内容块：块号（1 起）、标签名、纯文本、原始 HTML。 */
    public record Block(int index, String tag, String text, String html) {
    }

    /** 解析正文为内容块列表（顺序即正文顺序）。 */
    public static List<Block> blocks(String html) {
        List<Element> children = children(html);
        List<Block> blocks = new ArrayList<>(children.size());
        for (int index = 0; index < children.size(); index++) {
            Element element = children.get(index);
            blocks.add(new Block(index + 1, element.tagName(), element.wholeText().trim(), element.outerHtml()));
        }
        return List.copyOf(blocks);
    }

    /** 正文顶层内容块数量（0 表示空文章；{@code ArticleService.clean} 会把空正文落成单个空段落）。 */
    public static int count(String html) {
        return children(html).size();
    }

    /**
     * 用 {@code newBlocks} 原子替换块号 {@code startIndex}–{@code endIndex}（含两端）。
     *
     * @return 重建后的完整正文 HTML
     * @throws IllegalArgumentException 块号越界或新块含不允许的内容
     */
    public static String replace(String html, int startIndex, int endIndex, List<String> newBlocks) {
        List<Element> children = children(html);
        requireRange(children.size(), startIndex, endIndex);
        List<String> sanitized = sanitize(newBlocks);
        List<String> rebuilt = new ArrayList<>(children.size() + sanitized.size());
        for (int index = 0; index < children.size(); index++) {
            int blockNumber = index + 1;
            if (blockNumber == startIndex) rebuilt.addAll(sanitized);
            // 被替换的块整体丢弃：新块已经在 startIndex 之前插好
            if (blockNumber >= startIndex && blockNumber <= endIndex) continue;
            rebuilt.add(children.get(index).outerHtml());
        }
        return String.join("", rebuilt);
    }

    /**
     * 在第 {@code afterIndex} 块之后插入若干块；{@code afterIndex == 0} 表示插到正文最前面。
     *
     * <p>为什么用「前一块的块号」而不是「新块自己的块号」：插入点的语义是「接在哪一块后面」，
     * 0 天然表达「最前面」，模型不需要为「插到开头」编造一个特殊块号。
     */
    public static String insert(String html, int afterIndex, List<String> newBlocks) {
        List<Element> children = children(html);
        if (afterIndex < 0 || afterIndex > children.size()) {
            throw new IllegalArgumentException("插入位置超出范围：" + (children.isEmpty()
                    ? "这篇文章正文还没有任何内容块，请用 0 表示插到最前面"
                    : "本文共 " + children.size() + " 个内容块，afterIndex 只能是 0 到 " + children.size()));
        }
        List<String> sanitized = sanitize(newBlocks);
        List<String> rebuilt = new ArrayList<>(children.size() + sanitized.size());
        if (afterIndex == 0) rebuilt.addAll(sanitized);
        for (int index = 0; index < children.size(); index++) {
            rebuilt.add(children.get(index).outerHtml());
            if (index + 1 == afterIndex) rebuilt.addAll(sanitized);
        }
        return String.join("", rebuilt);
    }

    /**
     * 删除块号 {@code startIndex}–{@code endIndex}（含两端）；删除全部块得到空正文。
     *
     * <p>不委托 {@link #replace} 并传空列表：那条路径会给新块做清洗，而清洗拒绝「空列表」
     * （「新内容块不能为空」是**替换/插入**的正确约束，对删除毫无意义）。删除是一等操作，
     * 这里自己重建，只丢弃指定区间，其余块逐字保留。
     */
    public static String delete(String html, int startIndex, int endIndex) {
        List<Element> children = children(html);
        requireRange(children.size(), startIndex, endIndex);
        List<String> rebuilt = new ArrayList<>(children.size());
        for (int index = 0; index < children.size(); index++) {
            int blockNumber = index + 1;
            if (blockNumber >= startIndex && blockNumber <= endIndex) continue;
            rebuilt.add(children.get(index).outerHtml());
        }
        return String.join("", rebuilt);
    }

    private static List<Element> children(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        // 见类注释：关掉 prettyPrint 才能保证未被编辑的兄弟节点逐字不变
        document.outputSettings().prettyPrint(false);
        return new ArrayList<>(document.body().children());
    }

    private static void requireRange(int total, int startIndex, int endIndex) {
        if (startIndex < 1) {
            throw new IllegalArgumentException("开始块号必须从 1 开始（收到 " + startIndex + "）");
        }
        if (endIndex < startIndex) {
            throw new IllegalArgumentException("结束块号（" + endIndex + "）不能小于开始块号（" + startIndex + "）");
        }
        if (total == 0) {
            throw new IllegalArgumentException("这篇文章正文还没有任何内容块，请先用 insert_article_blocks 写入正文");
        }
        if (endIndex > total) {
            throw new IllegalArgumentException("块号超出范围：本文共 " + total + " 个内容块（块号从 1 开始），"
                    + "收到 " + startIndex + "–" + endIndex + "。请先调用 read_article_blocks 重新核对块号");
        }
    }

    private static List<String> sanitize(List<String> newBlocks) {
        if (newBlocks == null || newBlocks.isEmpty()) {
            throw new IllegalArgumentException("新内容块不能为空：请给出完整且可独立插入的 HTML 元素");
        }
        List<String> sanitized = new ArrayList<>(newBlocks.size());
        for (int index = 0; index < newBlocks.size(); index++) {
            sanitized.add(sanitizeBlock(index + 1, newBlocks.get(index)));
        }
        return sanitized;
    }

    private static String sanitizeBlock(int position, String block) {
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException("第 " + position + " 个内容块是空的：每个块都必须是一段完整 HTML（如 <p>…</p>）");
        }
        requireSafe(block, position);
        Document document = Jsoup.parseBodyFragment(block);
        document.outputSettings().prettyPrint(false);
        String normalized = document.body().html();
        // 再查一次：小写化/重新序列化之后仍可能暴露出原文里被大小写或转义掩盖的片段
        requireSafe(normalized, position);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("第 " + position + " 个内容块解析后为空：请提供完整的 HTML 元素（如 <p>…</p>）");
        }
        return normalized;
    }

    private static void requireSafe(String html, int position) {
        String lower = html.toLowerCase(Locale.ROOT);
        for (String snippet : FORBIDDEN_SNIPPETS) {
            if (lower.contains(snippet)) {
                throw new IllegalArgumentException("第 " + position + " 个内容块含有不允许的元素或属性（" + snippet + "）："
                        + "正文最终会进入公众号 HTML，脚本、内联框架与事件属性一律拒绝，请移除后重试");
            }
        }
    }
}