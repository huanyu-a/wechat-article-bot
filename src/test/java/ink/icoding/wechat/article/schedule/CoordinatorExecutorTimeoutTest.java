package ink.icoding.wechat.article.schedule;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.entity.MemoryMultipartFile;
import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.DelegateTools;
import ink.icoding.wechat.article.ai.ScheduledAgentFactory;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.SkillContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 协调者委托链路的**会话硬超时分档**单测（2026-09-18，run#19 的委托写作误杀修复）。
 *
 * <p>为什么这条要单独测：缺陷 B（写作阶段用 300s 全局档被误杀）在两条链路上各有一处接线——
 * PIPELINE 的写作阶段由 {@code PipelineExecutor} 传超时，委托写作由 {@code CoordinatorExecutor}
 * 的子智能体运行器传。只测前者的话，委托写作会静默退回 300s 而测试全绿。
 *
 * <p>实测依据（run#19，COORDINATOR）：{@code stages_summary} 里
 * {@code DELEGATE_WRITING seconds=300.0}——与 PIPELINE 的两次失败同一堵墙。
 *
 * <p>驱动方式：桩掉 {@link ScheduledAgentFactory}，在它被调用时把协调者构造好的
 * **委托工具组**截下来；随后让桩运行器在「主编」这一轮里真的去执行 {@code delegate_writing}，
 * 于是子智能体运行器（协调者内部那个 lambda）被真实触发，它传给运行器的超时就是被测对象。
 */
class CoordinatorExecutorTimeoutTest {

    /** 桩运行器：记录每次会话的（阶段, 超时），并在主编这一轮触发一次委托写作。 */
    private static class StubRunner extends AgentRunner {
        final List<String> stages = new ArrayList<>();
        final List<Long> timeouts = new ArrayList<>();
        private final TaskWorkspace workspace;
        /** 协调者构造的委托工具组（由被桩掉的工厂在 buildCandidates 时交出）。 */
        private List<ink.icoding.llm.core.tool.Tool> delegateTools;
        /** 委托写作只触发一次，避免返工环里反复委托。 */
        private boolean delegated;

        StubRunner(TaskWorkspace workspace) {
            this.workspace = workspace;
        }

        @Override
        public Outcome run(AgentClient agent, String command, List<MemoryMultipartFile> attachments) {
            return new Outcome("完成", 1, "stub");
        }

        @Override
        public Outcome runWithCandidates(List<Candidate> candidates, String command,
                                         List<MemoryMultipartFile> attachments, String logPrefix,
                                         int maxToolCalls, long timeoutSeconds, ProgressListener progress) {
            String stage = candidates.get(0).label();
            stages.add(stage);
            timeouts.add(timeoutSeconds);
            if ("COORDINATE".equals(stage) && !delegated) {
                delegated = true;
                delegateWriting();
            }
            return new Outcome("完成", 1, logPrefix + "stub");
        }

        /** 真的执行一次 {@code delegate_writing}——子智能体运行器因此被触发（被测接线就在里面）。 */
        @SuppressWarnings("unchecked")
        private void delegateWriting() {
            ink.icoding.llm.core.tool.Tool<DelegateTools.DelegateWritingParam> writing =
                    (ink.icoding.llm.core.tool.Tool<DelegateTools.DelegateWritingParam>) delegateTools.stream()
                            .filter(tool -> "delegate_writing".equals(tool.getClass()
                                    .getAnnotation(ink.icoding.llm.core.tool.annotations.ToolInfo.class).name()))
                            .findFirst().orElseThrow();
            DelegateTools.DelegateWritingParam param = new DelegateTools.DelegateWritingParam();
            param.setInstruction("写一篇关于 AI 的文章");
            writing.execute(param);
        }
    }

    private static AgentClient namedAgent(String code, String stage) {
        AgentClient agent = new AgentClient();
        agent.setName(code);
        agent.setDescription(stage);
        return agent;
    }

    private static ArticleAiService.ScheduledAgentRequest request() {
        return new ArticleAiService.ScheduledAgentRequest(1L, 1L, 9L, "Asia/Shanghai", "LOCAL_DRAFT",
                "写一篇关于 AI 的文章", List.of(), Map.of(), 2);
    }

    @Test
    void delegatedWritingGetsTheWritingTimeoutBudget() throws Exception {
        TaskWorkspace workspace = TaskWorkspace.create(9L, LayoutEngine.PROMPT);
        StubRunner runner = new StubRunner(workspace);

        ScheduledAgentFactory factory = mock(ScheduledAgentFactory.class);
        when(factory.skillContext(any(), any(), any())).thenReturn(
                new SkillContext(List.of(), List.of(), List.of(), List.of(), null,
                        SkillContext.Scene.SCHEDULED));
        // 主编那一轮的 buildCandidates 带着委托工具组（参数 6）；截下来供桩运行器执行。
        when(factory.buildCandidates(anyString(), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Function<TaskWorkspace, List<ink.icoding.llm.core.tool.Tool>> tools = invocation.getArgument(6);
                    if (tools != null) runner.delegateTools = tools.apply(workspace);
                    String code = invocation.getArgument(0);
                    String stage = invocation.getArgument(1);
                    return List.of(new AgentRunner.Candidate(namedAgent(code, stage), stage));
                });

        CoordinatorExecutor executor = new CoordinatorExecutor(factory, runner, ToolCallBudget.defaults(),
                StageTimeoutPolicy.defaults(), 1800L);

        // 委托写作要落草稿，否则协调者收尾时会判定「没有提交文章」
        workspace.draftState().save(draftParam());
        executor.execute(request(), workspace);

        int writingIndex = runner.stages.indexOf("WRITING");
        assertThat(writingIndex)
                .as("桩运行器应当通过 delegate_writing 触发过一次委托写作")
                .isGreaterThanOrEqualTo(0);
        assertThat(runner.timeouts.get(writingIndex))
                .as("委托写作与 PIPELINE 的写作阶段是同一件事，必须拿到写作档（修复前这里是 0 → 300s）")
                .isEqualTo(StageTimeoutPolicy.DEFAULT_WRITING_SECONDS);

        int chiefIndex = runner.stages.indexOf("COORDINATE");
        assertThat(runner.timeouts.get(chiefIndex))
                .as("主编仍走它自己的 1800s 档，不受写作档影响")
                .isEqualTo(1800L);
    }

    private static ScheduledArticleTools.SaveDraftParam draftParam() {
        ScheduledArticleTools.SaveDraftParam param = new ScheduledArticleTools.SaveDraftParam();
        param.setTitle("测试标题");
        param.setContentHtml("<p>正文自然段</p>");
        return param;
    }
}