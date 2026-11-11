package ink.icoding.wechat.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批量建档案接口（{@code POST /api/llm-profiles/batch}）的集成测试。
 *
 * <p>为什么必须走 HTTP + 真实库：这个接口的价值全在「部分成功」的账目上——
 * 其中一条失败时其余是否真的留下了、重复提交是否会重复建、「已存在」有没有被误报成失败。
 * 这些用 mock 一个 service 都验不出来。
 *
 * <p>另一个只有端到端才能验的点：**同一个模型名在两条批量请求之间不会重复建行**。
 * 用户会连点两下按钮，也会先探测一次再探测一次；接口必须幂等。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LlmProfileBatchApiIntegrationTests {
    private static final String PREFIX = "__batch_api_test__";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @Autowired
    private MockMvc mockMvc;

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = TOKEN.matcher(body);
        if (!matcher.find()) throw new AssertionError("登录失败：" + body);
        return "Bearer " + matcher.group(1);
    }

    private long createProvider(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of(
                                "name", name, "provider", "OPENAI_COMPATIBLE",
                                "baseUrl", "https://example.invalid/v1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(ID.matcher(body).results().findFirst().orElseThrow().group(1));
    }

    private JsonNode batch(String token, long providerId, List<String> names) throws Exception {
        String body = mockMvc.perform(post("/api/llm-profiles/batch")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of(
                                "providerId", providerId, "modelNames", names))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return MAPPER.readTree(body).get("data");
    }

    @Test
    void addsEveryRequestedModelInOneCall() throws Exception {
        String token = adminToken();
        long providerId = createProvider(token, PREFIX + "all");

        JsonNode data = batch(token, providerId, List.of(
                PREFIX + "-a", PREFIX + "-b", PREFIX + "-c"));

        assertThat(data.get("createdCount").asInt()).isEqualTo(3);
        assertThat(data.get("created")).hasSize(3);
        assertThat(data.get("skipped")).isEmpty();
        assertThat(data.get("failed")).isEmpty();
    }

    /**
     * 重复提交必须幂等：第二次全部落进 skipped，且**不会**再建行。
     *
     * <p>这条守的是用户真实行为——探测两次、点两下按钮。若第二次报错，用户会以为库里脏了。
     */
    @Test
    void resubmittingTheSameNamesIsIdempotent() throws Exception {
        String token = adminToken();
        long providerId = createProvider(token, PREFIX + "idem");
        List<String> names = List.of(PREFIX + "-x", PREFIX + "-y");

        batch(token, providerId, names);
        JsonNode second = batch(token, providerId, names);

        assertThat(second.get("createdCount").asInt()).isZero();
        assertThat(second.get("skipped")).hasSize(2);
        assertThat(second.get("failed")).as("已存在不是失败").isEmpty();
    }

    /**
     * 部分失败不连坐：撞名的那条进 failed，其余照常建。
     *
     * <p>「一条坏的全批回滚」会让用户回到一条条点，正是这个功能要消灭的痛点。
     */
    @Test
    void oneBadNameDoesNotAbortTheRest() throws Exception {
        String token = adminToken();
        long providerId = createProvider(token, PREFIX + "partial");
        String taken = PREFIX + "-taken";
        // 占掉一个**全局唯一**的档案名，但用**另一个模型名**——这样它不会落进
        // 「同供应商同模型 ⇒ 已存在」的幂等分支，才会真的走到名称唯一性校验上。
        mockMvc.perform(post("/api/llm-profiles")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of(
                                "name", taken, "providerId", providerId,
                                "modelName", taken + "-occupied"))))
                .andExpect(status().isOk());

        JsonNode data = batch(token, providerId, List.of(taken, PREFIX + "-ok"));

        assertThat(data.get("createdCount").asInt()).isEqualTo(1);
        assertThat(data.get("created").get(0).asText()).isEqualTo(PREFIX + "-ok");
        assertThat(data.get("failed")).as("撞名必须逐条报出来").hasSize(1);
        assertThat(data.get("failed").get(0).get("modelName").asText()).isEqualTo(taken);
        assertThat(data.get("failed").get(0).get("message").asText()).isNotBlank();
    }

    /** 空白与重复项在服务端归一化：前端全选时很容易把同一个名字带两遍。 */
    @Test
    void blankAndDuplicateNamesAreNormalized() throws Exception {
        String token = adminToken();
        long providerId = createProvider(token, PREFIX + "norm");

        JsonNode data = batch(token, providerId, List.of(
                PREFIX + "-dup", "  ", PREFIX + "-dup", PREFIX + "-other"));

        assertThat(data.get("createdCount").asInt()).isEqualTo(2);
    }

    /** 空选择必须是明确的业务错误，而不是悄悄成功（否则界面会显示「新增 0」让人以为点错了）。 */
    @Test
    void emptySelectionIsRejected() throws Exception {
        String token = adminToken();
        long providerId = createProvider(token, PREFIX + "empty");

        mockMvc.perform(post("/api/llm-profiles/batch")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of(
                                "providerId", providerId, "modelNames", List.of()))))
                .andExpect(status().is4xxClientError());
    }
}
