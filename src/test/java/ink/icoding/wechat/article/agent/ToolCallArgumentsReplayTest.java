package ink.icoding.wechat.article.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.entity.ModelType;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 回归测试：模型把「无参数工具调用」的空 arguments 原样写回历史，OpenAI 兼容网关会以
 * 400「Assistant tool call arguments must be valid JSON」拒绝整轮请求（真实缺陷：
 * 定时任务 PIPELINE 模式研究阶段调用无参工具后整轮失败、工具调用计数为 0）。
 *
 * 用 JDK 内置 HttpServer 伪造 SSE 通道：第一轮返回带空 arguments 的工具调用，
 * 第二轮按网关规则校验历史 arguments 是否为合法 JSON（不合法即 400），
 * 因此该用例在缺陷存在时必然失败、修复后必然通过，且不依赖外网。
 */
class ToolCallArgumentsReplayTest {
    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<String> requestBodies = new ArrayList<>();
    private final AtomicInteger rejectedAsInvalidJson = new AtomicInteger();
    private final AtomicInteger rejectedAsMissingContent = new AtomicInteger();
    private final AtomicInteger rejectedAsMissingToolCallId = new AtomicInteger();
    private final List<String> messagesMissingContent = new ArrayList<>();
    private final List<String> messagesMissingToolCallId = new ArrayList<>();
    /** 网关校验开关：默认两条规则都查；版本锚定用例只考察 content 那一条。 */
    private volatile boolean validateArguments = true;
    /** 网关校验开关：默认查 content；id 版本锚定用例要单独关掉，否则 content 会先拒绝、掩盖 id 缺陷。 */
    private volatile boolean validateContent = true;
    /**
     * 首轮 SSE 是否省略 {@code tool_calls[].id}：复刻真实网关把 id 放在后续分片、
     * 而 agent4j 只在「本分片带 id」时才记录 callId 的情形（缺 id 时 callId 保持 null）。
     */
    private volatile boolean omitToolCallIdOnFirstRound = false;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void replaysToolCallWithNoArgumentsAsEmptyJsonObject() {
        AgentClient agent = new AgentClient();
        agent.setName("调研员");
        agent.setDescription("你是调研员。");
        agent.setModel(ToolCallArgumentGuard.wrap(
                LLMModel.create(ModelType.OpenAI, baseUrl(), "mock-model", "test-key")));
        agent.setTools(List.of(new ReadDraftTool()));

        AgentClientSession session = agent.createSession();
        AgentSessionResult result = session.command("读取当前草稿状态。");

        assertThatCode(result::execute).doesNotThrowAnyException();
        assertThatCode(result::get).doesNotThrowAnyException();

        // 首轮历史归一化 + 工具执行前修复本轮 assistant 消息，两处都不能少
        assertThat(requestCount.get()).isEqualTo(2);
        assertThat(rejectedAsInvalidJson.get())
                .as("网关不应因历史里的非法 arguments 拒绝请求")
                .isZero();
        assertThat(requestBodies.get(1))
                .as("空 arguments 必须归一化为 {} 后再回传给网关")
                .contains("\"arguments\":\"{}\"");
    }

    /**
     * 回归测试：真实网关要求历史里每条消息都带 content，而 agent4j 回放「带 tool_calls 的
     * assistant 消息」时用 Message.fromAssistant() 构造、content 为 null，序列化后字段缺失
     * 或被写成 null，网关以 400「missing `***.content` parameter」拒绝整轮请求（真实缺陷：
     * 定时任务完成若干次 search_web 工具调用后整轮失败）。
     */
    @Test
    void replaysAssistantToolCallMessageWithNonNullContent() {
        AgentClient agent = new AgentClient();
        agent.setName("调研员");
        agent.setDescription("你是调研员。");
        agent.setModel(ToolCallArgumentGuard.wrap(
                LLMModel.create(ModelType.OpenAI, baseUrl(), "mock-model", "test-key")));
        agent.setTools(List.of(new ReadDraftTool()));

        AgentClientSession session = agent.createSession();
        AgentSessionResult result = session.command("读取当前草稿状态。");

        assertThatCode(result::execute).doesNotThrowAnyException();
        assertThatCode(result::get).doesNotThrowAnyException();

        assertThat(rejectedAsMissingContent.get())
                .as("回放历史里不能出现缺 content 的消息，实际违规：" + messagesMissingContent)
                .isZero();
        assertThat(rejectedAsInvalidJson.get()).as("arguments 归一化不应回归").isZero();
        assertThat(requestBodies.get(1))
                .as("带 tool_calls 的 assistant 回放消息必须带非空 content（agent4j 对空内容写 null，网关拒收）")
                .contains("\"content\":\" \"");
    }

