package ink.icoding.wechat.article.schedule;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 执行策略路由（skills-agent-plan 5.5）：按 schedule_task.execution_mode 选择执行器。
 * SINGLE/null → SingleAgentExecutor；PIPELINE → PipelineExecutor；COORDINATOR → CoordinatorExecutor。
 */
@Component
public class ScheduledExecutionRouter {
    private final SingleAgentExecutor singleAgentExecutor;
    private final PipelineExecutor pipelineExecutor;
    private final CoordinatorExecutor coordinatorExecutor;

    public ScheduledExecutionRouter(SingleAgentExecutor singleAgentExecutor, PipelineExecutor pipelineExecutor,
                                    CoordinatorExecutor coordinatorExecutor) {
        this.singleAgentExecutor = singleAgentExecutor;
        this.pipelineExecutor = pipelineExecutor;
        this.coordinatorExecutor = coordinatorExecutor;
    }

    public ScheduledExecutionStrategy strategy(String mode) {
        if (mode == null || mode.isBlank() || "SINGLE".equalsIgnoreCase(mode)) return singleAgentExecutor;
        if ("PIPELINE".equalsIgnoreCase(mode)) return pipelineExecutor;
        if ("COORDINATOR".equalsIgnoreCase(mode)) return coordinatorExecutor;
        return singleAgentExecutor;
    }

    /** 全部可用模式（前端下拉用）。 */
    public List<Map<String, String>> modes() {
        return List.of(
                Map.of("key", "SINGLE", "name", "单智能体"),
                Map.of("key", "PIPELINE", "name", "流水线"),
                Map.of("key", "COORDINATOR", "name", "协调者"));
    }
}
