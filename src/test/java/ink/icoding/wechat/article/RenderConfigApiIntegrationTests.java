package ink.icoding.wechat.article;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 排版渲染服务设置 API 集成测试（skills-agent-plan 5.6 / 8.2）：
 * /api/settings/render 的令牌加密与掩码、TTL 归一与越界、clearToken、ADMIN 权限、/test 连通性探测。
 *
 * 背景（2026-09-10 自检）：前端清空 TTL 输入框会发出 0，而后端 @Min(60) 直接 400；
 * 前端已改为「空 → null（用默认值）」，这里用集成测试锁死两侧契约。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RenderConfigApiIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken() throws Exception {
        for (String password : new String[]{"Admin@123", "Admin@456"}) {
            var result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                Matcher matcher = TOKEN.matcher(result.getResponse().getContentAsString());
                if (matcher.find()) return "Bearer " + matcher.group(1);
            }
        }
        throw new AssertionError("无法以 admin 身份登录");
    }

    private String viewerToken(String admin) throws Exception {
        mockMvc.perform(post("/api/system-users")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"render_viewer","displayName":"render_viewer",
                                 "password":"Passw0rd!","role":"VIEWER"}
                                """))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"render_viewer\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = TOKEN.matcher(body);
        if (!matcher.find()) throw new AssertionError("登录响应中缺少 token");
        return "Bearer " + matcher.group(1);
    }

    private String putConfig(String admin, String body) throws Exception {
        return mockMvc.perform(put("/api/settings/render")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 每个用例前把单行配置重置到已知状态，避免同类内方法互相污染（顺序无关）。 */
    private void resetConfig(String admin) throws Exception {
        putConfig(admin, """
                {"baseUrl":"https://www.bx9y.com.cn","clearToken":true,"siteBaseUrl":"",
                 "syntaxCacheTtlSeconds":21600,"enabled":false}
                """);
    }

    @Test
    void defaultConfigHasNoTokenAndIsDisabled() throws Exception {
        String admin = adminToken();
        resetConfig(admin);
        mockMvc.perform(get("/api/settings/render").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("MARKFLOW"))
                .andExpect(jsonPath("$.data.hasToken").value(false))
                .andExpect(jsonPath("$.data.tokenMasked").value("未配置"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void tokenIsEncryptedStoredAndMasked() throws Exception {
        String admin = adminToken();
        resetConfig(admin);
        String body = putConfig(admin, """
                {"baseUrl":"https://render.example.test/","token":"render-token-abcd1234",
                 "siteBaseUrl":"https://site.example.test/","syntaxCacheTtlSeconds":7200,
                 "enabled":true}
                """);

        // 返回体只给掩码（后 4 位），不回传明文
        assertThat(body).contains("••••••••1234").doesNotContain("render-token-abcd1234");
        assertThat(body).contains("https://render.example.test").contains("https://site.example.test");

        String encrypted = jdbcTemplate.queryForObject(
                "SELECT TOKEN_ENCRYPTED FROM RENDER_CONFIG ORDER BY ID DESC LIMIT 1", String.class);
        assertThat(encrypted).isNotBlank().doesNotContain("render-token-abcd1234");
    }

    @Test
    void nullTtlFallsBackToDefaultAndOutOfRangeRejected() throws Exception {
        String admin = adminToken();
        resetConfig(admin);

        // 前端清空输入框 → 发 null → 后端用默认 21600
        putConfig(admin, """
                {"baseUrl":"https://render.example.test","token":"tok-1234","syntaxCacheTtlSeconds":null,
                 "enabled":true}
                """);
        Integer ttl = jdbcTemplate.queryForObject(
                "SELECT SYNTAX_CACHE_TTL_SECONDS FROM RENDER_CONFIG ORDER BY ID DESC LIMIT 1", Integer.class);
        assertThat(ttl).isEqualTo(21600);

        // 越界值拒绝
        mockMvc.perform(put("/api/settings/render")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"https://render.example.test","syntaxCacheTtlSeconds":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankTokenKeepsExistingAndClearTokenRemoves() throws Exception {
        String admin = adminToken();
        resetConfig(admin);
        putConfig(admin, """
                {"baseUrl":"https://render.example.test","token":"keep-me-9999","enabled":true}
                """);
        String stored = jdbcTemplate.queryForObject(
                "SELECT TOKEN_ENCRYPTED FROM RENDER_CONFIG ORDER BY ID DESC LIMIT 1", String.class);

        // 空 token = 不修改
        putConfig(admin, """
                {"baseUrl":"https://render.example.test","token":"","enabled":true}
                """);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT TOKEN_ENCRYPTED FROM RENDER_CONFIG ORDER BY ID DESC LIMIT 1", String.class))
                .isEqualTo(stored);

        // clearToken 显式清空
        putConfig(admin, """
                {"baseUrl":"https://render.example.test","clearToken":true,"enabled":false}
                """);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT TOKEN_ENCRYPTED FROM RENDER_CONFIG ORDER BY ID DESC LIMIT 1", String.class)).isNull();
    }

    @Test
    void enablingWithoutTokenRejected() throws Exception {
        String admin = adminToken();
        putConfig(admin, """
                {"baseUrl":"https://render.example.test","clearToken":true,"enabled":false}
                """);
        mockMvc.perform(put("/api/settings/render")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"https://render.example.test","enabled":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewerForbiddenAndUnauthenticatedRejected() throws Exception {
        String admin = adminToken();
        String viewer = viewerToken(admin);

        mockMvc.perform(get("/api/settings/render").header("Authorization", viewer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/settings/render")).andExpect(status().isUnauthorized());
    }

    /** /test 指向一个必然连不上的地址，应返回 ok:false 且带可读原因（不抛 500）。 */
    @Test
    void testConnectionReportsFailureGracefully() throws Exception {
        String admin = adminToken();
        resetConfig(admin);
        putConfig(admin, """
                {"baseUrl":"http://127.0.0.1:1","token":"dummy-token","enabled":true}
                """);

        mockMvc.perform(post("/api/settings/render/test").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.emptyString())));
    }
}
