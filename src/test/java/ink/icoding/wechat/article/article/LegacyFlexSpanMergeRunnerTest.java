package ink.icoding.wechat.article.article;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link LegacyFlexSpanMergeRunner} loop: the defaults, the per-article write
 * contract and the flag that turns reporting into writing. No database and no Spring context - the
 * store is a fake, so dry-run and apply are exercised with exactly the rows the test decides.
 *
 * <p>What these tests defend: <b>a plain boot must not write anything</b>. Every existing runner in
 * this project runs on start-up, so "dry-run by default" is the property that keeps a content
 * migration safe to leave inside the application.
 */
class LegacyFlexSpanMergeRunnerTest {

    private static final String SPLIT_ROW = "<p style=\"display:flex\">"
            + "<span style=\"flex:1\">A</span><span style=\"flex:1\">B</span></p>";

    private static final String MERGED_ROW = "<p style=\"display:flex\"><span style=\"flex:1\">AB</span></p>";

    /** A store that records the writes instead of performing them. */
    private static final class FakeStore implements LegacyFlexSpanMergeRunner.Store {
        private final List<Long> ids = new ArrayList<>();
        private final Map<Long, String> bodies = new HashMap<>();
        /** Ids the database reports as gone (the write affects 0 rows). */
        private final Set<Long> gone = new HashSet<>();
        /** Ids whose write fails. */
        private final Set<Long> failing = new HashSet<>();

        private FakeStore(Long id, String contentHtml) {
            add(id, contentHtml);
        }

        private void add(Long id, String contentHtml) {
            ids.add(id);
            bodies.put(id, contentHtml);
        }

        @Override
        public List<LegacyFlexSpanMergeRunner.Store.ArticleRow> markflowFlexCandidates() {
            // Read from `bodies` so a write made by this run is visible to the next one, like a
            // real re-scan of the table.
            return ids.stream()
                    .map(id -> new LegacyFlexSpanMergeRunner.Store.ArticleRow(id, "稿件 " + id, bodies.get(id)))
                    .toList();
        }

        @Override
        public int writeMergedContentHtml(Long id, String contentHtml) {
            if (failing.contains(id)) throw new IllegalStateException("simulated database failure");
            if (gone.contains(id) || !bodies.containsKey(id)) return 0;
            bodies.put(id, contentHtml);
            return 1;
        }
    }

    private static FakeStore store(Long id, String contentHtml) {
        return new FakeStore(id, contentHtml);
    }

    private static List<String> report(LegacyFlexSpanMergeRunner.Store store, boolean apply) {
        List<String> lines = new ArrayList<>();
        LegacyFlexSpanMergeRunner.scan(store, apply, lines::add);
        return lines;
    }

    // ---------- dry-run is the default ----------

    /** Default mode reports the article, the containers and the before/after length - and writes nothing. */
    @Test
    void dryRunReportsWithoutWriting() {
        FakeStore store = store(1L, SPLIT_ROW);

        LegacyFlexSpanMergeRunner.Report report = LegacyFlexSpanMergeRunner.scan(store, false, line -> {
        });

        assertThat(report.scanned()).isEqualTo(1);
        assertThat(report.affected()).isEqualTo(1);
        assertThat(report.mergedContainers()).isEqualTo(1);
        assertThat(report.spansBefore()).isEqualTo(2);
        assertThat(report.spansAfter()).isEqualTo(1);
        assertThat(report.applied()).isFalse();
        LegacyFlexSpanMergeRunner.ArticleChange change = report.changes().get(0);
        assertThat(change.beforeLength()).isEqualTo(SPLIT_ROW.length());
        assertThat(change.afterLength()).isEqualTo(MERGED_ROW.length());
        assertThat(change.written()).isFalse();
        // The stored body is still the split one.
        assertThat(store.bodies.get(1L)).isEqualTo(SPLIT_ROW);
    }

    /** Every affected article gets its own line; the unaffected ones cost nothing. */
    @Test
    void dryRunReportsEachAffectedArticle() {
        FakeStore store = new FakeStore(1L, "<p>no flex here</p>");
        store.add(2L, SPLIT_ROW + SPLIT_ROW);

        List<String> lines = report(store, false);

        assertThat(lines).hasSize(2); // one article line + the totals line
        assertThat(lines.get(0)).contains("id=2").contains("dry-run 未写库");
        assertThat(lines.get(1)).contains("受影响 1 / 2 篇");
        assertThat(store.bodies.get(1L)).isEqualTo("<p>no flex here</p>");
    }

    /** Healthy stock: one totals line, no changes, nothing written. */
    @Test
    void nothingToMergeProducesASingleLine() {
        FakeStore store = store(7L, "<p>plain</p>");

        List<String> lines = report(store, false);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("受影响 0 / 1 篇");
    }

    // ---------- apply writes each article on its own ----------

    /** Apply writes the merged body back, once, and records the lengths around the write. */
    @Test
    void applyWritesTheMergedBodyBack() {
        FakeStore store = store(1L, SPLIT_ROW);

        List<String> lines = report(store, true);
        LegacyFlexSpanMergeRunner.Report report =
                LegacyFlexSpanMergeRunner.scan(store, true, lines::add);

        assertThat(report.applied()).isTrue();
        assertThat(report.affected()).isZero(); // second pass over the merged body: nothing left
        assertThat(store.bodies.get(1L)).isEqualTo(MERGED_ROW);
        // three lines from the applying pass: before-write lengths, written confirmation, totals
        assertThat(lines.subList(0, 3)).hasSize(3);
        assertThat(lines.get(0)).contains("写回前").contains("写回后");
        assertThat(lines.get(1)).contains("已写回");
        assertThat(lines.get(2)).contains("受影响 1 / 1 篇").contains("已写库");
    }

