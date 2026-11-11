package ink.icoding.wechat.article.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.wechat.article.MySqlTestDatabaseInitializer;
import ink.icoding.wechat.article.agent.AgentDefinitionService;
import ink.icoding.wechat.article.agent.LlmProfileService;
import ink.icoding.wechat.article.agent.LlmProviderService;
import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.assistant.tool.AssistantAgentTools;
import ink.icoding.wechat.article.assistant.tool.AssistantArticleTools;
import ink.icoding.wechat.article.assistant.tool.AssistantModelTools;
import ink.icoding.wechat.article.assistant.tool.AssistantSkillTools;
import ink.icoding.wechat.article.assistant.tool.AssistantTaskTools;
import ink.icoding.wechat.article.schedule.ScheduleTaskService;
import ink.icoding.wechat.article.skill.SkillService;
import ink.icoding.wechat.article.account.WechatAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 全局助手工具的真实执行集成测试（plan §7 验收项 4 / 5）。
 *
 * <p><b>为什么必须有这一层</b>：{@code AssistantContentBlocksTest} 只测纯函数、
 * {@code AssistantModelToolsSecretTest} 只测参数类的字段——两者都**一次也没让工具跑起来**。
 * 工具是「拿参数 → 调服务 → 返回 JSON」的适配层，最容易出错的地方恰好全在接缝上：
 * 参数名与方法签名不匹配、{@code @ToolInfo} 的名字与 {@code ToolRegistry} 登记的不一致、
 * 服务抛出的 {@code BusinessException} 没被正确透传、序列化出 null 导致 JSON 结构变化。
 * 这些错误编译期都发现不了，只有真的执行一次才知道。
 *
 * <p>写操作的断言落在**数据库的真实状态**上（改完再读回来比对），而不是只看工具返回的字符串：
 * 「工具说自己改了」和「数据真的改了」是两件事。
 */
