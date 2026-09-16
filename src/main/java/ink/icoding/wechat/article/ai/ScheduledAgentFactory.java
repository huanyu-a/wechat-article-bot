package ink.icoding.wechat.article.ai;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.wechat.article.account.WechatAccount;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.agent.AgentDefinition;
import ink.icoding.wechat.article.agent.AgentDefinitionMapper;
import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.agent.ToolRegistry;
import ink.icoding.wechat.article.schedule.AgentRunner;
import ink.icoding.wechat.article.schedule.TaskWorkspace;
import ink.icoding.wechat.article.schedule.ToolCallGovernor;
import ink.icoding.wechat.article.skill.SkillContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 定时链路智能体装配（skills-agent-plan 5.5）：把「按 agent code + 工作区 + 工具组」装配 AgentClient
 * 的公共逻辑集中在此，供 Single/Pipeline/Coordinator 三个执行器复用。
 */
@Component
public class ScheduledAgentFactory {
    private final AgentFactory agentFactory;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final ArticleMediaTools mediaTools;
    private final WechatAccountService wechatAccountService;

    public ScheduledAgentFactory(AgentFactory agentFactory, AgentDefinitionMapper agentDefinitionMapper,
                                 ArticleMediaTools mediaTools,
                                 WechatAccountService wechatAccountService) {
        this.agentFactory = agentFactory;
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.mediaTools = mediaTools;
        this.wechatAccountService = wechatAccountService;
    }

    /** 定时场景 Skill 上下文：任务技能 + 账号技能 + 账号默认风格 + agent 默认技能。 */
    public SkillContext skillContext(Long accountId, List<Long> taskSkillIds, List<Long> agentSkillIds) {
        WechatAccount account = accountId == null ? null : wechatAccountService.required(accountId);
        return new SkillContext(List.of(),
                taskSkillIds == null ? List.of() : taskSkillIds,
                account == null ? List.of() : WechatAccountService.parseSkillIds(account.getSkillIds()),
                agentSkillIds == null ? List.of() : agentSkillIds,
                account == null ? null : account.getDefaultStyle(),
                SkillContext.Scene.SCHEDULED);
    }

    /** 账号级技能（执行器构造上下文用）。 */
    public SkillContext skillContextFor(Long accountId, List<Long> taskSkillIds, AgentDefinition definition) {
        List<Long> agentSkills = definition == null
                ? List.of() : WechatAccountService.parseSkillIds(definition.getSkillIds());
        return skillContext(accountId, taskSkillIds, agentSkills);
    }

    /**
     * 按 code 装配定时链路智能体。
     *
     * @param workspace      共享工作区（草稿/调研/审核）
     * @param delegateTools  DELEGATE 组的工具工厂（仅协调者传入；其余传 null）
     */
    public AgentClient build(String code, String fallbackStage, SkillContext context, TaskWorkspace workspace,
                             Long accountId, Long userId, Function<TaskWorkspace, List<Tool>> delegateTools) {
        return build(code, fallbackStage, context, workspace, accountId, userId, delegateTools,
                new ToolMutationDeduplicator());
    }

    /**
     * 按 code 装配定时链路智能体。
     *
     * @param mediaMutations 媒体工具去重器：同一工作区内的子智能体应共用同一个实例，
     *                       避免重复生图/导入被计费两次（方案 5.5 成本护栏）
     */
    public AgentClient build(String code, String fallbackStage, SkillContext context, TaskWorkspace workspace,
                             Long accountId, Long userId, Function<TaskWorkspace, List<Tool>> delegateTools,
                             ToolMutationDeduplicator mediaMutations) {
        AgentDefinition definition = agentDefinitionMapper.findByCode(code);
        AgentFactory.ToolResolver resolver = groups -> resolveTools(groups, workspace, accountId, userId,
                delegateTools, mediaMutations, null, imageProfileId(definition));
        if (definition != null && Boolean.TRUE.equals(definition.getEnabled())) {
            // 复用已读到的定义，避免 buildByCode 内部再查一次库
            SkillContext effective = skillContextFor(accountId, context.taskSkillIds(), definition);
            return agentFactory.build(definition, effective, resolver);
        }
        return agentFactory.buildByCode(code, fallbackStage, context, resolver);
    }

    /**
     * 装配**故障切换候选**（Phase 1）：首个是主用，其余是「主用模型不可用时」的退路。
     *
     * <p>与 {@link #build} 的唯一区别是返回一组只差模型的 AgentClient（各带档案标签）。
     * 定义缺失/停用时退回单候选，保证存量行为不变。
     *
     * <p>标签由这里透传而不是让调用方从 AgentClient 反查：agent4j 的 {@code LLMModel}
     * 没有暴露模型名的 getter，事后查不出「这一轮用的到底是哪个档案」，而执行日志需要它。
     */
    public List<AgentRunner.Candidate> buildCandidates(String code, String fallbackStage, SkillContext context,
                                                       TaskWorkspace workspace, Long accountId, Long userId,
                                                       Function<TaskWorkspace, List<Tool>> delegateTools,
                                                       ToolMutationDeduplicator mediaMutations) {
        return buildCandidates(code, fallbackStage, context, workspace, accountId, userId, delegateTools,
                mediaMutations, new ToolCallGovernor());
    }

