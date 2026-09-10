package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Skill prompt 组装核心（skills-agent-plan 5.2）。
 *
 * 算法：
 * 1. 收集 enabled skill，按 agent &lt; account &lt; task &lt; article 优先级并集去重；
 * 2. 按维度分组注入（维度顺序按 3.3 白名单表，LAYOUT 排最后）；
 * 3. LAYOUT 维度单注入：仅优先级最高一枚生效，其余记入 ignoredLayoutSkills 丢弃（防双引擎指令混用）；
 * 4. LAYOUT 保底：无任何生效 LAYOUT skill 时注入内置 default_layout（DB 不可用回落代码常量）；
 * 5. 生效引擎 = 排序最前 LAYOUT skill 的 engine（无 LAYOUT skill → PROMPT）；
 * 6. EDITOR 场景过滤 MARKFLOW 技能（render_markflow 第④期才交付）；
 * 7. MARKFLOW 时「排版模板」区 = skill content + 渲染服务实时语法 guide（组装时获取）；
 * 8. 账号 defaultStyle 非空注入一行；
 * 9. 长度上限 60000 字符（口径：仅 skill content 合计，不含协议常量与运行时语法 guide）。
 */
@Component
public class SkillPromptAssembler {
    private static final Logger log = LoggerFactory.getLogger(SkillPromptAssembler.class);
    static final int MAX_SKILL_CONTENT_LENGTH = 60000;
    static final String FALLBACK_DEFAULT_LAYOUT_KEY = "default_layout";
    /** DB 不可用时的保底排版指令（default_layout 的最小可用版本，纯段落约束不可缺失）。 */
    static final String FALLBACK_DEFAULT_LAYOUT_CONTENT = """

            【公众号正文视觉模板】
            新创作整篇文章或整篇重写时，正文必须采用内联样式 HTML 版式；局部修改已有文章时保持原有版式。
            正文使用简洁自然段（p），不要使用 ul、ol、dl、table，也不要写成条目清单。
            """;

    /** 维度显示名（与前端 utils/skills.js label 一致）。 */
    private static final Map<String, String> DIMENSION_LABELS = Map.ofEntries(
            Map.entry("AUDIENCE", "目标读者"), Map.entry("TOPIC", "选题策略"),
            Map.entry("WRITING", "写作风格"), Map.entry("LANGUAGE", "语言习惯"),
            Map.entry("TITLE", "标题风格"), Map.entry("OPENING", "开头写法"),
            Map.entry("ENDING", "结尾写法"), Map.entry("DIGEST", "摘要风格"),
            Map.entry("IMAGE", "图片风格"), Map.entry("LAYOUT", "排版模板"),
            Map.entry("FACT_CHECK", "事实核查"), Map.entry("OTHER", "其他"));

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final SkillMapper skillMapper;
    private final MarkFlowRenderService markFlowRenderService;

    public SkillPromptAssembler(SkillMapper skillMapper, MarkFlowRenderService markFlowRenderService) {
        this.skillMapper = skillMapper;
        this.markFlowRenderService = markFlowRenderService;
    }

    /**
     * 组装 skill 注入块。
     * 单条 skill 失效（已删除/停用）静默跳过；DB 不可用时跳过全部 DB 技能并使用代码保底排版指令。
     */
    public SkillPromptResult assemble(SkillContext context) {
        List<Skill> ignored = new ArrayList<>();
        List<Skill> candidates = resolveCandidates(context, ignored);
        // 60000 上限口径：仅 skill content 合计（协议常量与运行时语法 guide 不计入，5.2 审查修订）
        long totalLength = 0;
        for (Skill skill : candidates) {
            totalLength += contentLength(skill);
        }
        if (totalLength > MAX_SKILL_CONTENT_LENGTH) {
            throw new BusinessException("创作技能内容合计超过 " + MAX_SKILL_CONTENT_LENGTH
                    + " 字上限（当前 " + totalLength + " 字），请精简技能或减少绑定数量");
        }
        // 第④期起：EDITOR 场景不再过滤 MARKFLOW 技能（编辑器已支持 render_markflow 占位替换）

        // 按维度分组（保持优先级顺序），LAYOUT 仅保留优先级最高一枚
        Map<String, List<Skill>> grouped = new LinkedHashMap<>();
        List<Skill> layouts = new ArrayList<>();
        for (Skill skill : candidates) {
            if ("LAYOUT".equals(skill.getDimension())) {
                layouts.add(skill);
            } else {
                grouped.computeIfAbsent(skill.getDimension(), key -> new ArrayList<>()).add(skill);
            }
        }
        Skill effectiveLayout = layouts.isEmpty() ? null : layouts.get(0);
        for (int i = 1; i < layouts.size(); i++) {
            ignored.add(layouts.get(i));
        }

        StringBuilder prompt = new StringBuilder();
        for (String dimension : SkillDimensions.INJECTION_ORDER) {
            List<Skill> skills = grouped.get(dimension);
            if (skills == null || skills.isEmpty()) continue;
            for (Skill skill : skills) {
                prompt.append('【').append(DIMENSION_LABELS.getOrDefault(dimension, dimension)).append('】')
                        .append('\n').append(skill.getContent() == null ? "" : skill.getContent().strip())
                        .append("\n\n");
            }
        }
        if (effectiveLayout != null) {
            prompt.append("【排版模板】\n");
            String layoutBody = effectiveLayout.getContent() == null ? "" : effectiveLayout.getContent().strip();
            prompt.append(layoutBody);
            if (isMarkflow(effectiveLayout)) {
                // MARKFLOW：注入 skill content + 渲染服务实时语法 guide（与线上引擎严格同步，绝不内置副本）
                String guide = markFlowRenderService.fetchSyntaxGuide();
                prompt.append("\n\n---\n\n【MarkFlow 语法指令（渲染式排版引擎实时语法）】\n").append(guide);
                // engine_config 的主题色策略必须注入，否则 UI 里选的 FIXED 主题色对 Agent 不可见
                String themeInstruction = themeInstruction(effectiveLayout);
                if (!themeInstruction.isEmpty()) prompt.append("\n\n").append(themeInstruction);
            }
            prompt.append("\n\n");
        } else {
            // LAYOUT 保底：无任何生效 LAYOUT skill → 注入内置 default_layout
            String fallback = resolveFallbackDefaultLayout();
            prompt.append("【排版模板】\n").append(fallback.strip()).append("\n\n");
        }

        if (context.accountDefaultStyle() != null && !context.accountDefaultStyle().isBlank()) {
            prompt.append("账号默认风格：").append(context.accountDefaultStyle().strip()).append('\n');
        }

        LayoutEngine engine = effectiveLayout == null ? LayoutEngine.PROMPT : engineOf(effectiveLayout);
        return new SkillPromptResult(prompt.toString(), engine, List.copyOf(ignored));
    }

