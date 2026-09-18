package ink.icoding.wechat.article.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ink.icoding.llm.core.model.LLMModel;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * 回放守卫的<b>网线级兜底</b>：在请求真正发出之前，直接修正 JSON 报文里缺失的工具调用 id。
 *
 * <h2>为什么轮内钩子不够（本类存在的全部理由）</h2>
 * {@link ToolCallArgumentGuard} 包装 {@code ToolExecutor}，在每次工具执行<b>前</b>归一化
 * {@code LLMResult.getAppendedMessages()}。这能修到 assistant 的 {@code tool_calls[].id}（对象共享），
 * 但<b>修不到 tool 消息的 {@code tool_call_id}</b>，证据链（agent4j 2.3.3 字节码，{@code javap -c -p}）：
 * <ol>
 *   <li>{@code handleToolCallsAndContinue} 偏移 231 调 {@code toolExecutor.execute(...)}——这是唯一回调点；</li>
 *   <li>偏移 254-264 才用 {@code Message.fromTool().withToolResult(entry.callId, result)} 构造 tool 消息，
 *       读的是 {@code ToolCallEntry.callId} 字段（偏移 259 的 {@code getfield}），
 *       <b>而不是</b>传给回调的 {@code ToolDescriptor}——偏移 181 的 {@code setCallId} 只是一份副本，无人再读；</li>
 *   <li>偏移 287-293 {@code executeAgentLoop} 立刻发出下一轮请求。</li>
 * </ol>
 * 于是「一轮里最后一次工具调用」产生的 tool 消息，在回调返回后、报文发出前<b>没有任何钩子</b>可碰。
 * 该字段为 null 时 Jackson 的 {@code put(String,String)} 写出显式 {@code "tool_call_id":null}，
 * 网关 serde 判为缺字段，整轮 400：
 * <pre>
 * HTTP 400 {"error":{"message":"Failed to deserialize the JSON body into the target type:
 * messages[20]: missing field `tool_call_id` at line 233 column 3","code":"json_parse_error"}}
 * </pre>
 * 真机实测（run#7 13 次工具调用 / run#9 20 次工具调用各命中一次）。该 400 是<b>终局</b>的：
 * {@code AgentInvoker} 的重试前置条件是「本次尝试零工具调用」（{@code retryable = attempt.toolCalls() == 0}），
 * 已经跑过工具就不再重试，而 400 既非 permanent 也非 transient，没有任何重试路径兜住它。
 *
 * <h2>为什么用拦截器 + 反射</h2>
 * 报文由 {@code OpenAIChatModel.buildRequestBody} 构造后，在 {@code executeAgentLoop} 偏移 3 转成
 * {@code String} 交给 okhttp（偏移 32-40）。okhttp 的 {@code client} 字段是 {@code private final}，
 * 且 {@code LLMModel.create} 不提供注入客户端或拦截器的入口——反射重建客户端是本项目能碰到的
 * <b>唯一</b>能把报文改在发出前的接缝。实测（{@code ClientSeamProbe}）：非编译期常量的
 * {@code private final} 实例字段可以被 {@code setAccessible(true)} 后改写，且
 * {@code executeAgentLoop} 每次请求都重新读该字段（偏移 77-80 {@code getfield client} →
 * {@code EventSources.createFactory}），因此换掉的客户端立即生效。
 *
 * <p><b>失败降级</b>：挂载失败只 WARN 不抛——轮内归一化仍在，功能退回修复前的水平，
 * 不会因为 agent4j 内部结构变化而让整个应用起不来。{@link #isAttached} 供测试断言挂载真的成功，
 * 这样上游改名字段时是<b>测试变红</b>，而不是静默失效。
 *
 * <p>合成 id 的规则与 {@link ToolCallArgumentGuard#normalizeToolCallIds} 完全一致
 * （{@code call_{消息位置}_{槽位}_{工具名}}），两层归一化因此幂等、互不打架。
 */
public final class ReplayWireNormalizer implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(ReplayWireNormalizer.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        RequestBody body = request.body();
        // 非一次性请求体才可重复读取；SSE 请求体是普通 RequestBody.create(String, MediaType)
        if (body == null || body.isOneShot() || body.isDuplex()) return chain.proceed(request);
        MediaType contentType = body.contentType();
        if (contentType == null || !contentType.subtype().toLowerCase(Locale.ROOT).contains("json")) {
            return chain.proceed(request);
        }
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        String original = buffer.readUtf8();
        String normalized = normalizeBody(original);
        // 只在真有违规时才改写：报文没毛病就原样发出，避免重新序列化扰动字节
        if (normalized.equals(original)) return chain.proceed(request);
        Request rewritten = request.newBuilder()
                .method(request.method(), RequestBody.create(normalized, contentType))
                .build();
        return chain.proceed(rewritten);
    }

    /**
     * 修正报文里缺失的工具调用 id；没有任何违规时<b>原样返回入参字符串</b>。
     *
     * <p>两类违规，与网关的两条 400 一一对应：
     * <ul>
     *   <li>带 {@code tool_calls} 的 assistant 消息里某一项的 {@code id} 为 null / 缺失
     *       → 合成 {@code call_{位置}_{槽位}_{工具名}}（{@code missing field `id`}）；</li>
     *   <li>{@code role:"tool"} 消息的 {@code tool_call_id} 为 null / 缺失
     *       → 用前面最近一条 assistant 消息的 id 队列按顺序配对（{@code missing field `tool_call_id`}）。</li>
     * </ul>
     */
    static String normalizeBody(String json) {
        if (json == null || json.isBlank()) return json;
        JsonNode parsed;
        try {
            parsed = JSON.readTree(json);
        } catch (Exception notJson) {
            return json;
        }
        if (!(parsed instanceof ObjectNode root)) return json;
        if (!(root.get("messages") instanceof ArrayNode messages)) return json;

        boolean changed = false;
        // 最近一条带 tool_calls 的 assistant 消息的 id 队列（按 tool_calls 顺序）
        Deque<String> pending = new ArrayDeque<>();
        for (int index = 0; index < messages.size(); index++) {
            if (!(messages.get(index) instanceof ObjectNode message)) continue;
            if (message.get("tool_calls") instanceof ArrayNode calls && !calls.isEmpty()) {
                pending.clear();
                for (int slot = 0; slot < calls.size(); slot++) {
                    if (!(calls.get(slot) instanceof ObjectNode call)) continue;
                    String id = textOf(call.get("id"));
                    if (id == null) {
                        id = synthesizeId(index, slot, toolNameOf(call));
                        call.put("id", id);
                        changed = true;
                        log.warn("网线兜底：回放的 tool_calls[{}]（消息位置 {}，工具 {}）缺少 id，已补齐为 {}，"
                                + "否则网关以 missing field `id` 拒绝整轮", slot, index, toolNameOf(call), id);
                    }
                    pending.addLast(id);
                }
                continue;
            }
            if (!"tool".equals(textOf(message.get("role")))) continue;
            // 与守卫一致：无论是否需要修补都推进队列，保证配对顺序语义
            String matched = pending.pollFirst();
            if (textOf(message.get("tool_call_id")) == null) {
                String id = matched == null ? synthesizeId(index, 0, "tool") : matched;
                message.put("tool_call_id", id);
                changed = true;
                log.warn("网线兜底：回放的 tool 消息（消息位置 {}）缺少 tool_call_id，已补齐为 {}，"
                        + "否则网关以 missing field `tool_call_id` 拒绝整轮", index, id);
            }
        }
        return changed ? root.toString() : json;
    }

    /**
     * 给模型挂上网线级兜底。必须作用于 {@code LLMModel.create} 返回的<b>原始模型</b>——
     * 包装器（如 {@link ToolCallArgumentGuard}）自身不含 {@code client} 字段。
     *
     * <p>返回同一个模型实例，便于装配处链式书写；null 原样返回。
     */
    public static LLMModel attach(LLMModel model) {
        if (model == null) return null;
        try {
            Field field = clientField(model.getClass());
            if (field == null) {
                log.warn("网线级回放兜底未挂载：{} 里找不到 OkHttpClient 字段（agent4j 内部结构可能已变化），"
                        + "将只依赖轮内归一化", model.getClass().getName());
                return model;
            }
            field.setAccessible(true);
            OkHttpClient client = (OkHttpClient) field.get(model);
            if (client == null) {
                log.warn("网线级回放兜底未挂载：{} 的 OkHttpClient 为 null", model.getClass().getName());
                return model;
            }
            if (containsNormalizer(client)) return model;
            field.set(model, client.newBuilder().addInterceptor(new ReplayWireNormalizer()).build());
            log.info("网线级回放兜底已挂载：{}（补齐 assistant tool_calls[].id 与 tool 消息 tool_call_id）",
                    model.getClass().getSimpleName());
        } catch (Throwable failure) {
            log.warn("网线级回放兜底挂载失败，退回轮内归一化：{}", failure.toString());
        }
        return model;
    }

    /** 测试用：断言拦截器真的挂上了（上游改字段名时应当变红，而不是静默失效）。 */
    static boolean isAttached(LLMModel model) {
        if (model == null) return false;
        try {
            Field field = clientField(model.getClass());
            if (field == null) return false;
            field.setAccessible(true);
            OkHttpClient client = (OkHttpClient) field.get(model);
            return client != null && containsNormalizer(client);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsNormalizer(OkHttpClient client) {
        for (Interceptor interceptor : client.interceptors()) {
            if (interceptor instanceof ReplayWireNormalizer) return true;
        }
        return false;
    }

    /** 沿继承链找 {@code OkHttpClient} 字段；找不到返回 null。 */
    private static Field clientField(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (OkHttpClient.class.isAssignableFrom(field.getType())) return field;
            }
        }
        return null;
    }

    /** 非空文本；null / 缺失 / 空白都算违规。 */
    private static String textOf(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String text = node.asText("");
        return text.isBlank() ? null : text;
    }

    private static String toolNameOf(ObjectNode call) {
        String name = call.path("function").path("name").asText("");
        return name.isBlank() ? "tool" : name;
    }

    /** 与 {@link ToolCallArgumentGuard} 相同的稳定占位 id：同一份历史反复归一化必得同一个值。 */
    private static String synthesizeId(int messageIndex, int slot, String toolName) {
        String name = toolName == null || toolName.isBlank() ? "tool" : toolName;
        return "call_" + messageIndex + "_" + slot + "_" + name;
    }
}
