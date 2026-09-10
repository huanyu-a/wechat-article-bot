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
}
