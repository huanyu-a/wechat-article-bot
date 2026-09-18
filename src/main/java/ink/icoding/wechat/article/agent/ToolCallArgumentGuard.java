package ink.icoding.wechat.article.agent;

import ink.icoding.llm.core.entity.Message;
import ink.icoding.llm.core.entity.MessageToolCall;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.model.LLMResult;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 回放守卫：把 agent4j 原样回放、但会被上游网关拒收的历史消息归一到合法形状。目前修三类：
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
 *   <li><b>{@code tool_calls[].id} 为 null</b> → 合成稳定占位 id。<br>
 *       为什么需要：网关把 {@code "id":null} 判为缺字段，整轮 400
 *       （{@code messages[10]: missing field `id`}）。见 {@link #normalizeToolCallIds} 的完整证据链。</li>
 * </ol>
 *
 * <h2>修复点（两处）</h2>
 * <ol>
 *   <li><b>进入 {@code ask} 时</b>归一化本轮下发的历史——修的是「上一轮遗留」的消息；</li>
 *   <li><b>本轮工具执行前</b>（包装 {@link ToolExecutor}）归一化
 *       {@link LLMResult#getAppendedMessages()}——修的是「本轮刚产生」的消息。</li>
 * </ol>
 *
 * <p>第 2 点能修到 assistant 的 {@code tool_calls[].id}，靠的是一条**对象共享**事实：
 * {@code handleToolCallsAndContinue} 把**同一个** {@code Message} 实例同时放进「本轮消息列表」
 * 与 {@code appendedMessages}（偏移 106-109 与 115-118 都是 {@code aload 8}），
 * 因此就地改 {@code appendedMessages} 里的对象，就等于改了即将序列化的那份。
 *
 * <h2>已知残留（已于 2026-09-18 消除，见 {@link ReplayWireNormalizer}）</h2>
 * ~~与 assistant 的 id **成对**出现的 tool 消息 {@code tool_call_id}，在本轮内<b>修不到</b>：
 * 它是在工具**执行返回之后**才由 {@code Message.fromTool().withToolResult(entry.callId, ...)} 构造
 * （偏移 254-264，读的是 {@code ToolCallEntry.callId} 而<b>不是</b>传给回调的 {@code ToolDescriptor}），
 * 而紧随其后的下一轮请求在偏移 287 的 {@code executeAgentLoop} 里立刻发出——两者之间不存在任何回调钩子。~~
 *
 * <p><b>更正</b>：上面的字节码事实仍然成立（轮内<b>确实</b>够不着），但由它推出的
 * 「上游限制、本项目改不动」是错的——当时漏掉了<b>报文发出前的 okhttp 接缝</b>。
 * 真机证据（渲染令牌配好后，任务得以跑进长链路）：
 * <pre>
 * messages[20]: missing field `tool_call_id` at line 233 column 3   （run#7，13 次工具调用）
 * messages[27]: missing field `tool_call_id` at line 331 column 3   （run#9，20 次工具调用）
 * </pre>
 * 该 400 是<b>终局</b>的：`AgentInvoker` 只在「本次尝试零工具调用」时重试
 * （{@code AgentInvoker.java:199}），已经跑过工具就不再重试，400 又既非 permanent 也非 transient。
 * 因此「下一轮会收敛」在真实故障里不成立——任务活不到下一轮。
 *
 * <p>现在由 {@link ReplayWireNormalizer} 在报文发出前补齐；本类的轮内归一化<b>保留</b>
 * （先修历史、先修参数，两层幂等互补）。
 *
 * <p>策略：只在原值违规时才改写（合法 arguments 原样保留、非 null content 不动、已有 id 不动），
 * 不改变语义。
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
        // 结果引用先占位、ask 返回后回填：工具执行发生在 ask 内部，此时引用已就绪
        AtomicReference<LLMResult> roundResult = new AtomicReference<>();
        ToolExecutor repairing = (toolName, paramJson, descriptor, handler) -> {
            LLMResult current = roundResult.get();
            // 本轮刚产生的 assistant 消息已在 appendedMessages 里（工具执行前就已落库），
            // 且与即将序列化的那份是**同一个对象**，就地修即可。
            if (current != null) normalizeReplay(current.getAppendedMessages());
            // 参数形状归一化（见 ToolParamRepair）：必须在**交给 agent4j 解析之前**改，
            // 因为 ToolParam.fromJsonString 一抛异常就没有第二次机会了。
            String repaired = ToolParamRepair.repair(toolName, paramJson,
                    descriptor == null ? null : descriptor.getParamClass());
            return toolExecutor.execute(toolName, repaired, descriptor, handler);
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
     * 归一化待回放的历史：修 {@code arguments}、补 {@code content}、补工具调用 id。返回被修正条数。
     *
     * <p>三类缺陷同源——agent4j 把流式产出原样回放，而网关会逐条校验历史消息的形状。
     */
    static int normalizeReplay(List<Message> messages) {
        return normalizeArguments(messages) + normalizeContent(messages) + normalizeToolCallIds(messages);
    }

    /**
     * 补齐工具调用的 id：{@code tool_calls[].id} 与（能修到的）tool 消息 {@code tool_call_id}。
     *
     * <h2>为什么需要（真实缺陷，run#129 配图阶段 HTTP 400）</h2>
     * 网关返回 400：{@code The request is invalid: Failed to deserialize the JSON body into the target
     * type: messages[10]: missing field `id` at line 1 column 56469}。证据链（agent4j 2.3.3 字节码）：
     * <ol>
     *   <li>{@code OpenAIChatModel$1} 的 SSE 解析器只在 {@code hasTextValue(idNode)} 为真时写
     *       {@code ToolCallEntry.callId}（偏移 591 → 604）；而 {@code hasTextValue} 对 {@code null}
     *       与「节点缺失」<b>都</b>返回 false。网关把 {@code id} 放在后续分片时，{@code callId} 就是 null。</li>
     *   <li>{@code handleToolCallsAndContinue} 偏移 96 用该 null 调
     *       {@code Message.appendToolCall(callId, ...)}；{@code Message.appendToolCall} <b>不做兜底</b>
     *       （探针实测：传入 null 后 {@code getId()} 仍为 null）。</li>
     *   <li>{@code appendNeutralMessage} 偏移 182 调 {@code ObjectNode.put("id", getId())}。
     *       Jackson 的 {@code put(String,String)} 对 null 写出的是<b>显式 JSON null</b>
     *       （探针实测：{@code {"id":null,...}}），网关 serde 判为缺字段。</li>
     * </ol>
     * 「只有 messages[10] 报错、前面的工具轮次都通过」这一事实同时说明：网关<b>通常</b>会推送 id，
     * 只有个别调用的 id 落在独立分片里被 agent4j 丢掉——所以这是**间歇性**缺陷，不是每次都炸。
     *
     * <h2>配对规则</h2>
     * 用「最近一条带 tool_calls 的 assistant 消息」的 id 队列，按出现顺序依次配给其后的 tool 消息
     * ——这正是 OpenAI 协议里 tool 消息与 tool_calls 的顺序对应语义。合成的 id 由**消息位置**推导，
     * 因此是幂等的：同一份历史反复归一化得到同一个 id，不会每次调用都换一个值。
     */
    static int normalizeToolCallIds(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int fixed = 0;
        // 最近一条 assistant 工具调用消息的 id 队列（按 tool_calls 顺序）
        Deque<String> pending = new ArrayDeque<>();
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            if (message == null) continue;
            if (message.getRole() == Message.Role.assistant && hasToolCalls(message)) {
                pending.clear();
                List<MessageToolCall> calls = message.getToolCalls();
                for (int slot = 0; slot < calls.size(); slot++) {
                    MessageToolCall call = calls.get(slot);
                    if (call == null) continue;
                    if (isBlank(call.getId())) {
                        call.setId(synthesizeId(index, slot, call.getName()));
                        log.warn("回放的工具调用缺少 id（工具：{}，消息位置 {}），已合成占位 id {}，"
                                + "否则网关以 missing field `id` 拒绝整轮", call.getName(), index, call.getId());
                        fixed++;
                    }
                    pending.addLast(call.getId());
                }
                continue;
            }
            if (message.getRole() == Message.Role.tool && message.getToolResult() != null) {
                String matched = pending.pollFirst();
                if (isBlank(message.getToolResult().getToolCallId())) {
                    // 兜底：没有可配对的 assistant 调用时按消息位置合成，保证字段非 null。
                    String id = matched == null ? synthesizeId(index, 0, "tool") : matched;
                    message.getToolResult().setToolCallId(id);
                    log.warn("回放的 tool 消息缺少 tool_call_id（消息位置 {}），已补齐为 {}，"
                            + "否则网关以 missing field `tool_call_id` 拒绝整轮", index, id);
                    fixed++;
                }
            }
        }
        return fixed;
    }

    /** 由消息位置与槽位推导的稳定占位 id：同一份历史反复归一化必得同一个值。 */
    private static String synthesizeId(int messageIndex, int slot, String toolName) {
        String name = toolName == null || toolName.isBlank() ? "tool" : toolName;
        return "call_" + messageIndex + "_" + slot + "_" + name;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
