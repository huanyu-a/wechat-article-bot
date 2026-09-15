package ink.icoding.wechat.article.ai;

import ink.icoding.wechat.article.asset.AssetService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * I9：{@code browse_webpage} 抓不到网页（403/404/410）应作为「可跳过」返回引导文本，
 * 而不是计为工具失败——否则外部噪声会把整次运行拖成「成功（有警告）」并掩盖真正的失败。
 * 但传输层故障（5xx/超时）仍必须作为失败抛出。
 */
class BrowseWebpageToolTests {

    @Test
    void unavailablePageIsSkippedInsteadOfCountedAsToolFailure() {
        SafeWebService webService = mock(SafeWebService.class);
        when(webService.browse("https://example.com/gone"))
                .thenThrow(new SafeWebService.PageUnavailableException(404, "网页不可访问（HTTP 404）"));
        ArticleMediaTools.BrowseWebpageTool tool = tool(webService);
        ArticleMediaTools.BrowseWebpageParam param = new ArticleMediaTools.BrowseWebpageParam();
        param.setUrl("https://example.com/gone");

        String result = tool.execute(param);

        assertThat(result).contains("\"skipped\":true").contains("404");
    }

    @Test
    void antiBotStatusIsAlsoSkipped() {
        SafeWebService webService = mock(SafeWebService.class);
        when(webService.browse("https://example.com/bot"))
                .thenThrow(new SafeWebService.PageUnavailableException(403, "网页不可访问（HTTP 403）"));
        ArticleMediaTools.BrowseWebpageTool tool = tool(webService);
        ArticleMediaTools.BrowseWebpageParam param = new ArticleMediaTools.BrowseWebpageParam();
        param.setUrl("https://example.com/bot");

        assertThat(tool.execute(param)).contains("\"skipped\":true").contains("403");
    }

    @Test
    void transportFailureStillSurfacesAsToolFailure() {
        SafeWebService webService = mock(SafeWebService.class);
        when(webService.browse("https://example.com/broken"))
                .thenThrow(new ink.icoding.wechat.article.common.BusinessException("网页请求失败（HTTP 500）"));
        ArticleMediaTools.BrowseWebpageTool tool = tool(webService);
        ArticleMediaTools.BrowseWebpageParam param = new ArticleMediaTools.BrowseWebpageParam();
        param.setUrl("https://example.com/broken");

        assertThatThrownBy(() -> tool.execute(param))
                .isInstanceOf(ink.icoding.wechat.article.common.BusinessException.class);
    }

    private static ArticleMediaTools.BrowseWebpageTool tool(SafeWebService webService) {
        ArticleMediaTools tools = new ArticleMediaTools(webService, mock(AssetService.class),
                mock(ImageGenerationService.class));
        // 直通 ReadExecutor：这里测的是工具本身对「页面不可访问」的分类，不是检索治理
        // （去重与循环检测由 ToolCallGovernorTest 覆盖），直通即生产默认行为。
        return tools.new BrowseWebpageTool((toolName, paramJson, action) -> action.get());
    }
}
