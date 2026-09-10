package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentResultHandler;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.entity.MemoryMultipartFile;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * agent4j 会话运行器（skills-agent-plan 5.5 / 8.1）：同步执行一次 AgentClient 会话，
 * 收集工具调用次数与执行日志（可带阶段前缀），供各执行策略复用。
 */
@Component
public class AgentInvoker extends AgentRunner {
    @Override
    public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments) {
        return run(agent, command, attachments, null);
    }

    /** @param logPrefix 执行日志前缀（如「【调研】」），null 表示不加前缀。 */
    @Override
    public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments, String logPrefix) {
        return run(agent, command, attachments, logPrefix, 0);
    }

    /** @param maxToolCalls 工具调用上限（&le;0 表示不限制）；超限抛异常中止会话（预算护栏）。 */
    @Override
    public Outcome runWithLimit(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                                String logPrefix, int maxToolCalls) {
        return run(agent, command, attachments, logPrefix, maxToolCalls);
    }

    private Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments,
                        String logPrefix, int maxToolCalls) {
        AtomicInteger toolCalls = new AtomicInteger();
        Set<String> countedCalls = ConcurrentHashMap.newKeySet();
        List<String> executionLog = java.util.Collections.synchronizedList(new ArrayList<>());
        StringBuilder assistantText = new StringBuilder();
        AgentClientSession session = agent.createSession();
        AgentSessionResult result = (attachments == null || attachments.isEmpty()
                ? session.command(command)
                : session.command(command, attachments))
                .then(new AgentResultHandler() {
                    @Override
                    public void onMessage(String message) {
                        if (message != null) assistantText.append(message);
                    }

                    @Override
                    public void onTool(ToolDescriptor tool, ToolStatus status) {
                        if (tool == null || status == ToolStatus.PREPARING) return;
                        String key = tool.getName() + "\n" + safeCallId(tool);
                        if (status == ToolStatus.CALLING && countedCalls.add(key)) {
                            int count = toolCalls.incrementAndGet();
                            executionLog.add(prefix(logPrefix) + "调用工具：" + tool.getName());
                            if (maxToolCalls > 0 && count > maxToolCalls) {
                                // 预算护栏：超出上限即中止会话（skills-agent-plan 5.5）
                                throw new IllegalStateException("子智能体工具调用超过上限 " + maxToolCalls + " 次，已中止");
                            }
                        } else if (status == ToolStatus.COMPLETED) {
                            executionLog.add(prefix(logPrefix) + "工具完成：" + tool.getName());
                        }
                    }

                    @Override
                    public void onToolError(ToolDescriptor tool, Exception error) {
                        executionLog.add(prefix(logPrefix) + "工具失败："
                                + (tool == null ? "unknown" : tool.getName()) + " - "
                                + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                    }
                });
        result.execute();
        String response;
        try {
            response = result.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage(), exception);
        }
        // 兜底护栏：回调里抛出的异常可能被 agent4j 吞掉，这里按最终计数再判一次，
        // 保证「子智能体工具调用超限」一定能被调用方感知（方案 5.5 预算护栏）。
        if (maxToolCalls > 0 && toolCalls.get() > maxToolCalls) {
            throw new IllegalStateException("子智能体工具调用超过上限 " + maxToolCalls + " 次，已中止");
        }
        String reply = response == null || response.isBlank() ? assistantText.toString().trim() : response.trim();
        return new Outcome(reply, toolCalls.get(), String.join("\n", executionLog));
    }

    private static String prefix(String logPrefix) {
        return logPrefix == null || logPrefix.isBlank() ? "" : logPrefix;
    }

    private static String safeCallId(ToolDescriptor descriptor) {
        return descriptor == null || descriptor.getCallId() == null ? "" : descriptor.getCallId();
    }
}
