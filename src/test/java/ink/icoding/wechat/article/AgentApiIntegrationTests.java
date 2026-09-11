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
}
