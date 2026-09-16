package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class ArticleMediaTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final SafeWebService webService;
    private final AssetService assetService;
    private final ImageGenerationService imageService;

    public ArticleMediaTools(SafeWebService webService, AssetService assetService,
                             ImageGenerationService imageService) {
        this.webService = webService;
        this.assetService = assetService;
        this.imageService = imageService;
    }

    public List<Tool> create(Long accountId, Long userId) {
        return create(accountId, userId, (toolName, paramJson, action) -> action.get());
    }

    public List<Tool> create(Long accountId, Long userId, MutationExecutor mutationExecutor) {
        return create(accountId, userId, mutationExecutor, (toolName, paramJson, action) -> action.get());
    }

    /**
     * @param readExecutor 只读检索的执行包装（Phase 4 治理）：同参数重复调用直接返回首次结果、
     *                     重复过频时在结果后追加可执行的收手指令。默认实现为直通（存量行为不变）。
     */
    public List<Tool> create(Long accountId, Long userId, MutationExecutor mutationExecutor,
                             ReadExecutor readExecutor) {
        return create(accountId, userId, mutationExecutor, readExecutor, null);
    }

    /**
     * @param imageProfileId 发起配图的智能体所绑定的模型档案（可为 null）。生图 / 修图工具把它透传给
     *                       {@link ImageGenerationService}——该档案声明了图片模型就用它，否则回落
     *                       全局图片设置。为什么由工具携带而不是让图片服务自己查：图片服务不知道
     *                       「这次配图是谁要的」，而装配点（AgentFactory / 各执行器）恰好知道。
     */
    public List<Tool> create(Long accountId, Long userId, MutationExecutor mutationExecutor,
                             ReadExecutor readExecutor, Long imageProfileId) {
        return List.of(new SearchWebTool(readExecutor), new BrowseWebpageTool(readExecutor),
                new SearchWebImagesTool(readExecutor), new ListAssetsTool(accountId, readExecutor),
                new ImportWebImageTool(accountId, userId, mutationExecutor),
                new GenerateImageTool(accountId, userId, mutationExecutor, imageProfileId),
                new EditImageTool(accountId, userId, mutationExecutor, imageProfileId));
    }

    @FunctionalInterface
    public interface MutationExecutor {
        String execute(String toolName, String paramJson, Supplier<String> action);
    }

    /**
     * 只读检索的执行包装（Phase 4）：与 {@link MutationExecutor} 对称、语义相反——
     * 那边是「有副作用，重复执行要拦住」，这边是「无副作用，重复执行直接复用首次结果」。
     *
     * <p>为什么不复用 MutationExecutor：变更类工具失败必须向上抛（生图失败要计为工具失败），
     * 而检索类工具失败已由 agent4j 统一转成工具错误回调；两者对异常的处理不同，
     * 混在一个 seam 里会让「谁负责抛」变得含糊。
     */
    @FunctionalInterface
    public interface ReadExecutor {
        String execute(String toolName, String paramJson, Supplier<String> action);
    }

    @ToolInfo(name = "search_web", description = "搜索公开网页，返回标题、链接和摘要。需要事实资料或外部来源时使用。")
    public class SearchWebTool implements Tool<SearchWebParam> {
        private final ReadExecutor readExecutor;
        public SearchWebTool(ReadExecutor readExecutor) { this.readExecutor = readExecutor; }
        @Override public String execute(SearchWebParam param) {
            return readExecutor.execute("search_web", json(param),
                    () -> json(webService.searchWeb(param.getQuery(), value(param.getMaxResults(), 5))));
        }
    }

    @Data
    public static class SearchWebParam extends ToolParam {
        @Param(description = "搜索关键词") private String query;
        @Param(required = false, description = "结果数量，1到10") private Integer maxResults;
    }

    @ToolInfo(name = "browse_webpage", description = "打开一个公开网页并提取标题和正文。必须先有明确URL，禁止访问内网。")
    public class BrowseWebpageTool implements Tool<BrowseWebpageParam> {
        private final ReadExecutor readExecutor;
        public BrowseWebpageTool(ReadExecutor readExecutor) { this.readExecutor = readExecutor; }
        @Override public String execute(BrowseWebpageParam param) {
            return readExecutor.execute("browse_webpage", json(param), () -> {
                try {
                    return json(webService.browse(param.getUrl()));
                } catch (SafeWebService.PageUnavailableException unavailable) {
                    // 403/404/410 属「这一页读不到」而非工具故障：返回可跳过的引导文本，不再计为工具失败，
                    // 避免外部噪声把整次运行拖成「有警告的成功」并掩盖真正的失败。
                    return json(Map.of("url", param.getUrl() == null ? "" : param.getUrl(),
                            "skipped", true,
                            "message", "该网页当前不可访问（HTTP " + unavailable.statusCode()
                                    + "，可能不存在或被反爬拦截），已跳过。请改用搜索结果中的其他来源。"));
                }
            });
        }
    }

    @Data
    public static class BrowseWebpageParam extends ToolParam {
        @Param(description = "要阅读的公开 HTTP/HTTPS 网页地址") private String url;
    }

    @ToolInfo(name = "search_web_images", description = "搜索与文章主题相关的网络图片候选，只返回图片和来源页地址；使用前应确认内容相关并通过import_web_image导入素材库。")
    public class SearchWebImagesTool implements Tool<SearchWebImagesParam> {
        private final ReadExecutor readExecutor;
        public SearchWebImagesTool(ReadExecutor readExecutor) { this.readExecutor = readExecutor; }
        @Override public String execute(SearchWebImagesParam param) {
            return readExecutor.execute("search_web_images", json(param),
                    () -> json(webService.searchImages(param.getQuery(), value(param.getMaxResults(), 6))));
        }
    }

    @Data
    public static class SearchWebImagesParam extends ToolParam {
        @Param(description = "图片搜索关键词，应包含主题和期望视觉风格") private String query;
        @Param(required = false, description = "结果数量，1到10") private Integer maxResults;
    }

    @ToolInfo(name = "list_image_assets", description = "检索文章素材库中的图片。优先复用用户提供或已有的合适素材；结果包含assetId和publicUrl。")
    public class ListAssetsTool implements Tool<ListAssetsParam> {
        private final Long accountId;
        private final ReadExecutor readExecutor;
        public ListAssetsTool(Long accountId, ReadExecutor readExecutor) {
            this.accountId = accountId;
            this.readExecutor = readExecutor;
        }
        @Override public String execute(ListAssetsParam param) {
            return readExecutor.execute("list_image_assets", json(param), () ->
                    json(assetService.search(accountId, param.getKeyword(), value(param.getMaxResults(), 10))
                            .stream().map(ArticleMediaTools::assetView).toList()));
        }
    }

    @Data
    public static class ListAssetsParam extends ToolParam {
        @Param(required = false, description = "文件名或图片描述关键词，留空返回最近素材") private String keyword;
        @Param(required = false, description = "结果数量，1到20") private Integer maxResults;
    }

    @ToolInfo(name = "import_web_image", description = "把已选定的公网图片下载并保存到素材库。必须保留来源页面URL；成功后使用返回的publicUrl通过insert_blocks插入文章。")
    public class ImportWebImageTool implements Tool<ImportWebImageParam> {
        private final Long accountId;
        private final Long userId;
        private final MutationExecutor mutationExecutor;
        public ImportWebImageTool(Long accountId, Long userId, MutationExecutor mutationExecutor) {
            this.accountId = accountId;
            this.userId = userId;
            this.mutationExecutor = mutationExecutor;
        }
        @Override public String execute(ImportWebImageParam param) {
            return mutationExecutor.execute("import_web_image", json(param), () -> {
                SafeWebService.BinaryResponse image = webService.downloadImage(param.getImageUrl());
                Asset asset = assetService.saveImage(accountId, param.getFilename(), image.contentType(), image.bytes(),
                        "WEB_IMPORT", param.getSourcePageUrl(), param.getDescription(), userId);
                return json(assetView(asset));
            });
        }
    }

    @Data
    public static class ImportWebImageParam extends ToolParam {
        @Param(description = "图片原始公网URL") private String imageUrl;
        @Param(description = "图片所在的来源页面URL，用于溯源") private String sourcePageUrl;
        @Param(required = false, description = "保存文件名") private String filename;
        @Param(description = "图片内容和适用位置的简短描述") private String description;
    }

    @ToolInfo(name = "generate_image", description = "使用后台配置的图片模型创作配图并保存到素材库。提示词应描述主体、构图、风格、比例且避免在图中生成文字；已绑定图片风格技能时（系统提示的【图片风格】注入区），提示词必须按该技能的脚手架组织并包含其要求的禁用项。成功后使用publicUrl通过insert_blocks插入文章。")
    public class GenerateImageTool implements Tool<GenerateImageParam> {
        private final Long accountId;
        private final Long userId;
        private final MutationExecutor mutationExecutor;
        private final Long imageProfileId;
        public GenerateImageTool(Long accountId, Long userId, MutationExecutor mutationExecutor) {
            this(accountId, userId, mutationExecutor, null);
        }
        public GenerateImageTool(Long accountId, Long userId, MutationExecutor mutationExecutor,
                                 Long imageProfileId) {
            this.accountId = accountId;
            this.userId = userId;
            this.mutationExecutor = mutationExecutor;
            this.imageProfileId = imageProfileId;
        }
        @Override public String execute(GenerateImageParam param) {
            return mutationExecutor.execute("generate_image", json(param), () ->
                    json(assetView(imageService.generate(accountId, param.getPrompt(), param.getFilename(),
                            userId, imageProfileId))));
        }
    }

    @Data
    public static class GenerateImageParam extends ToolParam {
        @Param(description = "详细的图片创作提示词") private String prompt;
        @Param(required = false, description = "保存到素材库的文件名") private String filename;
    }

    @ToolInfo(name = "edit_image", description = "使用图片模型编辑素材库中的图片并另存为新素材，不覆盖原图；成功后使用publicUrl通过insert_blocks插入文章。")
    public class EditImageTool implements Tool<EditImageParam> {
        private final Long accountId;
        private final Long userId;
        private final MutationExecutor mutationExecutor;
        private final Long imageProfileId;
        public EditImageTool(Long accountId, Long userId, MutationExecutor mutationExecutor) {
            this(accountId, userId, mutationExecutor, null);
        }
        public EditImageTool(Long accountId, Long userId, MutationExecutor mutationExecutor,
                             Long imageProfileId) {
            this.accountId = accountId;
            this.userId = userId;
            this.mutationExecutor = mutationExecutor;
            this.imageProfileId = imageProfileId;
        }
        @Override public String execute(EditImageParam param) {
            return mutationExecutor.execute("edit_image", json(param), () ->
                    json(assetView(imageService.edit(accountId, param.getAssetId(), param.getPrompt(),
                            param.getFilename(), userId, imageProfileId))));
        }
    }

    @Data
    public static class EditImageParam extends ToolParam {
        @Param(description = "要编辑的素材assetId") private Long assetId;
        @Param(description = "需要对图片进行的具体修改，说明要保留和改变的内容") private String prompt;
        @Param(required = false, description = "新图片保存文件名") private String filename;
    }

    private static Map<String, Object> assetView(Asset asset) {
        return Map.ofEntries(
                Map.entry("assetId", asset.getId()),
                Map.entry("publicUrl", asset.getPublicUrl()),
                Map.entry("filename", asset.getOriginalName()),
                Map.entry("contentType", asset.getContentType()),
                Map.entry("sourceType", asset.getSourceType() == null ? "UNKNOWN" : asset.getSourceType()),
                Map.entry("description", asset.getDescription() == null ? "" : asset.getDescription()));
    }

    private static int value(Integer value, int fallback) { return value == null ? fallback : value; }

    private static String json(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("工具结果序列化失败", exception); }
    }
}
