package ink.icoding.wechat.article.assistant.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 助手工具输出的**预算**：任何工具返回给模型的内容都必须有界。
 *
 * <p><b>为什么需要这个类（真实故障）</b>：加入 34 个模型档案后，用户在助手里问「当前有多少个模型」，
 * 工具 {@code list_llm_profiles} 把 47 条档案、每条约 315 字节全量返回——单次工具结果
 * <b>14.8 KB</b> 直接进上下文，随后上游以 {@code HTTP 200, Socket closed} 断流，整轮失败。
 * 那不是模型的问题，是这里没有预算：工具实现各自「把查到的东西全返回」，条数一多就把请求撑爆。
 *
 * <p>所以约束收在一处：list 类工具一律通过 {@link #page} 返回，条数有上限、且**必须**告诉模型
 * 「总共多少、只给了多少、怎么拿剩下的」。{@link #cap} 是最后一道兜底——即便将来有人漏用
 * {@code page}，序列化后的字符串也不会超过 {@link #MAX_CHARS}。
 *
 * <p>为什么上限取 20 条而不是「尽量多」：工具结果的价值在「让模型找到目标 id」，
 * 而不是「把库导出一份」。20 条足够翻到目标；真需要更多时，模型应该带上过滤条件再问一次——
 * 那一次请求也顺便变得更便宜、更准。
 */
final class AssistantToolBudget {

    /** list 工具默认返回条数。 */
    static final int DEFAULT_LIMIT = 20;
    /** 单次调用允许请求的最大条数（模型可以显式要更多，但仍有上限）。 */
    static final int MAX_LIMIT = 100;
    /** 单个工具结果的字符硬上限（兜底，正常路径由 {@link #page} 保证远小于它）。 */
    static final int MAX_CHARS = 6000;

    private AssistantToolBudget() {
    }

    /**
     * 组装分页结果。
     *
     * <p>返回体里**始终**带 {@code total}：只说「只有 20 条」而不说总数，模型会以为库里就这么多，
     * 于是回答「你只有 20 个模型」——这类自信的错误答案比报错更难发现。
     *
     * @param all            查询到的全部行（按业务需要的顺序）
     * @param requestedLimit 模型请求的条数，null 用默认
     * @param what           被截断对象的称呼，用于提示语（如「模型档案」）
     */
    static Map<String, Object> page(List<Map<String, Object>> all, Integer requestedLimit, String what) {
        int limit = clampLimit(requestedLimit);
        int total = all.size();
        List<Map<String, Object>> items = total <= limit ? all : new ArrayList<>(all.subList(0, limit));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("returned", items.size());
        payload.put("items", items);
        if (total > limit) {
            // 提示必须可执行：告诉模型下一步怎么做，而不是只说「被截断了」
            payload.put("truncationHint", "共 " + total + " 个" + what + "，本次只返回前 " + limit
                    + " 个。请用过滤条件缩小范围，或把 limit 提到最多 " + MAX_LIMIT
                    + "；需要时也可以再问一次并指定不同的过滤条件。");
        }
        return payload;
    }

    /** 归一化请求条数：null/越界都收进 [1, {@link #MAX_LIMIT}]。 */
    static int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null) return DEFAULT_LIMIT;
        return Math.min(MAX_LIMIT, Math.max(1, requestedLimit));
    }

    /**
     * 字符串兜底截断：超过 {@link #MAX_CHARS} 时**保留前面一段并注明**，而不是静默丢数据。
     *
     * <p>为什么注明的是「请缩小范围」而不是「已截断」：模型看到「已截断」只会重试同样的调用，
     * 看到「请缩小范围」才知道要换参数。
     */
    static String cap(String json) {
        if (json == null || json.length() <= MAX_CHARS) return json;
        String head = json.substring(0, MAX_CHARS);
        return head + "\n…（结果过长已截断：原始 " + json.length() + " 字符，上限 " + MAX_CHARS
                + "。请用过滤条件或更小的 limit 缩小范围后重试。）";
    }
}