    /** An article that disappeared in the meantime is reported as not written, and does not abort. */
    @Test
    void applyReportsAnArticleThatVanished() {
        FakeStore store = store(1L, SPLIT_ROW);
        store.gone.add(1L);

        List<String> lines = report(store, true);

        assertThat(lines.get(1)).contains("未写回（文章已不存在）");
        assertThat(store.bodies.get(1L)).isEqualTo(SPLIT_ROW);
    }

    /** One failing article does not stop the others (one article is one write). */
    @Test
    void applyFailureOnOneArticleDoesNotStopTheRest() {
        FakeStore store = new FakeStore(1L, SPLIT_ROW);
        store.add(2L, SPLIT_ROW);
        store.failing.add(1L);

        LegacyFlexSpanMergeRunner.Report report = LegacyFlexSpanMergeRunner.scan(store, true, line -> {
        });

        assertThat(report.affected()).isEqualTo(2);
        assertThat(report.changes().get(0).written()).isFalse();
        assertThat(report.changes().get(1).written()).isTrue();
        assertThat(store.bodies.get(2L)).isEqualTo(MERGED_ROW);
    }

    /** Running the apply twice writes once; the second pass finds nothing to do. */
    @Test
    void applyIsIdempotent() {
        FakeStore store = store(1L, SPLIT_ROW);

        LegacyFlexSpanMergeRunner.scan(store, true, line -> {
        });
        String afterFirstRun = store.bodies.get(1L);
        LegacyFlexSpanMergeRunner.Report second = LegacyFlexSpanMergeRunner.scan(store, true, line -> {
        });

        assertThat(afterFirstRun).isEqualTo(MERGED_ROW);
        assertThat(store.bodies.get(1L)).isEqualTo(afterFirstRun);
        assertThat(second.affected()).isZero();
        assertThat(second.spansBefore()).isZero();
    }

    /** Rows with no body at all are skipped, not a crash. */
    @Test
    void nullAndEmptyBodiesAreSkipped() {
        FakeStore store = new FakeStore(1L, "");
        store.add(2L, null);

        List<String> lines = report(store, false);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("受影响 0 / 2 篇");
    }

    // ---------- the flag decides, the default never writes ----------

    /** Only {@code --apply} (or {@code --apply=<value other than false>}) turns reporting into writing. */
    @Test
    void applyFlagIsRequiredToWrite() {
        assertThat(LegacyFlexSpanMergeRunner.applyRequestedFromCli(new String[] {"--apply"})).isTrue();
        assertThat(LegacyFlexSpanMergeRunner.applyRequestedFromCli(
                new String[] {"something", "--apply=true"})).isTrue();
        assertThat(LegacyFlexSpanMergeRunner.applyRequestedFromCli(new String[] {"--apply=false"})).isFalse();
        assertThat(LegacyFlexSpanMergeRunner.applyRequestedFromCli(new String[] {"--dry-run"})).isFalse();
        assertThat(LegacyFlexSpanMergeRunner.applyRequestedFromCli(new String[0])).isFalse();
        assertThat(LegacyFlexSpanMergeRunner.applyRequestedFromCli(null)).isFalse();

        assertThat(LegacyFlexSpanMergeRunner.applyRequested(
                new DefaultApplicationArguments(new String[] {"--apply"}))).isTrue();
        assertThat(LegacyFlexSpanMergeRunner.applyRequested(
                new DefaultApplicationArguments(new String[] {}))).isFalse();
        assertThat(LegacyFlexSpanMergeRunner.applyRequested((org.springframework.boot.ApplicationArguments) null)).isFalse();
    }

    /** The Spring wiring without the flag: reads the candidates, writes nothing. */
    @Test
    void runWithoutTheFlagDoesNotWrite() {
        LegacyFlexSpanMapper mapper = mapper();
        when(mapper.markflowFlexCandidates()).thenReturn(
                List.of(new LegacyFlexSpanMergeRunner.Store.ArticleRow(3L, "标题", SPLIT_ROW)));

        new LegacyFlexSpanMergeRunner(mapper).run(new DefaultApplicationArguments(new String[] {}));

        verify(mapper).markflowFlexCandidates();
        verify(mapper, never()).writeMergedContentHtml(any(), anyString());
    }

    /** The Spring wiring with the flag: the merged body reaches the mapper. */
    @Test
    void runWithTheFlagWrites() {
        LegacyFlexSpanMapper mapper = mapper();
        when(mapper.markflowFlexCandidates()).thenReturn(
                List.of(new LegacyFlexSpanMergeRunner.Store.ArticleRow(3L, "标题", SPLIT_ROW)));
        when(mapper.writeMergedContentHtml(3L, MERGED_ROW)).thenReturn(1);

        new LegacyFlexSpanMergeRunner(mapper).run(new DefaultApplicationArguments(new String[] {"--apply"}));

        verify(mapper).markflowFlexCandidates();
        verify(mapper).writeMergedContentHtml(3L, MERGED_ROW);
    }

    private static LegacyFlexSpanMapper mapper() {
        return mock(LegacyFlexSpanMapper.class);
    }
}