    /**
     * 版本锚定 canary（reviewer P2-2）：上面两条用例证明「有守卫就通过」，这条证明
     * **没有守卫时上游仍然会失败**——两者合起来才能说明守卫不是摆设，也不会随 agent4j 升级悄悄失效。
     *
     * <p>锁的是上游的两个事实：① agent4j 回放「带 tool_calls 的 assistant 消息」时 content 为 null，
     * 序列化后是 {@code "content":null}；② 这一步发生在同一次 ask 内部的下一轮请求上，
     * 因此守卫必须能在轮内拦截。若某天 agent4j 修好了 ① 或改了 ② 的顺序，这条用例会失败——
     * 那正是重新评估 {@link ToolCallArgumentGuard} 是否仍有必要、注入点是否仍正确的信号，
     * 而不是「测试挂了随便改绿」。
     */
    @Test
    void unguardedReplayStillOmitsAssistantContentOnTheWire() {
        // 只考察 content 一条规则：先把 arguments 那条关掉，否则网关会先以 JSON 规则拒绝、看不到 content 问题
        validateArguments = false;
        AgentClient agent = new AgentClient();
        agent.setName("调研员");
        agent.setDescription("你是调研员。");
        agent.setModel(LLMModel.create(ModelType.OpenAI, baseUrl(), "mock-model", "test-key"));
        agent.setTools(List.of(new ReadDraftTool()));

        try {
            AgentClientSession session = agent.createSession();
            AgentSessionResult result = session.command("读取当前草稿状态。");
            result.execute();
            result.get();
        } catch (Exception expected) {
            // 网关 400 以异常形式回到调用方；这里只断言网关侧观测到的事实
        }

        assertThat(requestCount.get())
                .as("必须真的发出第二轮请求，否则内容缺陷没有被触发")
                .isGreaterThanOrEqualTo(2);
        assertThat(rejectedAsMissingContent.get())
                .as("agent4j 2.3.3 仍会回放 content 为 null 的 assistant 工具调用消息；"
                        + "若此断言失败，说明上游已修复，请重新评估 ToolCallArgumentGuard 的必要性")
                .isEqualTo(1);
        assertThat(requestBodies.get(1))
                .as("无守卫时第二轮报文里应能看到 content:null（守卫存在的全部理由）")
                .contains("\"content\":null");
    }

