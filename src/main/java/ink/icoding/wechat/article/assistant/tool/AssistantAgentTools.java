package ink.icoding.wechat.article.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.agent.AgentDefinition;
import ink.icoding.wechat.article.agent.AgentDefinitionService;
import ink.icoding.wechat.article.agent.LlmProfileService;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局助手的智能体工具：查看、新建、修改、克隆、删除，以及切换模型绑定。
 *
 * <p><b>为什么归在 {@code ASSISTANT_MODEL} 组</b>：组键已经持久化在
 * {@code agent_definition.tool_keys} 里（内置助手定义就是这么写的），改名会让库里已有的授权
 * 一夜之间失配。所以键保持不变，扩的是这个组**管什么**——模型档案与智能体配置都是「系统配置」类，
 * 且都只给 ADMIN（{@code AssistantRolePolicy}）。
 *
 * <p>每个写工具的参数都只够改**一件事**：改人设就是改人设，换模型就是换模型。没有「把整个对象
 * 交上来」的入口——那种接口要求调用方先读全量再整体回写，漏一个字段就会静默清空。
 */
public final class AssistantAgentTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AssistantAgentTools() {
    }

    /**
     * 六个智能体工具：读、建、改、克隆、删、换模型。
     *
     * @param userId 写操作要记 {@code created_by}，而它在 {@code SecurityContext} 里；
     *               工具跑在智能体工作线程上，那里没有登录上下文，所以由装配点在请求线程解析后传进来
     */
    public static List<Tool> all(AgentDefinitionService agentService, LlmProfileService profileService,
                                 Long userId) {
        return List.of(new ReadAgentTool(agentService),
                new CreateAgentTool(agentService, userId),
                new UpdateAgentTool(agentService),
                new DuplicateAgentTool(agentService, userId),
                new DeleteAgentTool(agentService),
                new SetAgentModelTool(agentService, profileService));
    }

    @ToolInfo(name = "read_agent",
            description = "读取单个智能体的完整配置：标识、阶段、人格、工具组、技能、绑定的模型档案、"
                    + "温度、maxTokens、启用状态、是否内置。")
    public static class ReadAgentTool implements Tool<AgentIdParam> {
        private final AgentDefinitionService agentService;

        public ReadAgentTool(AgentDefinitionService agentService) {
            this.agentService = agentService;
        }

        @Override
        public String execute(AgentIdParam param) {
            return json(agentRow(agentService.required(requireId(param.getAgentId()))));
        }
    }

    @ToolInfo(name = "create_agent",
            description = "新建自定义智能体。标识只能用小写字母/数字/下划线（3-50 位），名称不可重复；"
                    + "toolKeys 至少一个（否则它无法产出任何结果）。"
                    + "内置智能体不可改，需要定制时用 duplicate_agent 克隆一个副本。")
    public static class CreateAgentTool implements Tool<CreateAgentParam> {
        private final AgentDefinitionService agentService;
        private final Long userId;

        public CreateAgentTool(AgentDefinitionService agentService, Long userId) {
            this.agentService = agentService;
            this.userId = userId;
        }

        @Override
        public String execute(CreateAgentParam param) {
            AgentDefinitionService.AgentRequest request = new AgentDefinitionService.AgentRequest(
                    param.getCode(), param.getName(), param.getStage(), param.getPersona(),
                    param.getToolKeys(), param.getSkillIds(), param.getLlmProfileId(),
                    param.getTemperature(), param.getMaxTokens(), param.getEnabled());
            AgentDefinition created = AssistantArticleTools.callAsUser(userId,
                    () -> agentService.create(request, userId));
            return json(agentRow(agentService.required(created.getId())));
        }
    }

    /**
     * 修改智能体：**只改传了的字段**。
     *
     * <p>服务层的 {@code update} 要求整份请求（名称、人设、工具组都必填），所以这里先读当前值、
     * 只覆盖调用方给的那些字段。这是助手内部的一处读改写，不是对外契约——调用方仍然只描述「改什么」。
     */
    @ToolInfo(name = "update_agent",
            description = "修改智能体：只需传要改的字段，未传的保持原值（例如只改人设就只传 persona）。"
                    + "内置智能体的标识与阶段锁定，不能改；要改内置智能体请先 clone。")
    public static class UpdateAgentTool implements Tool<UpdateAgentParam> {
        private final AgentDefinitionService agentService;

        public UpdateAgentTool(AgentDefinitionService agentService) {
            this.agentService = agentService;
        }

        @Override
        public String execute(UpdateAgentParam param) {
            AgentDefinition current = agentService.required(requireId(param.getAgentId()));
            AgentDefinitionService.AgentView view = AgentDefinitionService.toView(current);
            AgentDefinitionService.AgentRequest request = new AgentDefinitionService.AgentRequest(
                    firstNonNull(param.getCode(), view.code()),
                    firstNonNull(param.getName(), view.name()),
                    firstNonNull(param.getStage(), view.stage()),
                    firstNonNull(param.getPersona(), view.persona()),
                    param.getToolKeys() != null ? param.getToolKeys() : view.toolKeys(),
                    param.getSkillIds() != null ? param.getSkillIds() : view.skillIds(),
                    param.getLlmProfileId() != null ? param.getLlmProfileId() : view.llmProfileId(),
                    param.getTemperature() != null ? param.getTemperature() : view.temperature(),
                    param.getMaxTokens() != null ? param.getMaxTokens() : view.maxTokens(),
                    param.getEnabled() != null ? param.getEnabled() : view.enabled());
            agentService.update(current.getId(), request);
            return json(agentRow(agentService.required(current.getId())));
        }
    }

    @ToolInfo(name = "duplicate_agent",
            description = "把智能体（含内置的）克隆为自定义副本：工具组、技能、模型绑定、温度都照抄，"
                    + "名称自动加「副本」后缀并保证唯一。改内置智能体的正确做法是先克隆再改副本。")
    public static class DuplicateAgentTool implements Tool<AgentIdParam> {
        private final AgentDefinitionService agentService;
        private final Long userId;

        public DuplicateAgentTool(AgentDefinitionService agentService, Long userId) {
            this.agentService = agentService;
            this.userId = userId;
        }

        @Override
        public String execute(AgentIdParam param) {
            AgentDefinition copy = AssistantArticleTools.callAsUser(userId,
                    () -> agentService.duplicate(requireId(param.getAgentId()), userId));
            return json(agentRow(agentService.required(copy.getId())));
        }
    }

    @ToolInfo(name = "delete_agent",
            description = "删除智能体。内置智能体不可删除（会报错，请改用 duplicate_agent 克隆副本）。"
                    + "删除前应先向用户确认：被定时任务或编辑器引用的智能体删掉后，那些流程会缺少对应角色。")
    public static class DeleteAgentTool implements Tool<AgentIdParam> {
        private final AgentDefinitionService agentService;

        public DeleteAgentTool(AgentDefinitionService agentService) {
            this.agentService = agentService;
        }

        @Override
        public String execute(AgentIdParam param) {
            Long id = requireId(param.getAgentId());
            AgentDefinition agent = agentService.required(id);
            agentService.delete(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deleted", true);
            result.put("agentId", id);
            result.put("name", agent.getName());
            return json(result);
        }
    }

    @ToolInfo(name = "set_agent_model",
            description = "修改某个智能体使用的模型档案（只改绑定，不动人格、工具、技能）。"
                    + "先用 list_agents 查 id、用 list_llm_profiles 查档案 id。"
                    + "profileId 传 null 表示解除绑定、跟随系统默认档案。"
                    + "返回里 usable=false 表示该档案当前发不出请求（缺密钥或被禁用），"
                    + "此时智能体会自动改用默认档案——绑定成功不等于立刻可用，要如实转告用户。")
    public static class SetAgentModelTool implements Tool<SetAgentModelParam> {
        private final AgentDefinitionService agentService;
        private final LlmProfileService profileService;

        public SetAgentModelTool(AgentDefinitionService agentService, LlmProfileService profileService) {
            this.agentService = agentService;
            this.profileService = profileService;
        }

        @Override
        public String execute(SetAgentModelParam param) {
            AgentDefinition agent = agentService.setLlmProfile(requireId(param.getAgentId()),
                    param.getProfileId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("agentId", agent.getId());
            result.put("code", agent.getCode());
            result.put("name", agent.getName());
            result.put("stage", agent.getStage());
            result.put("llmProfileId", agent.getLlmProfileId());

            if (agent.getLlmProfileId() == null) {
                result.put("binding", "已解除绑定，运行时使用系统默认档案");
                return json(result);
            }

            LlmProfileService.ProfileView view = findProfile(profileService, agent.getLlmProfileId());
            Map<String, Object> bound = new LinkedHashMap<>();
            bound.put("id", view.id());
            bound.put("name", view.name());
            bound.put("providerName", view.providerName());
            bound.put("modelName", view.modelName());
            bound.put("enabled", view.enabled());
            bound.put("usable", view.runtimeAvailable());
            result.put("profile", bound);
            if (!view.runtimeAvailable()) {
                // 绑定成功但跑不起来，是最容易被「改好了」这句话盖过去的情况，必须显式说出来
                result.put("warning", "该档案当前不可用（缺密钥或被禁用），运行时会回落到默认档案");
            }
            return json(result);
        }
    }

    // ---- 参数类 ----

    @Data
    public static class AgentIdParam extends ToolParam {
        @Param(description = "智能体 id")
        private Long agentId;
    }

    @Data
    public static class CreateAgentParam extends ToolParam {
        @Param(description = "智能体标识：小写字母/数字/下划线，3-50 位，例如 social_writer")
        private String code;
        @Param(description = "名称，不可与现有智能体重名")
        private String name;
        @Param(description = "阶段：EDITOR / SCHEDULED_SINGLE / RESEARCH / WRITING / ILLUSTRATION / REVIEW / COORDINATE")
        private String stage;
        @Param(description = "人设提示词：它是什么角色、遵循什么原则、产出什么")
        private String persona;
        @Param(description = "工具组键列表，至少一个（例如 DRAFT_WRITE、RESEARCH、REVIEW）")
        private List<String> toolKeys;
        @Param(required = false, description = "绑定的技能 id 列表")
        private List<Long> skillIds;
        @Param(required = false, description = "绑定的模型档案 id；不传则跟随默认档案")
        private Long llmProfileId;
        @Param(required = false, description = "温度 0.0-2.0，留空用默认")
        private java.math.BigDecimal temperature;
        @Param(required = false, description = "maxTokens，256-32768，留空用默认")
        private Integer maxTokens;
        @Param(required = false, description = "是否启用，默认 true")
        private Boolean enabled;
    }

    @Data
    public static class UpdateAgentParam extends ToolParam {
        @Param(description = "要修改的智能体 id")
        private Long agentId;
        @Param(required = false, description = "新的标识（内置智能体不可改）")
        private String code;
        @Param(required = false, description = "新名称")
        private String name;
        @Param(required = false, description = "新阶段（内置智能体不可改）")
        private String stage;
        @Param(required = false, description = "新人设")
        private String persona;
        @Param(required = false, description = "新的工具组键列表（整体替换，至少一个）")
        private List<String> toolKeys;
        @Param(required = false, description = "新的技能 id 列表（整体替换，传空数组表示清空）")
        private List<Long> skillIds;
        @Param(required = false, description = "新的模型档案 id")
        private Long llmProfileId;
        @Param(required = false, description = "新温度 0.0-2.0")
        private java.math.BigDecimal temperature;
        @Param(required = false, description = "新 maxTokens 256-32768")
        private Integer maxTokens;
        @Param(required = false, description = "启用/停用")
        private Boolean enabled;
    }

    @Data
    public static class SetAgentModelParam extends ToolParam {
        @Param(description = "智能体 id")
        private Long agentId;
        @Param(required = false, description = "模型档案 id；留空表示解除绑定、跟随默认档案")
        private Long profileId;
    }

    // ---- 内部 ----

    /**
     * 智能体 → 上下文友好的一行。
     *
     * <p>回 {@code llmProfileId} 而不是档案名：模型档案随时会被改名/删除，回一个 id 让调用方
     * 能用 {@code list_llm_profiles} 复核当时的真实状态，而不是读到一个可能已经过期的名字。
     */
    private static Map<String, Object> agentRow(AgentDefinition agent) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", agent.getId());
        row.put("code", agent.getCode());
        row.put("name", agent.getName());
        row.put("stage", agent.getStage());
        row.put("enabled", agent.getEnabled());
        row.put("isBuiltin", agent.getIsBuiltin());
        row.put("toolKeys", AgentDefinitionService.parseToolKeys(agent.getToolKeys()));
        row.put("skillIds", parseSkillIds(agent.getSkillIds()));
        row.put("llmProfileId", agent.getLlmProfileId());
        row.put("temperature", agent.getTemperature());
        row.put("maxTokens", agent.getMaxTokens());
        row.put("persona", agent.getPersona());
        return row;
    }

    /** skill_ids 在库里是 JSON 数组字符串；脏数据解析失败时回空表而不是让整次工具调用崩掉。 */
    private static List<Long> parseSkillIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String part : raw.replaceAll("[\\[\\]\"\\s]", "").split(",")) {
            if (!part.isBlank()) {
                try {
                    ids.add(Long.parseLong(part));
                } catch (NumberFormatException ignored) {
                    // 单个坏值不该让「查看智能体」整体失败
                }
            }
        }
        return ids;
    }

    private static Long requireId(Long id) {
        if (id == null) throw new IllegalArgumentException("请给出智能体 id（可先用 list_agents 查找）");
        return id;
    }

    private static <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    /** 档案视图里没有按 id 单查的入口，{@code list()} 是几十行级别，直接找。 */
    private static LlmProfileService.ProfileView findProfile(LlmProfileService service, Long id) {
        for (LlmProfileService.ProfileView view : service.list()) {
            if (id.equals(view.id())) return view;
        }
        throw new IllegalArgumentException("模型档案不存在：id=" + id + "（可先用 list_llm_profiles 重新核对）");
    }

    private static String json(Map<String, Object> value) {
        try {
            return AssistantToolBudget.cap(MAPPER.writeValueAsString(value));
        } catch (Exception exception) {
            throw new IllegalStateException("工具结果序列化失败", exception);
        }
    }
}