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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 渲染产物入库清洗的集成验证（skills-agent-plan 5.10.4 / 第④期 P2-7）：
 * MarkFlow 渲染产物可能用 div 承载组件，且注入 data-render-id 标记——两者都必须能通过
 * {@code ArticleService.clean()} 的 Jsoup Safelist 存活，否则版式失效或防回灌识别失败。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ArticleHtmlSanitizeIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");

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

    @Test
    void renderMarkupSurvivesContentSanitize() throws Exception {
        String authorization = adminToken();
        String renderHtml = "<div data-render-id=\"r1\" class=\"mf-card\" style=\"padding:12px;\">"
                + "<section style=\"color:#27ae60;\"><p style=\"margin:0;\">渲染式版式内容</p></section>"
                + "</div><script>alert(1)</script>";

        mockMvc.perform(post("/api/articles")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "渲染产物入库验收",
                                  "author": "测试用户",
                                  "digest": "验证 div 与 data-render-id 存活",
                                  "contentHtml": %s
                                }
                                """.formatted(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                                .writeValueAsString(renderHtml))))
                .andExpect(status().isOk())
                // div 与 data-render-id 必须保留（否则 MarkFlow 组件版式失效、防回灌识别失败）
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString(
                        "<div data-render-id=\"r1\"")))
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString(
                        "class=\"mf-card\"")))
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString(
                        "style=\"padding:12px;\"")))
                // script 必须被剥离
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("script"))));
    }

    /**
     * 真实上游渲染产物结构（2026-09-09 实测：标签仅 h2/p/section/img，内联样式含 flex/gap/gradient）——
     * 验证清洗管道不丢版式。若未来上游改用 div 承载组件，上一用例已覆盖该情形。
     */
    @Test
    void realUpstreamRenderHtmlKeepsInlineStyles() throws Exception {
        String authorization = adminToken();
        String renderHtml = "<h2 style=\"margin:28px 0px 12px;font-size:20px;font-weight:700;color:#111827\">章节标题</h2>"
                + "<section style=\"margin:0px 0px 24px\"><p style=\"margin:0px;font-size:16px;"
                + "color:#334155;line-height:1.85;text-align:justify\">正文段落。</p></section>"
                + "<section style=\"margin:0px 0px 28px;display:flex;flex-wrap:nowrap;gap:0px;overflow-x:auto\">"
                + "<section style=\"flex:1 0 150px;background:linear-gradient(90deg,#27ae6066,#27ae6022)\">"
                + "<p style=\"margin:0\">步骤卡</p></section></section>"
                + "<figure style=\"margin:24px 0 10px 0\"><img src=\"https://example.com/a.png\" "
                + "style=\"display:block;width:100%\"><figcaption style=\"text-align:center\">图注</figcaption></figure>";

        mockMvc.perform(post("/api/articles")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "真实渲染产物验收",
                                  "author": "测试用户",
                                  "digest": "验证内联样式与结构存活",
                                  "contentHtml": %s
                                }
                                """.formatted(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                                .writeValueAsString(renderHtml))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString(
                        "display:flex")))
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString(
                        "linear-gradient")))
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString(
                        "<figcaption")))
                .andExpect(jsonPath("$.data.contentHtml").value(org.hamcrest.Matchers.containsString(
                        "<figure")));
    }
}
