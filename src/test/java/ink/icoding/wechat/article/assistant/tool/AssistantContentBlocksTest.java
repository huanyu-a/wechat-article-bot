package ink.icoding.wechat.article.assistant.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内容块引擎纯单测（docs/dev/global-assistant-plan.md §4.4）。
 *
 * <p>不启 Spring 上下文：{@link AssistantContentBlocks} 是纯静态函数，没有依赖。
 *
 * <p>这里固化的最重要一条是「**未触碰的兄弟节点逐字不变**」。全局助手的写工具会把重建后的整段正文
 * 落库，如果一次「只改第 2 段」顺带把第 1、3 段的标签属性、属性顺序或空白重排一遍，
 * 用户看到的就是「我只让它改一段，它改了整篇」——而且这种差异在 diff 里极难发现。
 */
class AssistantContentBlocksTest {

    private static final String ARTICLE = "<h2>标题</h2>"
            + "<p class=\"lead\">导语</p>"
            + "<p>第二段</p>"
            + "<ul><li>甲</li><li>乙</li></ul>";

    @Test
    void blocksAreIndexedFromOneAndKeepTagTextAndHtml() {
        List<AssistantContentBlocks.Block> blocks = AssistantContentBlocks.blocks(ARTICLE);

        assertThat(blocks).hasSize(4);
        // 块号从 1 起：模型看到的编号必须与人数的直觉一致（第 1 块就是第一块）
        assertThat(blocks.get(0).index()).isEqualTo(1);
        assertThat(blocks.get(0).tag()).isEqualTo("h2");
        assertThat(blocks.get(0).text()).isEqualTo("标题");
        assertThat(blocks.get(1).tag()).isEqualTo("p");
        assertThat(blocks.get(1).text()).isEqualTo("导语");
        assertThat(blocks.get(3).tag()).isEqualTo("ul");
        // 列表块的整体文本是它内部文字的拼接
        assertThat(blocks.get(3).text()).contains("甲").contains("乙");
        // 块的 html 是可直接回传的完整元素
        assertThat(blocks.get(1).html()).isEqualTo("<p class=\"lead\">导语</p>");
    }

    @Test
    void countMatchesParsedBlocks() {
        assertThat(AssistantContentBlocks.count(ARTICLE)).isEqualTo(4);
        assertThat(AssistantContentBlocks.count("")).isZero();
    }

    @Test
    void replaceMiddleRangeKeepsOtherBlocksByteIdentical() {
        String replaced = AssistantContentBlocks.replace(ARTICLE, 2, 2, List.of("<p>改过的导语</p>"));

        List<AssistantContentBlocks.Block> after = AssistantContentBlocks.blocks(replaced);
        assertThat(after).hasSize(4);
        assertThat(after.get(1).html()).isEqualTo("<p>改过的导语</p>");
        // 关键断言：其余三块与原文逐字相同（含 class 属性与列表内部结构）
        assertThat(after.get(0).html()).isEqualTo("<h2>标题</h2>");
        assertThat(after.get(2).html()).isEqualTo("<p>第二段</p>");
        assertThat(after.get(3).html()).isEqualTo("<ul><li>甲</li><li>乙</li></ul>");
    }

    @Test
    void replaceMultiBlockRangeCollapsesToOneOrMoreBlocks() {
        String replaced = AssistantContentBlocks.replace(ARTICLE, 1, 2,
                List.of("<h1>新标题</h1>", "<p>新导语</p>"));

        List<AssistantContentBlocks.Block> after = AssistantContentBlocks.blocks(replaced);
        assertThat(after).hasSize(4);
        assertThat(after.get(0).html()).isEqualTo("<h1>新标题</h1>");
        assertThat(after.get(1).html()).isEqualTo("<p>新导语</p>");
        assertThat(after.get(2).html()).isEqualTo("<p>第二段</p>");
    }

    @Test
    void insertAtZeroPutsBlocksAtTheVeryBeginning() {
        String inserted = AssistantContentBlocks.insert(ARTICLE, 0, List.of("<p>开头</p>"));

        List<AssistantContentBlocks.Block> after = AssistantContentBlocks.blocks(inserted);
        assertThat(after).hasSize(5);
        assertThat(after.get(0).html()).isEqualTo("<p>开头</p>");
        assertThat(after.get(1).html()).isEqualTo("<h2>标题</h2>");
    }

