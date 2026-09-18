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
        return assemble(definition, assembleDescription(definition, skillContext, preAssembled),
                resolveTools(definition, resolver), createModel(definition));
    }

    /**
     * 装配**故障切换候选**：返回一组除模型外完全相同的 AgentClient（首个为主用）。
     *
     * <p>为什么要一次建好一组：{@code AgentClient} 在 {@code setModel} 时就绑死了一个模型，
     * 会话失败后无法换模型重试。要让 {@code AgentInvoker} 能「换档案接着跑」，
     * 就必须在装配阶段把候选都准备好。候选之间**只差模型**——工具实例、工作区、
     * 技能提示全部共享（工具不持有模型状态，多建几个 client 是廉价且安全的）。
     *
     * <p>返回长度 ≥ 1：档案链为空（全部未启用/无 Key）时回落到原有的
     * 「默认档案 → llm_config」单候选行为，不改变存量语义。
     */
    public List<AgentClient> buildCandidates(AgentDefinition definition, SkillContext skillContext,
                                             SkillPromptResult preAssembled, ToolResolver resolver) {
        return buildLabeledCandidates(definition, skillContext, preAssembled, resolver).stream()
                .map(LabeledAgent::agent).toList();
    }

    /**
     * 故障切换候选 + 档案标签（首个为主用）。
     *
     * <p>标签必须由装配方给出：agent4j 的 {@code LLMModel} 没有暴露模型名的 getter，
     * 事后无法从 AgentClient 反查「这一轮用的到底是哪个档案」，而执行日志与终态消息都需要它。
     */
    public List<LabeledAgent> buildLabeledCandidates(AgentDefinition definition, SkillContext skillContext,
                                                     SkillPromptResult preAssembled, ToolResolver resolver) {
        if (definition == null) throw new BusinessException("智能体定义不存在");
        if (!Boolean.TRUE.equals(definition.getEnabled())) {
            throw new BusinessException("智能体「" + definition.getName() + "」已停用");
        }
        // 工具与提示只解析一次，候选之间**共享同一批实例**：工具不持有模型状态，
        // 且同一时刻只会有一个候选在跑（切换是串行的），共享既省一次解析也避免
        // resolver 里带副作用的工具（如共享去重器）被重复构造。
        String description = assembleDescription(definition, skillContext, preAssembled);
        List<Tool> tools = resolveTools(definition, resolver);
        List<LabeledAgent> candidates = new java.util.ArrayList<>();
        for (ModelCandidate candidate : createModelCandidates(definition)) {
            candidates.add(new LabeledAgent(
                    assemble(definition, description, tools, candidate.model()), candidate.label()));
        }
        return candidates;
    }

    /** 候选智能体 + 档案标签（label 形如「档案名/模型名」，用于日志与终态消息）。 */
    public record LabeledAgent(AgentClient agent, String label) {
    }

    /**
     * 按 code 装配**故障切换候选**：定义缺失/停用时退化为内置默认装配（与 {@link #buildByCode} 同语义），
     * 而不是抛「智能体定义不存在」。
     *
     * <p>为什么退化路径也要给整条档案链：SINGLE 链路此前调的是 {@code buildByCode}，
     * 定义被停用时会静默回落内置装配并照常运行；若这里改成抛异常或单候选，
     * 就会把「定义缺失」这个存量可容忍状态变成硬失败，或者让它失去故障切换能力。
     */
    public List<LabeledAgent> buildLabeledCandidatesByCode(String code, String fallbackStage,
                                                           SkillContext skillContext,
                                                           SkillPromptResult preAssembled,
                                                           ToolResolver resolver) {
        AgentDefinition definition = definitionMapper.findByCode(code);
        if (definition != null && Boolean.TRUE.equals(definition.getEnabled())) {
            return buildLabeledCandidates(definition, skillContext, preAssembled, resolver);
        }
        log.warn("智能体定义 {} 不存在或已停用，故障切换候选退化为内置默认装配（stage={}）", code, fallbackStage);
        SkillPromptResult skillPrompt = preAssembled == null
                ? skillPromptAssembler.assemble(skillContext) : preAssembled;
        String description = AgentProtocols.byStage(fallbackStage) + skillPrompt.prompt();
        List<Tool> resolved = resolver == null ? List.of() : resolver.resolve(defaultGroupsFor(fallbackStage));
        List<Tool> tools = resolved == null ? List.of() : resolved;
        List<LabeledAgent> candidates = new java.util.ArrayList<>();
        for (ModelCandidate candidate : createModelCandidates(null)) {
            candidates.add(new LabeledAgent(assemble("墨舟智能体", description, tools, candidate.model()),
                    candidate.label()));
        }
        return candidates;
    }

    /** 用给定模型装配（description 与工具在候选间完全一致，只有模型不同）。 */
    private AgentClient assemble(AgentDefinition definition, String description, List<Tool> tools, LLMModel model) {
        return assemble(definition.getName() == null ? "墨舟智能体" : definition.getName(), description, tools, model);
    }

    private AgentClient assemble(String name, String description, List<Tool> tools, LLMModel model) {
        AgentClient agent = new AgentClient();
        agent.setName(name);
        agent.setDescription(description);
        agent.setModel(model);
        agent.setTools(tools);
        return agent;
    }

    private List<Tool> resolveTools(AgentDefinition definition, ToolResolver resolver) {
        if (resolver == null) return List.of();
        List<String> groupKeys = AgentDefinitionService.parseToolKeys(definition.getToolKeys());
        List<Tool> tools = resolver.resolve(groupKeys);
        return tools == null ? List.of() : tools;
    }

    /**
     * 故障切换候选模型（按档案链顺序）。首个与 {@link #createModel} 的选择一致。
     *
     * <p>档案链由 {@link LlmProfileService#failoverChain} 给出（绑定 → 默认 → 兜底 → 其余已启用）。
     * 链为空时退回 {@link #createModel} 的「默认档案 → llm_config」路径，保证存量行为不变。
     */
    public List<ModelCandidate> createModelCandidates(AgentDefinition definition) {
        LlmProfile bound = null;
        if (definition != null && definition.getLlmProfileId() != null) {
            bound = llmProfileService.findById(definition.getLlmProfileId());
            if (bound == null) {
                log.warn("智能体 {} 绑定的模型档案 {} 已删除，按未绑定处理",
                        definition.getCode(), definition.getLlmProfileId());
            }
        }
        List<LlmProfile> chain = llmProfileService.failoverChain(bound);
        List<ModelCandidate> models = new java.util.ArrayList<>(chain.size());
        for (LlmProfile profile : chain) {
            LlmProfileService.RuntimeProfile runtime = llmProfileService.runtime(profile);
            if (runtime == null || !runtime.available()) continue;
            models.add(new ModelCandidate(
                    createModel(runtime.provider(), runtime.baseUrl(), runtime.modelName(), runtime.apiKey()),
                    profile.getName() + "/" + runtime.modelName()));
        }
        if (models.isEmpty()) {
            // 档案链为空（未配置任何可用档案）：回落原有的 llm_config 路径，保持存量兼容
            if (definition != null) warnIfOverridden(definition);
            return List.of(new ModelCandidate(createModel(definition), "llm_config 默认配置"));
        }
        if (definition != null) warnIfOverridden(definition);
        return models;
    }

    /** 候选模型 + 档案标签（label 形如「档案名/模型名」）。 */
    public record ModelCandidate(LLMModel model, String label) {
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
        // 回放守卫（两层，缺一不可）：
        // 1) 网线级兜底——直接改请求报文，能修到「一轮里最后一次工具调用」的 tool 消息
        //    tool_call_id（轮内钩子够不着，详见 ReplayWireNormalizer 的证据链）；
        // 2) 轮内归一化——在工具执行前修历史里的 arguments / content / tool_calls[].id。
        LLMModel model = ReplayWireNormalizer.attach(LLMModel.create(modelType, baseUrl, modelName, apiKey));
        return ToolCallArgumentGuard.wrap(model);
    }
}
