package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.DelegateTools;
import ink.icoding.wechat.article.ai.ScheduledAgentFactory;
import ink.icoding.wechat.article.agent.AgentDefinition;
import ink.icoding.wechat.article.agent.AgentDefinitionMapper;
import ink.icoding.wechat.article.agent.AgentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流水线执行器（skills-agent-plan 5.5）：代码固定编排 调研 → 写作 → 配图（可选）→ 审核（可选，不过则返工）。
 * 无协调层 LLM 开销；每个阶段一个独立 AgentClient 会话，共享同一 {@link TaskWorkspace}。
 * 阶段编排来自 schedule_task.stage_agents；缺省用内置默认 agent；配图/审核可显式跳过。
 */
@Component
public class PipelineExecutor extends ScheduledExecutionStrategy {
    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);
    private static final List<String> IMAGE_KEYWORDS = List.of("配图", "图片", "封面", "视觉", "插图", "图注");

    /** 阶段智能体装配 seam（单测可替换，skills-agent-plan 8.1）。 */
    @FunctionalInterface
    interface StageAgentBuilder {
        AgentClient build(String code, String fallbackStage, ArticleAiService.ScheduledAgentRequest request,
                          TaskWorkspace workspace);
    }

    private final ScheduledAgentFactory agentFactory;
    private final AgentDefinitionMapper definitionMapper;
    private final AgentRunner runner;
    private StageAgentBuilder stageAgentBuilder;

    public PipelineExecutor(ScheduledAgentFactory agentFactory, AgentDefinitionMapper definitionMapper,
                            AgentRunner runner) {
        this.agentFactory = agentFactory;
        this.definitionMapper = definitionMapper;
        this.runner = runner;
        this.stageAgentBuilder = (code, stage, request, workspace) -> {
            var context = agentFactory.skillContext(request.accountId(), request.skillIds(), List.of());
            return agentFactory.build(code, stage, context, workspace, request.accountId(),
                    request.userId(), null);
        };
    }

    /** 单测注入替身装配器。 */
    void setStageAgentBuilder(StageAgentBuilder builder) {
        this.stageAgentBuilder = builder;
    }

    @Override
    public String mode() {
        return "PIPELINE";
    }

    @Override
    public ArticleAiService.ScheduledAgentResult execute(ArticleAiService.ScheduledAgentRequest request,
                                                         TaskWorkspace workspace) throws Exception {
        List<String> executionLog = Collections.synchronizedList(new ArrayList<>());
        int toolCalls = 0;

        // ① 调研
        String researchCode = resolveCode(request.stageAgents(), "research", AgentFactory.CODE_RESEARCHER);
        AgentRunner.Outcome research = runStage(researchCode, "RESEARCH", request, workspace,
                researchCommand(request), "【调研】", executionLog);
        toolCalls += research.toolCalls();
        executionLog.add(workspace.hasResearchNotes()
                ? "【调研】调研简报已落盘（" + workspace.researchNotesText().length() + " 字）"
                : "【调研】未产生调研简报，写作阶段将依据任务要求自行检索");

        // ② 写作
        String writingCode = resolveCode(request.stageAgents(), "writing", AgentFactory.CODE_WRITER);
        AgentRunner.Outcome writing = runStage(writingCode, "WRITING", request, workspace,
                writingCommand(request, workspace, null), "【写作】", executionLog);
        toolCalls += writing.toolCalls();
        requireDraftSaved(workspace, "写作阶段");

        // ③ 配图（可跳过）
        boolean illustrationSkipped = isSkipped(request.stageAgents(), "illustration");
        if (!illustrationSkipped) {
            String illustrationCode = resolveCode(request.stageAgents(), "illustration",
                    AgentFactory.CODE_ILLUSTRATOR);
            AgentRunner.Outcome illustration = runStage(illustrationCode, "ILLUSTRATION", request, workspace,
                    illustrationCommand(request), "【配图】", executionLog);
            toolCalls += illustration.toolCalls();
        } else {
            executionLog.add("【配图】按任务配置跳过");
        }

        // ④ 审核 + 返工环
        boolean reviewSkipped = isSkipped(request.stageAgents(), "review");
        if (!reviewSkipped) {
            String reviewCode = resolveCode(request.stageAgents(), "review", AgentFactory.CODE_REVIEWER);
            int maxRounds = request.maxRevisionRounds() == null ? 2 : Math.max(0, request.maxRevisionRounds());
            int round = 0;
            while (true) {
                int roundsBefore = workspace.reviewRounds().size();
                AgentRunner.Outcome review = runStage(reviewCode, "REVIEW", request, workspace,
                        reviewCommand(request), "【审核】", executionLog);
                toolCalls += review.toolCalls();
                if (workspace.reviewRounds().size() == roundsBefore) {
                    // 本轮未调用 submit_review：按通过处理（宽松策略），不得沿用上一轮结论返工
                    executionLog.add("【审核】第 " + (roundsBefore + 1) + " 轮审稿人未调用 submit_review，按通过处理");
                    break;
                }
                TaskWorkspace.ReviewRound latest = workspace.latestReview();
                if (latest.passed()) {
                    executionLog.add("【审核】第 " + latest.round() + " 轮审核通过");
                    break;
                }
                if (round >= maxRounds) {
                    executionLog.add("【审核】返工已达上限 " + maxRounds + " 轮，仍不通过，按现状交付并记录问题");
                    break;
                }
                round++;
                workspace.nextRevisionRound();
                executionLog.add("【审核】第 " + latest.round() + " 轮未通过，进入第 " + round + " 次返工");
                AgentRunner.Outcome rewrite = runStage(writingCode, "WRITING", request, workspace,
                        writingCommand(request, workspace, workspace.latestIssuesText()), "【写作】", executionLog);
                toolCalls += rewrite.toolCalls();
                requireDraftSaved(workspace, "返工写作阶段");
                if (!illustrationSkipped && issuesMentionImages(latest.issues())) {
                    String illustrationCode = resolveCode(request.stageAgents(), "illustration",
                            AgentFactory.CODE_ILLUSTRATOR);
                    AgentRunner.Outcome illustration = runStage(illustrationCode, "ILLUSTRATION", request,
                            workspace, illustrationCommand(request), "【配图】", executionLog);
                    toolCalls += illustration.toolCalls();
                }
            }
        } else {
            executionLog.add("【审核】按任务配置跳过");
        }

        String reply = "流水线执行完成：" + String.join(" → ",
                reviewSkipped ? List.of("调研", "写作", illustrationSkipped ? "配图(跳过)" : "配图")
                        : List.of("调研", "写作", illustrationSkipped ? "配图(跳过)" : "配图", "审核"));
        return new ArticleAiService.ScheduledAgentResult(workspace.draftState().snapshot(), reply, toolCalls,
                String.join("\n", executionLog));
    }

    private AgentRunner.Outcome runStage(String code, String fallbackStage,
                                         ArticleAiService.ScheduledAgentRequest request, TaskWorkspace workspace,
                                         String command, String logPrefix, List<String> executionLog) {
        AgentClient agent = buildAgent(code, fallbackStage, request, workspace);
        executionLog.add(logPrefix + "启动智能体：" + agent.getName());
        // 每个阶段同样受工具调用上限约束（方案 5.5 预算护栏；超限中止并让任务以明确错误失败）
        AgentRunner.Outcome outcome = runner.runWithLimit(agent, command, null, logPrefix,
                DelegateTools.MAX_SUB_AGENT_TOOL_CALLS);
        executionLog.addAll(splitLines(outcome.executionLog()));
        return outcome;
    }

    /** 装配阶段智能体（包级可见，便于单测经 seam 替换，skills-agent-plan 8.1）。 */
    AgentClient buildAgent(String code, String fallbackStage,
                           ArticleAiService.ScheduledAgentRequest request, TaskWorkspace workspace) {
        return stageAgentBuilder.build(code, fallbackStage, request, workspace);
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return List.of(text.split("\n"));
    }

    private void requireDraftSaved(TaskWorkspace workspace, String stage) {
        if (!workspace.draftState().isSaved()) {
            throw new IllegalStateException(stage + "未提交草稿（save_article_draft 未被调用）");
        }
    }

    private static boolean issuesMentionImages(List<String> issues) {
        if (issues == null || issues.isEmpty()) return false;
        for (String issue : issues) {
            if (issue == null) continue;
            for (String keyword : IMAGE_KEYWORDS) {
                if (issue.contains(keyword)) return true;
            }
        }
        return false;
    }

    /** 阶段是否显式跳过（配置值 0）。research/writing 为必选阶段，配 0 时静默回落内置并记录。 */
    private boolean isSkipped(Map<String, Long> stageAgents, String stage) {
        if (stageAgents == null || !stageAgents.containsKey(stage)) return false;
        Long value = stageAgents.get(stage);
        if (value == null || value == 0L) {
            if ("research".equals(stage) || "writing".equals(stage)) {
                log.warn("阶段 {} 为必选阶段，配置的跳过值 0 已忽略，改用内置智能体", stage);
                return false;
            }
            return true;
        }
        return false;
    }

    /** 阶段 agent 解析：显式 id 优先（失效回落内置并记录），缺省用内置 code。 */
    private String resolveCode(Map<String, Long> stageAgents, String stage, String builtinCode) {
        if (stageAgents == null) return builtinCode;
        Long id = stageAgents.get(stage);
        if (id == null || id <= 0L) return builtinCode;
        AgentDefinition definition = definitionMapper.findById(id);
        if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
            log.warn("阶段 {} 配置的智能体 {} 不存在或已停用，回落内置 {}", stage, id, builtinCode);
            return builtinCode;
        }
        return definition.getCode();
    }

    private String researchCommand(ArticleAiService.ScheduledAgentRequest request) {
        return """
                当前时间：%s
                任务要求：
                %s

                请完成调研：先检索再阅读重要来源，交叉验证关键事实，然后把结构化调研简报通过 save_research_notes 提交。
                """.formatted(java.time.ZonedDateTime.now(java.time.ZoneId.of(request.timezone())),
                request.instruction());
    }

    private String writingCommand(ArticleAiService.ScheduledAgentRequest request, TaskWorkspace workspace,
                                  String issues) {
        StringBuilder command = new StringBuilder();
        command.append("当前时间：").append(java.time.ZonedDateTime.now(java.time.ZoneId.of(request.timezone())))
                .append("\n任务要求：\n").append(request.instruction()).append('\n');
        if (workspace != null && workspace.hasResearchNotes()) {
            // 调研简报必须传给撰稿人（方案 5.5：写作指令 = 任务要求 + 调研简报 + 技能注入）
            command.append("\n调研简报（由调研员落盘，事实与来源以此为准）：\n")
                    .append(workspace.researchNotesText()).append('\n');
        }
        if (issues != null && !issues.isBlank()) {
            command.append("\n上一稿审核意见（必须逐条修改后重新提交）：\n").append(issues);
        }
        command.append("""

                交付约束：%s

                请撰写完整文章并通过 save_article_draft 提交；写作前先调用 read_article_draft 查看当前草稿状态。
                """.formatted(deliveryRequirement(request)));
        return command.toString();
    }

    private String illustrationCommand(ArticleAiService.ScheduledAgentRequest request) {
        return """
                任务要求：
                %s

                请读取当前草稿，为正文选择或生成合适的配图并插入语义合适的位置，同时设置封面，然后重新提交完整草稿。
                """.formatted(request.instruction());
    }

    private String reviewCommand(ArticleAiService.ScheduledAgentRequest request) {
        return """
                任务要求：
                %s

                请读取当前草稿完成审核（事实准确性、结构、写作风格与技能符合度、排版与图片合规），
                并通过 submit_review 提交结构化结论。
                """.formatted(request.instruction());
    }

    static String deliveryRequirement(ArticleAiService.ScheduledAgentRequest request) {
        return "LOCAL_DRAFT".equals(request.outputMode())
                ? "保存为本地草稿；封面可按内容需要设置"
                : "将由系统同步或发布到微信；必须在提交文章前选择、导入或生成合适图片，并调用set_article_draft_cover设置封面";
    }
}
