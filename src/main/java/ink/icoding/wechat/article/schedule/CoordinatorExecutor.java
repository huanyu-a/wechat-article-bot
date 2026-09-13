package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.DelegateTools;
import ink.icoding.wechat.article.ai.ScheduledAgentFactory;
import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.skill.SkillContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 协调者执行器（skills-agent-plan 5.5 COORDINATOR）：chief 通过 DELEGATE 工具自主委托子智能体。
 *
 * 子智能体在工具内部同步运行（agent4j AgentClientSession 可嵌套，见第③期 Spike 结论），
 * 共享同一 {@link TaskWorkspace}；预算护栏由 {@link DelegateTools.Budget} 代码强制。
 */
@Component
public class CoordinatorExecutor extends ScheduledExecutionStrategy {
    /**
     * 主编会话的默认硬超时（秒）。
     *
     * <p>为什么与全局 {@code stage-timeout-seconds}（300s）不同：主编的一次会话**覆盖全部委托**，
     * 每次委托都是一次完整的子会话（自身还带一次停滞重试，最坏 600s）。实测 run#41/42/47/48 的卡点
     * 全是「主编侧无事件、子智能体正在运行」，300s 一到就把**正常工作中**的主编硬超时杀掉，
     * 整轮 FAILED。1800s 足以覆盖「数次委托 + 一到两次子会话停滞重试」，同时仍给失控会话封顶。
     */
    public static final long DEFAULT_CHIEF_TIMEOUT_SECONDS = 1800L;

    private final ScheduledAgentFactory agentFactory;
    private final AgentRunner runner;
    private final ToolCallBudget toolCallBudget;
    private final long chiefTimeoutSeconds;

    public CoordinatorExecutor(ScheduledAgentFactory agentFactory, AgentRunner runner,
                               ToolCallBudget toolCallBudget,
                               @Value("${app.schedule.coordinator-timeout-seconds:"
                                       + DEFAULT_CHIEF_TIMEOUT_SECONDS + "}") long chiefTimeoutSeconds) {
        this.agentFactory = agentFactory;
        this.runner = runner;
        this.toolCallBudget = toolCallBudget == null ? ToolCallBudget.defaults() : toolCallBudget;
        this.chiefTimeoutSeconds = chiefTimeoutSeconds > 0 ? chiefTimeoutSeconds : DEFAULT_CHIEF_TIMEOUT_SECONDS;
    }

    @Override
    public String mode() {
        return "COORDINATOR";
    }

    @Override
    public ArticleAiService.ScheduledAgentResult execute(ArticleAiService.ScheduledAgentRequest request,
                                                         TaskWorkspace workspace) throws Exception {
        // 日志写在工作区上（不是局部变量）：阶段失败时局部变量随异常丢弃，运行历史就只剩一行错误
        List<String> executionLog = workspace.executionLog();
        int maxRounds = request.maxRevisionRounds() == null ? 2 : Math.max(0, request.maxRevisionRounds());

        // 整轮共用同一个媒体去重器：chief 与所有子智能体重复请求同一张图/同一次生图只执行一次
        ink.icoding.wechat.article.ai.ToolMutationDeduplicator mediaMutations =
                new ink.icoding.wechat.article.ai.ToolMutationDeduplicator();
        // 子智能体运行器工厂：按 code/stage 装配并同步运行，复用同一工作区与预算日志
        DelegateTools.SubAgentRunner runnerProxy = (code, stage, command, logPrefix) -> {
            SkillContext context = agentFactory.skillContext(request.accountId(), request.skillIds(), List.of());
            AgentClient subAgent = agentFactory.build(code, stage, context, workspace, request.accountId(),
                    request.userId(), null, mediaMutations);
            executionLog.add(logPrefix + "启动子智能体：" + subAgent.getName());
            // 子智能体的工具计数与日志都**实时**汇入共享工作区：此前只实时上报计数、日志由 DelegateTools
            // 事后整体追加，子智能体停滞/超时的那次尝试其局部日志会随异常丢弃（I6 现状 a），
            // 失败后只剩一句引导文本。改为全量 progressListener 后，卡在哪一步在运行历史里也看得到。
            AgentRunner.Outcome outcome = runner.runWithLimit(subAgent, command, null, logPrefix,
                    toolCallBudget.subAgentLimitFor(stage), workspace.progressListener());
            // 子智能体的工具失败也要计入运行级失败数（终态判定见 TaskWorkspace.toolFailureCount）
            workspace.addToolFailures(outcome.toolFailures());
            // 日志已随 progressListener 实时落盘；这里清空 executionLog 返回，避免 DelegateTools.runSubAgent
            // 再 addAll 一次造成同一行记两遍（这正是原 toolCallsOnly 用意的替代实现）。
            return new AgentRunner.Outcome(outcome.reply(), outcome.toolCalls(), "", outcome.toolFailures());
        };

        List<Tool> delegateTools = DelegateTools.create(workspace, maxRounds,
                (code, stage) -> runnerProxy, executionLog, toolCallBudget);

        SkillContext chiefContext = agentFactory.skillContext(request.accountId(), request.skillIds(), List.of());
        AgentClient chief = agentFactory.build(AgentFactory.CODE_CHIEF, "COORDINATE", chiefContext, workspace,
                request.accountId(), request.userId(), ws -> delegateTools, mediaMutations);
        executionLog.add("【协调】启动主编智能体：" + chief.getName());

        String command = """
                当前时间：%s
                目标公众号：%s
                任务完成后的系统动作：%s
                交付约束：%s
                返工上限：%d 轮

                本次创作要求：
                %s

                请规划并委托子智能体完成这篇文章，最后确认草稿已落盘（read_article_draft 返回 saved=true）。
                """.formatted(
                java.time.ZonedDateTime.now(java.time.ZoneId.of(request.timezone())),
                request.accountId() == null ? "未指定，仅创建本地文章" : "公众号ID " + request.accountId(),
                request.outputMode(), PipelineExecutor.deliveryRequirement(request), maxRounds,
                request.instruction());

        // chief 同样受工具调用上限约束：读草稿 + ≤8 次委托，超出即中止（防失控）。
        // 会话超时单独放宽到 coordinator-timeout-seconds：这一次会话覆盖全部委托，量级与单阶段不同。
        // 日志实时汇入工作区：chief 停滞/超时时本次尝试的局部日志会随异常丢弃，只有实时上报的留得住。
        AgentRunner.Outcome outcome;
        try {
            outcome = runner.runWithLimit(chief, command, null, "【协调】",
                    toolCallBudget.chiefLimit(), chiefTimeoutSeconds, workspace.progressListener());
        } catch (RuntimeException error) {
            // 主编会话中止但草稿已落盘：文章是完整的，按现有草稿交付而不是整轮失败
            // （否则一次协调层抖动就会把子智能体已经写好的整篇文章丢掉）。
            if (!workspace.draftState().isSaved()) throw error;
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            executionLog.add("【协调】主编会话中止（" + reason + "），草稿已落盘，按现有草稿交付");
            workspace.addDegradation();
            outcome = new AgentRunner.Outcome("", 0, "", 0);
        }
        workspace.addToolFailures(outcome.toolFailures());

        if (!workspace.draftState().isSaved()) {
            throw new IllegalStateException("智能体没有通过 save_article_draft 提交文章");
        }
        String reply = outcome.reply() == null || outcome.reply().isBlank()
                ? "协调者已完成本次文章创作" : outcome.reply();
        return new ArticleAiService.ScheduledAgentResult(workspace.draftState().snapshot(), reply,
                outcome.toolCalls(), String.join("\n", executionLog));
    }
}