    /** 收集候选 skill：article（最高）&gt; task &gt; account &gt; agent 优先级并集去重，失效/停用 id 静默跳过。 */
    private List<Skill> resolveCandidates(SkillContext context, List<Skill> ignored) {
        LinkedHashSet<Long> orderedIds = new LinkedHashSet<>();
        // article 级优先级最高，排最前（仅 EDITOR 场景存在，定时链路无文章）
        if (context.scene() == SkillContext.Scene.EDITOR) {
            addAll(orderedIds, context.articleSkillIds());
        }
        addAll(orderedIds, context.taskSkillIds());
        addAll(orderedIds, context.accountSkillIds());
        addAll(orderedIds, context.agentDefaultSkillIds());
        if (orderedIds.isEmpty()) return List.of();

        List<Skill> resolved;
        try {
            resolved = skillMapper.findByIds(List.copyOf(orderedIds));
        } catch (Exception exception) {
            // DB 不可用：静默跳过全部 DB 技能，回落代码保底排版指令
            log.warn("读取创作技能失败，本次组装使用内置保底排版指令", exception);
            return List.of();
        }
        List<Skill> candidates = new ArrayList<>();
        for (Long id : orderedIds) {
            Skill skill = resolved.stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null);
            if (skill == null) continue; // 已删除：静默跳过（5.7 防悬挂引用）
            if (!Boolean.TRUE.equals(skill.getEnabled())) continue; // 已停用：不注入
            candidates.add(skill);
        }
        return candidates;
    }

    /**
     * MARKFLOW 主题色策略指令（engine_config）：FIXED 时必须把 Skill 配置的 accent/dark 原样下发给 Agent，
     * 否则设置页选定的固定主题色对模型不可见；AUTO 时提示按内容对照表就近选择。
     * 配置缺失/非法一律返回空串（不阻断组装）。
     */
    static String themeInstruction(Skill skill) {
        String raw = skill == null ? null : skill.getEngineConfig();
        if (raw == null || raw.isBlank()) return "";
        try {
            com.fasterxml.jackson.databind.JsonNode config = MAPPER.readTree(raw);
            String mode = config.path("accentMode").asText("AUTO");
            if ("FIXED".equalsIgnoreCase(mode)) {
                String accent = config.path("accent").asText("");
                String dark = config.path("dark").asText("");
                if (accent.isBlank()) return "";
                return "【主题色策略：FIXED】必须使用 accent=" + accent
                        + (dark.isBlank() ? "" : "、dark=" + dark)
                        + "，把它们作为 save_article_draft 的 accent/dark 参数提交，不要自行更换。";
            }
            return "【主题色策略：AUTO】依据内容主题从上方主题色对照表就近选择 accent"
                    + "（dark 可省略，由渲染服务自动派生），作为 save_article_draft 的 accent/dark 参数提交。";
        } catch (Exception exception) {
            log.warn("排版技能 {} 的 engine_config 不是合法 JSON，已忽略主题色策略", skill.getId());
            return "";
        }
    }

    /** LAYOUT 保底内容：优先取 DB 内置 default_layout；DB 不可用时回落代码常量。 */
    private String resolveFallbackDefaultLayout() {
        try {
            Skill builtin = skillMapper.findByBuiltinKey(FALLBACK_DEFAULT_LAYOUT_KEY);
            if (builtin != null && Boolean.TRUE.equals(builtin.getEnabled())
                    && builtin.getContent() != null && !builtin.getContent().isBlank()) {
                return builtin.getContent();
            }
        } catch (Exception exception) {
            log.warn("读取内置 default_layout 失败，使用代码保底排版指令", exception);
        }
        return FALLBACK_DEFAULT_LAYOUT_CONTENT;
    }

    private static boolean isMarkflow(Skill skill) {
        return "LAYOUT".equals(skill.getDimension()) && engineOf(skill) == LayoutEngine.MARKFLOW;
    }

    private static LayoutEngine engineOf(Skill skill) {
        return "MARKFLOW".equalsIgnoreCase(skill.getEngine()) ? LayoutEngine.MARKFLOW : LayoutEngine.PROMPT;
    }

    private static long contentLength(Skill skill) {
        return skill.getContent() == null ? 0 : skill.getContent().length();
    }

    private static void addAll(LinkedHashSet<Long> target, List<Long> ids) {
        if (ids == null) return;
        for (Long id : ids) {
            if (id != null) target.add(id);
        }
    }
}
