package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentResultHandler;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
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

    @Test
    void doesNotRetryModelErrors() throws Exception {
        Fixture fixture = new Fixture(30);
        when(fixture.session.command(anyString())).thenReturn(failsWith("模型余额不足"));

        assertThatThrownBy(() -> fixture.invoker.run(fixture.agent, "指令", null))
                .hasMessageContaining("余额不足");
        verify(fixture.agent, times(1)).createSession();
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
        assertThat(AgentInvoker.MAX_RATE_LIMIT_RETRIES).isEqualTo(3);
        assertThat(AgentInvoker.RATE_LIMIT_BACKOFF_MILLIS).hasSize(AgentInvoker.MAX_RATE_LIMIT_RETRIES);
        assertThat(AgentInvoker.MAX_STALL_RETRIES).isEqualTo(1);
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

    /** 工具已经开始执行后会话才失败——这是「不得重试」的那一类。 */
    private static AgentSessionResult callsToolThenFails(String toolName, String message) {
        return new AgentSessionResult(self -> {
            self.getHandler().onTool(tool(toolName, "call-1"), ToolStatus.CALLING);
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