    /**
     * 回归测试：网关未推送 {@code tool_calls[].id} 时，agent4j 把 callId 留成 null，
     * 序列化后是 {@code "id":null}，网关以 400「missing field `id`」拒绝整轮请求
     * （真实缺陷：run#129 配图委托阶段，{@code messages[10]: missing field `id`}，
     * 导致写作阶段从未产出草稿、审核阶段 7 次 submit_review 全失败后硬超时）。
     */
    @Test
    void replaysToolCallWithMissingIdUsingStableSynthesizedId() {
        omitToolCallIdOnFirstRound = true;
        AgentClient agent = new AgentClient();
        agent.setName("配图员");
        agent.setDescription("你是配图员。");
        agent.setModel(ToolCallArgumentGuard.wrap(ReplayWireNormalizer.attach(
                LLMModel.create(ModelType.OpenAI, baseUrl(), "mock-model", "test-key"))));
        agent.setTools(List.of(new ReadDraftTool()));

        AgentClientSession session = agent.createSession();
        AgentSessionResult result = session.command("读取当前草稿状态。");

        assertThatCode(result::execute).doesNotThrowAnyException();
        assertThatCode(result::get).doesNotThrowAnyException();

        assertThat(rejectedAsMissingToolCallId.get())
                .as("回放历史里 tool_calls 不得出现 null/缺失 id，实际违规：" + messagesMissingToolCallId)
                .isZero();
        // 归一化必须**幂等**：同一份历史反复归一化要得到同一个 id，否则每一轮都会换一个值、
        // 且 tool 消息的 tool_call_id 会指向一个已不存在的 id。
        assertThat(synthesizedIdOf(requestBodies.get(1), "read_article_draft"))
                .as("缺失 id 必须被合成为非空占位 id")
                .isNotNull();
        assertThat(synthesizedIdOf(requestBodies.get(1), "read_article_draft"))
                .as("同一份历史两次归一化必须得到同一个 id（幂等）")
                .isEqualTo(synthesizedIdOf(requestBodies.get(1), "read_article_draft"));
        // 原为「已知残留」canary（断言第二轮报文里 tool 消息的 tool_call_id 仍为 null）。
        // 2026-09-18 真机证伪了「网关不校验 tool 侧」这一前提（messages[27]: missing field
        // `tool_call_id`，且该 400 终局不可重试），于是加装了网线级兜底 ReplayWireNormalizer。
        // 现在断言翻转为「必须非 null 且与 assistant 的 id 配对一致」——这正是修复的判据。
        assertThat(toolMessageIdIn(requestBodies.get(1)))
                .as("网线级兜底必须补上本轮最后一条 tool 消息的 tool_call_id（轮内钩子够不着它）")
                .isNotNull()
                .isEqualTo(synthesizedIdOf(requestBodies.get(1), "read_article_draft"));
    }

    /**
     * 回归测试（2026-09-18 真机缺陷）：一轮里<b>最后一次</b>工具调用产生的 tool 消息，
     * 其 {@code tool_call_id} 在轮内<b>无法</b>修复——它由 {@code handleToolCallsAndContinue}
     * 偏移 254 在工具执行返回之后用 {@code ToolEntry.callId} 构造（偏移 259 的 {@code getfield}
     * 读的是 {@code ToolCallEntry} 字段，而非传给回调的 {@code ToolDescriptor}），
     * 紧随其后的下一轮请求在偏移 287-293 立刻发出，两者之间没有任何回调钩子。
     *
     * <p>因此修复必须落在报文发出前的网线层。本用例的真实缺陷证据：
     * {@code messages[20]: missing field `tool_call_id`}（run#7）、{@code messages[27]}（run#9），
     * 均为 HTTP 400 {@code json_parse_error} 且任务直接 FAILED。
     */
    @Test
    void wireNormalizerFillsToolCallIdForLastToolCallOfRound() {
        omitToolCallIdOnFirstRound = true;
        AgentClient agent = new AgentClient();
        agent.setName("配图员");
        agent.setDescription("你是配图员。");
        agent.setModel(ToolCallArgumentGuard.wrap(ReplayWireNormalizer.attach(
                LLMModel.create(ModelType.OpenAI, baseUrl(), "mock-model", "test-key"))));
        agent.setTools(List.of(new ReadDraftTool()));

        AgentClientSession session = agent.createSession();
        AgentSessionResult result = session.command("读取当前草稿状态。");

        assertThatCode(result::execute).doesNotThrowAnyException();
        assertThatCode(result::get).doesNotThrowAnyException();

        assertThat(rejectedAsMissingToolCallId.get())
                .as("报文里不得再出现缺 id / 缺 tool_call_id 的消息，实际违规：" + messagesMissingToolCallId)
                .isZero();
        assertThat(toolMessageIdIn(requestBodies.get(1)))
                .as("最后一条 tool 消息的 tool_call_id 必须由网线兜底补齐")
                .isNotNull();
        assertThat(synthesizedIdOf(requestBodies.get(1), "read_article_draft"))
                .as("assistant 侧 tool_calls[].id 仍必须是非空占位 id")
                .isNotNull();
        assertThat(toolMessageIdIn(requestBodies.get(1)))
                .as("两侧 id 必须一致配对，否则网关语义上无法把工具结果挂回调用")
                .isEqualTo(synthesizedIdOf(requestBodies.get(1), "read_article_draft"));
    }

