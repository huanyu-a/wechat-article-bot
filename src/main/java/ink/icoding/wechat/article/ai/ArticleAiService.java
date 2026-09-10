package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentResultHandler;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.entity.MemoryMultipartFile;
import ink.icoding.llm.core.model.ContextCompressionStatus;
import ink.icoding.llm.core.model.TokenUsage;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.auth.CurrentUser;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.account.WechatAccount;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.settings.LlmConfigService;
import ink.icoding.wechat.article.skill.LayoutEngine;
import ink.icoding.wechat.article.skill.MarkFlowRenderService;
import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.skill.SkillContext;
import ink.icoding.wechat.article.skill.SkillPromptAssembler;
import ink.icoding.wechat.article.skill.SkillPromptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ArticleAiService {
    private static final Logger log = LoggerFactory.getLogger(ArticleAiService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOOL_CALLS = 24;
    /**
     * 定时单智能体的工具调用上限（方案 5.5 预算护栏，定时侧为新增强制点）：
     * 该智能体独自完成调研 + 配图 + 写作，额度高于编辑器单轮编辑的 {@link #MAX_TOOL_CALLS}。
     */
    private static final int MAX_SCHEDULED_TOOL_CALLS = 40;
    /** 编辑器单次对话的渲染/委托额度（服务端工具不经过浏览器管道，需单独限流）。 */
    private static final int MAX_EDITOR_RENDER_CALLS = 10;
    private static final int MAX_EDITOR_DELEGATE_CALLS = 3;
    /**
     * 编辑器单次对话的服务端工具调用总上限（对齐浏览器工具的 {@link #MAX_TOOL_CALLS}）。
     * 服务端工具（素材检索/导入/生图/改图、渲染、委托）不经过 requestTool，因此需要独立计数，
     * 否则一次对话可以无限调用生图接口。
     */
    private static final int MAX_SERVER_TOOL_CALLS = 24;
    /** 渲染占位符（第④期）：{{render:<renderId>}}。 */
    private static final java.util.regex.Pattern RENDER_PLACEHOLDER =
            java.util.regex.Pattern.compile("\\{\\{render:([A-Za-z0-9_-]+)}}");
    /** 渲染区段标记（第④期）：data-render-id="rN"。 */
    private static final java.util.regex.Pattern RENDER_ID_ATTR =
            java.util.regex.Pattern.compile("data-render-id\\s*=\\s*[\"']([A-Za-z0-9_-]+)[\"']");

    /** 在渲染产物最外层注入 data-render-id 标记（供读回识别，防 HTML 回灌判断失配）。 */
    static String markRenderId(String html, String renderId) {
        if (html == null || html.isBlank()) return html;
        // 找第一个「真正的开始标签」：跳过 DOCTYPE、注释、纯文本前缀——
        // 否则 indexOf('>') 会把属性插进 <!-- --> 里，破坏产物且导致 read 时识别不到渲染区段
        java.util.regex.Matcher tagStart = FIRST_TAG.matcher(html);
        if (!tagStart.find()) {
            return "<section data-render-id=\"" + renderId + "\">" + html + "</section>";
        }
        int start = tagStart.start();
        int end = html.indexOf('>', start);
        if (end < 0) {
            return "<section data-render-id=\"" + renderId + "\">" + html + "</section>";
        }
        return html.substring(0, start) + html.substring(start, end)
                + " data-render-id=\"" + renderId + "\">" + html.substring(end + 1);
    }

    /** 第一个开始标签（<tag 或 <tag/），用于定位可注入属性的位置。 */
    private static final java.util.regex.Pattern FIRST_TAG =
            java.util.regex.Pattern.compile("<[a-zA-Z][a-zA-Z0-9-]*");
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;
    private static final Set<String> BROWSER_TOOL_NAMES = Set.of(
            "read_article", "read_blocks", "delete_blocks", "insert_blocks", "replace_blocks",
            "update_metadata", "update_cover");
    private final AiMessageMapper messageMapper;
    private final ArticleAgentSessionMapper agentSessionMapper;
    private final ArticleService articleService;
    private final CurrentUserService currentUserService;
    private final LlmConfigService llmConfigService;
    private final ArticleMediaTools mediaTools;
    private final AssetService assetService;
    private final SkillPromptAssembler skillPromptAssembler;
    private final MarkFlowRenderService markFlowRenderService;
    private final WechatAccountService wechatAccountService;
    private final ink.icoding.wechat.article.agent.AgentFactory agentFactory;
    private final ScheduledAgentFactory scheduledAgentFactory;
    private final ink.icoding.wechat.article.agent.AgentDefinitionMapper agentDefinitionMapper;
    private final ink.icoding.wechat.article.schedule.AgentRunner agentRunner;
    private final Map<String, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Object> articleSessionLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "article-ai-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public ArticleAiService(AiMessageMapper messageMapper, ArticleAgentSessionMapper agentSessionMapper,
                            ArticleService articleService,
                            CurrentUserService currentUserService, LlmConfigService llmConfigService,
                            ArticleMediaTools mediaTools, AssetService assetService,
                            SkillPromptAssembler skillPromptAssembler, MarkFlowRenderService markFlowRenderService,
                            WechatAccountService wechatAccountService,
                            ink.icoding.wechat.article.agent.AgentFactory agentFactory,
                            ScheduledAgentFactory scheduledAgentFactory,
                            ink.icoding.wechat.article.agent.AgentDefinitionMapper agentDefinitionMapper,
                            ink.icoding.wechat.article.schedule.AgentRunner agentRunner) {
        this.messageMapper = messageMapper;
        this.agentSessionMapper = agentSessionMapper;
        this.articleService = articleService;
        this.currentUserService = currentUserService;
        this.llmConfigService = llmConfigService;
        this.mediaTools = mediaTools;
        this.assetService = assetService;
        this.skillPromptAssembler = skillPromptAssembler;
        this.markFlowRenderService = markFlowRenderService;
        this.wechatAccountService = wechatAccountService;
        this.agentFactory = agentFactory;
        this.scheduledAgentFactory = scheduledAgentFactory;
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.agentRunner = agentRunner;
    }

    public List<AiMessage> messages(Long articleId) {
        articleService.required(articleId);
        return messageMapper.findByArticleId(articleId);
    }

    public SseEmitter chat(Long articleId, String instruction, List<Long> assetIds) {
        if (instruction == null || instruction.isBlank()) throw new BusinessException("请输入编辑要求");
        Article article = articleService.required(articleId);
        CurrentUser user = currentUserService.required();
        List<Asset> attachedAssets = resolveAttachedAssets(article, assetIds);
        save(articleId, "USER", instruction, "COMPLETED", 0, 0, user.id());

        SseEmitter emitter = new SseEmitter(0L);
        EditorSession session = new EditorSession(UUID.randomUUID().toString(), article, user, emitter, attachedAssets);
        sessions.put(session.id, session);
        emitter.onTimeout(() -> session.close("编辑会话已超时"));
        emitter.onError(error -> session.close("编辑会话连接已断开"));
        emitter.onCompletion(() -> session.close("编辑会话已结束"));
        session.startHeartbeat();
        CompletableFuture.runAsync(() -> execute(session, instruction));
        return emitter;
    }

    @PreDestroy
    public void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    private List<Asset> resolveAttachedAssets(Article article, List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) return List.of();
        List<Long> uniqueIds = new ArrayList<>(new HashSet<>(assetIds));
        if (uniqueIds.size() > 4) throw new BusinessException("每轮对话最多上传 4 张图片");
        List<Asset> assets = new ArrayList<>();
        for (Long id : uniqueIds) {
            Asset asset = assetService.required(id);
            if (asset.getAccountId() != null && article.getAccountId() != null
                    && !asset.getAccountId().equals(article.getAccountId())) {
                throw new BusinessException("图片素材不属于当前公众号");
            }
            assets.add(asset);
        }
        return assets;
    }

    public void completeTool(Long articleId, String sessionId, String callId, ToolResultRequest request) {
        EditorSession session = sessions.get(sessionId);
        CurrentUser user = currentUserService.required();
        if (session == null || !session.article.getId().equals(articleId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "AI 编辑会话不存在或已结束");
        }
        if (!session.user.id().equals(user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能操作其他用户的 AI 编辑会话");
        }
        session.completeTool(callId, request);
    }

    private void execute(EditorSession session, String instruction) {
        Object lock = articleSessionLocks.computeIfAbsent(session.article.getId(), ignored -> new Object());
        synchronized (lock) {
            executeWithAgentSession(session, instruction);
        }
    }

    private void executeWithAgentSession(EditorSession session, String instruction) {
        if (!llmConfigService.runtime().available()) {
            session.send("error", Map.of("message", "LLM 尚未在系统设置中启用或未配置 API Key"));
            finishSession(session);
            return;
        }

        AtomicInteger inputTokens = new AtomicInteger();
        AtomicInteger outputTokens = new AtomicInteger();
        StringBuilder assistantText = new StringBuilder();
        try {
            // runtime() 二次读取：档案配置非法时可能抛异常，必须落在 try 内，否则会话泄漏且无错误事件
            LlmConfigService.RuntimeConfig config = llmConfigService.runtime();
            session.send("state", Map.of(
                    "sessionId", session.id,
                    "status", "thinking",
                    "message", "智能体正在分析要求并准备读取编辑区…"));
            AgentClient agent = createArticleAgent(session);
            ArticleAgentSession storedSession = agentSessionMapper.findByArticleId(session.article.getId());
            AgentClientSession agentSession = storedSession == null
                    ? agent.createSession()
                    : agent.getSessionFromSerialization(storedSession.getSerializedSession());
            AgentSessionResult result = agentSession.command(commandWithAttachments(instruction, session.attachedAssets),
                            sessionAttachments(session.attachedAssets))
                    .then(new AgentResultHandler() {
                        @Override
                        public void onMessage(String message) {
                            assistantText.append(message);
                            session.send("delta", Map.of("content", message));
                        }

                        @Override
                        public void onUsage(TokenUsage usage) {
                            if (usage != null) {
                                inputTokens.addAndGet(usage.getInputTokens());
                                outputTokens.addAndGet(usage.getOutputTokens());
                            }
                        }

                        @Override
                        public void onTool(ToolDescriptor tool, ToolStatus status) {
                            if (isServerSideTool(tool)) session.serverToolStatus(tool, status);
                        }

                        @Override
                        public void onToolError(ToolDescriptor tool, Exception error) {
                            if (isServerSideTool(tool)) session.serverToolError(tool, error);
                        }

                        @Override
                        public void onContextCompression(ContextCompressionStatus status,
                                                         int beforeTokens, int afterTokens) {
                            session.send("state", Map.of(
                                    "sessionId", session.id,
                                    "status", "compressing_context",
                                    "message", status == ContextCompressionStatus.STARTED
                                            ? "正在压缩较早的对话上下文…" : "对话上下文压缩完成"));
                        }
                    });
            result.execute();
            String response = result.get();
            if (session.serverToolCalls.get() > MAX_SERVER_TOOL_CALLS) {
                throw new IllegalStateException("单次对话最多调用 " + MAX_SERVER_TOOL_CALLS
                        + " 次服务端工具（素材/渲染/委托），请合并操作后重试");
            }
            String reply = response == null || response.isBlank() ? assistantText.toString().trim() : response.trim();
            if (reply.isBlank()) reply = "文章编辑已完成。";

            Article updated = commitSession(session);
            persistAgentSession(storedSession, agentSession, session.article.getId(), session.user.id());
            save(session.article.getId(), "ASSISTANT", reply, "COMPLETED",
                    inputTokens.get(), outputTokens.get(), session.user.id());
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("message", reply);
            completed.put("toolCalls", session.toolCalls.get());
            if (session.modified) completed.put("article", updated);
            session.send("completed", completed);
        } catch (Exception exception) {
            String message = readableLlmError(exception);
            save(session.article.getId(), "ASSISTANT", "处理失败：" + message, "FAILED",
                    inputTokens.get(), outputTokens.get(), session.user.id());
            session.send("error", Map.of("message", message));
        } finally {
            finishSession(session);
        }
    }

    private AgentClient createArticleAgent(EditorSession editorSession) {
        // 第②期：装配改走 AgentFactory（内置 builtin_editor 定义，方案 7 第②期第 4 项）
        SkillContext context = editorSkillContext(editorSession.article);
        SkillPromptResult editorPrompt = skillPromptAssembler.assemble(context);
        LayoutEngine engine = editorPrompt.engine();
        editorSession.layoutEngine = engine;
        AgentFactory.ToolResolver resolver = groups -> {
            List<ink.icoding.llm.core.tool.Tool> tools = new ArrayList<>();
            if (groups.contains(ink.icoding.wechat.article.agent.ToolRegistry.BROWSER_EDITOR)) {
                tools.addAll(ArticleEditorTools.all((toolName, paramJson) ->
                        editorSession.requestTool(toolName, paramJson, null), engine));
            }
            if (groups.contains(ink.icoding.wechat.article.agent.ToolRegistry.MEDIA)) {
                tools.addAll(mediaTools.create(editorSession.article.getAccountId(), editorSession.user.id(),
                        editorSession.mediaMutations::execute));
            }
            if (groups.contains(ink.icoding.wechat.article.agent.ToolRegistry.RENDER)
                    && engine == LayoutEngine.MARKFLOW) {
                // 仅在渲染式排版生效时暴露 render_markflow（PROMPT 模式下该工具无意义）
                tools.addAll(EditorServiceTools.render(editorSession.renderCache()));
            }
            if (groups.contains(ink.icoding.wechat.article.agent.ToolRegistry.DELEGATE)) {
                tools.addAll(EditorServiceTools.delegateResearch(editorSession.researchDelegate()));
            }
            return tools;
        };
        return agentFactory.buildByCode(AgentFactory.CODE_EDITOR, "EDITOR", context, editorPrompt, resolver);
    }

    private String commandWithAttachments(String instruction, List<Asset> assets) {
        if (assets.isEmpty()) return instruction;
        StringBuilder command = new StringBuilder(instruction).append("\n\n本轮用户提供了以下图片素材，图片内容也附在本消息中：\n");
        for (Asset asset : assets) {
            command.append("- assetId=").append(asset.getId())
                    .append(", publicUrl=").append(asset.getPublicUrl())
                    .append(", filename=").append(asset.getOriginalName()).append('\n');
        }
        command.append("需要使用时可直接把对应 publicUrl 通过 insert_blocks 插入文章；如需修改图片，调用 edit_image。\n");
        return command.toString();
    }

    private List<MemoryMultipartFile> sessionAttachments(List<Asset> assets) {
        return assets.stream().map(asset -> new MemoryMultipartFile(
                assetService.readBytes(asset.getId()), asset.getContentType(), asset.getOriginalName())).toList();
    }

    private void persistAgentSession(ArticleAgentSession storedSession,
                                     AgentClientSession agentSession, Long articleId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        if (storedSession == null) {
            storedSession = new ArticleAgentSession();
            storedSession.setArticleId(articleId);
            storedSession.setCreatedAt(now);
            storedSession.setSerializedSession(agentSession.serialization());
            storedSession.setUpdatedBy(userId);
            storedSession.setUpdatedAt(now);
            agentSessionMapper.insert(storedSession);
            return;
        }
        storedSession.setSerializedSession(agentSession.serialization());
        storedSession.setUpdatedBy(userId);
        storedSession.setUpdatedAt(now);
        agentSessionMapper.updateById(storedSession);
    }

    private Article commitSession(EditorSession session) {
        EditorDocument document = session.latestDocument;
        if (!session.modified || document == null) return articleService.required(session.article.getId());
        Article original = session.article;
        Long coverAssetId = document.coverAssetId();
        String coverUrl = document.coverUrl();
        if (coverAssetId != null) {
            Asset cover = assetService.required(coverAssetId);
            if (original.getAccountId() != null && cover.getAccountId() != null
                    && !original.getAccountId().equals(cover.getAccountId())) {
                throw new BusinessException("封面素材不属于当前公众号");
            }
            coverUrl = cover.getPublicUrl();
        }
        ArticleService.ArticleRequest request = new ArticleService.ArticleRequest(
                original.getAccountId(), document.title(), original.getAuthor(), document.digest(),
                document.contentHtml(), coverAssetId, coverUrl,
                original.getSourceUrl(), original.getRevision(),
                ink.icoding.wechat.article.account.WechatAccountService.parseSkillIds(original.getSkillIds()),
                original.getLayoutEngine(), original.getContentMarkdown());
        return articleService.updateByAi(original.getId(), request,
                "AI 工具编辑（" + session.toolCalls.get() + " 次工具调用）", session.user.id());
    }

    /**
     * 编辑器场景的 Skill 组装上下文（方案 5.2）：文章级 &gt; 账号级 &gt; agent 默认（底座）。
     * agent 默认技能取自内置 builtin_editor 定义；定义缺失/停用时跳过。
     */
    private SkillContext editorSkillContext(Article article) {
        WechatAccount account = article.getAccountId() == null
                ? null : wechatAccountService.required(article.getAccountId());
        List<Long> agentSkillIds = List.of();
        try {
            ink.icoding.wechat.article.agent.AgentDefinition definition =
                    agentDefinitionMapper.findByCode(AgentFactory.CODE_EDITOR);
            if (definition != null) agentSkillIds = parseSkillIds(definition.getSkillIds());
        } catch (Exception exception) {
            log.warn("读取编辑智能体默认技能失败，本次跳过 agent 级技能", exception);
        }
        return new SkillContext(parseSkillIds(article.getSkillIds()), List.of(),
                account == null ? List.of() : parseSkillIds(account.getSkillIds()),
                agentSkillIds, account == null ? null : account.getDefaultStyle(),
                SkillContext.Scene.EDITOR);
    }

    /** 解析 skillIds 逗号分隔字符串；空/非法返回空列表（容错，不阻断对话）。 */
    public static List<Long> parseSkillIds(String skillIdsJson) {
        return ink.icoding.wechat.article.account.WechatAccountService.parseSkillIds(skillIdsJson);
    }

    /** 解析本次任务的生效排版引擎（TaskExecutionService 构造工作区时需要，避免重复组装 prompt）。 */
    public ink.icoding.wechat.article.skill.LayoutEngine resolveLayoutEngine(ScheduledAgentRequest request) {
        WechatAccount account = request.accountId() == null
                ? null : wechatAccountService.required(request.accountId());
        SkillContext context = scheduledSkillContext(request, account);
        return skillPromptAssembler.assemble(context).engine();
    }

    /**
     * 定时场景 Skill 上下文（方案 5.2）：任务技能 &gt; 账号技能 &gt; agent 默认技能（底座）。
     * agent 默认技能取自内置 builtin_scheduled_creator 定义；定义缺失/停用时跳过。
     */
    private SkillContext scheduledSkillContext(ScheduledAgentRequest request, WechatAccount account) {
        List<Long> agentSkillIds = List.of();
        try {
            ink.icoding.wechat.article.agent.AgentDefinition definition =
                    agentDefinitionMapper.findByCode(AgentFactory.CODE_SCHEDULED_CREATOR);
            if (definition != null) agentSkillIds = parseSkillIds(definition.getSkillIds());
        } catch (Exception exception) {
            log.warn("读取定时创作智能体默认技能失败，本次跳过 agent 级技能", exception);
        }
        return new SkillContext(List.of(),
                request.skillIds() == null ? List.of() : request.skillIds(),
                account == null ? List.of() : parseSkillIds(account.getSkillIds()),
                agentSkillIds,
                account == null ? null : account.getDefaultStyle(),
                SkillContext.Scene.SCHEDULED);
    }

    public ScheduledAgentResult runScheduledAgent(ScheduledAgentRequest request) throws Exception {
        LlmConfigService.RuntimeConfig config = llmConfigService.runtime();
        if (!config.available()) throw new BusinessException("LLM 尚未在系统设置中启用或未配置 API Key");

        // 账号级 Skill 与默认风格（skills-agent-plan 5.2）
        WechatAccount account = request.accountId() == null
                ? null : wechatAccountService.required(request.accountId());
        SkillContext skillContext = scheduledSkillContext(request, account);
        SkillPromptResult skillPrompt = skillPromptAssembler.assemble(skillContext);

        // 排版引擎：MARKFLOW 时 DraftState 走 Markdown 语义，渲染延迟到交付前（5.10.4）
        LayoutEngine layoutEngine = skillPrompt.engine();
        ScheduledArticleTools.DraftState draftState =
                new ScheduledArticleTools.DraftState(request.defaultCoverAssetId(), layoutEngine);
        ToolMutationDeduplicator mediaMutations = new ToolMutationDeduplicator();
        // 第②期：装配改走 AgentFactory（内置 builtin_scheduled_creator 定义，方案 7 第②期第 4 项）
        List<ink.icoding.llm.core.tool.Tool> draftTools = new ArrayList<>(ScheduledArticleTools.all(draftState));
        List<ink.icoding.llm.core.tool.Tool> mediaToolList =
                mediaTools.create(request.accountId(), request.userId(), mediaMutations::execute);
        AgentFactory.ToolResolver resolver = groups -> {
            List<ink.icoding.llm.core.tool.Tool> tools = new ArrayList<>();
            if (groups.contains(ink.icoding.wechat.article.agent.ToolRegistry.DRAFT_READ)
                    || groups.contains(ink.icoding.wechat.article.agent.ToolRegistry.DRAFT_WRITE)) {
                tools.addAll(draftTools);
            }
            if (groups.contains(ink.icoding.wechat.article.agent.ToolRegistry.MEDIA)) {
                tools.addAll(mediaToolList);
            }
            return tools;
        };
        AgentClient agent = agentFactory.buildByCode(AgentFactory.CODE_SCHEDULED_CREATOR, "SCHEDULED_SINGLE",
                skillContext, resolver);

        AtomicInteger toolCalls = new AtomicInteger();
        Set<String> countedCalls = ConcurrentHashMap.newKeySet();
        List<String> executionLog = java.util.Collections.synchronizedList(new ArrayList<>());
        StringBuilder assistantText = new StringBuilder();
        String deliveryRequirement = "LOCAL_DRAFT".equals(request.outputMode())
                ? "保存为本地草稿；封面可按内容需要设置"
                : "将由系统同步或发布到微信；必须在提交文章前选择、导入或生成合适图片，并调用set_article_draft_cover设置封面";
        String command = """
                当前时间：%s
                目标公众号：%s
                任务完成后的系统动作：%s
                交付约束：%s

                本次创作要求：
                %s
                """.formatted(
                ZonedDateTime.now(ZoneId.of(request.timezone())),
                request.accountId() == null ? "未指定，仅创建本地文章" : "公众号ID " + request.accountId(),
                request.outputMode(), deliveryRequirement, request.instruction());

        AgentSessionResult result = agent.createSession().command(command).then(new AgentResultHandler() {
            @Override
            public void onMessage(String message) {
                if (message != null) assistantText.append(message);
            }

            @Override
            public void onTool(ToolDescriptor tool, ToolStatus status) {
                if (tool == null || status == ToolStatus.PREPARING) return;
                String key = tool.getName() + "\n" + safeCallId(tool);
                if (status == ToolStatus.CALLING && countedCalls.add(key)) {
                    int count = toolCalls.incrementAndGet();
                    executionLog.add("调用工具：" + tool.getName());
                    if (count > MAX_SCHEDULED_TOOL_CALLS) {
                        // 预算护栏：单次任务工具调用超限即中止，避免无人值守任务无限消耗（方案 5.5）
                        throw new IllegalStateException(
                                "单次定时创作最多调用 " + MAX_SCHEDULED_TOOL_CALLS + " 次工具");
                    }
                } else if (status == ToolStatus.COMPLETED) {
                    executionLog.add("工具完成：" + tool.getName());
                }
            }

            @Override
            public void onToolError(ToolDescriptor tool, Exception error) {
                executionLog.add("工具失败：" + (tool == null ? "unknown" : tool.getName()) + " - "
                        + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            }
        });
        result.execute();
        String response = result.get();
        // 兜底护栏：回调异常可能被 agent4j 吞掉，按最终计数再判一次（方案 5.5）
        if (toolCalls.get() > MAX_SCHEDULED_TOOL_CALLS) {
            throw new IllegalStateException("单次定时创作最多调用 " + MAX_SCHEDULED_TOOL_CALLS + " 次工具");
        }
        // MARKFLOW 延迟渲染：交付前统一渲染一次（skills-agent-plan 5.10.4），失败则任务 FAIL 且 Markdown 不丢
        draftState.renderBeforeDelivery(markFlowRenderService);
        ScheduledArticleTools.Draft draft = draftState.snapshot();
        if (draft.coverAssetId() != null) {
            Asset cover = assetService.required(draft.coverAssetId());
            if (request.accountId() != null && cover.getAccountId() != null
                    && !request.accountId().equals(cover.getAccountId())) {
                throw new BusinessException("智能体选择的封面素材不属于任务目标公众号");
            }
        }
        String reply = response == null || response.isBlank() ? assistantText.toString().trim() : response.trim();
        if (reply.isBlank()) reply = "定时文章创作已完成";
        return new ScheduledAgentResult(draft, reply, toolCalls.get(), String.join("\n", executionLog));
    }

    private String readableLlmError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return "浏览器编辑工具等待超时，请保持文章编辑页面打开后重试。";
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return "AI 处理失败";
        if (message.contains("HTTP 401")) return "LLM 服务鉴权失败（HTTP 401），请检查 API Key 是否正确。";
        if (message.contains("HTTP 403")) {
            return "LLM 服务拒绝访问（HTTP 403）。请确认 API Key 属于当前 Base URL、未过期或禁用，并检查账号权限与 IP 白名单；上游未返回可读的错误详情。";
        }
        if (message.contains("HTTP 404")) return "LLM 接口不存在（HTTP 404），请检查 Base URL 和所选协议。";
        if (message.contains("HTTP 429")) return "LLM 请求过于频繁或额度不足（HTTP 429），请稍后重试或检查账户余额。";
        return message;
    }

    private void save(Long articleId, String role, String content, String status, int input, int output, Long userId) {
        AiMessage message = new AiMessage();
        message.setArticleId(articleId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setStatus(status);
        message.setPromptTokens(input);
        message.setCompletionTokens(output);
        message.setCreatedBy(userId);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private void finishSession(EditorSession session) {
        sessions.remove(session.id, session);
        session.close("编辑会话已结束");
        try {
            session.emitter.complete();
        } catch (Exception ignored) {
        }
    }

    /**
     * 是否为服务端工具（非浏览器工具）。注意 Set.of(...) 的 contains(null) 会抛 NPE，
     * 而 tool.getName() 来自上游描述符，必须显式判空——否则一次空名字的工具事件会打断整条 SSE 流。
     */
    private static boolean isServerSideTool(ToolDescriptor tool) {
        if (tool == null) return false;
        String name = tool.getName();
        return name != null && !BROWSER_TOOL_NAMES.contains(name);
    }

    private static String safeCallId(ToolDescriptor descriptor) {
        return descriptor == null || descriptor.getCallId() == null ? "" : descriptor.getCallId();
    }

    private final class EditorSession {
        private final String id;
        private final Article article;
        private final CurrentUser user;
        private final SseEmitter emitter;
        private final List<Asset> attachedAssets;
        private final Map<String, CompletableFuture<ToolResultRequest>> pending = new ConcurrentHashMap<>();
        private final Map<String, String> completedMutations = new ConcurrentHashMap<>();
        private final Map<String, String> currentReadResults = new ConcurrentHashMap<>();
        private final Map<String, String> completedReadInvocations = new ConcurrentHashMap<>();
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final AtomicInteger documentEpoch = new AtomicInteger();
        private final AtomicInteger serverToolSequence = new AtomicInteger();
        /** 服务端工具调用计数（与浏览器工具分开统计）。 */
        private final AtomicInteger serverToolCalls = new AtomicInteger();
        private final Map<String, String> serverToolIds = new ConcurrentHashMap<>();
        private final Set<String> completedServerToolKeys = ConcurrentHashMap.newKeySet();
        private final ToolMutationDeduplicator mediaMutations = new ToolMutationDeduplicator();
        /** 编辑器渲染缓存（第④期）：renderId → 渲染 HTML。 */
        private final Map<String, String> renderCache = new ConcurrentHashMap<>();
        private final AtomicInteger renderSequence = new AtomicInteger();
        /** 服务端高成本工具的每轮额度（编辑器会话内累计，防无限外呼/无限委托）。 */
        private final AtomicInteger renderCalls = new AtomicInteger();
        private final AtomicInteger delegateCalls = new AtomicInteger();
        /** 当前生效排版引擎（装配时解析，占位替换与校验用）。 */
        private volatile LayoutEngine layoutEngine = LayoutEngine.PROMPT;
        private volatile ScheduledFuture<?> heartbeat;
        private volatile EditorDocument latestDocument;
        private volatile boolean modified;
        private volatile boolean closed;

        /** 渲染缓存适配（EditorServiceTools.RenderCache）。 */
        private EditorServiceTools.RenderCache renderCache() {
            return new EditorServiceTools.RenderCache() {
                @Override
                public String render(String markdown, String accent, String dark) {
                    if (layoutEngine != LayoutEngine.MARKFLOW) {
                        throw new IllegalStateException("当前文章未启用渲染式排版技能，无法调用 render_markflow");
                    }
                    // render_markflow 是服务端工具，不经过浏览器管道因而不受 MAX_TOOL_CALLS 约束，
                    // 这里单独计数限流：每次调用都会外呼渲染服务并占用会话内存。
                    if (renderCalls.incrementAndGet() > MAX_EDITOR_RENDER_CALLS) {
                        renderCalls.decrementAndGet();
                        throw new IllegalStateException("单次对话最多调用 " + MAX_EDITOR_RENDER_CALLS
                                + " 次 render_markflow，请合并版式调整后重试");
                    }
                    MarkFlowRenderService.RenderResult result =
                            markFlowRenderService.render(markdown, accent, dark);
                    String renderId = "r" + renderSequence.incrementAndGet();
                    // 注入可识别标记：read_article/read_blocks 按 data-render-id 识别渲染区段（防 HTML 回灌）
                    renderCache.put(renderId, markRenderId(result.html(), renderId));
                    return EditorServiceTools.renderResultJson(result, renderId);
                }

                @Override
                public String htmlOf(String renderId) {
                    return renderId == null ? null : renderCache.get(renderId);
                }
            };
        }

        /** 编辑器内委托调研员（第④期）：经 AgentFactory 装配 researcher 并同步运行，结果回注对话。
         *  事件由通用 serverToolStatus 通道下发（onTool 回调），此处不重复发送。 */
        private EditorServiceTools.ResearchDelegate researchDelegate() {
            return instruction -> {
                // 委托同样是服务端工具：每次会拉起一个完整的调研子会话，需单独限流
                if (delegateCalls.incrementAndGet() > MAX_EDITOR_DELEGATE_CALLS) {
                    delegateCalls.decrementAndGet();
                    throw new IllegalStateException("单次对话最多委托调研 " + MAX_EDITOR_DELEGATE_CALLS
                            + " 次，请把调研要求合并后一次提出");
                }
                SkillContext context = agentFactoryResearchContext();
                ink.icoding.wechat.article.schedule.TaskWorkspace researchWorkspace =
                        ink.icoding.wechat.article.schedule.TaskWorkspace.create(null, LayoutEngine.PROMPT);
                AgentClient subAgent = scheduledAgentFactory.build(AgentFactory.CODE_RESEARCHER, "RESEARCH",
                        context, researchWorkspace, article.getAccountId(), user.id(), null);
                ink.icoding.wechat.article.schedule.AgentRunner.Outcome outcome =
                        agentRunner.runWithLimit(subAgent, "请完成调研任务。\n调研要求：\n" + instruction
                                        + "\n\n完成后必须调用 save_research_notes 提交结构化简报。", null,
                                "【委托·调研】", ink.icoding.wechat.article.ai.DelegateTools.MAX_SUB_AGENT_TOOL_CALLS);
                String notes = researchWorkspace.hasResearchNotes()
                        ? researchWorkspace.researchNotesText()
                        : (outcome.reply() == null || outcome.reply().isBlank()
                                ? "调研已完成，但未产出结构化简报。" : outcome.reply());
                return "调研员产出如下，请据此继续写作：\n\n" + notes;
            };
        }

        private SkillContext agentFactoryResearchContext() {
            return new SkillContext(List.of(), List.of(),
                    article.getAccountId() == null ? List.of()
                            : WechatAccountService.parseSkillIds(
                                    wechatAccountService.required(article.getAccountId()).getSkillIds()),
                    List.of(), null, SkillContext.Scene.SCHEDULED);
        }

        private EditorSession(String id, Article article, CurrentUser user, SseEmitter emitter,
                              List<Asset> attachedAssets) {
            this.id = id;
            this.article = article;
            this.user = user;
            this.emitter = emitter;
            this.attachedAssets = List.copyOf(attachedAssets);
        }

        private void serverToolStatus(ToolDescriptor tool, ToolStatus status) {
            if (status == ToolStatus.PREPARING) return;
            String key = tool.getName() + "\n" + safeCallId(tool);
            boolean stableKey = !safeCallId(tool).isBlank();
            if (stableKey && completedServerToolKeys.contains(key)) return;
            if (status == ToolStatus.CALLING) {
                int count = serverToolCalls.incrementAndGet();
                if (count > MAX_SERVER_TOOL_CALLS) {
                    throw new IllegalStateException("单次对话最多调用 " + MAX_SERVER_TOOL_CALLS
                            + " 次服务端工具（素材/渲染/委托），请合并操作后重试");
                }
                String callId = serverToolIds.computeIfAbsent(key,
                        ignored -> "server-tool-" + serverToolSequence.incrementAndGet());
                send("tool.call", Map.of("sessionId", id, "callId", callId,
                        "modelCallId", safeCallId(tool), "name", tool.getName(),
                        "arguments", Map.of()));
            } else if (status == ToolStatus.COMPLETED) {
                String callId = serverToolIds.remove(key);
                if (callId != null) {
                    if (stableKey) completedServerToolKeys.add(key);
                    send("tool.result", Map.of("callId", callId,
                            "name", tool.getName(), "status", "COMPLETED", "message", "工具执行完成"));
                }
            }
        }

        private void serverToolError(ToolDescriptor tool, Exception error) {
            String key = tool.getName() + "\n" + safeCallId(tool);
            boolean stableKey = !safeCallId(tool).isBlank();
            if (stableKey && !completedServerToolKeys.add(key)) return;
            String callId = serverToolIds.remove(key);
            if (callId == null) callId = "server-tool-" + serverToolSequence.incrementAndGet();
            send("tool.result", Map.of("callId", callId, "name", tool.getName(), "status", "FAILED",
                    "message", error.getMessage() == null ? "工具执行失败" : error.getMessage()));
        }

        private String requestTool(String toolName, String paramJson, ToolDescriptor descriptor) throws Exception {
            if (closed) throw new IllegalStateException("浏览器编辑会话已关闭");
            String modelCallId = safeCallId(descriptor);
            JsonNode arguments = MAPPER.readTree(paramJson == null || paramJson.isBlank() ? "{}" : paramJson);
            String signature = toolName + "\n" + MAPPER.writeValueAsString(arguments);
            if (isMutation(toolName)) {
                String cached = completedMutations.get(signature);
                if (cached != null) return cached;
            } else {
                String invocationKey = documentEpoch.get() + "\n" + modelCallId + "\n" + signature;
                String cached = completedReadInvocations.get(invocationKey);
                if (cached == null) cached = currentReadResults.get(signature);
                if (cached != null) return cached;
            }

            int toolNumber = toolCalls.incrementAndGet();
            if (toolNumber > MAX_TOOL_CALLS) {
                throw new IllegalStateException("单次编辑最多调用 " + MAX_TOOL_CALLS + " 次工具");
            }
            String callId = "tool-" + toolNumber;
            CompletableFuture<ToolResultRequest> future = new CompletableFuture<>();
            if (pending.putIfAbsent(callId, future) != null) throw new IllegalStateException("工具调用ID重复");

            // 第④期：占位符替换（{{render:renderId}} → 渲染 HTML），在 SSE 下发前完成，前端零改动。
            // 替换结果同时用于 tool.call 与 editor.insert.delta，避免流式通道落回字面量占位符。
            // 替换失败（未解析占位符）时必须清理 pending，否则 future 常驻到会话结束。
            Object replacedArguments;
            try {
                replacedArguments = replaceRenderPlaceholders(MAPPER.convertValue(arguments, Object.class));
            } catch (RuntimeException error) {
                pending.remove(callId, future);
                throw error;
            }
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("sessionId", id);
            call.put("callId", callId);
            call.put("modelCallId", modelCallId);
            call.put("name", toolName);
            call.put("arguments", replacedArguments);
            send("tool.call", call);

            if (isStreamingMutation(toolName)) {
                streamBlocks(callId, toolName, MAPPER.valueToTree(replacedArguments));
            }
            try {
                ToolResultRequest result = future.get(90, TimeUnit.SECONDS);
                if (result == null || !Boolean.TRUE.equals(result.success())) {
                    throw new IllegalStateException(result == null || result.result() == null
                            ? "浏览器未能执行工具" : result.result());
                }
                if (isMutation(toolName) && result.document() == null) {
                    throw new IllegalStateException("浏览器没有返回修改后的文章内容");
                }
                if (result.document() != null && !article.getRevision().equals(result.document().articleRevision())) {
                    throw new IllegalStateException("文章数据库版本已变化，请结束本轮编辑并刷新文章");
                }
                if (result.document() != null) latestDocument = result.document();
                if (isMutation(toolName)) modified = true;
                String toolResult = result.result() == null || result.result().isBlank()
                        ? MAPPER.writeValueAsString(result.document()) : result.result();
                toolResult = compactReadResult(toolName, toolResult);
                if (isMutation(toolName)) {
                    completedMutations.put(signature, toolResult);
                    documentEpoch.incrementAndGet();
                    currentReadResults.clear();
                    completedReadInvocations.clear();
                } else {
                    currentReadResults.put(signature, toolResult);
                    completedReadInvocations.put(documentEpoch.get() + "\n" + modelCallId + "\n" + signature,
                            toolResult);
                }
                send("tool.result", Map.of("callId", callId, "name", toolName, "status", "COMPLETED",
                        "message", "工具执行完成"));
                return toolResult;
            } catch (Exception error) {
                send("tool.result", Map.of("callId", callId, "name", toolName, "status", "FAILED",
                        "message", error.getMessage() == null ? "工具执行失败" : error.getMessage()));
                throw error;
            } finally {
                pending.remove(callId);
            }
        }

        private String compactReadResult(String toolName, String value) {
            if (!("read_article".equals(toolName) || "read_blocks".equals(toolName))) return value;
            try {
                JsonNode root = MAPPER.readTree(value);
                if (root != null && root.isObject()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) root).remove("contentHtml");
                    JsonNode blocks = root.get("blocks");
                    if (blocks != null && blocks.isArray()) {
                        blocks.forEach(block -> {
                            if (block.isObject()) {
                                ((com.fasterxml.jackson.databind.node.ObjectNode) block).remove("text");
                                // 第④期：渲染区段回灌占位摘要（防大段 HTML 进上下文被抄改）
                                JsonNode html = block.get("html");
                                if (html != null && !html.isNull()) {
                                    String renderId = renderIdOf(html.asText());
                                    if (renderId != null) {
                                        ((com.fasterxml.jackson.databind.node.ObjectNode) block)
                                                .put("html", EditorServiceTools.placeholderSummary(renderId,
                                                        html.asText().length()));
                                    }
                                }
                            }
                        });
                    }
                    return MAPPER.writeValueAsString(root);
                }
            } catch (Exception ignored) {
            }
            return value;
        }

        /** 从 HTML 中识别渲染区段：优先按 data-render-id 标记（TipTap 归一化后仍可识别），回退精确匹配。 */
        private String renderIdOf(String html) {
            if (html == null || html.isBlank()) return null;
            java.util.regex.Matcher marker = RENDER_ID_ATTR.matcher(html);
            if (marker.find()) {
                String renderId = marker.group(1);
                if (renderCache.containsKey(renderId)) return renderId;
            }
            for (Map.Entry<String, String> entry : renderCache.entrySet()) {
                if (html.equals(entry.getValue())) return entry.getKey();
            }
            return null;
        }

        /** 递归替换 arguments 中的 {{render:renderId}} 占位符；未解析占位符抛错（防伪造）。 */
        private Object replaceRenderPlaceholders(Object value) {
            if (value instanceof String text) return replacePlaceholdersInString(text);
            if (value instanceof Map<?, ?> map) {
                Map<Object, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(entry.getKey(), replaceRenderPlaceholders(entry.getValue()));
                }
                return result;
            }
            if (value instanceof List<?> list) {
                List<Object> result = new ArrayList<>();
                for (Object item : list) result.add(replaceRenderPlaceholders(item));
                return result;
            }
            return value;
        }

        private String replacePlaceholdersInString(String text) {
            if (text == null || !text.contains("{{render:")) return text;
            java.util.regex.Matcher matcher = RENDER_PLACEHOLDER.matcher(text);
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                String renderId = matcher.group(1);
                String html = renderCache.get(renderId);
                if (html == null) {
                    throw new IllegalStateException("未解析的渲染占位符 {{render:" + renderId
                            + "}}：renderId 不存在或已过期，请重新调用 render_markflow");
                }
                matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(html));
            }
            matcher.appendTail(result);
            return result.toString();
        }

        private void streamBlocks(String callId, String toolName, JsonNode arguments) throws InterruptedException {
            send("editor.insert.start", Map.of("callId", callId, "name", toolName));
            JsonNode blocks = arguments.get("blocks");
            int count = blocks != null && blocks.isArray() ? blocks.size() : 0;
            for (int index = 0; index < count; index++) {
                send("editor.insert.delta", Map.of(
                        "callId", callId, "index", index, "total", count,
                        "contentHtml", blocks.get(index).asText()));
                if (index + 1 < count) Thread.sleep(90L);
            }
            send("editor.insert.completed", Map.of("callId", callId, "total", count));
        }

        private void completeTool(String callId, ToolResultRequest result) {
            CompletableFuture<ToolResultRequest> future = pending.get(callId);
            if (future == null) throw new BusinessException(HttpStatus.NOT_FOUND, "工具调用不存在或已经完成");
            if (!future.complete(result)) throw new BusinessException("工具结果已经提交");
        }

        private void startHeartbeat() {
            heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                    () -> send("heartbeat", Map.of("sessionId", id, "timestamp", System.currentTimeMillis())),
                    HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }

        private void send(String event, Object data) {
            if (closed) return;
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name(event).data(data));
                }
            } catch (Exception error) {
                close("浏览器编辑会话连接已断开");
            }
        }

        private void close(String reason) {
            if (closed) return;
            closed = true;
            ScheduledFuture<?> heartbeatTask = heartbeat;
            if (heartbeatTask != null) heartbeatTask.cancel(false);
            pending.values().forEach(future -> future.completeExceptionally(new IllegalStateException(reason)));
            pending.clear();
        }
    }

    private static boolean isStreamingMutation(String toolName) {
        return "insert_blocks".equals(toolName) || "replace_blocks".equals(toolName);
    }

    private static boolean isMutation(String toolName) {
        return isStreamingMutation(toolName) || "delete_blocks".equals(toolName)
                || "update_metadata".equals(toolName) || "update_cover".equals(toolName);
    }

    public record EditorBlock(Integer line, String type, String text, String html) {
    }

    public record EditorDocument(String title, String digest, String contentHtml, Long coverAssetId,
                                 String coverUrl, Long documentVersion, Integer articleRevision,
                                 List<EditorBlock> blocks) {
    }

    public record ToolResultRequest(Boolean success, String result, EditorDocument document) {
    }

    public record Patch(String title, String digest, String contentHtml) {
    }

    public record ScheduledAgentRequest(Long accountId, Long userId, Long defaultCoverAssetId,
                                        String timezone, String outputMode, String instruction,
                                        java.util.List<Long> skillIds,
                                        java.util.Map<String, Long> stageAgents, Integer maxRevisionRounds) {

        /** 兼容构造：无阶段编排（SINGLE / 存量调用点）。 */
        public ScheduledAgentRequest(Long accountId, Long userId, Long defaultCoverAssetId, String timezone,
                                     String outputMode, String instruction, java.util.List<Long> skillIds) {
            this(accountId, userId, defaultCoverAssetId, timezone, outputMode, instruction, skillIds,
                    java.util.Map.of(), 2);
        }
    }

    public record ScheduledAgentResult(ScheduledArticleTools.Draft draft, String message,
                                       int toolCalls, String executionLog) {
    }
}
