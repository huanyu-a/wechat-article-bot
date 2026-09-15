package ink.icoding.wechat.article.schedule;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具调用治理（Phase 4）：**只读工具去重 + 循环检测 + 预算提示**。
 *
 * <p>三件事都作用在「工具结果回给模型之前」这一步，因此集中在一个类里，语义只有一处定义。
 *
 * <h2>为什么需要它</h2>
 * {@code ToolMutationDeduplicator} 只覆盖**有副作用**的工具（生图/导入/改图），因为重复执行它们
 * 会重复计费；而实测里烧掉预算的恰恰是**只读检索**的重复调用：
 * <ul>
 *   <li>run#46/#62/#85/#89 都是「同一工具、同一参数、反复调用」的形态，模型把 40 次预算一路烧完；</li>
 *   <li>已知记录：模型读到「已中止」后仍连调 7 次工具——**提示必须给出可执行的下一步**，
 *       一句干巴巴的错误它读不懂。</li>
 * </ul>
 *
 * <h2>与预算的关系</h2>
 * 去重**仍然计入预算**（{@link AgentInvoker} 在调用前就已计数）：保住硬上限这条护栏，
 * 只是省掉一次真实的网络往返与上游延迟。若去重不计入预算，模型就能无限重复同一检索，
 * 「跑不出可预期范围」这条护栏就失效了。
 */
public final class ToolCallGovernor {
    /**
     * 可去重的只读工具：同一参数在本次会话内重复调用时直接返回首次结果。
     *
     * <p>只列**幂等且无副作用**的检索类工具。{@code read_article_draft} 有意不在此列——
     * 它的返回值随草稿变化（写工具执行后必须重新读到新内容），缓存它会让模型基于过期状态决策。
     */
    public static final Set<String> READ_ONLY_TOOLS = Set.of(
            "search_web", "browse_webpage", "search_web_images", "list_image_assets");

    /**
     * 同参数重复次数的提示阈值：达到即注入收手指令（结果本身仍然返回）。
     *
     * <p>2 的依据：第一次是正常检索，第二次可能是换了个措辞但参数完全相同（模型没意识到），
     * 第三次及以后就纯粹是空转。取 2 意味着「第 3 次同参数调用」触发提示。
     */
    public static final int REPEAT_HINT_THRESHOLD = 2;

    /**
     * 连续无进展的**中止**阈值：同一参数重复到该次数时直接判本次会话失败。
     *
     * <p>6 的依据：提示阈值（2）之后模型仍有 4 次机会自行纠正——实测模型确实会在收到
     * 明确指令后收手（{@code AgentProtocols.RESEARCH} 的预算提示就是这么起作用的）。
     * 到 6 次还在重复同一参数，说明它已经进入循环，继续放行只是烧钱。
     */
    public static final int NO_PROGRESS_ABORT_THRESHOLD = 6;

    /** 预算提示阈值：剩余额度不超过该值时，每个工具结果追加剩余次数。 */
    public static final int BUDGET_HINT_REMAINING = 5;

    /** 首次结果缓存（键 = 工具名 + 规范化参数）。 */
    private final Map<String, String> readCache = new ConcurrentHashMap<>();
    /** 每个「工具名 + 参数」被调用的次数（含首次）。 */
    private final Map<String, Integer> repeatCounts = new ConcurrentHashMap<>();
    /**
     * 本次会话的工具调用上限与已用数（由 {@link AgentInvoker} 在每次尝试开始时设定）。
     *
     * <p>为什么要由运行器喂：预算提示必须出现在**工具结果**里（模型每次调用都能看到），
     * 而 agent4j 的 {@code AgentResultHandler} 没有「工具结果」回调——只有 PREPARING/CALLING/COMPLETED
     * 三个状态。因此提示只能由工具自己追加，而剩余额度只有运行器知道。
     * 运行器在每次尝试开始时调 {@link #beginSession(int)}，并在每次计入调用时调 {@link #countCall()}。
     */
    private volatile int budgetLimit;
    private final AtomicInteger budgetUsed = new AtomicInteger();

