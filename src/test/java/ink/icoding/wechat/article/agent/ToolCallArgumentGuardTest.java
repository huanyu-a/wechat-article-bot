package ink.icoding.wechat.article.agent;

import ink.icoding.llm.core.entity.Message;
import ink.icoding.llm.core.entity.MessageToolCall;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.model.LLMResult;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回放守卫单测（skills-agent-plan 8.1 同类 seam）：覆盖两条修复路径——进入 ask 前的历史归一化，
 * 以及工具执行前对「本轮刚落库 assistant 消息」的归一化。后者是 agent4j 同一次 ask 内继续下一轮
 * 的唯一拦截点，漏掉它 PIPELINE 调研阶段仍会在第二次请求被网关 400 拒绝。
 */
class ToolCallArgumentGuardTest {

    @Test
    void normalizesBlankArgumentsInIncomingHistory() {
        List<Message> messages = new ArrayList<>(List.of(assistantCall("call_1", "read_article_draft", "")));
        CapturingModel fake = new CapturingModel();
        ToolCallArgumentGuard guard = (ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake);

        guard.ask(messages, List.of());

        assertThat(fake.seen.get(0).getToolCalls().get(0).getArgumentsJson()).isEqualTo("{}");
    }

    @Test
    void keepsValidArgumentsUntouched() {
        String original = "{\"query\": \"科技新闻\"}";
        List<Message> messages = new ArrayList<>(List.of(assistantCall("call_1", "search_web", original)));
        CapturingModel fake = new CapturingModel();

        ((ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake)).ask(messages, List.of());

        assertThat(fake.seen.get(0).getToolCalls().get(0).getArgumentsJson()).isEqualTo(original);
    }

    @Test
    void normalizesNullAndNonJsonArguments() {
        List<Message> messages = new ArrayList<>(List.of(
                assistantCall("call_1", "search_web", null),
                assistantCall("call_2", "search_web", "not-json")));
        CapturingModel fake = new CapturingModel();

        ((ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake)).ask(messages, List.of());

        assertThat(fake.seen.get(0).getToolCalls().get(0).getArgumentsJson()).isEqualTo("{}");
        assertThat(fake.seen.get(1).getToolCalls().get(0).getArgumentsJson()).isEqualTo("{}");
    }

    @Test
    void keepsValidNonObjectJsonUntouched() {
        // 网关的拒收条件是「不是合法 JSON」，数组/标量同样合法；按原值回放，不悄悄改写成 {} 丢掉语义
        String array = "[1, 2]";
        String scalar = "123";
        List<Message> messages = new ArrayList<>(List.of(
                assistantCall("call_1", "search_web", array),
                assistantCall("call_2", "search_web", scalar)));
        CapturingModel fake = new CapturingModel();

        ((ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake)).ask(messages, List.of());

        assertThat(fake.seen.get(0).getToolCalls().get(0).getArgumentsJson()).isEqualTo(array);
        assertThat(fake.seen.get(1).getToolCalls().get(0).getArgumentsJson()).isEqualTo(scalar);
    }

    @Test
    void repairsAssistantMessageBeforeToolRunsInSameRound() {
        // 模拟 agent4j 一轮里的顺序：assistant 消息先落 appendedMessages，然后才执行工具、再发下一轮请求
        List<Message> appended = new ArrayList<>();
        Message assistant = assistantCall("call_1", "search_web", "");
        LLMModel fake = new InRoundModel(appended, assistant);
        ToolCallArgumentGuard guard = (ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake);

        LLMResult result = guard.ask(new ArrayList<>(List.of(Message.fromUser("搜索"))), List.of(),
                (name, json, desc, handler) -> "ok");
        result.execute();

        assertThat(appended).hasSize(1);
        assertThat(appended.get(0).getToolCalls().get(0).getArgumentsJson())
                .as("工具执行前必须完成归一化，否则紧随其后的下一轮请求会带上空 arguments")
                .isEqualTo("{}");
    }

    /**
     * 网关要求历史里每条消息都带 content 且不能为 null，而 agent4j 对「带 tool_calls 的 assistant
     * 消息」把 null 与空串一律写成 content:null（appendNeutralMessage 的 putNull 分支），
     * 真实缺陷：定时任务完成若干次工具调用后整轮 400「missing `***.content` parameter」。
     * 该分支只认非空内容，因此占位值是单空格而不是空串。
     */
    @Test
    void fillsNullContentOnReplayedMessages() {
        List<Message> messages = new ArrayList<>(List.of(assistantCall("call_1", "search_web", "{}")));
        CapturingModel fake = new CapturingModel();
        ToolCallArgumentGuard guard = (ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake);

        guard.ask(messages, List.of());

        assertThat(fake.seen.get(0).getContent()).isEqualTo(" ");
    }

