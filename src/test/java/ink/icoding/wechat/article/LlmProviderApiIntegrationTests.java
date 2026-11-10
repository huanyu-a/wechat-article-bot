package ink.icoding.wechat.article;

import ink.icoding.wechat.article.common.CryptoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 供应商 API（{@code /api/llm-providers}）集成测试（llm-provider-refactor §5.1 / §7.5 / §7.6）。
 *
 * <p>为什么这些用例必须走 HTTP 而不是只测 service：本接口有四条只有端到端才看得见的约定——
 * ①密钥**不能出现在任何响应里**（明文与密文都不行，都是 500/日志泄露的来源）；
 * ②{@code baseUrl} 必须归一化后再落库（前端会直接存 {@code /v1/chat/completions} 这种完整 URL）；
 * ③删除护栏的**错误消息要带剩余模型数**（用户据此知道该处理几条，否则只看到一个「删除失败」）；
 * ④全 ADMIN + 未认证 401（供应商是密钥载体，VIEWER 连读都不该有）。
 *
 * <p>用真实 {@link CryptoService} 校验落库密文：这是唯一能证明「明文没有以任何形式进库」的办法
 * （只用 mock 的话，把明文直接写进列里也能通过）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LlmProviderApiIntegrationTests {

    private static final String TEST_PREFIX = "__provider_api_test__";
    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final String LONG_KEY = "sk-provider-api-4321";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CryptoService cryptoService;

    @AfterEach
    void removeTestRows() {
        jdbcTemplate.update("DELETE FROM LLM_PROFILE WHERE NAME LIKE ?", TEST_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM LLM_PROVIDER WHERE NAME LIKE ?", TEST_PREFIX + "%");
        // 复查项 m-9：本类会经 {@code /api/system-users} 造 VIEWER 账号（token 随之落进 AUTH_TOKEN）。
        // 不清掉的话，同一上下文里后续用例看到的 SYS_USER 会逐次累积，而「账号数」类断言
        // 会在**别的测试类**里假红——症状与本次改动毫无关系，极难定位。
        jdbcTemplate.update("DELETE FROM SYS_USER WHERE USERNAME LIKE ?", TEST_PREFIX + "%");
    }

    private String adminToken() throws Exception {
        for (String password : new String[]{"Admin@123", "Admin@456"}) {
            var result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                Matcher matcher = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"")
                        .matcher(result.getResponse().getContentAsString());
                if (matcher.find()) return "Bearer " + matcher.group(1);
            }
        }
        throw new AssertionError("无法以 admin 身份登录");
    }

    private String viewerToken(String admin) throws Exception {
        String username = TEST_PREFIX + "-viewer";
        mockMvc.perform(post("/api/system-users")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"displayName\":\"" + username
                                + "\",\"password\":\"Passw0rd!\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        if (!matcher.find()) throw new AssertionError("登录响应中缺少 token");
        return "Bearer " + matcher.group(1);
    }

    private String createBody(String name, String provider, String baseUrl, String apiKey, boolean enabled) {
        return "{\"name\":\"" + name + "\",\"provider\":\"" + provider + "\",\"baseUrl\":\"" + baseUrl
                + "\",\"apiKey\":\"" + apiKey + "\",\"enabled\":" + enabled + "}";
    }

    private Long createProvider(String admin, String name, String provider, String baseUrl, String apiKey)
            throws Exception {
        String body = mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, provider, baseUrl, apiKey, true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = ID.matcher(body);
        if (!matcher.find()) throw new AssertionError("创建供应商的响应里缺少 id：" + body);
        return Long.valueOf(matcher.group(1));
    }

    /**
     * 增删改查全链路：归一化、掩码、密文落库、删除后即不可读。
     *
     * <p>{@code baseUrl} 故意带 {@code /v1/} 后缀：设置页与前端习惯填完整端点，
     * 落库必须是基址，否则运行时会拼出 {@code .../v1/chat/completions/v1/chat/completions}。
     */
    @Test
    void providerCrudRoundTripNormalizesBaseUrlAndMasksTheKey() throws Exception {
        String admin = adminToken();
        String name = TEST_PREFIX + "-crud";

        String created = mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, "OPENAI_COMPATIBLE", "https://api.provider-crud.test/v1/chat/completions",
                                LONG_KEY, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.baseUrl").value("https://api.provider-crud.test"))
                .andExpect(jsonPath("$.data.hasApiKey").value(true))
                .andExpect(jsonPath("$.data.apiKeyMasked").value("••••••••4321"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.modelCount").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain(LONG_KEY);
        Long id = Long.valueOf(ID.matcher(created).results().findFirst().orElseThrow().group(1));

        String ciphertext = jdbcTemplate.queryForObject(
                "SELECT API_KEY_ENCRYPTED FROM LLM_PROVIDER WHERE ID = " + id, String.class);
        assertThat(ciphertext).as("库里只能有密文").isNotBlank().doesNotContain(LONG_KEY);
        assertThat(cryptoService.decrypt(ciphertext)).as("密文必须能被真实密钥解回明文").isEqualTo(LONG_KEY);

        String listed = mockMvc.perform(get("/api/llm-providers").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listed).contains(name).doesNotContain(LONG_KEY).doesNotContain(ciphertext);
        mockMvc.perform(get("/api/llm-providers/" + id).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKeyMasked").value("••••••••4321"));

        // 改名 + 停用：不带 apiKey 时应保留原密钥（清除必须显式 clearApiKey）
        mockMvc.perform(put("/api/llm-providers/" + id)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name + "-renamed", "OPENAI_COMPATIBLE", "https://api.provider-crud.test",
                                "", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name + "-renamed"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.hasApiKey").value(true));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT API_KEY_ENCRYPTED FROM LLM_PROVIDER WHERE ID = " + id, String.class)).isEqualTo(ciphertext);

        mockMvc.perform(delete("/api/llm-providers/" + id).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/llm-providers/" + id).header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    /** 三条写入规则：重名、协议白名单、启用必须有 key。 */
    @Test
    void duplicateNameUnsupportedProtocolAndMissingKeyAreRejected() throws Exception {
        String admin = adminToken();
        String name = TEST_PREFIX + "-rules";
        createProvider(admin, name, "OPENAI_COMPATIBLE", "https://api.provider-rules.test", LONG_KEY);

        mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, "OPENAI_COMPATIBLE", "https://api.provider-rules.test", LONG_KEY, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("供应商名称已存在")));

        mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name + "-bad", "GEMINI", "https://api.provider-rules.test", LONG_KEY, true)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name + "-keyless", "OPENAI_COMPATIBLE", "https://api.provider-rules.test", "", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("API Key")));

        mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name + "-noproto", "OPENAI_COMPATIBLE", "api.provider-rules.test", LONG_KEY, true)))
                .andExpect(status().isBadRequest());
    }

    /**
     * 删除护栏：其下还有模型档案时拒绝，且消息里带上剩余数量。
     *
     * <p>为什么必须带数量：否则用户只知道「删不掉」，不知道是哪些档案挡着。
     * 这里也顺带证明「档案能挂到供应商上」这条写入链路（{@code providerId} 由接口落库）。
     */
    @Test
    void deleteIsRefusedWhileModelsAreAttachedAndTheMessageCarriesTheCount() throws Exception {
        String admin = adminToken();
        Long providerId = createProvider(admin, TEST_PREFIX + "-guard", "OPENAI_COMPATIBLE",
                "https://api.provider-guard.test", LONG_KEY);

        String profile = mockMvc.perform(post("/api/llm-profiles")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + TEST_PREFIX + "-model\",\"providerId\":" + providerId
                                + ",\"modelName\":\"guard-model\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerId").value(providerId))
                .andReturn().getResponse().getContentAsString();
        Long profileId = Long.valueOf(ID.matcher(profile).results().findFirst().orElseThrow().group(1));

        mockMvc.perform(get("/api/llm-providers/" + providerId).header("Authorization", admin))
                .andExpect(jsonPath("$.data.modelCount").value(1));
        mockMvc.perform(delete("/api/llm-providers/" + providerId).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1 个模型档案")));

        mockMvc.perform(delete("/api/llm-profiles/" + profileId).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/llm-providers/" + providerId).header("Authorization", admin))
                .andExpect(status().isOk());
    }

    /**
     * 模型发现端点（契约 §2.2）：三条「不打网络就能判死」的边界，全部走 HTTP 打真实链路。
     *
     * <p>真实的出网行为（超时 / 响应体上限 / 不跟随重定向 / 两种 JSON 形态）由
     * {@code ModelDiscoveryClientTest} 用本地 {@code HttpServer} 钉住；这里补的是只有经过
     * 过滤链才证明得了的三件事：**限流键、ADMIN 鉴权、无 key 时立刻 400 且不发请求**。
     *
     * <p>故意**不**在这里打真实上游：任何出网断言都会变成环境依赖，而 CI 上没有那个端点。
     */
    @Test
    void modelDiscoveryRejectsKeylessProvidersWithoutTouchingTheNetwork() throws Exception {
        String admin = adminToken();
        // 无 key + 停用：既不该出网，也不该因为停用而被拒（用户常在启用前先验证端点）
        String keyless = mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + TEST_PREFIX + "-keyless\",\"provider\":\"OPENAI_COMPATIBLE\","
                                + "\"baseUrl\":\"https://api.provider-discovery.test\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long keylessId = Long.valueOf(ID.matcher(keyless).results().findFirst().orElseThrow().group(1));

        mockMvc.perform(get("/api/llm-providers/" + keylessId + "/models").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未配置 API Key")));

        mockMvc.perform(get("/api/llm-providers/999999/models").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("供应商不存在")));
    }

    /** 模型发现同样是 ADMIN 专属：它是「用真密钥出网」的能力，VIEWER 不该有。 */
    @Test
    void modelDiscoveryRequiresAdminAndAuthentication() throws Exception {
        String admin = adminToken();
        String viewer = viewerToken(admin);
        Long providerId = createProvider(admin, TEST_PREFIX + "-disc-auth", "OPENAI_COMPATIBLE",
                "https://api.provider-discovery.test", LONG_KEY);

        mockMvc.perform(get("/api/llm-providers/" + providerId + "/models").header("Authorization", viewer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/llm-providers/" + providerId + "/models"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 供应商视图**不再有** {@code purpose} 键（契约 §4.3 修订：用途是模型的属性，不是供应商的）。
     *
     * <p>为什么要把「键不存在」当断言写下来：用户否决供应商级用途之后，如果哪天有人（或某次
     * 「顺手加个字段」）把它加回来，前端那张「文本 / 图片」徽标就会重新出现，而这类回归不会报错、
     * 只会让界面多出一个语义错误的标签。字段的**缺席**同样是契约的一部分。
     */
    @Test
    void providerViewHasNoPurposeFieldAnymore() throws Exception {
        String admin = adminToken();
        String created = mockMvc.perform(post("/api/llm-providers")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(TEST_PREFIX + "-no-purpose", "OPENAI_COMPATIBLE",
                                "https://api.provider-purpose.test", LONG_KEY, true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(created).doesNotContain("\"purpose\"");
        mockMvc.perform(get("/api/llm-providers").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].purpose").doesNotExist());
    }

    /** 供应商是密钥载体：VIEWER 连读都被拒，未认证一律 401。 */
    @Test
    void providerEndpointsRequireAdminAndAuthentication() throws Exception {
        String admin = adminToken();
        String viewer = viewerToken(admin);
        Long providerId = createProvider(admin, TEST_PREFIX + "-auth", "OPENAI_COMPATIBLE",
                "https://api.provider-auth.test", LONG_KEY);

        mockMvc.perform(get("/api/llm-providers").header("Authorization", viewer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/llm-providers/" + providerId).header("Authorization", viewer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/llm-providers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/llm-providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(TEST_PREFIX + "-anon", "OPENAI_COMPATIBLE",
                                "https://api.provider-auth.test", LONG_KEY, true)))
                .andExpect(status().isUnauthorized());
    }
}