    /**
     * 去重：同参数重复调用只读工具时返回首次结果。
     *
     * @return 缓存命中时返回「首次结果 + 缓存标记」，否则返回 null（调用方照常执行）
     */
    public String cachedResult(String toolName, String paramJson) {
        if (!isReadOnly(toolName)) return null;
        String first = readCache.get(key(toolName, paramJson));
        if (first == null) return null;
        return first + "\n\n{\"cached\": true, \"note\": \"同参数结果已在本次运行中获取过，未重复请求上游\"}";
    }

    /** 记录一次真实执行的结果（供后续同参数调用复用）。 */
    public void remember(String toolName, String paramJson, String result) {
        if (!isReadOnly(toolName) || result == null) return;
        readCache.putIfAbsent(key(toolName, paramJson), result);
    }

    /**
     * 只读检索的执行包装（供 {@code ArticleMediaTools.ReadExecutor} 使用）：
     * 缓存命中直接返回首次结果，否则执行并记住结果，两种情况都按需追加提示。
     *
     * <p><b>计数不在这里做</b>：调用计数由 {@link AgentInvoker} 在 CALLING 回调里统一记
     * （那里是每次工具调用的必经之处，且持有参数原文）。本方法只负责「不重复请求上游」与「追加提示」。
     * 两边都计数会让缓存命中被算两次，循环检测的阈值随之失真。
     */
    public String execute(String toolName, String paramJson, java.util.function.Supplier<String> action) {
        String cached = cachedResult(toolName, paramJson);
        String base;
        if (cached != null) {
            // 缓存命中不重复请求上游（省一次网络往返与上游延迟）；重复本身仍由运行器计数，
            // 因此「反正有缓存」刷不出无限调用。
            base = cached;
        } else {
            base = action.get();
            remember(toolName, paramJson, base);
        }
        return withNote(withNote(base, repeatHintFor(toolName, paramJson)), budgetHint(remainingBudget()));
    }

    /**
     * 记录一次调用（由 {@link AgentInvoker} 在每次工具调用时调用）。
     *
     * @return 是否已陷入无进展循环（调用方据此中止会话）
     */
    public boolean noteCall(String toolName, String paramJson) {
        String composite = key(toolName, paramJson);
        int count = repeatCounts.merge(composite, 1, Integer::sum);
        return isReadOnly(toolName) && count >= NO_PROGRESS_ABORT_THRESHOLD;
    }

    /** 该「工具 + 参数」当前的重复提示（未超阈值或非只读工具返回 null）。 */
    public String repeatHintFor(String toolName, String paramJson) {
        if (!isReadOnly(toolName)) return null;
        Integer count = repeatCounts.get(key(toolName, paramJson));
        return repeatHint(toolName, count == null ? 0 : count);
    }

    /** 该「工具 + 参数」当前的重复次数。 */
    public int repeatCount(String toolName, String paramJson) {
        Integer count = repeatCounts.get(key(toolName, paramJson));
        return count == null ? 0 : count;
    }

    /** 拼接提示（无提示时原样返回）。 */
    private static String withNote(String result, String note) {
        if (note == null || note.isBlank()) return result;
        return (result == null ? "" : result) + "\n\n" + note;
    }

    /** 重复次数的提示文本（仅在超过阈值时非空）。 */
    private String repeatHint(String toolName, String paramJson) {
        Integer count = repeatCounts.get(key(toolName, paramJson));
        return repeatHint(toolName, count == null ? 0 : count);
    }

    private static String repeatHint(String toolName, int count) {
        if (count <= REPEAT_HINT_THRESHOLD) return null;
        return "你已用**完全相同的参数**调用 " + toolName + " " + count + " 次，结果不会改变。"
                + "继续重复只会消耗预算而不会带来新信息：请立即改用收尾工具提交已有成果"
                + "（save_research_notes / save_article_draft / submit_review），"
                + "或换一个不同的关键词/来源再试一次。";
    }

