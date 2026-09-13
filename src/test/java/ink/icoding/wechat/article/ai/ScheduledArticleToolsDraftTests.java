package ink.icoding.wechat.article.ai;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.MarkFlowRenderService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交付前渲染（{@code DraftState.renderBeforeDelivery}）的主题留存。
 *
 * <p>此前它只取渲染结果的 html/summary，把请求里的 accent/dark 原样当「主题」往落库层传。
 * 模型没显式给色时请求值就是 null，而渲染服务会自己派生一组——只存 null 的话，
 * 库里记的主题与正文实际配色不是一回事，日后拿留存 Markdown 重排就换成另一套色。
 */
class ScheduledArticleToolsDraftTests {
    private static final String MARKDOWN = "# 标题\n\n正文。";

    private static ScheduledArticleTools.DraftState markflowDraft(String accent, String dark) {
        ScheduledArticleTools.DraftState state = new ScheduledArticleTools.DraftState(null, LayoutEngine.MARKFLOW);
        ScheduledArticleTools.SaveDraftParam param = new ScheduledArticleTools.SaveDraftParam();
        param.setTitle("测试文章");
        param.setContentHtml(MARKDOWN);
        param.setAccent(accent);
        param.setDark(dark);
        state.save(param);
        return state;
    }

    /** 渲染服务回报的实际生效主题必须写进快照，而不是保留「请求里传了什么」。 */
    @Test
    void renderBeforeDeliveryStoresThemeReportedByRenderer() {
        MarkFlowRenderService renderService = mock(MarkFlowRenderService.class);
        when(renderService.render(eq(MARKDOWN), eq(null), eq(null)))
                .thenReturn(new MarkFlowRenderService.RenderResult("<section>渲染产物</section>", "标题", "摘要",
                        "#0984e3", "#0652dd"));
        ScheduledArticleTools.DraftState state = markflowDraft(null, null);

        state.renderBeforeDelivery(renderService);

        ScheduledArticleTools.Draft draft = state.snapshot();
        assertThat(draft.contentHtml()).isEqualTo("<section>渲染产物</section>");
        assertThat(draft.themeAccent()).isEqualTo("#0984e3");
        assertThat(draft.themeDark()).isEqualTo("#0652dd");
    }

    /** 上游没回报主题时退回请求值：不能因为「没回报」就把已有主题写成 null。 */
    @Test
    void renderBeforeDeliveryKeepsRequestedThemeWhenRendererReportsNone() {
        MarkFlowRenderService renderService = mock(MarkFlowRenderService.class);
        when(renderService.render(eq(MARKDOWN), eq("#0984e3"), eq("#0652dd")))
                .thenReturn(new MarkFlowRenderService.RenderResult("<section>渲染产物</section>", "标题", "摘要",
                        null, null));
        ScheduledArticleTools.DraftState state = markflowDraft("#0984e3", "#0652dd");

        state.renderBeforeDelivery(renderService);

        ScheduledArticleTools.Draft draft = state.snapshot();
        assertThat(draft.themeAccent()).isEqualTo("#0984e3");
        assertThat(draft.themeDark()).isEqualTo("#0652dd");
    }

    /** 已渲染过就不要再渲染一次（SINGLE 链路已在会话内渲染，PIPELINE 交付前不得重复调用付费渲染）。 */
    @Test
    void renderBeforeDeliveryIsIdempotent() {
        MarkFlowRenderService renderService = mock(MarkFlowRenderService.class);
        when(renderService.render(any(), any(), any()))
                .thenReturn(new MarkFlowRenderService.RenderResult("<section>渲染产物</section>", "标题", "摘要",
                        "#0984e3", null));
        ScheduledArticleTools.DraftState state = markflowDraft(null, null);

        state.renderBeforeDelivery(renderService);
        state.renderBeforeDelivery(renderService);

        verify(renderService).render(eq(MARKDOWN), eq(null), eq(null));
    }

    /** PROMPT 排版的草稿不走渲染服务（没有 Markdown 源文）。 */
    @Test
    void renderBeforeDeliverySkipsPromptDraft() {
        MarkFlowRenderService renderService = mock(MarkFlowRenderService.class);
        ScheduledArticleTools.DraftState state = new ScheduledArticleTools.DraftState(null, LayoutEngine.PROMPT);
        ScheduledArticleTools.SaveDraftParam param = new ScheduledArticleTools.SaveDraftParam();
        param.setTitle("测试文章");
        param.setContentHtml("<p>正文。</p>");
        state.save(param);

        state.renderBeforeDelivery(renderService);

        verify(renderService, never()).render(any(), any(), any());
        assertThat(state.snapshot().contentHtml()).isEqualTo("<p>正文。</p>");
    }

