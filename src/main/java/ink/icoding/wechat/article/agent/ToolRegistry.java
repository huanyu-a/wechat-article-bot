package ink.icoding.wechat.article.agent;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具键注册表（skills-agent-plan 5.3）：统一登记工具组与组内工具名，AgentDefinition.tool_keys 保存时校验。
 * 浏览器编辑工具组（BROWSER_EDITOR）由前端 SSE 管道执行；其余为服务端工具。
 */
@Component
public class ToolRegistry {
    public static final String BROWSER_EDITOR = "BROWSER_EDITOR";
    public static final String MEDIA = "MEDIA";
    public static final String DRAFT_READ = "DRAFT_READ";
    public static final String DRAFT_WRITE = "DRAFT_WRITE";
    public static final String RESEARCH = "RESEARCH";
    public static final String REVIEW = "REVIEW";
    public static final String DELEGATE = "DELEGATE";
    public static final String RENDER = "RENDER";

    private static final Map<String, Group> GROUPS = buildGroups();

    private static Map<String, Group> buildGroups() {
        Map<String, Group> groups = new LinkedHashMap<>();
        groups.put(BROWSER_EDITOR, new Group(BROWSER_EDITOR, "浏览器编辑工具", true,
                "在用户浏览器中读写文章（需编辑器页面打开）",
                List.of("read_article", "read_blocks", "delete_blocks", "insert_blocks", "replace_blocks",
                        "update_metadata", "update_cover")));
        // 图片来源硬约束（2026-09-19）：MEDIA 组不含 search_web_images / import_web_image——
        // 所有图片只允许 generate_image（AI 创作）或素材库既有图片（list_image_assets），
        // 网络搜图从工具层面就不存在（用户约定，known-issues-handoff.md D55）。
        groups.put(MEDIA, new Group(MEDIA, "素材与联网工具", false, "搜索、浏览、图片检索/生成/编辑",
                List.of("search_web", "browse_webpage", "list_image_assets",
                        "generate_image", "edit_image")));
        groups.put(DRAFT_READ, new Group(DRAFT_READ, "草稿只读", false, "读取任务工作区草稿（检查不动笔的角色使用）",
                List.of("read_article_draft")));
        groups.put(DRAFT_WRITE, new Group(DRAFT_WRITE, "草稿读写", false, "保存草稿与设置封面",
                List.of("save_article_draft", "set_article_draft_cover")));
        groups.put(RESEARCH, new Group(RESEARCH, "调研笔记", false, "调研员落盘结构化简报",
                List.of("save_research_notes")));
        groups.put(REVIEW, new Group(REVIEW, "审核结论", false, "审稿人提交结构化审核结论",
                List.of("submit_review")));
        groups.put(DELEGATE, new Group(DELEGATE, "委托子智能体", false, "协调者委托调研/写作/配图/审核",
                List.of("delegate_research", "delegate_writing", "delegate_illustration", "delegate_review")));
        groups.put(RENDER, new Group(RENDER, "渲染式排版", false, "MarkFlow 渲染（编辑器链路）",
                List.of("render_markflow")));
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }

    /** 全部工具组（前端勾选用）。 */
    public List<Group> groups() {
        return List.copyOf(GROUPS.values());
    }

    public Set<String> groupKeys() {
        return GROUPS.keySet();
    }

    /** 校验工具组键列表，未知键抛 BusinessException。 */
    public void validate(List<String> keys) {
        if (keys == null) return;
        for (String key : keys) {
            if (key == null || !GROUPS.containsKey(key)) {
                throw new BusinessException("未知的工具组：" + key);
            }
        }
    }

    /** 展开工具组为工具名集合（供 AgentFactory 装配与调试）。 */
    public Set<String> toolNames(List<String> keys) {
        validate(keys);
        Set<String> names = new java.util.LinkedHashSet<>();
        if (keys == null) return names;
        for (String key : keys) {
            Group group = GROUPS.get(key);
            if (group != null) names.addAll(group.tools());
        }
        return names;
    }

    /**
     * 按工具组声明过滤工具实例（D55 图片来源硬约束，2026-09-19）：组声明是工具暴露的唯一事实源，
     * 装配点拿到的清单里凡不在这份名单内的实例一律剔除。MEDIA 组的
     * {@code search_web_images} / {@code import_web_image} 正是在这一步从智能体视野里消失的。
     *
     * <p>为什么在注册表这一层过滤，而不是让 {@code ArticleMediaTools.create()} 直接少返回两个：
     * create() 的产物还被按名索引的兜底映射与预算统计引用，整体收缩会牵连它们；过滤只收窄
     * 「暴露给模型」的集合，语义单一，且能让三个装配点共用同一份判定而不各写一遍。
     *
     * <p>未知组键返回空列表（fail-closed）：宁可一个工具都不暴露，也不在约束失效时退回全量清单。
     * 保持入参顺序；缺 {@code @ToolInfo} 注解的工具按其类简单名判定（agent4j 的
     * {@link ToolDescriptor#fromTool} 行为），同理不在名单内即被剔除。
     */
    public List<Tool> filterByGroup(String groupKey, List<Tool> tools) {
        Group group = groupKey == null ? null : GROUPS.get(groupKey);
        if (group == null || tools == null || tools.isEmpty()) return List.of();
        Set<String> allowed = new LinkedHashSet<>(group.tools());
        List<Tool> kept = new ArrayList<>(tools.size());
        for (Tool tool : tools) {
            if (tool != null && allowed.contains(ToolDescriptor.fromTool(tool).getName())) {
                kept.add(tool);
            }
        }
        return List.copyOf(kept);
    }

    public record Group(String key, String name, boolean browserSide, String description, List<String> tools) {
    }
}
