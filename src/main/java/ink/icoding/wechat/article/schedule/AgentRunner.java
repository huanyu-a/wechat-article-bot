package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.entity.MemoryMultipartFile;

import java.util.List;

/**
 * agent4j 调用 seam（skills-agent-plan 8.1）：把「运行一个 AgentClient 会话」抽成可替换的基类，
 * 便于 PipelineExecutor/CoordinatorExecutor 单测时桩掉真实 LLM。
 *
 * 注意：本项目 {@code @MapperScan("ink.icoding.wechat.article")} 会把该包下的**顶层接口**注册成 MyBatis
 * mapper bean，因此本 seam 用抽象类而非接口实现（避免出现重复 bean）。
 */
public abstract class AgentRunner {
    /** 同步运行一次会话并收集产出。 */
    public abstract Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments);

    /** 带阶段日志前缀的运行（如「【调研】」）；默认忽略前缀。 */
    public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                       String logPrefix) {
        return run(agent, command, attachments);
    }

    /**
     * 带工具调用上限的运行（skills-agent-plan 5.5 预算护栏）；默认忽略上限。
     * 实现应在超限时中止会话并抛出明确异常，由调用方转为引导性文本。
     */
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls) {
        return run(agent, command, attachments, logPrefix);
    }

    /**
     * 带上限与进度上报的运行：每次计入工具调用、每产生一行执行日志都**实时**回调，
     * 使调用方在会话抛异常（超限中止 / SSE 断流 / 阶段超时）时也能留住已完成的部分——
     * 包括「卡在哪一步」的那几行日志。若等会话返回再整体取 {@link Outcome#executionLog()}，
     * 停滞/超时的那一次尝试会被整段丢弃，运行历史就只剩一行错误。
     *
     * <p>默认实现委托给 5 参版本并在结束时一次性上报——替身实现只需覆盖 5 参版本；
     * 真实实现（AgentInvoker）覆盖本方法逐次上报。
     */
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls, ProgressListener progress) {
        Outcome outcome = runWithLimit(agent, command, attachments, logPrefix, maxToolCalls);
        if (progress == null) return outcome;
        progress.toolCallCounted(outcome.toolCalls());
        for (String line : splitLines(outcome.executionLog())) progress.logLine(line);
        return outcome;
    }

    /**
     * 带**会话超时覆盖**的运行。
     *
     * <p>为什么需要按调用方覆盖：全局 {@code app.schedule.stage-timeout-seconds}（300s）是照「单个阶段会话是
     * 分钟级」定的，但协调者主编的一次会话**覆盖全部委托**——每次委托都是一次完整的子会话，自身还带停滞重试，
     * 量级完全不同。实测 run#41/42/47/48：卡点都是「主编侧无事件、子智能体正在运行」，300s 一到就把**正常
     * 工作中**的主编杀掉，整轮失败。默认实现忽略该参数（沿用实现自身的超时配置），因此替身实现无需关心。
     *
     * @param timeoutSeconds 会话硬超时（秒）；&le;0 表示沿用实现自身的配置
     */
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls, long timeoutSeconds,
                                ProgressListener progress) {
        return runWithLimit(agent, command, attachments, logPrefix, maxToolCalls, progress);
    }

    /**
     * 带**模型档案故障切换**的运行：{@code candidates} 首个为主用，其余为退路（只差模型）。
     *
     * <p>为什么需要它：会话绑定的模型在 {@code AgentClient.setModel} 时就定死了，模型级错误
     * （{@code model_not_found} / {@code no available channel} / 该渠道额度耗尽）重发多少次结果都一样。
     * 实测上游下线一个模型后只能**手工改库**才能恢复。有了这个入口，{@link AgentInvoker}
     * 就能在「本次尝试零工具调用」的前提下换下一个档案接着跑。
     *
     * <p>默认实现只用首个候选并忽略其余——替身实现与不关心切换的调用方无需改动。
     *
     * @param candidates 候选（≥1）；空列表视为调用错误
     */
    public Outcome runWithCandidates(List<Candidate> candidates, String command,
                                     List<MemoryMultipartFile> attachments, String logPrefix,
                                     int maxToolCalls, long timeoutSeconds, ProgressListener progress) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("候选智能体列表不能为空");
        }
        return runWithLimit(candidates.get(0).agent(), command, attachments, logPrefix, maxToolCalls,
                timeoutSeconds, progress);
    }

    /**
     * 一个故障切换候选：智能体 + 人类可读的档案标签（用于执行日志与终态消息）。
     *
     * <p>{@code label} 必须单独携带：agent4j 的 {@code LLMModel} 接口没有暴露模型名/档案名的 getter，
     * 事后无法从 AgentClient 反查「这一轮用的到底是哪个档案」。
     *
     * <p>{@code governor} 也必须随候选携带：候选之间**共享同一批工具实例**，而只读检索的
     * 去重/循环状态就存在工具的治理器里——{@link AgentInvoker} 需要同一个实例才能
     * 按尝试重置预算、并在重复调用时中止会话。为 null 表示该候选不做检索治理。
     */
    public record Candidate(AgentClient agent, String label, ToolCallGovernor governor) {
        /** 无治理器（编辑器链路、多数单测）：检索治理退化为直通。 */
        public Candidate(AgentClient agent, String label) {
            this(agent, label, null);
        }
    }

    /**
     * 候选多于一个时给出「故障切换候选：A → B」后缀（写进启动日志）。
     *
     * <p>只有一个候选时返回空串：没有退路就没什么可说的，不必让每行启动日志都拖着它。
     */
    public static String profileSuffix(List<Candidate> candidates) {
        if (candidates == null || candidates.size() < 2) return "";
        return "（故障切换候选：" + candidates.stream().map(Candidate::label)
                .collect(java.util.stream.Collectors.joining(" → ")) + "）";
    }

    static List<String> splitLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return List.of(text.split("\n"));
    }

    /**
     * 会话进度回调：调用方把「工具调用计数」与「执行日志」都落到持久化位置（{@link TaskWorkspace}），
     * 因此这两类信息在**失败路径**上同样可见。
     */
    public interface ProgressListener {
        /** 每计入一次工具调用回调一次（{@code delta} 通常为 1）。 */
        void toolCallCounted(int delta);

        /** 每产生一行执行日志回调一次（已含阶段前缀）。 */
        void logLine(String line);

        /**
         * 工具参数 JSON 解析失败（默认忽略，仅工作区监听器关心）。
         *
         * <p>为什么要单独计数：{@code submit_review - Failed to parse tool param JSON} 是实测最常见的
         * 一类工具失败（模型把参数写成 Markdown 代码块或漏了引号），而它**混在「工具失败」总数里
         * 看不出趋势**——协议提示词改了之后到底有没有变好，只能靠单独计数回答。
         * 参数解析失败还特别浪费：一次返工轮次可能就因为它白跑。
         *
         * @param toolName 参数解析失败的工具名
         */
        default void toolParamParseFailed(String toolName) {
        }

        /**
         * 本次会话**实际用过**的一个模型档案（默认忽略，仅工作区监听器关心）。
         *
         * <p>为什么要实时上报而不是等会话结束：档案链是「用过哪些模型」的唯一记录，
         * 而**失败路径**恰恰是最需要它的时候——实测 run#123/#124 停滞失败后
         * {@code stages_summary.profilesUsed} 为空，看不出当时用的是哪个模型、
         * 有没有试过备用档案。成功路径可以从产出的 Outcome 里拿到档案链，
         * 失败路径只能靠这条实时回调留住（与工具计数、执行日志同一理由）。
         */
        default void profileUsed(String label) {
        }

        /**
         * 只上报工具调用计数、忽略日志的监听器：用于日志由调用方事后统一追加的场景
         * （例如 COORDINATOR 的子智能体，其日志经 {@code DelegateTools} 落盘），避免重复记。
         */
        static ProgressListener toolCallsOnly(java.util.function.IntConsumer sink) {
            return new ProgressListener() {
                @Override
                public void toolCallCounted(int delta) {
                    if (sink != null) sink.accept(delta);
                }

                @Override
                public void logLine(String line) {
                    // 有意忽略：日志由调用方统一追加
                }
            };
        }
    }

    /**
     * 一次会话的产出。
     *
     * @param reply        助手最终回复
     * @param toolCalls    工具调用次数
     * @param executionLog 阶段日志（含每次调用与每次失败）
     * @param toolFailures 工具失败次数。用于终态判定：整体成功但配图/落库类工具失败时，
     *                     运行不能再记成纯粹的 SUCCESS——那会让「交付物缺图」看起来像成功。
     */
    public record Outcome(String reply, int toolCalls, String executionLog, int toolFailures,
                          List<String> profilesUsed) {
        /** 三参构造（多数替身与不关心失败数的调用方使用）：失败数默认 0。 */
        public Outcome(String reply, int toolCalls, String executionLog) {
            this(reply, toolCalls, executionLog, 0, List.of());
        }

        /** 四参构造（存量调用点）：档案链默认空。 */
        public Outcome(String reply, int toolCalls, String executionLog, int toolFailures) {
            this(reply, toolCalls, executionLog, toolFailures, List.of());
        }

        public boolean hasToolFailures() {
            return toolFailures > 0;
        }

        /** 本次运行是否发生过模型档案切换（档案链长度 > 1）。 */
        public boolean switchedProfile() {
            return profilesUsed != null && profilesUsed.size() > 1;
        }
    }
}
