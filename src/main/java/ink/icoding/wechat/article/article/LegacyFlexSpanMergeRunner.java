package ink.icoding.wechat.article.article;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Heals MARKFLOW articles that were stored in the "split flex columns" form (D48/D49).
 *
 * <h2>What the defect was, and what is left of it</h2>
 * The editor used to serialize one {@code flex:1} span as several sibling spans that each carry a
 * copy of it, so a list row rendered as N columns instead of one
 * (known-issues-handoff.md D48/D49). The editor side is fixed: {@code mergeNestedSpanStyles:false}
 * plus {@code MarkflowInnerSpanStyle} in {@code webui/src/editorExtensions.js}. What is left is the
 * content that was already written that way - reopening such an article still shows the extra
 * columns, because the split spans are top-level siblings and the new rule only claims spans
 * nested inside a styled span.
 *
 * <h2>What this runner does</h2>
 * {@link MarkflowFlexSpanNormalizer} rewrites the body, this runner feeds it every live MARKFLOW
 * article and reports what would change:
 *
 * <ul>
 *   <li><b>dry-run is the default</b>: it only reports the affected article count, the merged
 *       container count and the before/after body length of each affected article. Nothing is
 *       written.</li>
 *   <li><b>{@code --apply} writes</b>: each affected article is written back on its own, the
 *       before/after lengths are recorded around the write.</li>
 * </ul>
 *
 * <h2>Why it runs on every start</h2>
 * The dry-run is read-only and answers the question "is there legacy stock left?" on every boot,
 * which is the only place a normal deployment can see it. Writing requires the explicit flag, so
 * the default path can never change stored content - the same contract as
 * {@link ink.icoding.wechat.article.agent.LlmProfileMigrationRunner} (warn and continue, never
 * block the boot).
 *
 * <h2>Why there is also a {@code main}</h2>
 * So the dry-run and the apply can be executed against a library without booting the web tier:
 * {@code java -cp <deps> ink.icoding.wechat.article.article.LegacyFlexSpanMergeRunner [--apply]}.
 * Starting the whole application to change stored content would also run the schema sync, the
 * seeders and Quartz first - side effects a content migration has no business triggering. The
 * standalone path therefore talks to the database directly through
 * {@link LegacyFlexSpanJdbcStore} and shares every decision with the Spring path through
 * {@link #scan(Store, boolean, Consumer)}.
 */
@Component
@Order(41)
public class LegacyFlexSpanMergeRunner implements ApplicationRunner {

    /** The only flag that turns the default dry-run into a write: {@code --apply}. */
    public static final String APPLY_OPTION = "apply";

    private static final Logger log = LoggerFactory.getLogger(LegacyFlexSpanMergeRunner.class);

    private final LegacyFlexSpanMapper mapper;

    public LegacyFlexSpanMergeRunner(LegacyFlexSpanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean apply = applyRequested(args);
        try {
            Report report = scan(mapper, apply, message -> log.info("[存量flex拆分合并] {}", message));
            log.info("[存量flex拆分合并] 完成（{}）：MARKFLOW 稿件扫描 {} 篇，受影响 {} 篇、{} 个容器，"
                            + "flex:1 顶级 span {} → {} 个",
                    apply ? "已写库" : "dry-run", report.scanned(), report.affected(),
                    report.mergedContainers(), report.spansBefore(), report.spansAfter());
            if (!apply && report.affected() > 0) {
                log.warn("[存量flex拆分合并] 有 {} 篇存量稿件仍是拆分形态但未被改动；确认无误后加 --{} 写回",
                        report.affected(), APPLY_OPTION);
            }
        } catch (Exception exception) {
            log.warn("存量 flex 拆分合并失败（跳过，不影响启动）：{}", exception.getMessage(), exception);
        }
    }

    /** Standalone entry point for the dry-run and for {@code --apply}; see the class javadoc. */
    public static void main(String[] args) {
        boolean apply = applyRequestedFromCli(args);
        try {
            scan(LegacyFlexSpanJdbcStore.fromEnvironment(), apply, System.out::println);
        } catch (Exception exception) {
            System.err.println("存量 flex 拆分合并失败：" + exception.getMessage());
            exception.printStackTrace();
            System.exit(1);
        }
    }

    // ---------- core loop (shared by the Spring path and the standalone entry) ----------

    /**
     * Scans MARKFLOW articles and, when {@code apply} is set, writes the merged bodies back.
     *
     * @param store    where the rows come from and where the writes go
     * @param apply    {@code false} = report only, never write
     * @param reporter receives one line per affected article plus the totals
     */
    public static Report scan(Store store, boolean apply, Consumer<String> reporter) {
        List<Store.ArticleRow> candidates = store.markflowFlexCandidates();
        List<ArticleChange> changes = new ArrayList<>();
        int mergedContainers = 0;
        int spansBefore = 0;
        int spansAfter = 0;

        for (Store.ArticleRow row : candidates) {
            MarkflowFlexSpanNormalizer.Result result = MarkflowFlexSpanNormalizer.normalizeWithStats(row.contentHtml());
            if (!result.changed()) continue;
            mergedContainers += result.mergedContainers();
            spansBefore += result.spansBefore();
            spansAfter += result.spansAfter();

            boolean written = false;
            if (apply) {
                reporter.accept(String.format(
                        "稿件 id=%d《%s》：写回前 %d 字符 → 写回后 %d 字符（%d 个容器、flex:1 顶级 span %d → 1）",
                        row.id(), titleOf(row), length(row.contentHtml()), result.html().length(),
                        result.mergedContainers(), result.spansBefore()));
                try {
                    written = store.writeMergedContentHtml(row.id(), result.html()) > 0;
                    reporter.accept(String.format("稿件 id=%d：%s", row.id(),
                            written ? "已写回" : "未写回（文章已不存在）"));
                } catch (Exception writeFailure) {
                    // One article is one write statement, so this only loses this article.
                    reporter.accept(String.format("稿件 id=%d：写回失败（%s），其余文章继续",
                            row.id(), writeFailure.getMessage()));
                }
            } else {
                reporter.accept(String.format(
                        "稿件 id=%d《%s》：待合并 %d 个容器、flex:1 顶级 span %d → 1，正文 %d → %d 字符（dry-run 未写库）",
                        row.id(), titleOf(row), result.mergedContainers(), result.spansBefore(),
                        length(row.contentHtml()), result.html().length()));
            }
            changes.add(new ArticleChange(row.id(), titleOf(row), length(row.contentHtml()),
                    result.html().length(), result.mergedContainers(), result.spansBefore(),
                    result.spansAfter(), written));
        }

        reporter.accept(String.format(
                "受影响 %d / %d 篇，合并 %d 个容器，flex:1 顶级 span %d → %d 个%s",
                changes.size(), candidates.size(), mergedContainers, spansBefore, spansAfter,
                apply ? "，已写库" : "（dry-run，未写库）"));
        return new Report(candidates.size(), changes.size(), mergedContainers, spansBefore, spansAfter,
                apply, changes);
    }

    /**
     * Where one merge run reads its rows and writes its results.
     *
     * <p>Implemented by {@link LegacyFlexSpanMapper} (Spring / mybatis) and by
     * {@link LegacyFlexSpanJdbcStore} (standalone {@code main}). Both share the same
     * {@link #scan(Store, boolean, Consumer)} loop, so a dry-run executed
     * either way decides exactly what an apply would do.
     */
    public interface Store {

        /** One candidate row: just what the merge needs. */
        record ArticleRow(Long id, String title, String contentHtml) {
        }

        /** Live MARKFLOW articles - the only population where the split form can appear. */
        List<ArticleRow> markflowFlexCandidates();

        /** Writes the merged body of one article; returns the number of rows affected. */
        int writeMergedContentHtml(Long id, String contentHtml);
    }

    /** One article this run changed (or, in dry-run, would change). */
    public record ArticleChange(Long id, String title, int beforeLength, int afterLength,
                                int mergedContainers, int spansBefore, int spansAfter, boolean written) {
    }

    /** The whole run's numbers; {@link #affected()} is the count of articles with split rows. */
    public record Report(int scanned, int affected, int mergedContainers, int spansBefore, int spansAfter,
                         boolean applied, List<ArticleChange> changes) {
    }

    // ---------- helpers ----------

    static boolean applyRequested(ApplicationArguments args) {
        if (args == null || !args.containsOption(APPLY_OPTION)) return false;
        List<String> values = args.getOptionValues(APPLY_OPTION);
        // `--apply` (no value) and `--apply=<anything but false>` both mean "write".
        return values == null || values.isEmpty() || !"false".equalsIgnoreCase(values.get(0).trim());
    }

    static boolean applyRequestedFromCli(String[] args) {
        if (args == null) return false;
        String flag = "--" + APPLY_OPTION + "=";
        for (String arg : args) {
            if (arg == null) continue;
            String trimmed = arg.trim();
            if (trimmed.equals("--" + APPLY_OPTION) || trimmed.equals("-" + APPLY_OPTION)) return true;
            if (trimmed.startsWith(flag)) {
                return !"false".equalsIgnoreCase(trimmed.substring(flag.length()).trim());
            }
        }
        return false;
    }

    private static String titleOf(Store.ArticleRow row) {
        return row.title() == null || row.title().isBlank() ? "(无标题)" : row.title();
    }

    private static int length(String html) {
        return html == null ? 0 : html.length();
    }
}
