package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import org.springframework.stereotype.Component;

/**
 * 单智能体执行器（skills-agent-plan 5.5）：现状逻辑平移，存量任务行为不变。
 * 实现委托 {@link ArticleAiService#runScheduledAgent}，装配已在该方法内改走 AgentFactory。
 */
@Component
public class SingleAgentExecutor extends ScheduledExecutionStrategy {
    private final ArticleAiService aiService;

    public SingleAgentExecutor(ArticleAiService aiService) {
        this.aiService = aiService;
    }

    @Override
    public String mode() {
        return "SINGLE";
    }

    @Override
    public ArticleAiService.ScheduledAgentResult execute(ArticleAiService.ScheduledAgentRequest request,
                                                         TaskWorkspace workspace) throws Exception {
        ArticleAiService.ScheduledAgentResult result = aiService.runScheduledAgent(request, workspace);
        // 让调用方拿到同一份草稿状态（runScheduledAgent 内部自建 DraftState，这里把快照同步回工作区）
        ScheduledArticleTools.Draft draft = result.draft();
        workspace.draftState().adopt(draft);
        return result;
    }
}
