package ink.icoding.wechat.article.agent;

import ink.icoding.llm.core.entity.Message;
import ink.icoding.llm.core.entity.MessageToolCall;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.model.LLMResult;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 回放守卫：把 agent4j 原样回放、但会被上游网关拒收的历史消息归一到合法形状。目前修两类：
 *
 * <ol>
 *   <li><b>空 / 非法 JSON 的 {@code tool_call.arguments}</b> → 归一化为 {@code {}}。<br>
 *       为什么需要：OpenAI 兼容网关会校验历史里每个 {@code function.arguments} 是否为合法 JSON，
 *       不合法直接整轮 400（{@code Assistant tool call arguments must be valid JSON.}）。而 agent4j 2.3.3 的
 *       {@code OpenAIChatModel} 把流式分片原样拼进 {@code argsBuffer} 且不做校验：当模型发起一次
 *       无参数工具调用时（网关首个分片就是 {@code "arguments":""}，之后不再推送任何 arguments 分片），
 *       历史里就留下 {@code "arguments":""}，下一轮请求必然被拒。定时任务 PIPELINE 的调研/配图阶段
 *       （search_web、list_image_assets 等全可选参数工具）最容易命中。</li>
 *   <li><b>{@code content} 为 null 的消息</b> → 补空串。<br>
 *       为什么需要：网关要求历史里每条消息都带 content，否则整轮 400
 *       （{@code missing `***.content` parameter}）。agent4j 回放「只发工具调用、不输出正文」的
 *       assistant 消息时用 {@code Message.fromAssistant()} 构造，content 为 null，Jackson 序列化成
 *       {@code "content":null} 被判为缺参数。定时任务完成若干次工具调用后必然命中。</li>
 * </ol>
 *
 * <p>修复位置（两处缺一不可）：
 * <ol>
 *   <li>进入 {@code ask} 时先归一化本轮下发的历史（跨轮次的历史修复）；</li>
 *   <li>agent4j 的工具循环在**同一次 ask 内部**继续下一轮（assistant 消息在工具执行前就已落到
 *       {@link LLMResult#getAppendedMessages()}），因此包一层 {@link ToolExecutor}：在工具真正执行前
 *       把刚落库的 assistant 消息修好，保证紧随其后的下一轮请求合法。</li>
 * </ol>
 *
 * <p>策略：只在原值违规时才改写（合法 arguments 原样保留、非 null content 不动），不改变语义。
 */
public final class ToolCallArgumentGuard implements LLMModel {
    private static final Logger log = LoggerFactory.getLogger(ToolCallArgumentGuard.class);
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private static final String EMPTY_OBJECT = "{}";
    /**
     * 带 tool_calls 的 assistant 消息的 content 占位值。必须**非空**：agent4j 对空内容直接写 null，
     * 而网关不接受 null。用单空格是能通过校验的最小值（真机实测通过）。
     */
    private static final String PLACEHOLDER_CONTENT = " ";

    private final LLMModel delegate;

    private ToolCallArgumentGuard(LLMModel delegate) {
        this.delegate = delegate;
    }

    /** 包装模型；null 原样返回，便于装配处无脑调用。 */
    public static LLMModel wrap(LLMModel model) {
        return model == null ? null : new ToolCallArgumentGuard(model);
    }

    @Override
    public LLMResult ask(Message message) {
        if (message != null) normalizeReplay(List.of(message));
        return delegate.ask(message);
    }

    @Override
    public LLMResult ask(List<Message> messages) {
        normalizeReplay(messages);
        return delegate.ask(messages);
    }

    @Override
    public LLMResult ask(List<Message> messages, List<Tool> tools) {
        normalizeReplay(messages);
        return delegate.ask(messages, tools);
    }

    @Override
    public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
        normalizeReplay(messages);
        if (toolExecutor == null) return delegate.ask(messages, tools);
        // 结果引用先占位、ask 返回后回填：工具执行发生在 execute() 之后，此时引用已就绪
        AtomicReference<LLMResult> roundResult = new AtomicReference<>();
        ToolExecutor repairing = (toolName, paramJson, descriptor, handler) -> {
            LLMResult current = roundResult.get();
            if (current != null) normalizeReplay(current.getAppendedMessages());
            return toolExecutor.execute(toolName, paramJson, descriptor, handler);
        };
        LLMResult result = delegate.ask(messages, tools, repairing);
        roundResult.set(result);
        return result;
    }

    @Override
    public void setRequestDebugEnabled(boolean enabled) {
        delegate.setRequestDebugEnabled(enabled);
    }

    @Override
    public boolean isRequestDebugEnabled() {
        return delegate.isRequestDebugEnabled();
    }

    /**
     * 归一化待回放的历史：修 {@code arguments}，并给缺 {@code content} 的消息补空串。返回被修正条数。
     *
     * <p>两类缺陷同源——agent4j 把流式产出原样回放，而网关会逐条校验历史消息的形状。
     */
    /**
     * 归一化待回放的历史：修 {@code arguments}，并给缺 {@code content} 的消息补齐。返回被修正条数。
     *
     * <p>两类缺陷同源——agent4j 把流式产出原样回放，而网关会逐条校验历史消息的形状。
     */
    static int normalizeReplay(List<Message> messages) {
        return normalizeArguments(messages) + normalizeContent(messages);
    }

    /**
     * 补齐 {@code content} 字段：网关要求历史里每条消息都带 content 且不能为 null，否则整轮 400
     * （{@code The request failed because it is missing `***.content` parameter}）。
     *
     * <p>为什么需要：agent4j 回放「带 tool_calls 的 assistant 消息」时用 {@code Message.fromAssistant()}
     * 构造、content 为 null，而它的序列化分支把 null 与空串**一律**写成 {@code content:null}
     * （{@code appendNeutralMessage} 里的 {@code putNull}），网关判定为「缺参数」。定时任务在完成
     * 若干次工具调用后（模型这一轮只发工具调用、不输出正文）必然命中。
     *
     * <p>为什么用空格而不是空串：该分支的判据是 {@code content != null && !content.isEmpty()}，
     * 传空串仍会落到 {@code putNull}；只有非空内容才会走 {@code put("content", ...)}。真机实测
     * nexus 网关对 {@code null} 返回 400、对 {@code ""} / {@code " "} 均返回 200，故取单空格占位
     * （模型侧等价于「这一轮没有正文」，实测不影响后续决策）。
     *
     * <p>其余消息走普通序列化分支，直接 {@code put("content", ...)}：只要不是 null 即可，补空串。
     * tool 消息的线上 content 取自 {@code toolResult.content}，因此修的是 toolResult。
     */
    static int normalizeContent(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int fixed = 0;
        for (Message message : messages) {
            if (message == null) continue;
            if (message.getRole() == Message.Role.tool && message.getToolResult() != null) {
                if (message.getToolResult().getContent() == null) {
                    message.getToolResult().setContent("");
                    fixed++;
                }
                continue;
            }
            if (message.getRole() == Message.Role.assistant && hasToolCalls(message)) {
                if (message.getContent() == null || message.getContent().isEmpty()) {
                    message.setContent(PLACEHOLDER_CONTENT);
                    log.warn("回放的 assistant 工具调用消息 content 为空（工具：{}），已补占位空格，"
                            + "否则网关以 missing content 拒绝整轮", describeToolNames(message));
                    fixed++;
                }
                continue;
            }
            if (message.getContent() == null) {
                message.setContent("");
                fixed++;
            }
        }
        return fixed;
    }

    private static boolean hasToolCalls(Message message) {
        return message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }

    private static String describeToolNames(Message message) {
        StringBuilder names = new StringBuilder();
        for (MessageToolCall call : message.getToolCalls()) {
            if (call == null) continue;
            if (names.length() > 0) names.append(", ");
            names.append(call.getName());
        }
        return names.length() == 0 ? "未知" : names.toString();
    }

    /** 归一化消息列表中的工具调用 arguments，返回被修正的条数。 */
    static int normalizeArguments(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int fixed = 0;
        for (Message message : messages) {
            if (message == null || message.getToolCalls() == null) continue;
            for (MessageToolCall call : new ArrayList<>(message.getToolCalls())) {
                if (call == null) continue;
                String arguments = call.getArgumentsJson();
                if (isValidJson(arguments)) {
                    if (!isObjectJson(arguments)) {
                        // 能解析但不是对象：网关只要求「合法 JSON」，按原值回放而不是悄悄改写语义
                        log.warn("工具调用 {} 的 arguments 是合法 JSON 但不是对象（{}），按原值回放",
                                call.getName(), describe(arguments));
                    }
                    continue;
                }
                log.warn("工具调用 {} 的 arguments 不是合法 JSON（原始值：{}），已按空参数 {} 回放，避免网关 400",
                        call.getName(), describe(arguments), EMPTY_OBJECT);
                call.setArgumentsJson(EMPTY_OBJECT);
                fixed++;
            }
        }
        return fixed;
    }

    /** 可解析即为合法 JSON（裸 null/数组/标量都算）——网关的拒收条件是「不是合法 JSON」，不是「不是对象」。 */
    private static boolean isValidJson(String arguments) {
        if (arguments == null || arguments.isBlank()) return false;
        try {
            JSON.readTree(arguments);
            return true;
        } catch (Exception invalid) {
            return false;
        }
    }

    private static boolean isObjectJson(String arguments) {
        try {
            return JSON.readTree(arguments).isObject();
        } catch (Exception invalid) {
            return false;
        }
    }

    /** 日志用摘要：截断并去掉换行，避免把长内容（可能含敏感片段）整段写进日志。 */
    private static String describe(String arguments) {
        if (arguments == null) return "null";
        String flat = arguments.replaceAll("\\s+", " ").trim();
        if (flat.isEmpty()) return "\"\"";
        return flat.length() > 200 ? "\"" + flat.substring(0, 200) + "…\"" : "\"" + flat + "\"";
    }
}