    @Test
    void fillsEmptyContentOnToolCallMessageWithPlaceholder() {
        // 空串同样会落到 putNull 分支，必须换成非空占位
        Message withEmpty = assistantCall("call_1", "search_web", "{}");
        withEmpty.setContent("");
        List<Message> messages = new ArrayList<>(List.of(withEmpty));
        CapturingModel fake = new CapturingModel();

        ((ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake)).ask(messages, List.of());

        assertThat(fake.seen.get(0).getContent()).isEqualTo(" ");
    }

    @Test
    void keepsExistingContentUntouched() {
        Message withText = Message.fromAssistant("我先查一下资料。");
        withText.setToolCalls(new ArrayList<>(List.of(new MessageToolCall("call_1", "search_web", "{}"))));
        List<Message> messages = new ArrayList<>(List.of(withText));
        CapturingModel fake = new CapturingModel();

        ((ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake)).ask(messages, List.of());

        assertThat(fake.seen.get(0).getContent()).isEqualTo("我先查一下资料。");
    }

    /** 普通消息（无 tool_calls）走 put("content", ...) 分支，空串即可被网关接受。 */
    @Test
    void fillsEmptyStringOnPlainMessagesWithoutToolCalls() {
        List<Message> messages = new ArrayList<>(List.of(Message.fromAssistant(), Message.fromUser()));
        CapturingModel fake = new CapturingModel();

        ((ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake)).ask(messages, List.of());

        assertThat(fake.seen.get(0).getContent()).isEmpty();
        assertThat(fake.seen.get(1).getContent()).isEmpty();
    }

    /** tool 消息的线上 content 取自 toolResult，修的是 toolResult 而不是 message.content。 */
    @Test
    void fillsNullToolResultContent() {
        Message tool = Message.fromTool().withToolResult("call_1", null);
        List<Message> messages = new ArrayList<>(List.of(tool));
        CapturingModel fake = new CapturingModel();

        ((ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake)).ask(messages, List.of());

        assertThat(fake.seen.get(0).getToolResult().getContent()).isEmpty();
    }

    @Test
    void repairsAssistantContentBeforeToolRunsInSameRound() {
        // 同一轮内：assistant 消息（content=null）先落 appendedMessages，工具执行前必须补齐 content
        List<Message> appended = new ArrayList<>();
        Message assistant = assistantCall("call_1", "search_web", "{}");
        LLMModel fake = new InRoundModel(appended, assistant);
        ToolCallArgumentGuard guard = (ToolCallArgumentGuard) ToolCallArgumentGuard.wrap(fake);

        LLMResult result = guard.ask(new ArrayList<>(List.of(Message.fromUser("搜索"))), List.of(),
                (name, json, desc, handler) -> "ok");
        result.execute();

        assertThat(appended).hasSize(1);
        assertThat(appended.get(0).getContent())
                .as("工具执行前必须补齐 content，否则紧随其后的下一轮请求会被网关以 missing content 拒绝")
                .isEqualTo(" ");
    }

    private static Message assistantCall(String id, String name, String arguments) {
        Message message = Message.fromAssistant();
        message.setToolCalls(new ArrayList<>(List.of(new MessageToolCall(id, name, arguments))));
        return message;
    }

    /** 记录 ask 收到的消息列表。 */
    private static class CapturingModel implements LLMModel {
        List<Message> seen = new ArrayList<>();

        @Override public LLMResult ask(Message message) { return new LLMResult(r -> {}); }

        @Override public LLMResult ask(List<Message> messages) { this.seen = messages; return new LLMResult(r -> {}); }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools) {
            this.seen = messages;
            return new LLMResult(r -> {});
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            this.seen = messages;
            return new LLMResult(r -> {});
        }
    }

    /** 复刻 agent4j 的轮内顺序：先记 assistant 消息，再调用 ToolExecutor。 */
    private static class InRoundModel implements LLMModel {
        private final List<Message> appended;
        private final Message assistant;

        InRoundModel(List<Message> appended, Message assistant) {
            this.appended = appended;
            this.assistant = assistant;
        }

        @Override public LLMResult ask(Message message) { return new LLMResult(r -> {}); }

        @Override public LLMResult ask(List<Message> messages) { return new LLMResult(r -> {}); }

        @Override public LLMResult ask(List<Message> messages, List<Tool> tools) { return new LLMResult(r -> {}); }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            return new LLMResult(result -> {
                result.addAppendedMessage(assistant);
                appended.add(assistant);
                toolExecutor.execute("search_web", "{}", new ToolDescriptor(), null);
            });
        }
    }
}
