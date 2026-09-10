package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.skill.LayoutEngine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务共享工作区（skills-agent-plan 5.4）：在多 Agent 协作的各阶段之间共享草稿、调研简报与审核轮次。
 *
 * 设计：包装现有 {@link ScheduledArticleTools.DraftState}（保持原 3 个工具行为完全不变），
 * 在其上叠加调研简报、审核轮次与返工计数；所有状态变更方法 synchronized，工具与编排线程可并发访问。
 */
public class TaskWorkspace {
    private final ScheduledArticleTools.DraftState draftState;
    private final List<String> researchNotes = new ArrayList<>();
    private final List<ReviewRound> reviewRounds = new ArrayList<>();
    private int revisionRound;

    public TaskWorkspace(ScheduledArticleTools.DraftState draftState) {
        this.draftState = draftState;
    }

    public static TaskWorkspace create(Long defaultCoverAssetId, LayoutEngine engine) {
        return new TaskWorkspace(new ScheduledArticleTools.DraftState(defaultCoverAssetId, engine));
    }

    public ScheduledArticleTools.DraftState draftState() {
        return draftState;
    }

    public LayoutEngine layoutEngine() {
        return draftState.layoutEngine();
    }

    /** 调研简报（追加式，带轮次标记）；写作阶段指令中注入。 */
    public synchronized String appendResearchNotes(String notes, String reason) {
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("调研简报不能为空");
        }
        String entry = "【第 " + (revisionRound + 1) + " 轮调研】"
                + (reason == null || reason.isBlank() ? "" : "（" + reason.trim() + "）") + "\n" + notes.strip();
        researchNotes.add(entry);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "调研简报已保存到任务工作区，撰稿人可直接使用");
        result.put("notesLength", notes.strip().length());
        result.put("round", revisionRound + 1);
        return json(result);
    }

    /** 全部调研简报拼接（供写作阶段指令注入）；无简报返回空串。 */
    public synchronized String researchNotesText() {
        return String.join("\n\n", researchNotes);
    }

    public synchronized boolean hasResearchNotes() {
        return !researchNotes.isEmpty();
    }

    /**
     * 提交审核结论（宽松解析：优先 JSON 块，失败回退「有条件通过 + 原文作为 issues」）。
     * 返回给 LLM 的工具结果文本。
     */
    public synchronized String submitReview(boolean passed, List<String> issues, List<String> suggestions,
                                            String rawText) {
        ReviewRound round = new ReviewRound(revisionRound + 1, passed,
                issues == null ? List.of() : List.copyOf(issues),
                suggestions == null ? List.of() : List.copyOf(suggestions),
                rawText == null ? "" : rawText.strip(), LocalDateTime.now());
        reviewRounds.add(round);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", passed ? "审核通过" : "审核未通过，已记录问题等待返工");
        result.put("round", round.round());
        result.put("passed", passed);
        result.put("issues", round.issues());
        return json(result);
    }

    /** 最近一轮审核结论；无审核记录返回 null。 */
    public synchronized ReviewRound latestReview() {
        return reviewRounds.isEmpty() ? null : reviewRounds.get(reviewRounds.size() - 1);
    }

    public synchronized List<ReviewRound> reviewRounds() {
        return List.copyOf(reviewRounds);
    }

    public synchronized int revisionRound() {
        return revisionRound;
    }

    /** 进入下一轮返工：返回自增后的轮次。 */
    public synchronized int nextRevisionRound() {
        revisionRound++;
        return revisionRound;
    }

    /** 审核意见文本（返工指令注入用）。 */
    public synchronized String latestIssuesText() {
        ReviewRound round = latestReview();
        if (round == null || round.issues().isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        int index = 1;
        for (String issue : round.issues()) {
            text.append(index++).append(". ").append(issue).append('\n');
        }
        return text.toString();
    }

    public synchronized Map<String, Object> summary() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("revisionRound", revisionRound);
        value.put("researchNotesRounds", researchNotes.size());
        value.put("reviewRounds", reviewRounds.size());
        value.put("saved", draftState.isSaved());
        return value;
    }

    private static String json(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("任务工作区结果序列化失败", exception);
        }
    }

    /** 审核轮次记录。 */
    public record ReviewRound(int round, boolean passed, List<String> issues, List<String> suggestions,
                              String rawText, LocalDateTime at) {
    }
}
