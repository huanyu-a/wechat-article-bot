package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ArticleAiService;

/**
 * 定时任务执行策略（skills-agent-plan 5.5）。
 * 实现：SINGLE（现状迁移）/ PIPELINE（代码编排）/ COORDINATOR（chief 自主委托）。
 *
 * 注：用抽象类而非接口——本项目 {@code @MapperScan("ink.icoding.wechat.article")} 会把该包下所有
 * 顶层接口注册为 MyBatis mapper bean，非 Mapper 的顶层接口会造成重复 bean 或注入歧义。
 */
public abstract class ScheduledExecutionStrategy {
    /** 执行模式名（与 schedule_task.execution_mode 取值一致）。 */
    public abstract String mode();

    public abstract ArticleAiService.ScheduledAgentResult execute(ArticleAiService.ScheduledAgentRequest request,
                                                                  TaskWorkspace workspace) throws Exception;
}