    /**
     * 挂载 canary：{@link ReplayWireNormalizer} 靠反射重建 {@code OpenAIChatModel.client}
     * （okhttp 客户端字段是 {@code private final}，{@code LLMModel.create} 不提供注入入口）。
     * 若上游改了字段名/类型，挂载会静默降级——这条用例让它在 CI 里变红而不是悄悄失效。
     */
    @Test
    void wireNormalizerAttachesItselfToTheOkHttpClient() {
        LLMModel model = LLMModel.create(ModelType.OpenAI, baseUrl(), "mock-model", "test-key");
        assertThat(ReplayWireNormalizer.isAttached(model))
                .as("attach 之前不应已经在链上（否则说明静态状态被污染）")
                .isFalse();

        ReplayWireNormalizer.attach(model);

        assertThat(ReplayWireNormalizer.isAttached(model))
                .as("attach 之后拦截器必须真的在 okhttp 客户端链路里；"
                        + "失败说明 agent4j 的 client 字段形态变了，需要同步修改 clientField 查找逻辑")
                .isTrue();
    }

    /**
     * 版本锚定 canary：证明**没有守卫时上游仍然会发出 null id**——与上面的绿灯用例合起来，
     * 才能说明守卫不是摆设，且不会随 agent4j 升级悄悄失效。
     *
     * <p>锁的是上游事实：agent4j 只在「本分片带 id」时才记录 callId
     * （{@code OpenAIChatModel$1} 偏移 591 {@code hasTextValue} → 604 {@code putfield callId}），
     * 缺 id 时 {@code appendToolCall(null,...)} 不做兜底。若某天 agent4j 自己补了 id，
     * 这条用例会失败——那正是重新评估 {@link ToolCallArgumentGuard} 是否仍有必要的信号。
     */
    @Test
    void unguardedReplayStillOmitsToolCallIdOnTheWire() {
        omitToolCallIdOnFirstRound = true;
        // 只考察 id 一条规则：关掉 content 与 arguments，否则它们会先拒绝、看不到 id 问题
        validateArguments = false;
        validateContent = false;
        AgentClient agent = new AgentClient();
        agent.setName("配图员");
        agent.setDescription("你是配图员。");
        agent.setModel(LLMModel.create(ModelType.OpenAI, baseUrl(), "mock-model", "test-key"));
        agent.setTools(List.of(new ReadDraftTool()));

        try {
            AgentClientSession session = agent.createSession();
            AgentSessionResult result = session.command("读取当前草稿状态。");
            result.execute();
            result.get();
        } catch (Exception expected) {
            // 网关 400 以异常形式回到调用方；这里只断言网关侧观测到的事实
        }

        assertThat(requestCount.get())
                .as("必须真的发出第二轮请求，否则 id 缺陷没有被触发")
                .isGreaterThanOrEqualTo(2);
        assertThat(rejectedAsMissingToolCallId.get())
                .as("agent4j 2.3.3 缺 id 时仍会回放 null 的 tool_calls[].id；"
                        + "若此断言失败，说明上游已自行兜底，请重新评估 ToolCallArgumentGuard 的必要性")
                .isEqualTo(1);
        assertThat(requestBodies.get(1))
                .as("无守卫时第二轮报文里应能看到 \"id\":null（守卫存在的全部理由）")
                .contains("\"id\":null");
    }

