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
    private final List<String> messagesMissingContent = new ArrayList<>();
    /** 网关校验开关：默认两条规则都查；版本锚定用例只考察 content 那一条。 */
    private volatile boolean validateArguments = true;

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

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int round = requestCount.incrementAndGet();
        requestBodies.add(body);

        if (round == 1) {
            // 真实网关行为：首个 delta 带 arguments:""，无参工具调用之后不再推送任何 arguments 分片
            respondSse(exchange, String.join("\n\n",
                    "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                            + "\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}",
                    "data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
                            + "\"delta\":{\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"index\":0,"
                            + "\"function\":{\"name\":\"read_article_draft\",\"arguments\":\"\"}}]}}]}",
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

        String missingContent = findMessageMissingContent(body);
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
