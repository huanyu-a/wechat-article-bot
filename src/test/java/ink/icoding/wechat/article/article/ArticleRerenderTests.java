package ink.icoding.wechat.article.article;

import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.auth.CurrentUser;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.MarkFlowRenderService;
import ink.icoding.wechat.article.skill.SkillBindingValidator;
import ink.icoding.wechat.article.wechat.WechatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I4：用留存 Markdown 重新渲染入口（{@code POST /api/articles/{id}/rerender}）。
 *
 * <p>重点守两件事：①不是 MARKFLOW 或没有留存源文时必须**明确拒绝**而不是静默覆盖；
 * ②只改主题色（正文文本不变）时新产物必须真正落库——否则「换主题批量重排」等于没做。
 * 第二条是 {@code reconcileRenderedLayout}（编辑器往返防降级）与重渲染的直接冲突点：
 * 重渲染必须绕过「正文文本未变就保留旧产物」的还原逻辑。
 */
class ArticleRerenderTests {
    private static final String OLD_HTML = "<section style=\"color:red\"><p>正文</p></section>";
    private static final String NEW_HTML = "<section style=\"color:blue\"><p>正文</p></section>";
    private static final String MARKDOWN = "# 正文";

    private ArticleMapper mapper;
    private ArticleRevisionMapper revisionMapper;
    private CurrentUserService currentUserService;
    private MarkFlowRenderService markFlowRenderService;
    private ArticleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ArticleMapper.class);
        revisionMapper = mock(ArticleRevisionMapper.class);
        currentUserService = mock(CurrentUserService.class);
        markFlowRenderService = mock(MarkFlowRenderService.class);
        service = new ArticleService(mapper, revisionMapper, currentUserService,
                mock(AssetService.class), mock(WechatAccountService.class), mock(WechatClient.class),
                markFlowRenderService, mock(SkillBindingValidator.class));
        when(currentUserService.required()).thenReturn(new CurrentUser(1L, "admin", "管理员", "ADMIN"));
    }

    @Test
    void rejectsArticleThatIsNotMarkflow() {
        when(mapper.findById(1L)).thenReturn(article(LayoutEngine.PROMPT.name(), OLD_HTML, MARKDOWN));

        assertThatThrownBy(() -> service.rerender(1L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MARKFLOW");
        verify(markFlowRenderService, never()).render(any(), any(), any());
    }

    @Test
    void rejectsMarkflowArticleWithoutRetainedMarkdown() {
        when(mapper.findById(1L)).thenReturn(article(LayoutEngine.MARKFLOW.name(), OLD_HTML, null));

        assertThatThrownBy(() -> service.rerender(1L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Markdown");
        verify(markFlowRenderService, never()).render(any(), any(), any());
    }

    @Test
    void themeOnlyRerenderOverwritesHtmlInsteadOfBeingRevertedToOldProduct() {
        // 正文文本完全相同，只有主题色变了——这正是 reconcileRenderedLayout 会误判为
        // 「编辑器往返降级」并把新产物还原成旧产物的场景；重渲染必须落库新 HTML。
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq("blue"), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult(NEW_HTML, "标题", "摘要", "blue", null));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, "blue", null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getContentHtml()).contains("color:blue");
        // 源文必须继续留存，否则第二次重渲染就没了依据
        assertThat(changed.getValue().getContentMarkdown()).isEqualTo(MARKDOWN);
        assertThat(changed.getValue().getLayoutEngine()).isEqualTo(LayoutEngine.MARKFLOW.name());
    }

    @Test
    void digestIsBackfilledFromRenderSummaryWhenArticleHasNone() {
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        current.setDigest(null);
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq(null), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult(NEW_HTML, "标题", "渲染摘要", null, null));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, null, null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getDigest()).isEqualTo("渲染摘要");
    }

    @Test
    void markflowRerenderKeepsFormulaStyleAndAriaMarkers() {
        // I3：渲染服务输出的配套 <style>（KaTeX 公式）与 aria-* 标记此前会被 Jsoup 清洗整段剥离。
        // MARKFLOW 请求必须放行它们，否则公众号侧公式只剩半套样式。
        String rendered = "<section><style>.katex{font-size:1.1em}</style>"
                + "<span aria-hidden=\"true\" class=\"katex\">x</span><p>正文</p></section>";
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq(null), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult(rendered, "标题", "摘要", null, null));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, null, null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getContentHtml())
                .contains("<style>").contains("font-size:1.1em").contains("aria-hidden=\"true\"");
    }

    @Test
    void markflowKeepsComponentTagsThatAnAllowListWouldUnwrap() {
        // 用户报的「精排版式没有 100% 复刻 MarkFlow 渲染能力」的直接来源之一：
        // 此前 MARKFLOW 正文也走 Jsoup 白名单，白名单对不认识的标签是**解包**——标签与它的内联样式一起消失、
        // 只留文字。公式的 MathML/KaTeX 结构、图表的 <svg>、<mark>/<kbd> 一类语义标签都是这样被拆掉的。
        // 渲染服务本身是黑名单语义（MarkFlowRenderService.sanitizeHtml 注释：「上游新增组件标签不被误杀」），
        // 落库这一侧必须保持一致。
        String rendered = "<section><p>公式："
                + "<math display=\"block\"><semantics><mrow><mi>E</mi><mo>=</mo><msup><mi>m</mi><mn>2</mn></msup></mrow>"
                + "<annotation encoding=\"application/x-tex\">E=mc^2</annotation></semantics></math></p>"
                + "<p><mark style=\"background:#fff2a8\">重点</mark>与<kbd>Ctrl</kbd>键</p></section>";
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq(null), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult(rendered, "标题", "摘要", null, null));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, null, null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getContentHtml())
                .contains("<math ").contains("<annotation").contains("<msup>")
                .contains("<mark ").contains("<kbd>").contains("background:#fff2a8");
    }

    @Test
    void markflowStillStripsDangerousElementsAndUriSchemes() {
        // 黑名单放宽的只是「未知但无害」的标签；危险元素、事件属性与 javascript: URI 必须照旧移除——
        // 接口用 {layoutEngine:"MARKFLOW"} 直接提交 HTML 是这条路径的真实攻击面。
        String rendered = "<section onclick=\"steal()\"><script>alert(1)</script>"
                + "<iframe src=\"https://evil.example\"></iframe>"
                + "<p style=\"color:red\">安全正文</p>"
                + "<a href=\"javascript:alert(1)\">链接</a>"
                + "<img src=\"x\" onerror=\"alert(1)\"></section>";
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq(null), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult(rendered, "标题", "摘要", null, null));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, null, null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getContentHtml())
                .doesNotContain("<script").doesNotContain("<iframe").doesNotContain("javascript:")
                .doesNotContain("onclick").doesNotContain("onerror")
                .contains("安全正文").contains("color:red");
    }

    /**
     * 重渲染不带主题色时必须**沿用文章留存的那一组**。
     *
     * <p>这是「重排后版式没有复刻原来的 MarkFlow 渲染」的根因：渲染产物 HTML 里反推不出主题
     * （颜色散落在几十条内联样式里），此前主题只活在「这一轮调用方传了什么」的内存态里，
     * 于是换主题重排只能传 null → 渲染服务按默认色渲染，一篇科技蓝的文章整篇漂成默认绿。
     */
    @Test
    void rerenderReusesStoredThemeInsteadOfFallingBackToRendererDefault() {
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        current.setThemeAccent("#0984e3");
        current.setThemeDark("#0652dd");
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq("#0984e3"), eq("#0652dd")))
                .thenReturn(new MarkFlowRenderService.RenderResult(NEW_HTML, "标题", "摘要",
                        "#0984e3", "#0652dd"));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, null, null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        // 断言落库值而不只是断言「传了什么」：只验传参会漏掉「渲染器回报的默认色反手覆盖留存值」
        assertThat(changed.getValue().getThemeAccent()).isEqualTo("#0984e3");
        assertThat(changed.getValue().getThemeDark()).isEqualTo("#0652dd");
    }

    /** 显式覆盖优先于留存值——只有显式给的那一路才换色，另一路继续沿用留存。 */
    @Test
    void explicitAccentOverridesStoredThemeButKeepsStoredDark() {
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        current.setThemeAccent("#0984e3");
        current.setThemeDark("#0652dd");
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq("#e74c3c"), eq("#0652dd")))
                .thenReturn(new MarkFlowRenderService.RenderResult(NEW_HTML, "标题", "摘要",
                        "#e74c3c", "#0652dd"));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, "#e74c3c", null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getThemeAccent()).isEqualTo("#e74c3c");
        assertThat(changed.getValue().getThemeDark()).isEqualTo("#0652dd");
    }

    /**
     * 渲染服务派生出的主题色（请求没给、服务自己选了一组）也必须留存——
     * 否则第一次渲染写进 HTML 的颜色与库里记的对不上，下一次重排又回到默认色。
     */
    @Test
    void rerenderPersistsThemeDerivedByRendererWhenRequestOmitsIt() {
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq(null), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult(NEW_HTML, "标题", "摘要",
                        "#27ae60", "#1d8348"));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, null, null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getThemeAccent()).isEqualTo("#27ae60");
        assertThat(changed.getValue().getThemeDark()).isEqualTo("#1d8348");
    }

    /** 上游没回报主题时退回请求值，不能把已有主题写没。 */
    @Test
    void rerenderFallsBackToRequestedThemeWhenRendererReportsNone() {
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        current.setThemeAccent("#0984e3");
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq("#0984e3"), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult(NEW_HTML, "标题", "摘要", null, null));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, null, null);

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getThemeAccent()).isEqualTo("#0984e3");
    }

    /**
     * 渲染降级（组件没被识别 / 内容被渲染器丢掉）必须留痕：以前只落在服务端日志里，
     * 用户换完模板只看到版式不对、查不到原因。降级条数与首条原因写进版本说明。
     */
    @Test
    void rerenderRecordsLayoutDegradationsInTheRevisionSummary() {
        Article current = article(LayoutEngine.MARKFLOW.name(), OLD_HTML, MARKDOWN);
        when(mapper.findById(1L)).thenReturn(current);
        when(markFlowRenderService.render(eq(MARKDOWN), eq(null), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult(NEW_HTML, "标题", "摘要", null, null,
                        java.util.List.of("组件标签 <timeline> 未被识别", "容器语法 :::compare 未被识别")));
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);

        service.rerender(1L, null, null);

        ArgumentCaptor<ArticleRevision> revision = ArgumentCaptor.forClass(ArticleRevision.class);
        verify(revisionMapper).insert(revision.capture());
        assertThat(revision.getValue().getChangeSummary())
                .contains("2 处版式降级").contains("<timeline>").contains("重新渲染排版");
    }

    @Test
    void nonMarkflowArticleStillStripsStyle() {        // I3 的放行必须**只对 MARKFLOW**：普通 HTML 文章的 <style> 仍是注入面，继续剥离。
        Article current = article(LayoutEngine.PROMPT.name(), OLD_HTML, null);
        when(mapper.findById(1L)).thenReturn(current);
        when(mapper.updateContent(any(), eq(1))).thenReturn(1);
        ArticleService.ArticleRequest request = new ArticleService.ArticleRequest(5L, "测试文章", null, null,
                "<section><style>.katex{}</style><p>正文</p></section>", null, null, null, 1, null, null, null,
                null, null);

        service.update(1L, request, "MANUAL", "编辑");

        ArgumentCaptor<Article> changed = ArgumentCaptor.forClass(Article.class);
        verify(mapper).updateContent(changed.capture(), eq(1));
        assertThat(changed.getValue().getContentHtml()).doesNotContain("<style>").contains("正文");
    }

    private static Article article(String layoutEngine, String html, String markdown) {
        Article article = new Article();
        article.setId(1L);
        article.setAccountId(5L);
        article.setTitle("测试文章");
        article.setContentHtml(html);
        article.setContentText("正文");
        article.setLayoutEngine(layoutEngine);
        article.setContentMarkdown(markdown);
        article.setRevision(1);
        return article;
    }
}
