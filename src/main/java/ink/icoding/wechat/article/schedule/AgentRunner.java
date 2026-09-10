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

    public record Outcome(String reply, int toolCalls, String executionLog) {
    }
}
