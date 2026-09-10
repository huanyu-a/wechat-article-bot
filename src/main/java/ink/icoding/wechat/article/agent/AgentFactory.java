package ink.icoding.wechat.article.agent;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.entity.ModelType;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.settings.LlmConfigService;
import ink.icoding.wechat.article.skill.SkillContext;
import ink.icoding.wechat.article.skill.SkillPromptAssembler;
import ink.icoding.wechat.article.skill.SkillPromptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能体装配工厂（skills-agent-plan 5.3）：按 AgentDefinition 组装 AgentClient。
 *
 * 装配顺序：
 * 1. 校验定义可用性（enabled）与模型档案（llm_profile_id 为空 → 默认档案；无任何档案 → 回落 llm_config）；
 * 2. description = 核心协议常量（按 stage）+ persona + SkillPromptAssembler 产出；
 * 3. 工具 = 由调用方提供的 ToolResolver 按 tool_keys 实例化（浏览器工具走 EditorSession 的 SSE 管道，
 *    服务端工具走 TaskWorkspace/ArticleMediaTools）；
 * 4. 模型 = LLMModel.create(provider, baseUrl, modelName, apiKey)（agent4j 2.3.3 仅 4 参，temperature/maxTokens 暂不生效）。
 */
@Component
public class AgentFactory {
    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    /** 内置定义 code（两链路默认装配用）。 */
    public static final String CODE_EDITOR = "builtin_editor";
    public static final String CODE_SCHEDULED_CREATOR = "builtin_scheduled_creator";
    public static final String CODE_RESEARCHER = "builtin_researcher";
    public static final String CODE_WRITER = "builtin_writer";
    public static final String CODE_ILLUSTRATOR = "builtin_illustrator";
    public static final String CODE_REVIEWER = "builtin_reviewer";
    public static final String CODE_CHIEF = "builtin_chief";

    private final AgentDefinitionMapper definitionMapper;
    private final LlmProfileService llmProfileService;
    private final LlmConfigService llmConfigService;
    private final SkillPromptAssembler skillPromptAssembler;

    public AgentFactory(AgentDefinitionMapper definitionMapper, LlmProfileService llmProfileService,
                        LlmConfigService llmConfigService, SkillPromptAssembler skillPromptAssembler) {
        this.definitionMapper = definitionMapper;
        this.llmProfileService = llmProfileService;
        this.llmConfigService = llmConfigService;
        this.skillPromptAssembler = skillPromptAssembler;
    }

    /** 工具解析器：按工具组键返回该上下文的工具实例。 */
    @FunctionalInterface
    public interface ToolResolver {
        List<Tool> resolve(List<String> groupKeys);
    }

    /**
     * 按 code 装配（两链路默认入口）：定义缺失/停用时回落到内置默认常量，保证存量行为不中断。
     *
     * @param code     期望的 agent code（如 builtin_editor）
     * @param fallbackStage 定义缺失时用于取核心协议与默认工具的 stage
     */
    public AgentClient buildByCode(String code, String fallbackStage, SkillContext skillContext,
                                   ToolResolver resolver) {
        return buildByCode(code, fallbackStage, skillContext, null, resolver);
    }

