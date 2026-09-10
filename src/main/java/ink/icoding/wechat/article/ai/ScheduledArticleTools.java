package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.article.ArticleContentPolicy;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.MarkFlowRenderService;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Server-side article drafting tools used by unattended scheduled agents.
 * 引擎感知（skills-agent-plan 5.10.4）：PROMPT 引擎行为与原实现完全一致；
 * MARKFLOW 引擎下 save_article_draft 只保存 Markdown（渲染延迟到交付前 renderBeforeDelivery），
 * read 返回 Markdown，LLM 上下文零 HTML。
 */
public final class ScheduledArticleTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScheduledArticleTools() {
    }

    public static List<Tool> all(DraftState state) {
        return List.of(new ReadDraftTool(state), new SaveDraftTool(state), new SetDraftCoverTool(state));
    }

    /** 只读草稿工具（DRAFT_READ 组单独授权时使用，方案 5.3 工具组拆分）。 */
    public static List<Tool> readOnly(DraftState state) {
        return List.of(new ReadDraftTool(state));
    }

    public static final class DraftState {
        private String title;
        private String author;
        private String digest;
        private String contentHtml;
        private String sourceUrl;
        private Long coverAssetId;
        private long documentVersion;
        private boolean saved;
        private LayoutEngine layoutEngine = LayoutEngine.PROMPT;
        private String contentMarkdown;
        private String saveAccent;
        private String saveDark;
        private boolean rendered;

        public DraftState(Long defaultCoverAssetId) {
            this(defaultCoverAssetId, LayoutEngine.PROMPT);
        }

        public DraftState(Long defaultCoverAssetId, LayoutEngine layoutEngine) {
            this.coverAssetId = defaultCoverAssetId;
            this.layoutEngine = layoutEngine == null ? LayoutEngine.PROMPT : layoutEngine;
        }

        public String getContentMarkdown() {
            return contentMarkdown;
        }

        public LayoutEngine layoutEngine() {
            return layoutEngine;
        }

        public synchronized boolean isSaved() {
            return saved;
        }

        public synchronized String title() {
            return title;
        }

        public synchronized String contentHtml() {
            return contentHtml;
        }

        private synchronized String read() {
            return json(view());
        }

        public synchronized String save(SaveDraftParam param) {
            if (param.getTitle() == null || param.getTitle().isBlank()) {
                throw new IllegalArgumentException("文章标题不能为空");
            }
            boolean markflow = layoutEngine == LayoutEngine.MARKFLOW;
            String body = param.getContentHtml();
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException(markflow ? "文章正文（MarkFlow 语法 Markdown）不能为空" : "文章正文不能为空");
            }
            if (param.getTitle().length() > 64) throw new IllegalArgumentException("文章标题不能超过64字");
            if (param.getDigest() != null && param.getDigest().length() > 120) {
                throw new IllegalArgumentException("文章摘要不能超过120字");
            }
            if (markflow) {
                // MARKFLOW：正文是待渲染 Markdown，渲染延迟到交付前；不跑纯段落校验（组件属预期）
                saveAccent = blankToNull(param.getAccent());
                saveDark = blankToNull(param.getDark());
                contentMarkdown = body;
                rendered = false;
                contentHtml = body;
            } else {
                ArticleContentPolicy.requireParagraphProse(body);
                contentMarkdown = null;
                saveAccent = null;
                saveDark = null;
            }
            title = param.getTitle().trim();
            author = blankToNull(param.getAuthor());
            digest = blankToNull(param.getDigest());
            contentHtml = body;
            sourceUrl = blankToNull(param.getSourceUrl());
            saved = true;
            documentVersion++;
            return json(Map.of("message", markflow
                            ? "文章草稿已保存到本轮任务工作区（MarkFlow 语法 Markdown，交付前由系统渲染为公众号 HTML）"
                            : "文章草稿已保存到本轮任务工作区",
                    "engine", layoutEngine.name(), "rendered", rendered, "draft", view()));
        }

        private synchronized String setCover(SetDraftCoverParam param) {
            if (param.getAssetId() == null) throw new IllegalArgumentException("封面素材ID不能为空");
            coverAssetId = param.getAssetId();
            documentVersion++;
            return json(Map.of("message", "文章封面已设置", "assetId", coverAssetId,
                    "documentVersion", documentVersion));
        }

        /** 交付前渲染：MARKFLOW 模式渲染成功才覆盖 contentHtml（失败抛错，Markdown 不丢）；PROMPT 模式无操作。 */
        public synchronized void renderBeforeDelivery(MarkFlowRenderService renderService) {
            if (layoutEngine != LayoutEngine.MARKFLOW || !saved || rendered) return;
            if (renderService == null) {
                throw new IllegalStateException("MARKFLOW 排版需要 MarkFlowRenderService，但当前上下文未提供");
            }
            MarkFlowRenderService.RenderResult result = renderService.render(contentMarkdown, saveAccent, saveDark);
            contentHtml = result.html();
            digest = digest == null || digest.isBlank() ? result.summary() : digest;
            rendered = true;
            documentVersion++;
        }

        public synchronized Draft snapshot() {
            if (!saved) throw new IllegalStateException("智能体没有通过 save_article_draft 提交文章");
            return new Draft(title, author, digest, contentHtml, sourceUrl, coverAssetId, documentVersion,
                    layoutEngine, contentMarkdown, saveAccent, saveDark, rendered);
        }

        /** 采纳一次完整快照（单智能体执行器把 runScheduledAgent 的产出同步回共享工作区）。 */
        public synchronized void adopt(Draft draft) {
            if (draft == null) return;
            this.title = draft.title();
            this.author = draft.author();
            this.digest = draft.digest();
            this.contentHtml = draft.contentHtml();
            this.sourceUrl = draft.sourceUrl();
            this.coverAssetId = draft.coverAssetId();
            this.documentVersion = draft.documentVersion();
            this.layoutEngine = draft.layoutEngine() == null ? LayoutEngine.PROMPT : draft.layoutEngine();
            this.contentMarkdown = draft.contentMarkdown();
            this.saveAccent = draft.themeAccent();
            this.saveDark = draft.themeDark();
            this.rendered = draft.rendered();
            this.saved = true;
        }

        private Map<String, Object> view() {
            java.util.LinkedHashMap<String, Object> value = new java.util.LinkedHashMap<>();
            boolean markflow = layoutEngine == LayoutEngine.MARKFLOW;
            value.put("title", title == null ? "" : title);
            value.put("author", author == null ? "" : author);
            value.put("digest", digest == null ? "" : digest);
            value.put(markflow ? "contentMarkdown" : "contentHtml", contentHtml == null ? "" : contentHtml);
            value.put("sourceUrl", sourceUrl == null ? "" : sourceUrl);
            value.put("coverAssetId", coverAssetId);
            value.put("documentVersion", documentVersion);
            value.put("saved", saved);
            value.put("engine", layoutEngine.name());
            if (markflow) value.put("rendered", rendered);
            return value;
        }
    }

    @ToolInfo(name = "read_article_draft", description = "读取本次定时创作任务当前的文章草稿。需要检查或继续修改已保存草稿时使用。")
    public static class ReadDraftTool implements Tool<ReadDraftParam> {
        private final DraftState state;
        public ReadDraftTool(DraftState state) { this.state = state; }
        @Override public String execute(ReadDraftParam param) { return state.read(); }
    }

    @Data
    public static class ReadDraftParam extends ToolParam {
        @Param(required = false, description = "读取草稿的原因") private String reason;
    }

    @ToolInfo(name = "save_article_draft", description = "把完整文章保存到本次任务工作区。研究和整理完成后必须调用；再次调用会原子覆盖上一版草稿。正文必须使用系统提示中的公众号视觉模板生成完整内联样式HTML，以01、02等居中章节号、绿色短横线、居中章节标题和自然段组织内容，禁止使用ul、ol、dl或table；可引用素材工具返回的publicUrl插入图片。")
    public static class SaveDraftTool implements Tool<SaveDraftParam> {
        private final DraftState state;
        public SaveDraftTool(DraftState state) { this.state = state; }
        @Override public String execute(SaveDraftParam param) { return state.save(param); }
    }

    @Data
    public static class SaveDraftParam extends ToolParam {
        @Param(description = "完整文章标题，最多64字") private String title;
        @Param(required = false, description = "文章作者") private String author;
        @Param(required = false, description = "文章摘要，最多120字") private String digest;
        @Param(description = "完整文章正文HTML；严格使用系统提示中的公众号视觉模板及内联样式，使用居中章节号、章节标题和p自然段组织行文，不得包含项目符号列表、编号列表、定义列表或表格") private String contentHtml;
        @Param(required = false, description = "最主要的参考来源URL；多个来源应在正文末尾列出") private String sourceUrl;
        @Param(required = false, description = "主题主色（6位hex，仅渲染式排版且主题策略为自动时提供，依据系统提示中的主题对照表就近选择）") private String accent;
        @Param(required = false, description = "主题深色（6位hex，仅渲染式排版且主题策略为自动时提供；未提供时由渲染服务自动派生）") private String dark;
    }

    @ToolInfo(name = "set_article_draft_cover", description = "把素材库图片设置为本次定时创作文章的封面。assetId必须来自默认封面、素材库检索、网络图片导入或图片生成/编辑工具。")
    public static class SetDraftCoverTool implements Tool<SetDraftCoverParam> {
        private final DraftState state;
        public SetDraftCoverTool(DraftState state) { this.state = state; }
        @Override public String execute(SetDraftCoverParam param) { return state.setCover(param); }
    }

    @Data
    public static class SetDraftCoverParam extends ToolParam {
        @Param(description = "封面素材assetId") private Long assetId;
    }

    public record Draft(String title, String author, String digest, String contentHtml,
                        String sourceUrl, Long coverAssetId, long documentVersion,
                        LayoutEngine layoutEngine, String contentMarkdown, String themeAccent,
                        String themeDark, boolean rendered) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("定时文章工具结果序列化失败", error);
        }
    }
}
