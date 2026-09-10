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
}
