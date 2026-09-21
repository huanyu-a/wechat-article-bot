package ink.icoding.wechat.article.article;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Heals MARKFLOW articles that were saved in the "split flex columns" form produced by the old
 * editor code (known-issues-handoff.md D48 / D49).
 *
 * <h2>The defect shape</h2>
 * A MarkFlow list row is one flex row:
 *
 * <pre>{@code
 * <section style="display:flex;align-items:flex-start">
 *   <span style="...;flex-shrink:0"></span>            <- the bullet dot, NOT a flex item
 *   <span style="flex:1"><strong>时间</strong>：每天 …</span>   <- one single text column
 * </section>}
 * </pre>
 *
 * The old editor split that single {@code flex:1} span into several sibling spans that each carry a
 * copy of it, because the serializer closes and re-opens the span whenever a textStyle mark's
 * attributes change (see D49 for the mechanism). The stored form then looks like
 * {@code <span style="flex:1 1 0%">A</span><span style="flex:1 1 0%">B</span>} and the row renders
 * as N columns instead of one. A real sample captured from the editor exit of article 8 is kept at
 * {@code target/probe/browser/r39_article8_editor-exit.html}.
 *
 * <h2>The rule</h2>
 * Inside one flex container, a run of <b>adjacent</b> top-level {@code <span>} siblings whose style
 * declares {@code flex:1} is replaced by a single span that carries the declarations the run shares
 * (that is the flex style the container works with); every sibling's own children keep their place
 * and a sibling that carries <b>extra</b> declarations keeps them on a nested span, so inner inline
 * styles stay nested instead of being flattened away.
 *
 * <pre>{@code
 * before: <span style="flex:1 1 0%"><strong>时间</strong></span><span style="flex:1 1 0%">：每天 …</span>
 * after:  <span style="flex:1 1 0%"><strong>时间</strong>：每天 …</span>}
 * </pre>
 *
 * Anything else is left completely alone - in particular:
 * <ul>
 *   <li>a single {@code flex:1} child (nothing to heal, and it may be an intentional column);</li>
 *   <li>siblings that only share <b>nothing</b> - they are not copies of one visual box;</li>
 *   <li>{@code <section>} / {@code <p>} children carrying {@code flex:1} - MarkFlow uses sections for
 *       real multi-column components (a metrics row), so merging them would destroy the layout;</li>
 *   <li>the bullet dot (its style declares {@code flex-shrink:0}, not {@code flex});</li>
 *   <li>the D35 badge guard sample: three adjacent same-style {@code display:inline-flex} spans that
 *       are NOT children of a flex container stay three independent boxes.</li>
 * </ul>
 *
 * When nothing is merged the caller's string is returned untouched (no Jsoup round trip), so an
 * article that is already healthy is written back byte-identical - which is what makes this normalizer
 * idempotent.
 *
 * <p>Only {@code display:flex} containers are handled, not {@code display:inline-flex}: the recorded
 * defect always sits in a {@code display:flex} row, and an inline-flex box with several {@code flex:1}
 * children is much more likely to be an intentional layout.
 */
public final class MarkflowFlexSpanNormalizer {

    private MarkflowFlexSpanNormalizer() {
    }

    /**
     * Outcome of one normalization pass.
     *
     * @param html             the rewritten body HTML, or {@code null} when {@code changed} is false
     *                         (meaning "keep the input string as it was")
     * @param changed          whether at least one flex row was merged
     * @param mergedContainers how many flex containers received a merge
     * @param spansBefore      top-level {@code flex:1} spans before the pass (merged runs only)
     * @param spansAfter       top-level {@code flex:1} spans after the pass (one per merged run)
     */
    public record Result(String html, boolean changed, int mergedContainers, int spansBefore, int spansAfter) {

        /** Returned whenever the document needs no healing; {@link #html()} is {@code null}. */
        public static final Result UNCHANGED = new Result(null, false, 0, 0, 0);
    }

    /**
     * @return the healed HTML, or the input string unchanged when there is nothing to heal.
     *         {@code null} in yields {@code null} out.
     */
    public static String normalize(String html) {
        Result result = normalizeWithStats(html);
        return result.changed() ? result.html() : html;
    }

    /** Whether the HTML still contains at least one mergeable split flex row. */
    public static boolean hasSplitFlexSpans(String html) {
        return normalizeWithStats(html).changed();
    }

