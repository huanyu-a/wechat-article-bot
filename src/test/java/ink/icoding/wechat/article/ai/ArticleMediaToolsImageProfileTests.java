package ink.icoding.wechat.article.ai;

import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配图档案的**透传**：装配点传入的 {@code imageProfileId} 必须真的走到 {@code ImageGenerationService}。
 *
 * <p>为什么要专门钉这条：整条「按智能体配图片模型」的能力里，真正容易断的不是解析逻辑
 * （{@code LlmProfileServiceTest} / {@code LlmConfigServiceImageRuntimeTest} 已覆盖），
 * 而是**中间那一跳**——{@code ArticleMediaTools.create(..., imageProfileId)} 的参数一路传给
 * {@code GenerateImageTool}/{@code EditImageTool}，再由它们转给图片服务。这一段全是「传参」，
 * 漏一个就会静默退化成「永远走全局图片设置」：功能看着正常（图还是生成了），
 * 只是用户配的档案级图片模型从来没生效过——典型的只能靠断言发现的缺陷。
 */
class ArticleMediaToolsImageProfileTests {

    /** 生图：工具必须把档案 id 原样传给图片服务（而不是自己吞掉、或传 null）。 */
    @Test
    void generateImagePassesTheProfileIdThrough() {
        ImageGenerationService imageService = mock(ImageGenerationService.class);
        when(imageService.generate(any(), any(), any(), any(), any())).thenReturn(asset());
        ArticleMediaTools tools = tools(imageService);

        ArticleMediaTools.GenerateImageTool tool = generateTool(tools, 42L);
        ArticleMediaTools.GenerateImageParam param = new ArticleMediaTools.GenerateImageParam();
        param.setPrompt("一只在窗边打盹的橘猫");
        tool.execute(param);

        verify(imageService).generate(eq(7L), eq("一只在窗边打盹的橘猫"), any(), eq(99L), eq(42L));
    }

    /** 修图同理：另一条独立的传参路径，不能只测生图就以为都通了。 */
    @Test
    void editImagePassesTheProfileIdThrough() {
        ImageGenerationService imageService = mock(ImageGenerationService.class);
        when(imageService.edit(any(), any(), any(), any(), any(), any())).thenReturn(asset());
        ArticleMediaTools tools = tools(imageService);

        ArticleMediaTools.EditImageTool tool = editTool(tools, 42L);
        ArticleMediaTools.EditImageParam param = new ArticleMediaTools.EditImageParam();
        param.setAssetId(5L);
        param.setPrompt("把背景换成纯白");
        tool.execute(param);

        verify(imageService).edit(eq(7L), eq(5L), eq("把背景换成纯白"), any(), eq(99L), eq(42L));
    }

    /**
     * 未绑定档案（装配点传 null）时传 null，图片服务据此回落全局图片设置。
     *
     * <p>这条是**存量行为**的保证：编辑器与未绑定档案的智能体都走这里，
     * 若中途被替换成某个「默认档案 id」，存量部署的全局图片模型就会被悄悄绕过。
     */
    @Test
    void nullProfileIdStaysNull() {
        ImageGenerationService imageService = mock(ImageGenerationService.class);
        when(imageService.generate(any(), any(), any(), any(), any())).thenReturn(asset());
        ArticleMediaTools tools = tools(imageService);

        ArticleMediaTools.GenerateImageTool tool = generateTool(tools, null);
        ArticleMediaTools.GenerateImageParam param = new ArticleMediaTools.GenerateImageParam();
        param.setPrompt("封面图");
        tool.execute(param);

        verify(imageService).generate(eq(7L), eq("封面图"), any(), eq(99L), isNull());
    }

    /**
     * 只读检索工具**不受**配图档案影响：给 {@code create} 传了档案 id，也不该改变它们的构造。
     *
     * <p>钉的是「新参数只作用于图片工具」这个边界——否则一次顺手重构就可能让检索工具
     * 也依赖上档案，把「配图模型」变成整条链路的隐式耦合。
     */
    @Test
    void readOnlyToolsAreUnaffectedByTheProfileId() {
        ImageGenerationService imageService = mock(ImageGenerationService.class);
        ArticleMediaTools tools = tools(imageService);

        List<ink.icoding.llm.core.tool.Tool> created = tools.create(7L, 99L,
                (toolName, paramJson, action) -> action.get(),
                (toolName, paramJson, action) -> action.get(), 42L);

        assertThat(created).hasSize(7);
        assertThat(created).extracting(t -> t.getClass().getSimpleName())
                .contains("SearchWebTool", "BrowseWebpageTool", "SearchWebImagesTool", "ListAssetsTool");
        // 只有两个图片工具携带了档案，且它们与不带档案时构造出的类型一致
        assertThat(created).extracting(t -> t.getClass().getSimpleName())
                .contains("GenerateImageTool", "EditImageTool", "ImportWebImageTool");
    }

    /** 三参重载（存量调用点）仍可用，且等价于「不指定档案」。 */
    @Test
    void legacyOverloadWithoutProfileStillWorks() {
        ImageGenerationService imageService = mock(ImageGenerationService.class);
        when(imageService.generate(any(), any(), any(), any(), any())).thenReturn(asset());
        ArticleMediaTools tools = tools(imageService);

        List<ink.icoding.llm.core.tool.Tool> created = tools.create(7L, 99L);
        assertThat(created).hasSize(7);

        ArticleMediaTools.GenerateImageTool tool = (ArticleMediaTools.GenerateImageTool) created.stream()
                .filter(t -> t instanceof ArticleMediaTools.GenerateImageTool).findFirst().orElseThrow();
        ArticleMediaTools.GenerateImageParam param = new ArticleMediaTools.GenerateImageParam();
        param.setPrompt("封面图");
        tool.execute(param);

        verify(imageService).generate(eq(7L), eq("封面图"), any(), eq(99L), isNull());
    }

    // ---------- 构造 ----------

    private static ArticleMediaTools tools(ImageGenerationService imageService) {
        return new ArticleMediaTools(mock(SafeWebService.class), mock(AssetService.class), imageService);
    }

    private static ArticleMediaTools.GenerateImageTool generateTool(ArticleMediaTools tools, Long profileId) {
        return tools.new GenerateImageTool(7L, 99L, (toolName, paramJson, action) -> action.get(), profileId);
    }

    private static ArticleMediaTools.EditImageTool editTool(ArticleMediaTools tools, Long profileId) {
        return tools.new EditImageTool(7L, 99L, (toolName, paramJson, action) -> action.get(), profileId);
    }

    private static Asset asset() {
        Asset asset = new Asset();
        asset.setId(1L);
        asset.setPublicUrl("/uploads/a.png");
        asset.setOriginalName("a.png");
        asset.setContentType("image/png");
        asset.setSourceType("AI_GENERATED");
        return asset;
    }
}
