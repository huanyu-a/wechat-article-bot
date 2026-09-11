package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskExecutionService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final ScheduleTaskMapper mapper;
    private final TaskRunMapper runMapper;
    private final ArticleAiService aiService;
    private final ArticleService articleService;
    private final ScheduledExecutionRouter router;
    private final ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService;
    private final ink.icoding.wechat.article.common.InFlightGate inFlightGate;
    private final long staleRunHours;
    private final Map<Long, Object> taskLocks = new ConcurrentHashMap<>();

    public TaskExecutionService(ScheduleTaskMapper mapper, TaskRunMapper runMapper,
                                ArticleAiService aiService, ArticleService articleService,
                                ScheduledExecutionRouter router,
                                ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService,
                                ink.icoding.wechat.article.common.InFlightGate inFlightGate,
                                @Value("${app.schedule.stale-run-hours:" + StaleRunPolicy.DEFAULT_STALE_HOURS + "}")
                                long staleRunHours) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.aiService = aiService;
        this.articleService = articleService;
        this.router = router;
        this.markFlowRenderService = markFlowRenderService;
        this.inFlightGate = inFlightGate;
        this.staleRunHours = staleRunHours;
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
            TaskRun running = runMapper.findRunning(taskId);
            if (running != null) {
                if (!StaleRunPolicy.isStale(running, staleRunHours, LocalDateTime.now())) {
                    throw new BusinessException("任务正在执行，请勿重复启动");
                }
                // 上次进程非正常退出遗留的 RUNNING 会永久占住并发闸门；超过阈值即可判定为孤儿，
                // 就地中止后允许本次触发继续（触发路径自愈，避免必须手工改库才能恢复）。
                abortStale(running);
            }
            TaskRun run = new TaskRun();
            run.setTaskId(taskId);
            run.setTriggerType(triggerType);
            run.setStatus("RUNNING");
            run.setFetchedCount(0);
            run.setGeneratedCount(0);
            run.setToolCallCount(0);
            run.setStartedAt(LocalDateTime.now());
            runMapper.insert(run);
            // taskLocks 只是 JVM 本地锁：Quartz 以 isClustered=true 部署时，另一个实例可能在同一瞬间
            // 也通过了上面的检查。插入后按「RUNNING 中 ID 最小者为唯一属主」复核——先插入再复核不需要
            // 任何跨实例锁，且 ID 单调递增保证两个并发插入里恰好有一个赢家；落败者撤回自己刚插入的行，
            // 与「未插入就抛错」的既有语义一致（历史里不留下被拒绝的空运行）。
            TaskRun owner = runMapper.findEarliestRunning(taskId);
            if (owner != null && !owner.getId().equals(run.getId())) {
                runMapper.deleteById(run.getId());
                log.warn("任务 #{} 的并发触发被另一实例占先（属主运行 #{}），本次运行 #{} 已撤回",
                        taskId, owner.getId(), run.getId());
                throw new BusinessException("任务正在执行，请勿重复启动");
            }
            return run;
        }
    }

    /** 启动自愈：中止上次进程未正常结束时遗留的 RUNNING 运行，返回中止条数。 */
    public int reapStaleRuns() {
        List<TaskRun> running = runMapper.findAllRunning();
        int aborted = 0;
        for (TaskRun run : running) {
            if (!StaleRunPolicy.isStale(run, staleRunHours, LocalDateTime.now())) continue;
            try {
                abortStale(run);
                aborted++;
            } catch (Exception exception) {
                log.warn("中止遗留运行 #{} 失败（跳过，不影响启动）", run.getId(), exception);
            }
        }
        return aborted;
    }

    private void abortStale(TaskRun run) {
        // 比较并交换：只有该行仍是 RUNNING 才写；返回 0 说明它已在扫描间隙被正常收尾，
        // 此时不再打「判定为孤儿」的日志，避免把一次正常完成误报成孤儿中止。
        if (runMapper.abortStale(run.getId(), StaleRunPolicy.ABORT_MESSAGE, LocalDateTime.now()) == 0) return;
        log.warn("任务 #{} 的遗留运行 #{}（开始于 {}）已超过 {} 小时仍为 RUNNING，判定为孤儿并中止",
                run.getTaskId(), run.getId(), run.getStartedAt(), staleRunHours);
    }

    private TaskRun executeRun(ScheduleTask task, TaskRun run) {
        String mode = task.getExecutionMode() == null || task.getExecutionMode().isBlank()
                ? "SINGLE" : task.getExecutionMode();
        run.setMode(mode);
        // 在飞闸门（见 InFlightGate）：一次运行只占一个名额，嵌套的子智能体会话在同一个运行内因而不重复占位。
        String gateLabel = "任务 #" + task.getId() + " 的运行 #" + run.getId();
        ink.icoding.wechat.article.common.InFlightGate.Lease lease = inFlightGate.acquire(gateLabel);
        if (lease == null) {
            // 取不到额度就**明确失败**（方案的硬要求：排队但不能静默卡住）。
            // 这里不走「任务正在执行」那类拒绝——运行行已经插入，必须留下可读的终态说明。
            run.setStatus("FAILED");
            run.setMessage("LLM 并发额度等待超时（上限 " + inFlightGate.limit() + "，已等待 "
                    + inFlightGate.acquireTimeoutSeconds() + " 秒）：同一时刻进行中的智能体运行占满了名额。"
                    + "请稍后重试，或调大 app.llm.max-in-flight（须低于上游网关的并发上限）。");
            runMapper.finishRun(run);
            mapper.touchRun(task.getId());
            return runMapper.selectById(run.getId());
        }
        TaskWorkspace workspace = null;
        try {
            ArticleAiService.ScheduledAgentRequest request = new ArticleAiService.ScheduledAgentRequest(
                    task.getAccountId(), task.getCreatedBy(), task.getCoverAssetId(), task.getTimezone(),
                    task.getOutputMode(), task.getAiPrompt(),
                    ArticleAiService.parseSkillIds(task.getSkillIds()),
                    ScheduleTaskService.parseStageAgents(task.getStageAgents()),
                    task.getMaxRevisionRounds());
            ScheduledExecutionStrategy strategy = router.strategy(mode);
            // SINGLE 由 ArticleAiService 内部自建工作区状态（adopt 回填），无需提前解析引擎
            workspace = TaskWorkspace.create(task.getCoverAssetId(),
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
            // 工具调用数取工作区累计值：COORDINATOR 的子智能体调用量只有累计在工作区里才统计得到
            // （执行结果只带 chief 自身的计数），SINGLE/PIPELINE 两条链路与它逐次同步。
            run.setToolCallCount(workspace.toolCallCount());
            run.setExecutionLog(result.executionLog());

            if ("WECHAT_DRAFT".equals(task.getOutputMode())) articleService.syncDraft(article.getId());
            if ("AUTO_PUBLISH".equals(task.getOutputMode())) articleService.publish(article.getId());

            RunCompletion completion = completion(workspace.toolFailureCount(), result.message());
            run.setStatus(completion.status());
            run.setMessage(completion.message());
        } catch (Exception error) {
            run.setStatus("FAILED");
            // 失败也要留住已产生的分阶段日志：否则运行历史只剩一行错误，看不出卡在哪一阶段
            // （PIPELINE 的失败排查几乎只能依赖这份日志，例如「【调研】智能体会话超时」）
            if (workspace != null) {
                String partial = workspace.executionLogText();
                if (!partial.isBlank()) run.setExecutionLog(partial);
                // 失败也要留住已发生的工具调用数：运行历史里「调了几次工具」不该在失败时永远是 0
                run.setToolCallCount(workspace.toolCallCount());
            }
            run.setMessage(trimMessage(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage()));
        } finally {
            // 名额在整个运行期间持有（含渲染与落库），先释放再收尾，避免终态写入拖长占用
            lease.close();
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

    /**
     * 终态判定：有工具失败时不能记成干净的 SUCCESS。「配图工具失败但整次运行成功」会让用户
     * 以为交付物完整——实测 {@code generate_image} 因描述超长落库失败，配图没进文章却记了 SUCCESS。
     *
     * <p>独立成函数是为了让这条规则可被单测钉住（{@code TaskRunCompletionTest}）：它只依赖
     * 「工具失败数 + 智能体回复」两个值，不依赖运行链路，因此不必为了验证它而跑一次真实会话。
     */
    static RunCompletion completion(int toolFailures, String agentMessage) {
        String message = agentMessage == null ? "" : agentMessage;
        if (toolFailures > 0) {
            return new RunCompletion("SUCCESS_WITH_WARNINGS",
                    trimMessage("有 " + toolFailures + " 次工具调用失败（详见执行日志），交付内容可能不完整。" + message));
        }
        return new RunCompletion("SUCCESS", trimMessage(message));
    }

    private static String trimMessage(String value) {
        if (value == null || value.isBlank()) return "任务执行结束";
        return value.length() > 60_000 ? value.substring(0, 60_000) : value;
    }

    /** 运行终态（状态 + 说明）。 */
    record RunCompletion(String status, String message) {
    }
}