    /**
     * 是否已陷入无进展循环（同一参数重复次数达到中止阈值）。
     *
     * @return 触发中止时返回可执行的说明，否则 null
     */
    public String noProgressReason(String toolName, String paramJson) {
        if (!isReadOnly(toolName)) return null;
        int seen = repeatCount(toolName, paramJson);
        if (seen < NO_PROGRESS_ABORT_THRESHOLD) return null;
        return noProgressMessage(toolName, seen);
    }

    /** 无进展中止的可执行说明（直接作为工具失败原因回给模型）。 */
    public static String noProgressMessage(String toolName, int count) {
        return "会话已陷入循环：以完全相同的参数调用 " + toolName + " " + count + " 次且结果不变，"
                + "继续下去不会产生新信息。本次会话中止，已有成果请在下次运行中提交。";
    }

    /**
     * 开始一次会话尝试：设定本次的预算上限并清零已用数。
     *
     * <p>每次尝试都重置：{@link AgentInvoker} 的重试会重建会话，上一轮的用量不该延续到新会话
     * （否则重试一开始就显示「剩余 0 次」）。
     */
    public void beginSession(int maxToolCalls) {
        this.budgetLimit = maxToolCalls;
        budgetUsed.set(0);
    }

    /** 运行器在每次计入工具调用时调用（与运行器的计数保持同步）。 */
    public void countCall() {
        budgetUsed.incrementAndGet();
    }

    /** 当前剩余额度；未设上限（&le;0）时返回 {@link Integer#MAX_VALUE}（永不提示）。 */
    public int remainingBudget() {
        if (budgetLimit <= 0) return Integer.MAX_VALUE;
        return budgetLimit - budgetUsed.get();
    }

    /**
     * 预算提示：剩余额度不多时追加一句，让模型自己安排收尾，而不是等事后被拒绝。
     *
     * <p>实测依据：{@code AgentProtocols.RESEARCH} 里事先告知预算后，调研阶段不再「先搜满再交简报」，
     * 而是检索到足够支撑交叉验证就提交。把这条信息放到**工具结果里**（每次都能看到）比只写在系统提示里更有效。
     *
     * @param remaining 剩余额度（&le;0 表示已用尽，不提示——此时收尾工具之外都会被拒）
     */
    public static String budgetHint(int remaining) {
        if (remaining <= 0 || remaining > BUDGET_HINT_REMAINING) return null;
        return "本次会话剩余工具调用额度：" + remaining + " 次，请优先提交成果。";
    }

    /** 是否可去重（只读且参数幂等）。 */
    public static boolean isReadOnly(String toolName) {
        return toolName != null && READ_ONLY_TOOLS.contains(toolName);
    }

    private static String key(String toolName, String paramJson) {
        return toolName + "\n" + normalize(paramJson);
    }

    /**
     * 参数规范化：忽略空白与**字段顺序**差异。
     *
     * <p>为什么要排序：{@code {"keyword":"AI","count":5}} 与 {@code {"count":5,"keyword":"AI"}} 是同一个
     * 检索请求，模型完全可能换个字段顺序再发一次。不排序的话两次会被当成不同参数，
     * 去重与循环检测双双失效——而这两个机制的价值恰恰在于识别「换个写法、其实一样」的重复。
     */
    static String normalize(String value) {
        if (value == null || value.isBlank()) return "{}";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(sortKeys(mapper.readTree(value)));
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    /** 递归按字段名排序（只对对象排序，数组顺序有意义故保持原样）。 */
    private static com.fasterxml.jackson.databind.JsonNode sortKeys(com.fasterxml.jackson.databind.JsonNode node) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            com.fasterxml.jackson.databind.node.ObjectNode sorted =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            java.util.List<String> names = new java.util.ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            java.util.Collections.sort(names);
            for (String name : names) sorted.set(name, sortKeys(object.get(name)));
            return sorted;
        }
        if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode array) {
            com.fasterxml.jackson.databind.node.ArrayNode copy =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            array.forEach(item -> copy.add(sortKeys(item)));
            return copy;
        }
        return node;
    }
}
