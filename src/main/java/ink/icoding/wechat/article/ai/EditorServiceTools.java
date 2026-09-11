package ink.icoding.wechat.article.ai;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.skill.MarkFlowRenderService;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑器链路的服务端工具（skills-agent-plan 5.10.4 / 第④期）：
 * - render_markflow（RENDER 组）：markdown → renderId，渲染结果存 EditorSession 缓存，**不把 HTML 回灌给 LLM**；
 * - delegate_research（DELEGATE 组）：编辑器对话内委托调研员，结果回注当前对话。
 */
public final class EditorServiceTools {
    private EditorServiceTools() {
    }

    /** 渲染结果缓存（EditorSession 持有，按 renderId 索引）。 */
    public interface RenderCache {
        /** 渲染并缓存，返回 renderId 与元信息。 */
        String render(String markdown, String accent, String dark);

        /** 取缓存 HTML（占位替换用）；未命中返回 null。 */
        String htmlOf(String renderId);
    }

    public static List<Tool> render(RenderCache cache) {
        return List.of(new RenderMarkflowTool(cache));
    }

    /** 编辑器内委托调研员（第④期，结果回注当前对话）。 */
    @FunctionalInterface
    public interface ResearchDelegate {
        String run(String instruction);
    }

    public static List<Tool> delegateResearch(ResearchDelegate delegate) {
        return List.of(new DelegateResearchTool(delegate));
    }

    @ToolInfo(name = "delegate_research", description = "委托调研员检索并核实外部资料，返回结构化调研简报（含来源链接与可引用素材）。适合「先调研再写」的场景：调研结果会出现在本轮对话中，可据此继续写作。调研耗时较长，请一次给出完整的调研要求。")
    public static class DelegateResearchTool implements Tool<DelegateResearchParam> {
        private final ResearchDelegate delegate;

        public DelegateResearchTool(ResearchDelegate delegate) {
            this.delegate = delegate;
        }

        @Override
        public String execute(DelegateResearchParam param) {
            if (param.getInstruction() == null || param.getInstruction().isBlank()) {
                throw new IllegalArgumentException("调研要求不能为空");
            }
            return delegate.run(param.getInstruction());
        }
    }

    @Data
    public static class DelegateResearchParam extends ToolParam {
        @Param(description = "给调研员的完整要求：主题、需要回答的关键问题、时间与地域范围、产出重点")
        private String instruction;
    }

    @ToolInfo(name = "render_markflow", description = "把 MarkFlow 语法 Markdown 渲染为公众号内联样式 HTML，返回 renderId。渲染结果不会直接返回给你，请在 insert_blocks/replace_blocks 的 blocks 中使用 {{render:renderId}} 占位符引用，服务端会自动替换为完整版式。整篇重排或大段替换时优先使用本工具。")
    public static class RenderMarkflowTool implements Tool<RenderMarkflowParam> {
        private final RenderCache cache;

        public RenderMarkflowTool(RenderCache cache) {
            this.cache = cache;
        }

        @Override
        public String execute(RenderMarkflowParam param) {
            if (param.getMarkdown() == null || param.getMarkdown().isBlank()) {
                throw new IllegalArgumentException("markdown 不能为空");
            }
            return cache.render(param.getMarkdown(), param.getAccent(), param.getDark());
        }
    }

    @Data
    public static class RenderMarkflowParam extends ToolParam {
        @Param(description = "MarkFlow 语法 Markdown 正文（组件写法以系统提示中的实时语法指令为准：步骤用 <steps> 标签、超过 3 步须显式 type=\"DA02\"、双栏对比用 :::compare 容器、提示用 > [TIP]、标签徽章用 <badges type=\"accent\">A|B</badges>；不要使用 :::steps 容器与行内 <badge>）")
        private String markdown;
        @Param(required = false, description = "主题主色（6 位 hex，可选；留空由渲染服务派生）") private String accent;
        @Param(required = false, description = "主题深色（6 位 hex，可选；留空由渲染服务派生）") private String dark;
    }

    /** 渲染成功后的返回体（不含 html）。 */
    public static String renderResultJson(MarkFlowRenderService.RenderResult result, String renderId) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("renderId", renderId);
        value.put("title", result.title() == null ? "" : result.title());
        value.put("summary", result.summary() == null ? "" : result.summary());
        Map<String, Object> theme = new LinkedHashMap<>();
        theme.put("accent", result.themeAccent() == null ? "" : result.themeAccent());
        theme.put("dark", result.themeDark() == null ? "" : result.themeDark());
        value.put("theme", theme);
        value.put("message", "渲染完成，请在 blocks 中使用 {{render:" + renderId + "}} 引用版式");
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("渲染结果序列化失败", exception);
        }
    }

    /** 渲染区段的读回占位摘要（防大段 HTML 回灌上下文）。 */
    public static String placeholderSummary(String renderId, int chars) {
        return "已渲染版式块 renderId=" + renderId + "，约 " + chars + " 字，请勿改写其内部 HTML；如需调整版式请重新调用 render_markflow。";
    }
}