    /**
     * 渲染式排版任务下发给模型的 save_article_draft **必须**是 Markdown 语义的那一份。
     *
     * <p>工具说明比系统提示更具体，是模型实际遵循的那一份。此前 MARKFLOW 任务复用了 PROMPT 版的说明
     * （「生成完整内联样式HTML…禁止 ul/ol/dl/table」），模型照它写 HTML，渲染服务对 HTML 原样透传：
     * 精排版式一次都没生效，颜色写死在 HTML 里导致换主题色也无效。断言的是**送给模型的 schema 文本**，
     * 不是内部字段，这样「说明与引擎不一致」这类退化能被这条测试挡住。
     */
    @Test
    void markflowTaskShipsMarkdownSaveToolSchema() {
        ToolDescriptor schema = saveToolSchema(LayoutEngine.MARKFLOW);

        assertThat(schema.getName()).isEqualTo("save_article_draft");
        assertThat(schema.getDescription())
                .contains("MarkFlow 语法 Markdown")
                .doesNotContain("内联样式HTML");
        assertThat(schema.getParams()).extracting(ToolDescriptor.ParamInfo::getName)
                .contains("contentMarkdown")
                .doesNotContain("contentHtml");
        ToolDescriptor.ParamInfo body = schema.getParams().stream()
                .filter(param -> "contentMarkdown".equals(param.getName())).findFirst().orElseThrow();
        assertThat(body.getDescription()).contains("MarkFlow 语法 Markdown").contains("不是 HTML");
    }

    /** 指令式排版任务的工具说明保持原样：改这里等于改变 PROMPT 引擎的产出契约。 */
    @Test
    void promptTaskKeepsHtmlSaveToolSchema() {
        ToolDescriptor schema = saveToolSchema(LayoutEngine.PROMPT);

        assertThat(schema.getDescription()).contains("内联样式HTML");
        assertThat(schema.getParams()).extracting(ToolDescriptor.ParamInfo::getName)
                .contains("contentHtml")
                .doesNotContain("contentMarkdown");
    }

    /** MARKFLOW 版工具提交的正文必须原样成为重排依据（content_markdown），而不是只停在内存里。 */
    @Test
    void markflowSaveToolStoresSubmittedMarkdownAsLayoutSource() {
        ScheduledArticleTools.DraftState state = new ScheduledArticleTools.DraftState(null, LayoutEngine.MARKFLOW);
        ScheduledArticleTools.SaveMarkflowDraftParam param = new ScheduledArticleTools.SaveMarkflowDraftParam();
        param.setTitle("测试文章");
        param.setContentMarkdown("<p-title number=\"01\" title=\"小标题\"></p-title>\n\n正文。");
        param.setAccent("#0984e3");

        state.save(param);

        ScheduledArticleTools.Draft draft = state.snapshot();
        assertThat(draft.contentMarkdown()).contains("<p-title number=\"01\"");
        assertThat(draft.themeAccent()).isEqualTo("#0984e3");
    }

    /**
     * 「手写模板 HTML」判据必须能把它与 MarkFlow 组件语法分开：前者会让渲染服务原样透传
     * （精排失效、主题色换不动），后者才是渲染式排版的输入；误判会让正常的渲染式任务被刷满告警。
     */
    @Test
    void handWrittenHtmlDetectorSeparatesTemplateHtmlFromMarkflowSyntax() {
        assertThat(ScheduledArticleTools.looksLikeHandWrittenHtml(
                "<div style=\"max-width: 100%; color: #0984e3;\">\n<p style=\"margin-bottom:16px;\">正文</p></div>"))
                .as("PROMPT 引擎的视觉模板 HTML")
                .isTrue();
        assertThat(ScheduledArticleTools.looksLikeHandWrittenHtml(
                "<p-title number=\"01\" title=\"小标题\" subtitle=\"SUBTITLE\"></p-title>\n\n- 列表项\n\n正文。"))
                .as("MarkFlow 组件标签不带 style 属性")
                .isFalse();
    }

