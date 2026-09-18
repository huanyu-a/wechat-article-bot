package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.common.InFlightGate;
import ink.icoding.wechat.article.skill.LayoutEngine;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「运行中进度真的会被周期写回库」的**接线**验证（I10）。
 *
 * <h2>为什么需要这个测试：既有测试全都测不到「接线」</h2>
 *
 * I10（{@code updateProgress}）此前已经有测试：{@code StaleRunRecoveryIntegrationTests}
 * 的 {@code progressFlushPersistsLiveFieldsAndStopsAfterFinish}。但那个测试**直接调
 * {@code runMapper.updateProgress(...)}**，证明的是「这条 SQL 写对了」，而不是
 * 「执行线程真的会周期性地调它」。
 *
 * 这两件事之间的缺口是**实打实的**。第三十七轮做过一次变异验证：把
 * {@code TaskExecutionService.executeRun} 里那一行
 * <pre>{@code ScheduledFuture<?> progressFlush = scheduleProgressFlush(run, liveWorkspace);}</pre>
 * 改成 {@code = null}（等价于删掉整条周期刷写），然后跑全部 6 个与 schedule 相关的测试类
 * （{@code StaleRunRecoveryIntegrationTests} / {@code TaskExecutionServiceCoverTest} /
 * {@code TaskExecutionFinishTest} / {@code TaskRunCompletionTest} / {@code TaskFailureMessageTest} /
 * {@code PipelineExecutorTest}，共 **53 个用例**）——**全部通过**。
 *
 * 也就是说：**把 I10 整条接线删掉，测试是绿的。** 症状会精确回到修复前的样子——
 * 运行中的行 MODE / EXECUTION_LOG / TOOL_CALL_COUNT 永远是默认值，排查时看不出这一轮走的是
 * SINGLE 还是 COORDINATOR（这正是当初要修的问题）。这正是「声明与真实必须同向」：
 * 文档与提交信息都说 I10 已修，但**没有任何东西阻止它被悄悄删掉**。
 *
 * <h2>本测试怎么测「接线」</h2>
 *
 * 不 mock {@code TaskExecutionService} 本身（那样等于把被测对象换掉），而是：
 * <ol>
 *   <li>构造一个**真实的** {@code TaskExecutionService}，只把它的协作者换成 mock；</li>
 *   <li>让策略执行时往工作区写一行日志并计数，然后**阻塞**住——模拟「一次运行正在跑」；</li>
 *   <li>把 {@code progress-flush-seconds} 设成 1 秒，在阻塞期间观察 mock 的 {@code TaskRunMapper}
 *       是否**自己**收到了 {@code updateProgress} 调用。</li>
 * </ol>
 *
 * 关键点：断言的是「mapper 被调用」这件事**由执行线程的定时器发起**，而不是由测试直接发起。
 * 因此删掉那一行接线，本测试必然失败。
 *
 * <p>用 {@code progressFlushSeconds=1} 而不是默认 15：只为把等待从 15 秒压到 ~2 秒，
 * 不改变被测行为；「间隔是否真被用上」由 {@link #flushHonoursTheConfiguredInterval()} 反向钉住。
 */
class TaskExecutionProgressWiringTest {

    /**
     * 一个只做三件事的策略：写日志、计一次工具调用、然后阻塞到测试放行。
     *
     * <p>latch 是**每个实例一份**（不是 static）：两个用例各需要一段独立的 RUNNING 窗口。
     * 写成 static 会让第二个用例的 {@code await} 立刻返回——**测试仍然通过，但什么都没测**
     * （这正是本类要防的那类静默失效，不能在测试自己身上再犯一次）。
     */
    private static final class BlockingStrategy extends ScheduledExecutionStrategy {
        private final CountDownLatch entered;
        private final CountDownLatch release;
        /** 进入与离开 execute 的时刻，供失败诊断说明「阻塞窗口到底有多长」。 */
        final java.util.concurrent.atomic.AtomicLong enteredAtNanos = new java.util.concurrent.atomic.AtomicLong();
        final java.util.concurrent.atomic.AtomicLong exitedAtNanos = new java.util.concurrent.atomic.AtomicLong();

        BlockingStrategy(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public String mode() {
            return "SINGLE";
        }

        @Override
        public ArticleAiService.ScheduledAgentResult execute(ArticleAiService.ScheduledAgentRequest request,
                                                             TaskWorkspace workspace) throws Exception {
            // ⚠️ 用的是 TaskWorkspace 自己的 API（addExecutionLog / addToolCalls）。
            // `logLine` / `toolCallCounted` 虽然名字更直白，但它们是 `progressListener()` 返回的
            // **匿名内部类**上的方法，不是 TaskWorkspace 的方法——写成 workspace.logLine(...) 编译不过。
            workspace.addExecutionLog("【调研】测试写入的分阶段日志");
            workspace.addToolCalls(5);
            enteredAtNanos.set(System.nanoTime());
            entered.countDown();
            try {
                // 阻塞到测试放行：这段窗口就是「周期刷写应该发生」的窗口
                release.await(20, TimeUnit.SECONDS);
            } finally {
                exitedAtNanos.set(System.nanoTime());
            }
            // 主动中断运行：本测试只关心「运行中」的刷写，不关心终态
            throw new IllegalStateException("测试主动中断运行");
        }

        long blockedMillis() {
            long exit = exitedAtNanos.get();
            return exit == 0 ? -1 : (exit - enteredAtNanos.get()) / 1_000_000;
        }
    }

    /** 测试脚手架：一次「正在运行」的 execute() 调用。 */
    private record Harness(TaskRunMapper runMapper, TaskExecutionService service, BlockingStrategy strategy,
                           CountDownLatch entered, CountDownLatch release,
                           AtomicReference<Throwable> failure, Thread runner) {
    }

    private static Harness startRun(long flushSeconds) throws Exception {
        TaskRunMapper runMapper = mock(TaskRunMapper.class);
        ScheduleTaskMapper taskMapper = mock(ScheduleTaskMapper.class);
        ScheduledExecutionRouter router = mock(ScheduledExecutionRouter.class);
        AssetService assetService = mock(AssetService.class);
        ArticleAiService aiService = mock(ArticleAiService.class);

        // 闸门要真实（构造器会读 limit() 来定线程池大小）；超时 0 秒即可：名额空闲时立即成功
        InFlightGate gate = new InFlightGate(2, 0);

        // insert 时回填 ID，贴近 smart-mybatis 的真实行为（否则 run.getId() 为 null，
        // 断言虽然仍能靠 invocation 记录通过，但离真实路径更远）
        when(runMapper.insert(any())).thenAnswer(invocation -> {
            ((TaskRun) invocation.getArgument(0)).setId(77L);
            return 1;
        });
        when(runMapper.findRunning(anyLong())).thenReturn(null);
        when(runMapper.updateProgress(anyLong(), any(), any(), any(), any())).thenReturn(1);
        when(runMapper.updateHeartbeat(anyLong(), any())).thenReturn(1);
        when(runMapper.finishRun(any())).thenReturn(1);
        when(taskMapper.touchRun(anyLong())).thenReturn(1);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BlockingStrategy strategy = new BlockingStrategy(entered, release);
        when(router.strategy(anyString())).thenReturn(strategy);

        ScheduleTask task = new ScheduleTask();
        task.setId(1L);
        task.setAccountId(1L);
        task.setCreatedBy(1L);
        task.setExecutionMode("SINGLE");
        task.setOutputMode("LOCAL_DRAFT");
        task.setAiPrompt("测试");
        when(taskMapper.findById(1L)).thenReturn(task);

        // articleService / markFlowRenderService 传 null：策略总是抛错，走不到用它们的那几行
        TaskExecutionService service = new TaskExecutionService(taskMapper, runMapper, aiService, null,
                router, null, gate, assetService, 3L, 120L, flushSeconds);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        // execute() 是同步的，且会被 BlockingStrategy 阻塞住，因此另起线程
        Thread runner = new Thread(() -> {
            try {
                service.execute(1L, "MANUAL");
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "progress-wiring-test-runner");
        runner.setDaemon(true);
        runner.start();

        // 先确认策略真的进去了：否则下面的等待只是在等一个没启动的东西
        assertThat(entered.await(10, TimeUnit.SECONDS))
                .as("策略应已进入 execute 并阻塞住（否则本测试没有制造出 RUNNING 窗口）")
                .isTrue();

        return new Harness(runMapper, service, strategy, entered, release, failure, runner);
    }

    private static long updateProgressCalls(TaskRunMapper runMapper) {
        return Mockito.mockingDetails(runMapper).getInvocations().stream()
                .filter(invocation -> "updateProgress".equals(invocation.getMethod().getName()))
                .count();
    }

    /**
     * 所有被调用过的方法名 + 次数。只用于**断言失败时的诊断信息**。
     *
     * <p>为什么值得单独写：这个测试失败的样子是「次数 = 0」，而 0 有很多种原因
     * （定时器没起、起了但走的是心跳分支、起了但方法名不同……）。把实际发生的调用打出来，
     * 下一个人不用再猜。
     */
    private static String invocationSummary(TaskRunMapper runMapper) {
        java.util.Map<String, Long> counts = new java.util.TreeMap<>();
        Mockito.mockingDetails(runMapper).getInvocations().forEach(invocation ->
                counts.merge(invocation.getMethod().getName(), 1L, Long::sum));
        return counts.toString();
    }

    private static void finish(Harness harness) throws Exception {
        harness.release().countDown();
        harness.runner().join(TimeUnit.SECONDS.toMillis(15));
        // 断言执行线程确实收尾了，避免「测试结束了但线程还在跑」污染其它用例
        assertThat(harness.runner().isAlive())
                .as("执行线程应已收尾（release 后不应仍存活）")
                .isFalse();
    }

    /**
     * 正例：运行期间，执行线程**自己**会周期调用 {@code runMapper.updateProgress}。
     *
     * <p>删掉 {@code executeRun} 里的 {@code scheduleProgressFlush(...)} 接线，本用例必红。
     */
    @Test
    void progressIsFlushedByTheExecutorItselfWhileRunning() throws Exception {
        Harness harness = startRun(1L);
        long calls = 0;
        try {
            // 间隔 1 秒，给到 5 秒余量
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                calls = updateProgressCalls(harness.runMapper());
                if (calls > 0) break;
                Thread.sleep(100);
            }
        } finally {
            finish(harness);
        }

        assertThat(calls)
                .as("运行期间 TaskExecutionService 应自行周期调用 runMapper.updateProgress（I10 接线）。"
                        + "若为 0，说明 executeRun 里那行 scheduleProgressFlush 接线被删或失效——"
                        + "症状会精确回到修复前：RUNNING 行的 MODE/EXECUTION_LOG/TOOL_CALL_COUNT 永远是默认值。"
                        + "实际发生的 mapper 调用：%s；策略实际阻塞时长：%d ms",
                        invocationSummary(harness.runMapper()), harness.strategy().blockedMillis())
                .isGreaterThan(0);

        // 顺带钉住「刷的是真工作区」：接线在、传的却是空串，同样等于没修
        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(harness.runMapper(), Mockito.atLeastOnce())
                .updateProgress(anyLong(), any(), logCaptor.capture(), any(), any());
        assertThat(logCaptor.getAllValues())
                .as("刷写进库的执行日志应包含工作区里写入的那一行（证明刷的是真工作区，不是空壳）")
                .anySatisfy(value -> assertThat(value).contains("【调研】测试写入的分阶段日志"));

        ArgumentCaptor<Integer> toolCountCaptor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(harness.runMapper(), Mockito.atLeastOnce())
                .updateProgress(anyLong(), any(), any(), toolCountCaptor.capture(), any());
        assertThat(toolCountCaptor.getAllValues())
                .as("刷写进库的工具调用数应包含工作区里累计的 5 次")
                .anySatisfy(value -> assertThat(value).isEqualTo(5));
    }

    /**
     * 反向钉住：{@code progress-flush-seconds} 必须真的被用上，而不是「无论如何都会刷」。
     *
     * <p>用很大的间隔（1 小时）跑同样场景，断言运行期间**不会**发生周期刷写。
     * 这条能防住「把间隔写死成小值」或「在 schedule 之前先刷一次」之类的假修复——
     * 那种改法能让正例通过，却不再是「周期」刷写。
     */
    @Test
    void flushHonoursTheConfiguredInterval() throws Exception {
        Harness harness = startRun(3600L);
        long calls;
        try {
            // 2.5 秒——远大于上一条用例的 1 秒间隔；若间隔配置没被用上，这里就会抓到刷写
            Thread.sleep(2500);
            calls = updateProgressCalls(harness.runMapper());
        } finally {
            finish(harness);
        }

        assertThat(calls)
                .as("progress-flush-seconds=3600 时，2.5 秒的运行期间不应发生周期刷写"
                        + "（否则说明间隔配置没被用上）")
                .isZero();
    }
}
