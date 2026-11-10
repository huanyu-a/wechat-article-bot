package ink.icoding.wechat.article.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前后端 JSON 契约回归（skills-agent-plan 6.6 / 8.2）：
 * 前端读取 `isDefault` / `isBuiltin` 驼峰字段，record 的 `isXxx` 访问器若被 Jackson 剥离前缀会静默变成
 * `default`，导致「默认档案」徽标与删除保护失效。这里用真实 ObjectMapper 锁死字段名。
 *
 * <p>本轮（llm-provider-refactor §5.2）档案视图改形：去掉 `provider`/`baseUrl`，增加
 * `providerId`/`providerName`/`providerEnabled`。前端据此把档案落到供应商分组下、
 * 并判断「供应商已不存在」的告警态，因此这三个键的存在性与命名同样属于前后端契约。
 */
class AgentJsonContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void profileViewSerializesIsDefault() throws Exception {
        LlmProfileService.ProfileView view = new LlmProfileService.ProfileView(
                1L, "默认配置", 7L, "nexus", true, "gpt-4.1-mini",
                true, "••••••••abcd", new BigDecimal("0.7"), 4096, true, true, false, null, false, null, true);
        String json = mapper.writeValueAsString(view);
        assertThat(json).contains("\"isDefault\":true").doesNotContain("\"default\":");
        assertThat(json).doesNotContain("apiKey\""); // 不得回传完整 key
    }

    /**
     * 图片通道的两个字段都要出现在档案响应里，且字段名逐字固定。
     *
     * <p>{@code isImageModel}（契约 §4.3 定稿）是界面唯一还会读写的那个：模型卡片上的复选框。
     * 它必须序列化成**布尔值**而不是字符串或 {@code 0/1}，否则前端 {@code v-model} 拿到
     * {@code "false"} 这种真值，复选框会一直显示为勾上。
     *
     * <p>{@code imageModelName} 已经**退出界面**（第二版设计被用户否决），但字段必须留着：
     * ①b 旧数据兼容要读它，且历史请求/历史行里可能带着值。它是可空的，因此额外钉住
     * 「值为 null 时序列化成 {@code null} 而不是字段消失」——字段一旦消失，反序列化侧的
     * 旧请求就再也读不到它，兼容段也就断了。
     */
    @Test
    void profileViewSerializesImageChannelFields() throws Exception {
        LlmProfileService.ProfileView imageModel = new LlmProfileService.ProfileView(
                4L, "配图档", 7L, "nexus", true, "gpt-image-1",
                true, "••••••••abcd", null, null, true, false, false, null, true, null, true);
        String json = mapper.writeValueAsString(imageModel);
        assertThat(json)
                .as("勾选态必须是布尔 true，前端复选框才认得出")
                .contains("\"isImageModel\":true");

        LlmProfileService.ProfileView textOnly = new LlmProfileService.ProfileView(
                5L, "纯文本档", 7L, "nexus", true, "deepseek-flash",
                true, "••••••••abcd", null, null, true, false, false, null, false, null, true);
        String textJson = mapper.writeValueAsString(textOnly);
        assertThat(textJson).contains("\"isImageModel\":false");
        assertThat(textJson)
                .as("①b 兼容段依赖这个键存在；消失则历史请求与历史行都读不到")
                .contains("\"imageModelName\":null");
    }

    /**
     * ①b 旧数据兼容：{@code imageModelName} 有值、但没勾选复选框时也必须能序列化出来。
     *
     * <p>这是升级窗口里真实存在的组合——老部署的 {@code image_model_name} 有值，
     * 新列 {@code is_image_model} 补出来是 NULL。两个字段此时**语义不同**，不能互相顶替。
     */
    @Test
    void profileViewKeepsLegacyImageModelNameWhenTheBoxIsNotTicked() throws Exception {
        LlmProfileService.ProfileView legacy = new LlmProfileService.ProfileView(
                6L, "老配图档", 7L, "nexus", true, "deepseek-flash",
                true, "••••••••abcd", null, null, true, false, false, "step-image-edit-2", false, null, true);
        assertThat(mapper.writeValueAsString(legacy))
                .contains("\"imageModelName\":\"step-image-edit-2\"")
                .contains("\"isImageModel\":false");
    }

    /**
     * 兜底档案的字段名同样是前后端契约：前端用它渲染「兜底」徽标与切换按钮。
     *
     * <p>与 {@code isDefault} 同理——record 的 {@code isFallback} 访问器一旦被 Jackson 剥掉前缀，
     * 字段会静默变成 {@code fallback}，前端读不到就永远显示「未设置兜底档案」，
     * 而故障切换链的最后一段实际上是配好的。
     */
    @Test
    void profileViewSerializesIsFallback() throws Exception {
        LlmProfileService.ProfileView view = new LlmProfileService.ProfileView(
                3L, "兜底档案", 7L, "nexus", true, "hy4-preview",
                true, "••••••••wxyz", null, null, true, false, true, null, false, null, true);
        String json = mapper.writeValueAsString(view);
        assertThat(json).contains("\"isFallback\":true").doesNotContain("\"fallback\":");
    }

    /**
     * 归属供应商的三个字段（{@code providerId} / {@code providerName} / {@code providerEnabled}）
     * 是本轮新增的前后端契约。
     *
     * <p>为什么值得单独钉：前端要拿 {@code providerName} 分组、拿 {@code providerEnabled} 决定
     * {@code usable()}（缺省 `true` 是存量兼容的关键），而「供应商被删」的告警态恰恰是
     * {@code providerName=null} + {@code providerEnabled=false} —— 若这两个键被序列化器省略，
     * 前端拿到 {@code undefined}，`undefined !== false` 会让一条**不可用**的档案留在前端链上。
     * 因此它们的「键在场」必须显式钉住（含值为 null 的情形）。
     */
    @Test
    void profileViewSerializesProviderBindingEvenWhenTheProviderIsGone() throws Exception {
        LlmProfileService.ProfileView attached = new LlmProfileService.ProfileView(
                10L, "挂供应商的档案", 7L, "nexus", true, "deepseek-flash",
                true, "••••••••abcd", null, null, true, false, false, null, false, null, true);
        assertThat(mapper.writeValueAsString(attached))
                .contains("\"providerId\":7")
                .contains("\"providerName\":\"nexus\"")
                .contains("\"providerEnabled\":true")
                .doesNotContain("\"baseUrl\"")   // 端点改由供应商行提供，档案视图不再回传
                .doesNotContain("\"provider\":\""); // 同理：协议也归供应商

        LlmProfileService.ProfileView dangling = new LlmProfileService.ProfileView(
                11L, "供应商被删的档案", 99L, null, false, "deepseek-flash",
                false, "未配置", null, null, true, false, false, null, false, null, false);
        assertThat(mapper.writeValueAsString(dangling))
                .as("悬空态必须是「键在场、值为 null/false」，前端才认得出告警态")
                .contains("\"providerId\":99")
                .contains("\"providerName\":null")
                .contains("\"providerEnabled\":false");
    }

    /**
     * 契约 §3.3 新增的计算字段 {@code runtimeAvailable}：必须**键在场**。
     *
     * <p>为什么要专钉「键在场」：前端用它渲染「可用 / 不可用」徽标。字段被序列化器省略时
     * 前端拿到 {@code undefined}，徽标永远不出现，而没有任何报错。
     *
     * <p>同时钉住 {@code providerPurpose} 的**缺席**：契约 §4.3 修订后供应商级用途已删
     * （用户原话：「供应商会同时供应所有类型模型」——用途是模型的属性，不是供应商的）。
     * 它若悄悄回到响应里，前端那张语义错误的「文本 / 图片」供应商徽标就会重新出现；
     * 这类回归不报错，只是把界面讲错，因此「键不存在」同样要当契约断言写下来。
     */
    @Test
    void profileViewSerializesRuntimeAvailabilityAndHasNoProviderPurpose() throws Exception {
        LlmProfileService.ProfileView attached = new LlmProfileService.ProfileView(
                20L, "配图档", 7L, "nexus", true, "step-image-edit-2",
                true, "••••••••abcd", null, null, true, false, false, "step-image-edit-2", false, null, true);
        String json = mapper.writeValueAsString(attached);
        assertThat(json).contains("\"runtimeAvailable\":true");
        assertThat(json)
                .as("供应商级用途已删除：该键不得再出现在档案视图里")
                .doesNotContain("providerPurpose");

        LlmProfileService.ProfileView dangling = new LlmProfileService.ProfileView(
                21L, "悬空档", 99L, null, false, "deepseek-flash",
                false, "无法解密（请检查 APP_SECRET_KEY）", null, null, true, false, false, null, false, null, false);
        assertThat(mapper.writeValueAsString(dangling)).contains("\"runtimeAvailable\":false");
    }

    @Test
    void agentDefinitionSerializesIsBuiltin() throws Exception {
        AgentDefinition definition = new AgentDefinition();
        definition.setId(1L);
        definition.setCode("builtin_editor");
        definition.setName("墨舟编辑智能体");
        definition.setIsBuiltin(true);
        String json = mapper.writeValueAsString(definition);
        assertThat(json).contains("\"isBuiltin\":true").doesNotContain("\"builtin\":");
    }

    @Test
    void profileViewWithoutApiKeyReportsMaskedState() throws Exception {
        LlmProfileService.ProfileView view = new LlmProfileService.ProfileView(
                2L, "无 key 档案", 8L, "anthropic", false, "claude", false, "未配置",
                null, null, false, false, false, null, false, null, false);
        String json = mapper.writeValueAsString(view);
        assertThat(json).contains("\"hasApiKey\":false").contains("\"isDefault\":false");
    }
}
