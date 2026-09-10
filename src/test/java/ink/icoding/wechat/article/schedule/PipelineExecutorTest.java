package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.entity.MemoryMultipartFile;
import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.DelegateTools;
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
        PipelineExecutor executor = new PipelineExecutor(null, null, runner);
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
                if (ink.icoding.wechat.article.agent.AgentFactory.CODE_WRITER.equals(agent.getName())) {
                    saveDraft(workspace);
                }
                if (ink.icoding.wechat.article.agent.AgentFactory.CODE_REVIEWER.equals(agent.getName())) {
                    workspace.submitReview(true, List.of(), List.of(), "ok");
                }
                return new Outcome("完成", 1, "");
            }
        };
        PipelineExecutor executor = new PipelineExecutor(null, null, capped);
        executor.setStageAgentBuilder((code, stage, request, ws) -> {
            AgentClient agent = new AgentClient();
            agent.setName(code);
            return agent;
        });
        executor.execute(request(Map.of(), 2), workspace);

        assertThat(limits).isNotEmpty().allMatch(value -> value == DelegateTools.MAX_SUB_AGENT_TOOL_CALLS);
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
}