    @Test
    void insertAtEndAppendsBlocks() {
        String inserted = AssistantContentBlocks.insert(ARTICLE, 4, List.of("<p>结尾</p>"));

        List<AssistantContentBlocks.Block> after = AssistantContentBlocks.blocks(inserted);
        assertThat(after).hasSize(5);
        assertThat(after.get(4).html()).isEqualTo("<p>结尾</p>");
        assertThat(after.get(0).html()).isEqualTo("<h2>标题</h2>");
    }

    @Test
    void insertIntoEmptyArticleWorks() {
        String inserted = AssistantContentBlocks.insert("", 0, List.of("<p>第一段</p>"));

        assertThat(AssistantContentBlocks.blocks(inserted)).hasSize(1);
        assertThat(AssistantContentBlocks.blocks(inserted).get(0).text()).isEqualTo("第一段");
    }

    @Test
    void deleteWholeRangeEmptiesTheBody() {
        String deleted = AssistantContentBlocks.delete(ARTICLE, 1, 4);

        assertThat(AssistantContentBlocks.count(deleted)).isZero();
    }

    @Test
    void deleteMiddleBlockKeepsOthers() {
        String deleted = AssistantContentBlocks.delete(ARTICLE, 2, 3);

        List<AssistantContentBlocks.Block> after = AssistantContentBlocks.blocks(deleted);
        assertThat(after).hasSize(2);
        assertThat(after.get(0).html()).isEqualTo("<h2>标题</h2>");
        assertThat(after.get(1).html()).isEqualTo("<ul><li>甲</li><li>乙</li></ul>");
    }

    // ---------------------------------------------------------------- 越界与非法输入

    @Test
    void replaceRejectsStartIndexBelowOne() {
        assertThatThrownBy(() -> AssistantContentBlocks.replace(ARTICLE, 0, 1, List.of("<p>x</p>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("从 1 开始");
    }

    @Test
    void replaceRejectsEndBeforeStart() {
        assertThatThrownBy(() -> AssistantContentBlocks.replace(ARTICLE, 3, 2, List.of("<p>x</p>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能小于");
    }

    @Test
    void replaceRejectsEndBeyondBlockCount() {
        // 报错里要带上「本文共几块」：模型据此能自己纠正，而不是反复猜
        assertThatThrownBy(() -> AssistantContentBlocks.replace(ARTICLE, 1, 9, List.of("<p>x</p>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("共 4 个内容块");
    }

    @Test
    void insertRejectsNegativeIndex() {
        assertThatThrownBy(() -> AssistantContentBlocks.insert(ARTICLE, -1, List.of("<p>x</p>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超出范围");
    }

    @Test
    void insertRejectsIndexBeyondBlockCount() {
        assertThatThrownBy(() -> AssistantContentBlocks.insert(ARTICLE, 99, List.of("<p>x</p>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("共 4 个内容块");
    }

    @Test
    void emptyNewBlocksAreRejected() {
        assertThatThrownBy(() -> AssistantContentBlocks.replace(ARTICLE, 1, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void blankBlockIsRejected() {
        assertThatThrownBy(() -> AssistantContentBlocks.replace(ARTICLE, 1, 1, List.of("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("是空的");
    }

    // ---------------------------------------------------------------- 危险内容

    /** 正文最终会进入公众号 HTML：脚本与内联事件属性必须在写库前就被拒，而不是被静默清洗。 */
    @Test
    void scriptTagIsRejected() {
        assertThatThrownBy(() -> AssistantContentBlocks.insert(ARTICLE, 0,
                List.of("<p>正常</p><script>alert(1)</script>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许的元素或属性");
    }

    @Test
    void iframeTagIsRejected() {
        assertThatThrownBy(() -> AssistantContentBlocks.insert(ARTICLE, 0,
                List.of("<iframe src=\"https://evil.example\"></iframe>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许的元素或属性");
    }

    @Test
    void inlineEventHandlerIsRejectedCaseInsensitively() {
        assertThatThrownBy(() -> AssistantContentBlocks.insert(ARTICLE, 0,
                List.of("<p ONERROR=\"alert(1)\">x</p>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许的元素或属性");
    }

    @Test
    void normalRichBlockIsAcceptedAndNormalised() {
        String inserted = AssistantContentBlocks.insert(ARTICLE, 0,
                List.of("<p class=\"note\">说明<strong>加粗</strong></p>"));

        assertThat(AssistantContentBlocks.blocks(inserted).get(0).html())
                .isEqualTo("<p class=\"note\">说明<strong>加粗</strong></p>");
    }
}