    /**
     * @param governor 只读检索治理器：候选之间共享同一个实例（工具实例也是共享的），
     *                 {@link AgentRunner} 据此在每次尝试时重置预算并中止无进展循环。
     */
    public List<AgentRunner.Candidate> buildCandidates(String code, String fallbackStage, SkillContext context,
                                                       TaskWorkspace workspace, Long accountId, Long userId,
                                                       Function<TaskWorkspace, List<Tool>> delegateTools,
                                                       ToolMutationDeduplicator mediaMutations,
                                                       ToolCallGovernor governor) {
        AgentDefinition definition = agentDefinitionMapper.findByCode(code);
        AgentFactory.ToolResolver resolver = groups -> resolveTools(groups, workspace, accountId, userId,
                delegateTools, mediaMutations, governor, imageProfileId(definition));
        if (definition != null && Boolean.TRUE.equals(definition.getEnabled())) {
            SkillContext effective = skillContextFor(accountId, context.taskSkillIds(), definition);
            return agentFactory.buildLabeledCandidates(definition, effective, null, resolver).stream()
                    .map(labeled -> new AgentRunner.Candidate(labeled.agent(), labeled.label(), governor))
                    .toList();
        }
        return List.of(new AgentRunner.Candidate(
                agentFactory.buildByCode(code, fallbackStage, context, resolver), fallbackStage, governor));
    }

    /**
     * 配图用的模型档案 id：取该智能体绑定的档案；未绑定或定义不可用时返回 null（配图回落全局设置）。
     *
     * <p>为什么未绑定时不替它选默认档案：{@code imageCarrier} 会沿整条故障切换链找，
     * 传 null 与传默认档案的结果在「默认档案没声明图片模型」时完全一致，
     * 而传 null 少一次查库、也少一处「谁才是默认」的重复判断。
     *
     * <p>为什么定义停用时也返回 null：停用的智能体走的是内置兜底装配（工具组由 stage 决定），
     * 那条路径上它已经不是「这个智能体」了，沿用它的绑定会让「停用」这个动作只生效一半。
     */
    private static Long imageProfileId(AgentDefinition definition) {
        if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) return null;
        return definition.getLlmProfileId();
    }

    /** 工具组 → 工具实例。 */
    public List<Tool> resolveTools(List<String> groups, TaskWorkspace workspace, Long accountId, Long userId,
                                   Function<TaskWorkspace, List<Tool>> delegateTools,
                                   ToolMutationDeduplicator mediaMutations) {
        return resolveTools(groups, workspace, accountId, userId, delegateTools, mediaMutations, null, null);
    }

    /** 工具组 → 工具实例（可带只读检索治理器，见 {@link ToolCallGovernor}）。 */
    public List<Tool> resolveTools(List<String> groups, TaskWorkspace workspace, Long accountId, Long userId,
                                   Function<TaskWorkspace, List<Tool>> delegateTools,
                                   ToolMutationDeduplicator mediaMutations, ToolCallGovernor governor) {
        return resolveTools(groups, workspace, accountId, userId, delegateTools, mediaMutations, governor, null);
    }

    /**
     * 工具组 → 工具实例（可带只读检索治理器与配图档案，见 {@link ToolCallGovernor}）。
     *
     * @param imageProfileId 发起配图的智能体所绑定的模型档案，可为 null（回落全局图片设置）
     */
    public List<Tool> resolveTools(List<String> groups, TaskWorkspace workspace, Long accountId, Long userId,
                                   Function<TaskWorkspace, List<Tool>> delegateTools,
                                   ToolMutationDeduplicator mediaMutations, ToolCallGovernor governor,
                                   Long imageProfileId) {
        List<Tool> tools = new ArrayList<>();
        if (groups == null) return tools;
        if (groups.contains(ToolRegistry.DRAFT_READ) && !groups.contains(ToolRegistry.DRAFT_WRITE)) {
            // DRAFT_READ 单独授权 = 只读语义（协调者/审稿人），方案 5.3 工具组拆分
            tools.addAll(ScheduledArticleTools.readOnly(workspace.draftState()));
        } else if (groups.contains(ToolRegistry.DRAFT_READ) || groups.contains(ToolRegistry.DRAFT_WRITE)) {
            tools.addAll(ScheduledArticleTools.all(workspace.draftState()));
        }
        if (groups.contains(ToolRegistry.RESEARCH)) {
            tools.addAll(TaskWorkspaceTools.research(workspace));
        }
        if (groups.contains(ToolRegistry.REVIEW)) {
            tools.addAll(TaskWorkspaceTools.review(workspace));
        }
        if (groups.contains(ToolRegistry.MEDIA)) {
            // 与编辑器链路一致：同参数重复调用复用首次结果，避免重复生图/计费
            tools.addAll(mediaTools.create(accountId, userId,
                    (mediaMutations == null ? new ToolMutationDeduplicator() : mediaMutations)::execute,
                    readExecutor(governor), imageProfileId));
        }
        if (groups.contains(ToolRegistry.DELEGATE) && delegateTools != null) {
            tools.addAll(delegateTools.apply(workspace));
        }
        return tools;
    }

    /**
     * 只读检索治理器 → {@link ArticleMediaTools.ReadExecutor}。
     *
     * <p>治理器为 null（编辑器链路、单测）时返回直通实现：这些调用方要么是用户在场的交互式编辑
     * （重复检索由用户自己叫停），要么根本没有上游调用，套一层缓存只会改变它们的既有行为。
     */
    static ArticleMediaTools.ReadExecutor readExecutor(ToolCallGovernor governor) {
        if (governor == null) return (toolName, paramJson, action) -> action.get();
        return (toolName, paramJson, action) -> governor.execute(toolName, paramJson, action);
    }
}
