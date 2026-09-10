package ink.icoding.wechat.article.article;

import ink.icoding.wechat.article.skill.LayoutEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ArticleContentPolicy 引擎感知校验（skills-agent-plan 5.10.4 / 8.1）。
 */
class ArticleContentPolicyTest {

    @Test
    void promptEngineRejectsLists() {
        assertThatThrownBy(() -> ArticleContentPolicy.requireParagraphProse(
                "<ul><li>x</li></ul>", LayoutEngine.PROMPT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markflowEngineAllowsListsAndTables() {
        assertThatCode(() -> ArticleContentPolicy.requireParagraphProse(
                "<ul><li>x</li></ul><table><tr><td>1</td></tr></table>", LayoutEngine.MARKFLOW))
                .doesNotThrowAnyException();
    }

    @Test
    void promptEngineRejectsMarkflowSyntaxSmuggling() {
        assertThatThrownBy(() -> ArticleContentPolicy.requireParagraphProse(
                "<p>:::steps</p>", LayoutEngine.PROMPT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MarkFlow");
        assertThatThrownBy(() -> ArticleContentPolicy.requireParagraphProse(
                "<p>{{render:1}}</p>", LayoutEngine.PROMPT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promptEngineKeepsOriginalSingleArgBehavior() {
        assertThatCode(() -> ArticleContentPolicy.requireParagraphProse(
                "<p style=\"margin:0\">自然段</p>"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ArticleContentPolicy.requireParagraphProse("<dl><dt>x</dt></dl>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
