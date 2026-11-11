package ink.icoding.wechat.article.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.skill.Skill;
import ink.icoding.wechat.article.skill.SkillService;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局助手的创作技能工具：查看、新建、修改、克隆、删除。
 *
 * <p><b>为什么不提供「预览」</b>：页面上那条预览链路要跑模板引擎并回渲染结果，属于编辑器交互；
 * 助手这边只维护技能定义本身，预览交给页面——一个工具一旦开始承担两种用途，它的返回格式就会
 * 既不适合对话也不适合页面。
 *
 * <p>技能正文可能很长（整份写作提示词），{@code read_skill} 会回正文但统一经
 * {@link AssistantToolBudget#cap} 截断，避免一次读取把后续轮次的上下文挤没。
 */
public final class AssistantSkillTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AssistantSkillTools() {
    }

    public static List<Tool> all(SkillService skillService, Long userId) {
        return List.of(new ReadSkillTool(skillService),
                new CreateSkillTool(skillService, userId),
                new UpdateSkillTool(skillService),
                new DuplicateSkillTool(skillService, userId),
                new DeleteSkillTool(skillService));
    }

    @ToolInfo(name = "read_skill",
            description = "读取单个创作技能的完整定义（含正文提示词）。正文过长会被截断。")
    public static class ReadSkillTool implements Tool<SkillIdParam> {
        private final SkillService skillService;

        public ReadSkillTool(SkillService skillService) {
            this.skillService = skillService;
        }

        @Override
        public String execute(SkillIdParam param) {
            return json(skillRow(skillService.required(requireId(param.getSkillId())), true));
        }
    }

    @ToolInfo(name = "create_skill",
            description = "新建创作技能。dimension 是技能分类（如 RESEARCH / WRITING / ILLUSTRATION / REVIEW），"
                    + "content 是写进模型上下文的正文提示词。")
    public static class CreateSkillTool implements Tool<CreateSkillParam> {
        private final SkillService skillService;
        private final Long userId;

        public CreateSkillTool(SkillService skillService, Long userId) {
            this.skillService = skillService;
            this.userId = userId;
        }

        @Override
        public String execute(CreateSkillParam param) {
            SkillService.SkillRequest request = new SkillService.SkillRequest(
                    param.getName(), param.getDimension(), param.getDescription(), param.getContent(),
                    param.getEngine(), param.getEngineConfig(), param.getEnabled());
            Skill created = AssistantArticleTools.callAsUser(userId,
                    () -> skillService.create(request, userId));
            return json(skillRow(skillService.required(created.getId()), false));
        }
    }

    /**
     * 修改技能：只改传了的字段，其余保持原值。
     *
     * <p>服务层要求整份 {@code SkillRequest}，所以这里先读当前值再覆盖——与 {@code update_agent}
     * 同样的理由：调用方只该描述「改什么」，不该为了改一句描述把整篇正文重发一遍。
     */
    @ToolInfo(name = "update_skill",
            description = "修改创作技能：只传要改的字段，未传的保持原值（例如只改 content 就只传它）。"
                    + "内置技能可以改副本，直接改内置技能会被服务层拒绝时请改用 duplicate_skill。")
    public static class UpdateSkillTool implements Tool<UpdateSkillParam> {
        private final SkillService skillService;

        public UpdateSkillTool(SkillService skillService) {
            this.skillService = skillService;
        }

        @Override
        public String execute(UpdateSkillParam param) {
            Skill current = skillService.required(requireId(param.getSkillId()));
            SkillService.SkillRequest request = new SkillService.SkillRequest(
                    param.getName() != null ? param.getName() : current.getName(),
                    param.getDimension() != null ? param.getDimension() : current.getDimension(),
                    param.getDescription() != null ? param.getDescription() : current.getDescription(),
                    param.getContent() != null ? param.getContent() : current.getContent(),
                    param.getEngine() != null ? param.getEngine() : current.getEngine(),
                    param.getEngineConfig() != null ? param.getEngineConfig() : current.getEngineConfig(),
                    param.getEnabled() != null ? param.getEnabled() : current.getEnabled());
            skillService.update(current.getId(), request);
            return json(skillRow(skillService.required(current.getId()), false));
        }
    }

    @ToolInfo(name = "duplicate_skill",
            description = "把技能（含内置的）克隆为自定义副本，名称自动加「副本」后缀并保证唯一。")
    public static class DuplicateSkillTool implements Tool<SkillIdParam> {
        private final SkillService skillService;
        private final Long userId;

        public DuplicateSkillTool(SkillService skillService, Long userId) {
            this.skillService = skillService;
            this.userId = userId;
        }

        @Override
        public String execute(SkillIdParam param) {
            Skill copy = AssistantArticleTools.callAsUser(userId,
                    () -> skillService.duplicate(requireId(param.getSkillId()), userId));
            return json(skillRow(skillService.required(copy.getId()), false));
        }
    }

    @ToolInfo(name = "delete_skill",
            description = "删除技能。内置技能不可删除（请用 duplicate_skill 克隆副本）。"
                    + "删除前先向用户确认：技能被智能体或定时任务引用，删掉后它们会缺少对应能力。")
    public static class DeleteSkillTool implements Tool<SkillIdParam> {
        private final SkillService skillService;

        public DeleteSkillTool(SkillService skillService) {
            this.skillService = skillService;
        }

        @Override
        public String execute(SkillIdParam param) {
            Long id = requireId(param.getSkillId());
            Skill skill = skillService.required(id);
            skillService.delete(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deleted", true);
            result.put("skillId", id);
            result.put("name", skill.getName());
            return json(result);
        }
    }

    // ---- 参数类 ----

    @Data
    public static class SkillIdParam extends ToolParam {
        @Param(description = "技能 id")
        private Long skillId;
    }

    @Data
    public static class CreateSkillParam extends ToolParam {
        @Param(description = "技能名称，不可重复")
        private String name;
        @Param(description = "技能分类：RESEARCH / WRITING / ILLUSTRATION / REVIEW 等")
        private String dimension;
        @Param(required = false, description = "一句话说明这个技能做什么")
        private String description;
        @Param(description = "技能正文（写进模型上下文的提示词）")
        private String content;
        @Param(required = false, description = "渲染引擎，一般留空")
        private String engine;
        @Param(required = false, description = "引擎配置对象，一般留空")
        private Object engineConfig;
        @Param(required = false, description = "是否启用，默认 true")
        private Boolean enabled;
    }

    @Data
    public static class UpdateSkillParam extends ToolParam {
        @Param(description = "要修改的技能 id")
        private Long skillId;
        @Param(required = false, description = "新名称")
        private String name;
        @Param(required = false, description = "新分类")
        private String dimension;
        @Param(required = false, description = "新说明")
        private String description;
        @Param(required = false, description = "新正文提示词")
        private String content;
        @Param(required = false, description = "新渲染引擎")
        private String engine;
        @Param(required = false, description = "新引擎配置")
        private Object engineConfig;
        @Param(required = false, description = "启用/停用")
        private Boolean enabled;
    }

    // ---- 内部 ----

    /**
     * 技能 → 上下文友好的一行。
     *
     * @param withContent 列表场景不回正文（几十个技能各带一篇正文会撑爆上下文），单个读取时才回
     */
    private static Map<String, Object> skillRow(Skill skill, boolean withContent) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", skill.getId());
        row.put("name", skill.getName());
        row.put("dimension", skill.getDimension());
        row.put("description", skill.getDescription());
        row.put("engine", skill.getEngine());
        row.put("enabled", skill.getEnabled());
        row.put("isBuiltin", skill.getIsBuiltin());
        if (withContent) row.put("content", skill.getContent());
        return row;
    }

    private static Long requireId(Long id) {
        if (id == null) throw new IllegalArgumentException("请给出技能 id（可先用 list_skills 查找）");
        return id;
    }

    private static String json(Map<String, Object> value) {
        try {
            return AssistantToolBudget.cap(MAPPER.writeValueAsString(value));
        } catch (Exception exception) {
            throw new IllegalStateException("工具结果序列化失败", exception);
        }
    }
}