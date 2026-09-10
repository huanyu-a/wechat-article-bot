package ink.icoding.wechat.article.ai;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.wechat.article.account.WechatAccount;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.agent.AgentDefinition;
import ink.icoding.wechat.article.agent.AgentDefinitionMapper;
import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.agent.ToolRegistry;
import ink.icoding.wechat.article.schedule.TaskWorkspace;
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
                delegateTools, mediaMutations);
        if (definition != null && Boolean.TRUE.equals(definition.getEnabled())) {
            // 复用已读到的定义，避免 buildByCode 内部再查一次库
            SkillContext effective = skillContextFor(accountId, context.taskSkillIds(), definition);
            return agentFactory.build(definition, effective, resolver);
        }
        return agentFactory.buildByCode(code, fallbackStage, context, resolver);
    }

    /** 工具组 → 工具实例。 */
    public List<Tool> resolveTools(List<String> groups, TaskWorkspace workspace, Long accountId, Long userId,
                                   Function<TaskWorkspace, List<Tool>> delegateTools,
                                   ToolMutationDeduplicator mediaMutations) {
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
                    (mediaMutations == null ? new ToolMutationDeduplicator() : mediaMutations)::execute));
        }
        if (groups.contains(ToolRegistry.DELEGATE) && delegateTools != null) {
            tools.addAll(delegateTools.apply(workspace));
        }
        return tools;
    }
}
