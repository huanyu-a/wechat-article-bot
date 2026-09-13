package ink.icoding.wechat.article;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
 * MARKFLOW 文章排版元数据落库的回归保护（skills-agent-plan 5.10.4）。
 *
 * <p>背景（2026-09-10 端到端走查实测）：编辑器只认识渲染产物 HTML，加载后会重新序列化——
 * 一次「只改标题」的保存就把渲染产物改写成编辑器 HTML（内联样式重排、&lt;span&gt;/&lt;em&gt; 变
 * &lt;strong&gt;/&lt;p&gt;、表格补 colgroup），公众号版式随之丢失；而渲染产物无法反推回 Markdown。
 * 因此：① MARKFLOW 文章必须留存引擎与 Markdown 源文，② 正文文本未变化的保存不得覆盖渲染产物。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class MarkFlowArticleLayoutPersistenceTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");
    private static final String RENDERED = "<section style=\"margin:16px 0px\">"
            + "<p style=\"margin:0px;font-size:16px;color:#334155;line-height:1.85\">正文第一段。</p></section>";
    private static final String EDITOR_RESERIALIZED = "<section style=\"margin: 16px 0px;\">"
            + "<p style=\"font-size: 16px; color: rgb(51, 65, 85); line-height: 1.85; margin: 0px;\">"
            + "正文第一段。</p></section>";
    private static final String MARKDOWN = "正文第一段。";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = TOKEN.matcher(body);
        if (!matcher.find()) throw new AssertionError("登录响应中缺少 token");
        return "Bearer " + matcher.group(1);
    }

    private String createMarkflowArticle(String authorization) throws Exception {
        String body = mockMvc.perform(post("/api/articles")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("MarkFlow 排版持久化验收", 0, RENDERED, MARKDOWN, "MARKFLOW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layoutEngine").value("MARKFLOW"))
                .andExpect(jsonPath("$.data.contentMarkdown").value(MARKDOWN))
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(body);
        if (!matcher.find()) throw new AssertionError("创建文章响应中缺少 id");
        return matcher.group(1);
    }

    private String articleJson(String title, int revision, String contentHtml, String markdown, String engine)
            throws Exception {
        var mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        return """
                {
                  "title": %s,
                  "author": "测试用户",
                  "digest": "摘要",
                  "contentHtml": %s,
                  "revision": %d,
                  "layoutEngine": %s,
                  "contentMarkdown": %s
                }
                """.formatted(mapper.writeValueAsString(title), mapper.writeValueAsString(contentHtml), revision,
                mapper.writeValueAsString(engine), mapper.writeValueAsString(markdown));
    }

    /** MARKFLOW 引擎与 Markdown 源文必须随文章落库，否则渲染产物被覆盖后无法重排。 */
    @Test
    void markflowArticleKeepsEngineAndMarkdownSource() throws Exception {
        String authorization = adminToken();
        String id = createMarkflowArticle(authorization);

        mockMvc.perform(get("/api/articles/" + id).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layoutEngine").value("MARKFLOW"))
                .andExpect(jsonPath("$.data.contentMarkdown").value(MARKDOWN))
                .andExpect(jsonPath("$.data.contentHtml").value(RENDERED));
    }

    /**
     * 只改标题的保存不得覆盖渲染产物：正文文本一致时保留库中原有 HTML 与 Markdown 源文
     * （否则用户改个标题就丢掉公众号版式）。
     */
    @Test
    void titleOnlySaveKeepsRenderedHtmlAndMarkdown() throws Exception {
        String authorization = adminToken();
        String id = createMarkflowArticle(authorization);

        mockMvc.perform(put("/api/articles/" + id)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        // 模拟编辑器：回传重新序列化后的正文，同时改标题
                        .content(articleJson("MarkFlow 排版持久化验收（改标题）", 1,
                                EDITOR_RESERIALIZED, MARKDOWN, "PROMPT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("MarkFlow 排版持久化验收（改标题）"))
                .andExpect(jsonPath("$.data.contentHtml").value(RENDERED))
                .andExpect(jsonPath("$.data.contentMarkdown").value(MARKDOWN))
                .andExpect(jsonPath("$.data.layoutEngine").value("MARKFLOW"));
    }

    /**
     * 正文确实被改动时按编辑器结果落库（不能因为保版式把用户的编辑吞掉），
     * 同时丢弃已与正文不符的 Markdown 源文——否则后续重排会悄悄覆盖用户刚做的编辑。
     */
    @Test
    void bodyEditPersistsEditorContentAndDropsStaleMarkdown() throws Exception {
        String authorization = adminToken();
        String id = createMarkflowArticle(authorization);
        String edited = RENDERED.replace("正文第一段。", "正文第一段（已编辑）。");

        mockMvc.perform(put("/api/articles/" + id)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        // 真实编辑器回传：article 对象里带着 MARKFLOW 与（此时已过期的）Markdown 源文
                        .content(articleJson("MarkFlow 排版持久化验收", 1, edited, MARKDOWN, "MARKFLOW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString("已编辑")))
                .andExpect(jsonPath("$.data.contentMarkdown").doesNotExist());

        String stored = mockMvc.perform(get("/api/articles/" + id).header("Authorization", authorization))
                .andReturn().getResponse().getContentAsString();
        assertThat(stored).contains("已编辑");
    }

    /**
     * 按新的 Markdown 重新渲染后覆盖正文时，必须留存这份新 Markdown 作为产物的源文。
     * 与上一条的区别只在提交的 Markdown 是否与库中一致——手动编辑/AI 局部编辑总是原样回传旧值，
     * 因此「不同」是重排提交的可判定信号；若一律丢弃，重排一次就再也无法二次调整。
     */
    @Test
    void markdownRerenderKeepsNewMarkdownSource() throws Exception {
        String authorization = adminToken();
        String id = createMarkflowArticle(authorization);
        String rerendered = RENDERED.replace("正文第一段。", "正文第一段（重排后）。");
        String newMarkdown = "正文第一段（重排后）。";

        mockMvc.perform(put("/api/articles/" + id)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("MarkFlow 排版持久化验收", 1, rerendered, newMarkdown, "MARKFLOW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString("重排后")))
                .andExpect(jsonPath("$.data.contentMarkdown").value(newMarkdown))
                .andExpect(jsonPath("$.data.layoutEngine").value("MARKFLOW"));
    }

    /**
     * 回滚必须恢复目标版本的**完整状态**（回归保护）：此前只取标题/摘要/正文，
     * 作者、来源 URL、排版引擎与 Markdown 源文都保留当前值——回滚结果并不是那个版本的样子；
     * 更关键的是 article_revision 根本没有引擎/Markdown 两列，回滚后 MARKFLOW 文章连源文都拿不回来。
     */
    @Test
    void rollbackRestoresAuthorSourceUrlAndMarkdownSource() throws Exception {
        String authorization = adminToken();
        String v2Markdown = "正文第一段（第二版）。";
        String v2Html = RENDERED.replace("正文第一段。", "正文第一段（第二版）。");
        String created = mockMvc.perform(post("/api/articles")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"回滚验收","author":"第一版作者","digest":"第一版摘要",
                                 "contentHtml":%s,"layoutEngine":"MARKFLOW","contentMarkdown":%s,
                                 "sourceUrl":"https://example.com/v1"}
                                """.formatted(json(RENDERED), json(MARKDOWN))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = idOf(created);

        // 第二版：作者、来源、正文与源文全部改掉（revision 1 → 2）
        mockMvc.perform(put("/api/articles/" + id)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"回滚验收（第二版）","author":"第二版作者","digest":"第二版摘要",
                                 "contentHtml":%s,"revision":1,"layoutEngine":"MARKFLOW",
                                 "contentMarkdown":%s,"sourceUrl":"https://example.com/v2"}
                                """.formatted(json(v2Html), json(v2Markdown))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author").value("第二版作者"));

        // 回滚到第一版：标题/作者/来源/正文/引擎/Markdown 都必须回到第一版
        mockMvc.perform(post("/api/articles/" + id + "/revisions/1/rollback")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("回滚验收"))
                .andExpect(jsonPath("$.data.author").value("第一版作者"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://example.com/v1"))
                .andExpect(jsonPath("$.data.contentHtml").value(RENDERED))
                .andExpect(jsonPath("$.data.layoutEngine").value("MARKFLOW"))
                .andExpect(jsonPath("$.data.contentMarkdown").value(MARKDOWN));
    }

    /**
     * 升级前落库的旧版本没有 author/source_url/layout_engine/content_markdown 四列（值为 null）：
     * 回滚到这类版本时保留当前值，而不是把字段抹成 null——「恢复不了」不应表现为「丢数据」。
     */
    @Test
    void rollbackToLegacyRevisionKeepsCurrentValuesInsteadOfNullingThem() throws Exception {
        String authorization = adminToken();
        String created = mockMvc.perform(post("/api/articles")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"旧版本回滚验收","author":"现任作者","digest":"现任摘要",
                                 "contentHtml":"<p>当前正文。</p>","sourceUrl":"https://example.com/current"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = idOf(created);

        // 直接造一条「升级前形态」的版本行：四个新列全为 NULL
        jdbcTemplate.update("""
                INSERT INTO ARTICLE_REVISION (ARTICLE_ID, REVISION, TITLE, DIGEST, CONTENT_HTML,
                                              CHANGE_SOURCE, CHANGE_SUMMARY, CREATED_BY, CREATED_AT)
                VALUES (?, 99, '升级前标题', '升级前摘要', '<p>升级前正文。</p>', 'MANUAL', '升级前快照', 1, NOW())
                """, Long.valueOf(id));

        mockMvc.perform(post("/api/articles/" + id + "/revisions/99/rollback")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("升级前标题"))
                .andExpect(jsonPath("$.data.contentHtml").value("<p>升级前正文。</p>"))
                // 旧快照没有这些字段 → 保留当前值
                .andExpect(jsonPath("$.data.author").value("现任作者"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://example.com/current"))
                .andExpect(jsonPath("$.data.layoutEngine").value("PROMPT"));
    }

    /**
     * 主题色必须随文章留存，且**编辑器保存（请求体不带主题字段）不得把它抹掉**。
     *
     * <p>渲染产物 HTML 里反推不出主题色（颜色散落在几十条内联样式里），此前它只活在「本轮调用方
     * 传了什么」的内存态里、落库即丢；于是换主题重排只能传 null，渲染服务按默认色渲染，
     * 一篇科技蓝的文章重排一次就整篇漂成默认绿——这正是「重排后的版式没有复刻原来的 MarkFlow 渲染」。
     */
    @Test
    void themeSurvivesEditorSaveThatOmitsThemeFields() throws Exception {
        String authorization = adminToken();
        String created = mockMvc.perform(post("/api/articles")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"主题留存验收","contentHtml":%s,"layoutEngine":"MARKFLOW",
                                 "contentMarkdown":%s,"themeAccent":"#0984e3","themeDark":"#0652dd"}
                                """.formatted(json(RENDERED), json(MARKDOWN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeAccent").value("#0984e3"))
                .andExpect(jsonPath("$.data.themeDark").value("#0652dd"))
                .andReturn().getResponse().getContentAsString();
        String id = idOf(created);

        // 模拟编辑器保存：正文文本不变、改标题，请求体里**没有**主题字段（老前端/第三方客户端即如此）
        mockMvc.perform(put("/api/articles/" + id)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("主题留存验收（改标题）", 1, EDITOR_RESERIALIZED, MARKDOWN, "MARKFLOW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeAccent").value("#0984e3"))
                .andExpect(jsonPath("$.data.themeDark").value("#0652dd"));
    }

    /** 转为指令式排版（PROMPT）时必须清掉主题色：它只对渲染式产物有意义，留着会让下次误用旧色重排。 */
    @Test
    void switchingToPromptLayoutClearsStoredTheme() throws Exception {
        String authorization = adminToken();
        String created = mockMvc.perform(post("/api/articles")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"引擎切换验收","contentHtml":%s,"layoutEngine":"MARKFLOW",
                                 "contentMarkdown":%s,"themeAccent":"#0984e3","themeDark":"#0652dd"}
                                """.formatted(json(RENDERED), json(MARKDOWN))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = idOf(created);

        mockMvc.perform(put("/api/articles/" + id)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("引擎切换验收", 1, "<p>改成普通正文。</p>", null, "PROMPT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layoutEngine").value("PROMPT"))
                .andExpect(jsonPath("$.data.themeAccent").doesNotExist())
                .andExpect(jsonPath("$.data.themeDark").doesNotExist());
    }

    private static String json(String value) throws Exception {
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(value);
    }

    private static String idOf(String responseBody) {
        Matcher matcher = Pattern.compile("\\\"id\\\":(\\d+)").matcher(responseBody);
        if (!matcher.find()) throw new AssertionError("响应中缺少 id：" + responseBody);
        return matcher.group(1);
    }
}