    /** 取工具组里 save_article_draft 的 schema——即模型真正看到的那份说明。 */
    private static ToolDescriptor saveToolSchema(LayoutEngine engine) {
        ScheduledArticleTools.DraftState state = new ScheduledArticleTools.DraftState(null, engine);
        return ScheduledArticleTools.all(state).stream()
                .map(tool -> ToolDescriptor.fromTool((Tool) tool))
                .filter(schema -> "save_article_draft".equals(schema.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("工具组里没有 save_article_draft"));
    }

    /** 渲染降级警告必须能随快照传到运行终态：产物里留着未识别的语法就是「版式没有 100% 复刻」。 */
    @Test
    void renderWarningsTravelWithTheDraftSnapshot() {
        MarkFlowRenderService renderService = mock(MarkFlowRenderService.class);
        when(renderService.render(any(), any(), any()))
                .thenReturn(new MarkFlowRenderService.RenderResult("<section>渲染产物</section>", "标题", "摘要",
                        "#27ae60", "#1e8449",
                        java.util.List.of("容器语法 :::compare 未被识别（多半是缺少收尾的 :::），已作为正文文字输出")));
        ScheduledArticleTools.DraftState state = markflowDraft(null, null);

        state.renderBeforeDelivery(renderService);

        assertThat(state.renderWarnings()).hasSize(1);
        assertThat(state.snapshot().renderWarnings().get(0)).contains(":::compare");
        // 重新保存必须清掉上一版的警告，否则「改好了」也会被记成有降级
        ScheduledArticleTools.SaveDraftParam again = new ScheduledArticleTools.SaveDraftParam();
        again.setTitle("测试文章");
        again.setContentHtml(MARKDOWN);
        state.save(again);
        assertThat(state.renderWarnings()).isEmpty();
    }

    /**
     * 保存时就要把「渲染器一定识别不了」的写法告诉模型：渲染发生在交付前，那时模型已无修正机会，
     * 而工具结果是模型一定会读到的下一段输入。判据全部来自 2026-09-12 对渲染 API 的实测。
     */
    @Test
    void markflowSyntaxHintsCatchUnrenderableSyntax() {
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::compare\n维度 | A 方描述 | B 方描述 | accent\n:::\n\n<steps>\n第一步。\n\n第二步。\n"))
                .as("规范写法不报问题")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::compare\n维度 | A 方描述 | B 方描述 | accent\n"))
                .as("未闭合的容器会把后续整段内容吞掉并字面输出")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("缺少收尾"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(":::steps\n### 第一步\n正文\n:::\n"))
                .as(":::steps 不是 guide 定义的语法，步骤流要用 <steps> 标签")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("<steps>"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints("  :::compare\n维度 | A | B | accent\n  :::\n"))
                .as("不顶格不会被识别")
                .allSatisfy(hint -> assertThat(hint).doesNotContain("缺少收尾"))
                .anySatisfy(hint -> assertThat(hint).contains("顶格"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<timeline>\n2026年01月 | 项目启动 | 完成需求分析\n</timeline>\n"))
                .as("<timeline> 标签写三列同样能渲染，不该报问题")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints("<timeline>\n2026年01月 | 第一件事\n</timeline>\n"))
                .as("<timeline> 标签缺列时整块渲染为空，与容器写法同一条规则")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("三列"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "```\n:::compare\n维度 | A | B | accent\n```\n\n正常正文。\n"))
                .as("代码块里的语法示例是正文内容，不是真的容器")
                .isEmpty();
    }

    /**
     * 渲染器支持的容器不止 {@code :::compare}：{@code :::tip} 等六种提示框实测都会渲染成
     * 与 {@code > [TIP]} 相同的样式。**这条用例是防回归的**——此前判据写死只认 compare，
     * 会把正确的 {@code :::tip} 误报成「不支持的容器语法」，把模型从一条好写法上劝退。
     */
    @Test
    void containerCheckAcceptsEveryContainerTheRendererSupports() {
        for (String container : new String[]{"compare", "tip", "note", "info", "warning", "caution", "important"}) {
            // compare 的每一行有列数要求（见 compareRowsWithWrongColumnCount），这里给一份合法内容，
            // 好让这条用例只回答「容器本身认不认」这一个问题。
            String body = "compare".equals(container)
                    ? "维度 | 甲 | 乙 | accent\n价格 | 高 | 低 |\n"
                    : "内容一行。\n";
            assertThat(ScheduledArticleTools.markflowSyntaxHints(
                    ":::" + container + "\n" + body + ":::\n"))
                    .as(":::%s 是渲染器支持的容器", container)
                    .isEmpty();
        }
        // 反面：不在支持列表里的容器要照旧报出来，并说清支持哪些
        assertThat(ScheduledArticleTools.markflowSyntaxHints(":::danger\n内容一行。\n:::\n"))
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains(":::danger").contains(":::compare"));
    }