    /**
     * 按 code 装配，可复用已组装的 Skill 提示（避免一次装配里重复组装：MARKFLOW 下会重复取语法 guide）。
     * {@code preAssembled} 为 null 时内部组装。
     */
    public AgentClient buildByCode(String code, String fallbackStage, SkillContext skillContext,
                                   SkillPromptResult preAssembled, ToolResolver resolver) {
        AgentDefinition definition = definitionMapper.findByCode(code);
        if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
            log.warn("智能体定义 {} 不存在或已停用，回落内置默认装配（stage={}）", code, fallbackStage);
            return buildFallback(fallbackStage, skillContext, preAssembled, resolver);
        }
        return build(definition, skillContext, preAssembled, resolver);
    }

    /** 按定义装配。 */
    public AgentClient build(AgentDefinition definition, SkillContext skillContext, ToolResolver resolver) {
        return build(definition, skillContext, null, resolver);
    }

    /** 按定义装配（可复用已组装的 Skill 提示）。 */
    public AgentClient build(AgentDefinition definition, SkillContext skillContext,
                             SkillPromptResult preAssembled, ToolResolver resolver) {
        if (definition == null) throw new BusinessException("智能体定义不存在");
        if (!Boolean.TRUE.equals(definition.getEnabled())) {
            throw new BusinessException("智能体「" + definition.getName() + "」已停用");
        }
        List<String> groupKeys = AgentDefinitionService.parseToolKeys(definition.getToolKeys());
        AgentClient agent = new AgentClient();
        agent.setName(definition.getName() == null ? "墨舟智能体" : definition.getName());
        agent.setDescription(assembleDescription(definition, skillContext, preAssembled));
        agent.setModel(createModel(definition));
        List<Tool> tools = resolver == null ? List.of() : resolver.resolve(groupKeys);
        agent.setTools(tools == null ? List.of() : tools);
        return agent;
    }

    /** 定义缺失时的兜底装配：核心协议按 stage、模型走默认档案、工具由 resolver 按 fallbackStage 的默认组解析。 */
    private AgentClient buildFallback(String stage, SkillContext skillContext,
                                      SkillPromptResult preAssembled, ToolResolver resolver) {
        AgentClient agent = new AgentClient();
        agent.setName("墨舟智能体");
        SkillPromptResult skillPrompt = preAssembled == null
                ? skillPromptAssembler.assemble(skillContext) : preAssembled;
        agent.setDescription(AgentProtocols.byStage(stage) + skillPrompt.prompt());
        agent.setModel(createModel(null));
        List<Tool> tools = resolver == null ? List.of() : resolver.resolve(defaultGroupsFor(stage));
        agent.setTools(tools == null ? List.of() : tools);
        return agent;
    }

    /** 兜底工具组：按 stage 精确给出（与内置 seed 一致，避免审稿人兜底时拿到写草稿权限）。 */
    static List<String> defaultGroupsFor(String stage) {
        return switch (stage == null ? "" : stage) {
            case "EDITOR" -> List.of(ToolRegistry.BROWSER_EDITOR, ToolRegistry.MEDIA,
                    ToolRegistry.RENDER, ToolRegistry.DELEGATE);
            case "RESEARCH" -> List.of(ToolRegistry.RESEARCH, ToolRegistry.MEDIA);
            case "WRITING" -> List.of(ToolRegistry.DRAFT_READ, ToolRegistry.DRAFT_WRITE);
            case "ILLUSTRATION" -> List.of(ToolRegistry.MEDIA, ToolRegistry.DRAFT_READ,
                    ToolRegistry.DRAFT_WRITE);
            case "REVIEW" -> List.of(ToolRegistry.DRAFT_READ, ToolRegistry.MEDIA, ToolRegistry.REVIEW);
            case "COORDINATE" -> List.of(ToolRegistry.DELEGATE, ToolRegistry.DRAFT_READ);
            default -> List.of(ToolRegistry.DRAFT_READ, ToolRegistry.DRAFT_WRITE, ToolRegistry.MEDIA);
        };
    }

    /** 系统提示 = 核心协议（按 stage，代码常量）+ persona（DB，可编辑）+ Skill 注入块。 */
    public String assembleDescription(AgentDefinition definition, SkillContext skillContext) {
        return assembleDescription(definition, skillContext, null);
    }

    /** 系统提示（可复用已组装的 Skill 提示，避免重复组装）。 */
    public String assembleDescription(AgentDefinition definition, SkillContext skillContext,
                                      SkillPromptResult preAssembled) {
        SkillPromptResult skillPrompt = preAssembled == null
                ? skillPromptAssembler.assemble(skillContext) : preAssembled;
        String persona = definition.getPersona() == null ? "" : definition.getPersona().strip();
        return AgentProtocols.byStage(definition.getStage())
                + (persona.isEmpty() ? "" : persona + "\n\n")
                + skillPrompt.prompt();
    }

    /**
     * 模型解析：llm_profile_id 指定档案 → 默认档案 → 回落 llm_config（存量兼容）。
     * 注：agent4j 2.3.3 的 LLMModel.create 仅接受 (ModelType, baseUrl, modelName, apiKey)，
     * temperature/maxTokens 覆盖项当前无法生效（方案 7 第②期第 0 项已确认），保留字段待上游支持。
     */
    private LLMModel createModel(AgentDefinition definition) {
        if (definition != null && definition.getLlmProfileId() != null) {
            // 软引用容错（方案 5.7）：绑定的档案被删除时静默回落默认档案，不让智能体直接不可用
            LlmProfile profile = llmProfileService.findById(definition.getLlmProfileId());
            if (profile == null) {
                log.warn("智能体 {} 绑定的模型档案 {} 已删除，回落默认档案",
                        definition.getCode(), definition.getLlmProfileId());
            } else {
                LlmProfileService.RuntimeProfile runtime = llmProfileService.runtime(profile);
                if (runtime != null && runtime.available()) {
                    warnIfOverridden(definition);
                    return createModel(runtime.provider(), runtime.baseUrl(), runtime.modelName(),
                            runtime.apiKey());
                }
                log.warn("智能体 {} 指定的模型档案不可用，回落默认档案", definition.getCode());
            }
        }
        LlmProfile defaultProfile = llmProfileService.defaultProfile();
        LlmProfileService.RuntimeProfile runtime = llmProfileService.runtime(defaultProfile);
        if (runtime != null && runtime.available()) {
            if (definition != null) warnIfOverridden(definition);
            return createModel(runtime.provider(), runtime.baseUrl(), runtime.modelName(), runtime.apiKey());
        }
        LlmConfigService.RuntimeConfig config = llmConfigService.runtime();
        if (!config.available()) throw new BusinessException("LLM 尚未在系统设置中启用或未配置 API Key");
        return createModel(config.provider(), config.baseUrl(), config.modelName(), config.apiKey());
    }

    private void warnIfOverridden(AgentDefinition definition) {
        if (definition.getTemperature() != null || definition.getMaxTokens() != null) {
            log.debug("智能体 {} 的 temperature/maxTokens 覆盖项暂不生效（agent4j LLMModel.create 无对应参数）",
                    definition.getCode());
        }
    }

    private LLMModel createModel(String provider, String baseUrl, String modelName, String apiKey) {
        ModelType modelType = switch (provider) {
            case "ANTHROPIC" -> ModelType.Anthropic;
            case "OPENAI_RESPONSES" -> ModelType.OpenAIResponse;
            case "OPENAI_COMPATIBLE" -> ModelType.OpenAI;
            default -> throw new BusinessException("不支持的 LLM 服务类型：" + provider);
        };
        return LLMModel.create(modelType, baseUrl, modelName, apiKey);
    }
}
