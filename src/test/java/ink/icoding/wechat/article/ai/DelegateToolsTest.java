package ink.icoding.wechat.article.ai;

import ink.icoding.wechat.article.schedule.AgentRunner;
import ink.icoding.wechat.article.schedule.TaskWorkspace;
import ink.icoding.wechat.article.skill.LayoutEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DelegateTools 预算护栏单测（skills-agent-plan 5.5 / 8.1）：
 * 委托上限 8 次、返工上限、超限返回引导文本而非抛异常。
 */
class DelegateToolsTest {
    private static final String RESEARCH = "delegate_research";
    private static final String WRITING = "delegate_writing";
    private static final String REVIEW = "delegate_review";

    private static AgentRunner.Outcome outcome(String reply) {
        return new AgentRunner.Outcome(reply, 1, "stub");
    }

    private static DelegateTools.SubAgentRunner runner(List<String> invoked) {
        return (code, stage, command, logPrefix) -> {
            invoked.add(code + "|" + stage);
            return outcome("完成");
        };
    }

    private static <T extends ink.icoding.llm.core.tool.ToolParam> String run(
            ink.icoding.llm.core.tool.Tool<T> tool, T param) {
        return tool.execute(param);
    }

    @Test
    void delegationBudgetStopsAtEight() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        List<String> invoked = new ArrayList<>();
        List<ink.icoding.llm.core.tool.Tool> tools =
                DelegateTools.create(workspace, 2, (code, stage) -> runner(invoked), new ArrayList<>());
        @SuppressWarnings("unchecked")
        ink.icoding.llm.core.tool.Tool<DelegateTools.DelegateResearchParam> research =
                (ink.icoding.llm.core.tool.Tool<DelegateTools.DelegateResearchParam>) tools.get(0);

        DelegateTools.DelegateResearchParam param = new DelegateTools.DelegateResearchParam();
        param.setInstruction("调研 AI 行业");
        for (int i = 0; i < DelegateTools.MAX_DELEGATIONS; i++) {
            assertThat(research.execute(param)).isEqualTo("完成");
        }
        assertThat(research.execute(param)).contains("委托次数已达上限");
        assertThat(invoked).hasSize(DelegateTools.MAX_DELEGATIONS);
    }

    @Test
    void writingDelegationCountsRevisionAndEnforcesMaxRounds() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        List<String> invoked = new ArrayList<>();
        List<ink.icoding.llm.core.tool.Tool> tools =
                DelegateTools.create(workspace, 1, (code, stage) -> runner(invoked), new ArrayList<>());
        @SuppressWarnings("unchecked")
        ink.icoding.llm.core.tool.Tool<DelegateTools.DelegateWritingParam> writing =
                (ink.icoding.llm.core.tool.Tool<DelegateTools.DelegateWritingParam>) tools.get(1);

        DelegateTools.DelegateWritingParam first = new DelegateTools.DelegateWritingParam();
        first.setInstruction("写初稿");
        assertThat(writing.execute(first)).isEqualTo("完成");

        DelegateTools.DelegateWritingParam revision = new DelegateTools.DelegateWritingParam();
        revision.setInstruction("按审核意见返工");
        revision.setRevision(true);
        assertThat(writing.execute(revision)).isEqualTo("完成"); // maxRounds=1，第 1 次返工允许
        assertThat(workspace.revisionRound()).isEqualTo(1);

        assertThat(writing.execute(revision)).contains("返工次数已达上限");
        assertThat(workspace.revisionRound()).isEqualTo(1); // 超限不再累加
    }

    @Test
    void writingCommandInjectsResearchNotesWhenPresent() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        workspace.appendResearchNotes("核心结论：AI 正在普及", "第一轮");
        List<String> commands = new ArrayList<>();
        DelegateTools.SubAgentRunner capturing = (code, stage, command, logPrefix) -> {
            commands.add(command);
            return outcome("完成");
        };
        List<ink.icoding.llm.core.tool.Tool> tools =
                DelegateTools.create(workspace, 2, (code, stage) -> capturing, new ArrayList<>());
        @SuppressWarnings("unchecked")
        ink.icoding.llm.core.tool.Tool<DelegateTools.DelegateWritingParam> writing =
                (ink.icoding.llm.core.tool.Tool<DelegateTools.DelegateWritingParam>) tools.get(1);

        DelegateTools.DelegateWritingParam param = new DelegateTools.DelegateWritingParam();
        param.setInstruction("写一篇关于 AI 的文章");
        writing.execute(param);

        assertThat(commands).hasSize(1);
        assertThat(commands.get(0)).contains("调研简报").contains("核心结论：AI 正在普及")
                .contains("save_article_draft");
    }

    @Test
    void eachDelegateToolTargetsCorrectBuiltinAgent() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        List<String> invoked = new ArrayList<>();
        List<ink.icoding.llm.core.tool.Tool> tools =
                DelegateTools.create(workspace, 2, (code, stage) -> runner(invoked), new ArrayList<>());

        DelegateTools.DelegateResearchParam research = new DelegateTools.DelegateResearchParam();
        research.setInstruction("r");
        tools.get(0).execute(research);
        DelegateTools.DelegateWritingParam writing = new DelegateTools.DelegateWritingParam();
        writing.setInstruction("w");
        tools.get(1).execute(writing);
        DelegateTools.DelegateIllustrationParam illustration = new DelegateTools.DelegateIllustrationParam();
        illustration.setInstruction("i");
        tools.get(2).execute(illustration);
        DelegateTools.DelegateReviewParam review = new DelegateTools.DelegateReviewParam();
        review.setInstruction("v");
        tools.get(3).execute(review);

        assertThat(invoked).containsExactly(
                "builtin_researcher|RESEARCH", "builtin_writer|WRITING",
                "builtin_illustrator|ILLUSTRATION", "builtin_reviewer|REVIEW");
    }

    @Test
    void renderResultJsonOmitsHtmlAndExposesTheme() {
        ink.icoding.wechat.article.skill.MarkFlowRenderService.RenderResult result =
                new ink.icoding.wechat.article.skill.MarkFlowRenderService.RenderResult(
                        "<p>大段 HTML</p>", "标题", "摘要", "#27ae60", "#1e8449");
        String json = EditorServiceTools.renderResultJson(result, "r1");
        assertThat(json).contains("\"renderId\":\"r1\"").contains("标题").contains("#27ae60")
                .doesNotContain("大段 HTML");
    }

    @Test
    void placeholderSummaryHidesHtml() {
        assertThat(EditorServiceTools.placeholderSummary("r2", 1200))
                .contains("renderId=r2").contains("1200").doesNotContain("<p>");
    }
}
