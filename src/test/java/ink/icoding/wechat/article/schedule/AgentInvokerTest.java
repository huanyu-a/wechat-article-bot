package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentResultHandler;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
import ink.icoding.wechat.article.skill.LayoutEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话运行器的有界重试与停滞诊断单测（2026-09-11 网关并发事故修复）。
 *
 * <p>用 Mockito 桩掉 agent4j 的会话（{@code AgentClient}/{@code AgentClientSession} 都是可覆写的普通类），
 * 会话行为由 {@link AgentSessionResult} 的构造参数（一个 {@code Consumer}）决定，
 * 因此可以精确复刻「返回 429」「SSE 掐断不回调」「工具调用到一半就失败」三类现场，不依赖外部服务。
 *
 * <p>三条必须钉住的规则：
 * <ol>
 *   <li>429 / concurrent limit 属外部瞬时原因，可重试，但**必须重建会话**（旧会话在 agent4j 里无法取消）；</li>
 *   <li>一旦本次尝试**已经调用过工具**，绝不重试——那些工具可能已产生付费副作用（生图/委托/落库）；</li>
 *   <li>停滞只重试一次，且失败信息必须带「最后活动 / 距今多久 / 已调用几次工具」。</li>
 * </ol>
 */
class AgentInvokerTest {

