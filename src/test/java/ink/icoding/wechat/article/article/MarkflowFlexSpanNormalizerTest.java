package ink.icoding.wechat.article.article;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link MarkflowFlexSpanNormalizer} - the HTML half of the D49 leftover
 * ("存量稿件合并"), four legs as required: normal / boundary / exception / idempotent.
 *
 * <p>The fixtures below are shapes that were really captured: the split row is the editor exit of
 * article 8 ({@code target/probe/browser/r39_article8_editor-exit.html}), the bullet-dot row is the
 * documented MarkFlow list item (known-issues-handoff.md D32), the three-badge sample is the D35
 * guard sample from {@code tools/render-verify/browser/r48-flex-span-probe.js}.
 */
class MarkflowFlexSpanNormalizerTest {

    // ---------- fixtures ----------

    /** Bullet dot: a {@code flex-shrink:0} span, NOT a flex item. */
    private static final String DOT = "<span style=\"display:inline-block;width:6px;height:6px;"
            + "border-radius:50%;background-color:#27ae60;margin-right:12px;margin-top:12px;flex-shrink:0\""
            + " contenteditable=\"false\"></span>";

    /** One text column of the list row, as the browser serializes it. */
    private static final String FLEX = "<span style=\"flex: 1 1 0%; min-width: 0px; overflow-wrap: anywhere;\">";

    /** The healthy (render output) form of the same row: one single flex column. */
    private static final String RENDERED_ROW =
            "<section style=\"margin: 4px 0px; line-height: 1.8; letter-spacing: 0.5px; display: flex; align-items: flex-start;\">"
                    + "<p style=\"display: flex; align-items: flex-start; margin: 0px;\">"
                    + DOT
                    + "<span style=\"flex:1\"><strong style=\"font-weight: 800; color: rgb(17, 24, 39);\">时间</strong>"
                    + "：每天 30 至 60 分钟即可。</span>"
                    + "</p></section>";

    /** The same row after the old editor split the single column into two sibling spans. */
    private static final String SPLIT_ROW =
            "<section style=\"margin: 4px 0px; line-height: 1.8; letter-spacing: 0.5px; display: flex; align-items: flex-start;\">"
                    + "<p style=\"display: flex; align-items: flex-start; margin: 0px;\">"
                    + DOT
                    + FLEX + "<strong style=\"font-weight: 800; color: rgb(17, 24, 39);\">时间</strong></span>"
                    + FLEX + "：每天 30 至 60 分钟即可。</span>"
                    + "</p></section>";

    /** The row rebuilt by the merge: one span again, dot and bold untouched. */
    private static final String MERGED_ROW =
            "<section style=\"margin: 4px 0px; line-height: 1.8; letter-spacing: 0.5px; display: flex; align-items: flex-start;\">"
                    + "<p style=\"display: flex; align-items: flex-start; margin: 0px;\">"
                    + DOT
                    + "<span style=\"flex: 1 1 0%;min-width: 0px;overflow-wrap: anywhere\">"
                    + "<strong style=\"font-weight: 800; color: rgb(17, 24, 39);\">时间</strong>"
                    + "：每天 30 至 60 分钟即可。</span>"
                    + "</p></section>";

