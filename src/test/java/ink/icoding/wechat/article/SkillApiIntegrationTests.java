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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Skill API 集成测试（skills-agent-plan 8.2）：
 * /api/skills CRUD、engineConfig 双形态契约（前端发对象 / 脚本发字符串）、维度与引擎校验、
 * 内置不可删、克隆、角色矩阵、preview 组装。
 *
 * 其中 engineConfig 双形态是前端契约的回归保护：前端 SkillsView 直接回传 JSON 对象，
 * 若后端用 String 接收会 400（Jackson 不能把 Object 反序列化成 String）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SkillApiIntegrationTests {
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
                                {"username":"skill_viewer","displayName":"skill_viewer",
                                 "password":"Passw0rd!","role":"VIEWER"}
                                """))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"skill_viewer\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = TOKEN.matcher(body);
        if (!matcher.find()) throw new AssertionError("登录响应中缺少 token");
        return "Bearer " + matcher.group(1);
    }

    @Test
    void builtinSkillsAreSeeded() throws Exception {
        String admin = adminToken();
        mockMvc.perform(get("/api/skills").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='默认公众号版式')]").exists())
                .andExpect(jsonPath("$.data[?(@.name=='MarkFlow 精排版式')]").exists())
                .andExpect(jsonPath("$.data[?(@.name=='事实核查基线')]").exists())
                // 图片风格维度（IMAGE）自带三种预设：无预设时该维度形同虚设
                .andExpect(jsonPath("$.data[?(@.name=='纪实摄影风')]").exists())
                .andExpect(jsonPath("$.data[?(@.name=='扁平插画风')]").exists())
                .andExpect(jsonPath("$.data[?(@.name=='柔和 3D 渲染风')]").exists());

        Long builtin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SKILL WHERE IS_BUILTIN = 1", Long.class);
        // 从种子清单推导而非写死数字：新增内置技能时用例自动跟随，不必手工改断言
        assertThat(builtin).isEqualTo((long) ink.icoding.wechat.article.skill.SkillSeeder.seeds().size());

        // 图片风格预设必须落在 IMAGE 维度（维度白名单校验通过且可被 prompt 组装注入）
        Long imageBuiltin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SKILL WHERE IS_BUILTIN = 1 AND DIMENSION = 'IMAGE'", Long.class);
        assertThat(imageBuiltin).isEqualTo(3L);

        // 内置技能不可删除
        Long id = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'default_layout'", Long.class);
        mockMvc.perform(delete("/api/skills/" + id).header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    /** 前端 SkillsView 的写法：engineConfig 是 JSON 对象。 */
    @Test
    void createMarkflowSkillWithEngineConfigObject() throws Exception {
        String admin = adminToken();
        String created = mockMvc.perform(post("/api/skills")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "契约-对象形态主题",
                                  "dimension": "LAYOUT",
                                  "description": "前端对象形态",
                                  "content": "渲染式排版风格说明",
                                  "engine": "MARKFLOW",
                                  "engineConfig": {"accentMode":"FIXED","accent":"#e74c3c","dark":"#c0392b"},
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("MARKFLOW"))
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll("(?s).*\"id\":(\\d+).*", "$1"));

        String stored = jdbcTemplate.queryForObject(
                "SELECT ENGINE_CONFIG FROM SKILL WHERE ID = " + id, String.class);
        assertThat(stored).contains("FIXED").contains("#e74c3c").contains("#c0392b");
    }

    /** 脚本 / 外部 API 的写法：engineConfig 是已序列化 JSON 字符串。 */
    @Test
    void createMarkflowSkillWithEngineConfigString() throws Exception {
        String admin = adminToken();
        mockMvc.perform(post("/api/skills")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "契约-字符串形态主题",
                                  "dimension": "LAYOUT",
                                  "content": "渲染式排版风格说明",
                                  "engine": "MARKFLOW",
                                  "engineConfig": "{\\"accentMode\\":\\"AUTO\\"}",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("MARKFLOW"));
    }

    @Test
    void dimensionEngineAndContentValidation() throws Exception {
        String admin = adminToken();

        // 未知维度
        mockMvc.perform(post("/api/skills")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"坏维度","dimension":"NOT_A_DIM","content":"x","enabled":true}
                                """))
                .andExpect(status().isBadRequest());

        // 未知引擎
        mockMvc.perform(post("/api/skills")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"坏引擎","dimension":"LAYOUT","content":"x",
                                 "engine":"WORDPRESS","enabled":true}
                                """))
                .andExpect(status().isBadRequest());

        // 空内容
        mockMvc.perform(post("/api/skills")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"空内容","dimension":"WRITING","content":"   ","enabled":true}
                                """))
                .andExpect(status().isBadRequest());

        // 非 LAYOUT 维度不允许带引擎（后端置空）
        String created = mockMvc.perform(post("/api/skills")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"写作带引擎","dimension":"WRITING","content":"写作风格",
                                 "engine":"MARKFLOW","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain("MARKFLOW");
    }

    @Test
    void updateDuplicateAndFilterWork() throws Exception {
        String admin = adminToken();
        String created = mockMvc.perform(post("/api/skills")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"待更新技能","dimension":"WRITING","content":"初版内容","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(created.replaceAll("(?s).*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(put("/api/skills/" + id)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"待更新技能（改）","dimension":"WRITING","content":"改后内容","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("待更新技能（改）"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 维度过滤
        mockMvc.perform(get("/api/skills").param("dimension", "WRITING").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.dimension!='WRITING')]").doesNotExist());

        // 克隆内置技能 → 自定义副本
        Long builtinId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'minimal_layout'", Long.class);
        mockMvc.perform(post("/api/skills/" + builtinId + "/duplicate").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isBuiltin").value(false))
                .andExpect(jsonPath("$.data.name").value("极简黑白版式副本"));

        // 删除自定义技能
        mockMvc.perform(delete("/api/skills/" + id).header("Authorization", admin))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotWriteButCanRead() throws Exception {
        String admin = adminToken();
        String viewer = viewerToken(admin);

        mockMvc.perform(get("/api/skills").header("Authorization", viewer))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/skills")
                        .header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"越权技能","dimension":"WRITING","content":"x","enabled":true}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/skills")).andExpect(status().isUnauthorized());
    }

    /**
     * 文章级 Skill 绑定往返（前端 ArticleEditorView 依赖）：
     * 落库是逗号分隔字符串，而**接口两侧都必须是数组**——读取侧曾直接返回实体（字符串），
     * 调用方把 GET 结果原样 PUT 回来就会被 Jackson 以「不能把 String 反序列化成 ArrayList&lt;Long&gt;」拒绝。
     */
    @Test
    void articleSkillIdsRoundTrip() throws Exception {
        String admin = adminToken();
        Long layoutId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'minimal_layout'", Long.class);

        String created = mockMvc.perform(post("/api/articles")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"技能绑定往返","contentHtml":"<p>正文</p>","skillIds":[%d]}
                                """.formatted(layoutId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long articleId = jdbcTemplate.queryForObject("SELECT ID FROM ARTICLE WHERE TITLE = '技能绑定往返' ORDER BY ID DESC LIMIT 1", Long.class);

        // 落库为逗号分隔字符串
        String stored = jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM ARTICLE WHERE ID = " + articleId, String.class);
        assertThat(stored).isEqualTo(String.valueOf(layoutId));

        // 读取返回**数组**形态（与写入侧同形）
        mockMvc.perform(get("/api/articles/" + articleId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillIds").isArray())
                .andExpect(jsonPath("$.data.skillIds[0]").value(layoutId));

        // 更新为另一组绑定并回读
        Long writingId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'practical_tutorial'", Long.class);
        mockMvc.perform(put("/api/articles/" + articleId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"技能绑定往返","contentHtml":"<p>正文</p>","revision":1,
                                 "skillIds":[%d]}
                                """.formatted(writingId)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM ARTICLE WHERE ID = " + articleId, String.class))
                .isEqualTo(String.valueOf(writingId));

        // 真·往返：把 GET 到的 data 原样 PUT 回去（修复前这里因 skillIds 是字符串而 400）
        String fetched = mockMvc.perform(get("/api/articles/" + articleId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String roundTrip = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(fetched).path("data").toString();
        mockMvc.perform(put("/api/articles/" + articleId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roundTrip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillIds[0]").value(writingId));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM ARTICLE WHERE ID = " + articleId, String.class))
                .isEqualTo(String.valueOf(writingId));
    }

    /**
     * 清空文章级技能绑定必须真正落库为 NULL（回归保护）：
     * ArticleMapper.updateContent 曾用 `if (changes.getSkillIds() != null)` 守卫，
     * 导致「解绑全部技能」被静默忽略——用户取消勾选后保存，绑定依然存在。
     */
    @Test
    void clearingArticleSkillIdsPersists() throws Exception {
        String admin = adminToken();
        Long layoutId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'minimal_layout'", Long.class);

        mockMvc.perform(post("/api/articles")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"解绑技能回归","contentHtml":"<p>正文</p>","skillIds":[%d]}
                                """.formatted(layoutId)))
                .andExpect(status().isOk());
        Long articleId = jdbcTemplate.queryForObject(
                "SELECT ID FROM ARTICLE WHERE TITLE = '解绑技能回归' ORDER BY ID DESC LIMIT 1", Long.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM ARTICLE WHERE ID = " + articleId, String.class))
                .isEqualTo(String.valueOf(layoutId));

        // 取消全部勾选 → 传空数组 → 必须清空
        mockMvc.perform(put("/api/articles/" + articleId)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"解绑技能回归","contentHtml":"<p>正文</p>","revision":1,"skillIds":[]}
                                """))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT SKILL_IDS FROM ARTICLE WHERE ID = " + articleId, String.class)).isNull();
    }

    @Test
    void previewReturnsAssembledPromptForPromptSkill() throws Exception {
        String admin = adminToken();
        Long layoutId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'minimal_layout'", Long.class);

        // 必须断言被预览技能自身的正文在场：只断言「排版模板」会被内置保底版式蒙混过关
        // （这正是 preview 把 skillIds 放进 SCHEDULED 不生效的 articleSkillIds 槽位时漏掉的回归）
        mockMvc.perform(post("/api/skills/preview")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillIds":[%d],"scene":"SCHEDULED"}
                                """.formatted(layoutId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engine").value("PROMPT"))
                .andExpect(jsonPath("$.data.prompt").value(org.hamcrest.Matchers.containsString("排版模板")))
                .andExpect(jsonPath("$.data.prompt").value(org.hamcrest.Matchers.containsString("极简黑白版式")))
                .andExpect(jsonPath("$.data.prompt").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("公众号正文视觉模板"))));
    }

    @Test
    void previewInjectsImageStyleSkill() throws Exception {
        String admin = adminToken();
        Long imageId = jdbcTemplate.queryForObject(
                "SELECT ID FROM SKILL WHERE BUILTIN_KEY = 'photo_documentary'", Long.class);

        mockMvc.perform(post("/api/skills/preview")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillIds":[%d],"scene":"SCHEDULED"}
                                """.formatted(imageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prompt").value(org.hamcrest.Matchers.containsString("【图片风格】")))
                .andExpect(jsonPath("$.data.prompt").value(org.hamcrest.Matchers.containsString("纪实摄影风格")));
    }
}