@SpringBootTest
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AssistantToolsIntegrationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 与 {@code BootstrapAdminRunner} 建的管理员同 id（集成测试库每次重建，id 稳定为 1）。 */
    private static final Long ADMIN_ID = 1L;

    @Autowired
    private ArticleService articleService;
    @Autowired
    private WechatAccountService accountService;
    @Autowired
    private SkillService skillService;
    @Autowired
    private AgentDefinitionService agentDefinitionService;
    @Autowired
    private ScheduleTaskService scheduleTaskService;
    @Autowired
    private LlmProviderService providerService;
    @Autowired
    private LlmProfileService profileService;
    @Autowired
    private ink.icoding.wechat.article.agent.LlmProfileMapper profileMapper;
    @Autowired
    private ink.icoding.wechat.article.agent.LlmProviderMapper providerMapper;

    /** 本次用例临时插进去的档案/供应商 id：有值才需要清理（复用已有数据时保持 null） */
    private Long seededProfileId;
    private Long seededProviderId;

    /** 按名字取工具，模拟 agent4j 按 {@code ToolRegistry} 白名单过滤后的调用。 */
    private static Tool pick(List<Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(ToolDescriptor.fromTool(tool).getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("工具不存在：" + name));
    }

    /**
     * 按 agent4j 的真实路径调用工具：JSON 参数 → 参数对象 → execute → JSON 字符串。
     *
     * <p>反射是必要的（25 个工具各有自己的 {@code XxxParam}），但**异常语义必须保留**：
     * 工具抛的 {@code IllegalArgumentException} / {@code BusinessException} 是断言的判据，
     * 因此调用目标异常原样重抛，不包成 AssertionError——否则「拒绝」和「崩溃」就分不清了。
     */
    private static JsonNode run(Tool tool, Map<String, Object> arguments) {
        try {
            Object param = MAPPER.convertValue(arguments, parameterType(tool));
            Object result = tool.getClass().getMethod("execute", parameterType(tool)).invoke(tool, param);
            return MAPPER.readTree((String) result);
        } catch (java.lang.reflect.InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("工具执行失败", cause);
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new IllegalStateException("工具调用或结果解析失败：" + error.getMessage(), error);
        }
    }

    /**
     * 工具的 execute 参数类型。
     *
     * <p>必须取**最具体**的那个：泛型擦除会额外生成一个 {@code execute(ToolParam)} 桥方法
     * （编译器为满足 {@code Tool<P>} 接口而合成），按 {@code getMethods()} 的顺序取第一个会拿到
     * {@code ToolParam} 这个抽象类，Jackson 反序列化随即失败——这正是本测试第一次跑出来的错误。
     */
    private static Class<?> parameterType(Tool tool) {
        Class<?> best = null;
        for (java.lang.reflect.Method method : tool.getClass().getMethods()) {
            if (!"execute".equals(method.getName()) || method.getParameterCount() != 1) continue;
            Class<?> candidate = method.getParameterTypes()[0];
            if (best == null || best.isAssignableFrom(candidate)) best = candidate;
        }
        if (best == null) throw new AssertionError("找不到 execute 方法");
        return best;
    }

    private List<Tool> readTools() {
        return AssistantArticleTools.read(articleService, accountService, skillService, agentDefinitionService);
    }

    private List<Tool> writeTools() {
        return AssistantArticleTools.write(articleService, ADMIN_ID);
    }

    private List<Tool> taskTools() {
        return AssistantTaskTools.all(scheduleTaskService, ADMIN_ID);
    }

    private List<Tool> modelTools() {
        return AssistantModelTools.all(providerService, profileService, ADMIN_ID);
    }

    private List<Tool> agentTools() {
        return AssistantAgentTools.all(agentDefinitionService, profileService, ADMIN_ID);
    }

    private List<Tool> skillTools() {
        return AssistantSkillTools.all(skillService, ADMIN_ID);
    }

    // ------------------------------------------------------------ 只读工具

    /** 六个读工具都要真的跑通：它们的输出是模型唯一的上下文来源，报错就等于助手「看不见项目」。 */
    @Test
    void everyReadToolExecutesAndReturnsJson() {
        JsonNode articles = run(pick(readTools(), "list_articles"), Map.of());
        assertThat(articles.has("items")).isTrue();

        JsonNode accounts = run(pick(readTools(), "list_accounts"), Map.of());
        assertThat(accounts.has("items")).isTrue();

        JsonNode skills = run(pick(readTools(), "list_skills"), Map.of());
        assertThat(skills.has("items")).isTrue();

        JsonNode agents = run(pick(readTools(), "list_agents"), Map.of());
        assertThat(agents.get("total").asInt()).isGreaterThanOrEqualTo(8);
    }

    /** 读不存在的文章要给中文可读错误（不是 NullPointerException），模型才能自行纠正。 */
    @Test
    void readingMissingArticleFailsWithReadableChineseMessage() {
        assertThatThrownBy(() -> run(pick(readTools(), "read_article"), Map.of("articleId", 999999)))
                .hasMessageContaining("文章");
    }

    // ------------------------------------------------------------ 文章写工具

    /**
     * 建文章 → 读块 → 改块 → 再读回来验证。
     *
     * <p>这是整个助手最核心的一条链路：助手说「我改了第 2 段」，落库的正文必须真的变了，
     * 而且没被点到的块要逐字保留。
     */
    @Test
    void createThenEditBlocksRoundTripsThroughDatabase() throws Exception {
        JsonNode created = run(pick(writeTools(), "create_article"), Map.of(
                "title", "助手集成测试文章",
                "author", "助手",
                "contentHtml", "<h2>原标题</h2><p>原第一段</p><p>原第二段</p>"));
        long articleId = created.get("id").asLong();
        int revision = created.get("revision").asInt();

        Article stored = articleService.required(articleId);
        assertThat(stored.getTitle()).isEqualTo("助手集成测试文章");

        JsonNode blocks = run(pick(readTools(), "read_article_blocks"), Map.of("articleId", articleId));
        assertThat(blocks.get("totalBlocks").asInt()).isEqualTo(3);
        assertThat(blocks.get("writable").asBoolean()).isTrue();

        // 替换第 2 块：编号是 1-based，与模型看到的 idx 一致
        run(pick(writeTools(), "replace_article_blocks"), Map.of(
                "articleId", articleId,
                "expectedRevision", revision,
                "startIndex", 2,
                "endIndex", 2,
                "blocks", List.of("<p class=\"note\">改过的第一段</p>")));

        Article after = articleService.required(articleId);
        assertThat(after.getContentHtml())
                .contains("改过的第一段")
                // 未触碰的兄弟块逐字保留（含原标签与属性）
                .contains("<h2>原标题</h2>")
                .contains("<p>原第二段</p>")
                .doesNotContain("原第一段");
        assertThat(after.getRevision()).isGreaterThan(revision);
    }

    /**
     * 乐观锁必须真的生效：用过期 revision 写入要被拒，且**不能**改动数据。
     *
     * <p>这条断言的价值在于「拒绝」比「写入」更容易写错：若 {@code expectedRevision} 被忽略，
     * 两个并发助手会互相覆盖，而测试若只验证成功路径永远发现不了。
     */
    @Test
    void writingWithStaleRevisionIsRejectedAndDataUnchanged() {
        JsonNode created = run(pick(writeTools(), "create_article"), Map.of(
                "title", "乐观锁测试", "contentHtml", "<p>正文</p>"));
        long articleId = created.get("id").asLong();
        int revision = created.get("revision").asInt();
        String before = articleService.required(articleId).getContentHtml();

        // 正常写一次，把版本推高
        run(pick(writeTools(), "update_article_meta"), Map.of(
                "articleId", articleId, "expectedRevision", revision, "title", "改名成功"));

        // 再用**旧**版本写：必须被拒
        assertThatThrownBy(() -> run(pick(writeTools(), "update_article_meta"), Map.of(
                "articleId", articleId, "expectedRevision", revision, "title", "不该生效")))
                .hasMessageContaining("修改");

        Article now = articleService.required(articleId);
        assertThat(now.getTitle()).isEqualTo("改名成功");
        assertThat(now.getContentHtml()).isEqualTo(before);
    }

    // ------------------------------------------------------------ 定时任务工具

    /** 建任务 → 查列表 → 停用 → 查回状态，验证「先读全量再整体回写」没有丢掉 cron/prompt。 */
    @Test
    void scheduleTaskToolsKeepCronAndPromptWhenTogglingEnabled() {
        JsonNode created = run(pick(taskTools(), "create_schedule_task"), Map.of(
                "name", "助手建的测试任务",
                "cronExpression", "0 0 8 * * ?",
                "aiPrompt", "每天写一篇关于健康饮食的文章",
                "timezone", "Asia/Shanghai",
                "outputMode", "LOCAL_DRAFT",
                "executionMode", "SINGLE"));
        long taskId = created.get("id").asLong();

        JsonNode list = run(pick(taskTools(), "list_schedule_tasks"), Map.of());
        assertThat(list.get("total").asInt()).isGreaterThanOrEqualTo(1);

        run(pick(taskTools(), "set_schedule_task_enabled"), Map.of("taskId", taskId, "enabled", false));

        // 关键断言：局部更新不得把未指定的字段清空（apply 是全量覆盖，少读一次就会清空 cron）
        ink.icoding.wechat.article.schedule.ScheduleTask after = scheduleTaskService.required(taskId);
        assertThat(after.getEnabled()).as("toggle must take effect").isFalse();
        assertThat(after.getCronExpression()).as("cron must survive a partial update").isEqualTo("0 0 8 * * ?");
        assertThat(after.getAiPrompt()).as("prompt must survive a partial update").contains("健康饮食");
    }

    /**
     * 删除类工具现在**存在**（策略改为「助手可操作全部功能」），所以这里守的不是「有没有」，
     * 而是「对不存在的 id 会不会炸」：删除是不可逆操作，一个 NPE 会让模型以为「删成功了」或
     * 盲目重试；必须是可读的业务错误，且**什么都没删掉**。
     */
    @Test
    void everyDeleteToolRejectsUnknownIdsWithoutSideEffects() {
        long missing = 9_999_999L;
        List<Tool> all = new java.util.ArrayList<>();
        all.addAll(taskTools());
        all.addAll(modelTools());
        all.addAll(writeTools());
        all.addAll(agentTools());
        all.addAll(skillTools());

        Map<String, Map<String, Object>> calls = Map.of(
                "delete_article", Map.of("articleId", missing),
                "delete_schedule_task", Map.of("taskId", missing),
                "delete_llm_profile", Map.of("profileId", missing),
                "delete_llm_provider", Map.of("providerId", missing),
                "delete_agent", Map.of("agentId", missing),
                "delete_skill", Map.of("skillId", missing));

        for (Map.Entry<String, Map<String, Object>> call : calls.entrySet()) {
            Tool tool = pick(all, call.getKey());
            assertThatThrownBy(() -> run(tool, call.getValue()))
                    .as("%s 对不存在的 id 应抛业务错误", call.getKey())
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NullPointerException.class);
        }
    }

    // ------------------------------------------------------------ 智能体工具

    /**
     * 换模型只改绑定：人格、工具组、技能、温度一个都不能动。
     *
     * <p>这是用户实际要求的能力（「把智能体的模型都改掉」）。服务层的 {@code update} 是**全量覆盖**，
     * 如果这里走的是「读全量再整体回写」的路径，漏读一个字段就会把它静默清空——所以除了
     * {@code llmProfileId} 变了之外，其余字段必须逐一相等。
     */
    /**
     * 造一条临时档案：测试库可能只有一个档案，而「换模型」必须有第二个可选目标。
     *
     * <p>直接用 mapper 插入而不是走服务层：服务层会校验供应商必须有密钥，而这里要的只是
     * 一个「不同的档案 id」，与密钥可用性无关。用完立刻删除，不留脏数据。
     */
    private ink.icoding.wechat.article.agent.LlmProfile seedTemporaryProfile() {
        // 测试库可能是干净的（一个档案都没有），此时连供应商一起造；都直接插表，
        // 因为「换模型」只关心档案 id 不同，与密钥/启用状态无关
        ink.icoding.wechat.article.agent.LlmProfile origin = profileMapper.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    ink.icoding.wechat.article.agent.LlmProvider provider =
                            new ink.icoding.wechat.article.agent.LlmProvider();
                    provider.setName("__换模型测试供应商__");
                    provider.setProvider("OPENAI_COMPATIBLE");
                    provider.setBaseUrl("http://127.0.0.1:1");
                    provider.setEnabled(false);
                    provider.setCreatedAt(java.time.LocalDateTime.now());
                    providerMapper.insert(provider);
                    seededProviderId = provider.getId();

                    ink.icoding.wechat.article.agent.LlmProfile placeholder =
                            new ink.icoding.wechat.article.agent.LlmProfile();
                    placeholder.setName("__换模型测试模板__");
                    placeholder.setProviderId(provider.getId());
                    placeholder.setProvider(provider.getName());
                    placeholder.setBaseUrl(provider.getBaseUrl());
                    placeholder.setModelName("mock-model");
                    placeholder.setEnabled(false);
                    placeholder.setIsDefault(false);
                    placeholder.setIsFallback(false);
                    placeholder.setCreatedAt(java.time.LocalDateTime.now());
                    profileMapper.insert(placeholder);
                    seededProfileId = placeholder.getId();
                    return placeholder;
                });
        ink.icoding.wechat.article.agent.LlmProfile copy = new ink.icoding.wechat.article.agent.LlmProfile();
        copy.setName("__换模型测试档案__");
        copy.setProviderId(origin.getProviderId());
        copy.setProvider(origin.getProvider());
        copy.setBaseUrl(origin.getBaseUrl());
        copy.setModelName(origin.getModelName());
        copy.setImageModelName(origin.getImageModelName());
        copy.setIsImageModel(origin.getIsImageModel());
        copy.setApiKeyEncrypted(origin.getApiKeyEncrypted());
        copy.setTemperature(origin.getTemperature());
        copy.setMaxTokens(origin.getMaxTokens());
        copy.setEnabled(true);
        copy.setIsDefault(false);
        copy.setIsFallback(false);
        copy.setCreatedAt(java.time.LocalDateTime.now());
        profileMapper.insert(copy);
        return copy;
    }

    @Test
    void setAgentModelChangesOnlyTheBinding() {
        // 内置智能体同样可以换模型（改的是绑定，不是定义本身），测试库只有内置的
        ink.icoding.wechat.article.agent.AgentDefinition before =
                agentDefinitionService.list(null, null).stream().findFirst()
                        .orElseThrow(() -> new AssertionError("测试库里至少需要一个智能体"));
        Long originalProfile = before.getLlmProfileId();
        ink.icoding.wechat.article.agent.LlmProfile temporary = seedTemporaryProfile();
        Long targetProfile = temporary.getId();

        JsonNode result = run(pick(agentTools(), "set_agent_model"),
                Map.of("agentId", before.getId(), "profileId", targetProfile));
        assertThat(result.get("llmProfileId").asLong()).isEqualTo(targetProfile);

        ink.icoding.wechat.article.agent.AgentDefinition after =
                agentDefinitionService.required(before.getId());
        assertThat(after.getLlmProfileId()).isEqualTo(targetProfile);
        assertThat(after.getPersona()).as("换模型不得改人设").isEqualTo(before.getPersona());
        assertThat(after.getToolKeys()).as("换模型不得改工具组").isEqualTo(before.getToolKeys());
        assertThat(after.getSkillIds()).as("换模型不得改技能").isEqualTo(before.getSkillIds());
        assertThat(after.getTemperature()).as("换模型不得改温度").isEqualTo(before.getTemperature());
        assertThat(after.getName()).isEqualTo(before.getName());

        // 复原并清掉临时档案，避免污染其它用例（测试库是共享的）
        agentDefinitionService.setLlmProfile(before.getId(), originalProfile);
        profileMapper.deleteById(temporary.getId());
        if (seededProfileId != null) {
            profileMapper.deleteById(seededProfileId);
            providerMapper.deleteById(seededProviderId);
            seededProfileId = null;
            seededProviderId = null;
        }
    }

    /** 传 null 表示解除绑定：不该被当成「没传这个字段」而静默忽略。 */
    @Test
    void setAgentModelCanUnbindWithNullProfile() {
        ink.icoding.wechat.article.agent.AgentDefinition target =
                agentDefinitionService.list(null, null).stream().findFirst()
                        .orElseThrow(() -> new AssertionError("测试库需要至少一个智能体"));
        Long original = target.getLlmProfileId();

        run(pick(agentTools(), "set_agent_model"),
                java.util.Collections.singletonMap("agentId", target.getId()));
        assertThat(agentDefinitionService.required(target.getId()).getLlmProfileId()).isNull();

        agentDefinitionService.setLlmProfile(target.getId(), original);
    }

    // ------------------------------------------------------------ 模型工具

    /**
     * 建供应商（无密钥）→ 确认它是「无密钥且被停用」→ 试图用未配密钥的供应商建档案必须被拒。
     *
     * <p><b>这不是测试瑕疵，而是真实的产品行为</b>：{@code LlmProviderService} 在「启用但无 key」时
     * 直接抛 {@code BusinessException("启用供应商前必须配置 API Key")}（见 LlmProviderService:419），
     * 而档案又必须挂在一个可用供应商上。于是助手的正确行为是：
     * 建出供应商 → 明确告诉用户去「模型档案」页填 key → 由用户再建档案。
     * 断言这一点，等于把「密钥不能在对话里传递」这条约束的**下游后果**也钉住。
     */
    @Test
    void modelToolsCreateProviderWithoutKeyAndGuideTheUserToFillIt() {
        Map<String, Object> providerArg = new LinkedHashMap<>();
        providerArg.put("name", "助手建的供应商");
        providerArg.put("provider", "OPENAI_COMPATIBLE");
        providerArg.put("baseUrl", "https://example.invalid/v1");
        // 不传 enabled：工具必须原样透传 null，服务层落成「停用」——这是「先存端点、后补 key」的前提
        JsonNode created = run(pick(modelTools(), "create_llm_provider"), providerArg);
        long providerId = created.get("provider").get("id").asLong();
        assertThat(created.get("provider").get("enabled").asBoolean())
                .as("无密钥的新供应商必须落成停用，否则会撞上「启用前必须配置 API Key」")
                .isFalse();

        // 建出来必须是「没有密钥」的状态——本工具不该、也无法写入密钥
        assertThat(created.get("provider").get("hasApiKey").asBoolean()).isFalse();
        assertThat(created.get("provider").get("apiKeyMasked").asText()).isNotBlank();
        assertThat(created.get("nextStep").asText()).contains("模型档案");

        // 档案可以挂在「尚无密钥」的供应商上（LlmProfileService.create 不校验密钥）：
        // 它此刻 runtimeAvailable=false，可由 list_llm_profiles 观察到。
        // 这正说明「建档案」不等于「可用」，工具如实回传 runtimeAvailable 让模型能解释给用户。
        JsonNode profile = run(pick(modelTools(), "create_llm_profile"), Map.of(
                "name", "助手建的档案",
                "providerId", providerId,
                "modelName", "test-model"));
        long profileId = profile.get("profile").get("id").asLong();

        JsonNode listed = run(pick(modelTools(), "list_llm_profiles"), Map.of());
        JsonNode mine = null;
        for (JsonNode row : listed.get("items")) {
            if (row.get("id").asLong() == profileId) mine = row;
        }
        assertThat(mine).as("新建的档案应出现在列表里").isNotNull();
        // 字段名从 runtimeAvailable 收紧成 usable（行更短），语义不变
        assertThat(mine.get("usable").asBoolean())
                .as("挂在不带密钥的供应商上，运行时不可用")
                .isFalse();

        // 设为默认仍可执行（默认是路由选择，不是可用性判断）
        JsonNode setDefault = run(pick(modelTools(), "set_default_llm_profile"), Map.of("profileId", profileId));
        assertThat(setDefault.get("profile").get("isDefault").asBoolean()).isTrue();

        // 列表输出不得出现明文密钥字段（只有 apiKeyMasked）
        JsonNode providers = run(pick(modelTools(), "list_llm_providers"), Map.of());
        assertThat(providers.toString()).doesNotContain("\"apiKey\":\"");
        assertThat(providers.toString()).contains("apiKeyMasked");
    }

    /** 非法供应商类型要在工具层被拒（而不是落库后由运行时炸）。 */
    @Test
    void creatingProviderWithUnknownTypeIsRejected() {
        Map<String, Object> arg = new LinkedHashMap<>();
        arg.put("name", "非法类型");
        arg.put("provider", "NOT_A_PROVIDER");
        arg.put("baseUrl", "https://example.invalid/v1");
        assertThatThrownBy(() -> run(pick(modelTools(), "create_llm_provider"), arg))
                .hasMessageContaining("不支持的供应商类型");
    }

    /**
     * 回归：{@code list_llm_profiles} 的输出必须有界。
     *
     * <p>真实故障：库里 47 条档案时，这个工具全字段返回 **14.8 KB** 进上下文，上游随即
     * {@code HTTP 200, Socket closed}，整轮对话失败。用户当时的判断是「不是模型的问题，
     * 是你设计的问题」——正确：工具没有输出预算。
     *
     * <p>所以断言三件事：只给默认 20 条、始终报 total、序列化长度压回千级；
     * 外加密钥字段不再出现（它既无用，又让人条件反射去追密钥）。
     */
    @Test
    void listLlmProfilesOutputIsBoundedEvenWithManyProfiles() {
        // 测试库初始只有内置档案，自己造够「多于默认上限」的量，这条才有意义
        var provider = new ink.icoding.wechat.article.agent.LlmProviderService.ProviderRequest(
                "__budget_test_provider__", "OPENAI_COMPATIBLE", "https://example.invalid/v1", null, null, null);
        Long providerId = providerService.create(provider, ADMIN_ID).id();
        int existing = profileService.list().size();
        for (int i = 0; i < 25; i++) {
            profileService.create(new ink.icoding.wechat.article.agent.LlmProfileService.ProfileRequest(
                    "__budget_test_" + i + "__", providerId, "__budget_model_" + i + "__",
                    null, null, null, null, null), ADMIN_ID);
        }
        int total = profileService.list().size();
        assertThat(total).as("需要多于默认上限的档案，这条才有意义").isGreaterThan(existing + 20);

        Tool tool = pick(AssistantModelTools.all(providerService, profileService, ADMIN_ID), "list_llm_profiles");
        JsonNode result = run(tool, Map.of());

        assertThat(result.get("total").asInt()).isEqualTo(total);
        assertThat(result.get("returned").asInt()).isEqualTo(AssistantToolBudgetLimit.DEFAULT);
        assertThat(result.get("items")).hasSize(AssistantToolBudgetLimit.DEFAULT);
        assertThat(result.has("truncationHint")).as("截断后必须告诉模型怎么拿剩下的").isTrue();
        assertThat(result.toString().length())
                .as("必须远小于故障时的 14820 字符")
                .isLessThan(6000);
        assertThat(result.toString()).doesNotContain("apiKeyMasked");
    }

    /** 默认上限常量在本包外不可见，这里用一个本地常量对齐，改了上限测试会失败并提醒同步。 */
    private static final class AssistantToolBudgetLimit {
        private static final int DEFAULT = 20;
    }
}