    /**
     * Heals the HTML and reports what happened (used by the runner for its dry-run statistics).
     *
     * <p>Never throws for hostile input: {@code null}, empty and malformed HTML are handled - a
     * parse failure or a style value that cannot be understood makes this pass a no-op that keeps
     * the caller's string.
     */
    public static Result normalizeWithStats(String html) {
        if (html == null || html.isEmpty()) return Result.UNCHANGED;
        Document document;
        try {
            document = Jsoup.parseBodyFragment(html);
        } catch (RuntimeException parseFailure) {
            // Jsoup is lenient and does not normally throw; if it ever does, treat the document as
            // "not ours" and leave it alone instead of failing a whole migration run.
            return Result.UNCHANGED;
        }
        document.outputSettings().prettyPrint(false);

        int mergedContainers = 0;
        int spansBefore = 0;
        int spansAfter = 0;
        for (Element container : flexContainers(document)) {
            boolean mergedInContainer = false;
            for (List<Element> run : mergeableRuns(container)) {
                if (!mergeRun(run)) continue;
                mergedInContainer = true;
                spansBefore += run.size();
                spansAfter += 1;
            }
            if (mergedInContainer) mergedContainers++;
        }
        if (mergedContainers == 0) return Result.UNCHANGED;
        return new Result(document.body().html(), true, mergedContainers, spansBefore, spansAfter);
    }

    // ---------- scanning ----------

    /** Every element that lays its children out as flex items, in document order. */
    private static List<Element> flexContainers(Document document) {
        List<Element> containers = new ArrayList<>();
        for (Element element : document.getAllElements()) {
            if (isFlexContainer(element)) containers.add(element);
        }
        return containers;
    }

    private static boolean isFlexContainer(Element element) {
        return "flex".equals(displayValue(element));
    }

    /** The first token of the {@code display} declaration, or {@code null} when absent. */
    private static String displayValue(Element element) {
        String value = null;
        for (Declaration declaration : declarations(element)) {
            if (declaration.name().equals("display")) value = declaration.value();
        }
        return value == null ? null : firstToken(value);
    }

    /**
     * Runs of adjacent top-level {@code <span>} siblings that each declare {@code flex:1}.
     *
     * <p>Adjacency stops at any element that is not such a span and at any text node that is not
     * blank: a real text between two boxes would render as its own anonymous flex item, so moving it
     * inside the merged span would change the layout. Blank text runs between flex items are ignored
     * (the flexbox spec does not render them), so a pretty-printed row still heals.
     */
    private static List<List<Element>> mergeableRuns(Element container) {
        List<List<Element>> runs = new ArrayList<>();
        List<Node> children = container.childNodes();
        int index = 0;
        while (index < children.size()) {
            if (!isFlexOneSpan(children.get(index))) {
                index++;
                continue;
            }
            List<Element> run = new ArrayList<>();
            run.add((Element) children.get(index));
            index++;
            while (index < children.size()) {
                Node next = children.get(index);
                if (next instanceof TextNode textNode && textNode.text().isBlank()) {
                    index++;
                    continue;
                }
                if (isFlexOneSpan(next)) {
                    run.add((Element) next);
                    index++;
                    continue;
                }
                break;
            }
            if (run.size() >= 2) runs.add(run);
        }
        return runs;
    }

    private static boolean isFlexOneSpan(Node node) {
        return node instanceof Element element
                && element.normalName().equals("span")
                && isFlexOne(element);
    }

    /**
     * Whether the element's own {@code flex} declaration makes it a single flexible column
     * ({@code flex:1}, {@code flex:1 1 0%}, {@code flex: 1 0%} ...). The last declaration wins, as
     * in CSS, and only the property named exactly {@code flex} counts - {@code flex-shrink:0} on a
     * bullet dot is not a flex item.
     */
    private static boolean isFlexOne(Element element) {
        String flex = null;
        for (Declaration declaration : declarations(element)) {
            if (declaration.name().equals("flex")) flex = declaration.value();
        }
        return flex != null && "1".equals(firstToken(flex));
    }

    // ---------- merging ----------

