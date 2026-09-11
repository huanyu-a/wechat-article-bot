package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.skill.LayoutEngine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    /**
     * 分阶段执行日志。放在工作区而不是执行器局部变量：阶段抛异常时局部变量会被丢弃，
     * 运行历史只剩一行错误，看不出失败在哪一阶段（PIPELINE 排查几乎只能靠这份日志）。
     */
    private final List<String> executionLog = Collections.synchronizedList(new ArrayList<>());
    /**
     * 工具调用计数（跨阶段累计，chief 与子智能体都计入）。放在工作区而不是执行器局部变量：
     * 阶段抛异常时局部计数随异常丢弃，运行历史里「调了几次工具」在失败时永远是 0；
     * 而 COORDINATOR 的委托子智能体调用量也只有累计到这里才统计得到（执行结果只带 chief 自身的计数）。
     */
    private final java.util.concurrent.atomic.AtomicInteger toolCalls = new java.util.concurrent.atomic.AtomicInteger();
    /**
     * 工具失败计数（成功路径判定「是否需要带警告的成功」的依据）。
     *
     * <p>背景：一次运行里配图工具因描述超长落库失败，整次运行仍记 SUCCESS——用户看到的是成功，
     * 交付物却缺图。终态判定必须能看见「有工具失败」这件事，所以失败数随工具调用数一起累计到工作区。
     */
    private final java.util.concurrent.atomic.AtomicInteger toolFailures = new java.util.concurrent.atomic.AtomicInteger();
    private int revisionRound;

    public TaskWorkspace(ScheduledArticleTools.DraftState draftState) {
        this.draftState = draftState;
    }

    public static TaskWorkspace create(Long defaultCoverAssetId, LayoutEngine engine) {
        return new TaskWorkspace(new ScheduledArticleTools.DraftState(defaultCoverAssetId, engine));
    }

    /** 分阶段执行日志的写入端（执行器直接往这个列表 add/addAll，成功与失败路径共用同一份）。 */
    public List<String> executionLog() {
        return executionLog;
    }

    /** 分阶段执行日志全文；未产生日志时返回空串。 */
    public synchronized String executionLogText() {
        return String.join("\n", List.copyOf(executionLog));
    }

    /** 记入一次工具调用（运行器在每次计入时实时上报，失败路径也能留住已完成的部分）。 */
    public void addToolCalls(int count) {
        if (count > 0) toolCalls.addAndGet(count);
    }

    /** 追加一行执行日志（运行器逐行实时上报时用，见 {@link #progressListener()}）。 */
    public void addExecutionLog(String line) {
        if (line != null && !line.isBlank()) executionLog.add(line);
    }

    /**
     * 会话进度监听器：把工具计数与日志行实时落到工作区。
     *
     * <p>为什么必须实时：进度若等会话返回后再整体读取，停滞/超时的那次尝试会连同日志一起被丢弃，
     * 运行历史里就只剩一行错误（实测停滞失败的运行 {@code EXECUTION_LOG} 为空，看不出卡在哪一步）。
     * 三条链路（PIPELINE / COORDINATOR / SINGLE）都从这里取监听器，语义只有一处定义。
     */
    public AgentRunner.ProgressListener progressListener() {
        return new AgentRunner.ProgressListener() {
            @Override
            public void toolCallCounted(int delta) {
                addToolCalls(delta);
            }

            @Override
            public void logLine(String line) {
                addExecutionLog(line);
            }
        };
    }

    /** 本次运行累计的工具调用次数（成功与失败路径共用；TaskExecutionService 据此写 task_run）。 */
    public int toolCallCount() {
        return toolCalls.get();
    }

    /** 记入工具失败次数（子智能体与各阶段都会上报）。 */
    public void addToolFailures(int count) {
        if (count > 0) toolFailures.addAndGet(count);
    }

    /** 本次运行累计的工具失败次数；&gt;0 时运行终态不应是纯粹的 SUCCESS（见 TaskExecutionService）。 */
    public int toolFailureCount() {
        return toolFailures.get();
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
        value.put("toolCalls", toolCalls.get());
        value.put("toolFailures", toolFailures.get());
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
