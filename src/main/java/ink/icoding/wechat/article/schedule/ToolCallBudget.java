package ink.icoding.wechat.article.schedule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 工具调用预算（skills-agent-plan 5.5 预算护栏的可配置来源）。
 *
 * <p>为什么需要按阶段分档：原先三条链路统一用 {@code MAX_SUB_AGENT_TOOL_CALLS = 24}。
 * 实测 run#46（PIPELINE，任务「每日科技早报」）的调研子智能体在第 25 次调用处被中止，
 * 而它的前 25 次**全部成功**——search_web ×20 + browse_webpage ×5，零失败、零重试，
 * 是在正常地换关键词检索并逐个打开来源核对。宽口径调研的工作量本就随主题宽度增长，
 * 24 次是写作/审核类阶段的合理额度，对调研阶段却低于正常工作量；一旦触顶，
 * PIPELINE 整轮失败、COORDINATOR 的调研委托则空手而归（撰稿人拿不到简报）。
 *
 * <p>因此调研阶段单独一档。默认值仍是「防失控」的量级（40 次），
 * 配合 {@link TaskWorkspace} 的整轮总预算，失控会话依旧跑不出可预期的范围。
 */
@Component
public class ToolCallBudget {
    /**
     * 调研阶段默认额度：覆盖「多轮检索 + 逐来源精读」。
     *
     * <p>40 是按 run#46「需要 &gt;24」定的；run#62 说明它对宽口径任务仍不够——同一任务（6 条动态的
     * 每日科技早报）的调研子智能体发起 47 次调用、**前 40 次全部成功且零失败**，随后 7 次被预算拒绝，
     * 简报一个字没交出来。零失败、每次都换关键词与来源的检索不是「失控」，是正常工作量，
     * 因此按实测值上浮到 60。再宽的任务仍可能触顶，触顶后的产出由
     * {@link #TERMINAL_TOOLS} + {@link #TERMINAL_GRACE} 兜住。
     *
     * <p>长期更优的做法是在调研协议里把预算事先告诉模型（已加入 {@code AgentProtocols.RESEARCH}），
     * 让它自己安排「先交简报再继续查」，而不是靠事后拒绝。
     */
    public static final int DEFAULT_RESEARCH = 60;
    /**
     * 其余阶段（写作/配图/审核）默认额度。
     *
     * <p>24 → 36 的依据是修复后两轮实跑的**逐会话计数**（run#63 / run#68，task#2 PIPELINE）：
     * 配图阶段 26 次、审核阶段 25 次——**两个阶段的正常工作量都已经越过 24**，能跑完只是因为
     * 收尾工具宽限把最后几次放行了（run#63 日志：「预算已用尽，放行收尾工具：submit_review」；
     * run#68：「放行收尾工具：set_article_draft_cover」「放行收尾工具：save_article_draft」）。
     * 宽限是兜底，不该是常态：一旦模型在触顶后还需要一个**非收尾**工具（例如再导入一张配图），
     * 那次调用就会被拒、这一阶段的产出就没了——用户报的「超过上限 24 次，已中止」正是这个形态。
     * 写作阶段实测只用 2–7 次，36 对它是冗余，但同一档位便于理解，且整轮总额度另有约束。
     */
    public static final int DEFAULT_STAGE = 36;
    /** 协调者自身额度。 */
    public static final int DEFAULT_CHIEF = 48;
    /**
     * 整轮（所有子智能体累计）额度。
     *
     * <p>120 → 200：run#68 整轮实测 110 次，距 120 只剩 8 次；而同一轮里配图与审核都刚触到阶段上限，
     * 一旦总额度先到，后续阶段会被直接拒绝（{@code DelegateTools.Budget.consume} 会拒绝新委托）。
     * 抬到 200 后，四个阶段各跑满 36 次仍有 56 次余量，同时「跑不出可预期范围」这条护栏仍然成立。
     */
    public static final int DEFAULT_TOTAL = 200;

    /**
     * 「交出成果」的收尾工具：预算用尽后仍放行有限次（见 {@link #TERMINAL_GRACE}）。
     *
     * <p>实测（run#62，task#2「每日科技早报」，PIPELINE）：调研子智能体在 40 次预算里**全部成功**
     * （47 次尝试 / 40 次成功 / 零失败），随后连调 3 次 {@code save_research_notes} 想把简报交出来，
     * 每一次都被同一套预算拦下——**40 次成功的检索产出全部作废**，写作阶段拿不到简报，
     * 只留下一句「未产出调研简报，写作阶段将在没有它的情况下继续」。预算的目的是拦住失控的**检索**，
     * 不是拦住「把已有成果交出来」；收尾工具不产出新信息，放行的代价远小于丢掉整段工作。
     */
    public static final Set<String> TERMINAL_TOOLS = Set.of(
            "save_research_notes", "save_article_draft", "submit_review", "set_article_draft_cover");

    /**
     * 预算用尽后仍允许的收尾工具调用次数。
     *
     * <p>必须有上限：{@code save_research_notes} 是**追加**语义，放任下去模型会用重复简报刷满工作区。
     * 3 次足够覆盖「提交简报 / 补一次摘要 / 重试一次格式错误」，实测超限后模型的有效尝试正是 1–3 次。
     */
    public static final int TERMINAL_GRACE = 3;

    private final int research;
    private final int stage;
    private final int chief;
    private final int total;

    public ToolCallBudget(@Value("${app.schedule.tool-calls.research:" + DEFAULT_RESEARCH + "}") int research,
                          @Value("${app.schedule.tool-calls.stage:" + DEFAULT_STAGE + "}") int stage,
                          @Value("${app.schedule.tool-calls.chief:" + DEFAULT_CHIEF + "}") int chief,
                          @Value("${app.schedule.tool-calls.total:" + DEFAULT_TOTAL + "}") int total) {
        // 非正数会让「超限即中止」的护栏失效（AgentInvoker 里 <=0 表示不限制），
        // 配置写错时回落到默认值，而不是静默放开额度。
        this.research = positiveOr(research, DEFAULT_RESEARCH);
        this.stage = positiveOr(stage, DEFAULT_STAGE);
        this.chief = positiveOr(chief, DEFAULT_CHIEF);
        this.total = positiveOr(total, DEFAULT_TOTAL);
    }

    /** 默认预算（非 Spring 上下文构造 DelegateTools 时用，如单测）。 */
    public static ToolCallBudget defaults() {
        return new ToolCallBudget(DEFAULT_RESEARCH, DEFAULT_STAGE, DEFAULT_CHIEF, DEFAULT_TOTAL);
    }

    /** 子智能体额度：调研阶段单独一档，其余阶段共用一档。 */
    public int subAgentLimitFor(String stageCode) {
        return isResearch(stageCode) ? research : stage;
    }

    public int chiefLimit() {
        return chief;
    }

    /** 整轮（所有子智能体累计）额度。 */
    public int totalLimit() {
        return total;
    }

    private static boolean isResearch(String stageCode) {
        return stageCode != null && "RESEARCH".equalsIgnoreCase(stageCode.trim());
    }

    private static int positiveOr(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
