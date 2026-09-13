package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.entity.MemoryMultipartFile;
import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.skill.LayoutEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PipelineExecutor 编排单测（skills-agent-plan 8.1）：经 {@link AgentRunner} + {@link StageAgentBuilder}
 * 双 seam 桩掉真实 agent4j 调用，只验证阶段顺序、跳过规则与返工环熔断。
 */
class PipelineExecutorTest {
    private static final String RESEARCH_CODE = AgentFactory.CODE_RESEARCHER;
    private static final String WRITING_CODE = AgentFactory.CODE_WRITER;
    private static final String ILLUSTRATION_CODE = AgentFactory.CODE_ILLUSTRATOR;
    private static final String REVIEW_CODE = AgentFactory.CODE_REVIEWER;

    /** 桩运行器：按 agent code 记录阶段，并触发预设副作用。 */
    private static class StubRunner extends AgentRunner {
        final List<String> stages = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        private final TaskWorkspace workspace;
        private final java.util.function.Function<String, String> effects;
        int toolCallsPerRun = 3;

        StubRunner(TaskWorkspace workspace, java.util.function.Function<String, String> effects) {
            this.workspace = workspace;
            this.effects = effects;
        }

        @Override
        public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments) {
            return run(agent, command, attachments, null);
        }

        @Override
        public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                           String logPrefix) {
            String code = agent.getName();
            stages.add(code);
            commands.add(command);
            String forced = effects == null ? null : effects.apply(code);
            if (forced != null) return new Outcome(forced, toolCallsPerRun, logPrefix + "stub-" + code);
            return new Outcome("完成", toolCallsPerRun, logPrefix + "stub-" + code);
        }
    }

    private static PipelineExecutor executor(StubRunner runner) {
        PipelineExecutor executor = new PipelineExecutor(null, null, runner, ToolCallBudget.defaults());
        executor.setStageAgentBuilder((code, stage, request, workspace) -> {
            AgentClient agent = new AgentClient();
            agent.setName(code);
            agent.setDescription(stage);
            return agent;
        });
        return executor;
    }

    private static ArticleAiService.ScheduledAgentRequest request(Map<String, Long> stageAgents, int maxRounds) {
        return new ArticleAiService.ScheduledAgentRequest(1L, 1L, 9L, "Asia/Shanghai", "LOCAL_DRAFT",
                "写一篇关于 AI 的文章", List.of(), stageAgents, maxRounds);
    }

    private static void saveDraft(TaskWorkspace workspace) {
        ScheduledArticleTools.SaveDraftParam param = new ScheduledArticleTools.SaveDraftParam();
        param.setTitle("测试标题");
        param.setContentHtml("<p>正文自然段</p>");
        workspace.draftState().save(param);
    }

    @Test
    void fullPipelineRunsFourStagesInOrder() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace, code -> {
            if (RESEARCH_CODE.equals(code)) {
                workspace.appendResearchNotes("核心结论：AI 正在普及", "第一轮");
            } else if (WRITING_CODE.equals(code)) {
                saveDraft(workspace);
            } else if (REVIEW_CODE.equals(code)) {
                workspace.submitReview(true, List.of(), List.of(), "通过");
            }
            return null;
        });
        ArticleAiService.ScheduledAgentResult result = executor(runner)
                .execute(request(Map.of(), 2), workspace);

        assertThat(runner.stages).containsExactly(RESEARCH_CODE, WRITING_CODE, ILLUSTRATION_CODE, REVIEW_CODE);
        assertThat(result.executionLog()).contains("【调研】").contains("【写作】")
                .contains("【配图】").contains("【审核】").contains("调研简报已落盘");
        assertThat(result.draft().title()).isEqualTo("测试标题");
        assertThat(result.toolCalls()).isEqualTo(4 * runner.toolCallsPerRun);
    }

    @Test
    void writingCommandInjectsDeliveryConstraint() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace, code -> {
            if (WRITING_CODE.equals(code)) saveDraft(workspace);
            if (REVIEW_CODE.equals(code)) workspace.submitReview(true, List.of(), List.of(), "ok");
            return null;
        });
        executor(runner).execute(request(Map.of("illustration", 0L, "review", 0L), 2), workspace);

        assertThat(runner.stages).containsExactly(RESEARCH_CODE, WRITING_CODE);
        String writingCommand = runner.commands.get(1);
        assertThat(writingCommand).contains("保存为本地草稿").contains("写一篇关于 AI 的文章");
    }

    @Test
    void writingCommandInjectsResearchNotes() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace, code -> {
            if (RESEARCH_CODE.equals(code)) {
                workspace.appendResearchNotes("核心结论：AI 行业增长 30%", "第一轮");
            } else if (WRITING_CODE.equals(code)) {
                saveDraft(workspace);
            }
            return null;
        });
        executor(runner).execute(request(Map.of("illustration", 0L, "review", 0L), 2), workspace);

        String writingCommand = runner.commands.get(1);
        assertThat(writingCommand).contains("调研简报").contains("核心结论：AI 行业增长 30%");
    }

    @Test
    void skippedStagesAreNotRun() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace, code -> {
            if (WRITING_CODE.equals(code)) saveDraft(workspace);
            return null;
        });
        ArticleAiService.ScheduledAgentResult result = executor(runner)
                .execute(request(Map.of("illustration", 0L, "review", 0L), 2), workspace);

        assertThat(runner.stages).containsExactly(RESEARCH_CODE, WRITING_CODE);
        assertThat(result.executionLog()).contains("【配图】按任务配置跳过").contains("【审核】按任务配置跳过");
    }

    @Test
    void reviewFailureTriggersRewriteUntilMaxRounds() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        List<Integer> reviewCalls = new ArrayList<>();
        StubRunner runner = new StubRunner(workspace, code -> {
            if (WRITING_CODE.equals(code)) {
                saveDraft(workspace);
            } else if (REVIEW_CODE.equals(code)) {
                int round = reviewCalls.size() + 1;
                reviewCalls.add(round);
                // 前两轮不通过，第三轮通过
                boolean passed = round >= 3;
                workspace.submitReview(passed, passed ? List.of() : List.of("第" + round + "轮：数据来源缺失"),
                        List.of(), "审核结论");
            }
            return null;
        });
        ArticleAiService.ScheduledAgentResult result = executor(runner)
                .execute(request(Map.of("illustration", 0L), 2), workspace);

        // 写作 1 次 + 审核 3 次 + 返工写作 2 次
        long writingRuns = runner.stages.stream().filter(WRITING_CODE::equals).count();
        long reviewRuns = runner.stages.stream().filter(REVIEW_CODE::equals).count();
        assertThat(writingRuns).isEqualTo(3);
        assertThat(reviewRuns).isEqualTo(3);
        assertThat(workspace.revisionRound()).isEqualTo(2);
        assertThat(result.executionLog()).contains("返工").contains("审核通过");
    }

    @Test
    void reviewFailureAtMaxRoundsStopsWithoutFurtherRewrite() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace, code -> {
            if (WRITING_CODE.equals(code)) {
                saveDraft(workspace);
            } else if (REVIEW_CODE.equals(code)) {
                workspace.submitReview(false, List.of("永远不通过"), List.of(), "拒绝");
            }
            return null;
        });
        ArticleAiService.ScheduledAgentResult result = executor(runner)
                .execute(request(Map.of("illustration", 0L), 1), workspace);

        long writingRuns = runner.stages.stream().filter(WRITING_CODE::equals).count();
        // maxRounds=1：写作 1 次 + 返工 1 次；审核 2 次（第 2 次达上限即停）
        assertThat(writingRuns).isEqualTo(2);
        assertThat(result.executionLog()).contains("返工已达上限 1 轮");
    }

    @Test
    void reviewWithoutSubmitReviewTreatedAsPassed() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace, code -> {
            if (WRITING_CODE.equals(code)) saveDraft(workspace);
            return null; // 审稿人不调用 submit_review
        });
        ArticleAiService.ScheduledAgentResult result = executor(runner)
                .execute(request(Map.of("illustration", 0L), 2), workspace);

        assertThat(result.executionLog()).contains("未调用 submit_review，按通过处理");
        assertThat(runner.stages).containsExactly(RESEARCH_CODE, WRITING_CODE, REVIEW_CODE);
    }

    @Test
    void stageRunsAreCappedByToolLimit() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        List<Integer> limits = new ArrayList<>();
        List<String> researchLimits = new ArrayList<>();
        List<String> otherLimits = new ArrayList<>();
        AgentRunner capped = new AgentRunner() {
            @Override
            public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments) {
                return run(agent, command, attachments, null);
            }

            @Override
            public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                               String logPrefix) {
                return new Outcome("完成", 1, "");
            }

            @Override
            public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                        String logPrefix, int maxToolCalls) {
                limits.add(maxToolCalls);
                if (isResearcher(agent)) researchLimits.add(agent.getName());
                else otherLimits.add(agent.getName());
                if (ink.icoding.wechat.article.agent.AgentFactory.CODE_WRITER.equals(agent.getName())) {
                    saveDraft(workspace);
                }
                if (ink.icoding.wechat.article.agent.AgentFactory.CODE_REVIEWER.equals(agent.getName())) {
                    workspace.submitReview(true, List.of(), List.of(), "ok");
                }
                return new Outcome("完成", 1, "");
            }
        };
        PipelineExecutor executor = new PipelineExecutor(null, null, capped, ToolCallBudget.defaults());
        executor.setStageAgentBuilder((code, stage, request, ws) -> {
            AgentClient agent = new AgentClient();
            agent.setName(code);
            agent.setDescription(stage);
            return agent;
        });
        executor.execute(request(Map.of(), 2), workspace);

        // 额度按阶段分档：调研阶段高于写作/配图/审核——实测宽口径调研 25 次调用全部成功却因
        // 撞上 24 的上限让整轮 PIPELINE 失败（run#46），调研必须单独放宽。
        assertThat(researchLimits).containsExactly(RESEARCH_CODE);
        assertThat(otherLimits).containsExactly(WRITING_CODE, ILLUSTRATION_CODE, REVIEW_CODE);
        assertThat(limits).isNotEmpty().allMatch(value -> value == ToolCallBudget.DEFAULT_RESEARCH
                || value == ToolCallBudget.DEFAULT_STAGE);
        assertThat(ToolCallBudget.DEFAULT_RESEARCH).isGreaterThan(ToolCallBudget.DEFAULT_STAGE);
    }

    private static boolean isResearcher(AgentClient agent) {
        return RESEARCH_CODE.equals(agent.getName());
    }

    @Test
    void writingWithoutSavedDraftFails() {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace, code -> null); // 写作阶段不存草稿
        assertThatThrownBy(() -> executor(runner).execute(request(Map.of("illustration", 0L, "review", 0L), 2),
                workspace))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未提交草稿");
    }

    @Test
    void stageFailureKeepsPartialExecutionLogInWorkspace() {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace, code -> null); // 写作阶段不存草稿 → 阶段失败

        assertThatThrownBy(() -> executor(runner).execute(request(Map.of("illustration", 0L, "review", 0L), 2),
                workspace))
                .isInstanceOf(IllegalStateException.class);

        // 日志若留在执行器局部变量里会随异常丢弃，运行历史只剩一行错误、看不出失败在哪个阶段；
        // 失败路径必须能从工作区取到已产生的日志（TaskExecutionService 据此落库）
        assertThat(workspace.executionLogText()).contains("【调研】").contains("【写作】");
    }

    @Test
    void runnerFailureKeepsTheStalledStageLogInWorkspace() {
        // 事故现场：阶段因为 SSE 停滞/硬超时抛异常。若日志只在运行器返回后才整体追加，
        // 这一次尝试的日志会随异常一起丢掉——运行历史里 EXECUTION_LOG 为空，看不出卡在哪一步。
        // 调研阶段现在会降级，因此这里观察的是「写作阶段（草稿未落盘、无法降级）的失败」：
        // 调研阶段实时上报的那一行日志同样必须留在工作区里。
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        AgentRunner stalled = new AgentRunner() {
            @Override
            public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments) {
                return run(agent, command, attachments, null);
            }

            @Override
            public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                               String logPrefix) {
                throw new StageTimeoutException("智能体会话超时（300 秒未结束）");
            }

            @Override
            public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                        String logPrefix, int maxToolCalls, ProgressListener progress) {
                // 会话在停滞前已经调用过工具并产生了日志：这些必须已经落到工作区
                progress.toolCallCounted(1);
                progress.logLine("【调研】调用工具：search_web");
                throw new StageTimeoutException("智能体会话超时（300 秒未结束）");
            }
        };
        PipelineExecutor executor = new PipelineExecutor(null, null, stalled, ToolCallBudget.defaults());
        executor.setStageAgentBuilder((code, stage, request, ws) -> {
            AgentClient agent = new AgentClient();
            agent.setName(code);
            return agent;
        });

        assertThatThrownBy(() -> executor.execute(request(Map.of("illustration", 0L, "review", 0L), 2), workspace))
                .isInstanceOf(StageTimeoutException.class);

        assertThat(workspace.executionLogText()).contains("【调研】调用工具：search_web");
        // 调研阶段降级后写作阶段同样跑起来并上报了 1 次调用：两次「抛异常前已发生的计数」都必须留住，
        // 这正是失败路径不能只靠返回值统计的原因。
        assertThat(workspace.toolCallCount()).isEqualTo(2);
    }

    @Test
    void imageIssuesTriggerIllustrationRerun() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        List<Integer> reviewCalls = new ArrayList<>();
        StubRunner runner = new StubRunner(workspace, code -> {
            if (WRITING_CODE.equals(code)) {
                saveDraft(workspace);
            } else if (REVIEW_CODE.equals(code)) {
                int round = reviewCalls.size() + 1;
                reviewCalls.add(round);
                workspace.submitReview(round >= 2, round >= 2 ? List.of() : List.of("配图位置不当"), List.of(), "x");
            }
            return null;
        });
        executor(runner).execute(request(Map.of(), 2), workspace);

        long illustrationRuns = runner.stages.stream().filter(ILLUSTRATION_CODE::equals).count();
        // 首轮配图 1 次 + 因图片类 issues 返工再配图 1 次
        assertThat(illustrationRuns).isEqualTo(2);
    }

    /** 指定 code 的智能体会话直接中止（other 阶段照常完成并落盘草稿）。 */
    private static AgentRunner stageDiesWith(String dyingCode, boolean saveDraftBeforeDying, TaskWorkspace workspace) {
        return new AgentRunner() {
            @Override
            public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments) {
                return run(agent, command, attachments, null);
            }

            @Override
            public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                               String logPrefix) {
                throw new IllegalStateException("会话中止（模拟超限/停滞）");
            }

            @Override
            public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                        String logPrefix, int maxToolCalls, ProgressListener progress) {
                if (dyingCode.equals(agent.getName())) {
                    if (saveDraftBeforeDying) saveDraft(workspace);
                    throw new IllegalStateException("会话中止（模拟超限/停滞）");
                }
                if (WRITING_CODE.equals(agent.getName())) saveDraft(workspace);
                if (REVIEW_CODE.equals(agent.getName())) {
                    workspace.submitReview(true, List.of(), List.of(), "ok");
                }
                return new Outcome("完成", 1, "");
            }
        };
    }

    private static PipelineExecutor executor(AgentRunner runner) {
        PipelineExecutor executor = new PipelineExecutor(null, null, runner, ToolCallBudget.defaults());
        executor.setStageAgentBuilder((code, stage, request, ws) -> {
            AgentClient agent = new AgentClient();
            agent.setName(code);
            return agent;
        });
        return executor;
    }

    @Test
    void researchStageFailureDegradesInsteadOfFailingTheWholeRun() throws Exception {
        // run#46 的教训：调研的 25 次检索全部成功，只因超出上限 1 次就让整轮 PIPELINE FAILED，
        // 而写作阶段完全有能力依据任务要求自行成文。调研失败必须降级，而不是丢掉整次交付。
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        ArticleAiService.ScheduledAgentResult result = executor(
                stageDiesWith(RESEARCH_CODE, false, workspace))
                .execute(request(Map.of("illustration", 0L, "review", 0L), 2), workspace);

        assertThat(result.draft().title()).isEqualTo("测试标题");
        assertThat(workspace.degradationCount()).isEqualTo(1);
        assertThat(result.executionLog()).contains("【调研】阶段中止").contains("未产生调研简报");
    }

    @Test
    void writingStageFailureWithoutASavedDraftStillFailsHard() {
        // 写作阶段是交付的硬前提：没有草稿可交付时不能"降级成功"，必须保持明确失败。
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        assertThatThrownBy(() -> executor(stageDiesWith(WRITING_CODE, false, workspace))
                .execute(request(Map.of("illustration", 0L, "review", 0L), 2), workspace))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("会话中止");
        assertThat(workspace.degradationCount()).isZero();
    }

    @Test
    void writingStageFailureAfterDraftSavedStillDelivers() throws Exception {
        // 会话在提交草稿之后才中止：文章是完整的，丢掉它等于白白浪费整轮算力。
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        ArticleAiService.ScheduledAgentResult result = executor(
                stageDiesWith(WRITING_CODE, true, workspace))
                .execute(request(Map.of("illustration", 0L, "review", 0L), 2), workspace);

        assertThat(result.draft().title()).isEqualTo("测试标题");
        assertThat(workspace.degradationCount()).isEqualTo(1);
        assertThat(result.executionLog()).contains("【写作】阶段中止");
    }
}