    private static int count(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String textOf(String html) {
        return Jsoup.parseBodyFragment(html).text();
    }

    // ---------- 正常：相邻的 flex:1 兄弟合并成一个嵌套 span ----------

    /** Two sibling {@code flex:1} spans become one; the merged span keeps the flex style. */
    @Test
    void adjacentFlexSiblingsAreMergedIntoOneNestedSpan() {
        MarkflowFlexSpanNormalizer.Result result = MarkflowFlexSpanNormalizer.normalizeWithStats(SPLIT_ROW);

        assertThat(result.changed()).isTrue();
        assertThat(result.mergedContainers()).isEqualTo(1);
        assertThat(result.spansBefore()).isEqualTo(2);
        assertThat(result.spansAfter()).isEqualTo(1);
        // The exact shape: one span left in the row, carrying the shared flex style.
        assertThat(result.html()).isEqualTo(MERGED_ROW);
        assertThat(count(result.html(), FLEX)).isZero();
        assertThat(count(result.html(), "flex: 1 1 0%;min-width: 0px;overflow-wrap: anywhere")).isEqualTo(1);
        // Everything that was inside the split spans survives; the bullet dot is untouched.
        assertThat(textOf(result.html())).isEqualTo(textOf(SPLIT_ROW));
        assertThat(result.html()).contains(DOT);
    }

    /**
     * A sibling carrying extra declarations keeps them on a **nested** span, so the inner inline
     * style is not flattened onto the outer flex box (that is what the fixed editor produces).
     */
    @Test
    void extraInnerStyleStaysNestedInsteadOfBeingFlattened() {
        String split = "<section style=\"display:flex;align-items:center\">"
                + "<span style=\"width:6px;height:6px;border-radius:50%;background:#27ae60;margin-right:12px\"></span>"
                + "<span style=\"flex:1\"><strong>时间</strong>：每天 </span>"
                + "<span style=\"flex:1;color:#e74c3c\">09:00</span>"
                + "<span style=\"flex:1\"> 更新</span>"
                + "</section>";

        MarkflowFlexSpanNormalizer.Result result = MarkflowFlexSpanNormalizer.normalizeWithStats(split);

        assertThat(result.html()).isEqualTo(
                "<section style=\"display:flex;align-items:center\">"
                        + "<span style=\"width:6px;height:6px;border-radius:50%;background:#27ae60;margin-right:12px\"></span>"
                        + "<span style=\"flex:1\"><strong>时间</strong>：每天 "
                        + "<span style=\"color:#e74c3c\">09:00</span> 更新</span>"
                        + "</section>");
        assertThat(result.spansBefore()).isEqualTo(3);
        assertThat(result.spansAfter()).isEqualTo(1);
    }

    /** Two split rows in one document: each flex container is healed on its own. */
    @Test
    void everyFlexContainerInTheDocumentIsMerged() {
        String html = "<div><p style=\"display:flex\">"
                + "<span style=\"flex:1\">A</span><span style=\"flex:1\">B</span></p>"
                + "<p>plain paragraph</p>"
                + "<p style=\"display:flex\">"
                + "<span style=\"flex:1\">C</span><span style=\"flex:1\">D</span><span style=\"flex:1\">E</span></p></div>";

        MarkflowFlexSpanNormalizer.Result result = MarkflowFlexSpanNormalizer.normalizeWithStats(html);

        assertThat(result.mergedContainers()).isEqualTo(2);
        assertThat(result.spansBefore()).isEqualTo(5);
        assertThat(result.spansAfter()).isEqualTo(2);
        assertThat(result.html()).isEqualTo("<div><p style=\"display:flex\">"
                + "<span style=\"flex:1\">AB</span></p><p>plain paragraph</p>"
                + "<p style=\"display:flex\"><span style=\"flex:1\">CDE</span></p></div>");
    }

    /**
     * Blank text between the spans does not break adjacency: a pretty-printed row heals too,
     * because the flexbox spec does not render white-space-only text runs as flex items.
     */
    @Test
    void blankTextBetweenTheSpansStillMerges() {
        String html = "<p style=\"display:flex\">\n  <span style=\"flex:1\">A</span>\n  <span style=\"flex:1\">B</span>\n</p>";

        assertThat(MarkflowFlexSpanNormalizer.normalizeWithStats(html).changed()).isTrue();
        assertThat(count(MarkflowFlexSpanNormalizer.normalize(html), "flex:1")).isEqualTo(1);
    }

    // ---------- 边界：只有「≥2 个相邻 flex:1 span 兄弟」才动 ----------

    /** A single flex column is already healthy (and may be an intentional column) - untouched. */
    @Test
    void singleFlexSiblingIsReturnedUnchanged() {
        MarkflowFlexSpanNormalizer.Result result = MarkflowFlexSpanNormalizer.normalizeWithStats(RENDERED_ROW);

        assertThat(result.changed()).isFalse();
        assertThat(MarkflowFlexSpanNormalizer.normalize(RENDERED_ROW)).isSameAs(RENDERED_ROW);
    }

    /** Without a flex container there is nothing to heal - the D35 badge sample stays intact. */
    @Test
    void flexSpansOutsideAFlexContainerAreUntouched() {
        String badges = "<span style=\"display:inline-flex;padding:4px 12px;border-radius:999px\">A</span>"
                + "<span style=\"display:inline-flex;padding:4px 12px;border-radius:999px\">B</span>"
                + "<span style=\"display:inline-flex;padding:4px 12px;border-radius:999px\">C</span>";

        assertThat(MarkflowFlexSpanNormalizer.normalize(badges)).isSameAs(badges);
        assertThat(MarkflowFlexSpanNormalizer.hasSplitFlexSpans(badges)).isFalse();
    }

    /**
     * A span without a style attribute has no flex declaration, so it is neither a merge candidate
     * nor something the merge writes over.
     */
    @Test
    void spansWithoutStyleAreNeverMerged() {
        String html = "<p style=\"display:flex\"><span>A</span><span>B</span>"
                + "<span style=\"flex:1\">C</span></p>";

        assertThat(MarkflowFlexSpanNormalizer.normalize(html)).isSameAs(html);
    }

    /**
     * The bullet dot declares {@code flex-shrink:0}, not {@code flex} - it must not be pulled into
     * the run (this is the D32 dot, whose loss used to blank the whole list item).
     */
    @Test
    void bulletDotIsNeverPartOfTheMergedRun() {
        String result = MarkflowFlexSpanNormalizer.normalize(SPLIT_ROW);

        assertThat(result).contains(DOT);
        // dot + the one merged column; no second flex column is left
        assertThat(count(result, "<span")).isEqualTo(2);
        assertThat(count(result, "<strong")).isEqualTo(1);
    }

    /**
     * MarkFlow renders multi-column components with {@code <section>} children carrying
     * {@code flex:1}; merging those would turn a metrics row into one cell, so only spans qualify.
     */
    @Test
    void sectionColumnsAreNotMerged() {
        String html = "<section style=\"display:flex;gap:8px\">"
                + "<section style=\"text-align:center;flex:1\">A</section>"
                + "<section style=\"text-align:center;flex:1\">B</section></section>";

        assertThat(MarkflowFlexSpanNormalizer.normalize(html)).isSameAs(html);
    }

    /**
     * Only <b>adjacent</b> siblings merge: real text between two boxes renders as its own flex
     * item, so moving it inside the merged span would change the layout.
     */
    @Test
    void nonBlankTextBetweenTheSpansBlocksTheMerge() {
        String html = "<p style=\"display:flex\"><span style=\"flex:1\">A</span> x <span style=\"flex:1\">B</span></p>";

        assertThat(MarkflowFlexSpanNormalizer.normalize(html)).isSameAs(html);
    }

    /** A {@code flex:2} box is not a {@code flex:1} copy, so a run ends there. */
    @Test
    void aDifferentFlexValueEndsTheRun() {
        String html = "<p style=\"display:flex\"><span style=\"flex:1\">A</span>"
                + "<span style=\"flex:2 1 0%\">B</span></p>";

        assertThat(MarkflowFlexSpanNormalizer.normalize(html)).isSameAs(html);
    }

    // ---------- 异常：坏输入不抛异常，安全跳过 ----------

    /** {@code null} in, {@code null} out. */
    @Test
    void nullHtmlIsReturnedAsNull() {
        assertThat(MarkflowFlexSpanNormalizer.normalize(null)).isNull();
        assertThat(MarkflowFlexSpanNormalizer.normalizeWithStats(null).changed()).isFalse();
        assertThatNoException().isThrownBy(() -> MarkflowFlexSpanNormalizer.hasSplitFlexSpans(null));
    }

    /** Empty and whitespace-only bodies are returned unchanged. */
    @Test
    void emptyHtmlIsReturnedUnchanged() {
        assertThat(MarkflowFlexSpanNormalizer.normalize("")).isEmpty();
        assertThat(MarkflowFlexSpanNormalizer.normalize("   ")).isSameAs("   ");
    }

    /** Unpaired tags: no exception, and nothing is silently duplicated or dropped. */
    @Test
    void unpairedTagsDoNotThrow() {
        String malformed = "<section style=\"display:flex\"><span style=\"flex:1\">a</p></div>";

        // A thrown exception fails this test already; the assertions pin the "safe" part.
        MarkflowFlexSpanNormalizer.Result result = MarkflowFlexSpanNormalizer.normalizeWithStats(malformed);

        assertThat(result.changed()).isFalse();
        assertThat(textOf(MarkflowFlexSpanNormalizer.normalize(malformed))).isEqualTo(textOf(malformed));
    }

    /** Unpaired tags on a document that *would* merge: it still must not throw. */
    @Test
    void unpairedTagsAroundAMergeableRowDoNotThrow() {
        String malformed = "<p style=\"display:flex\"><span style=\"flex:1\">A</span>"
                + "<span style=\"flex:1\">B</span></p></section>";

        String healed = MarkflowFlexSpanNormalizer.normalize(malformed);

        assertThat(textOf(healed)).isEqualTo("AB");
        assertThat(count(healed, "flex:1")).isEqualTo(1);
    }

    /** Garbage and unterminated values in a style attribute are skipped, not thrown away. */
    @Test
    void brokenStyleValuesDoNotThrow() {
        String html = "<p style=\"display:flex\">"
                + "<span style=\"flex:1;color:\">A</span>"
                + "<span style=\"flex:1;width:calc(10px;gap:8px\">B</span></p>";

        assertThatNoException().isThrownBy(() -> MarkflowFlexSpanNormalizer.normalize(html));
    }

    // ---------- 幂等：对已合并结果再跑一次不变 ----------

    /** Second pass over the healed row reports no change at all. */
    @Test
    void healingTheSameRowTwiceIsANoOp() {
        MarkflowFlexSpanNormalizer.Result first = MarkflowFlexSpanNormalizer.normalizeWithStats(SPLIT_ROW);
        String healed = first.html();
        MarkflowFlexSpanNormalizer.Result second = MarkflowFlexSpanNormalizer.normalizeWithStats(healed);

        assertThat(healed).isEqualTo(MERGED_ROW);
        assertThat(second.changed()).isFalse();
        assertThat(second.mergedContainers()).isZero();
        assertThat(MarkflowFlexSpanNormalizer.normalize(healed)).isSameAs(healed);
    }

    /** The healthy render form is a fixed point of the normalizer. */
    @Test
    void healthyRenderOutputIsAFixedPoint() {
        assertThat(MarkflowFlexSpanNormalizer.normalize(RENDERED_ROW)).isSameAs(RENDERED_ROW);
    }
}