    /** 取报文里第一条带工具调用消息中某工具名对应的 id——用来断言合成了 id 且幂等。 */
    private static String synthesizedIdOf(String body, String toolName) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            for (com.fasterxml.jackson.databind.JsonNode message : root.path("messages")) {
                for (com.fasterxml.jackson.databind.JsonNode call : message.path("tool_calls")) {
                    if (!toolName.equals(call.path("function").path("name").asText(""))) continue;
                    String id = call.get("id") == null ? null : call.get("id").asText("");
                    return id == null || id.isBlank() ? null : id;
                }
            }
            return null;
        } catch (Exception unreadable) {
            return null;
        }
    }

    /** 取报文里第一条 {@code role:"tool"} 消息的 tool_call_id。 */
    private static String toolMessageIdIn(String body) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            for (com.fasterxml.jackson.databind.JsonNode message : root.path("messages")) {
                if (!"tool".equals(message.path("role").asText(""))) continue;
                com.fasterxml.jackson.databind.JsonNode id = message.get("tool_call_id");
                return id == null || id.isNull() ? null : id.asText("");
            }
            return null;
        } catch (Exception unreadable) {
            return null;
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int round = requestCount.incrementAndGet();
        requestBodies.add(body);

        if (round == 1) {
            // 真实网关行为：首个 delta 带 arguments:""，无参工具调用之后不再推送任何 arguments 分片
            // omitToolCallIdOnFirstRound 时连 id 也一并省略：复刻「网关把 id 放在后续分片」的形态，
            // agent4j 因此把 callId 留成 null（hasTextValue 对缺失节点返回 false）
            String toolCall = omitToolCallIdOnFirstRound
                    ? "{\"type\":\"function\",\"index\":0,"
                            + "\"function\":{\"name\":\"read_article_draft\",\"arguments\":\"\"}}"
                    : "{\"id\":\"call_1\",\"type\":\"function\",\"index\":0,"
                            + "\"function\":{\"name\":\"read_article_draft\",\"arguments\":\"\"}}";
            respondSse(exchange, String.join("\n\n",
                    "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                            + "\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}",
                    "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                            + "\"delta\":{\"tool_calls\":[" + toolCall + "]}}]}",
                    "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                            + "\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                    "data: [DONE]"));
            return;
        }

        String invalid = validateArguments ? firstInvalidArguments(body) : null;
        if (invalid != null) {
            rejectedAsInvalidJson.incrementAndGet();
            byte[] error = ("{\"error\":{\"message\":\"Assistant tool call arguments must be valid JSON.\","
                    + "\"type\":\"BadRequest\",\"code\":400},\"offending\":" + escape(invalid) + "}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, error.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(error);
            }
            return;
        }

        // 复刻真实网关的 serde 行为：tool_calls[].id 为 null（或缺失）时报
        // 「missing field `id`」拒绝整轮——注意 Jackson 的 put(String,String) 对 null
        // 写出的是**显式 null**，网关的 Rust serde 把它判为缺字段（真实缺陷 run#129 配图阶段）。
        String missingId = findToolCallMissingId(body);
        if (missingId != null) {
            rejectedAsMissingToolCallId.incrementAndGet();
            messagesMissingToolCallId.add(missingId);
            byte[] error = ("{\"error\":{\"message\":\"The request is invalid: Failed to deserialize the JSON "
                    + "body into the target type: messages[10]: missing field `id`\",\"type\":\"BadRequest\","
                    + "\"code\":400},\"offending\":" + escape(missingId) + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, error.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(error);
            }
            return;
        }

        String missingContent = validateContent ? findMessageMissingContent(body) : null;
        if (missingContent != null) {
            rejectedAsMissingContent.incrementAndGet();
            messagesMissingContent.add(missingContent);
            byte[] error = ("{\"error\":{\"message\":\"The request failed because it is missing "
                    + "`***.content` parameter.\",\"type\":\"invalid_request_error\","
                    + "\"param\":\"\",\"code\":\"400001\"},\"offending\":" + escape(missingContent) + "}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, error.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(error);
            }
            return;
        }
        respondSse(exchange, String.join("\n\n",
                "data: {\"id\":\"2\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                        + "\"delta\":{\"role\":\"assistant\",\"content\":\"草稿为空。\"}}]}",
                "data: {\"id\":\"2\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                        + "\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]"));
    }

    /** 返回第一个非法（空或非 JSON）的历史工具调用 arguments；全部合法时返回 null。 */
    private static String firstInvalidArguments(String body) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            for (com.fasterxml.jackson.databind.JsonNode message : root.path("messages")) {
                for (com.fasterxml.jackson.databind.JsonNode call : message.path("tool_calls")) {
                    String arguments = call.path("function").path("arguments").asText("");
                    if (arguments.isBlank()) return arguments;
                    try {
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(arguments);
                    } catch (Exception invalidJson) {
                        return arguments;
                    }
                }
            }
            return null;
        } catch (Exception unreadable) {
            return body;
        }
    }

    /**
     * 复刻真实网关的 id 校验：{@code tool_calls[]} 里每一项都必须有非 null 的 {@code id}，
     * 且每条 {@code role:"tool"} 消息都必须有非 null 的 {@code tool_call_id}，
     * 否则分别报 {@code missing field `id`} / {@code missing field `tool_call_id`} 拒绝整轮。
     * 返回违规项的 JSON 片段；全部合规时返回 null。
     *
     * <p><b>为什么两侧都查（本节曾在 2026-09-18 被修正）</b>：早期版本只查 assistant 侧，
     * 理由写的是「{@code tool_call_id} 从未出现在网关报错里」。该理由已被真机证伪——
     * 渲染令牌配置完成后，定时任务连续两次整轮 400：
     * <pre>
     * messages[20]: missing field `tool_call_id` at line 233 column 3   （run#7，13 次工具调用）
     * messages[27]: missing field `tool_call_id` at line 331 column 3   （run#9，20 次工具调用）
     * </pre>
     * 即网关<b>确实</b>校验 tool 侧字段，而且这条 400 是<b>终局</b>的：已经跑过工具就不再满足
     * {@code AgentInvoker} 的重试前置条件（{@code retryable = attempt.toolCalls() == 0}），
     * 400 又既非 permanent 也非 transient，没有任何重试路径兜住它。把这条规则写进 mock，
     * 才是复刻真机契约；只查一侧等于放走真实缺陷。
     */
    private static String findToolCallMissingId(String body) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            List<String> offenders = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode message : root.path("messages")) {
                for (com.fasterxml.jackson.databind.JsonNode call : message.path("tool_calls")) {
                    com.fasterxml.jackson.databind.JsonNode id = call.get("id");
                    if (id == null || id.isNull() || id.asText("").isBlank()) {
                        offenders.add("assistant " + call.toString());
                    }
                }
                if (!"tool".equals(message.path("role").asText(""))) continue;
                com.fasterxml.jackson.databind.JsonNode toolCallId = message.get("tool_call_id");
                if (toolCallId == null || toolCallId.isNull() || toolCallId.asText("").isBlank()) {
                    offenders.add("tool " + message.toString());
                }
            }
            return offenders.isEmpty() ? null : String.join(" ### ", offenders);
        } catch (Exception unreadable) {
            return null;
        }
    }

    /**
     * 复刻真实网关的 content 校验：任何一条消息（含带 tool_calls 的 assistant 消息）都不得
     * 缺少 content 字段或 content 为 null，否则以 400「missing `***.content` parameter」拒绝。
     * 返回第一条违规消息的 JSON 片段；全部合规时返回 null。
     */
    private static String findMessageMissingContent(String body) {
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            List<String> offenders = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode message : root.path("messages")) {
                com.fasterxml.jackson.databind.JsonNode content = message.get("content");
                if (content == null || content.isNull()) offenders.add(message.toString());
            }
            return offenders.isEmpty() ? null : String.join(" ### ", offenders);
        } catch (Exception unreadable) {
            return null;
        }
    }

    private static String escape(String raw) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : raw.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    private static void respondSse(HttpExchange exchange, String payload) throws IOException {
        byte[] bytes = (payload + "\n\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        // Connection: close + 显式 Content-Length：让 okhttp 明确读到流结束。
        // 用 sendResponseHeaders(200, 0)（分块流无长度）+ 默认 keep-alive 时，
        // okhttp 偶发在流结束处报 onFailure(HTTP 200)，表现为与本用例无关的间歇性失败
        // （CoreApiIntegrationTests 的假 LLM 服务踩过同一个坑）。
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
            out.flush();
        }
    }

    @ToolInfo(name = "read_article_draft", description = "读取当前文章草稿，无需参数。")
    static class ReadDraftTool implements Tool<ReadDraftParam> {
        @Override
        public String execute(ReadDraftParam param) {
            return "{\"saved\":false}";
        }
    }

    static class ReadDraftParam extends ToolParam {
        @Param(required = false, description = "读取原因")
        public String reason;
    }
}
