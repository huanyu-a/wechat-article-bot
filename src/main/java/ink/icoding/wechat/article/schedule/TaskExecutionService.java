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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TaskExecutionService {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);
    /** 本 JVM 的调度实例标识（I2）；Quartz 多实例部署时用于区分运行属主。 */
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final ScheduleTaskMapper mapper;
    private final TaskRunMapper runMapper;
    private final ArticleAiService aiService;
    private final ArticleService articleService;
    private final ScheduledExecutionRouter router;
    private final ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService;
    private final ink.icoding.wechat.article.common.InFlightGate inFlightGate;
    /** 素材查询（封面归属校验用，见 {@link #requireCoverOwnership}）。 */
    private final ink.icoding.wechat.article.asset.AssetService assetService;
    private final long staleRunHours;
    /** 心跳超时（秒，I2）：心跳早于此值即判定属主实例失联，运行可被中止。 */
    private final long heartbeatTimeoutSeconds;
    /** 运行中进度落库间隔（秒，I10）；同时充当心跳间隔。 */
    private final long progressFlushSeconds;
    /**
     * 周期性刷新运行进度/心跳的调度器（I10/I2）。
     *
     * <p>为什么不是单线程：每个在飞运行各占一条周期任务，若共用一个线程，某次落库被数据库阻塞
     * （如连接超时 30 秒）会把其余运行的心跳一起卡住；心跳一旦超过
     * {@code heartbeat-timeout-seconds}，别的实例就会把这些**正常执行中**的运行误判为孤儿并中止，
     * 这是比「进度刷新延迟」严重得多的后果。线程数按并发运行上限再加一条余量，避免这种队头阻塞。
     */
    private final ScheduledExecutorService progressExecutor;
    /**
     * 手工触发执行线程池。
     *
     * <p>为什么不用 {@code CompletableFuture.runAsync}：它跑在**公共 ForkJoinPool** 上
     * （并行度 = CPU 核数 - 1）。一次定时运行要跑几分钟到半小时，且全程**阻塞**在 SSE 等待上；
     * 几次手工触发就能把公共池占满，而同一进程里其它用到公共池的并行工作（流处理、并行查询）
     * 会被一起饿死——表现为「点了几次运行之后，页面别的功能也卡住了」。
     *
     * <p>线程数按并发运行上限 + 1：名额由 {@link ink.icoding.wechat.article.common.InFlightGate}
     * 统一管，这里只需保证「拿到名额的运行都有线程可用」，多出的一条用于覆盖正在收尾（渲染/落库）的那次。
     * 用有界队列 + CallerRunsPolicy 而不是无界队列：队列满时在调用线程执行，把背压交回调用方，
     * 而不是无声堆积任务。
     */
    private final ExecutorService manualRunExecutor;
    private final Map<Long, Object> taskLocks = new ConcurrentHashMap<>();

    public TaskExecutionService(ScheduleTaskMapper mapper, TaskRunMapper runMapper,
                                ArticleAiService aiService, ArticleService articleService,
                                ScheduledExecutionRouter router,
                                ink.icoding.wechat.article.skill.MarkFlowRenderService markFlowRenderService,
                                ink.icoding.wechat.article.common.InFlightGate inFlightGate,
                                ink.icoding.wechat.article.asset.AssetService assetService,
                                @Value("${app.schedule.stale-run-hours:" + StaleRunPolicy.DEFAULT_STALE_HOURS + "}")
                                long staleRunHours,
                                @Value("${app.schedule.heartbeat-timeout-seconds:"
                                        + StaleRunPolicy.DEFAULT_HEARTBEAT_TIMEOUT_SECONDS + "}")
                                long heartbeatTimeoutSeconds,
                                @Value("${app.schedule.progress-flush-seconds:15}")
                                long progressFlushSeconds) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.aiService = aiService;
        this.articleService = articleService;
        this.router = router;
        this.markFlowRenderService = markFlowRenderService;
        this.inFlightGate = inFlightGate;
        this.assetService = assetService;
        this.staleRunHours = staleRunHours;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        this.progressFlushSeconds = progressFlushSeconds;
        this.progressExecutor = Executors.newScheduledThreadPool(
                Math.max(2, inFlightGate.limit() + 1), task -> {
                    Thread thread = new Thread(task, "task-run-progress");
                    thread.setDaemon(true);
                    return thread;
                });
        this.manualRunExecutor = new ThreadPoolExecutor(
                Math.max(2, inFlightGate.limit() + 1), Math.max(2, inFlightGate.limit() + 1),
                60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64), task -> {
                    Thread thread = new Thread(task, "task-run-manual");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @jakarta.annotation.PreDestroy
    public void shutdownProgressExecutor() {
        progressExecutor.shutdownNow();
        manualRunExecutor.shutdownNow();
    }

    /** Starts a manual execution without keeping the HTTP request open for the entire agent run. */
    public TaskRun start(Long taskId, String triggerType) {
        ScheduleTask task = requiredTask(taskId);
        TaskRun run = createRun(task, triggerType);
        manualRunExecutor.execute(() -> executeRun(task, run));
        return run;
    }

    /** Executes synchronously for Quartz, so DisallowConcurrentExecution remains effective. */
    public TaskRun execute(Long taskId, String triggerType) {
        ScheduleTask task = requiredTask(taskId);
        TaskRun run = createRun(task, triggerType);
        return executeRun(task, run);
    }

    private ScheduleTask requiredTask(Long taskId) {
        ScheduleTask task = mapper.findById(taskId);
        if (task == null) throw new BusinessException("定时任务不存在");
        return task;
    }

    /**
     * 执行模式：任务配置为空时按 SINGLE（存量任务的默认模式）。
     *
     * <p>解析独立成方法，是因为它现在要在**插入运行行之前**用到（见 {@link #createRun}），
     * 而 {@code executeRun} 里也仍要用同一份判据——两处各写一遍迟早会漂移。
     */
    static String resolveMode(ScheduleTask task) {
        return task.getExecutionMode() == null || task.getExecutionMode().isBlank()
                ? "SINGLE" : task.getExecutionMode();
    }

    private TaskRun createRun(ScheduleTask task, String triggerType) {
        Long taskId = task.getId();
        synchronized (taskLocks.computeIfAbsent(taskId, ignored -> new Object())) {
            TaskRun running = runMapper.findRunning(taskId);
            if (running != null) {
                if (!StaleRunPolicy.isStale(running, staleRunHours, LocalDateTime.now(),
                        heartbeatTimeoutSeconds)) {
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
            // MODE 在**插入前**就写好：此前它只在 executeRun 里赋值，而运行行在 createRun 里就已落库，
            // 于是「刚触发、进度还没刷过」的运行在库里 MODE 为 NULL（实测观察到），
            // 排查时看不出这一轮走的是 SINGLE 还是 COORDINATOR——而这正是最需要知道的第一件事。
            run.setMode(resolveMode(task));
            run.setFetchedCount(0);
            run.setGeneratedCount(0);
            run.setToolCallCount(0);
            run.setInstanceId(INSTANCE_ID);
            LocalDateTime startedAt = LocalDateTime.now();
            run.setStartedAt(startedAt);
            // 初始心跳 = 开始时间：即便执行线程还没进入周期刷新，别的实例也不会把这条新运行误判为孤儿（I2）
            run.setHeartbeatAt(startedAt);
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
            if (!StaleRunPolicy.isStale(run, staleRunHours, LocalDateTime.now(), heartbeatTimeoutSeconds)) {
                continue;
            }
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
        // 判据要说清楚：心跳判据（属主失联）与时间判据（旧行无心跳）的自愈窗口差着数量级，
        // 只写「超过 N 小时」会把分钟级的心跳回收误报成小时级，排查时对不上账。
        String reason = run.getHeartbeatAt() == null
                ? "开始于 " + run.getStartedAt() + " 已超过 " + staleRunHours + " 小时"
                : "心跳停止于 " + run.getHeartbeatAt() + "（超过 " + heartbeatTimeoutSeconds + " 秒）";
        log.warn("任务 #{} 的遗留运行 #{}（实例 {}，{}）仍为 RUNNING，判定为孤儿并中止",
                run.getTaskId(), run.getId(), run.getInstanceId(), reason);
    }

    private TaskRun executeRun(ScheduleTask task, TaskRun run) {
        // MODE 已在 createRun 里落库（见那里的说明）；这里再赋一次是为了让 executeRun 自洽——
        // 它可能被单测直接调用，也可能收到一个手工构造的 TaskRun。
        run.setMode(resolveMode(task));
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
        // 周期把运行中的进度与心跳写回库（I10/I2）：定时线程读这里的最新工作区，故用引用持有。
        AtomicReference<TaskWorkspace> liveWorkspace = new AtomicReference<>();
        ScheduledFuture<?> progressFlush = scheduleProgressFlush(run, liveWorkspace);
        try {
            ArticleAiService.ScheduledAgentRequest request = new ArticleAiService.ScheduledAgentRequest(
                    task.getAccountId(), task.getCreatedBy(), task.getCoverAssetId(), task.getTimezone(),
                    task.getOutputMode(), task.getAiPrompt(),
                    ArticleAiService.parseSkillIds(task.getSkillIds()),
                    ScheduleTaskService.parseStageAgents(task.getStageAgents()),
                    task.getMaxRevisionRounds());
            ScheduledExecutionStrategy strategy = router.strategy(run.getMode());
            // SINGLE 由 ArticleAiService 内部自建工作区状态（adopt 回填），无需提前解析引擎
            workspace = TaskWorkspace.create(task.getCoverAssetId(),
                    "SINGLE".equals(run.getMode()) ? null : resolveLayoutEngine(request));
            liveWorkspace.set(workspace);
            ArticleAiService.ScheduledAgentResult result = strategy.execute(request, workspace);
            renderAndSummarize(workspace, markFlowRenderService, run);
            ScheduledArticleTools.Draft draft = workspace.draftState().snapshot();
            // 封面归属校验**在这里**做（三条链路统一），而不是各执行器各做一次：
            // 此前只有 SINGLE 链路校验（ArticleAiService.runScheduledAgent），PIPELINE / COORDINATOR
            // 产出的文章可以带**别的公众号**的封面素材——把 A 号文章封面发到 B 号是明确的越权交付。
            // 放在这里还有个好处：位置就在落库之前，是「交付前最后一道」的天然位置。
            requireCoverOwnership(task.getAccountId(), draft.coverAssetId());
            ArticleService.ArticleRequest articleRequest = new ArticleService.ArticleRequest(task.getAccountId(),
                    draft.title(), draft.author(), draft.digest(), draft.contentHtml(),
                    draft.coverAssetId(), null, draft.sourceUrl(), null, null,
                    draft.layoutEngine() == null ? null : draft.layoutEngine().name(), draft.contentMarkdown(),
                    draft.themeAccent(), draft.themeDark());
            Article article = articleService.createForTask(articleRequest, task.getCreatedBy());
            run.setArticleId(article.getId());
            run.setGeneratedCount(1);
            // 工具调用数取工作区累计值：COORDINATOR 的子智能体调用量只有累计在工作区里才统计得到
            // （执行结果只带 chief 自身的计数），SINGLE/PIPELINE 两条链路与它逐次同步。
            run.setToolCallCount(workspace.toolCallCount());
            run.setExecutionLog(result.executionLog());

            if ("WECHAT_DRAFT".equals(task.getOutputMode())) articleService.syncDraft(article.getId());
            if ("AUTO_PUBLISH".equals(task.getOutputMode())) articleService.publish(article.getId());

            RunCompletion completion = completion(workspace.toolFailureCount(),
                    workspace.degradationCount(), workspace.draftState().renderWarnings(),
                    workspace.draftState().saveWarnings(), workspace.profilesUsed(),
                    workspace.toolParamParseFailureCount(), result.message());
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
                // 摘要同理：失败路径同样要有一份，否则「失败 + 无摘要」比「失败 + 半份摘要」更难排查
                run.setStagesSummary(workspaceSummary(workspace));
            }
            run.setMessage(trimMessage(failureMessage(error)));
        } finally {
            // 先停心跳/进度刷写，再收尾：避免收尾后又被定时线程写回一行 RUNNING 快照
            if (progressFlush != null) progressFlush.cancel(false);
            try {
                // 先落终态再释放名额：反过来（先释放）会让这条运行在「已不占名额、却仍是 RUNNING」的窗口里
                // 被其它实例的扫描看到，若此刻进程被杀，运行历史里就多一条「孤儿中止」，而它其实已正常收尾。
                runMapper.finishRun(run);
                mapper.touchRun(task.getId());
            } finally {
                // 名额在整个运行期间持有（含渲染与落库）；放在 finally 里保证收尾写库失败时也一定归还
                lease.close();
            }
        }
        return runMapper.selectById(run.getId());
    }

    /**
     * 运行中进度周期落库（I10）与属主心跳（I2）。
     *
     * <p>此前仅在收尾时写一次 {@code execution_log}/{@code tool_call_count}：COORDINATOR 长任务（十几分钟）
     * 在运行期间历史里永远是「0 次工具调用、空日志」，前端看不到任何进展；进程被杀时这些进度更是全部丢失。
     * 现在每 {@code progress-flush-seconds} 秒刷一次，同时刷新心跳——心跳停止即证明属主实例已失联，
     * 其它实例可据此把孤儿自愈窗口从「开始时间 + 3h」压到分钟级。
     *
     * <p>工作区尚未建立（如布局引擎解析阶段）时只刷心跳：此时同样需要宣告属主存活。
     * 整个过程 best-effort——数据库抖动不能影响主执行流程。
     */
    private ScheduledFuture<?> scheduleProgressFlush(TaskRun run, AtomicReference<TaskWorkspace> liveWorkspace) {
        long interval = Math.max(1L, progressFlushSeconds);
        try {
            return progressExecutor.scheduleWithFixedDelay(() -> {
                try {
                    TaskWorkspace workspace = liveWorkspace.get();
                    LocalDateTime now = LocalDateTime.now();
                    if (workspace == null) {
                        runMapper.updateHeartbeat(run.getId(), now);
                    } else {
                        runMapper.updateProgress(run.getId(), run.getMode(), workspace.executionLogText(),
                                workspace.toolCallCount(), now);
                    }
                } catch (Exception exception) {
                    log.warn("刷新运行 #{} 的进度/心跳失败（跳过本次，不影响执行）", run.getId(), exception);
                }
            }, interval, interval, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("启动运行 #{} 的进度刷写失败（不影响执行）", run.getId(), exception);
            return null;
        }
    }

    /**
     * 交付前渲染 + 把工作区摘要写进运行记录。
     *
     * <p>MARKFLOW 延迟渲染：交付前统一渲染一次（skills-agent-plan 5.10.4）。SINGLE 链路已在
     * {@code runScheduledAgent} 内渲染（{@code rendered=true} 时幂等跳过），此处覆盖 PIPELINE/COORDINATOR。
     *
     * <p>两件事**必须按这个顺序**、且不可拆开调用：{@code stages_summary.renderWarnings} 正是渲染的产出
     * （{@code DraftState.renderWarnings}）。先取摘要再渲染会得到一个**结构性恒为 0** 的字段——
     * PIPELINE/COORDINATOR 从不提前渲染，于是 run#71 出现「摘要写着 0 处渲染降级、运行说明写着 1 处」
     * 的自相矛盾，排查时会先怀疑渲染服务，而不是这个字段本身。
     *
     * <p>独立成包级方法是为了让这条顺序被单测钉死：它只依赖渲染服务与工作区两个对象。
     */
    static void renderAndSummarize(TaskWorkspace workspace,
                                   ink.icoding.wechat.article.skill.MarkFlowRenderService renderService,
                                   TaskRun run) {
        workspace.draftState().renderBeforeDelivery(renderService);
        run.setStagesSummary(workspaceSummary(workspace));
    }

    /** 工作区摘要（best-effort）：写入 task_run.stages_summary。 */
    static String workspaceSummary(TaskWorkspace workspace) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(workspace.summary());
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 封面归属校验：任务选定公众号后，封面素材必须属于同一个公众号。
     *
     * <p>为什么必须校验：素材库按公众号隔离，封面是**对外可见**的内容——把 A 号素材当 B 号封面，
     * 既是越权使用，也可能泄露未发布的视觉素材。此前只有 SINGLE 链路校验，
     * PIPELINE / COORDINATOR 两条链路产出的文章可以带别的公众号的封面。
     *
     * <p>素材本身查不到（已被删除）时**放行**：那是「素材被删」而不是「归属错误」，
     * 让后续的落库路径按既有逻辑处理（快照里的 coverAssetId 指向不存在的素材，
     * 由 ArticleService 决定是忽略还是报错），不在这里越权替它判断。
     *
     * @param accountId    任务目标公众号；为 null（仅创建本地文章）时不校验
     * @param coverAssetId 封面素材 id；为 null 时无封面，不校验
     */
    void requireCoverOwnership(Long accountId, Long coverAssetId) {
        if (accountId == null || coverAssetId == null) return;
        ink.icoding.wechat.article.asset.Asset cover;
        try {
            cover = assetService.required(coverAssetId);
        } catch (Exception missing) {
            log.warn("封面素材 {} 查询失败（可能已被删除），跳过归属校验", coverAssetId, missing);
            return;
        }
        if (cover.getAccountId() != null && !accountId.equals(cover.getAccountId())) {
            throw new BusinessException("智能体选择的封面素材不属于任务目标公众号");
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
     * <p>「降级」与「工具失败」分开表述：降级是某个阶段整个没做完（如调研阶段会话中止，
     * 写作阶段仍据任务要求完成了文章），说成「N 次工具调用失败」与实情不符。
     * 两者都让终态变成 SUCCESS_WITH_WARNINGS——交付确实打了折扣，不该显示为干净的绿色对勾。
     *
     * <p>独立成函数是为了让这条规则可被单测钉住（{@code TaskRunCompletionTest}）：它只依赖
     * 「工具失败数 + 降级数 + 渲染警告 + 保存警告 + 智能体回复」五个值，不依赖运行链路，因此不必为了验证它而跑一次真实会话。
     *
     * @param renderWarnings MarkFlow 渲染降级警告（{@code RenderResult.warnings}）：非空说明文章的版式
     *                       **没有完全复刻**（产物里留着未识别的 {@code <steps>}/{@code :::} 等）。
     *                       这类降级此前完全静默——渲染器把语法当普通文字输出，落库的 HTML 看起来「有样式」，
     *                       只有量过结构才知道版式没了。
     * @param saveWarnings   保存降级警告（{@code DraftState.saveWarnings}）：标题/摘要超长被截断。
     *                       与渲染降级分开表述——前者是「内容被改短了」，后者是「版式没复刻」，
     *                       混成一句话会让排查的人找错方向。这类降级此前是**硬失败**（直接抛异常），
     *                       实测因此丢掉过整篇文章（run#85/#89 的 40 次成功检索全部作废）。
     */
    static RunCompletion completion(int toolFailures, int degradations, java.util.List<String> renderWarnings,
                                    java.util.List<String> saveWarnings, String agentMessage) {
        return completion(toolFailures, degradations, renderWarnings, saveWarnings, java.util.List.of(),
                agentMessage);
    }

    /**
     * 终态判定（含模型档案切换）。
     *
     * <p>档案切换单独占一条 warning 而不是并进上面任何一条：它既不是「工具失败」也不是「阶段中止」——
     * 整轮交付**可能是完整**的，只是过程里换过一次模型。但对排查的人来说这是关键信息：
     * 交付物质量与耗时都受它影响（换上的档案能力/速度不同），且它出现就说明主用档案当时不可用。
     * 不记的话，一次「换了模型但成功」的运行在历史里与「一切照常」完全一样。
     *
     * @param profilesUsed 本次运行实际用过的模型档案（按首次使用顺序）；长度 &gt;1 即发生过切换
     */
    static RunCompletion completion(int toolFailures, int degradations, java.util.List<String> renderWarnings,
                                    java.util.List<String> saveWarnings, java.util.List<String> profilesUsed,
                                    String agentMessage) {
        return completion(toolFailures, degradations, renderWarnings, saveWarnings, profilesUsed, 0, agentMessage);
    }

    /**
     * 终态判定（含模型档案切换与工具参数格式失败）。
     *
     * @param toolParamParseFailures 其中属于「工具参数不是合法 JSON」的次数。单独提示的原因：
     *        它是实测最常见的一类工具失败，但完全被「N 次工具调用失败」这句话吞掉了——
     *        用户看不出「失败集中在参数格式」这个可操作的信号（该改提示词/该简化参数结构），
     *        也看不出协议补了 JSON 示例之后有没有变好。
     */
    static RunCompletion completion(int toolFailures, int degradations, java.util.List<String> renderWarnings,
                                    java.util.List<String> saveWarnings, java.util.List<String> profilesUsed,
                                    int toolParamParseFailures, String agentMessage) {
        String message = agentMessage == null ? "" : agentMessage;
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (toolFailures > 0) {
            String detail = toolParamParseFailures > 0
                    ? "（其中 " + toolParamParseFailures + " 次是工具参数不是合法 JSON）" : "";
            warnings.add("有 " + toolFailures + " 次工具调用失败" + detail);
        }
        if (degradations > 0) warnings.add("有 " + degradations + " 个阶段中止并已按现有产出继续");
        if (renderWarnings != null && !renderWarnings.isEmpty()) {
            warnings.add("有 " + renderWarnings.size() + " 处 MarkFlow 渲染降级（" + renderWarnings.get(0) + "）");
        }
        if (saveWarnings != null && !saveWarnings.isEmpty()) {
            warnings.add("有 " + saveWarnings.size() + " 处保存降级（" + saveWarnings.get(0) + "）");
        }
        if (profilesUsed != null && profilesUsed.size() > 1) {
            warnings.add("期间切换过模型档案（" + String.join(" → ", profilesUsed)
                    + "）：主用档案不可用或停滞，已自动换用备用档案继续");
        }
        if (warnings.isEmpty()) return new RunCompletion("SUCCESS", trimMessage(message));
        return new RunCompletion("SUCCESS_WITH_WARNINGS",
                trimMessage(String.join("，", warnings) + "（详见执行日志），交付内容可能不完整。" + message));
    }

    /** 兼容重载：无保存降级警告。 */
    static RunCompletion completion(int toolFailures, int degradations, java.util.List<String> renderWarnings,
                                    String agentMessage) {
        return completion(toolFailures, degradations, renderWarnings, java.util.List.of(), agentMessage);
    }

    /** 兼容重载：无渲染降级警告、无保存降级警告。 */
    static RunCompletion completion(int toolFailures, int degradations, String agentMessage) {
        return completion(toolFailures, degradations, java.util.List.of(), java.util.List.of(), agentMessage);
    }

    private static String trimMessage(String value) {
        if (value == null || value.isBlank()) return "任务执行结束";
        return value.length() > 60_000 ? value.substring(0, 60_000) : value;
    }

    /**
     * 失败终态的可读说明：按**失败类别**给出「发生了什么 + 下一步怎么办」，而不是抛原始异常文本。
     *
     * <p>为什么必须分类：此前所有失败都只写一行原始异常（如
     * {@code 子智能体工具调用超过上限 60 次，已中止。预算已用尽…}），用户看到的是「任务失败」加一段
     * 内部术语，既不知道属于哪一类问题，也不知道该调哪个参数。实测排查时最常见的四个问题正是：
     * 「是不是卡住了」「是不是模型挂了」「是不是额度不够」「为什么没提交草稿」——这四类各有一句人话解释。
     *
     * <p>分类**只加说明，不改写原文**：原始异常是唯一的现场证据（含上游返回的 code），
     * 覆盖掉它会让排查失去依据。因此格式是「【类别】人话解释 原始信息」。
     *
     * <p>与 {@link AgentInvoker} 的失败分类保持同源：那边决定「重试还是换档案」，
     * 这边决定「写给用户看的话」，判据一致才不会出现「日志说换模型、终态说超时」的矛盾。
     */
    static String failureMessage(Throwable error) {
        if (error == null) return "任务执行结束";
        String raw = error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
        String hint = failureHint(error);
        return hint == null ? raw : hint + " 原始信息：" + raw;
    }

    /** 失败类别的可读解释；无法归类时返回 null（原样输出原始信息）。 */
    private static String failureHint(Throwable error) {
        if (hasCause(error, StageTimeoutException.class)) {
            // 措辞必须说「机制」而不是「已发生」：实测 run#123/#124 是**已调用 33/21 次工具**后的停滞，
            // 此时按安全边界（可能已生图/落库）**有意不切换档案**，switchedProfile=false、profilesUsed 为空。
            // 原措辞写「系统已自动尝试切换备用模型档案」是谎报——排查的人会去找一条不存在的切换记录。
            // 判据是「有没有调用过**有副作用**的工具」（见 AgentInvoker 的 paidSideEffect），
            // 只读检索不算：实测 run#20 的调研阶段调了 24 次只读工具后仍切换了档案。
            // 旧措辞写「已调用过工具」会把这种正常切换说成「不会切换」，同样误导排查。
            return "【会话停滞】模型长时间没有返回任何内容，已按无进展中止（不再白等整个超时）。"
                    + "常见原因是上游网关抖动或该模型当前不可用。"
                    + "注意：若本次已调用过**有副作用**的工具（生图/落库/委托等），系统**不会**切换模型档案重跑"
                    + "（那可能重复计费或重复落库）；只读检索不在此列。"
                    + "本次实际用过的档案见 stages_summary.profilesUsed。";
        }
        String text = error.getMessage() == null ? "" : error.getMessage();
        if (text.contains("工具调用超过上限") || text.contains("预算已用尽")) {
            return "【预算耗尽】本次会话的工具调用次数达到上限后仍未交出成果，已中止。"
                    + "可调大 app.schedule.tool-calls.* 或收窄任务要求；"
                    + "若执行日志里出现大量同参数的检索，说明模型在重复检索而非推进，应精简提示词。";
        }
        if (text.contains("陷入循环") || text.contains("无进展")) {
            return "【重复调用】模型以完全相同的参数反复调用同一个检索工具且结果不变，已判定为循环并中止。"
                    + "请检查任务的调研要求是否过于宽泛（宽口径任务最容易出现这种空转）。";
        }
        if (text.contains("未提交草稿") || text.contains("没有通过 save_article_draft")) {
            return "【未提交草稿】会话结束前没有调用 save_article_draft，没有可交付的文章。"
                    + "这通常是会话中途失败所致（见执行日志的最后几行）；"
                    + "若模型只是在回复里写了文章正文而没有调用工具，可在提示词里明确要求必须调用该工具。";
        }
        if (text.contains("不属于任务目标公众号")) {
            return "【封面归属】智能体选用的封面素材属于另一个公众号，已拒绝交付（素材库按公众号隔离）。"
                    + "请检查任务绑定的封面素材，或让智能体改用本公众号的素材/重新生成。";
        }
        if (text.contains("parse tool param") || text.contains("JsonMappingException")) {
            return "【参数格式】模型生成的工具参数不是合法 JSON，该次工具调用被拒绝。"
                    + "这类失败会白耗一次调用（收尾工具失败还会占额度宽限）；"
                    + "系统已在工具协议里补完整 JSON 示例，若仍频繁出现可进一步精简该工具的参数结构。";
        }
        if (text.contains("并发额度") || text.contains("在飞")) {
            return "【并发受限】同一时刻进行中的智能体运行占满了名额，本次未取得额度。"
                    + "这是保护上游网关的护栏（上游并发上限很低），稍后重试即可。";
        }
        if (text.contains("model_not_found") || text.contains("no available channel")
                || text.contains("invalid_api_key")) {
            return "【模型不可用】所有候选模型档案都不可用（渠道下线 / 密钥无效 / 该渠道无额度）。"
                    + "请在「模型档案」里更换主用档案，或确认上游是否已下线该模型。";
        }
        if (text.contains("402") || text.contains("额度") || text.contains("quota")
                || text.contains("余额")) {
            return "【额度不足】上游账号/渠道的额度已耗尽。更换档案若仍走同一账号无效，"
                    + "需要充值或换用其它网关账号。";
        }
        // 排版渲染服务未就绪：这是**环境配置**缺失，不是内容不合格。
        // 必须排在下面那条 BusinessException 兜底之前——否则「没配渲染令牌」会被笼统地
        // 报成「交付内容不满足落库要求（标题/摘要超长、素材归属等）」，把用户引向完全错误的方向。
        // 匹配必须**只认「没配令牌」这一种**：MarkFlowRenderService.requireRuntime() 的两条消息是
        // 「…请到系统设置 → 排版渲染服务启用并配置令牌…」与「…未配置令牌，请…填写渲染令牌」。
        // 若图省事只匹配 "MarkFlow"/"渲染服务"，那么「渲染失败：语法非法」「获取语法指令失败」
        // 这类**真的渲染故障**也会被归到这里，等于用一个新误判换掉一个旧误判。
        if (text.contains("配置令牌") || text.contains("渲染令牌")) {
            return "【渲染服务未就绪】本次绑定了 MarkFlow 渲染式排版技能，但「排版渲染服务」未启用或未配置令牌，"
                    + "排版指令取不到，任务在开工前即中止。两种解法：到「系统设置 → 排版渲染服务」"
                    + "启用并填入渲染令牌；或把任务的排版技能换成指令式（PROMPT 引擎）版式。";
        }
        if (hasCause(error, ink.icoding.wechat.article.common.BusinessException.class)) {
            return "【业务校验未通过】交付内容不满足落库要求（如标题/摘要超长、素材归属等），"
                    + "详见原始信息与执行日志。";
        }
        return null;
    }

    /** 沿 cause 链查找指定类型的异常（失败常被包成 ExecutionException / IllegalStateException 多层）。 */
    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        int guard = 0;
        while (current != null && guard++ < 16) {
            if (type.isInstance(current)) return true;
            if (current.getCause() == current) return false;
            current = current.getCause();
        }
        return false;
    }

    /** 运行终态（状态 + 说明）。 */
    record RunCompletion(String status, String message) {
    }
}
