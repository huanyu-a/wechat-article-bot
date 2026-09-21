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
    /**
     * 降级计数：某个阶段/主编会话中止，但已产出物可用、按现有产出继续完成交付（见 PipelineExecutor、CoordinatorExecutor）。
     *
     * <p>与工具失败分开计数：降级不是「某个工具报错」，而是「这一阶段整个没做完」——
     * 混进 toolFailures 会让运行说明写成「有 N 次工具调用失败」，与实情不符。
     * 单独计数才能让终态说清楚「哪次交付是打了折扣的」。
     */
    private final java.util.concurrent.atomic.AtomicInteger degradations = new java.util.concurrent.atomic.AtomicInteger();
    /**
     * 工具参数 JSON 解析失败计数（Phase 4.4）。
     *
     * <p>与 {@code toolFailures} 分开：它是实测最常见的一类工具失败（模型把参数写成 Markdown
     * 代码块或漏引号），但混在「工具失败」总数里看不出趋势——协议提示词补了完整 JSON 示例之后
     * 到底有没有变好，只能靠这个单独的数字回答。
     */
    private final java.util.concurrent.atomic.AtomicInteger toolParamParseFailures =
            new java.util.concurrent.atomic.AtomicInteger();
    /**
     * 本次运行**实际用过的模型档案**（按首次使用顺序，去重）。
     *
     * <p>为什么必须记在运行级：档案切换发生在 {@link AgentInvoker} 内部，单次产出只带「这次会话用过
     * 哪些档案」，而一次运行有多个阶段/子智能体，各自的切换要汇总才看得出「这一轮到底换了几个模型」。
     * 只在日志里留一行 warn 的话，事后想统计「哪个模型在拖后腿」只能全文检索日志。
     */
    private final List<String> profilesUsed = Collections.synchronizedList(new ArrayList<>());
    /**
     * 各阶段的实际耗时、工具调用数与用过的档案（按发生顺序）。
     *
     * <p>为什么必须有：此前 {@code stages_summary} 只有「总共调了几次工具、返工几轮」这类**汇总值**，
     * 一次 1801 秒的 COORDINATOR 运行看不出时间花在哪个阶段——是主编在规划、还是某个子智能体在
     * 反复检索、还是返工轮次拖长，全都只能靠人读 {@code EXECUTION_LOG} 的时间戳反推。
     * 记下每阶段耗时后，「哪一阶段在拖后腿」一眼可见，也是对照基线做效率回归的依据。
     */
    private final List<StageRecord> stages = Collections.synchronizedList(new ArrayList<>());
    /**
     * 调研简报注入上限（字符数）。
     *
     * <p>12000 字约合 8000 token：足够容纳「核心结论 + 关键事实与数据 + 来源链接 + 风险争议」的完整简报
     * （实测一份结构完整的简报在 3000–8000 字），同时把返工轮次的重复注入成本压在有界范围。
     */
    private static final int DEFAULT_MAX_RESEARCH_NOTES_CHARS = 12_000;
    private final int maxResearchNotesChars;
    /**
     * 工具调用治理器（Phase 4：只读去重 + 循环检测 + 预算提示）。
     *
     * <p>放在工作区（运行级）而不是每个 AgentClient 一份：一次运行里「同一个检索被重复发起」
     * 常常跨越阶段/子智能体（调研阶段搜过的词，写作阶段再搜一次），只在单会话内去重就漏掉了
     * 这类重复。它的预算计数由 {@link AgentInvoker} 按会话重置，因此「剩余额度」仍是会话语义。
     */
    private final ToolCallGovernor governor = new ToolCallGovernor();
    private int revisionRound;

    public TaskWorkspace(ScheduledArticleTools.DraftState draftState) {
        this(draftState, DEFAULT_MAX_RESEARCH_NOTES_CHARS);
    }

    public TaskWorkspace(ScheduledArticleTools.DraftState draftState, int maxResearchNotesChars) {
        this.draftState = draftState;
        this.maxResearchNotesChars = maxResearchNotesChars;
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

            /**
             * 被丢弃的那次尝试里的工具失败也要留下：成功路径的计数由
             * {@code addToolFailures(outcome.toolFailures())} 汇总，这条只覆盖
             * 「产出被重试/换档案丢弃、计数否则就没了」的那部分（见 ProgressListener#toolFailuresCounted）。
             */
            @Override
            public void toolFailuresCounted(int delta) {
                addToolFailures(delta);
            }

            @Override
            public void logLine(String line) {
                addExecutionLog(line);
            }

            @Override
            public void toolParamParseFailed(String toolName) {
                addToolParamParseFailure();
            }

            @Override
            public void profileUsed(String label) {
                // 实时汇入运行级档案链：成功路径还会经 addProfilesUsed(Outcome) 再报一次（去重），
                // 而失败路径只有这一条能留住「当时用的是哪个模型」。
                addProfilesUsed(java.util.List.of(label));
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

    /**
     * 记入一次「工具参数 JSON 解析失败」（Phase 4.4）。
     *
     * <p>与 {@link #addToolFailures} 分开：参数解析失败混在工具失败总数里看不出趋势，
     * 而「协议提示词补了完整 JSON 示例之后有没有变好」正是要靠这个单独的数字回答。
     */
    public void addToolParamParseFailure() {
        toolParamParseFailures.incrementAndGet();
    }

    /** 本次运行累计的工具参数解析失败次数。 */
    public int toolParamParseFailureCount() {
        return toolParamParseFailures.get();
    }

    /** 记入一次降级（某阶段中止但按现有产出继续）。 */
    public void addDegradation() {
        degradations.incrementAndGet();
    }

    /** 汇总一次会话实际用过的模型档案（去重、保持首次出现顺序）。 */
    public void addProfilesUsed(List<String> labels) {
        if (labels == null || labels.isEmpty()) return;
        synchronized (profilesUsed) {
            for (String label : labels) {
                if (label != null && !label.isBlank() && !profilesUsed.contains(label)) {
                    profilesUsed.add(label);
                }
            }
        }
    }

    /** 本次运行实际用过的模型档案（按首次使用顺序）；未发生过任何切换时长度为 1。 */
    public List<String> profilesUsed() {
        synchronized (profilesUsed) {
            return List.copyOf(profilesUsed);
        }
    }

    /** 本次运行是否发生过模型档案切换（用过 &gt;1 个档案）。 */
    public boolean switchedProfile() {
        synchronized (profilesUsed) {
            return profilesUsed.size() > 1;
        }
    }

    /**
     * 记入一个阶段的执行情况（耗时 / 工具调用数 / 用过的档案）。
     *
     * <p>失败路径也要记：阶段中止时正是最需要知道「卡了多久、调了几次工具」的时刻，
     * 只记成功路径等于把最该看的那些运行漏掉。
     */
    public void recordStage(String stage, long durationMillis, int toolCalls, List<String> profiles) {
        stages.add(new StageRecord(stage == null ? "UNKNOWN" : stage, Math.max(0, durationMillis),
                Math.max(0, toolCalls),
                profiles == null ? List.of() : List.copyOf(profiles), LocalDateTime.now()));
    }

    /** 各阶段执行记录（按发生顺序）；未经过阶段编排的链路（SINGLE）为空。 */
    public List<StageRecord> stageRecords() {
        synchronized (stages) {
            return List.copyOf(stages);
        }
    }

    /** 各阶段记录转成可序列化的摘要视图（秒为单位，便于人读运行历史）。 */
    private List<Map<String, Object>> stageSummary() {
        List<Map<String, Object>> value = new ArrayList<>();
        for (StageRecord stage : stageRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", stage.stage());
            item.put("seconds", Math.round(stage.durationMillis() / 100.0) / 10.0);
            item.put("toolCalls", stage.toolCalls());
            if (!stage.profiles().isEmpty()) item.put("profiles", stage.profiles());
            value.add(item);
        }
        return value;
    }

    /** 本次运行累计的降级次数；&gt;0 时交付物是打了折扣的（见 TaskExecutionService 的终态判定）。 */
    public int degradationCount() {
        return degradations.get();
    }

    public ScheduledArticleTools.DraftState draftState() {
        return draftState;
    }

    /** 工具调用治理器（只读去重 / 循环检测 / 预算提示）。 */
    public ToolCallGovernor governor() {
        return governor;
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

    /**
     * 全部调研简报拼接（供写作阶段指令注入）；无简报返回空串。
     *
     * <p><b>注入前截断</b>：{@code researchNotes} 是追加式的，而写作指令在**每一轮返工**里都会
     * 把全文重发一次。宽口径任务的简报可以到几万字（run#62 的调研子智能体发起 47 次调用），
     * 若不截断，一次返工就要重发全部简报——token 成本随返工轮数线性放大，
     * 而撰稿人真正需要的是结论与关键事实，不是每一条检索记录。
     *
     * <p>截断保留**开头**（核心结论与关键事实按协议排在最前），并在末尾明确告知被截断：
     * 静默丢弃会让撰稿人以为「简报就这些」，进而漏掉后面的风险与争议项。
     */
    public synchronized String researchNotesText() {
        return truncateResearchNotes(String.join("\n\n", researchNotes), maxResearchNotesChars);
    }

    /** 调研简报注入上限（字符数）；截断逻辑独立成静态方法以便单测直接钉住边界。 */
    static String truncateResearchNotes(String text, int maxChars) {
        if (text == null || text.isEmpty()) return "";
        if (maxChars <= 0 || text.length() <= maxChars) return text;
        return text.substring(0, maxChars)
                + "\n\n（调研简报过长已截断，仅保留前 " + maxChars + " 字；完整简报见任务工作区记录）";
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
        value.put("toolParamParseFailures", toolParamParseFailures.get());
        value.put("degradations", degradations.get());
        value.put("profilesUsed", profilesUsed());
        value.put("switchedProfile", switchedProfile());
        value.put("stages", stageSummary());
        value.put("renderWarnings", draftState.renderWarnings().size());
        value.put("saveWarnings", draftState.saveWarnings().size());
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

    /**
     * 单个阶段的执行记录。
     *
     * @param stage          阶段名（RESEARCH / WRITING / ILLUSTRATION / REVIEW / COORDINATE）
     * @param durationMillis 该阶段实际耗时（含失败前的部分）
     * @param toolCalls      该阶段累计的工具调用数
     * @param profiles       该阶段用过的模型档案（发生过切换时 &gt;1 个）
     */
    public record StageRecord(String stage, long durationMillis, int toolCalls, List<String> profiles,
                              LocalDateTime at) {
    }
}
