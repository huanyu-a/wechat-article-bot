package ink.icoding.wechat.article.article;

import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.MarkFlowRenderService;
import ink.icoding.wechat.article.skill.SkillBindingValidator;
import ink.icoding.wechat.article.wechat.WechatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本站素材 URL 的落库归一：**无论正文里的绝对域名是什么，正文一律以相对 `/uploads/…` 落库**。
 *
 * <p>为什么值得单独立一条：调度链路的正文来自渲染服务产物，而渲染服务要求绝对地址——
 * {@code MarkFlowRenderService.absoluteImageUrls} 送渲染前把 {@code /uploads/…} 绝对化成
 * {@code {site_base_url}/uploads/…}，这个地址随产物一起写回 {@code CONTENT_HTML}。
 * 落库边界（{@code ArticleService.clean}）此前只认相对形式，于是每一篇 MARKFLOW 成稿都把
 * 站点域名烧进了正文：实测 2026-09-13 全库 19 篇带着 {@code http://localhost:8081/uploads/…}
 * （含当天 09:29 生成的最新一篇），同库 PROMPT 稿则是干净的相对路径——
 * 换部署 / 换域名后编辑器与预览整片裂图，而微信同步那边其实一直正常
 * （{@code localStorageName} 只看路径不看域名，参照 {@code ArticleWechatContentTests}）。
 */
class ArticleLocalAssetUrlTests {
    private static final String ASSET = "01c6fecfce0547a29b12db20c4788fe2.png";

    private ArticleMapper mapper;
    private ArticleRevisionMapper revisionMapper;
    private ArticleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ArticleMapper.class);
        revisionMapper = mock(ArticleRevisionMapper.class);
        service = new ArticleService(mapper, revisionMapper, mock(CurrentUserService.class),
                mock(AssetService.class), mock(WechatAccountService.class), mock(WechatClient.class),
                mock(MarkFlowRenderService.class), mock(SkillBindingValidator.class));
        when(mapper.insert(any())).thenReturn(1);
        // createWithUser 末尾会用 article.getId() 回查一次；桩掉即可，断言取的是 insert 的入参
        when(mapper.findById(any())).thenReturn(new Article());
    }

    private final ArgumentCaptor<Article> captured = ArgumentCaptor.forClass(Article.class);

    /** 走真实落库入口 {@code createForTask}（调度链路用的就是它），只把 Mapper 换成桩。 */
    private String storedHtml(String contentHtml, String layoutEngine) {
        ArticleService.ArticleRequest request = new ArticleService.ArticleRequest(null, "标题", null, null,
                contentHtml, null, null, null, null, null, layoutEngine, null, null, null);
        service.createForTask(request, 1L);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.atLeastOnce()).insert(captured.capture());
        Article article = captured.getAllValues().get(captured.getAllValues().size() - 1);
        assertThat(article.getContentHtml()).isNotNull();
        return article.getContentHtml();
    }

    @Test
    void absoluteSiteHostInRenderedContentIsNormalizedToRelative() {
        // 渲染服务产物里就是这个形态：src="http://localhost:8081/uploads/<32位hex>.png"
        assertThat(storedHtml("<section><img src=\"http://localhost:8081/uploads/" + ASSET
                + "\" width=\"600\" /></section>", LayoutEngine.MARKFLOW.name()))
                .as("站点域名必须被剥掉，正文只留相对路径")
                .isEqualTo("<section><img src=\"/uploads/" + ASSET + "\" width=\"600\"></section>");
    }

    @Test
    void anyAbsoluteHostOverTheLocalAssetPathIsNormalized() {
        for (String origin : new String[]{
                "https://articles.example.com",
                "http://127.0.0.1:8081",
                "https://wechat-article-local.invalid"}) {
            assertThat(storedHtml("<p><img src=\"" + origin + "/uploads/" + ASSET + "\"></p>",
                    LayoutEngine.MARKFLOW.name()))
                    .as("%s 只是换了个写法，落库语义与 localStorageName 一致（只看路径不看域名）", origin)
                    .isEqualTo("<p><img src=\"/uploads/" + ASSET + "\"></p>");
        }
    }

    @Test
    void otherHostsOverTheAssetPathAreNormalizedToo() {
        // 判据刻意不认域名：这与 localStorageName 完全一致——微信同步那边同样只看路径是不是
        // /uploads/<32位hex>.<ext>，不看挂在哪个域名下（那正是「换 CDN 域名后旧稿仍能同步」的原因）。
        // 两边口径必须一致，否则会出现「归一后微信认、不归一时编辑器认」的撕裂。
        assertThat(storedHtml("<p><img src=\"https://example.com/uploads/" + ASSET + "\"></p>",
                LayoutEngine.MARKFLOW.name()))
                .as("路径形状就是本站素材，域名换成什么都一样")
                .isEqualTo("<p><img src=\"/uploads/" + ASSET + "\"></p>");
    }

    @Test
    void promptEngineNormalizesTheSameWay() {
        assertThat(storedHtml("<p><img src=\"http://localhost:8081/uploads/" + ASSET + "\"></p>",
                LayoutEngine.PROMPT.name()))
                .as("PROMPT 引擎走的是白名单清洗，同样要归一到相对路径")
                .isEqualTo("<p><img src=\"/uploads/" + ASSET + "\"></p>");
    }

    @Test
    void alreadyRelativeLocalAssetIsLeftAsIs() {
        assertThat(storedHtml("<p><img src=\"/uploads/" + ASSET + "\"></p>", LayoutEngine.MARKFLOW.name()))
                .as("相对写法本来就是正确形态，回归守一下")
                .isEqualTo("<p><img src=\"/uploads/" + ASSET + "\"></p>");
    }

    @Test
    void externalImagesAndNonAssetPathsAreNotRewritten() {
        for (String src : new String[]{
                "https://cdn.example.com/pic.png",
                "https://robocopmao.github.io/r-markdown/banner4.webp",
                // 本站域名但文件名不是 32 位 hex 存储名（历史上 e2e 夹具用过这种名字）：
                // localStorageName 同样不认，归一它会与微信同步的口径打架
                "http://localhost:8081/uploads/e2e-sample.png"}) {
            assertThat(storedHtml("<p><img src=\"" + src + "\"></p>", LayoutEngine.MARKFLOW.name()))
                    .as("外链与本地路径不匹配的地址一律不动：%s", src)
                    .contains(src);
        }
    }
}
