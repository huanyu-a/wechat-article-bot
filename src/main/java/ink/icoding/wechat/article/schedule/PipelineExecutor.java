package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.ScheduledAgentFactory;
import ink.icoding.wechat.article.ai.ToolMutationDeduplicator;
import ink.icoding.wechat.article.agent.AgentDefinition;
import ink.icoding.wechat.article.agent.AgentDefinitionMapper;
import ink.icoding.wechat.article.agent.AgentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    /** 阶段智能体装配 seam（单测可替换，skills-agent-plan 8.1）：返回**故障切换候选**，首个为主用。 */
    @FunctionalInterface
    interface StageAgentBuilder {
        List<AgentRunner.Candidate> build(String code, String fallbackStage,
                                          ArticleAiService.ScheduledAgentRequest request,
                                          TaskWorkspace workspace);
    }

    private final ScheduledAgentFactory agentFactory;
    private final AgentDefinitionMapper definitionMapper;
    private final AgentRunner runner;
    private final ToolCallBudget toolCallBudget;
    private StageAgentBuilder stageAgentBuilder;

    public PipelineExecutor(ScheduledAgentFactory agentFactory, AgentDefinitionMapper definitionMapper,
                            AgentRunner runner, ToolCallBudget toolCallBudget) {
        this.agentFactory = agentFactory;
        this.definitionMapper = definitionMapper;
        this.runner = runner;
        this.toolCallBudget = toolCallBudget == null ? ToolCallBudget.defaults() : toolCallBudget;
        this.stageAgentBuilder = (code, stage, request, workspace) -> {
            var context = agentFactory.skillContext(request.accountId(), request.skillIds(), List.of());
            // 每个阶段一个独立的检索治理器：阶段之间不共享缓存（不同阶段该看到各自的新检索结果），
            // 而阶段内部的重复调用正是要治理的对象。
            return agentFactory.buildCandidates(code, stage, context, workspace, request.accountId(),
                    request.userId(), null, new ToolMutationDeduplicator(), new ToolCallGovernor());
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
        // 日志写在工作区上（不是局部变量）：阶段失败时局部变量随异常丢弃，运行历史就只剩一行错误
        List<String> executionLog = workspace.executionLog();
        int toolCalls = 0;

        // ① 调研（可降级：没有简报时写作阶段会自行检索，不该因此让整轮失败）
        String researchCode = resolveCode(request.stageAgents(), "research", AgentFactory.CODE_RESEARCHER);
        AgentRunner.Outcome research = runStageOrContinue(researchCode, "RESEARCH", request, workspace,
                researchCommand(request), "【调研】", executionLog, null);
        toolCalls += research.toolCalls();
        executionLog.add(workspace.hasResearchNotes()
                ? "【调研】调研简报已落盘（" + workspace.researchNotesText().length() + " 字）"
                : "【调研】未产生调研简报，写作阶段将依据任务要求自行检索");

        // ② 写作（可降级但须已落盘：会话中止前若已调用 save_article_draft，草稿是完整的，不该丢弃）
        String writingCode = resolveCode(request.stageAgents(), "writing", AgentFactory.CODE_WRITER);
        AgentRunner.Outcome writing = runStageOrContinue(writingCode, "WRITING", request, workspace,
                writingCommand(request, workspace, null), "【写作】", executionLog,
                () -> workspace.draftState().isSaved());
        toolCalls += writing.toolCalls();
        requireDraftSaved(workspace, "写作阶段");

        // ③ 配图（可跳过；失败可降级：没有配图的草稿仍可交付）
        boolean illustrationSkipped = isSkipped(request.stageAgents(), "illustration");
        if (!illustrationSkipped) {
            String illustrationCode = resolveCode(request.stageAgents(), "illustration",
                    AgentFactory.CODE_ILLUSTRATOR);
            AgentRunner.Outcome illustration = runStageOrContinue(illustrationCode, "ILLUSTRATION", request,
                    workspace, illustrationCommand(request), "【配图】", executionLog,
                    () -> workspace.draftState().isSaved());
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
                // 审核阶段可降级：审稿人会话中止时按「未提交结论」处理（宽松策略），
                // 不能让一次审稿失败把已经写好的文章整轮丢掉。
                AgentRunner.Outcome review = runStageOrContinue(reviewCode, "REVIEW", request, workspace,
                        reviewCommand(request), "【审核】", executionLog, null);
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
                AgentRunner.Outcome rewrite = runStageOrContinue(writingCode, "WRITING", request, workspace,
                        writingCommand(request, workspace, workspace.latestIssuesText()), "【写作】",
                        executionLog, () -> workspace.draftState().isSaved());
                toolCalls += rewrite.toolCalls();
                requireDraftSaved(workspace, "返工写作阶段");
                if (!illustrationSkipped && issuesMentionImages(latest.issues())) {
                    String illustrationCode = resolveCode(request.stageAgents(), "illustration",
                            AgentFactory.CODE_ILLUSTRATOR);
                    AgentRunner.Outcome illustration = runStageOrContinue(illustrationCode, "ILLUSTRATION",
                            request, workspace, illustrationCommand(request), "【配图】", executionLog,
                            () -> workspace.draftState().isSaved());
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
        List<AgentRunner.Candidate> candidates = buildCandidates(code, fallbackStage, request, workspace);
        executionLog.add(logPrefix + "启动智能体：" + candidates.get(0).agent().getName()
                + AgentRunner.profileSuffix(candidates));
        // 每个阶段受工具调用上限约束（方案 5.5 预算护栏；超限中止并让任务以明确错误失败）。
        // 额度按阶段取：调研阶段高于写作/配图/审核（见 ToolCallBudget——实测宽口径调研的正常检索量
        // 就会超过 24 次，此前正是调查阶段触顶让整轮任务失败）。
        // 计数与日志都经 progressListener 实时汇入工作区：阶段因停滞/超时失败时，
        // 本次尝试的局部日志会随异常丢弃，只有实时上报的那份留得住（运行历史据此定位卡点）。
        // 走 runWithCandidates（而非 runWithLimit）：模型级错误时由 AgentInvoker 换下一个档案接着跑。
        long startedAt = System.nanoTime();
        try {
            AgentRunner.Outcome outcome = runner.runWithCandidates(candidates, command, null, logPrefix,
                    toolCallBudget.subAgentLimitFor(fallbackStage), 0, workspace.progressListener());
            // 阶段内的工具失败计入运行级失败数：流水线即使跑完，也不该把「配图失败」记成干净的成功
            workspace.addToolFailures(outcome.toolFailures());
            workspace.addProfilesUsed(outcome.profilesUsed());
            workspace.recordStage(fallbackStage, elapsedMillis(startedAt), outcome.toolCalls(),
                    outcome.profilesUsed());
            return outcome;
        } catch (RuntimeException failure) {
            // 失败路径同样记一段耗时：阶段中止时「卡了多久、调了几次工具」正是排查的起点，
            // 只记成功路径会让最该看的那几类运行反而没有阶段记录。
            workspace.recordStage(fallbackStage, elapsedMillis(startedAt), workspace.toolCallCount(), List.of());
            throw failure;
        }
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    /**
     * 跑一个「失败可降级」的阶段：阶段会话中止（工具调用超限 / SSE 停滞 / 硬超时）时，
     * 只要已产出物仍可用（{@code salvageable}）就记录降级并继续后续阶段，而不是让整轮运行失败。
     *
     * <p>为什么必须降级：一次 PIPELINE 运行的价值集中在「文章本身」。调研阶段触顶就让整轮失败，
     * 等于把已经能交付的文章（写作阶段尚未开始）连同调研结果一起丢掉——实测 run#46 正是如此：
     * 调研的 25 次检索全部成功，只因超出上限 1 次就整轮 FAILED。
     *
     * <p>降级是**可见**的：写入执行日志 + 计入 {@link TaskWorkspace#degradationCount()}，
     * 运行终态因此是 SUCCESS_WITH_WARNINGS 而不是干净的 SUCCESS。
     *
     * @param salvageable 失败后可继续的判据；返回 false 时原样抛出（真正不可恢复的失败不掩埋）
     */
    private AgentRunner.Outcome runStageOrContinue(String code, String fallbackStage,
                                                  ArticleAiService.ScheduledAgentRequest request,
                                                  TaskWorkspace workspace, String command, String logPrefix,
                                                  List<String> executionLog,
                                                  java.util.function.BooleanSupplier salvageable) {
        try {
            return runStage(code, fallbackStage, request, workspace, command, logPrefix, executionLog);
        } catch (RuntimeException error) {
            if (salvageable != null && !salvageable.getAsBoolean()) throw error;
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            executionLog.add(logPrefix + "阶段中止（" + reason + "），已按现有产出继续后续阶段");
            workspace.addDegradation();
            return new AgentRunner.Outcome("", 0, "", 0);
        }
    }

    /** 装配阶段智能体候选（包级可见，便于单测经 seam 替换，skills-agent-plan 8.1）。 */
    List<AgentRunner.Candidate> buildCandidates(String code, String fallbackStage,
                                                ArticleAiService.ScheduledAgentRequest request,
                                                TaskWorkspace workspace) {
        return stageAgentBuilder.build(code, fallbackStage, request, workspace);
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
