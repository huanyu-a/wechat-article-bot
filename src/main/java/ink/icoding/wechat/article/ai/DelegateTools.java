package ink.icoding.wechat.article.ai;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.schedule.AgentRunner;
import ink.icoding.wechat.article.schedule.TaskWorkspace;
import ink.icoding.wechat.article.schedule.ToolCallBudget;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * 协调者委托工具（skills-agent-plan 5.5 COORDINATOR）：chief 通过这 4 个工具同步委托子智能体。
 *
 * 预算护栏（代码强制，超限工具直接返回引导性错误文本而非抛异常，让 chief 收尾）：
 * - 委托总次数 ≤ 8；
 * - 同一草稿返工 ≤ max_revision_rounds；
 * - 子智能体单次工具调用 ≤ {@link ToolCallBudget#subAgentLimitFor(String)}（调研阶段单独一档）；
 * - 整轮（所有子智能体累计）工具调用 ≤ {@link #MAX_TOTAL_TOOL_CALLS}。
 */
public final class DelegateTools {
    public static final int MAX_DELEGATIONS = 8;
    /**
     * 子智能体工具调用上限的**缺省值**（写作/配图/审核阶段）；实际额度按阶段取自 {@link ToolCallBudget}。
     * 与 {@link ToolCallBudget#DEFAULT_STAGE} 必须同源，否则会出现「执行器按 36 跑完、这里按 24 判超限」的
     * 自相矛盾日志（{@code ToolCallBudgetTest.defaultsStayAlignedWithTheDelegateToolConstants} 钉住这条）。
     */
    public static final int MAX_SUB_AGENT_TOOL_CALLS = 36;
    /** 协调者自身工具调用上限（读草稿 + 8 次委托，留足余量但防失控）。 */
    public static final int MAX_CHIEF_TOOL_CALLS = 48;
    /**
     * **整轮**工具调用总上限（子智能体侧累计）。此前只有逐项额度——chief 48 次、
     * 每次委托的子智能体 24 次、最多 8 次委托——叠加上界达 240 次，比 SINGLE 的
     * {@code MAX_SCHEDULED_TOOL_CALLS=40} 高一个量级；无人值守的定时任务因此可能在一次运行里
     * 消耗远超预期的额度，而「委托次数上限」拦不住「每次委托都跑满额度」这种组合。
     * 取 200 ≈ SINGLE 的 5 倍：实测一轮 PIPELINE 已用到 110 次（run#68），
     * 四个阶段各跑满 36 次时仍有 56 次余量，同时「失控时把量级压回可预期范围」这条仍然成立。
     * 判据是「**已累计** ≥ 上限才拒绝下一次委托」（{@code consume}），所以最后一次委托可以带着整笔
     * 阶段额度越线，实际落在 [上限, 上限 + 单次额度) 区间里——总闸拦的是量级，不是逐次记账。
     * chief 自身的额度仍由 MAX_CHIEF_TOOL_CALLS 单独约束。
     */
    public static final int MAX_TOTAL_TOOL_CALLS = 200;

    private DelegateTools() {
    }

    /** 子智能体运行器：给定 code/stage/指令，装配并同步运行，返回执行结果。 */
    @FunctionalInterface
    public interface SubAgentRunner {
        AgentRunner.Outcome run(String code, String stage, String command, String logPrefix);
    }

    /**
     * 构建 DELEGATE 工具组。
     *
     * @param workspace     共享工作区
     * @param maxRounds     返工上限（来自任务配置）
     * @param runnerFactory 由执行器提供的子智能体运行器工厂（参数：是否写作阶段、指令）
     */
    public static List<Tool> create(TaskWorkspace workspace, int maxRounds,
                                    BiFunction<String, String, SubAgentRunner> runnerFactory,
                                    List<String> executionLog) {
        return create(workspace, maxRounds, runnerFactory, executionLog, ToolCallBudget.defaults());
    }

    /**
     * 构建 DELEGATE 工具组（按阶段预算）。
     *
     * @param budget 工具调用预算（Spring 注入的 {@link ToolCallBudget}；缺省即默认额度）
     */
    public static List<Tool> create(TaskWorkspace workspace, int maxRounds,
                                    BiFunction<String, String, SubAgentRunner> runnerFactory,
                                    List<String> executionLog, ToolCallBudget budget) {
        Budget state = new Budget(workspace, maxRounds, executionLog, budget);
        return List.of(
                new DelegateResearchTool(state, runnerFactory),
                new DelegateWritingTool(state, runnerFactory),
                new DelegateIllustrationTool(state, runnerFactory),
                new DelegateReviewTool(state, runnerFactory));
    }

    /** 委托预算与返工计数（共享于 4 个工具实例）。 */
    public static final class Budget {
        private final TaskWorkspace workspace;
        private final int maxRounds;
        private final List<String> executionLog;
        private final ToolCallBudget limits;
        private final AtomicInteger delegations = new AtomicInteger();
        /** 子智能体累计工具调用数（整轮预算，见 {@link #MAX_TOTAL_TOOL_CALLS}）。 */
        private final AtomicInteger subAgentToolCalls = new AtomicInteger();

        Budget(TaskWorkspace workspace, int maxRounds, List<String> executionLog, ToolCallBudget limits) {
            this.workspace = workspace;
            this.maxRounds = maxRounds;
            this.executionLog = executionLog == null
                    ? Collections.synchronizedList(new ArrayList<>()) : executionLog;
            this.limits = limits == null ? ToolCallBudget.defaults() : limits;
        }

        TaskWorkspace workspace() {
            return workspace;
        }

        List<String> log() {
            return executionLog;
        }

        /** 本阶段子智能体的工具调用额度（调研阶段高于其他阶段，见 {@link ToolCallBudget}）。 */
        int subAgentLimit(String stage) {
            return limits.subAgentLimitFor(stage);
        }

        /** 预算检查：超限返回引导文本，未超限返回 null 并占用一次额度。 */
        String consume(String stageLabel) {
            if (subAgentToolCalls.get() >= limits.totalLimit()) {
                // 整轮预算已耗尽：与「委托次数上限」同样返回引导文本，让 chief 收尾而不是中断会话
                log().add("【协调】整轮工具调用已达上限 " + limits.totalLimit() + " 次，拒绝新的委托");
                return "本次运行的工具调用总量已达上限 " + limits.totalLimit()
                        + " 次，请直接依据现有草稿与调研结果收尾，不要再发起新的委托。";
            }
            int used = delegations.incrementAndGet();
            if (used > MAX_DELEGATIONS) {
                delegations.decrementAndGet();
                return "委托次数已达上限 " + MAX_DELEGATIONS + " 次，请直接依据现有草稿与调研结果收尾，"
                        + "不要再发起新的委托。";
            }
            log().add("【协调】委托 #" + used + "：" + stageLabel);
            return null;
        }

        /** 记入一次子智能体的工具调用量（整轮预算的在途累计）。 */
        void recordSubAgentToolCalls(int count) {
            if (count > 0) subAgentToolCalls.addAndGet(count);
        }

        /** 写作类委托的返工计数：超限返回引导文本。 */
        String checkRevision() {
            if (workspace.revisionRound() >= maxRounds) {
                return "返工次数已达上限 " + maxRounds + " 次，请接受当前版本或自行说明遗留问题后收尾。";
            }
            return null;
        }

        int delegations() {
            return delegations.get();
        }

        int totalToolCalls() {
            return subAgentToolCalls.get();
        }
    }

    private static String runSubAgent(Budget budget, SubAgentRunner runner, String code, String stage,
                                      String command, String logPrefix) {
        AgentRunner.Outcome outcome;
        try {
            outcome = runner.run(code, stage, command, logPrefix);
        } catch (IllegalStateException error) {
            // 子智能体超限中止：转成引导性文本，让 chief 收尾而不是中断整个会话
            String message = error.getMessage() == null ? "子智能体执行失败" : error.getMessage();
            budget.log().add(logPrefix + message);
            return "委托中止：" + message + "。请直接依据现有产出收尾，不要再次委托同一阶段。";
        }
        // 整轮预算记账：子智能体即使被上限中止，也已消耗掉部分额度
        budget.recordSubAgentToolCalls(outcome.toolCalls());
        budget.log().addAll(splitLines(outcome.executionLog()));
        String reply = outcome.reply();
        // 阶段额度按 stage 取（调研阶段高于其他阶段）：这里与 runnerFactory 传入 runWithLimit 的
        // 额度必须同源，否则会出现「执行器按 40 跑完、这里按 24 判超限」的自相矛盾日志。
        int limit = budget.subAgentLimit(stage);
        if (outcome.toolCalls() > limit) {
            budget.log().add(logPrefix + "子智能体工具调用 " + outcome.toolCalls() + " 次，超出上限 " + limit);
            return "子智能体本次工具调用已达上限（" + limit + " 次）。"
                    + "请直接依据现有产出收尾，不要再次委托同一阶段。\n\n"
                    + (reply == null || reply.isBlank() ? "（无产出摘要）" : reply);
        }
        return reply == null || reply.isBlank() ? "子智能体已完成委托任务" : reply;
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return List.of(text.split("\n"));
    }

    @ToolInfo(name = "delegate_research", description = "委托调研员完成资料检索与核实，产出结构化调研简报（落盘到任务工作区，撰稿人可直接使用）。指令中必须说明：调研主题、需要回答的关键问题、时间与地域范围、期望的产出重点。调研员完成后返回简报摘要。")
    public static class DelegateResearchTool implements Tool<DelegateResearchParam> {
        private final Budget budget;
        private final BiFunction<String, String, SubAgentRunner> runnerFactory;

        public DelegateResearchTool(Budget budget, BiFunction<String, String, SubAgentRunner> runnerFactory) {
            this.budget = budget;
            this.runnerFactory = runnerFactory;
        }

        @Override
        public String execute(DelegateResearchParam param) {
            String blocked = budget.consume("调研员");
            if (blocked != null) return blocked;
            String command = "请完成调研任务。\n调研要求：\n" + param.getInstruction()
                    + "\n\n完成后必须调用 save_research_notes 提交结构化简报。";
            return runSubAgent(budget, runnerFactory.apply(AgentFactory.CODE_RESEARCHER, "RESEARCH"),
                    AgentFactory.CODE_RESEARCHER, "RESEARCH", command, "【委托·调研】");
        }
    }

    @Data
    public static class DelegateResearchParam extends ToolParam {
        @Param(description = "给调研员的完整指令：主题、关键问题、范围、产出重点") private String instruction;
    }

    @ToolInfo(name = "delegate_writing", description = "委托撰稿人撰写或按审核意见返工。指令中必须说明：写作要求（或返工要改什么）、是否需要参考调研简报。撰稿人完成后草稿已落盘，返回完成摘要。")
    public static class DelegateWritingTool implements Tool<DelegateWritingParam> {
        private final Budget budget;
        private final BiFunction<String, String, SubAgentRunner> runnerFactory;

        public DelegateWritingTool(Budget budget, BiFunction<String, String, SubAgentRunner> runnerFactory) {
            this.budget = budget;
            this.runnerFactory = runnerFactory;
        }

        @Override
        public String execute(DelegateWritingParam param) {
            String blocked = budget.consume("撰稿人");
            if (blocked != null) return blocked;
            if (Boolean.TRUE.equals(param.getRevision())) {
                String revisionBlocked = budget.checkRevision();
                if (revisionBlocked != null) return revisionBlocked;
                budget.workspace().nextRevisionRound();
            }
            String command = "请完成写作任务。\n写作要求：\n" + param.getInstruction();
            if (budget.workspace().hasResearchNotes()) {
                command += "\n\n调研简报（已由调研员落盘，可直接使用）：\n" + budget.workspace().researchNotesText();
            }
            command += "\n\n完成后必须调用 save_article_draft 提交完整文章。";
            return runSubAgent(budget, runnerFactory.apply(AgentFactory.CODE_WRITER, "WRITING"),
                    AgentFactory.CODE_WRITER, "WRITING", command, "【委托·写作】");
        }
    }

    @Data
    public static class DelegateWritingParam extends ToolParam {
        @Param(description = "给撰稿人的完整指令：写作要求或返工要点") private String instruction;
        @Param(required = false, description = "是否为按审核意见返工（true 时计入返工轮次）") private Boolean revision;
    }

    @ToolInfo(name = "delegate_illustration", description = "委托配图师为当前草稿配图并设置封面。指令中说明配图风格与密度要求（如每章一张、偏实拍或偏插画）。配图师完成后草稿已更新，返回完成摘要。")
    public static class DelegateIllustrationTool implements Tool<DelegateIllustrationParam> {
        private final Budget budget;
        private final BiFunction<String, String, SubAgentRunner> runnerFactory;

        public DelegateIllustrationTool(Budget budget, BiFunction<String, String, SubAgentRunner> runnerFactory) {
            this.budget = budget;
            this.runnerFactory = runnerFactory;
        }

        @Override
        public String execute(DelegateIllustrationParam param) {
            String blocked = budget.consume("配图师");
            if (blocked != null) return blocked;
            String command = "请为当前草稿完成配图。\n配图要求：\n" + param.getInstruction()
                    + "\n\n完成后必须重新调用 save_article_draft 提交更新后的完整草稿，并调用 set_article_draft_cover 设置封面。";
            return runSubAgent(budget, runnerFactory.apply(AgentFactory.CODE_ILLUSTRATOR, "ILLUSTRATION"),
                    AgentFactory.CODE_ILLUSTRATOR, "ILLUSTRATION", command, "【委托·配图】");
        }
    }

    @Data
    public static class DelegateIllustrationParam extends ToolParam {
        @Param(description = "给配图师的完整指令：配图风格、密度、封面要求") private String instruction;
    }

    @ToolInfo(name = "delegate_review", description = "委托审稿人审核当前草稿。审稿人通过 submit_review 提交结论；返回内容会包含 passed 与 issues。若未通过，请把 issues 原文交给撰稿人返工。")
    public static class DelegateReviewTool implements Tool<DelegateReviewParam> {
        private final Budget budget;
        private final BiFunction<String, String, SubAgentRunner> runnerFactory;

        public DelegateReviewTool(Budget budget, BiFunction<String, String, SubAgentRunner> runnerFactory) {
            this.budget = budget;
            this.runnerFactory = runnerFactory;
        }

        @Override
        public String execute(DelegateReviewParam param) {
            String blocked = budget.consume("审稿人");
            if (blocked != null) return blocked;
            String command = "请审核当前草稿。\n审核重点：\n" + param.getInstruction()
                    + "\n\n完成后必须调用 submit_review 提交结构化结论。";
            return runSubAgent(budget, runnerFactory.apply(AgentFactory.CODE_REVIEWER, "REVIEW"),
                    AgentFactory.CODE_REVIEWER, "REVIEW", command, "【委托·审核】");
        }
    }

    @Data
    public static class DelegateReviewParam extends ToolParam {
        @Param(description = "给审稿人的审核重点说明") private String instruction;
    }

    /** 供测试观察：委托次数与日志。 */
    public static int delegations(Budget budget) {
        return budget.delegations();
    }
}
