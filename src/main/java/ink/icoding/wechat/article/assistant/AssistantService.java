package ink.icoding.wechat.article.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentResultHandler;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.model.ContextCompressionStatus;
import ink.icoding.llm.core.model.TokenUsage;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.agent.AgentDefinitionMapper;
import ink.icoding.wechat.article.agent.AgentDefinitionService;
import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.agent.LlmProfileService;
import ink.icoding.wechat.article.agent.LlmProviderService;
import ink.icoding.wechat.article.agent.ToolRegistry;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.assistant.tool.AssistantAgentTools;
import ink.icoding.wechat.article.assistant.tool.AssistantArticleTools;
import ink.icoding.wechat.article.assistant.tool.AssistantModelTools;
import ink.icoding.wechat.article.assistant.tool.AssistantSkillTools;
import ink.icoding.wechat.article.assistant.tool.AssistantTaskTools;
import ink.icoding.wechat.article.auth.CurrentUser;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.schedule.AgentRunner;
import ink.icoding.wechat.article.schedule.ScheduleTaskService;
import ink.icoding.wechat.article.schedule.StageTimeout;
import ink.icoding.wechat.article.skill.SkillContext;
import ink.icoding.wechat.article.skill.SkillPromptAssembler;
import ink.icoding.wechat.article.skill.SkillPromptResult;
import ink.icoding.wechat.article.skill.SkillService;
import ink.icoding.wechat.article.settings.LlmConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全局助手（docs/dev/global-assistant-plan.md）。
 *
 * <p>与 {@link ink.icoding.wechat.article.ai.ArticleAiService} 的关系是**并存而非替代**：
 * 编辑器助手是**文章域**的、写工具跑在浏览器里（Tiptap 文档为真值），因为用户可能正在编辑、
 * 服务端库里的 HTML 不是当前真值。全局助手是**项目域**的、写工具跑在服务端，
 * 面向已落库的数据，因此可以在任意页面使用。
 *
 * <p>两者为什么不能合成一个：同一篇文章一旦有两个写者（浏览器自动保存 + 全局助手写库），
 * 结果是丢更新——乐观锁只能让一方报 409，不能避免内容被覆盖。前端因此在编辑器页隐藏本面板。
 *
 * <p>复用而非重写：装配走 {@link AgentFactory}，协议走 {@link InkAgentProtocols 常量}，
 * 会话超时走 {@link StageTimeout}，重试/换档案走 {@link AgentRunner}，并发走
 * {@link ink.icoding.wechat.article.common.InFlightGate}，换档案候选由 AgentFactory 给出。
 * 这些都是线上事故换来的护栏，自己另写一套等于把它们作废。
 */
