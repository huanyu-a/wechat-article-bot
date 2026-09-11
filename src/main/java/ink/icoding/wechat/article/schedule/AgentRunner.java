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
    public record Outcome(String reply, int toolCalls, String executionLog, int toolFailures) {
        /** 三参构造（多数替身与不关心失败数的调用方使用）：失败数默认 0。 */
        public Outcome(String reply, int toolCalls, String executionLog) {
            this(reply, toolCalls, executionLog, 0);
        }

        public boolean hasToolFailures() {
            return toolFailures > 0;
        }
    }
}