    /**
     * Replaces one run of sibling spans with a single span.
     *
     * @return {@code false} when the run has no declaration in common (there is no shared flex
     *         style to keep), which leaves the document untouched.
     */
    private static boolean mergeRun(List<Element> run) {
        List<Declaration> shared = sharedDeclarations(run);
        if (shared.isEmpty()) return false;

        Element first = run.get(0);
        Element merged = first.ownerDocument().createElement(first.normalName());
        // Non-style attributes come from the first sibling: every copy of a split span carries the
        // same ones (the split only ever diverges in style), so taking the first is exact in the
        // real defect shape and merely conservative otherwise.
        first.attributes().forEach(attribute -> {
            if (!attribute.getKey().equalsIgnoreCase("style")) merged.attr(attribute.getKey(), attribute.getValue());
        });
        merged.attr("style", styleText(shared));

        for (Element sibling : run) {
            List<Declaration> extras = extraDeclarations(sibling, shared);
            List<Node> children = sibling.childNodesCopy();
            if (extras.isEmpty()) {
                merged.appendChildren(children);
            } else {
                Element inner = sibling.ownerDocument().createElement(sibling.normalName());
                inner.attr("style", styleText(extras));
                inner.appendChildren(children);
                merged.appendChild(inner);
            }
        }

        first.before(merged);
        for (Element sibling : run) {
            sibling.remove();
        }
        return true;
    }

    /** Declarations the whole run agrees on, in the order the first sibling writes them. */
    private static List<Declaration> sharedDeclarations(List<Element> run) {
        List<Declaration> shared = new ArrayList<>();
        for (Declaration candidate : declarations(run.get(0))) {
            boolean agreedByAll = true;
            for (int index = 1; index < run.size(); index++) {
                if (!declaresEquivalent(declarations(run.get(index)), candidate)) {
                    agreedByAll = false;
                    break;
                }
            }
            if (agreedByAll) shared.add(candidate);
        }
        return shared;
    }

    private static boolean declaresEquivalent(List<Declaration> declarations, Declaration candidate) {
        for (Declaration declaration : declarations) {
            if (declaration.name().equals(candidate.name())
                    && declaration.canonicalValue().equals(candidate.canonicalValue())) {
                return true;
            }
        }
        return false;
    }

    /** Declarations of a sibling that are not part of the shared (outer) style. */
    private static List<Declaration> extraDeclarations(Element sibling, List<Declaration> shared) {
        List<Declaration> extras = new ArrayList<>();
        for (Declaration declaration : declarations(sibling)) {
            boolean covered = false;
            for (Declaration keep : shared) {
                if (keep.name().equals(declaration.name())) {
                    covered = true;
                    break;
                }
            }
            if (!covered) extras.add(declaration);
        }
        return extras;
    }

    private static String styleText(List<Declaration> declarations) {
        StringBuilder builder = new StringBuilder();
        for (Declaration declaration : declarations) {
            if (builder.length() > 0) builder.append(';');
            builder.append(declaration.text());
        }
        return builder.toString();
    }

    // ---------- style parsing ----------

    /**
     * One CSS declaration.
     *
     * @param name           lower-cased property name
     * @param value          trimmed property value as written
     * @param text           the declaration as written (used verbatim when the style is rebuilt)
     * @param canonicalValue value used for equality: internal whitespace collapsed, and the
     *                       {@code flex} shorthand reduced to its first token because
     *                       {@code flex:1} and {@code flex:1 1 0%} are the same rule
     */
    private record Declaration(String name, String value, String text, String canonicalValue) {
    }
    private static List<Declaration> declarations(Element element) {
        String style = element.attr("style");
        if (style == null || style.isBlank()) return List.of();
        List<Declaration> result = new ArrayList<>();
        for (String raw : splitDeclarations(style)) {
            String text = raw.trim();
            if (text.isEmpty()) continue;
            int colon = text.indexOf(':');
            if (colon <= 0 || colon == text.length() - 1) continue;
            String name = text.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = text.substring(colon + 1).trim();
            if (name.isEmpty() || value.isEmpty()) continue;
            result.add(new Declaration(name, value, text, canonicalValue(name, value)));
        }
        return result;
    }

    /**
     * Splits a style value on {@code ;} but not inside parentheses or quotes, so a
     * {@code url(data:image/png;base64,…)} declaration is never cut in half.
     */
    private static List<String> splitDeclarations(String style) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int index = 0; index < style.length(); index++) {
            char character = style.charAt(index);
            if (quote != 0) {
                current.append(character);
                if (character == quote) quote = 0;
                continue;
            }
            if (character == '"' || character == '\'') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')' && depth > 0) {
                depth--;
            }
            if (character == ';' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        parts.add(current.toString());
        return parts;
    }

    private static String canonicalValue(String name, String value) {
        String collapsed = value.trim().replaceAll("\\s+", " ");
        return name.equals("flex") ? firstToken(collapsed) : collapsed;
    }

    private static String firstToken(String value) {
        String trimmed = value.trim();
        int cut = 0;
        while (cut < trimmed.length() && !Character.isWhitespace(trimmed.charAt(cut))) {
            cut++;
        }
        return trimmed.substring(0, cut);
    }
}