@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;
    /** 单轮工具调用上限的配置默认值（可在 application.yaml 覆盖）。 */
    private static final int DEFAULT_MAX_TOOL_CALLS = 30;
    private static final long DEFAULT_TIMEOUT_SECONDS = 900L;

    private final AssistantMessageMapper messageMapper;
    private final AssistantSessionMapper sessionMapper;
    /** 用户偏好（目前只有「用哪个模型档案」）。 */
    private final AssistantPreferenceMapper preferenceMapper;
    private final CurrentUserService currentUserService;
    private final LlmConfigService llmConfigService;
    private final AgentFactory agentFactory;
    private final ToolRegistry toolRegistry;
    private final AgentRunner agentRunner;
    private final ink.icoding.wechat.article.common.InFlightGate inFlightGate;
    private final SkillPromptAssembler skillPromptAssembler;
    /** 工具组装配所需的服务（工具实例按角色裁剪后才会进 AgentClient）。 */
    private final ArticleService articleService;
    private final WechatAccountService wechatAccountService;
    private final SkillService skillService;
    private final AgentDefinitionService agentDefinitionService;
    private final ScheduleTaskService scheduleTaskService;
    private final LlmProviderService llmProviderService;
    private final LlmProfileService llmProfileService;
    private final long timeoutSeconds;
    private final int maxToolCalls;
    /** 每用户串行：同一用户连发两条指令时排队，避免两条会话同时改同一批数据。 */
    private final Map<Long, Object> userLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "assistant-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public AssistantService(AssistantMessageMapper messageMapper, AssistantSessionMapper sessionMapper,
                            AssistantPreferenceMapper preferenceMapper,
                            CurrentUserService currentUserService, LlmConfigService llmConfigService,
                            AgentFactory agentFactory, ToolRegistry toolRegistry, AgentRunner agentRunner,
                            ink.icoding.wechat.article.common.InFlightGate inFlightGate,
                            SkillPromptAssembler skillPromptAssembler, ArticleService articleService,
                            WechatAccountService wechatAccountService, SkillService skillService,
                            AgentDefinitionService agentDefinitionService,
                            ScheduleTaskService scheduleTaskService, LlmProviderService llmProviderService,
                            LlmProfileService llmProfileService,
                            @Value("${app.assistant.timeout-seconds:" + DEFAULT_TIMEOUT_SECONDS + "}")
                            long timeoutSeconds,
                            @Value("${app.assistant.max-tool-calls:" + DEFAULT_MAX_TOOL_CALLS + "}")
                            int maxToolCalls) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.preferenceMapper = preferenceMapper;
        this.currentUserService = currentUserService;
        this.llmConfigService = llmConfigService;
        this.agentFactory = agentFactory;
        this.toolRegistry = toolRegistry;
        this.agentRunner = agentRunner;
        this.inFlightGate = inFlightGate;
        this.skillPromptAssembler = skillPromptAssembler;
        this.articleService = articleService;
        this.wechatAccountService = wechatAccountService;
        this.skillService = skillService;
        this.agentDefinitionService = agentDefinitionService;
        this.scheduleTaskService = scheduleTaskService;
        this.llmProviderService = llmProviderService;
        this.llmProfileService = llmProfileService;
        // 用户在场，等 300 秒太短；与编辑器链路同一量级，但独立配置——两条链路的
        // 「正常多久」不同，共用一个旋钮会让调其中一条时误伤另一条。
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.maxToolCalls = maxToolCalls > 0 ? maxToolCalls : DEFAULT_MAX_TOOL_CALLS;
        log.info("全局助手就绪：会话超时 {} 秒，单轮工具上限 {} 次", this.timeoutSeconds, this.maxToolCalls);
    }

    @jakarta.annotation.PreDestroy
    public void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    /** 本人最近若干条助手消息（新的在后，便于前端直接追加渲染）。 */
    public List<AssistantMessage> messages(int limit) {
        CurrentUser user = currentUserService.required();
        return messageMapper.findRecent(user.id(), limit);
    }

    /**
     * 开新对话：清掉本人的会话序列化，历史消息**保留**。
     *
     * <p>为什么保留消息：会话序列化决定「模型记得多少上下文」，历史消息决定「用户能不能翻到
     * 之前让它做过什么」。清空前者是为了摆脱被带偏的上下文，顺带抹掉后者只会让操作无处可查。
     */
    public void reset() {
        CurrentUser user = currentUserService.required();
        AssistantSession existing = sessionMapper.findByUserId(user.id());
        if (existing == null) return;
        sessionMapper.deleteById(existing.getId());
    }

    /**
     * 可选的模型档案（助手面板的下拉框）。
     *
     * <p><b>为什么不直接复用 {@code /api/llm-profiles}</b>：那个接口整体是 ADMIN 权限
     * （模型档案属于系统配置，普通编辑不该看到别人的档案与密钥状态）。但「我的助手用哪个模型」
     * 是使用者的日常需求，EDITOR 也必须能选。所以这里只暴露**选择所需的最小字段**：
     * 名称、模型名、供应商名、是否可用——没有任何密钥、掩码或 baseUrl。
     *
     * <p>{@code available=false} 的档案照样列出但标为不可选：把不可用的藏起来，
     * 用户会以为「档案丢了」；展示出来并说明不可用，他才知道该去补 API Key。
     */
    public ModelChoice models() {
        CurrentUser user = currentUserService.required();
        return modelChoice(user.id());
    }

    /** 保存「我的助手用哪个模型」。{@code profileId=null} 表示清除选择，回到自动挑链。 */
    public ModelChoice selectModel(Long profileId) {
        CurrentUser user = currentUserService.required();
        if (profileId != null) {
            // 只校验「存在」，不校验「此刻可用」：可用性取决于供应商密钥，会随运维变化；
            // 允许先把选择存下来，等密钥配好自动生效，比强迫用户按顺序操作更合理。
            llmProfileService.required(profileId);
        }
        AssistantPreference preference = preferenceMapper.findByUserId(user.id());
        if (preference == null) {
            preference = new AssistantPreference();
            preference.setUserId(user.id());
            preference.setProfileId(profileId);
            preference.setUpdatedAt(java.time.LocalDateTime.now());
            preferenceMapper.insert(preference);
        } else {
            preference.setProfileId(profileId);
            preference.setUpdatedAt(java.time.LocalDateTime.now());
            preferenceMapper.updateById(preference);
        }
        return modelChoice(user.id());
    }

    private ModelChoice modelChoice(Long userId) {
        AssistantPreference preference = preferenceMapper.findByUserId(userId);
        Long stored = preference == null ? null : preference.getProfileId();
        List<ModelOption> items = new java.util.ArrayList<>();
        Long effective = null;
        for (LlmProfileService.ProfileView view : llmProfileService.list()) {
            boolean available = Boolean.TRUE.equals(view.enabled()) && view.runtimeAvailable();
            boolean selected = stored != null && stored.equals(view.id());
            if (selected) effective = available ? view.id() : null;
            items.add(new ModelOption(view.id(), view.name(), view.modelName(), view.providerName(),
                    available, view.isDefault(), selected));
        }
        // 选中的档案已不可用 / 不存在时，界面要显示「实际会用哪个」而不是一个骗人的空值：
        // 回落到默认档案，与真正的挑链顺序一致。
        if (effective == null) {
            for (ModelOption option : items) {
                if (option.available() && option.isDefault()) {
                    effective = option.id();
                    break;
                }
            }
        }
        if (effective == null) {
            for (ModelOption option : items) {
                if (option.available()) {
                    effective = option.id();
                    break;
                }
            }
        }
        return new ModelChoice(stored, effective, labelOf(effective), items);
    }

    /** 模型下拉的选项（**不含**密钥、掩码、baseUrl）。 */
    public record ModelOption(Long id, String name, String modelName, String providerName,
                              boolean available, boolean isDefault, boolean selected) {
    }

    /**
     * 面板所需的三样东西。
     *
     * <p><b>为什么「选中的」与「生效的」要分成两个字段</b>：用户完全可能选中一条此刻不可用的档案
     * （例如供应商还没配密钥——这正是「先建档案、后补 Key」流程的正常中间态）。若只给一个
     * {@code currentId} 表示生效档案，那条选择就会被显示成「未选择」，下拉框跳回自动，
     * 用户以为自己的选择丢了、于是反复重选。{@code selectedId} 驱动下拉框的值，
     * {@code currentId/currentLabel} 表示「现在真会用哪个」，两者都由界面如实呈现。
     *
     * @param selectedId   用户选中的档案 id（可能不可用；null = 没选，自动挑）
     * @param currentId    此刻真正会使用的档案 id（null = 没有任何可用档案）
     * @param currentLabel 上者对用户可读的标签，形如「档案名/模型名」
     */
    public record ModelChoice(Long selectedId, Long currentId, String currentLabel, List<ModelOption> items) {
    }

    /**
     * 解析本次请求要用哪个档案作为链首。
     *
     * <p>优先级：请求里带的 {@code profileId}（用户在面板上临时选的）→ 本人保存的偏好 → null（按定义链）。
     * 请求里带了但档案不存在时**抛错**而不是回落：用户明确点了一个不存在的选项，
     * 悄悄用别的模型跑完，是最难排查的一类不一致。
     */
    private Long resolvePreferredProfile(Long userId, Long requestedProfileId) {
        if (requestedProfileId != null) {
            llmProfileService.required(requestedProfileId);
            return requestedProfileId;
        }
        AssistantPreference preference = preferenceMapper.findByUserId(userId);
        return preference == null ? null : preference.getProfileId();
    }

    /** 档案 id → 「档案名/模型名」；解析不到时返回 null（面板据此显示「自动选择」）。 */
    private String labelOf(Long profileId) {
        if (profileId == null) return null;
        try {
            ink.icoding.wechat.article.agent.LlmProfile profile = llmProfileService.required(profileId);
            return profile.getName() + "/" + profile.getModelName();
        } catch (RuntimeException exception) {
            log.warn("模型档案 {} 无法解析标签，面板将显示为自动选择", profileId);
            return null;
        }
    }

    private Map<String, Object> statePayload(String sessionId, String status, String message, String model) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("status", status);
        payload.put("message", message);
        if (model != null) payload.put("model", model);
        return payload;
    }

    /**
     * 流式对话。返回的 emitter 立刻可用，工作在同名守护线程里跑。
     *
     * <p>上下文（当前页面、文章 id）通过 {@code contextPrompt} 注入到**本轮指令前面**而不是
     * 系统提示里：系统提示是 {@code AgentProtocols} 的常量 + 人设，属于跨轮稳定内容，
     * 把会变的页面位置塞进去等于每轮都变，既破坏缓存也让会话序列化里堆满历史页面。
     */
    public SseEmitter chat(String instruction, AssistantContext context, Long profileId) {
        if (instruction == null || instruction.isBlank()) {
            throw new BusinessException("请输入要让助手做什么");
        }
        CurrentUser user = currentUserService.required();
        save(user.id(), "USER", instruction, "COMPLETED", 0, 0);

        SseEmitter emitter = new SseEmitter(0L);
        Talk talk = new Talk(UUID.randomUUID().toString(), user, emitter);
        emitter.onTimeout(() -> talk.close("助手会话已超时"));
        emitter.onError(error -> talk.close("助手连接已断开"));
        emitter.onCompletion(() -> talk.close("助手会话已结束"));
        talk.startHeartbeat();
        CompletableFuture.runAsync(() -> execute(talk, instruction, context, profileId));
        return emitter;
    }

    /** 当前页面上下文（前端每次请求带上）。 */
    public record AssistantContext(String path, String pageTitle, Long articleId) {
        static AssistantContext empty() {
            return new AssistantContext(null, null, null);
        }
    }

    private void execute(Talk talk, String instruction, AssistantContext context, Long profileId) {
        // 串行化：同一用户的两次提问排队。为什么按用户而不是全局：不同用户操作的是同一批数据，
        // 但他们的指令互不相关；真正的冲突由 ArticleService 的乐观锁在写入口兜住。
        Object lock = userLocks.computeIfAbsent(talk.user.id(), ignored -> new Object());
        synchronized (lock) {
            talk.llmLease = inFlightGate.acquire("全局助手会话 " + talk.id);
            if (talk.llmLease == null) {
                talk.send("error", Map.of("message", "LLM 并发额度等待超时（上限 " + inFlightGate.limit()
                        + "，已等待 " + inFlightGate.acquireTimeoutSeconds()
                        + " 秒）：同一时刻进行中的智能体运行占满了名额，请稍后重试。"));
                finish(talk);
                return;
            }
            executeWithAgent(talk, instruction, context, profileId);
        }
    }

    private void executeWithAgent(Talk talk, String instruction, AssistantContext context, Long profileId) {
        if (!llmConfigService.runtime().available()) {
            talk.send("error", Map.of("message",
                    "LLM 尚未配置可用的模型档案（请到「模型档案」页检查默认档案与它的供应商）"));
            finish(talk);
            return;
        }

        AtomicInteger inputTokens = new AtomicInteger();
        AtomicInteger outputTokens = new AtomicInteger();
        StringBuilder assistantText = new StringBuilder();
        try {
            // 先用「将要用的模型」发一次 state：面板据此显示模型名，用户不必等首字才知道用的哪个。
            // 真正跑起来后若发生故障切换，attempt(...) 会再发一次带新 model 的 state 覆盖它。
            Long preferred = resolvePreferredProfile(talk.user.id(), profileId);
            talk.send("state", statePayload(talk.id, "thinking", "助手正在理解你的要求…",
                    labelOf(preferred)));
            List<AgentRunner.Candidate> candidates = candidates(talk.user, preferred);
            if (candidates.isEmpty()) throw new BusinessException(
                    "当前账号没有可用的助手工具权限，请联系管理员分配角色");
            AssistantSession stored = sessionMapper.findByUserId(talk.user.id());
            Attempt attempt = attempt(candidates, stored, talk, command(instruction, context),
                    assistantText, inputTokens, outputTokens);
            String reply = attempt.response() == null || attempt.response().isBlank()
                    ? assistantText.toString().trim() : attempt.response().trim();
            if (reply.isBlank()) reply = "已完成。";

            persistSession(stored, attempt.agentSession(), talk.user.id());
            save(talk.user.id(), "ASSISTANT", reply, "COMPLETED",
                    inputTokens.get(), outputTokens.get());
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("message", reply);
            completed.put("toolCalls", talk.toolCalls.get());
            talk.send("completed", completed);
        } catch (Exception exception) {
            String message = readableError(exception);
            save(talk.user.id(), "ASSISTANT", "处理失败：" + message, "FAILED",
                    inputTokens.get(), outputTokens.get());
            talk.send("error", Map.of("message", message));
        } finally {
            finish(talk);
        }
    }

    /**
     * 跑一次会话；模型级错误且本次零工具调用时换下个档案重试。
     *
     * <p>零工具调用是硬前提：已经改过数据再换模型重跑，会把同一次改动做两遍；有历史会话时也不切换
     * （续接的上下文换模型接着写，行为不可预期）。与编辑器链路的判据逐字相同——两条链路
     * 面对的是同一类上游故障。
     */
    private Attempt attempt(List<AgentRunner.Candidate> candidates, AssistantSession stored, Talk talk,
                            String command, StringBuilder assistantText, AtomicInteger inputTokens,
                            AtomicInteger outputTokens) throws Exception {
        int index = 0;
        // 连接级瞬断的重试额度（每个候选一次），见 isTransientConnectionFailure 的说明。
        boolean retriedTransport = false;
        while (true) {
            AgentRunner.Candidate candidate = candidates.get(index);
            AgentClient agent = candidate.agent();
            AgentClientSession agentSession = stored == null
                    ? agent.createSession()
                    : agent.getSessionFromSerialization(stored.getSerializedSession());
            int callsBefore = talk.toolCalls.get();
            AgentSessionResult result = agentSession.command(command).then(handler(talk, assistantText,
                    inputTokens, outputTokens));
            try {
                StageTimeout.await(result, timeoutSeconds, "全局助手");
                return new Attempt(agentSession, result.get());
            } catch (Exception failure) {
                boolean noSideEffects = stored == null && talk.toolCalls.get() == callsBefore;
                // ① 连接级瞬断：**先原地重试同一档案**，不要急着换模型。
                //    实测形态：网关每轮发完就关掉 SSE 连接，客户端连接池里留下已失效的连接，
                //    下一轮复用它就得到「HTTP 200, Socket closed」。这类失败与模型无关，
                //    换档案既没必要（新档案照样会踩到死连接），还会让面板莫名其妙地换模型。
                if (noSideEffects && !retriedTransport && isTransientConnectionFailure(failure)) {
                    retriedTransport = true;
                    log.warn("全局助手连接瞬断（{}），原地重试一次：{}",
                            brief(failure), candidate.label());
                    talk.send("state", statePayload(talk.id, "retrying",
                            "与供应商的连接中断（" + brief(failure) + "），正在重试…",
                            candidate.label()));
                    continue;
                }
                boolean hasNext = index + 1 < candidates.size();
                if (noSideEffects && hasNext && isModelLevelFailure(failure)) {
                    String next = candidates.get(index + 1).label();
                    log.warn("全局助手模型档案不可用（{}），切换 {} → {}", failure.getMessage(),
                            candidate.label(), next);
                    // 切换后必须把新模型名一并发出：否则面板会一直显示失败的那个，
                    // 用户看到的「当前模型」与实际在跑的永远差一步——正是这次要修的观感问题。
                    talk.send("state", statePayload(talk.id, "switching_model",
                            "当前模型不可用（" + brief(failure) + "），已自动切换到 " + next, next));
                    index++;
                    continue;
                }
                throw failure;
            }
        }
    }

    /**
     * 本轮可用的工具组 → 候选智能体。
     *
     * <p>授权在这里落地，分两步取交集：
     * ① 定义声明的组（{@code agent_definition.tool_keys}）；
     * ② 当前角色允许的组（{@link AssistantRolePolicy}）。
     * 缺任一步都会出问题——只用 ①，管理员改了定义就能给 EDITOR 开写任务的能力；只用 ②，
     * 定义里删掉的工具组还会被默认值带回来。未知角色得到空集（fail-closed）。
     */
    private List<AgentRunner.Candidate> candidates(CurrentUser user, Long preferredProfileId) {
        List<String> allowed = AssistantRolePolicy.groupsFor(user.role());
        if (allowed.isEmpty()) {
            log.warn("全局助手：用户 {}（角色 {}）无任何允许的工具组，拒绝装配", user.id(), user.role());
            return List.of();
        }
        SkillContext skillContext = new SkillContext(List.of(), List.of(), List.of(), List.of(), null,
                SkillContext.Scene.SCHEDULED);
        SkillPromptResult skillPrompt = skillPromptAssembler.assemble(skillContext);
        AgentFactory.ToolResolver resolver = groups ->
                resolveTools(AssistantRolePolicy.intersect(groups, user.role()), user.id());
        return agentFactory.buildLabeledCandidatesByCode(AgentFactory.CODE_ASSISTANT, "ASSISTANT",
                        skillContext, skillPrompt, resolver, preferredProfileId).stream()
                .map(labeled -> new AgentRunner.Candidate(labeled.agent(), labeled.label(),
                        new ink.icoding.wechat.article.schedule.ToolCallGovernor()))
                .toList();
    }

    /**
     * 展开工具组为实例，并**再按组过滤一次**（fail-closed）。
     *
     * <p>为什么实例化了还要过滤：{@code filterByGroup} 是「组声明是唯一暴露源」这条约束的执行点。
     * 工具工厂返回的是该域的全部实例，若某天有人在工厂里多加一个工具却没在
     * {@link ToolRegistry} 里登记，过滤会把它剔除（而不是悄悄暴露出去）。
     */
    private List<Tool> resolveTools(List<String> groupKeys, Long userId) {
        List<Tool> all = new ArrayList<>();
        all.addAll(AssistantArticleTools.read(articleService, wechatAccountService, skillService,
                agentDefinitionService));
        all.addAll(AssistantArticleTools.write(articleService, userId));
        all.addAll(AssistantTaskTools.all(scheduleTaskService, userId));
        all.addAll(AssistantModelTools.all(llmProviderService, llmProfileService, userId));
        // 改智能体的模型绑定属于模型配置权限：与档案工具同组（ASSISTANT_MODEL），仅 ADMIN
        all.addAll(AssistantAgentTools.all(agentDefinitionService, llmProfileService, userId));
        all.addAll(AssistantSkillTools.all(skillService, userId));
        List<Tool> kept = new ArrayList<>();
        for (String groupKey : groupKeys) {
            for (Tool tool : toolRegistry.filterByGroup(groupKey, all)) {
                if (!kept.contains(tool)) kept.add(tool);
            }
        }
        return List.copyOf(kept);
    }

    private AgentResultHandler handler(Talk talk, StringBuilder assistantText,
                                       AtomicInteger inputTokens, AtomicInteger outputTokens) {
        return new AgentResultHandler() {
            @Override
            public void onMessage(String message) {
                assistantText.append(message);
                talk.send("delta", Map.of("content", message));
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
                talk.toolStatus(tool, status);
            }

            @Override
            public void onToolError(ToolDescriptor tool, Exception error) {
                talk.toolError(tool, error);
            }

            @Override
            public void onContextCompression(ContextCompressionStatus status, int before, int after) {
                talk.send("state", Map.of("sessionId", talk.id, "status", "compressing_context",
                        "message", status == ContextCompressionStatus.STARTED
                                ? "正在压缩较早的对话上下文…" : "对话上下文压缩完成"));
            }
        };
    }

    /**
     * 把「用户在哪一页」拼进本轮指令。
     *
     * <p>只拼一句定位信息，不拼页面 HTML 或列表数据：那些应当由模型用
     * {@code list_articles} / {@code read_article} 现取，既保证是当前值，也让「读过什么」
     * 在工具卡里对用户可见。
     */
    static String command(String instruction, AssistantContext context) {
        if (context == null) return instruction;
        StringBuilder prefix = new StringBuilder();
        if (context.pageTitle() != null && !context.pageTitle().isBlank()) {
            prefix.append("【用户当前所在页面：").append(context.pageTitle());
            if (context.path() != null && !context.path().isBlank()) {
                prefix.append("（").append(context.path()).append("）");
            }
            prefix.append("】");
        }
        if (context.articleId() != null) {
            prefix.append("【当前打开的文章 id=").append(context.articleId()).append("】");
        }
        if (prefix.isEmpty()) return instruction;
        return prefix.append('\n').append(instruction).toString();
    }

    private void persistSession(AssistantSession stored, AgentClientSession agentSession, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        if (stored == null) {
            AssistantSession created = new AssistantSession();
            created.setUserId(userId);
            created.setSerializedSession(agentSession.serialization());
            created.setUpdatedBy(userId);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            sessionMapper.insert(created);
            return;
        }
        stored.setSerializedSession(agentSession.serialization());
        stored.setUpdatedBy(userId);
        stored.setUpdatedAt(now);
        sessionMapper.updateById(stored);
    }

    private void save(Long userId, String role, String content, String status, int input, int output) {
        AssistantMessage message = new AssistantMessage();
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setStatus(status);
        message.setPromptTokens(input);
        message.setCompletionTokens(output);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private void finish(Talk talk) {
        if (talk.llmLease != null) talk.llmLease.close();
        talk.close("助手会话已结束");
        try {
            talk.emitter.complete();
        } catch (Exception ignored) {
            // 浏览器已断开时 complete() 会抛，属正常路径
        }
    }

    /** 模型级错误判据（与 ArticleAiService / AgentInvoker 同一清单）。 */
    /**
     * 连接级瞬断（可原地重试）。
     *
     * <p>为什么单独判一类：这类失败**与模型能力无关**——网关发完一轮就关闭 SSE 连接，
     * 客户端连接池里留下的死连接被下一轮复用，就得到 {@code HTTP 200, Socket closed}。
     * 把它当作「档案不可用」会去换模型，换完照样踩；而原地重试一次几乎必然成功，
     * 因为新建连接的过程会把这个死连接换掉。
     *
     * <p>为什么限定「本轮零工具调用」才重试：已经改过数据再重跑会把同一次改动做两遍。
     * 宁可让用户重说一遍，也不能悄悄重复写入。
     */
    static boolean isTransientConnectionFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("socket closed") || lower.contains("connection reset")
                        || lower.contains("broken pipe") || lower.contains("unexpected end of")
                        || lower.contains("premature end") || lower.contains("goaway")
                        || lower.contains("stream was reset")) {
                    return true;
                }
            }
            if (current.getCause() == current) break;
        }
        return false;
    }

    static boolean isModelLevelFailure(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable cause = failure;
        while (cause != null) {
            if (cause.getMessage() != null) text.append(cause.getMessage()).append('\n');
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        String message = text.toString().toLowerCase(java.util.Locale.ROOT);
        for (String keyword : new String[]{"model_not_found", "no available channel", "invalid_api_key",
                "401", "402", "403", "404"}) {
            if (message.contains(keyword)) return true;
        }
        return false;
    }

    /** 给用户看的错误摘要：业务异常直接用它的 message，其它取首行并截断。 */
    static String readableError(Throwable failure) {
        if (failure instanceof BusinessException business && business.getMessage() != null) {
            return business.getMessage();
        }
        // 停滞/超时是用户最可能遇到的一类失败，它的 message 已经是完整可读的中文句子
        if (failure instanceof ink.icoding.wechat.article.schedule.StageTimeoutException) {
            return failure.getMessage();
        }
        String raw = failure.getMessage() == null ? "" : failure.getMessage();
        // 上游 SDK 把整个 HTTP 响应体塞进异常 message（形如
        // 「SSE connection failed: HTTP 429: {"error":{"type":"rate_limit_error"…}}」），
        // 直接展示给用户就是一堆英文 JSON。这里按已知的几类故障给出可操作的中文，
        // 同时保留「哪个供应商/哪个码」的线索——排查时不至于完全没方向。
        String friendly = friendlyUpstream(raw);
        if (friendly != null) return friendly;
        String message = raw.isBlank() ? failure.getClass().getSimpleName() : raw;
        int newline = message.indexOf('\n');
        if (newline > 0) message = message.substring(0, newline);
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }

    /**
     * 已知上游故障 → 中文提示；认不出来返回 {@code null}（由调用方走原样截断）。
     *
     * <p>为什么用「包含关键字」而不是解析 JSON：异常 message 里的 JSON 经常被截断或转义，
     * 解析必然在某个版本上失败；而这几条关键字（429 / rate limit / 401 / timeout）在
     * OpenAI 兼容生态里是稳定的。宁可偶尔漏判（退回原始文本），也不要因为解析异常把
     * 真正的错误吞掉。
     */
    static String friendlyUpstream(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("429") || lower.contains("rate_limit") || lower.contains("tpm")
                || lower.contains("rpm limit") || lower.contains("exceeds tpm")) {
            return "当前模型触发了供应商的调用频率/额度限制（429）。请稍等一会儿重试，"
                    + "或在助手面板换一个模型档案；若持续出现，请到「模型档案」页检查该供应商的额度。";
        }
        if (lower.contains("401") || lower.contains("invalid_api_key") || lower.contains("unauthorized")) {
            return "模型供应商鉴权失败（401）：API Key 可能已失效或被撤销。"
                    + "请到「模型档案」页重新填写该供应商的密钥。";
        }
        if (lower.contains("403") || lower.contains("insufficient_quota") || lower.contains("quota")) {
            return "模型供应商拒绝访问（403/额度）：账户额度可能已用尽或该模型未开通。"
                    + "请到「模型档案」页检查供应商余额与模型权限。";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "等待模型响应超时。可以稍后重试，或在助手面板换一个更快的模型档案。";
        }
        if (lower.contains(" 500:") || lower.contains(" 502:") || lower.contains(" 503:")
                || lower.contains(" 504:") || lower.contains("service_unavailable")) {
            return "模型供应商暂时不可用（5xx），通常是上游过载或维护中。请稍后重试，"
                    + "或在助手面板换一个模型档案。";
        }
        if (lower.contains("socket closed") || lower.contains("connection reset")
                || lower.contains("premature end") || lower.contains("broken pipe")
                || lower.contains("unexpected end of")) {
            // 定位结论（探针实测，见 docs/dev/global-assistant-plan.md §12）：
            // 网关对「工具续话」请求完全接受（手工回放同一份报文返回 200）；
            // Socket closed 是**本地**连接被关闭的信号——供应商每轮发完就关连接，
            // 我们复用了连接池里的死连接。所以既不是模型不行，也不是上下文太大。
            // 现在连接级瞬断会自动原地重试；仍然失败才提示用户。
            return "与模型供应商的连接被提前中断（Socket closed）。这是连接层的问题"
                    + "（长连接被复用后失效），不是模型能力或上下文大小的问题。"
                    + "系统已自动重试过一次仍失败，请再发一次这条指令。";
        }
        if (lower.contains("connection refused") || lower.contains("unknownhost")
                || lower.contains("no such host")) {
            return "连不上模型供应商的接口地址，请到「模型档案」页检查该供应商的 baseUrl。";
        }
        return null;
    }

    /**
     * 切换提示里「当前模型不可用（…）」括号内的**短原因**。
     *
     * <p>这里刻意不用 {@link #friendlyUpstream} 的整句：那句话是给最终 error 事件用的，
     * 塞进「（…）」里会变成「（模型供应商暂时不可用（5xx），通常是上游过载…）」——
     * 双层括号加一整句操作指引，比原来的英文 JSON 还难读。括号里只要一句短语。
     * 认不出来的错误退回截断原文，保留排查线索。
     */
    private static String brief(Throwable failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        String reason = shortReason(message);
        if (reason != null) return reason;
        return message.length() > 80 ? message.substring(0, 80) + "…" : message;
    }

    /**
     * 短原因（括号用）。与 {@link #friendlyUpstream} 同一套关键字，但只给短语、不给操作指引，
     * 也**不**带状态码——它外面已经套了一层「当前模型不可用（…）」，再写「（5xx）」就是双重括号。
     * 两处判定必须同源：分开写迟早漂移成「这里说限流、那里说不超时」。
     */
    static String shortReason(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("429") || lower.contains("rate_limit") || lower.contains("tpm")
                || lower.contains("rpm limit") || lower.contains("exceeds tpm")) {
            return "供应商限流或额度不足";
        }
        if (lower.contains("401") || lower.contains("invalid_api_key") || lower.contains("unauthorized")) {
            return "供应商鉴权失败";
        }
        if (lower.contains("403") || lower.contains("insufficient_quota") || lower.contains("quota")) {
            return "供应商额度用尽或拒绝访问";
        }
        if (lower.contains(" 500:") || lower.contains(" 502:") || lower.contains(" 503:")
                || lower.contains(" 504:") || lower.contains("service_unavailable")) {
            return "供应商暂时不可用";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) return "等待响应超时";
        if (lower.contains("socket closed") || lower.contains("connection reset")
                || lower.contains("premature end") || lower.contains("broken pipe")
                || lower.contains("unexpected end of")) {
            return "连接被中断";
        }
        if (lower.contains("connection refused") || lower.contains("unknownhost")
                || lower.contains("no such host")) {
            return "连不上供应商地址";
        }
        return null;
    }

    private record Attempt(AgentClientSession agentSession, String response) {
    }

    /**
     * 一次对话的工作状态：SSE 发送、工具计数、心跳与关闭标记。
     *
     * <p>为什么把 send 包一层：SSE 在浏览器关页后写入会抛 IOException，而这条路径散布在
     * 回调里（模型 delta、工具状态），逐处 try/catch 会把真正的逻辑淹掉。统一在这里吞掉，
     * 并用 {@code closed} 标记后续不再尝试。
     */
    private final class Talk {
        private final String id;
        private final CurrentUser user;
        private final SseEmitter emitter;
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final Set<String> completedTools = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final Map<String, String> toolCallIds = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger toolSequence = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile java.util.concurrent.ScheduledFuture<?> heartbeat;
        private volatile ink.icoding.wechat.article.common.InFlightGate.Lease llmLease;

        private Talk(String id, CurrentUser user, SseEmitter emitter) {
            this.id = id;
            this.user = user;
            this.emitter = emitter;
        }

        private void startHeartbeat() {
            heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> send("heartbeat", Map.of()),
                    HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        }

        private void send(String event, Map<String, Object> data) {
            if (closed.get()) return;
            try {
                emitter.send(SseEmitter.event().name(event).data(MAPPER.writeValueAsString(data),
                        org.springframework.http.MediaType.TEXT_PLAIN));
            } catch (Exception exception) {
                if (closed.compareAndSet(false, true)) {
                    log.debug("助手会话 {} 的 SSE 连接已断开：{}", id, exception.getMessage());
                }
            }
        }

        private void toolStatus(ToolDescriptor tool, ToolStatus status) {
            if (status == ToolStatus.PREPARING) return;
            String key = tool.getName() + "\n" + safeCallId(tool);
            boolean stable = !safeCallId(tool).isBlank();
            if (stable && completedTools.contains(key)) return;
            if (status == ToolStatus.CALLING) {
                int count = toolCalls.incrementAndGet();
                if (count > maxToolCalls) {
                    throw new IllegalStateException("单轮最多调用 " + maxToolCalls
                            + " 次工具，请把操作拆成几轮再试");
                }
                String callId = toolCallIds.computeIfAbsent(key,
                        ignored -> "assistant-tool-" + toolSequence.incrementAndGet());
                send("tool.call", Map.of("sessionId", id, "callId", callId,
                        "name", tool.getName(), "arguments", Map.of()));
            } else if (status == ToolStatus.COMPLETED) {
                String callId = toolCallIds.remove(key);
                if (callId != null) {
                    if (stable) completedTools.add(key);
                    send("tool.result", Map.of("callId", callId, "name", tool.getName(),
                            "status", "COMPLETED", "message", "工具执行完成"));
                }
            }
        }

        private void toolError(ToolDescriptor tool, Exception error) {
            String key = tool.getName() + "\n" + safeCallId(tool);
            boolean stable = !safeCallId(tool).isBlank();
            if (stable && !completedTools.add(key)) return;
            String callId = toolCallIds.remove(key);
            if (callId == null) callId = "assistant-tool-" + toolSequence.incrementAndGet();
            send("tool.result", Map.of("callId", callId, "name", tool.getName(), "status", "FAILED",
                    "message", error.getMessage() == null ? "工具执行失败" : error.getMessage()));
        }

        private void close(String reason) {
            closed.set(true);
            java.util.concurrent.ScheduledFuture<?> task = heartbeat;
            if (task != null) task.cancel(false);
            log.debug("助手会话 {} 结束：{}", id, reason);
        }
    }

    private static String safeCallId(ToolDescriptor tool) {
        try {
            return tool.getCallId() == null ? "" : tool.getCallId();
        } catch (Exception ignored) {
            return "";
        }
    }
}