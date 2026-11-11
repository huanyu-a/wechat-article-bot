package ink.icoding.wechat.article.assistant;

import ink.icoding.wechat.article.agent.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 全局助手的角色裁剪策略（docs/dev/global-assistant-plan.md §4.3 / §5.1）。
 *
 * <p>为什么授权在代码里而不是靠提示词：{@code agent_definition.tool_keys} 是**可编辑数据**，
 * 而提示词只是「行为可预期」的软约束。真正的安全边界必须是「不在允许组里的工具实例根本不存在于
 * {@code AgentClient.tools} 里」——模型看不到也调不到。因此装配点取「定义声明的组 ∩ 角色允许的组」，
 * 收敛由本类承担，工具层只负责按组名过滤实例。
 *
 * <p>为什么未知角色 fail-closed（返回空集，而不是回落到全集）：角色名来自数据库/请求上下文，
 * 拼错一个字母（如 {@code ADMINN}）、写成小写之外的怪值、或将来新增未登记角色时，回落全集等于
 * **静默发放写权限**——一次录入错误就能让原本只读的账号改文章、改定时任务、改模型档案。
 * 宁可一个工具都不给（用户看到「没有可用工具」会来问），也不能让写权限在无人察觉时被放大。
 */
public final class AssistantRolePolicy {
    private AssistantRolePolicy() {
    }

    /** 只读：列文章/公众号/技能/智能体。 */
    private static final String READ = ToolRegistry.ASSISTANT_READ;
    /** 文章写：建文章与按内容块改写。 */
    private static final String ARTICLE_WRITE = ToolRegistry.ASSISTANT_ARTICLE_WRITE;
    /** 定时任务（不含删除）。 */
    private static final String TASK = ToolRegistry.ASSISTANT_TASK;
    /** 模型档案（不含密钥参数）。 */
    private static final String MODEL = ToolRegistry.ASSISTANT_MODEL;

    /**
     * 角色 → 允许的组键（规范化后的角色名为入参）。
     * 与 plan §4.3 的表逐行一致：ADMIN 四组、OPERATOR 除模型外三组、EDITOR 文章两组。
     *
     * <p><b>REVIEWER / VIEWER 为什么是空集而不是只读组</b>：{@code /api/assistant/chat} 的角色闸门是
     * {@code hasAnyRole('ADMIN','OPERATOR','EDITOR')}（plan §4.1），这两类角色根本发不起对话。
     * 若这里给它们 {@code ASSISTANT_READ}，那是一份**永远用不上的授权**——而「授权表比实际闸门宽」
     * 是最危险的一种不一致：将来谁放宽了闸门，工具权限会跟着自动放大，评审时却看不出这是一次提权。
     * 让两处口径相同（都没有），不确定的一侧一律收紧。
     */
    private static Set<String> allowedOf(String normalizedRole) {
        return switch (normalizedRole) {
            case "ADMIN" -> Set.of(READ, ARTICLE_WRITE, TASK, MODEL);
            case "OPERATOR" -> Set.of(READ, ARTICLE_WRITE, TASK);
            case "EDITOR" -> Set.of(READ, ARTICLE_WRITE);
            // REVIEWER / VIEWER：见方法注释——闸门不允许它们发起对话，这里也不给组

            // fail-closed：未知角色不给任何工具，绝不放宽成全集
            default -> Set.of();
        };
    }

    /**
     * 角色允许的组键，顺序固定按 {@link ToolRegistry#ASSISTANT_GROUPS} 的声明顺序
     * （ADMIN 为 [READ, ARTICLE_WRITE, TASK, MODEL]）。
     *
     * <p>为什么按注册表顺序过滤、而不是在 switch 里直接写死 {@code List.of(...)}：
     * 组键的唯一枚举源是 {@link ToolRegistry}，这里只声明「哪些键被允许」，输出顺序跟着注册表走，
     * 加组/改名时不需要在策略表里同步重排。反过来也**不会**因为注册表新增了组就自动放权——
     * 新组不在 {@code allowedOf} 的集合里，过滤后依然不出现（fail-closed 同样适用于未来的新组）。
     */
    public static List<String> groupsFor(String role) {
        Set<String> allowed = allowedOf(normalize(role));
        if (allowed.isEmpty()) return List.of();
        List<String> groups = new ArrayList<>(allowed.size());
        for (String key : ToolRegistry.ASSISTANT_GROUPS) {
            if (allowed.contains(key)) groups.add(key);
        }
        return List.copyOf(groups);
    }

    /**
     * 把「定义声明的组」收敛到角色允许的组（{@code agent_definition.tool_keys} ∩ 角色策略）。
     *
     * <p>逐条语义：保持 {@code declared} 的原有顺序（用户在定义里排的顺序即期望的暴露顺序）；
     * {@code null} 与未知组键直接丢弃（不是抛异常——定义是从数据库读出来的历史数据，
     * 一个残留的旧组键不该让整条助手链路不可用）；未登记角色返回空列表（同 fail-closed）。
     *
     * <p>为什么不去重：这里只是把声明**收窄**，重复项由下游 {@code ToolRegistry.toolNames}
     * （LinkedHashSet）自然消解；在收窄函数里顺手改集合大小，会让「声明了几个组」这件事失真。
     */
    public static List<String> intersect(List<String> declared, String role) {
        if (declared == null || declared.isEmpty()) return List.of();
        Set<String> allowed = new LinkedHashSet<>(groupsFor(role));
        if (allowed.isEmpty()) return List.of();
        List<String> kept = new ArrayList<>(declared.size());
        for (String key : declared) {
            if (key == null) continue;
            String normalizedKey = key.trim();
            if (allowed.contains(normalizedKey)) kept.add(normalizedKey);
        }
        return List.copyOf(kept);
    }

    /** 角色名大小写与首尾空白都不可信（前端/DB/请求上下文都可能带），统一规范化后再比较。 */
    private static String normalize(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}