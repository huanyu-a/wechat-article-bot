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
                new AgentRunner.ProgressListener() {
                    @Override
                    public void toolCallCounted(int delta) {
                        reported.addAndGet(delta);
                    }

                    @Override
                    public void logLine(String line) {
                        streamedLog.add(line);
                    }
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("超过上限 2 次");
        // 超限前已发生的调用必须实时上报过，否则失败路径里的「调了几次工具」永远是 0
        assertThat(reported.get()).isEqualTo(3);
        // 失败路径也要留住日志：会话抛异常时局部日志会丢弃，只有实时上报的那份能进运行历史
        assertThat(streamedLog).contains("【配图】调用工具：generate_image");
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