    @Test
    void retriesOnceOnRateLimitAndSucceeds() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                failsWith("HTTP 429 concurrent limit exceeded: running=7 max=6"),
                completes("已生成"));

        AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null);

        assertThat(outcome.reply()).isEqualTo("已生成");
        // 重试必须新建会话：失败的那次无法取消，复用只会再读到同一个已死的 Future
        verify(fixture.agent, times(2)).createSession();
    }

    @Test
    void doesNotRetryRateLimitOnceAToolAlreadyRan() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                callsToolThenFails("generate_image", "HTTP 429 concurrent limit exceeded"));

        assertThatThrownBy(() -> fixture.invoker.run(fixture.agent, "指令", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("429");
        // 生图可能已经扣过费：重跑就是重复计费，宁可失败
        verify(fixture.agent, times(1)).createSession();
    }

    /**
     * 永久错误不重试：重发同一个请求结果一定相同，重试只是白烧配额与时间。
     *
     * <p>这条在 2026-09-15 扩围「瞬时错误可重试」后**必须保留**——扩围的前提正是
     * 「永久类先被排除」，否则余额不足/鉴权失败也会被反复重试。
     */
    @Test
    void doesNotRetryModelErrors() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(failsWith("模型余额不足"));

        assertThatThrownBy(() -> fixture.invoker.run(fixture.agent, "指令", null))
                .hasMessageContaining("余额不足");
        verify(fixture.agent, times(1)).createSession();
    }

    /** 其余永久类同样不重试（鉴权 / 内容审查 / 模型未配置 / 接口不存在），逐一钉住分类器。 */
    @Test
    void doesNotRetryPermanentErrors() throws Exception {
        for (String permanent : List.of(
                "SSE connection failed: HTTP 401: unauthorized",
                "SSE connection failed: HTTP 403: IP 不在白名单",
                "SSE connection failed: HTTP 451: {\"code\":\"censorship_blocked\"}",
                "SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}",
                "SSE connection failed: HTTP 404: not found",
                // 实测 run#117：跑满 59 次工具调用后被告知试用额度耗尽且未开后付费。
                // 这是账号级状态，重发一定还是 402；此前只因为错误码 401008 里含 "401" 才被误打误撞拦下。
                "SSE connection failed: HTTP 402: {\"error\":{\"message\":\"The free trial quota for the"
                        + " service has been exhausted and postpaid billing is not enabled\","
                        + "\"type\":\"permission_error\",\"code\":\"401008\"}}")) {
            Fixture fixture = new Fixture(30);
            when(fixture.session.command(anyString())).thenReturn(failsWith(permanent));

            assertThatThrownBy(() -> fixture.invoker.run(fixture.agent, "指令", null))
                    .as("永久错误不应重试：%s", permanent)
                    .isInstanceOf(IllegalStateException.class);
            verify(fixture.agent, times(1)).createSession();
        }
    }

    /**
     * 瞬时模型报错重试并成功（2026-09-15 扩围）。
     *
     * <p>此前只认 429 与停滞，于是「连接被重置」「HTTP 5xx」这些同样瞬时、且常在**零工具调用**时
     * 数秒内返回的错误一次都不重试。实测 run#8/#9/#39 分别在 0/6/5 秒就失败，完全满足
     * 「无付费副作用」的重试前提却被直接判死。
     */
    @Test
    void retriesTransientModelErrorsAndSucceeds() throws Exception {
        for (String transientError : List.of(
                "SSE connection failed: , Connection reset",
                "SSE connection failed: HTTP 500: {\"message\":\"openai_error\"}",
                "SSE connection failed: HTTP 502: nginx",
                "SSE connection failed: HTTP 200: stream reset INTERNAL_ERROR")) {
            Fixture fixture = new Fixture(30);
            when(fixture.session.command(anyString())).thenReturn(
                    failsWith(transientError), completes("已生成"));

            AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null);

            assertThat(outcome.reply()).as("瞬时错误应重试成功：%s", transientError).isEqualTo("已生成");
            verify(fixture.agent, times(2)).createSession();
        }
    }

    /**
     * 瞬时错误重试**必须重建会话**且受次数上限约束（不能变成无限重试）。
     *
     * <p>退避合计 31 秒，这里用 1ms 的退避跑满 5 次重试，避免用例真的等半分钟。
     */
    @Test
    void transientRetriesAreBoundedAndRebuildTheSession() throws Exception {
        AgentClient agent = mock(AgentClient.class);
        AgentClientSession session = mock(AgentClientSession.class);
        when(agent.createSession()).thenReturn(session);
        when(session.command(anyString())).thenReturn(failsWith("Connection reset"));
        AgentInvoker invoker = new AgentInvoker(30, 5, "1,1,1,1,1", 1);

        assertThatThrownBy(() -> invoker.run(agent, "指令", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Connection reset");
        // 1 次原始尝试 + 5 次重试
        verify(agent, times(6)).createSession();
    }

    /** 已调用过工具时不重试瞬时错误：那些工具可能已产生付费副作用。 */
    @Test
    void doesNotRetryTransientErrorOnceAToolAlreadyRan() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                callsToolThenFails("generate_image", "SSE connection failed: , Connection reset"));

        assertThatThrownBy(() -> fixture.invoker.run(fixture.agent, "指令", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Connection reset");
        verify(fixture.agent, times(1)).createSession();
    }

    /**
     * 零工具调用的空指针可重试（真实定时失败 run#86）。
     *
     * <p>该 NPE 来自 agent4j 对未知工具名不判空：{@code OpenAIChatModel.handleToolCallsAndContinue}
     * 用 {@code toolMap.get(工具名)} 拿到 null 后直接交给无判空的 {@code ToolDescriptor.fromTool}
     * （偏移 14 即 {@code tool.getClass()}）。异常发生在工具执行**之前**，故 toolCalls 恒为 0。
     * 重建会话后模型通常不会再返回那个越界的工具名。
     */
    @Test
    void retriesNullPointerFromUnknownToolName() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                failsWithNullPointer(), completes("恢复"));

        AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null);

        assertThat(outcome.reply()).isEqualTo("恢复");
        verify(fixture.agent, times(2)).createSession();
    }

    /** 空指针失败时把「本次广告的工具清单」记进日志：原异常里没有工具名，事后无从查起。 */
    @Test
    void nullPointerFailureRecordsAdvertisedToolNames() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(failsWithNullPointer());
        List<String> streamedLog = new java.util.concurrent.CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> fixture.invoker.runWithLimit(fixture.agent, "指令", null, "【协调】", 10,
                progressListener(new AtomicInteger(), streamedLog)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(streamedLog).anySatisfy(line ->
                assertThat(line).contains("【协调】会话抛空指针").contains("本次广告的工具："));
    }

    /** 停滞有自己的预算，**不能**被瞬时错误的 5 次重试吞掉（否则一次停滞会拖成小时级）。 */
    @Test
    void stageStallKeepsItsOwnSmallerRetryBudget() throws Exception {
        Fixture fixture = new Fixture(1);
        when(fixture.session.command(anyString())).thenReturn(hangs(), hangs(), hangs());

        assertThatThrownBy(() -> fixture.invoker.run(fixture.agent, "指令", null))
                .isInstanceOf(StageTimeoutException.class);
        // 1 次原始尝试 + 1 次停滞重试（而不是 1 + 5）
        verify(fixture.agent, times(2)).createSession();
    }

    @Test
    void retriesOnceAfterStageStallAndSucceeds() throws Exception {
        Fixture fixture = new Fixture(1); // 1 秒硬超时，让「停滞」在测试里可复现
        when(fixture.session.command(anyString())).thenReturn(hangs(), completes("恢复"));

        AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null);

        assertThat(outcome.reply()).isEqualTo("恢复");
        verify(fixture.agent, times(2)).createSession();
    }

    @Test
    void reportsStallDiagnosticsAfterStallRetryIsExhausted() throws Exception {
        Fixture fixture = new Fixture(1);
        when(fixture.session.command(anyString())).thenReturn(hangs(), hangs());

        assertThatThrownBy(() -> fixture.invoker.run(fixture.agent, "指令", null))
                .isInstanceOf(StageTimeoutException.class)
                .hasMessageContaining("智能体会话超时")
                .hasMessageContaining("卡点：最后活动为")
                .hasMessageContaining("已调用工具 0 次");
        // 停滞只重试一次，不做无限重试
        verify(fixture.agent, times(2)).createSession();
    }

    /**
     * 只输出思维链、暂时没有正文的会话**不是停滞**：思维链增量必须计入进展。
     *
     * <p>反例（run#123/#124）：hy4-preview 先连续输出几分钟 {@code reasoning_content} 再吐正文，
     * agent4j 把这类增量路由到 {@code onThink}（见 {@code OpenAIChatModel} 的 delta 分派），
     * 而此前只把 {@code onMessage} 计为活动——于是「正在思考」与「流已断」在检测层完全同形。
     * 实测 269 秒的生成里有 3322 条思维链增量、仅 195 条正文增量，按正文计的最大空档达 254 秒，
     * 超过 180 秒阈值 → 两个本该成功的 SINGLE 运行被判停滞中止，而线程当时一直在正常出字
     * （判死后 3 分钟仍有草稿落库、11 分钟后仍有校验日志）。
     */
    @Test
    void reasoningOnlyOutputCountsAsProgressNotStall() throws Exception {
        // 阈值 1 秒：只发思维链、间隔 300ms 连续 3 秒，远超阈值。若思维链不算进展必然误杀。
        AgentInvoker invoker = new AgentInvoker(30, 0, "0", 0, 1);
        AgentClient agent = mock(AgentClient.class);
        AgentClientSession session = mock(AgentClientSession.class);
        when(agent.createSession()).thenReturn(session);
        when(session.command(anyString())).thenReturn(thinkingOnly(10, 300, "思考完毕"));

        AgentRunner.Outcome outcome = invoker.run(agent, "指令", null);

        assertThat(outcome.reply()).isEqualTo("思考完毕");
        // 一次会话就跑完，没有因为「无进展」被中止重建
        verify(agent, times(1)).createSession();
    }

    /** 只回调 {@code onThink}（正文一个字符都不发）的会话，用于复现思维链盲区。 */
    private static AgentSessionResult thinkingOnly(int times, long gapMillis, String reply) {
        return new AgentSessionResult(self -> {
            for (int i = 0; i < times; i++) {
                self.getHandler().onThink("思考第 " + i + " 步");
                try {
                    Thread.sleep(gapMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            self.complete(reply);
        });
    }

    @Test
    void abortsWhenToolBudgetIsExceededAndReportsPartialCount() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(toolCalls("generate_image", 3));
        AtomicInteger reported = new AtomicInteger();
        List<String> streamedLog = new java.util.concurrent.CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> fixture.invoker.runWithLimit(fixture.agent, "指令", null, "【配图】", 2,
                progressListener(reported, streamedLog)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("超过上限 2 次")
                // 这段文字会作为工具结果回给模型，必须给出下一步动作，而不是只说「已中止」：
                // 实测 run#62 的模型读到「已中止」后并不明白要收手，又白调了 7 次工具
                .hasMessageContaining("不要再检索")
                .hasMessageContaining("save_research_notes");
        // 超限前已发生的调用必须实时上报过，否则失败路径里的「调了几次工具」永远是 0
        assertThat(reported.get()).isEqualTo(3);
        // 失败路径也要留住日志：会话抛异常时局部日志会丢弃，只有实时上报的那份能进运行历史
        assertThat(streamedLog).contains("【配图】调用工具：generate_image");
    }

    /**
     * 预算用尽后，收尾工具仍须放行——拦下它等于把前面所有成功检索的产出全部作废。
     *
     * <p>实测 run#62（PIPELINE 调研阶段）：40 次成功检索后连调 3 次 {@code save_research_notes}
     * 全被同一套预算拒绝，简报一个字没存下来，写作阶段只能在没有调研的情况下硬写。
     * 这条用例钉住「超限 + 收尾工具 → 不抛异常、正常结束」，即产出必须能交出来。
     */
    @Test
    void terminalToolsAreAllowedPastTheBudgetSoTheWorkIsNotThrownAway() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                toolCallSequence("search_web", "search_web", "save_research_notes"));
        List<String> streamedLog = new java.util.concurrent.CopyOnWriteArrayList<>();

        AgentRunner.Outcome outcome = fixture.invoker.runWithLimit(fixture.agent, "指令", null, "【调研】", 2,
                progressListener(new AtomicInteger(), streamedLog));

        assertThat(outcome.reply()).isEqualTo("完成");
        assertThat(outcome.toolCalls()).isEqualTo(3);
        assertThat(streamedLog).contains("【调研】预算已用尽，放行收尾工具：save_research_notes");
    }

    /**
     * 放行必须有上限：{@code save_research_notes} 是追加语义，不封顶就会被重复简报刷满工作区。
     */
    @Test
    void terminalToolGraceIsBounded() throws Exception {
        Fixture fixture = new Fixture(30);
        // 1 次正常调用 + 超过宽限（3）的 4 次收尾调用：第 4 次收尾必须被拒
        when(fixture.session.command(anyString())).thenReturn(toolCallSequence(
                "search_web",
                "save_research_notes", "save_research_notes", "save_research_notes", "save_research_notes"));

        assertThatThrownBy(() -> fixture.invoker.runWithLimit(fixture.agent, "指令", null, "【调研】", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("超过上限 1 次")
                .hasMessageContaining("save_research_notes");
    }

    /**
     * 「超限被拒」与「这一阶段白干」是两件事：预算用尽后模型仍用收尾工具交出了成果，
     * 整轮就不该记成中止（实测 run#63/#68 都是靠收尾工具才把成果交出来的）。
     */
    @Test
    void budgetRejectionIsNotAFailureOnceTheDeliverableIsSubmitted() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(rejectedThenDelivers(
                "search_web", "save_research_notes"));
        List<String> streamedLog = new java.util.concurrent.CopyOnWriteArrayList<>();

        AgentRunner.Outcome outcome = fixture.invoker.runWithLimit(fixture.agent, "指令", null, "【调研】", 2,
                progressListener(new AtomicInteger(), streamedLog));

        assertThat(outcome.reply()).isEqualTo("完成");
        assertThat(streamedLog).contains("【调研】预算已用尽，放行收尾工具：save_research_notes");
        assertThat(streamedLog)
                .contains("【调研】预算超限被拒 1 次，但收尾工具已提交成果，本次按已交付处理");
    }

    /** 反过来：被拒之后没有任何收尾工具交成果，这一阶段确实是白干，仍须失败。 */
    @Test
    void budgetRejectionWithoutADeliverableStillFails() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(rejectedWithoutDeliverable("search_web"));

        assertThatThrownBy(() -> fixture.invoker.runWithLimit(fixture.agent, "指令", null, "【调研】", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("超过上限 2 次");
    }

    private static AgentRunner.ProgressListener progressListener(AtomicInteger reported, List<String> streamedLog) {
        return new AgentRunner.ProgressListener() {
            @Override
            public void toolCallCounted(int delta) {
                reported.addAndGet(delta);
            }

            @Override
            public void logLine(String line) {
                streamedLog.add(line);
            }
        };
    }

    @Test
    void countsToolFailuresAndRecordsThemInTheExecutionLog() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                toolErrorThenCompletes("generate_image", "Data too long for column 'DESCRIPTION'", "完成"));

        AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null, "【配图】");

        assertThat(outcome.toolFailures()).isEqualTo(1);
        assertThat(outcome.hasToolFailures()).isTrue();
        assertThat(outcome.executionLog())
                .contains("【配图】工具失败：generate_image - Data too long for column 'DESCRIPTION'");
    }

    /**
     * 工具失败发生在**被重试丢弃的那次尝试**里时，计数不能跟着那次尝试一起丢掉。
     *
     * <p>定性的现象：某轮报告的 {@code toolFailures} 为 0，而同一轮的执行日志里明明有
     * 「工具失败：…」——日志是**实时**上报的（失败路径留得住），失败计数却只随 {@code Outcome} 回来，
     * 而被重试/换档案丢弃的那次尝试根本不产生 Outcome。于是「重试成功」被记成了「本轮没有工具失败过」。
     *
     * <p>断言按生产调用方的形状走：工作区监听器 + 成功路径的
     * {@code addToolFailures(outcome.toolFailures())}（见 {@code PipelineExecutor} 第 208 行），
     * 而不是只看 {@code outcome.toolFailures()}——后者本来就只带得回最后一次尝试的计数。
     */
    @Test
    void keepsToolFailuresFromADiscardedAttempt() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                readOnlyToolErrorThenFails("search_web", "HTTP 429 concurrent limit exceeded: running=7 max=6"),
                completes("已生成"));
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);

        AgentRunner.Outcome outcome = fixture.invoker.runWithLimit(fixture.agent, "指令", null, "【调研】", 0,
                workspace.progressListener());
        // 生产调用方的形状：成功返回后把产出里的失败数汇入工作区
        workspace.addToolFailures(outcome.toolFailures());

        assertThat(outcome.reply()).isEqualTo("已生成");
        assertThat(workspace.executionLogText())
                .as("失败那一行的日志是实时上报的，失败路径也留得住")
                .contains("【调研】工具失败：search_web - 工具自身报错");
        assertThat(workspace.toolFailureCount())
                .as("被重试丢弃的那次尝试里的工具失败，必须计入运行级失败数")
                .isEqualTo(1);
    }

    /**
     * 成功路径仍只记**一次**：失败发生在最后一次尝试里时，计数只由产出（Outcome）带回，
     * 实时通道不得再报一遍。这是新增实时通道最直接的风险——两条路径都报会把同一次失败记两遍，
     * 那样「有 N 次工具调用失败」的终态说明就会虚高。
     */
    @Test
    void countsSuccessPathToolFailuresExactlyOnce() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                toolErrorThenCompletes("generate_image", "Data too long for column 'DESCRIPTION'", "完成"));
        TaskWorkspace workspace = TaskWorkspace.create(null, LayoutEngine.PROMPT);

        AgentRunner.Outcome outcome = fixture.invoker.runWithLimit(fixture.agent, "指令", null, "【配图】", 0,
                workspace.progressListener());
        workspace.addToolFailures(outcome.toolFailures());

        assertThat(workspace.toolFailureCount())
                .as("成功路径的失败数只经 Outcome 汇总一次，实时通道不得重复上报")
                .isEqualTo(1);
    }

    @Test
    void fallsBackToAssistantTextWhenSessionReplyIsBlank() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(messageThenCompletesWith("已完成", "   "));

        AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null);

        assertThat(outcome.reply()).isEqualTo("已完成");
    }

    @Test
    void recognizesRateLimitOnlyThroughTheCauseChain() {
        assertThat(AgentInvoker.isRateLimited(new IllegalStateException("HTTP 429 Too Many Requests"))).isTrue();
        assertThat(AgentInvoker.isRateLimited(
                new IllegalStateException("concurrent limit exceeded: running=7 max=6"))).isTrue();
        // 真实调用链里 429 往往被 ExecutionException / IllegalStateException 包了几层
        assertThat(AgentInvoker.isRateLimited(
                new IllegalStateException("包装", new RuntimeException("Concurrent Limit exceeded")))).isTrue();
        assertThat(AgentInvoker.isRateLimited(new IllegalStateException("模型余额不足"))).isFalse();
        assertThat(AgentInvoker.isRateLimited(new IllegalStateException((String) null))).isFalse();
    }

    @Test
    void retryBudgetIsBoundedAndBackoffMatchesRetryCount() {
        // 结构性钉住「有界」：退避数组长度必须与重试次数一致，避免将来加了重试却忘了给退避间隔
        assertThat(AgentInvoker.MAX_TRANSIENT_RETRIES).isEqualTo(5);
        assertThat(AgentInvoker.TRANSIENT_BACKOFF_MILLIS).hasSize(AgentInvoker.MAX_TRANSIENT_RETRIES);
        // 退避必须递增：恒定间隔在持续抖动下等于持续冲击上游
        for (int index = 1; index < AgentInvoker.TRANSIENT_BACKOFF_MILLIS.length; index++) {
            assertThat(AgentInvoker.TRANSIENT_BACKOFF_MILLIS[index])
                    .isGreaterThan(AgentInvoker.TRANSIENT_BACKOFF_MILLIS[index - 1]);
        }
        assertThat(AgentInvoker.MAX_STALL_RETRIES).isEqualTo(1);
    }

    /** 退避配置写错时回落默认值，不能静默变成零退避（那会把重试变成对上游的连续冲击）。 */
    @Test
    void malformedBackoffConfigFallsBackToDefaults() {
        assertThat(AgentInvoker.parseBackoff(null)).isEqualTo(AgentInvoker.TRANSIENT_BACKOFF_MILLIS);
        assertThat(AgentInvoker.parseBackoff("  ")).isEqualTo(AgentInvoker.TRANSIENT_BACKOFF_MILLIS);
        assertThat(AgentInvoker.parseBackoff("abc,def")).isEqualTo(AgentInvoker.TRANSIENT_BACKOFF_MILLIS);
        assertThat(AgentInvoker.parseBackoff("1000,-5")).isEqualTo(AgentInvoker.TRANSIENT_BACKOFF_MILLIS);
        assertThat(AgentInvoker.parseBackoff("500,1500")).containsExactly(500L, 1500L);
    }

    /**
     * 回归：{@code request_id} 的十六进制体与数字错误码**子串碰撞**，不得把限流误判成模型级错误。
     *
     * <p>两条样本取自**同一次真实运行**（{@code target/run-20260916.log}），错误体逐字相同、
     * 只有 {@code request_id} 不同，正好构成对照：
     * <ul>
     *   <li>L603（09:45:48.733）：{@code chatcmpl-205af2300c034118b5a1f4015ac57f9f}——
     *       十六进制体里的 {@code ...1f4015ac...} 含子串 {@code "401"}，修复前被判成
     *       「模型档案不可用」并触发了一次**无谓的换档案**；</li>
     *   <li>L322（09:42:53.231）：{@code chatcmpl-1a425c40e6c547ecad0fb5271c8ca7be} 不含关键字，
     *       正确地走了瞬时重试路径。</li>
     * </ul>
     * 两者都是 {@code code:"rate_limit_rpm_exceeded"}——**账号级**限流，换档案无效，
     * 必须走同模型退避重试。
     */
    @Test
    void requestIdHexDoesNotCollideWithNumericErrorCodes() {
        String collided = "java.lang.RuntimeException: SSE connection failed: HTTP 429: {\"error\":{\"message\":"
                + "\"rate limit reached for RPM (request_id: chatcmpl-205af2300c034118b5a1f4015ac57f9f)\","
                + "\"type\":\"rate_limit_exceeded_error\",\"param\":\"\",\"code\":\"rate_limit_rpm_exceeded\"}}";
        String control = "java.lang.RuntimeException: SSE connection failed: HTTP 429: {\"error\":{\"message\":"
                + "\"rate limit reached for RPM (request_id: chatcmpl-1a425c40e6c547ecad0fb5271c8ca7be)\","
                + "\"type\":\"rate_limit_exceeded_error\",\"param\":\"\",\"code\":\"rate_limit_rpm_exceeded\"}}";

        assertThat(AgentInvoker.isModelLevelFailure(new IllegalStateException(collided)))
                .as("十六进制体里的 401 是碰撞，不是鉴权失败——不得判成模型级错误")
                .isFalse();
        assertThat(AgentInvoker.isRateLimited(new IllegalStateException(collided)))
                .as("真实语义是 429 限流")
                .isTrue();
        assertThat(AgentInvoker.isTransient(new IllegalStateException(collided)))
                .as("限流走同模型退避重试")
                .isTrue();
        // 对照组：同一份错误体、无碰撞的 request_id，两条判据必须一致
        assertThat(AgentInvoker.isModelLevelFailure(new IllegalStateException(control))).isFalse();
        assertThat(AgentInvoker.isRateLimited(new IllegalStateException(control))).isTrue();
    }

    /** 剥离不透明 id 不得伤到**真实**的错误码——真正的鉴权/路由失败仍须换档案。 */
    @Test
    void genuineAuthAndRoutingFailuresStillSwitchProfiles() {
        for (String genuine : List.of(
                "SSE connection failed: HTTP 401: {\"error\":{\"message\":\"invalid api key\"}}",
                "SSE connection failed: HTTP 404: {\"error\":{\"message\":\"no such model\"}}",
                "SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}",
                "SSE connection failed: HTTP 403: {\"error\":{\"message\":\"permission denied\"}}",
                "SSE connection failed: HTTP 402: {\"error\":{\"message\":\"insufficient balance\"}}")) {
            assertThat(AgentInvoker.isModelLevelFailure(new IllegalStateException(genuine)))
                    .as("真实错误码必须仍然触发换档案：%s", genuine)
                    .isTrue();
        }
    }

    /**
     * 回归：缺 id 的工具调用不得**塌缩成同一个键**，否则预算护栏与在飞集合一起失效。
     *
     * <p>实测 run#129：调研阶段 {@code browse_webpage} 调用 9 次却只记 9 行、完成 12 行，
     * {@code search_web} 调用 9 次、完成 15 行（CALL=39 / DONE=43 / FAIL=9，对不上）。
     * 根因是 agent4j 只在网关推送了 {@code id} 分片时才写 {@code ToolCallEntry.callId}，
     * 缺 id 时它是 null，而键函数把 null 归一成 {@code ""}——**同一工具的所有调用共用一个键**。
     *
     * <p>这里让 3 次同名调用全部**不带 id**：修复前只有第一次被计数（计数为 1），修复后必须是 3。
     */
    @Test
    void countsEverySameNameCallEvenWhenGatewayOmitsTheId() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            // 三次同名、**同参数**、且都不带 callId（callId=null 复刻网关漏推 id 分片）
            for (int index = 0; index < 3; index++) {
                handler.onTool(tool("search_web", null), ToolStatus.CALLING);
            }
            self.complete("完成");
        }));

        AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null);

        assertThat(outcome.toolCalls())
                .as("缺 id 的 3 次同名调用必须各记一次，而不是塌缩成 1 次")
                .isEqualTo(3);
    }

    /**
     * 缺 id 时「在飞集合」必须成对：每次 CALLING 都要能被对应 COMPLETED 摘掉。
     *
     * <p>这是比计数更严重的一层——{@code inFlightTools} 是**无进展检测的关键输入**，
     * 用来保护「主编等待子智能体」不被误杀。键碰撞会让先结束的调用摘掉仍在运行的同名调用的键，
     * 于是无进展检测以为没有工具在跑，把正常等待委托的主编当成卡死。
     *
     * <p>样本必须**复用同一个 descriptor 实例**才能验到成对性：agent4j 对每次调用新建一个
     * {@code ToolDescriptor}（{@code handleToolCallsAndContinue} 偏移 169-194），该实例贯穿
     * 这次调用的 PREPARING/CALLING/COMPLETED。若这里每次都用新实例，等于在测另一件事。
     */
    @Test
    void pairsInFlightKeysForCallsWithoutId() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            // 三次同名调用各自一个 descriptor 实例，且都不带 callId（复刻网关漏推 id 分片）
            List<ToolDescriptor> descriptors = new java.util.ArrayList<>();
            for (int index = 0; index < 3; index++) descriptors.add(tool("browse_webpage", null));
            for (ToolDescriptor descriptor : descriptors) handler.onTool(descriptor, ToolStatus.CALLING);
            for (ToolDescriptor descriptor : descriptors) handler.onTool(descriptor, ToolStatus.COMPLETED);
            self.complete("完成");
        }));

        AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null);

        assertThat(outcome.reply()).isEqualTo("完成");
        assertThat(outcome.toolCalls()).isEqualTo(3);
        assertThat(outcome.toolFailures()).as("三次都成功完成，不该记失败").isZero();
    }

    /** 分类器：永久类必须先于瞬时类被识别（503 model_not_found 不得被当成 5xx 重试）。 */
    @Test
    void classifiesPermanentBeforeTransient() {
        assertThat(AgentInvoker.isPermanent(
                new IllegalStateException("HTTP 503 model_not_found"))).isTrue();
        assertThat(AgentInvoker.isTransient(
                new IllegalStateException("HTTP 503 model_not_found"))).isFalse();
        assertThat(AgentInvoker.isTransient(
                new IllegalStateException("HTTP 503 service unavailable"))).isTrue();
        assertThat(AgentInvoker.isTransient(
                new IllegalStateException("HTTP 429 concurrent limit exceeded"))).isTrue();
        // 停滞由独立预算处理，不进入瞬时类
        assertThat(AgentInvoker.isTransient(new StageTimeoutException("超时"))).isFalse();
        // 空指针（未知工具名）是瞬时的，但只在零工具调用时才真正重试
        assertThat(AgentInvoker.isTransient(new NullPointerException("tool is null"))).isTrue();
    }

    /** 桩掉 agent4j 会话：{@code command()} 按队列依次返回预置的会话结果。 */
    private static final class Fixture {
        private final AgentClient agent;
        private final AgentClientSession session;
        private final AgentInvoker invoker;

        private Fixture(long stageTimeoutSeconds) {
            this.agent = mock(AgentClient.class);
            this.session = mock(AgentClientSession.class);
            when(agent.createSession()).thenReturn(session);
            this.invoker = new AgentInvoker(stageTimeoutSeconds);
        }
    }

    // ==================== 模型档案故障切换（Phase 1） ====================

    /**
     * 候选桩：每个候选一个独立的 agent + session，各自按队列返回预置结果。
     *
     * <p>为什么必须独立：切换的前提就是「换一个 AgentClient」——agent4j 的模型在
     * {@code setModel} 时绑死，同一个 client 换不了模型（这正是整个功能要解决的问题）。
     */
    private static final class Candidates {
        private final List<AgentRunner.Candidate> candidates = new java.util.ArrayList<>();
        private final List<AgentClient> agents = new java.util.ArrayList<>();
        private final List<AgentClientSession> sessions = new java.util.ArrayList<>();

        /** 追加一个候选，其会话按顺序返回给定结果。 */
        private Candidates add(String label, AgentSessionResult... results) {
            AgentClient agent = mock(AgentClient.class);
            AgentClientSession session = mock(AgentClientSession.class);
            when(agent.createSession()).thenReturn(session);
            when(session.command(anyString())).thenReturn(results[0], java.util.Arrays.copyOfRange(results, 1,
                    results.length));
            candidates.add(new AgentRunner.Candidate(agent, label));
            agents.add(agent);
            sessions.add(session);
            return this;
        }

        private List<AgentRunner.Candidate> list() {
            return candidates;
        }

        private AgentClient agent(int index) {
            return agents.get(index);
        }
    }

    /**
     * 模型级错误 + **零工具调用** → 换下一个档案，整轮仍然成功。
     *
     * <p>这是新功能的核心断言。此前 {@code isPermanent} 把 {@code model_not_found} 判为
     * 「重发结果一定相同」而完全不重试——**这个判断对同一个模型是对的，但换一个模型就可能治好**。
     * 实测上游下线 agnes-3.0-flash 后，唯一的恢复手段是手工改库。
     */
    @Test
    void switchesToNextCandidateOnModelLevelFailure() throws Exception {
        Candidates candidates = new Candidates()
                .add("主用/glm-5.3-flash", failsWith("SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}"))
                .add("兜底/hy4-preview", completes("已生成"));

        AgentRunner.Outcome outcome = new AgentInvoker(30).runWithCandidates(candidates.list(), "指令", null,
                "【写作】", 40, 0, null);

        assertThat(outcome.reply()).isEqualTo("已生成");
        // 换档案必须**新建会话**：失败的那次在 agent4j 里无法取消，且它的模型是绑死的
        verify(candidates.agent(0), times(1)).createSession();
        verify(candidates.agent(1), times(1)).createSession();
        // 档案链随产出返回，供 stages_summary 与终态消息说明「这一轮换了几个模型」
        assertThat(outcome.profilesUsed()).containsExactly("主用/glm-5.3-flash", "兜底/hy4-preview");
        assertThat(outcome.switchedProfile()).isTrue();
    }

    /**
     * **有工具调用时不切换**：可能已经生图/落库，换模型重跑会重复计费与重复写入。
     *
     * <p>这条与「瞬时错误不重试」共用同一个前置条件，是新功能最重要的安全边界。
     */
    @Test
    void doesNotSwitchOnceAToolAlreadyRan() throws Exception {
        Candidates candidates = new Candidates()
                .add("主用/a", callsToolThenFails("generate_image",
                        "SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}"))
                .add("兜底/b", completes("已生成"));

        assertThatThrownBy(() -> new AgentInvoker(30).runWithCandidates(candidates.list(), "指令", null,
                null, 40, 0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model_not_found");
        verify(candidates.agent(1), times(0)).createSession();
    }

    /**
     * **只读工具不阻断重试**：调用过 {@code read_article_draft} 之后遇瞬时错误仍须重试。
     *
     * <p>依据（2026-09-18，run#14 / run#18 两次真实 FAILED）：写作阶段的会话在停滞前
     * **只调用过一次 {@code read_article_draft}**（执行日志末两行就是它的调用与完成），
     * 却因「已调用过工具」被判不可重试——12 个候选档案一个都没用上，整轮 FAILED、文章没有落库。
     * 只读检索没有不可撤销的后果，重跑它是安全的；判据见 {@link ToolCallGovernor#hasPaidSideEffect}。
     */
    @Test
    void retriesTransientErrorAfterOnlyReadOnlyTools() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(
                readOnlyToolThenFails("read_article_draft",
                        "HTTP 429 concurrent limit exceeded: running=7 max=6"),
                completes("已生成"));

        AgentRunner.Outcome outcome = fixture.invoker.run(fixture.agent, "指令", null);

        assertThat(outcome.reply())
                .as("只读工具不该把重试闸门关上——本次尝试没有产生任何不可撤销的后果")
                .isEqualTo("已生成");
        verify(fixture.agent, times(2)).createSession();
    }

    /**
     * **只读工具不阻断换档案**：这条直接复刻 run#18 的形状（12 个候选一个都没用上）。
     *
     * <p>主用档案在只调用过一次 {@code read_article_draft} 后报模型级错误，必须能切到兜底档案
     * 并把整轮跑完。修复前 {@code retryable = toolCalls == 0} 会把这次切换整个挡掉。
     */
    @Test
    void switchesProfileAfterOnlyReadOnlyTools() throws Exception {
        Candidates candidates = new Candidates()
                .add("主用/a", readOnlyToolThenFails("read_article_draft",
                        "SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}"))
                .add("兜底/b", completes("已生成"));

        AgentRunner.Outcome outcome = new AgentInvoker(30).runWithCandidates(candidates.list(), "指令", null,
                null, 40, 0, null);

        assertThat(outcome.reply()).isEqualTo("已生成");
        assertThat(outcome.switchedProfile())
                .as("只读工具后仍须换档案——这正是 run#18 里 12 个候选全被浪费的那一步")
                .isTrue();
        verify(candidates.agent(1), times(1)).createSession();
    }

    /**
     * 未知工具名一律按**有副作用**处理（保守方向）：宁可少一次重试，也不能重复计费或重复落库。
     *
     * <p>这条钉住 {@link ToolCallGovernor#hasPaidSideEffect} 对 null / 未知名字的返回语义——
     * 工具清单是随技能与阶段变化的，判据不能假设「没见过的名字就是只读的」。
     */
    @Test
    void unknownToolNameCountsAsSideEffect() throws Exception {
        for (String unknown : List.of("some_future_tool", "unknown")) {
            Fixture fixture = new Fixture(30);
            when(fixture.session.command(anyString())).thenReturn(
                    readOnlyToolThenFails(unknown, "HTTP 429 concurrent limit exceeded: running=7 max=6"));

            assertThatThrownBy(() -> fixture.invoker.run(fixture.agent, "指令", null))
                    .as("未知工具名必须按有副作用处理，不得重试：%s", unknown)
                    .isInstanceOf(IllegalStateException.class);
            verify(fixture.agent, times(1)).createSession();
        }
    }

    /**
     * {@code 451}（内容审查）与 {@code 429}（账号级并发）**不切换**。
     *
     * <p>451 换个模型同样会被拦；429 是**账号级**并发配额，同一网关下换档案无效，
     * 仍走原有的同模型退避重试。把这两类也拿来切换只会白费一次尝试。
     */
    @Test
    void doesNotSwitchOnCensorshipOrRateLimit() throws Exception {
        for (String notAModelProblem : List.of(
                "SSE connection failed: HTTP 451: {\"code\":\"censorship_blocked\"}",
                "HTTP 429 concurrent limit exceeded: running=7 max=6")) {
            Candidates candidates = new Candidates()
                    .add("主用/a", failsWith(notAModelProblem))
                    .add("兜底/b", completes("已生成"));

            assertThatThrownBy(() -> new AgentInvoker(30).runWithCandidates(candidates.list(), "指令", null,
                    null, 40, 0, null))
                    .as("这类错误换模型无效，不该切换：%s", notAModelProblem)
                    .isInstanceOf(IllegalStateException.class);
            verify(candidates.agent(1), times(0)).createSession();
        }
    }

    /**
     * 失败路径也要留下「用的是哪个档案」——这是 run#123/#124 暴露的真实缺陷。
     *
     * <p>那两次运行停滞失败后 {@code stages_summary.profilesUsed} 为空、{@code switchedProfile=false}，
     * 事后完全看不出当时跑的是哪个模型、有没有试过备用档案。原因：档案链原先只随成功的
     * {@code Outcome} 返回，而失败路径根本没有 Outcome。修复是让运行器**实时**上报用过的档案
     * （与工具计数、执行日志同一理由：失败的那次尝试的局部数据会随异常丢弃）。
     *
     * <p>这里直接钉住「候选耗尽后抛出，但监听器已经收到主用与切换后的两个档案」。
     */
    @Test
    void reportsProfilesUsedEvenWhenAllCandidatesFail() throws Exception {
        Candidates candidates = new Candidates()
                .add("主用/a", failsWith("SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}"))
                .add("兜底/b", failsWith("SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}"));
        List<String> reported = new java.util.ArrayList<>();

        assertThatThrownBy(() -> new AgentInvoker(30).runWithCandidates(candidates.list(), "指令", null,
                null, 40, 0, new AgentRunner.ProgressListener() {
                    @Override
                    public void toolCallCounted(int delta) {
                    }

                    @Override
                    public void logLine(String line) {
                    }

                    @Override
                    public void profileUsed(String label) {
                        reported.add(label);
                    }
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(reported).containsExactly("主用/a", "兜底/b");
    }

    /** 单候选失败同样要上报主用档案（否则「只有一条档案」的部署永远看不到归因信息）。 */
    @Test
    void reportsTheSingleCandidateWhenItFails() throws Exception {
        Candidates candidates = new Candidates()
                .add("主用/only", failsWith("SSE connection failed: HTTP 503: {\"code\":\"model_not_found\"}"));
        List<String> reported = new java.util.ArrayList<>();

        assertThatThrownBy(() -> new AgentInvoker(30).runWithCandidates(candidates.list(), "指令", null,
                null, 40, 0, new AgentRunner.ProgressListener() {
                    @Override
                    public void toolCallCounted(int delta) {
                    }

                    @Override
                    public void logLine(String line) {
                    }

                    @Override
                    public void profileUsed(String label) {
                        reported.add(label);
                    }
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(reported).containsExactly("主用/only");
    }

    /**
     * 停滞（零工具调用）→ 优先换档案，而不是原地重建会话。
     *
     * <p>依据：零工具调用的停滞说明这个模型在本次请求上卡死了，换模型比原地重试更对症，
     * 也省下「再白等一整个会话超时」的时间（SINGLE 900s / COORDINATOR 1800s）。
     */
    @Test
    void switchesOnStallInsteadOfRetryingInPlace() throws Exception {
        Candidates candidates = new Candidates()
                .add("主用/a", hangs())
                .add("兜底/b", completes("已生成"));

        AgentRunner.Outcome outcome = new AgentInvoker(1).runWithCandidates(candidates.list(), "指令", null,
                null, 40, 0, null);

        assertThat(outcome.reply()).isEqualTo("已生成");
        assertThat(outcome.switchedProfile()).isTrue();
        // 原地停滞重试的预算（1 次）没有被用掉：有候选时直接换，不浪费
        verify(candidates.agent(0), times(1)).createSession();
        verify(candidates.agent(1), times(1)).createSession();
    }

    /** 候选耗尽后仍失败：抛出原始错误，不静默吞掉。 */
    @Test
    void throwsAfterCandidatesAreExhausted() throws Exception {
        Candidates candidates = new Candidates()
                .add("主用/a", failsWith("HTTP 503: {\"code\":\"model_not_found\"}"))
                .add("兜底/b", failsWith("HTTP 503: {\"code\":\"model_not_found\"}"));

        assertThatThrownBy(() -> new AgentInvoker(30).runWithCandidates(candidates.list(), "指令", null,
                null, 40, 0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model_not_found");
        verify(candidates.agent(1), times(1)).createSession();
    }

    /** 未发生切换时产出不带档案链：终态消息不该说「换过模型」。 */
    @Test
    void singleCandidateOutcomeHasNoProfileChain() throws Exception {
        Candidates candidates = new Candidates().add("主用/a", completes("已生成"));

        AgentRunner.Outcome outcome = new AgentInvoker(30).runWithCandidates(candidates.list(), "指令", null,
                null, 40, 0, null);

        assertThat(outcome.switchedProfile()).isFalse();
    }

    /** 空候选列表是调用错误（装配层保证至少一个候选，这里兜住误用）。 */
    @Test
    void rejectsEmptyCandidateList() {
        assertThatThrownBy(() -> new AgentInvoker(30).runWithCandidates(List.of(), "指令", null,
                null, 40, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("候选智能体列表不能为空");
    }

    // ==================== 工具治理（Phase 4） ====================

    /**
     * 无进展循环：同一工具 + 同一参数重复到阈值即中止会话。
     *
     * <p>依据：run#46/#62/#85/#89 都是「同一工具、同一参数、反复调用」把预算一路烧完的形态。
     * 中止必须在**工具执行前**判，否则那次上游请求已经发出去了。
     */
    @Test
    void abortsSessionOnNoProgressLoop() throws Exception {
        ToolCallGovernor governor = new ToolCallGovernor();
        AgentSessionResult loop = new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            try {
                for (int index = 0; index < ToolCallGovernor.NO_PROGRESS_ABORT_THRESHOLD; index++) {
                    // callId 每次不同（真实链路里每次工具调用都是新的 call id），
                    // 但**参数完全相同**——循环检测看的正是「工具 + 参数」，不是 call id。
                    ToolDescriptor descriptor = tool("search_web", "call-" + index);
                    descriptor.setInputParams("{\"query\":\"AI\"}");
                    handler.onTool(descriptor, ToolStatus.CALLING);
                }
            } catch (IllegalStateException expected) {
                // 回调里抛出的异常在真实链路会被 agent4j 转成该工具的失败原因，会话不终止
            }
            self.complete("完成");
        });
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(loop);

        assertThatThrownBy(() -> fixture.invoker.runWithCandidates(
                List.of(new AgentRunner.Candidate(fixture.agent, "a", governor)), "指令", null,
                null, 40, 0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("陷入循环");
    }

    /** 不同参数不算循环：换关键词继续检索是正常工作量（run#46 的 25 次调用全部成功且参数各异）。 */
    @Test
    void differentParametersAreNotALoop() throws Exception {
        ToolCallGovernor governor = new ToolCallGovernor();
        AgentSessionResult varied = new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            for (int index = 0; index < ToolCallGovernor.NO_PROGRESS_ABORT_THRESHOLD + 2; index++) {
                ToolDescriptor descriptor = tool("search_web", "call-" + index);
                descriptor.setInputParams("{\"query\":\"关键词" + index + "\"}");
                handler.onTool(descriptor, ToolStatus.CALLING);
            }
            self.complete("完成");
        });
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(varied);

        AgentRunner.Outcome outcome = fixture.invoker.runWithCandidates(
                List.of(new AgentRunner.Candidate(fixture.agent, "a", governor)), "指令", null,
                null, 40, 0, null);

        assertThat(outcome.reply()).isEqualTo("完成");
    }

    private static AgentSessionResult completes(String reply) {
        return new AgentSessionResult(self -> {
            self.complete(reply);
        });
    }

    private static AgentSessionResult messageThenCompletesWith(String message, String reply) {
        return new AgentSessionResult(self -> {
            self.getHandler().onMessage(message);
            self.complete(reply);
        });
    }

    private static AgentSessionResult failsWith(String message) {
        return new AgentSessionResult(self -> self.completeExceptionally(new IllegalStateException(message)));
    }

    /** 复刻 agent4j 对未知工具名不判空时抛出的空指针（ToolDescriptor.fromTool → tool.getClass()）。 */
    private static AgentSessionResult failsWithNullPointer() {
        return new AgentSessionResult(self -> self.completeExceptionally(new NullPointerException(
                "Cannot invoke \"ink.icoding.llm.core.tool.Tool.getClass()\" because \"tool\" is null")));
    }

    /** 工具已经开始执行后会话才失败——这是「不得重试」的那一类。 */
    private static AgentSessionResult callsToolThenFails(String toolName, String message) {
        return new AgentSessionResult(self -> {
            self.getHandler().onTool(tool(toolName, "call-1"), ToolStatus.CALLING);
            self.completeExceptionally(new IllegalStateException(message));
        });
    }

    /**
     * 只调用了一个工具（成功完成）之后会话才失败——是否可重试取决于**这个工具有没有副作用**。
     *
     * <p>与 {@link #callsToolThenFails} 的区别在于这里补了 COMPLETED：真实故障（run#14/#18）里
     * {@code read_article_draft} 是**成功完成**的，执行日志末两行就是它的调用与完成。
     */
    private static AgentSessionResult readOnlyToolThenFails(String toolName, String message) {
        return new AgentSessionResult(self -> {
            self.getHandler().onTool(tool(toolName, "call-1"), ToolStatus.CALLING);
            self.getHandler().onTool(tool(toolName, "call-1"), ToolStatus.COMPLETED);
            self.completeExceptionally(new IllegalStateException(message));
        });
    }

    /**
     * 只读工具**自己失败**（{@code onToolError}）之后会话才失败：这次尝试既可重试
     * （只读工具无付费副作用），又已经留下过一次工具失败——正是「0/失败并存」的形状。
     */
    private static AgentSessionResult readOnlyToolErrorThenFails(String toolName, String message) {
        return new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            handler.onTool(tool(toolName, "call-1"), ToolStatus.CALLING);
            handler.onToolError(tool(toolName, "call-1"), new IllegalStateException("工具自身报错"));
            self.completeExceptionally(new IllegalStateException(message));
        });
    }

    private static AgentSessionResult toolCalls(String toolName, int times) {
        return new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            for (int index = 1; index <= times; index++) {
                handler.onTool(tool(toolName, "call-" + index), ToolStatus.CALLING);
            }
            self.complete("完成");
        });
    }

    /** 依次发起指定的工具调用（用于验证「预算用尽后收尾工具仍被放行」这类混合序列）。 */
    private static AgentSessionResult toolCallSequence(String... toolNames) {
        return new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            for (int index = 0; index < toolNames.length; index++) {
                handler.onTool(tool(toolNames[index], "call-" + index), ToolStatus.CALLING);
            }
            self.complete("完成");
        });
    }

    /**
     * 超限被拒之后又把成果交出来的形状：前两次检索正常、第三次检索被预算拒绝、随后收尾工具提交成功。
     *
     * <p>回调里抛出的异常在真实链路里会被 agent4j 转成该工具自身的失败原因，会话并不因此终止，
     * 所以这里显式吞掉它——直接让异常冒出去就复刻不出「运行结束时的兜底判据」。
     */
    private static AgentSessionResult rejectedThenDelivers(String noisyTool, String deliverTool) {
        return new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            twoCallsThenOneRejected(handler, noisyTool);
            handler.onTool(tool(deliverTool, "deliver-1"), ToolStatus.CALLING);
            handler.onTool(tool(deliverTool, "deliver-1"), ToolStatus.COMPLETED);
            self.complete("完成");
        });
    }

    /** 只有超限被拒、没有任何收尾工具：这一阶段确实白干。 */
    private static AgentSessionResult rejectedWithoutDeliverable(String noisyTool) {
        return new AgentSessionResult(self -> {
            twoCallsThenOneRejected(self.getHandler(), noisyTool);
            self.complete("完成");
        });
    }

    private static void twoCallsThenOneRejected(AgentResultHandler handler, String toolName) {
        handler.onTool(tool(toolName, "search-1"), ToolStatus.CALLING);
        handler.onTool(tool(toolName, "search-2"), ToolStatus.CALLING);
        try {
            handler.onTool(tool(toolName, "search-3"), ToolStatus.CALLING);
            throw new AssertionError("第 3 次调用应当越过上限被拒");
        } catch (IllegalStateException rejected) {
            // 预算护栏按预期生效
        }
    }

    private static AgentSessionResult toolErrorThenCompletes(String toolName, String error, String reply) {
        return new AgentSessionResult(self -> {
            AgentResultHandler handler = self.getHandler();
            handler.onTool(tool(toolName, "call-1"), ToolStatus.CALLING);
            handler.onToolError(tool(toolName, "call-1"), new IllegalStateException(error));
            self.complete(reply);
        });
    }

    /** 永不结束的会话：复刻「SSE 被掐断且不回调」的停滞。 */
    private static AgentSessionResult hangs() {
        return new AgentSessionResult(self -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** {@code ToolDescriptor} 的 name 只读，用匿名子类补上（agent4j 未提供 setter）。 */
    private static ToolDescriptor tool(String name, String callId) {
        ToolDescriptor descriptor = new ToolDescriptor() {
            @Override
            public String getName() {
                return name;
            }
        };
        descriptor.setCallId(callId);
        return descriptor;
    }
}
