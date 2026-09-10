package ink.icoding.wechat.article.ai;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.schedule.TaskWorkspace;
import lombok.Data;

import java.util.List;

/**
 * 多 Agent 协作的服务端工具（skills-agent-plan 5.3 / 5.4）：
 * 调研员落盘简报（RESEARCH 组）、审稿人提交结论（REVIEW 组）。
 * 与 {@link ScheduledArticleTools} 共享同一个 {@link TaskWorkspace}。
 */
public final class TaskWorkspaceTools {
    private TaskWorkspaceTools() {
    }

    public static List<Tool> research(TaskWorkspace workspace) {
        return List.of(new SaveResearchNotesTool(workspace));
    }

    public static List<Tool> review(TaskWorkspace workspace) {
        return List.of(new SubmitReviewTool(workspace));
    }

    @ToolInfo(name = "save_research_notes", description = "把结构化调研简报保存到任务工作区，供撰稿人直接使用。调研完成后必须调用；再次调用会在上一轮简报之后追加。简报应包含：核心结论、关键事实与数据（附来源链接）、可引用素材、风险与争议、给撰稿人的建议。")
    public static class SaveResearchNotesTool implements Tool<SaveResearchNotesParam> {
        private final TaskWorkspace workspace;

        public SaveResearchNotesTool(TaskWorkspace workspace) {
            this.workspace = workspace;
        }

        @Override
        public String execute(SaveResearchNotesParam param) {
            if (param.getNotes() == null || param.getNotes().isBlank()) {
                throw new IllegalArgumentException("调研简报不能为空");
            }
            return workspace.appendResearchNotes(param.getNotes(), param.getReason());
        }
    }

    @Data
    public static class SaveResearchNotesParam extends ToolParam {
        @Param(description = "结构化调研简报全文（Markdown）：核心结论 / 关键事实与数据（含来源链接）/ 可引用素材 / 风险与争议 / 给撰稿人的建议")
        private String notes;
        @Param(required = false, description = "本轮调研的补充说明，例如返工时的针对性问题") private String reason;
    }

    @ToolInfo(name = "submit_review", description = "提交本次审核结论。审核完成后必须调用；系统据此决定是否需要撰稿人返工。passed 只在存在影响发布的实质问题时为 false，措辞润色类意见放入 suggestions。")
    public static class SubmitReviewTool implements Tool<SubmitReviewParam> {
        private final TaskWorkspace workspace;

        public SubmitReviewTool(TaskWorkspace workspace) {
            this.workspace = workspace;
        }

        @Override
        public String execute(SubmitReviewParam param) {
            if (param.getPassed() == null) {
                // 宽松回退（方案 5.4）：模型没给结论时按「有条件通过」处理，把原文记为问题，
                // 既不阻断交付也不丢失审核信息；硬失败会让整篇文章白写。
                String raw = param.getSummary() == null ? "" : param.getSummary().strip();
                List<String> issues = new java.util.ArrayList<>(
                        param.getIssues() == null ? List.of() : param.getIssues());
                issues.add(raw.isBlank() ? "审稿人未明确给出 passed 结论，已按有条件通过处理" : "未给出 passed 结论，原文：" + raw);
                return workspace.submitReview(true, issues, param.getSuggestions(),
                        raw.isBlank() ? "未给出 passed 结论" : raw);
            }
            return workspace.submitReview(param.getPassed(), param.getIssues(), param.getSuggestions(),
                    param.getSummary());
        }
    }

    @Data
    public static class SubmitReviewParam extends ToolParam {
        @Param(description = "是否通过审核（true=可直接发布；false=存在影响发布的实质问题需返工）")
        private Boolean passed;
        @Param(required = false, description = "必须修改的问题列表，每条具体到位置与原因") private List<String> issues;
        @Param(required = false, description = "建议性意见（不阻断发布）") private List<String> suggestions;
        @Param(required = false, description = "审核结论摘要") private String summary;
    }
}
