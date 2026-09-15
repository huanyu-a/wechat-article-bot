package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.TaskWorkspaceTools;
import ink.icoding.wechat.article.skill.LayoutEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TaskWorkspace 与协作工具单测（skills-agent-plan 5.4 / 8.1）：
 * 调研简报追加与注入、审核轮次与返工计数、submit_review 宽松回退、审核意见文本。
 */
class TaskWorkspaceTest {

    @Test
    void researchNotesAppendWithRoundMarker() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        assertThat(workspace.hasResearchNotes()).isFalse();
        assertThat(workspace.researchNotesText()).isEmpty();

        workspace.appendResearchNotes("核心结论：AI 普及率上升", "第一轮");
        workspace.appendResearchNotes("补充数据：2026 年达 40%", null);

        String notes = workspace.researchNotesText();
        assertThat(notes).contains("核心结论：AI 普及率上升").contains("补充数据：2026 年达 40%")
                .contains("【第 1 轮调研】");
        assertThat(workspace.hasResearchNotes()).isTrue();
    }

    @Test
    void blankResearchNotesRejected() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        assertThatThrownBy(() -> workspace.appendResearchNotes("   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> workspace.appendResearchNotes(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 调研简报注入前截断：保留开头（核心结论按协议排在最前）并明确告知被截断。
     *
     * <p>为什么必须截断：{@code researchNotes} 是追加式的，而写作指令在**每一轮返工**里都会
     * 把全文重发一次。宽口径任务的简报可以到几万字（run#62 的调研子智能体发起 47 次调用），
     * 不截断的话 token 成本随返工轮数线性放大。
     *
     * <p>为什么不能静默丢弃：撰稿人会以为「简报就这些」，进而漏掉后面的风险与争议项。
     */
    @Test
    void overlongResearchNotesAreTruncatedWithAVisibleMarker() {
        String longNotes = "核心结论：".concat("甲".repeat(200));
        int limit = 100;

        String truncated = TaskWorkspace.truncateResearchNotes(longNotes, limit);

        assertThat(truncated).startsWith("核心结论：");
        assertThat(truncated).contains("调研简报过长已截断").contains(String.valueOf(limit));
        // 截断后总长 = 上限 + 提示语，不随原文长度增长
        assertThat(truncated.length()).isLessThan(longNotes.length() + 100);
    }

    /** 未超上限时原样返回（不得引入任何标记，否则正常简报会被误认为被截断过）。 */
    @Test
    void researchNotesUnderTheLimitAreUntouched() {
        String notes = "短简报";

        assertThat(TaskWorkspace.truncateResearchNotes(notes, 100)).isEqualTo(notes);
        assertThat(TaskWorkspace.truncateResearchNotes("", 100)).isEmpty();
        assertThat(TaskWorkspace.truncateResearchNotes(null, 100)).isEmpty();
        // 上限 ≤0 视为不限制
        assertThat(TaskWorkspace.truncateResearchNotes(notes, 0)).isEqualTo(notes);
    }

    /**
     * 工作区默认上限生效：注入文本不会超过默认上限太多（多出的只是截断提示语）。
     *
     * <p>默认上限的意义是「返工轮次的重复注入成本有界」，因此这条断言必须走真实工作区，
     * 而不是只测静态方法。
     */
    @Test
    void workspaceAppliesTheDefaultTruncationOnInjection() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        workspace.appendResearchNotes("甲".repeat(30_000), null);

        String injected = workspace.researchNotesText();

        assertThat(injected).contains("调研简报过长已截断");
        assertThat(injected.length()).isLessThan(30_000);
    }

    @Test
    void reviewRoundsTrackPassFailAndRevision() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        assertThat(workspace.latestReview()).isNull();
        assertThat(workspace.latestIssuesText()).isEmpty();

        workspace.submitReview(false, List.of("数据来源缺失", "结构混乱"), List.of("可加小标题"), "首轮");
        assertThat(workspace.latestReview().passed()).isFalse();
        assertThat(workspace.latestReview().round()).isEqualTo(1);
        assertThat(workspace.latestIssuesText()).contains("1. 数据来源缺失").contains("2. 结构混乱");
        assertThat(workspace.reviewRounds()).hasSize(1);
        assertThat(workspace.revisionRound()).isZero();

        assertThat(workspace.nextRevisionRound()).isEqualTo(1);
        assertThat(workspace.revisionRound()).isEqualTo(1);

        workspace.submitReview(true, List.of(), List.of(), "通过");
        assertThat(workspace.latestReview().passed()).isTrue();
        assertThat(workspace.latestReview().round()).isEqualTo(2);
        assertThat(workspace.latestIssuesText()).isEmpty();
    }

    @Test
    void submitReviewToolFallsBackToConditionalPass() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        TaskWorkspaceTools.SubmitReviewParam param = new TaskWorkspaceTools.SubmitReviewParam();
        param.setPassed(null); // 模型没给结论
        param.setSummary("整体尚可，个别措辞待打磨");

        String result = new TaskWorkspaceTools.SubmitReviewTool(workspace).execute(param);

        assertThat(result).contains("\"passed\":true");
        assertThat(workspace.latestReview().passed()).isTrue();
        assertThat(workspace.latestReview().issues())
                .anyMatch(issue -> issue.contains("整体尚可，个别措辞待打磨"));
    }

    @Test
    void submitReviewToolKeepsExplicitConclusion() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        TaskWorkspaceTools.SubmitReviewParam param = new TaskWorkspaceTools.SubmitReviewParam();
        param.setPassed(false);
        param.setIssues(List.of("事实错误：数据来源不可靠"));
        param.setSuggestions(List.of("补充对比数据"));

        new TaskWorkspaceTools.SubmitReviewTool(workspace).execute(param);

        assertThat(workspace.latestReview().passed()).isFalse();
        assertThat(workspace.latestReview().issues()).containsExactly("事实错误：数据来源不可靠");
        assertThat(workspace.latestReview().suggestions()).containsExactly("补充对比数据");
    }

    @Test
    void saveResearchNotesToolValidatesInput() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        TaskWorkspaceTools.SaveResearchNotesParam param = new TaskWorkspaceTools.SaveResearchNotesParam();
        param.setNotes("简报内容");
        param.setReason("首轮");

        String result = new TaskWorkspaceTools.SaveResearchNotesTool(workspace).execute(param);
        assertThat(result).contains("调研简报已保存");
        assertThat(workspace.researchNotesText()).contains("简报内容");

        TaskWorkspaceTools.SaveResearchNotesParam blank = new TaskWorkspaceTools.SaveResearchNotesParam();
        blank.setNotes(" ");
        assertThatThrownBy(() -> new TaskWorkspaceTools.SaveResearchNotesTool(workspace).execute(blank))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summaryReflectsWorkspaceState() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        workspace.appendResearchNotes("简报", null);
        workspace.submitReview(false, List.of("问题"), List.of(), "首轮");
        workspace.nextRevisionRound();

        assertThat(workspace.summary()).containsEntry("revisionRound", 1)
                .containsEntry("researchNotesRounds", 1)
                .containsEntry("reviewRounds", 1)
                .containsEntry("saved", false);
    }

    /**
     * 阶段记录（Phase 5）：一次 1801 秒的 COORDINATOR 运行必须能看出时间花在哪个阶段。
     *
     * <p>此前 {@code stages_summary} 只有汇总值（总工具数、返工轮数），「哪一阶段在拖后腿」
     * 只能靠人读执行日志的时间戳反推——这是效率回归无法量化对照的直接原因。
     */
    @Test
    void stageRecordsCarryDurationToolCallsAndProfiles() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        assertThat(workspace.stageRecords()).isEmpty();

        workspace.recordStage("RESEARCH", 12_300L, 18, List.of("deepseek-flash"));
        workspace.recordStage("WRITING", 45_600L, 3, List.of("glm-5.3-flash", "hy4-preview"));

        assertThat(workspace.stageRecords()).hasSize(2);
        assertThat(workspace.stageRecords().get(0).stage()).isEqualTo("RESEARCH");
        assertThat(workspace.stageRecords().get(1).profiles())
                .containsExactly("glm-5.3-flash", "hy4-preview");

        // 摘要里以「秒」呈现（保留一位小数）：运行历史是给人读的，毫秒精度没有意义
        assertThat(workspace.summary()).containsKey("stages");
        List<?> stages = (List<?>) workspace.summary().get("stages");
        assertThat(stages).hasSize(2);
        assertThat(stages.get(0).toString()).contains("RESEARCH").contains("12.3").contains("18");
    }

    /** 阶段名缺失或耗时/计数为负时归一化，不让脏值进运行历史（它是给排查用的，不能自带错误）。 */
    @Test
    void stageRecordsNormalizeMissingOrNegativeValues() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        workspace.recordStage(null, -5L, -3, null);

        TaskWorkspace.StageRecord record = workspace.stageRecords().get(0);
        assertThat(record.stage()).isEqualTo("UNKNOWN");
        assertThat(record.durationMillis()).isZero();
        assertThat(record.toolCalls()).isZero();
        assertThat(record.profiles()).isEmpty();
    }

    /**
     * 工具参数解析失败单独计数（Phase 4.4）：它混在「工具失败」总数里看不出趋势，
     * 而协议提示词补了完整 JSON 示例之后有没有变好，正是要靠这个数字回答。
     */
    @Test
    void toolParamParseFailuresAreCountedSeparately() {
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);
        assertThat(workspace.toolParamParseFailureCount()).isZero();

        // 经进度监听器上报（与运行器同一条路径），而不是直接调 addToolParamParseFailure
        workspace.progressListener().toolParamParseFailed("submit_review");
        workspace.progressListener().toolParamParseFailed("submit_review");
        workspace.addToolFailures(3);

        assertThat(workspace.toolParamParseFailureCount()).isEqualTo(2);
        assertThat(workspace.toolFailureCount()).isEqualTo(3);
        assertThat(workspace.summary()).containsEntry("toolParamParseFailures", 2)
                .containsEntry("toolFailures", 3);
    }

    /** 参数解析失败的识别判据：agent4j 的原文、Jackson 的原文都要认得，无关错误不得误判。 */
    @Test
    void paramParseFailureDetectionCoversUpstreamWording() {
        assertThat(AgentInvoker.isToolParamParseFailure(
                "Failed to parse tool param JSON: unexpected token")).isTrue();
        assertThat(AgentInvoker.isToolParamParseFailure(
                "com.fasterxml.jackson.databind.JsonMappingException: Cannot deserialize")).isTrue();
        assertThat(AgentInvoker.isToolParamParseFailure("文章摘要不能超过120字")).isFalse();
        assertThat(AgentInvoker.isToolParamParseFailure(null)).isFalse();
    }
}
