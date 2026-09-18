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
 * 第②③期新增 API 的集成测试（skills-agent-plan 8.2）：
 * /api/agents 与 /api/llm-profiles CRUD + 角色矩阵 403、任务新字段透传、默认档案兼容映射。
 *
 * 注意：本类会写入全局状态（默认模型档案、任务记录），类结束后重建上下文与测试库，
 * 避免污染共用同一上下文的其它集成测试类（{@code MySqlTestDatabaseInitializer} 在新上下文时清库）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AgentApiIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken() throws Exception {
        // CoreApiIntegrationTests 末尾会把 admin 密码改成 Admin@456；两种密码都试一次
        StringBuilder diagnostics = new StringBuilder();
        for (String password : new String[]{"Admin@123", "Admin@456"}) {
            var result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"))
                    .andReturn();
            diagnostics.append('[').append(password).append(" -> ").append(result.getResponse().getStatus())
                    .append(' ').append(result.getResponse().getContentAsString()).append(']');
            if (result.getResponse().getStatus() == 200) {
                Matcher matcher = TOKEN.matcher(result.getResponse().getContentAsString());
                if (matcher.find()) return "Bearer " + matcher.group(1);
            }
        }
        java.util.List<String> rows = jdbcTemplate.queryForList(
                "SELECT CONCAT(ID, '|', USERNAME, '|', STATUS, '|', LEFT(PASSWORD_HASH, 7), '|', LENGTH(PASSWORD_HASH)) FROM SYS_USER",
                String.class);
        throw new AssertionError("无法以 admin 身份登录（用户行=" + rows + "）：" + diagnostics);
    }

    private String createUserAndLogin(String token, String username, String role) throws Exception {
        mockMvc.perform(post("/api/system-users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"%s","password":"Passw0rd!","role":"%s"}
                                """.formatted(username, username, role)))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = TOKEN.matcher(body);
        if (!matcher.find()) throw new AssertionError("登录响应中缺少 token");
        return "Bearer " + matcher.group(1);
    }

    @Test
    void agentAndProfileApisHonourRoleMatrix() throws Exception {
        String admin = adminToken();
        String viewer = createUserAndLogin(admin, "viewer_role_test", "VIEWER");

        // 读：全员可读
        mockMvc.perform(get("/api/agents").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/agents/tool-groups").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8));
        mockMvc.perform(get("/api/tasks/execution-modes").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        // 写：VIEWER 被拒（403），ADMIN 放行
        mockMvc.perform(post("/api/agents")
                        .header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"viewer_agent","name":"越权智能体","stage":"WRITING",
                                 "persona":"测试","toolKeys":["MEDIA"],"skillIds":[],"enabled":true}
                                """))
                .andExpect(status().isForbidden());

        // 模型档案：全 ADMIN（VIEWER 连读都被拒）
        mockMvc.perform(get("/api/llm-profiles").header("Authorization", viewer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/llm-profiles").header("Authorization", admin))
                .andExpect(status().isOk());

        // 未认证一律 401
        mockMvc.perform(get("/api/agents")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/llm-profiles")).andExpect(status().isUnauthorized());
    }

    @Test
    void builtinAgentsAreSeededIdempotently() throws Exception {
        String admin = adminToken();
        mockMvc.perform(get("/api/agents").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='builtin_editor')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='builtin_scheduled_creator')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='builtin_researcher')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='builtin_writer')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='builtin_illustrator')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='builtin_reviewer')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='builtin_chief')]").exists());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM AGENT_DEFINITION WHERE IS_BUILTIN = 1", Long.class);
        assertThat(count).isEqualTo(7L);
    }

    @Test
    void agentCrudAndToolGroupValidation() throws Exception {
        String admin = adminToken();

        String created = mockMvc.perform(post("/api/agents")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"crud_test_agent","name":"CRUD 测试智能体","stage":"WRITING",
                                 "persona":"你是测试撰稿人。","toolKeys":["DRAFT_READ","DRAFT_WRITE"],
                                 "skillIds":[],"temperature":0.5,"maxTokens":2048,"enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("crud_test_agent"))
                .andExpect(jsonPath("$.data.isBuiltin").value(false))
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll("(?s).*\"id\":(\\d+).*", "$1"));

        // tool_keys 以 JSON 数组字符串存储
        String toolKeys = jdbcTemplate.queryForObject(
                "SELECT TOOL_KEYS FROM AGENT_DEFINITION WHERE ID = " + id, String.class);
        assertThat(toolKeys).contains("DRAFT_READ").contains("DRAFT_WRITE");

        // 更新
        mockMvc.perform(put("/api/agents/" + id)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"crud_test_agent","name":"CRUD 测试智能体（改）","stage":"WRITING",
                                 "persona":"改后的人设。","toolKeys":["DRAFT_READ"],"skillIds":[],"enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("CRUD 测试智能体（改）"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 未知工具组拒绝
        mockMvc.perform(post("/api/agents")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"bad_tools_agent","name":"非法工具组","stage":"WRITING",
                                 "persona":"x","toolKeys":["NOT_A_GROUP"],"enabled":true}
                                """))
                .andExpect(status().isBadRequest());

        // 缺少 stage 必须是 400（曾因 Set.of().contains(null) 抛 NPE 变成 500）
        mockMvc.perform(post("/api/agents")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"no_stage_agent","name":"缺阶段","persona":"x",
                                 "toolKeys":["MEDIA"],"enabled":true}
                                """))
                .andExpect(status().isBadRequest());

        // 空工具组拒绝（无工具 = 空转智能体）
        mockMvc.perform(post("/api/agents")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"no_tools_agent","name":"空工具组","stage":"WRITING",
                                 "persona":"x","toolKeys":[],"enabled":true}
                                """))
                .andExpect(status().isBadRequest());

        // 内置 agent 不可删
        Long builtinId = jdbcTemplate.queryForObject(
                "SELECT ID FROM AGENT_DEFINITION WHERE BUILTIN_KEY = 'builtin_editor'", Long.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/agents/" + builtinId).header("Authorization", admin))
                .andExpect(status().isBadRequest());

        // 克隆
        mockMvc.perform(post("/api/agents/" + builtinId + "/duplicate").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isBuiltin").value(false))
                .andExpect(jsonPath("$.data.code").value("builtin_editor_copy"));
    }

    @Test
    void llmProfileCrudAndSettingsLlmCompatMapping() throws Exception {
        String admin = adminToken();

        // 存量 API 写入 → 默认档案同步
        mockMvc.perform(put("/api/settings/llm")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"OPENAI_COMPATIBLE","baseUrl":"https://llm.compat.test/v1/",
                                 "modelName":"compat-model","apiKey":"compat-secret-key","enabled":true,
                                 "temperature":0.3,"maxTokens":1024}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/llm-profiles").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.isDefault==true)].modelName").value("compat-model"))
                .andExpect(jsonPath("$.data[?(@.isDefault==true)].baseUrl").value("https://llm.compat.test"));

        // 存量 API 读回默认档案值（签名与语义不变）
        mockMvc.perform(get("/api/settings/llm").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelName").value("compat-model"))
                .andExpect(jsonPath("$.data.hasApiKey").value(true));

        // 新建档案并设为默认
        String created = mockMvc.perform(post("/api/llm-profiles")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"廉价调研档案","provider":"OPENAI_RESPONSES",
                                 "baseUrl":"https://cheap.test","modelName":"cheap-model",
                                 "apiKey":"cheap-key","enabled":true,"temperature":0.1,"maxTokens":512}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andExpect(jsonPath("$.data.apiKeyMasked").value("••••••••••••"))
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll("(?s).*\"id\":(\\d+).*", "$1"));

        // 掩码不得回传完整 key
        String listed = mockMvc.perform(get("/api/llm-profiles").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        assertThat(listed).doesNotContain("cheap-key\"");
        assertThat(listed).doesNotContain("\"apiKey\"");

        mockMvc.perform(post("/api/llm-profiles/" + id + "/set-default").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));
        Long defaultCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM LLM_PROFILE WHERE IS_DEFAULT = 1", Long.class);
        assertThat(defaultCount).isEqualTo(1L);

        // 默认档案不可删除
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/llm-profiles/" + id).header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    /**
     * 存量 {@code PUT /api/settings/llm} 不得改写用户自建的普通档案（HTTP 端到端）。
     *
     * <p>缺陷现场：{@code syncDefaultFromConfig} 用 {@code defaultProfile()}（= {@code findDefault() ?? findFirst()}）
     * 选写透目标。当表里有档案、但**一条都不是默认**时，它会落到第一条用户档案上，
     * 覆盖其 provider/baseUrl/modelName/apiKey 且不打 is_default 标记。
     *
     * <p>为什么这条必须走 HTTP 而不是只留单测：真正的触发前提是「库里存在一条非默认档案」，
     * 这是**数据库状态**决定的，只有集成测试能造出来。单测只能证明逻辑，证明不了
     * 「这个状态在真实表里会走到那条分支」。
     *
     * <p>本用例自带清理：清空 LLM_PROFILE 后重建一条非默认档案作为靶子，
     * 跑完恢复成「一条默认档案」，避免污染共享同一上下文的其它用例。
     */
    @Test
    void legacySettingsWriteThroughDoesNotHijackAUserProfile() throws Exception {
        String admin = adminToken();

        // 造出触发前提：库里只有一条**非默认**档案（模拟「用户自建了档案但从没设过默认」）。
        // 不动 AGENT_DEFINITION：内置智能体在全新测试库里 LLM_PROFILE_ID 本来就是 NULL
        // （AgentSeeder 只在档案已存在时才写绑定），且两表之间没有外键约束。
        jdbcTemplate.update("DELETE FROM LLM_PROFILE");
        jdbcTemplate.update("""
                INSERT INTO LLM_PROFILE (NAME, PROVIDER, BASE_URL, MODEL_NAME, API_KEY_ENCRYPTED,
                                         TEMPERATURE, MAX_TOKENS, ENABLED, IS_DEFAULT, IS_FALLBACK,
                                         CREATED_AT, UPDATED_AT)
                VALUES ('用户的档案', 'OPENAI_COMPATIBLE', 'https://user.example.com', '用户原来的模型',
                        'user-encrypted-key', 0.7, 4096, 1, 0, 0, NOW(), NOW())
                """);

        try {
            mockMvc.perform(put("/api/settings/llm")
                            .header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"provider":"OPENAI_COMPATIBLE","baseUrl":"https://from.settings.test",
                                     "modelName":"settings-model","apiKey":"settings-key","enabled":true,
                                     "temperature":0.3,"maxTokens":1024}
                                    """))
                    .andExpect(status().isOk());

            // 用户自建的档案必须原样不动
            String hijacked = jdbcTemplate.queryForObject(
                    "SELECT MODEL_NAME FROM LLM_PROFILE WHERE NAME = '用户的档案'", String.class);
            assertThat(hijacked)
                    .as("存量设置页把用户自建的普通档案覆盖了——这正是本用例要钉住的缺陷")
                    .isEqualTo("用户原来的模型");

            // 写透必须落在一个**带 is_default 标记**的档案上（新建的那条）
            Long defaultCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM LLM_PROFILE WHERE IS_DEFAULT = 1", Long.class);
            assertThat(defaultCount).as("写透后必须恰有一条默认档案").isEqualTo(1L);
            String defaultModel = jdbcTemplate.queryForObject(
                    "SELECT MODEL_NAME FROM LLM_PROFILE WHERE IS_DEFAULT = 1", String.class);
            assertThat(defaultModel).isEqualTo("settings-model");

            // 设置页回显的仍是新值（读路径同样回落，故「回显对」不能证明「写对了对象」）
            mockMvc.perform(get("/api/settings/llm").header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.modelName").value("settings-model"));
        } finally {
            // 恢复成**本类启动时的原始状态**（测试库重建后 LLM_PROFILE 是空的：
            // LlmProfileSeeder 只在存在「有 key 的默认档案」时才补标准档案）。
            // 不要在这里造一条「默认配置」——那会与原始状态不同，同样会污染后续用例。
            jdbcTemplate.update("DELETE FROM LLM_PROFILE");
        }
    }

    /**
     * 任务/账号的技能绑定清空必须真正落库（与文章级同一类风险）：
     * 若 UPDATE 语句跳过 null 字段，「取消勾选全部技能后保存」会被静默忽略。
     */
    @Test
    void clearingTaskAndAccountSkillIdsPersists() throws Exception {
        String admin = adminToken();
        Long skillId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'default_layout'", Long.class);

        // 任务：绑定 → 清空
        String task = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"解绑回归任务","cronExpression":"0 0 5 * * ?","timezone":"Asia/Shanghai",
                                 "aiPrompt":"写一篇","outputMode":"LOCAL_DRAFT","enabled":true,
                                 "skillIds":[%d]}
                                """.formatted(skillId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SCHEDULE_TASK WHERE NAME = '解绑回归任务' ORDER BY ID DESC LIMIT 1",
                Long.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM SCHEDULE_TASK WHERE ID = " + taskId, String.class))
                .isEqualTo(String.valueOf(skillId));

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"解绑回归任务","cronExpression":"0 0 5 * * ?","timezone":"Asia/Shanghai",
                                 "aiPrompt":"写一篇","outputMode":"LOCAL_DRAFT","enabled":true,"skillIds":[]}
                                """))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM SCHEDULE_TASK WHERE ID = " + taskId, String.class)).isNull();

        // 账号：绑定 → 清空
        String account = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"解绑回归公众号","appId":"wx_unbind_test","appSecret":"secret-123456",
                                 "accountType":"SERVICE","defaultStyle":"清爽","skillIds":[%d]}
                                """.formatted(skillId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long accountId = jdbcTemplate.queryForObject(
                "SELECT ID FROM WECHAT_ACCOUNT WHERE APP_ID = 'wx_unbind_test' LIMIT 1",
                Long.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM WECHAT_ACCOUNT WHERE ID = " + accountId, String.class))
                .isEqualTo(String.valueOf(skillId));

        mockMvc.perform(put("/api/accounts/" + accountId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"解绑回归公众号","appId":"wx_unbind_test","accountType":"SERVICE",
                                 "defaultStyle":"清爽","skillIds":[]}
                                """))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM WECHAT_ACCOUNT WHERE ID = " + accountId, String.class)).isNull();
    }

    /** 账号类型/状态白名单（status 参与「公众号已停用」判断，任意值会静默停用账号）。 */
    @Test
    void accountTypeAndStatusValidated() throws Exception {
        String admin = adminToken();

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"非法类型","appId":"wx_type_bad","appSecret":"secret-123456",
                                 "accountType":"PERSONAL"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"非法状态","appId":"wx_status_bad","appSecret":"secret-123456",
                                 "accountType":"SERVICE","status":"ARCHIVED"}
                                """))
                .andExpect(status().isBadRequest());

        // 合法值写入成功
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"合法订阅号","appId":"wx_status_ok","appSecret":"secret-123456",
                                 "accountType":"SUBSCRIPTION","status":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    /**
     * 智能体默认技能绑定的往返与清空（前端 AgentsView 的 SkillPicker）：
     * agent.skillIds 同样是「逗号分隔字符串」持久化，需保证写读一致且能清空。
     *
     * <p>同时守住响应契约：tool_keys / skill_ids 在库里是字符串编码（JSON 数组 / 逗号分隔），
     * 但读接口必须还原成数组，否则 GET 的产物无法回填 PUT（资源不可往返）。
     */
    @Test
    void agentSkillIdsRoundTripAndClear() throws Exception {
        String admin = adminToken();
        Long skillId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'default_layout'", Long.class);

        mockMvc.perform(post("/api/agents")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"skill_bind_agent","name":"技能绑定智能体","stage":"WRITING",
                                 "persona":"人设","toolKeys":["DRAFT_READ","DRAFT_WRITE"],"skillIds":[%d],"enabled":true}
                                """.formatted(skillId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillIds[0]").value(skillId))
                .andExpect(jsonPath("$.data.toolKeys[0]").value("DRAFT_READ"))
                .andExpect(jsonPath("$.data.toolKeys[1]").value("DRAFT_WRITE"));

        Long agentId = jdbcTemplate.queryForObject(
                "SELECT ID FROM AGENT_DEFINITION WHERE CODE = 'skill_bind_agent'", Long.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM AGENT_DEFINITION WHERE ID = " + agentId, String.class))
                .isEqualTo(String.valueOf(skillId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT TOOL_KEYS FROM AGENT_DEFINITION WHERE ID = " + agentId, String.class))
                .isEqualTo("[\"DRAFT_READ\",\"DRAFT_WRITE\"]");

        // 读接口形状与写接口一致：GET 出来的数组能原样回填 PUT
        mockMvc.perform(get("/api/agents/" + agentId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillIds[0]").value(skillId))
                .andExpect(jsonPath("$.data.toolKeys[0]").value("DRAFT_READ"));

        // 用 GET 的产物形状回写（只改 enabled），必须仍是 200
        mockMvc.perform(put("/api/agents/" + agentId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"skill_bind_agent","name":"技能绑定智能体","stage":"WRITING",
                                 "persona":"人设","toolKeys":["DRAFT_READ"],"skillIds":[%d],"enabled":false}
                                """.formatted(skillId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillIds[0]").value(skillId));

        mockMvc.perform(put("/api/agents/" + agentId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"skill_bind_agent","name":"技能绑定智能体","stage":"WRITING",
                                 "persona":"人设","toolKeys":["DRAFT_READ"],"skillIds":[],"enabled":true}
                                """))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM AGENT_DEFINITION WHERE ID = " + agentId, String.class)).isNull();
    }

    @Test
    void taskExecutionModeFieldsRoundTrip() throws Exception {
        String admin = adminToken();

        String created = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"多 Agent 编排验收","cronExpression":"0 0 10 * * ?",
                                 "timezone":"Asia/Shanghai","aiPrompt":"写一篇 AI 行业观察。",
                                 "outputMode":"LOCAL_DRAFT","enabled":true,
                                 "executionMode":"PIPELINE",
                                 "stageAgents":{"research":0,"writing":1,"illustration":0,"review":1},
                                 "maxRevisionRounds":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionMode").value("PIPELINE"))
                .andExpect(jsonPath("$.data.maxRevisionRounds").value(3))
                .andExpect(jsonPath("$.data.stageAgents.research").value(0))
                .andExpect(jsonPath("$.data.skillIds").isArray())
                .andReturn().getResponse().getContentAsString();
        Long taskId = Long.valueOf(created.replaceAll("(?s).*\"id\":(\\d+).*", "$1"));

        // 读回：stage_agents 以结构化对象出参（库里是 JSON 字符串）
        String fetched = mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionMode").value("PIPELINE"))
                .andExpect(jsonPath("$.data.maxRevisionRounds").value(3))
                .andExpect(jsonPath("$.data.stageAgents.research").value(0))
                .andExpect(jsonPath("$.data.stageAgents.review").value(1))
                .andReturn().getResponse().getContentAsString();

        // 读接口形状必须与写接口一致：GET 的产物原样回填 PUT（修复前 skill_ids 是逗号串、
        // stage_agents 是 JSON 字符串，PUT 直接报 JSON parse error，资源不可往返）
        String roundTrip = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(fetched).path("data").toString();
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roundTrip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stageAgents.writing").value(1));

        // 非法执行模式拒绝
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"非法模式","cronExpression":"0 0 11 * * ?","timezone":"Asia/Shanghai",
                                 "aiPrompt":"x","outputMode":"LOCAL_DRAFT","executionMode":"WRONG"}
                                """))
                .andExpect(status().isBadRequest());

        // 非法阶段键拒绝
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"非法阶段","cronExpression":"0 0 12 * * ?","timezone":"Asia/Shanghai",
                                 "aiPrompt":"x","outputMode":"LOCAL_DRAFT","executionMode":"PIPELINE",
                                 "stageAgents":{"unknown_stage":1}}
                                """))
                .andExpect(status().isBadRequest());

        // 存量兼容：不传新字段默认 SINGLE / 2
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"存量任务","cronExpression":"0 0 13 * * ?","timezone":"Asia/Shanghai",
                                 "aiPrompt":"x","outputMode":"LOCAL_DRAFT","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionMode").value("SINGLE"))
                .andExpect(jsonPath("$.data.maxRevisionRounds").value(2));
    }

    /**
     * 可空字段在 **HTTP 层**必须「键在场、值为 null」，而不是被序列化器省略。
     *
     * <p>为什么单靠 {@code AgentJsonContractTest} 不够：那个类用的是**进程内自建**的
     * {@code new ObjectMapper()}，只证明了「裸 Jackson 的默认行为」。
     * 真正决定响应形状的是 **Spring MVC 的 ObjectMapper**——它受
     * {@code spring.jackson.default-property-inclusion}、自定义
     * {@code Jackson2ObjectMapperBuilderCustomizer}、以及任何 {@code @JsonInclude}
     * 影响。一旦有人加上 {@code NON_NULL}（一个很常见的「精简响应」优化），
     * 裸 ObjectMapper 的用例**仍然全绿**，而前端收到的对象里根本没有
     * {@code imageModelName} 这个键。
     *
     * <p>为什么「键在场」是硬要求：前端 {@code ModelProfilesView} 用
     * {@code profile.imageModelName || ''} 回填表单、{@code AgentsView} 用
     * {@code (p.imageModelName || '').trim()} 决定配图模型来源。字段一旦被省略，
     * 前端拿到的是 {@code undefined}，与「后端明确说这条档案没有图片模型」不是一回事。
     *
     * <p><b>本用例必须自建档案并**清理干净**</b>：测试库启动时 `LLM_PROFILE` 是**空**的
     * （{@code LlmProfileSeeder} 只在存在「有 key 的默认档案」时才补标准档案），
     * 所以没有现成的「未声明图片模型」档案可读。
     *
     * <p>同时它**必须**清理：本类所有方法共享同一 Spring 上下文与同一个库，
     * 而方法执行顺序由 JUnit 的哈希决定（新增方法会改变顺序）。早先的写法只建不删，
     * 结果在 {@code llmProfileCrudAndSettingsLlmCompatMapping} 之前留下一条非默认档案，
     * 那条用例的 {@code PUT /api/settings/llm} 会经
     * {@code syncDefaultFromConfig → defaultProfile() → findFirst()} 落到**这条多余的档案**上
     * 而不标记 {@code is_default}，于是它的 {@code $.data[?(@.isDefault==true)]} 断言落空。
     * 用 {@code try/finally} 保证无论断言成败都不留残留。
     */
    @Test
    void nullableProfileFieldsArePresentAsNullOverHttp() throws Exception {
        String admin = adminToken();

        // 建一条**不声明图片模型**的档案：imageModelName 落库为 NULL
        String created = mockMvc.perform(post("/api/llm-profiles")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"HTTP 空值契约档案","provider":"OPENAI_COMPATIBLE",
                                 "baseUrl":"https://null-contract.test","modelName":"null-contract-model",
                                 "apiKey":"null-contract-key","enabled":true,
                                 "temperature":0.5,"maxTokens":1024}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll("(?s).*\"id\":(\\d+).*", "$1"));

        try {
            // ① 单条读：键必须在场、值为 null。用 Jackson 判键的存在性，
            //    而不是字符串 contains——后者分不清「键在场为 null」与「键缺失」以外的假阳性
            String single = mockMvc.perform(get("/api/llm-profiles/" + id).header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(imageModelNameKeyIsPresent(single))
                    .as("GET /api/llm-profiles/{id} 的 data.imageModelName 必须是「键在场、值为 null」，"
                            + "实际响应=" + single).isTrue();

            // ② 列表读：同一份契约在列表里也要成立（前端档案页走的是列表接口）
            String listed = mockMvc.perform(get("/api/llm-profiles").header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(imageModelNameKeyIsPresentInList(listed, id))
                    .as("列表接口里该档案同样必须是「键在场、值为 null」，实际响应=" + listed).isTrue();
        } finally {
            // 清理：本类共享上下文，留下多余档案会破坏其它用例（见 javadoc）
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .delete("/api/llm-profiles/" + id).header("Authorization", admin));
        }
    }

    /** 列表响应里指定 id 那条的 {@code imageModelName} 是否「键在场、值为 null」。 */
    private static boolean imageModelNameKeyIsPresentInList(String json, Long id) throws Exception {
        for (com.fasterxml.jackson.databind.JsonNode profile
                : new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path("data")) {
            if (profile.path("id").asLong() == id) {
                return profile.has("imageModelName") && profile.get("imageModelName").isNull();
            }
        }
        return false;
    }

    /**
     * 响应里 {@code imageModelName} 这个**键**是否存在且值为 null。
     *
     * <p>用 Jackson 解析而不是字符串匹配：键存在但值为 null 与键被省略，在 JSON 文本上
     * 分别是 {@code "imageModelName":null} 与完全没有该子串；但若字段名恰好作为**值**
     * 出现在别处（例如 message 里），纯字符串匹配会给出假阳性。
     */
    private static boolean imageModelNameKeyIsPresent(String json) throws Exception {
        com.fasterxml.jackson.databind.JsonNode data =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path("data");
        return data.has("imageModelName") && data.get("imageModelName").isNull();
    }
}
