package ink.icoding.wechat.article.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.wechat.article.MySqlTestDatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 助手「模型选择」接口的集成测试（plan §10）。
 *
 * <p>验证的是三件在单测里无法覆盖的事：
 * ① 接口返回的字段集合——**绝不能**出现密钥（这是把模型列表开给 EDITOR 的前提）；
 * ② 选择真的落到 {@code assistant_preference}，换一个会话/重新请求仍然生效；
 * ③ 不存在的档案被拒，而不是静默回落到默认模型。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantModelSelectionIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /** 造一个「有供应商但没配密钥」的档案：它存在于列表里，但 available=false。 */
    private long createKeylessProfile(String token, String name) throws Exception {
        String providerBody = mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of(
                                "name", name + "-provider", "provider", "OPENAI_COMPATIBLE",
                                "baseUrl", "https://example.invalid/v1"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long providerId = Long.parseLong(ID.matcher(providerBody).results().findFirst().orElseThrow().group(1));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("providerId", providerId);
        request.put("modelName", "test-model");
        String body = mockMvc.perform(post("/api/llm-profiles")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(ID.matcher(body).results().findFirst().orElseThrow().group(1));
    }

    @Test
    void modelsEndpointReturnsOptionsWithoutAnySecret() throws Exception {
        String token = adminToken();

        String body = mockMvc.perform(get("/api/assistant/models").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        // 字段白名单：只允许选择所需的最小集合
        assertThat(body).doesNotContain("apiKey").doesNotContain("baseUrl")
                .doesNotContain("Encrypted").doesNotContain("secret");

        JsonNode data = MAPPER.readTree(body).get("data");
        assertThat(data.has("items")).isTrue();
        assertThat(data.has("selectedId")).isTrue();
        assertThat(data.has("currentId")).isTrue();
        for (JsonNode item : data.get("items")) {
            assertThat(item.fieldNames()).toIterable().containsExactlyInAnyOrder(
                    "id", "name", "modelName", "providerName", "available", "isDefault", "selected");
        }
    }

    @Test
    void selectedProfileIsPersistedAndEchoedBack() throws Exception {
        String token = adminToken();
        long profileId = createKeylessProfile(token, "选择测试档案");

        mockMvc.perform(put("/api/assistant/model")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":" + profileId + "}"))
                .andExpect(status().isOk())
                // 选中即回显在 selectedId；档案没有密钥，所以 currentId（真会用哪个）应为空
                .andExpect(jsonPath("$.data.selectedId").value(profileId))
                .andExpect(jsonPath("$.data.currentId").doesNotExist());

        // 重新拉一次列表：preference 已落库，选中项必须还是它（不是"只在响应里回显"）
        String body = mockMvc.perform(get("/api/assistant/models").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = MAPPER.readTree(body).get("data");
        assertThat(data.get("selectedId").asLong())
                .as("选择必须落库，而不是只在响应里回显")
                .isEqualTo(profileId);
        boolean foundSelected = false;
        for (JsonNode item : data.get("items")) {
            if (item.get("id").asLong() == profileId) {
                assertThat(item.get("selected").asBoolean()).isTrue();
                // 没有密钥 ⇒ 运行时不可用，面板必须能看出来
                assertThat(item.get("available").asBoolean()).isFalse();
                foundSelected = true;
            }
        }
        assertThat(foundSelected).isTrue();
    }

    @Test
    void clearingSelectionFallsBackToAutomaticChoice() throws Exception {
        String token = adminToken();
        long profileId = createKeylessProfile(token, "清除测试档案");
        mockMvc.perform(put("/api/assistant/model")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":" + profileId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/assistant/model")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedId").doesNotExist());
    }

    /** 选了不存在的档案：必须报错，不能悄悄用默认模型跑完。 */
    @Test
    void selectingMissingProfileIsRejected() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/assistant/model")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":999999}"))
                .andExpect(status().is4xxClientError());
    }
}