package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskExecutionService {
    private final ScheduleTaskMapper mapper;
    private final TaskRunMapper runMapper;
    private final ArticleAiService aiService;
    private final ArticleService articleService;
    private final ScheduledExecutionRouter router;
    private final ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService;
    private final Map<Long, Object> taskLocks = new ConcurrentHashMap<>();

    public TaskExecutionService(ScheduleTaskMapper mapper, TaskRunMapper runMapper,
                                ArticleAiService aiService, ArticleService articleService,
                                ScheduledExecutionRouter router,
                                ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.aiService = aiService;
        this.articleService = articleService;
        this.router = router;
        this.markFlowRenderService = markFlowRenderService;
    }

    /** Starts a manual execution without keeping the HTTP request open for the entire agent run. */
    public TaskRun start(Long taskId, String triggerType) {
        ScheduleTask task = requiredTask(taskId);
        TaskRun run = createRun(taskId, triggerType);
        CompletableFuture.runAsync(() -> executeRun(task, run));
        return run;
    }

    /** Executes synchronously for Quartz, so DisallowConcurrentExecution remains effective. */
    public TaskRun execute(Long taskId, String triggerType) {
        ScheduleTask task = requiredTask(taskId);
        TaskRun run = createRun(taskId, triggerType);
        return executeRun(task, run);
    }

    private ScheduleTask requiredTask(Long taskId) {
        ScheduleTask task = mapper.findById(taskId);
        if (task == null) throw new BusinessException("定时任务不存在");
        return task;
    }

    private TaskRun createRun(Long taskId, String triggerType) {
        synchronized (taskLocks.computeIfAbsent(taskId, ignored -> new Object())) {
            if (runMapper.findRunning(taskId) != null) throw new BusinessException("任务正在执行，请勿重复启动");
            TaskRun run = new TaskRun();
            run.setTaskId(taskId);
            run.setTriggerType(triggerType);
            run.setStatus("RUNNING");
            run.setFetchedCount(0);
            run.setGeneratedCount(0);
            run.setToolCallCount(0);
            run.setStartedAt(LocalDateTime.now());
            runMapper.insert(run);
            return run;
        }
    }

    private TaskRun executeRun(ScheduleTask task, TaskRun run) {
        String mode = task.getExecutionMode() == null || task.getExecutionMode().isBlank()
                ? "SINGLE" : task.getExecutionMode();
        run.setMode(mode);
        try {
            ArticleAiService.ScheduledAgentRequest request = new ArticleAiService.ScheduledAgentRequest(
                    task.getAccountId(), task.getCreatedBy(), task.getCoverAssetId(), task.getTimezone(),
                    task.getOutputMode(), task.getAiPrompt(),
                    ArticleAiService.parseSkillIds(task.getSkillIds()),
                    ScheduleTaskService.parseStageAgents(task.getStageAgents()),
                    task.getMaxRevisionRounds());
            ScheduledExecutionStrategy strategy = router.strategy(mode);
            // SINGLE 由 ArticleAiService 内部自建工作区状态（adopt 回填），无需提前解析引擎
            TaskWorkspace workspace = TaskWorkspace.create(task.getCoverAssetId(),
                    "SINGLE".equals(mode) ? null : resolveLayoutEngine(request));
            ArticleAiService.ScheduledAgentResult result = strategy.execute(request, workspace);
            run.setStagesSummary(workspaceSummary(workspace));
            // MARKFLOW 延迟渲染：交付前统一渲染一次（skills-agent-plan 5.10.4）。
            // SINGLE 链路已在 runScheduledAgent 内渲染（rendered=true 时幂等跳过），此处覆盖 PIPELINE/COORDINATOR。
            workspace.draftState().renderBeforeDelivery(markFlowRenderService);
            ScheduledArticleTools.Draft draft = workspace.draftState().snapshot();
            ArticleService.ArticleRequest articleRequest = new ArticleService.ArticleRequest(task.getAccountId(),
                    draft.title(), draft.author(), draft.digest(), draft.contentHtml(),
                    draft.coverAssetId(), null, draft.sourceUrl(), null, null,
                    draft.layoutEngine() == null ? null : draft.layoutEngine().name(), draft.contentMarkdown());
            Article article = articleService.createForTask(articleRequest, task.getCreatedBy());
            run.setArticleId(article.getId());
            run.setGeneratedCount(1);
            run.setToolCallCount(result.toolCalls());
            run.setExecutionLog(result.executionLog());

            if ("WECHAT_DRAFT".equals(task.getOutputMode())) articleService.syncDraft(article.getId());
            if ("AUTO_PUBLISH".equals(task.getOutputMode())) articleService.publish(article.getId());

            run.setStatus("SUCCESS");
            run.setMessage(trimMessage(result.message()));
        } catch (Exception error) {
            run.setStatus("FAILED");
            run.setMessage(trimMessage(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage()));
        } finally {
            runMapper.finishRun(run);
            mapper.touchRun(task.getId());
        }
        return runMapper.selectById(run.getId());
    }

    /** 工作区摘要（best-effort）：写入 task_run.stages_summary。 */
    private String workspaceSummary(TaskWorkspace workspace) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(workspace.summary());
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 排版引擎解析（SINGLE 走 ArticleAiService 内部组装，其余执行器需要提前知道引擎以构造工作区）。
     * 不吞异常：MARKFLOW 技能绑定但渲染服务不可用时必须让任务以明确错误失败，
     * 而不是静默回落 PROMPT（静默换引擎会产出完全不同的版式，违背用户预期，方案 5.10.2）。
     */
    private ink.icoding.wechat.article.skill.LayoutEngine resolveLayoutEngine(
            ArticleAiService.ScheduledAgentRequest request) {
        return aiService.resolveLayoutEngine(request);
    }

    private String trimMessage(String value) {
        if (value == null || value.isBlank()) return "任务执行结束";
        return value.length() > 60_000 ? value.substring(0, 60_000) : value;
    }
}