    /**
     * guide 漏写、但渲染器确实支持的容器组件必须放行，否则自检会把**正确写法**报成错误。
     *
     * <p>清单来自 MarkFlow Web 端组件库（`/markflow/` 的前端 bundle 里那份组件注册表），
     * 每一条都用注册表自带的官方示例打了真实渲染 API：阅读路线出编号圆点导航、
     * 横向/纵向步骤流出步骤卡、案例流出案例条、轮播图出带 {@code <animateTransform>} 的 SVG、
     * 提示卡出彩色提示框、表格容器出带标题的卡片表格、对齐容器出居中段落。
     * 这些名字自带连字符，因此也可以反证容器名解析没有把 {@code steps-horizontal} 截成 {@code steps}。
     */
    @Test
    void containerCheckAcceptsComponentsTheGuideNeverMentions() {
        String[][] cases = {
                {"reading-path", ":::reading-path\n- 问题定义 | 为什么读者 3 秒就走\n- 模块原理 | 61 个组件\n:::\n"},
                {"steps-horizontal", ":::steps-horizontal label=\"HOW IT WORKS\" title=\"三步上手\"\n- 写作 | 完成正文\n- 发布 | 复制到公众号\n:::\n"},
                {"steps-vertical", ":::steps-vertical title=\"竖向步骤流\"\n- 注册账号 | 填写信息\n- 开始使用 | 选择模块\n:::\n"},
                {"case-flow", ":::case-flow\n- [案例 01] 从零搭建个人知识库\n- [案例 02] 用 AI 辅助写周报\n:::\n"},
                {"slider", ":::slider images=\"https://example.com/a.png,https://example.com/b.png\" interval=\"3\"\n:::\n"},
                {"callout", ":::callout type=\"tip\" title=\"排版小技巧\"\n信息型内容用正文模块。\n:::\n"},
                {"align", ":::align align=\"center\"\n居中引用的一句话。\n:::\n"},
                {"code-block", ":::code-block lang=\"js\" title=\"示例\"\n```js\nconst a = 1\n```\n:::\n"},
                {"breaking", ":::breaking badge=\"NEW\" title=\"标题\" subtitle=\"副标题\"\n正文一句。\n:::\n"},
                {"timeline", ":::timeline\n- 2024年01月 | 项目启动 | 完成需求分析\n- 2024年06月 | 一期上线 | 核心功能发布\n:::\n"},
                {"table", ":::table style=\"card\"\n| 甲 | 乙 |\n| --- | --- |\n| 一 | 二 |\n:::\n"},
        };
        for (String[] item : cases) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(item[1]))
                    .as(":::%s 渲染器支持（guide 漏写），不该被自检报成错误", item[0])
                    .isEmpty();
        }
    }

    /**
     * 内容会**静默消失**的两种写法必须在保存时拦下：{@code :::case-flow} 的行少了 {@code [标签]}、
     * {@code :::timeline} 的行不足三列，实测整块产物都是 0 字符（HTTP 200、ok:true、上游不报 warnings）。
     * 渲染发生在交付前，模型届时已无修正机会，所以判据要放在保存这一刻。
     *
     * <p>{@code :::reading-path} 是同一类：**只要有一行不带 `-` 列表符号**（整块换成 `*` 也算），
     * 导航区就整块消失（实测 0 字符、零 warnings）——而它正是技能新补的那批组件之一，
     * 不拦的话模型会照「自然段」的直觉写出一个渲染为空的花架子。
     */
    @Test
    void containerRowsThatRenderToNothingAreReported() {
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::case-flow\n- [案例 01] 从零搭建个人知识库\n- [案例 02] 用 AI 辅助写周报\n:::\n"))
                .as("带 [标签] 的案例行是正确写法")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::case-flow\n- 从零搭建个人知识库\n- 用 AI 辅助写周报\n:::\n"))
                .as("少了 [标签] 整块渲染为空")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("case-flow").contains("[标签]"));

        // 第八轮补：真正的判据是**行首的 `-`**，不是「有没有 `[标签]`」——旧判据把 `-` 写成可选
        // （`^\s*[-*]?\s*\[`），恰好放过了下面这四种实测产物为 0 字符的写法。
        // 证据与产物几何见 `target/probe/round8_caseflow_bullet.txt`（容器式 / 标签式各打一遍，结论一致）。
        // （行首 `- ` 的正确写法由本方法第一条断言守着，不进这个循环）
        for (String marker : new String[]{"* ", "+ ", "1. ", ""}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(
                    ":::case-flow\n" + marker + "[案例 01] 标题甲 | 描述甲\n"
                            + marker + "[案例 02] 标题乙 | 描述乙\n:::\n"))
                    .as("行首写成「%s」时整块渲染为空（实测 0 字符）", marker.isEmpty() ? "（没有符号）" : marker.trim())
                    .isNotEmpty()
                    .anySatisfy(hint -> assertThat(hint).contains("case-flow"));
        }

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::case-flow\n  - [案例 01] 标题甲 | 描述甲\n  - [案例 02] 标题乙 | 描述乙\n:::\n"))
                .as("行首缩进两格后只剩标签栏、内容丢失（实测 547 字符 / 可见文字 9 个），同样要报")
                .isNotEmpty()
                .anySatisfy(hint -> assertThat(hint).contains("case-flow"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<case-flow label=\"案例\">\n- [案例 01] 标题甲\n- [案例 02] 标题乙\n</case-flow>\n"))
                .as("标签式的行格式与容器式完全相同，正确写法不报")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<case-flow label=\"案例\">\n[案例 01] 标题甲\n[案例 02] 标题乙\n</case-flow>\n"))
                .as("标签式同样要求行首的 `-`，漏了整块 0 字符")
                .isNotEmpty()
                .anySatisfy(hint -> assertThat(hint).contains("case-flow"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::timeline\n- 2024年01月 | 项目启动 | 完成需求分析\n:::\n"))
                .as("三列时间线是正确写法")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::timeline\n- 2024年01月 | 项目启动\n:::\n"))
                .as("两列的行会被整行忽略")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("timeline").contains("三列"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::reading-path\n- 章节甲 | 一句话说明\n- 章节乙 | 一句话说明\n:::\n"))
                .as("带 `-` 列表符号的导航行是正确写法（竖线可省）")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::reading-path\n- 章节甲 | 一句话说明\n章节乙 | 一句话说明\n:::\n"))
                .as("只要一行不带 `-`，整块导航渲染为空")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("reading-path"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::reading-path\n* 章节甲 | 一句话说明\n* 章节乙 | 一句话说明\n:::\n"))
                .as("换成 `*` 列表符号同样渲染为空（渲染器只认 `-`）")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("reading-path"));
    }

    /**
     * `:::slider` 少了 `images` 不算「渲染为空」，但更难看：渲染器原样输出一个灰底提示框
     * 「请提供图片URL列表」（实测产物 165 字符就是那个框），那句话会直接留在成稿里。
     */
    @Test
    void sliderWithoutImagesIsReported() {
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::slider images=\"https://example.com/a.png,https://example.com/b.png\" interval=\"3\"\n:::\n"))
                .as("给出图片直链是正确写法")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(":::slider\n轮播说明文字\n:::\n"))
                .as("没给 images 会渲染出「请提供图片URL列表」提示框")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("slider").contains("images"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::slider images=\"https://example.com/a.png\"\n:::\n"))
                .as("单张图也是合法写法（实测渲染成一张 img）")
                .isEmpty();
    }

    /**
     * 标签式 `<slider …>` **必须成对**：少了 `</slider>`（自闭合 `/` 顶不了事）整行标签连同属性
     * 原样留在 `<p>` 里，成稿里就是一行裸标签（实测 356 字符、0 个 `<svg>`、HTTP 200、`ok:true`、
     * 零 warnings）。上游缺陷 R4 记的就是这一条，而本仓库原先的「绕过」只改了技能提示——
     * 保存自检根本不看标签式，模型真写了也收不到任何提示，等于没有绕过。
     *
     * <p>判据是「有没有闭合标签」而不是「有没有自闭合斜杠」：实测 `<slider …/></slider>`
     * （斜杠 + 闭合标签同时在）能正常出 1141 字符的 SVG 轮播，按斜杠判会误报。
     * 成对写法与单图成对写法都必须放行。
     */
    @Test
    void sliderTagWithoutClosingTagIsReported() {
        for (String open : new String[]{
                "<slider images=\"https://example.com/a.png,https://example.com/b.png\" interval=\"3\" />",
                "<slider images=\"https://example.com/a.png,https://example.com/b.png\" interval=\"3\"/>",
                "<slider images=\"https://example.com/a.png,https://example.com/b.png\" interval=\"3\">"}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(open + "\n"))
                    .as("标签式 slider 没有闭合标签时会被当普通正文原样透传：%s", open)
                    .hasSize(1)
                    .allSatisfy(hint -> assertThat(hint).contains("slider").contains(":::slider"));
        }

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<slider images=\"https://example.com/a.png,https://example.com/b.png\" interval=\"3\">\n</slider>\n"))
                .as("成对写法实测能出完整 SVG 轮播，不该报")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<slider images=\"https://example.com/a.png,https://example.com/b.png\" interval=\"3\"/></slider>\n"))
                .as("斜杠与闭合标签同时在的写法实测也能渲染，只能按闭合标签判")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<slider images=\"https://example.com/a.png\">\n</slider>\n"))
                .as("单图成对写法渲染成一张 img，不报")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::slider images=\"https://example.com/a.png,https://example.com/b.png\" interval=\"3\"\n:::\n"))
                .as("容器式是本项目要求的写法，不报")
                .isEmpty();
    }

    /**
     * `:::compare` 的行只接受 3–4 列：5 列及以上、2 列及以下会被整行忽略，少列时末尾那一方的
     * **整列内容一起丢**（实测 2 列时对比方「乙」的文字在产物里完全找不到，且 `ok:true`）。
     * 上游只在 `meta.warnings` 里说一句，而渲染发生在交付前——那时模型已经没机会改。
     */
    @Test
    void compareRowsWithTheWrongColumnCountAreReported() {
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::compare\n维度 | 甲 | 乙 | accent\n价格 | 高 | 低 |\n:::\n"))
                .as("4 列是 guide 记载的写法")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::compare\n维度 | 甲 | 乙\n价格 | 高 | 低\n:::\n"))
                .as("3 列（省略强调标记）实测也能接受，不报")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::compare\n维度 | 甲 | 乙 | accent | 备注\n价格 | 高 | 低 | accent | 无\n:::\n"))
                .as("5 列的行会被整行忽略")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("compare").contains("4 列"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::compare\n维度 | 甲\n价格 | 高\n:::\n"))
                .as("2 列时对比方整列内容丢失")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("compare"));
    }

    /**
     * 只有容器写法的组件被写成 XML 标签时必须在保存时拦下：渲染器不认这种标签，**标签名会原样留在正文里**
     * （实测 2026-09-13：`<compare>` 的产物是 `<p style="…"><compare></p>`，HTTP 200、`ok:true`、上游无
     * warnings）。guide 第六节第 6 条特意提醒过「双栏对比用 `:::compare`，不是 `<compare>` 标签」，
     * 而保存侧此前没有任何提示——同一轮里也没有第二次机会（渲染发生在交付前）。
     *
     * <p>反面同样要守住：`<slider>` / `<align>` / `<breaking>` / `<steps>` / `<timeline>` / `<case-flow>`
     * **有**标签形式（实测能渲染出 SVG 轮播、居中段落、开篇大卡等），把它们也算进来的话，
     * 自检会把正确写法报成错误——这正是 F9 的教训。
     *
     * <p>`hint` 是第八轮补的（`target/probe/round8_na_discriminator.txt`、`round8_unknown_tags.txt`）：
     * `:::hint` 渲染成蓝色信息卡（与 `:::info` 同一套产物），`<hint>…</hint>` 的标签原样留在正文里，
     * 正是「有容器、没标签」的第 7 个。
     */
    @Test
    void containerOnlyComponentsWrittenAsTagsAreReported() {
        for (String name : new String[]{"compare", "reading-path", "steps-horizontal", "steps-vertical",
                "callout", "code-block", "hint"}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(
                    "<" + name + ">\n内容一行。\n</" + name + ">\n"))
                    .as("<%s> 没有标签形式，应当报出来", name)
                    .hasSize(1)
                    .allSatisfy(hint -> assertThat(hint).contains(":::" + name));
        }

        // 正确写法与「有标签形式的那些」都不能被误报
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::compare\n维度 | 甲 | 乙 | accent\n价格 | 高 | 低 |\n:::\n"))
                .as("容器写法是正确写法")
                .isEmpty();
        for (String allowed : new String[]{
                "<slider images=\"https://example.com/a.png\">\n</slider>\n",
                "<align align=\"center\">\n居中一句。\n</align>\n",
                "<breaking badge=\"NEW\" title=\"标题\">\n正文一句。\n</breaking>\n",
                "<steps label=\"HOW IT WORKS\" title=\"三步\">\n- 写作 | 完成正文\n- 发布 | 复制到公众号\n</steps>\n"}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(allowed))
                    .as("%s 有标签形式，不该被报成错误", allowed.lines().findFirst().orElse(""))
                    .isEmpty();
        }
    }

    /**
     * `layout-*` 家族（hero / toc / metrics / cards / part / label-title / infographic / compare /
     * steps / timeline / checklist / stat-row / verdict / myth-fact / image-annotate / audience-fit …共 38 个）
     * 的**容器式与标签式渲染器都不认**：实测 38 个名字 × 2 种写法共 76 组全部把语法原样留在产物里，
     * HTTP 200、`ok:true`、`meta.warnings` 为空（见 `target/probe/round10_registry_closure.txt`；
     * 第八轮先测了其中 16 个名字 / 32 组，见 `round8_unknown_tags.txt`）。
     *
     * <p>这一族来自 MarkFlow Web 端另一套组件库，服务端渲染器一个都没实现，所以按前缀整族拦下，
     * 不做逐个名字打补丁。容器侧（`SkillSeeder` 的禁写名单）同理只写前缀 `layout-*` 而不逐个列名。
     *
     * <p>反面要守住：本项目真正支持的那些标签一个都不能被这条规则误伤。
     */
    @Test
    void unsupportedLayoutFamilyIsReported() {
        for (String name : new String[]{"layout-hero", "layout-toc", "layout-metrics", "layout-cards",
                "layout-steps", "layout-timeline", "layout-checklist", "layout-image-annotate"}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints("<" + name + ">甲 乙</" + name + ">\n"))
                    .as("<%s> 渲染器不认，标签会原样留在正文里", name)
                    .hasSize(1)
                    .allSatisfy(hint -> assertThat(hint).contains("layout-"));
        }

        assertThat(ScheduledArticleTools.markflowSyntaxHints(":::layout-hero\n甲 乙\n:::\n"))
                .as("容器式由通用的「不支持的容器」提示兜底")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("layout-hero"));

        for (String allowed : new String[]{
                ":::reading-path\n- 章节甲 | 一句话说明\n:::\n",
                ":::steps-horizontal label=\"HOW IT WORKS\"\n- 名称 | 描述\n:::\n",
                ":::timeline\n- 2026年01月 | 启动 | 完成需求\n:::\n"}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(allowed))
                    .as("%s 是核实过的正确写法", allowed.lines().findFirst().orElse(""))
                    .isEmpty();
        }
    }

    /**
     * `:::compare` 第 4 格（强调标记）的取值白名单（实测 2026-09-13，见 `probe_compare_marker.py`
     * → `compare_marker_probe.txt`）：只认 `accent` / `default` / 留空，**写中文或别的英文词都会被判坏行**。
     *
     * <p>为什么值得单独立一条：run#81 / run#82 连续两轮的 `renderWarnings` 都是
     * 「compare 有 1 行列数不是「维度 | A方 | B方 | accent|default」」，而两篇稿子表头第 4 格写的
     * 正是「强调」——**那是照着本项目技能提示里的占位词抄的**，属 D28 同类问题：
     * 我们自己的提示词教出了一个会被渲染器判坏、并且被记成降级警告的写法。
     *
     * <p>旧判据只看列数（3–4），这一格因为「4 列」而通过，所以两轮都没拦住。
     */
    @Test
    void compareMarkerColumnOnlyAcceptsAccentOrDefault() {
        for (String bad : new String[]{"强调", "高亮", "highlight", "strong"}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(
                    ":::compare\n维度 | 甲 | 乙 | " + bad + "\n价格 | 高 | 低 | accent\n:::\n"))
                    .as("第 4 格写「%s」会被判坏行整行忽略，保存时就要报出来", bad)
                    .isNotEmpty()
                    .anySatisfy(hint -> assertThat(hint).contains("accent").contains("default"));
        }

        for (String allowed : new String[]{
                ":::compare\n维度 | 甲 | 乙 | accent\n价格 | 高 | 低 | default\n:::\n",
                ":::compare\n维度 | 甲 | 乙 | ACCENT\n价格 | 高 | 低 | Default\n:::\n",
                ":::compare\n维度 | 甲 | 乙 |\n价格 | 高 | 低 | accent\n:::\n",
                ":::compare\n维度 | 甲 | 乙\n价格 | 高 | 低\n:::\n"}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(allowed))
                    .as("accent / default / 留空都是合法形态：%s", allowed.lines().skip(1).findFirst().orElse(""))
                    .isEmpty();
        }
    }

    /**
     * 给渲染器**不输出** `title=` 的四个容器写了标题时要报（实测 2026-09-13，见 `attr_probe3.txt`）：
     * `:::table` / `:::timeline` / `:::slider` / `:::compare` 的 `title="…"` 里的文字在产物里一个字都没有，
     * 上游不报错、不 warning——run#76 就是这么背了一条「组件属性里的文字找不到」的降级告警。
     *
     * <p>反面要守住：`:::callout` 的 `title`、`:::steps-horizontal` 的 `label/title/hint`、
     * `:::code-block` 的 `title` **都会渲染**，报它们就是误报。
     */
    @Test
    void titlesOnContainersThatDropThemAreReported() {
        String[][] cases = {
            {"table", ":::table title=\"容器标题\"\n| 列甲 | 列乙 |\n| --- | --- |\n| 值一 | 值二 |\n:::\n"},
            {"timeline", ":::timeline title=\"容器标题\"\n- 2024年01月 | 项目启动 | 完成需求分析\n:::\n"},
            {"slider", ":::slider images=\"https://example.com/a.png\" title=\"容器标题\"\n:::\n"},
            {"compare", ":::compare title=\"容器标题\"\n维度 | 甲 | 乙 | accent\n方向 | 上 | 下 |\n:::\n"},
        };
        for (String[] item : cases) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(item[1]))
                    .as(":::%s 的 title 渲染器不输出，应当报出来", item[0])
                    .isNotEmpty()
                    .anySatisfy(hint -> assertThat(hint).contains(":::" + item[0]).contains("不输出"));
        }

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                ":::table style=\"card\"\n| 列甲 | 列乙 |\n| --- | --- |\n| 值一 | 值二 |\n:::\n"))
                .as("不带 title 的表格容器是正确写法")
                .isEmpty();
        for (String allowed : new String[]{
                ":::callout type=\"tip\" title=\"会渲染的标题\"\n正文一句。\n:::\n",
                ":::steps-horizontal label=\"HOW IT WORKS\" title=\"从草稿到发布\" hint=\"按顺序完成\"\n"
                        + "- 甲 | 说明A\n- 乙 | 说明B\n:::\n",
                ":::code-block lang=\"js\" title=\"会渲染的代码标题\"\n```js\nconst a = 1;\n```\n:::\n"}) {
            assertThat(ScheduledArticleTools.markflowSyntaxHints(allowed))
                    .as("%s 的 title 会渲染，不该被报成错误", allowed.lines().findFirst().orElse(""))
                    .isEmpty();
        }
    }

    /** 表格后不空行的写法要报：紧随表格的正文会被系统当成表格注释（guide 第六节第 10 条）。 */
    @Test
    void textRightAfterATableIsReported() {
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "| 列甲 | 列乙 |\n| --- | --- |\n| 值一 | 值二 |\n\n紧随其后的正文。\n"))
                .as("空一行才是正确写法")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "| 列甲 | 列乙 |\n| --- | --- |\n| 值一 | 值二 |\n紧随其后的正文。\n"))
                .as("紧贴表格的正文会被当成表格注释")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("空一行"));
    }

    /** 图注与图片之间隔空行**不影响**识别（实测两种写法产物逐字节相同），不该报问题。 */
    @Test
    void captionSpacingIsNotReported() {
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "![配图](https://example.com/a.png)\n图 1: 配图说明\n"))
                .isEmpty();
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "![配图](https://example.com/a.png)\n\n图 1: 配图说明\n"))
                .as("空行与否都渲染成 data-caption-kind=\"image\" 的居中图注")
                .isEmpty();
    }

    /**
     * `<steps>` 里写 Markdown 小标题是实测踩过的坑：渲染器不把它当标题，
     * 产物扁平文本是「1###第一步：准备2准备工作的正文。」——`###` 字面留在正文，这一步还被拆成两个格子。
     */
    @Test
    void stepHeadingsAndPairedSelfClosingTagsAreReported() {
        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<steps>\n### 第一步：准备\n准备工作的正文。\n\n第二步。\n</steps>\n"))
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("<steps>").contains("###"));

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<steps>\n第一步。\n\n第二步。\n</steps>\n\n## 下面是一个正常的二级标题\n"))
                .as("块外的 Markdown 标题不受影响")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints(
                "<badge type=\"tip\" title=\"推荐\" />\n\n<icon name=\"material-symbols:star\" />\n"))
                .as("自闭合写法是正确的")
                .isEmpty();

        assertThat(ScheduledArticleTools.markflowSyntaxHints("<badge type=\"tip\">推荐</badge>\n"))
                .as("成对写法会把 </badge> 吐进正文")
                .hasSize(1)
                .allSatisfy(hint -> assertThat(hint).contains("<badge>").contains("自闭合"));
    }

    /** 保存结果里的警告要带「下一步动作」：只说「有问题」模型不知道该干什么。 */
    @Test
    void saveResultCarriesSyntaxHintsWithNextAction() {
        ScheduledArticleTools.DraftState state = new ScheduledArticleTools.DraftState(null, LayoutEngine.MARKFLOW);
        ScheduledArticleTools.SaveMarkflowDraftParam param = new ScheduledArticleTools.SaveMarkflowDraftParam();
        param.setTitle("测试文章");
        param.setContentMarkdown(":::compare\n维度 | A | B | accent\n");

        String result = state.save(param);

        assertThat(result).contains("warnings").contains(":::compare").contains("重新调用 save_article_draft");
        // 语法问题不能拦下保存：拦下等于把这一稿整段作废（与 D19 同一教训）
        assertThat(state.isSaved()).isTrue();
    }
}
