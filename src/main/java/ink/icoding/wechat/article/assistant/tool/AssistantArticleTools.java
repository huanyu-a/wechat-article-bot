package ink.icoding.wechat.article.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.agent.AgentDefinition;
import ink.icoding.wechat.article.agent.AgentDefinitionService;
import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.common.PageResult;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.Skill;
import ink.icoding.wechat.article.skill.SkillService;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局助手的文章工具（docs/dev/global-assistant-plan.md §4.3 / §4.4）。
 *
 * <p>与 {@code ArticleEditorTools} 的根本区别：那一套把调用**转发到浏览器**（Tiptap 文档才是当前真值），
 * 这一套直接在服务端读写数据库里的文章行。因此它不需要「编辑器页面开着」，但也必须自己承担
 * 乐观锁与版本快照——两者都经 {@link ArticleService#updateByAi} 完成，工具层不另造一套写路径。
 *
 * <p><b>{@code userId} 为什么要从装配点传进来</b>：工具在智能体的工作线程里执行，那里没有 HTTP 请求的
 * {@code SecurityContext}（{@code CurrentUserService.required()} 会拿到空上下文），而版本快照的
 * {@code updated_by} 必须记下「是谁改的」。装配点在请求线程里解析好 id 再交给工厂——与
 * {@code ArticleAiService.commitSession} 传 {@code session.user.id()} 是同一个理由。
 *
 * <p><b>为什么不提供删除/发布/同步</b>：这三件事有真实外部副作用或不可逆，必须由人在界面上确认
 * （plan §1.2）。工具清单里没有，模型就不会以为自己做过。
 */
public final class AssistantArticleTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 排版引擎名（与落库值逐字一致；{@code LayoutEngine.MARKFLOW.name()} 的常量同源）。 */
    private static final String MARKFLOW = LayoutEngine.MARKFLOW.name();

    /** 一次读取的块数上限：再多会把模型上下文撑爆，而分页读的成本极低。 */
    private static final int MAX_BLOCK_PAGE = 30;

    /** MARKFLOW 拒写正文的理由（插入/替换/删除三个写工具共用同一句话）。 */
    private static final String MARKFLOW_WRITE_REJECTED =
            "该文章使用渲染式排版（MARKFLOW），正文由 Markdown 渲染生成：请只修改标题/摘要/作者，"
                    + "正文请在文章编辑器里修改";

    private AssistantArticleTools() {
    }

    /** 只读工具：列文章/读文章/读块/列公众号/列技能/列智能体。 */
    public static List<Tool> read(ArticleService articleService, WechatAccountService accountService,
                                 SkillService skillService, AgentDefinitionService agentService) {
        return List.of(new ListArticlesTool(articleService), new ReadArticleTool(articleService),
                new ReadArticleBlocksTool(articleService), new ListArticleRevisionsTool(articleService),
                new ListAccountsTool(accountService), new ListSkillsTool(skillService),
                new ListAgentsTool(agentService));
    }

    /**
     * 写工具：建文章 + 元信息 + 三种块级编辑。
     *
     * @param userId 装配点在**请求线程**里解析出的当前用户 id；工具的版本快照要用它记 {@code updated_by}
     */
    public static List<Tool> write(ArticleService articleService, Long userId) {
        return List.of(new CreateArticleTool(articleService, userId),
                new UpdateArticleMetaTool(articleService, userId),
                new InsertArticleBlocksTool(articleService, userId),
                new ReplaceArticleBlocksTool(articleService, userId),
                new DeleteArticleBlocksTool(articleService, userId),
                new RollbackArticleRevisionTool(articleService),
                new RerenderArticleTool(articleService),
                new DeleteArticleTool(articleService));
    }

    @ToolInfo(name = "list_articles",
            description = "分页列出文章（不含正文）。返回每篇的 id、标题、摘要、作者、公众号、版本号与状态；"
                    + "要读正文用 read_article_blocks。")
    public static class ListArticlesTool implements Tool<ListArticlesParam> {
        private final ArticleService articleService;

        public ListArticlesTool(ArticleService articleService) {
            this.articleService = articleService;
        }

        @Override
        public String execute(ListArticlesParam param) {
            PageResult<Article> page = articleService.list(param.getAccountId(), blankToNull(param.getStatus()),
                    blankToNull(param.getKeyword()), value(param.getPage(), 1), value(param.getPageSize(), 10));
            List<Map<String, Object>> items = new ArrayList<>(page.items().size());
            for (Article article : page.items()) items.add(articleSummary(article));
            return json(Map.of("total", page.total(), "page", page.page(), "pageSize", page.pageSize(),
                    "count", items.size(), "items", items));
        }
    }

    @Data
    public static class ListArticlesParam extends ToolParam {
        @Param(required = false, description = "按公众号 id 过滤，留空列出全部公众号的文章")
        private Long accountId;
        @Param(required = false, description = "按业务状态过滤：DRAFT（草稿）或 PUBLISHED（已发布），留空全部")
        private String status;
        @Param(required = false, description = "标题或正文关键词，留空不过滤")
        private String keyword;
        @Param(required = false, description = "页码，从 1 开始（默认 1）")
        private Integer page;
        @Param(required = false, description = "每页条数，1 到 100（默认 10）")
        private Integer pageSize;
    }

    /**
     * 文章元信息 + 块数量。
     *
     * <p>为什么这里不返回正文 HTML：整篇正文动辄几万字符，一次性塞进上下文既贵又容易让模型在复述时
     * 抄错标签。读正文一律走 {@link ReadArticleBlocksTool} 的窗口读取。
     */
    @ToolInfo(name = "read_article",
            description = "读取一篇文章的元信息：标题、摘要、作者、公众号、版本号（revision）、排版引擎与内容块数量。"
                    + "**不返回正文 HTML**，正文请用 read_article_blocks 按块窗口读取；"
                    + "任何写操作之前必须先调用本工具拿到最新的 revision。")
    public static class ReadArticleTool implements Tool<ReadArticleParam> {
        private final ArticleService articleService;

        public ReadArticleTool(ArticleService articleService) {
            this.articleService = articleService;
        }

        @Override
        public String execute(ReadArticleParam param) {
            Article article = articleService.required(requiredArticleId(param.getArticleId()));
            Map<String, Object> view = new LinkedHashMap<>(articleSummary(article));
            view.put("blockCount", AssistantContentBlocks.count(article.getContentHtml()));
            view.put("sourceUrl", article.getSourceUrl());
            // writable 只针对**正文**：MARKFLOW 文章的正文由渲染产物构成，服务端拼 HTML 会与 Markdown 失配
            boolean writable = !isMarkflow(article);
            view.put("bodyWritable", writable);
            view.put("bodyWriteHint", writable ? "正文可按内容块编辑（块号从 1 开始）" : MARKFLOW_WRITE_REJECTED);
            return json(view);
        }
    }

    @Data
    public static class ReadArticleParam extends ToolParam {
        @Param(description = "文章 id（可从 list_articles 获取）")
        private Long articleId;
    }

    /**
     * 按块窗口读取正文。
     *
     * <p>窗口上限 {@value #MAX_BLOCK_PAGE} 块：分页读的代价是一次工具调用，而一次读完的超长上下文会
     * 挤掉后续轮次的空间，还可能触发上游的长度限制。
     */
    @ToolInfo(name = "read_article_blocks",
            description = "按块窗口读取文章正文。内容块是顶层元素（标题、段落、列表、图片块等），不是屏幕换行；"
                    + "块号从 1 开始，一次最多 " + MAX_BLOCK_PAGE + " 块。返回每块的 index、tag、text 与 html，"
                    + "以及 totalBlocks 和 writable；writable=false 表示该文正文不可按块编辑（见 reason）。")
    public static class ReadArticleBlocksTool implements Tool<ReadArticleBlocksParam> {
        private final ArticleService articleService;

        public ReadArticleBlocksTool(ArticleService articleService) {
            this.articleService = articleService;
        }

        @Override
        public String execute(ReadArticleBlocksParam param) {
            Article article = articleService.required(requiredArticleId(param.getArticleId()));
            List<AssistantContentBlocks.Block> blocks = AssistantContentBlocks.blocks(article.getContentHtml());
            int total = blocks.size();
            int start = Math.max(1, value(param.getStartIndex(), 1));
            int requestedEnd = value(param.getEndIndex(), start + MAX_BLOCK_PAGE - 1);
            if (requestedEnd < start) {
                throw new IllegalArgumentException("结束块号（" + requestedEnd + "）不能小于开始块号（" + start + "）");
            }
            // 超上限时截断到上限而不是报错：模型给出「1 到 200」的意图是「都要」，截断后仍能拿到前 30 块
            int end = Math.min(requestedEnd, start + MAX_BLOCK_PAGE - 1);
            boolean truncated = requestedEnd > end;
            List<Map<String, Object>> items = new ArrayList<>();
            for (AssistantContentBlocks.Block block : blocks) {
                if (block.index() < start || block.index() > end) continue;
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("index", block.index());
                view.put("tag", block.tag());
                view.put("text", block.text());
                view.put("html", block.html());
                items.add(view);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("articleId", article.getId());
            result.put("title", article.getTitle());
            result.put("revision", article.getRevision());
            result.put("layoutEngine", engineOf(article));
            result.put("totalBlocks", total);
            result.put("startIndex", start);
            result.put("endIndex", items.isEmpty() ? start : start + items.size() - 1);
            result.put("truncated", truncated);
            if (truncated) {
                result.put("truncationHint", "本次只返回 " + MAX_BLOCK_PAGE + " 块（单次上限），请用 startIndex="
                        + (start + items.size()) + " 继续读取");
            }
            boolean writable = !isMarkflow(article);
            result.put("writable", writable);
            if (!writable) result.put("reason", MARKFLOW_WRITE_REJECTED);
            result.put("blocks", items);
            return json(result);
        }
    }

    @Data
    public static class ReadArticleBlocksParam extends ToolParam {
        @Param(description = "文章 id")
        private Long articleId;
        @Param(required = false, description = "开始块号，从 1 开始（默认 1）")
        private Integer startIndex;
        @Param(required = false, description = "结束块号（含），默认 startIndex+29，单次最多 30 块")
        private Integer endIndex;
    }

    @ToolInfo(name = "list_accounts",
            description = "列出已配置的公众号（id、名称、类型、状态、默认作者）。新建或修改文章需要 accountId 时先查这里。")
    public static class ListAccountsTool implements Tool<ListAccountsParam> {
        private final WechatAccountService accountService;

        public ListAccountsTool(WechatAccountService accountService) {
            this.accountService = accountService;
        }

        @Override
        public String execute(ListAccountsParam param) {
            List<Map<String, Object>> items = accountService.list().stream().map(account -> {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", account.id());
                view.put("name", account.name());
                view.put("accountType", account.accountType());
                view.put("status", account.status());
                view.put("defaultAuthor", account.defaultAuthor());
                view.put("verified", account.verified());
                return view;
            }).toList();
            return json(AssistantToolBudget.page(items, param.getLimit(), "公众号"));
        }
    }

    @Data
    public static class ListAccountsParam extends ToolParam {
        @Param(required = false, description = "返回条数，1 到 " + AssistantToolBudget.MAX_LIMIT
                + "（默认 " + AssistantToolBudget.DEFAULT_LIMIT + "）")
        private Integer limit;
    }

    @ToolInfo(name = "list_skills",
            description = "列出创作技能（id、名称、维度、描述、排版引擎、是否内置）。需要绑定技能时先查这里。")
    public static class ListSkillsTool implements Tool<ListSkillsParam> {
        private final SkillService skillService;

        public ListSkillsTool(SkillService skillService) {
            this.skillService = skillService;
        }

        @Override
        public String execute(ListSkillsParam param) {
            List<Skill> skills = skillService.list(blankToNull(param.getDimension()), param.getEnabled());
            List<Map<String, Object>> items = skills.stream().map(skill -> {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", skill.getId());
                view.put("name", skill.getName());
                view.put("dimension", skill.getDimension());
                view.put("description", skill.getDescription());
                view.put("engine", skill.getEngine());
                view.put("enabled", skill.getEnabled());
                view.put("isBuiltin", skill.getIsBuiltin());
                return view;
            }).toList();
            return json(AssistantToolBudget.page(items, param.getLimit(), "技能"));
        }
    }

    @Data
    public static class ListSkillsParam extends ToolParam {
        @Param(required = false, description = "返回条数，1 到 " + AssistantToolBudget.MAX_LIMIT
                + "（默认 " + AssistantToolBudget.DEFAULT_LIMIT + "）")
        private Integer limit;
        @Param(required = false, description = "技能维度过滤，如 STYLE / LAYOUT；留空列出全部")
        private String dimension;
        @Param(required = false, description = "true 只看已启用、false 只看已停用，留空全部")
        private Boolean enabled;
    }

    @ToolInfo(name = "list_agents",
            description = "列出智能体（id、标识、名称、阶段、是否内置）。需要引用某个智能体时先查这里。")
    public static class ListAgentsTool implements Tool<ListAgentsParam> {
        private final AgentDefinitionService agentService;

        public ListAgentsTool(AgentDefinitionService agentService) {
            this.agentService = agentService;
        }

        @Override
        public String execute(ListAgentsParam param) {
            List<AgentDefinition> agents = agentService.list(blankToNull(param.getStage()), param.getEnabled());
            List<Map<String, Object>> items = agents.stream().map(agent -> {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", agent.getId());
                view.put("code", agent.getCode());
                view.put("name", agent.getName());
                view.put("stage", agent.getStage());
                view.put("enabled", agent.getEnabled());
                view.put("isBuiltin", agent.getIsBuiltin());
                view.put("llmProfileId", agent.getLlmProfileId());
                return view;
            }).toList();
            return json(AssistantToolBudget.page(items, param.getLimit(), "智能体"));
        }
    }

    @Data
    public static class ListAgentsParam extends ToolParam {
        @Param(required = false, description = "阶段过滤，如 EDITOR / SCHEDULED_SINGLE / RESEARCH；留空全部")
        private String stage;
        @Param(required = false, description = "true 只看已启用、false 只看已停用，留空全部")
        private Boolean enabled;
        @Param(required = false, description = "返回条数，1 到 " + AssistantToolBudget.MAX_LIMIT
                + "（默认 " + AssistantToolBudget.DEFAULT_LIMIT + "）")
        private Integer limit;
    }

    /**
     * 新建文章草稿。
     *
     * <p>{@code sourceType} 用 {@code SCHEDULED} 而不是更贴切的 {@code AI_ASSISTANT}：本工具必须带上
     * 「谁建的」这个身份（{@code created_by}），而 {@code ArticleService.create(request, sourceType)}
     * 内部从 {@code SecurityContext} 取用户——工具跑在工作线程里，那里没有登录上下文。
     * 目前只有 {@code createForTask(request, userId)} 接受显式 userId，它的实现把 sourceType 写死成
     * {@code SCHEDULED}（见 {@code ArticleService.java:106-107}）。等 {@code ArticleService} 补上
     * 「sourceType + userId」的重载后，这里换成那个重载即可，工具签名与行为不变。
     */
    @ToolInfo(name = "create_article",
            description = "新建一篇**草稿**文章（不会发布、不会同步微信）。contentHtml 留空时正文为空。"
                    + "调用者本人会记为这篇文章的创建人与默认作者。返回 {id, title, revision}；"
                    + "后续要改正文请用 insert_article_blocks（块号从 1 开始）。")
    public static class CreateArticleTool implements Tool<CreateArticleParam> {
        private final ArticleService articleService;
        private final Long userId;

        public CreateArticleTool(ArticleService articleService, Long userId) {
            this.articleService = articleService;
            this.userId = userId;
        }

        @Override
        public String execute(CreateArticleParam param) {
            String title = blankToNull(param.getTitle());
            if (title == null) throw new IllegalArgumentException("新建文章必须给出标题");
            // accountId 的存在性**不在这里校验**：本工厂只拿到 ArticleService（见 write 的签名），
            // 而「公众号是否存在」只有 WechatAccountService 知道。工具描述因此要求模型先调 list_accounts
            // 取得真实 id——比在这里凭空造一个校验更诚实（错了会在同步草稿时以「公众号不存在」暴露）。
            ArticleService.ArticleRequest request = new ArticleService.ArticleRequest(
                    param.getAccountId(), title, blankToNull(param.getAuthor()), blankToNull(param.getDigest()),
                    param.getContentHtml(), null, null, null, null, null, null, null, null, null);
            Article created = articleService.createForTask(request, userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", created.getId());
            result.put("title", created.getTitle());
            result.put("revision", created.getRevision());
            return json(result);
        }
    }

    @Data
    public static class CreateArticleParam extends ToolParam {
        @Param(description = "文章标题（必填）")
        private String title;
        @Param(required = false, description = "目标公众号 id（可从 list_accounts 获取）；留空则未绑定公众号")
        private Long accountId;
        @Param(required = false, description = "作者名；留空则用公众号默认作者")
        private String author;
        @Param(required = false, description = "摘要，最多 120 字")
        private String digest;
        @Param(required = false, description = "正文 HTML；留空则正文为空，之后用 insert_article_blocks 写入")
        private String contentHtml;
    }

    /**
     * 改标题/摘要/作者。
     *
     * <p>为什么必须重新读一遍文章再拼**完整** {@code ArticleRequest}：{@code ArticleService} 的更新是
     * 「整行覆盖」语义（每个字段都从请求里取），只传三个可变字段会把公众号、封面、正文、
     * 排版引擎、主题色等全部写成 null。这与 {@code ArticleAiService.commitSession} 的写法一致：
     * 从库里的当前行出发，只替换用户真正要改的字段。
     */
    @ToolInfo(name = "update_article_meta",
            description = "修改文章标题/摘要/作者，未提供的字段保持不变。必须先用 read_article 拿到最新 revision，"
                    + "并把它作为 expectedRevision 传入（乐观锁：版本不符会返回冲突错误，此时请重新读取再试）。"
                    + "正文不受影响；MARKFLOW 排版的文章只能用本工具改这三项。")
    public static class UpdateArticleMetaTool implements Tool<UpdateArticleMetaParam> {
        private final ArticleService articleService;
        private final Long userId;

        public UpdateArticleMetaTool(ArticleService articleService, Long userId) {
            this.articleService = articleService;
            this.userId = userId;
        }

        @Override
        public String execute(UpdateArticleMetaParam param) {
            Long articleId = requiredArticleId(param.getArticleId());
            Article current = articleService.required(articleId);
            if (param.getTitle() == null && param.getDigest() == null && param.getAuthor() == null) {
                throw new IllegalArgumentException("至少要给出 title、digest、author 中的一项，否则这次调用不会改动任何内容");
            }
            String title = param.getTitle() == null ? current.getTitle() : param.getTitle().trim();
            ArticleService.ArticleRequest request = fullRequest(current, title,
                    param.getAuthor() == null ? current.getAuthor() : blankToNull(param.getAuthor()),
                    param.getDigest() == null ? current.getDigest() : blankToNull(param.getDigest()),
                    current.getContentHtml(), param.getExpectedRevision());
            Article updated = articleService.updateByAi(articleId, request, "AI 助手（全局）修改文章元信息", userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", updated.getId());
            result.put("title", updated.getTitle());
            result.put("digest", updated.getDigest());
            result.put("author", updated.getAuthor());
            result.put("revision", updated.getRevision());
            return json(result);
        }
    }

    @Data
    public static class UpdateArticleMetaParam extends ToolParam {
        @Param(description = "文章 id")
        private Long articleId;
        @Param(description = "最近一次 read_article 返回的 revision；与实际版本不符时写入会被拒绝")
        private Integer expectedRevision;
        @Param(required = false, description = "完整的新标题；不传则保持不变")
        private String title;
        @Param(required = false, description = "完整的新摘要（最多 120 字）；不传则保持不变")
        private String digest;
        @Param(required = false, description = "新的作者名；不传则保持不变")
        private String author;
    }

    /**
     * 在第 N 块之后插入内容块。
     *
     * <p>MARKFLOW 文章拒绝：正文真值是 {@code content_markdown} 经渲染服务产出的产物，服务端拼 HTML
     * 会与 Markdown 失配（下次重排就覆盖），因此只放行元信息修改。
     */
    @ToolInfo(name = "insert_article_blocks",
            description = "在文章第 afterIndex 个内容块之后插入若干完整 HTML 内容块（afterIndex=0 表示插到最前面）。"
                    + "每块必须是可独立插入的完整元素，如 <p>…</p>、<h2>…</h2>、<figure>…</figure>。"
                    + "必须先用 read_article 拿到最新 revision 并作为 expectedRevision 传入。"
                    + "MARKFLOW 排版的文章拒写正文。")
    public static class InsertArticleBlocksTool implements Tool<InsertArticleBlocksParam> {
        private final ArticleService articleService;
        private final Long userId;

        public InsertArticleBlocksTool(ArticleService articleService, Long userId) {
            this.articleService = articleService;
            this.userId = userId;
        }

        @Override
        public String execute(InsertArticleBlocksParam param) {
            Long articleId = requiredArticleId(param.getArticleId());
            Article current = requireWritableBody(articleService, articleId);
            Integer afterIndex = param.getAfterIndex();
            if (afterIndex == null || afterIndex < 0) {
                throw new IllegalArgumentException("请给出插入位置 afterIndex：0 表示最前面，N 表示第 N 块之后");
            }
            String contentHtml = AssistantContentBlocks.insert(current.getContentHtml(), afterIndex, param.getBlocks());
            return writeBody(articleService, current, contentHtml, param.getExpectedRevision(), userId,
                    "AI 助手（全局）插入内容块 " + (afterIndex + 1) + " 起 " + param.getBlocks().size() + " 块");
        }
    }

    @Data
    public static class InsertArticleBlocksParam extends ToolParam {
        @Param(description = "文章 id")
        private Long articleId;
        @Param(description = "最近一次 read_article 返回的 revision")
        private Integer expectedRevision;
        @Param(description = "插入位置：0 表示插到正文最前面，N 表示接在第 N 个内容块之后")
        private Integer afterIndex;
        @Param(description = "依次插入的完整 HTML 内容块")
        private List<String> blocks;
    }

    @ToolInfo(name = "replace_article_blocks",
            description = "原子替换文章第 startIndex 到 endIndex 个内容块（含两端），适合改写已有段落。"
                    + "每块必须是完整 HTML 元素；必须先用 read_article 拿到最新 revision 并作为 expectedRevision 传入。"
                    + "MARKFLOW 排版的文章拒写正文。")
    public static class ReplaceArticleBlocksTool implements Tool<ReplaceArticleBlocksParam> {
        private final ArticleService articleService;
        private final Long userId;

        public ReplaceArticleBlocksTool(ArticleService articleService, Long userId) {
            this.articleService = articleService;
            this.userId = userId;
        }

        @Override
        public String execute(ReplaceArticleBlocksParam param) {
            Long articleId = requiredArticleId(param.getArticleId());
            Article current = requireWritableBody(articleService, articleId);
            int startIndex = requiredBlockNumber(param.getStartIndex(), "startIndex");
            int endIndex = requiredBlockNumber(param.getEndIndex(), "endIndex");
            String contentHtml = AssistantContentBlocks.replace(current.getContentHtml(), startIndex, endIndex,
                    param.getBlocks());
            return writeBody(articleService, current, contentHtml, param.getExpectedRevision(), userId,
                    "AI 助手（全局）替换内容块 " + startIndex + "–" + endIndex);
        }
    }

    @Data
    public static class ReplaceArticleBlocksParam extends ToolParam {
        @Param(description = "文章 id")
        private Long articleId;
        @Param(description = "最近一次 read_article 返回的 revision")
        private Integer expectedRevision;
        @Param(description = "开始块号，从 1 开始")
        private Integer startIndex;
        @Param(description = "结束块号（含该块）")
        private Integer endIndex;
        @Param(description = "依次替换为这些完整 HTML 内容块")
        private List<String> blocks;
    }

    @ToolInfo(name = "delete_article_blocks",
            description = "删除文章第 startIndex 到 endIndex 个内容块（含两端）。必须先用 read_article 拿到最新 revision "
                    + "并作为 expectedRevision 传入。删除全部块会得到空正文——通常不要这么做。"
                    + "MARKFLOW 排版的文章拒写正文。")
    public static class DeleteArticleBlocksTool implements Tool<DeleteArticleBlocksParam> {
        private final ArticleService articleService;
        private final Long userId;

        public DeleteArticleBlocksTool(ArticleService articleService, Long userId) {
            this.articleService = articleService;
            this.userId = userId;
        }

        @Override
        public String execute(DeleteArticleBlocksParam param) {
            Long articleId = requiredArticleId(param.getArticleId());
            Article current = requireWritableBody(articleService, articleId);
            int startIndex = requiredBlockNumber(param.getStartIndex(), "startIndex");
            int endIndex = requiredBlockNumber(param.getEndIndex(), "endIndex");
            String contentHtml = AssistantContentBlocks.delete(current.getContentHtml(), startIndex, endIndex);
            return writeBody(articleService, current, contentHtml, param.getExpectedRevision(), userId,
                    "AI 助手（全局）删除内容块 " + startIndex + "–" + endIndex);
        }
    }

    @Data
    public static class DeleteArticleBlocksParam extends ToolParam {
        @Param(description = "文章 id")
        private Long articleId;
        @Param(description = "最近一次 read_article 返回的 revision")
        private Integer expectedRevision;
        @Param(description = "开始块号，从 1 开始")
        private Integer startIndex;
        @Param(description = "结束块号（含该块）")
        private Integer endIndex;
    }

    /**
     * 块级写入的公共尾部：重建完整请求 → 走 {@code updateByAi}（乐观锁 + 版本快照）→ 回读块数。
     *
     * <p>冲突（{@code BusinessException} + 409）**不在这里捕获**：agent4j 会把消息原样回给模型，
     * 模型据此重新读取后重试，比工具层擅自重试安全得多——盲目重试会用同一份过期 revision 再撞一次，
     * 或者更糟：覆盖别人刚保存的内容。
     */
    private static String writeBody(ArticleService articleService, Article current, String contentHtml,
                                    Integer expectedRevision, Long userId, String summary) {
        ArticleService.ArticleRequest request = fullRequest(current, current.getTitle(), current.getAuthor(),
                current.getDigest(), contentHtml, expectedRevision);
        Article updated = articleService.updateByAi(current.getId(), request, summary, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", updated.getId());
        result.put("title", updated.getTitle());
        result.put("revision", updated.getRevision());
        result.put("totalBlocks", AssistantContentBlocks.count(updated.getContentHtml()));
        result.put("summary", summary);
        return json(result);
    }

    /**
     * 从库里的当前行出发拼**完整** {@code ArticleRequest}（14 个参数一个不漏）。
     *
     * <p>为什么不能省掉「看起来没变」的字段：{@code ArticleService.updateWithUser} 把请求当成整行新值，
     * 每个 setter 都无条件调用。少传一个字段就等于把它写成 null——封面试过一次正文改一次就会丢。
     * 排版相关的四项（引擎、Markdown 源文、两种主题色）尤其不能省：省了就等于让 MARKFLOW 文章
     * 降级成普通 HTML。
     */
    private static ArticleService.ArticleRequest fullRequest(Article current, String title, String author,
                                                             String digest, String contentHtml,
                                                             Integer revision) {
        return new ArticleService.ArticleRequest(
                current.getAccountId(), title, author, digest, contentHtml,
                current.getCoverAssetId(), current.getCoverUrl(), current.getSourceUrl(), revision,
                WechatAccountService.parseSkillIds(current.getSkillIds()),
                current.getLayoutEngine(), current.getContentMarkdown(),
                current.getThemeAccent(), current.getThemeDark());
    }

    /** 读文章并挡住 MARKFLOW 的块级写（三个块写工具共用）。 */
    private static Article requireWritableBody(ArticleService articleService, Long articleId) {
        Article current = articleService.required(articleId);
        if (isMarkflow(current)) throw new IllegalArgumentException(MARKFLOW_WRITE_REJECTED);
        return current;
    }

    private static boolean isMarkflow(Article article) {
        return article != null && MARKFLOW.equalsIgnoreCase(engineOf(article));
    }

    /** 排版引擎名；库里可能为 null（存量行），统一按 PROMPT 语义返回，避免把 null 抛给模型。 */
    private static String engineOf(Article article) {
        String engine = article.getLayoutEngine();
        return engine == null || engine.isBlank() ? LayoutEngine.PROMPT.name() : engine;
    }

    private static Long requiredArticleId(Long articleId) {
        if (articleId == null) throw new IllegalArgumentException("请给出文章 id（可先用 list_articles 查找）");
        return articleId;
    }

    private static int requiredBlockNumber(Integer value, String name) {
        if (value == null) throw new IllegalArgumentException("请给出 " + name + "（块号从 1 开始）");
        return value;
    }

    /** 列表/摘要共用的文章视图（不含正文，避免把整篇 HTML 塞进上下文）。 */
    private static Map<String, Object> articleSummary(Article article) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", article.getId());
        view.put("title", article.getTitle());
        view.put("digest", article.getDigest());
        view.put("author", article.getAuthor());
        view.put("accountId", article.getAccountId());
        view.put("accountName", article.getAccountName());
        view.put("revision", article.getRevision());
        view.put("layoutEngine", engineOf(article));
        view.put("businessStatus", article.getBusinessStatus());
        view.put("workflowStatus", article.getWorkflowStatus());
        view.put("wechatStatus", article.getWechatStatus());
        view.put("sourceType", article.getSourceType());
        view.put("updatedAt", text(article.getUpdatedAt()));
        return view;
    }

    /**
     * 时间统一序列化成 ISO 字符串。
     *
     * <p>为什么不直接把 {@code LocalDateTime} 交给共享 MAPPER：本文件用的是裸 {@code new ObjectMapper()}
     * （与 {@code ArticleMediaTools} 同款），没注册 JSR-310 模块，序列化 {@code LocalDateTime} 会抛
     * {@code InvalidDefinitionException}——一个只读工具因此变成工具失败，模型会以为读取出了问题。
     */
    private static String text(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

/**
     * 以指定用户身份执行一段服务调用（工具跑在工作线程里，那里没有 HTTP 请求的 SecurityContext）。
     *
     * <p>为什么需要它：{@code ScheduleTaskService.create} 等既有服务内部仍会调
     * {@code currentUserService.required()} 来记录 {@code created_by}，而工具线程的安全上下文是空的，
     * 直接调用会抛「登录已失效」。这里临时装一个只含该用户的 {@link SecurityContext}，执行完**必定还原**
     * （{@code finally}）——工作线程是复用的，残留的上下文会让下一个任务被记到别人名下。
     *
     * <p>为什么不改成让服务全部接收 userId 参数：那要改动多条既有写路径的公共签名，而它们的
     * HTTP 调用点本就依赖上下文。在工具边界上做一次上下文注入，改动面最小且语义清楚：
     * 「这一段的身份就是 userId」。
     *
     * <p><b>安全前提</b>：{@code userId} 必须由装配点在**请求线程**里用
     * {@code currentUserService.required()} 解析后传入，绝不能来自模型给的参数——
     * 否则模型可以自称任意用户。工厂签名（{@code write(articleService, userId)}）保证了这一点。
     */
    @ToolInfo(name = "list_article_revisions",
            description = "列出某篇文章的历史版本（版本号、来源、摘要、时间）。要回退到某一版用 rollback_article_revision。")
    public static class ListArticleRevisionsTool implements Tool<ArticleIdParam> {
        private final ArticleService articleService;

        public ListArticleRevisionsTool(ArticleService articleService) {
            this.articleService = articleService;
        }

        @Override
        public String execute(ArticleIdParam param) {
            Long id = param.getArticleId();
            if (id == null) throw new IllegalArgumentException("请给出文章 id（可先用 list_articles 查找）");
            java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
            for (ink.icoding.wechat.article.article.ArticleRevision revision : articleService.revisions(id)) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("revision", revision.getRevision());
                row.put("changeSource", revision.getChangeSource());
                row.put("summary", revision.getChangeSummary());
                row.put("title", revision.getTitle());
                row.put("createdAt", revision.getCreatedAt());
                rows.add(row);
            }
            return json(AssistantToolBudget.page(rows, param.getLimit(), "版本"));
        }
    }

    @Data
    public static class ArticleIdParam extends ToolParam {
        @Param(description = "文章 id")
        private Long articleId;
        @Param(required = false, description = "最多返回多少条（回滚用的版本列表）")
        private Integer limit;
    }

    @ToolInfo(name = "rollback_article_revision",
            description = "把文章回退到指定历史版本（回退本身也会生成一个新版本，历史不会丢）。"
                    + "版本号先用 list_article_revisions 查。这是内容覆盖操作，执行前应向用户确认。")
    public static class RollbackArticleRevisionTool implements Tool<RollbackArticleParam> {
        private final ArticleService articleService;

        public RollbackArticleRevisionTool(ArticleService articleService) {
            this.articleService = articleService;
        }

        @Override
        public String execute(RollbackArticleParam param) {
            if (param.getArticleId() == null) throw new IllegalArgumentException("请给出文章 id");
            if (param.getRevision() == null) throw new IllegalArgumentException("请给出要回退到的版本号");
            ink.icoding.wechat.article.article.Article article =
                    articleService.rollback(param.getArticleId(), param.getRevision());
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("articleId", article.getId());
            row.put("title", article.getTitle());
            row.put("revision", article.getRevision());
            row.put("workflowStatus", article.getWorkflowStatus());
            return json(row);
        }
    }

    @Data
    public static class RollbackArticleParam extends ToolParam {
        @Param(description = "文章 id")
        private Long articleId;
        @Param(description = "要回退到的版本号")
        private Integer revision;
    }

    @ToolInfo(name = "rerender_article",
            description = "重新渲染 MarkFlow 排版（仅 MARKFLOW 文章可用）。可传 accent/dark 主题色覆盖，"
                    + "不传则沿用原主题。正文 Markdown 源文被改过时用它让排版跟上。")
    public static class RerenderArticleTool implements Tool<RerenderArticleParam> {
        private final ArticleService articleService;

        public RerenderArticleTool(ArticleService articleService) {
            this.articleService = articleService;
        }

        @Override
        public String execute(RerenderArticleParam param) {
            if (param.getArticleId() == null) throw new IllegalArgumentException("请给出文章 id");
            ink.icoding.wechat.article.article.Article article =
                    articleService.rerender(param.getArticleId(), param.getAccent(), param.getDark());
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("articleId", article.getId());
            row.put("title", article.getTitle());
            row.put("revision", article.getRevision());
            row.put("themeAccent", article.getThemeAccent());
            row.put("themeDark", article.getThemeDark());
            return json(row);
        }
    }

    @Data
    public static class RerenderArticleParam extends ToolParam {
        @Param(description = "文章 id")
        private Long articleId;
        @Param(required = false, description = "主题强调色，如 #2b9a70；留空沿用原值")
        private String accent;
        @Param(required = false, description = "深色主题色，如 #16221c；留空沿用原值")
        private String dark;
    }

    @ToolInfo(name = "delete_article",
            description = "把文章移入回收（软删除，不物理清除）。不可自动恢复，"
                    + "执行前必须向用户确认，并说明是哪一篇（用标题+id 复述）。")
    public static class DeleteArticleTool implements Tool<ArticleIdParam> {
        private final ArticleService articleService;

        public DeleteArticleTool(ArticleService articleService) {
            this.articleService = articleService;
        }

        @Override
        public String execute(ArticleIdParam param) {
            Long id = param.getArticleId();
            if (id == null) throw new IllegalArgumentException("请给出文章 id");
            ink.icoding.wechat.article.article.Article article = articleService.required(id);
            articleService.delete(id);
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("deleted", true);
            row.put("articleId", id);
            row.put("title", article.getTitle());
            return json(row);
        }
    }

    static <T> T callAsUser(Long userId, java.util.function.Supplier<T> action) {
        if (userId == null) throw new IllegalArgumentException("缺少当前用户，无法执行写操作");
        org.springframework.security.core.context.SecurityContext previous =
                org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            ink.icoding.wechat.article.auth.CurrentUser user = new ink.icoding.wechat.article.auth.CurrentUser(
                    userId, null, null, null);
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            user, null, java.util.List.of());
            org.springframework.security.core.context.SecurityContext context =
                    org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            return action.get();
        } finally {
            // 工作线程复用：不还原就会把本次用户留给下一个任务（跨用户串味）
            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
        }
    }

    private static String json(Object value) {
        try {
            // 统一兜底：即便某个工具忘了用 AssistantToolBudget.page，输出也不会撑爆上下文
            return AssistantToolBudget.cap(MAPPER.writeValueAsString(value));
        } catch (Exception error) {
            throw new IllegalStateException("助手文章工具结果序列化失败", error);
        }
    }
}