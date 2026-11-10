package ink.icoding.wechat.article.settings;

import ink.icoding.wechat.article.agent.LlmProfile;
import ink.icoding.wechat.article.agent.LlmProfileService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 配图通道解析（{@link LlmConfigService#imageRuntime(Long)}）：档案优先、全局兜底。
 *
 * <p>为什么这条解析值得单测：它决定「配图到底用哪个图片模型」，而**错法的后果是静默的**——
 * 用错模型不会报错，只会让配图变差或变贵，甚至悄悄花掉付费额度。三种错法尤其要紧：
 * <ul>
 *   <li>链上没人标图片模型时**不能**无视全局设置（存量部署的图片模型只配在全局里，
 *       无视它 = 升级后配图立刻不可用）；</li>
 *   <li>标了图片模型时端点/密钥必须**跟着那条档案走**，不能与全局三件套交叉拼接
 *       （模型名属于某个供应商，塞进另一个供应商的端点里必然失败）；</li>
 *   <li>全局开关关着时哪一跳都不得放行（漏法是静默的：不报错，只花钱）。</li>
 * </ul>
 *
 * <p>契约 §4.3 定稿后图片通道是三段：①勾选了「这是图片模型」的档案 → ①b 历史
 * {@code imageModelName}（旧数据兼容，只读）→ ②{@code llm_config} 图片三件套。
 */
class LlmConfigServiceImageRuntimeTest {

    private static final String GLOBAL_MODEL = "global-image-model";
    private static final String GLOBAL_KEY = "global-key";
    private static final String PROFILE_KEY = "profile-key";

    /**
     * ① 主路径：勾选了「这是图片模型」的档案 ⇒ 用它的 {@code modelName}，端点密钥都取该档案的。
     *
     * <p>「端点跟着档案走」是本用例的重点：档案可以指向完全不同的网关，
     * 若这里回落到全局 baseUrl，请求会打到错误的供应商上。
     */
    @Test
    void tickedProfileWinsTogetherWithItsOwnEndpointAndKey() {
        LlmProfile carrier = profile(1L, "配图档", "gpt-image-1", null, true, "https://profile.example.com");

        LlmConfigService.ImageRuntime runtime =
                service(profileService(carrier), globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName())
                .as("勾选档案的图片模型名就是它自己的 modelName")
                .isEqualTo("gpt-image-1");
        assertThat(runtime.baseUrl()).isEqualTo("https://profile.example.com");
        assertThat(runtime.apiKey()).isEqualTo(PROFILE_KEY);
        assertThat(runtime.available()).isTrue();
    }

    /**
     * ①b 旧数据兼容：没勾选、但声明了 {@code imageModelName} 的老档案仍然生效。
     *
     * <p>第二版设计（卡片上填「图片模型名称」文本框）被用户否决了，界面不再暴露那个输入框，
     * 但**已经这样配过的部署不能炸**。这条用例就是那个承诺的钉子。
     */
    @Test
    void legacyImageModelNameStillWorksWhenNothingIsTicked() {
        LlmProfile legacy = profile(1L, "老配图档", "text-model", "step-image-edit-2", false,
                "https://profile.example.com");

        LlmConfigService.ImageRuntime runtime =
                service(profileService(legacy), globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName())
                .as("①b 用历史字段，而不是这条档案的 modelName")
                .isEqualTo("step-image-edit-2");
        assertThat(runtime.baseUrl()).isEqualTo("https://profile.example.com");
    }

    /** ① 压过 ①b：勾选了就用 {@code modelName}，历史 {@code imageModelName} 不再参与。 */
    @Test
    void tickedProfileWinsOverTheLegacyImageModelName() {
        LlmProfile both = profile(1L, "两者都有", "gpt-image-1", "step-image-edit-2", true,
                "https://profile.example.com");

        LlmConfigService.ImageRuntime runtime =
                service(profileService(both), globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName()).isEqualTo("gpt-image-1");
    }

    /**
     * 档案存在但既没勾选、也没历史图片模型名 → 逐字回落到全局图片三件套（**改造前的存量行为**）。
     *
     * <p>这是本次改动最要紧的一条：存量库里 {@code is_image_model} 是 NULL、{@code image_model_name}
     * 是空，若这里返回一个不可用的通道，升级当天所有配图都会失败。
     *
     * <p>用 {@code forcedCarrier}（而不是传 null）来打：传 null 走的是「没绑定档案」那条路，
     * 与本用例要钉的「档案在、但它不是图片模型」是两条不同的分支，混同就丢了区分力。
     */
    @Test
    void undeclaredProfileFallsBackToTheGlobalImageTrio() {
        LlmProfile carrier = profile(1L, "未声明档", "text-model", null, false, "https://profile.example.com");

        LlmConfigService.ImageRuntime runtime =
                service(forcedCarrier(carrier), globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
        assertThat(runtime.apiKey()).isEqualTo(GLOBAL_KEY);
        assertThat(runtime.available()).isTrue();
    }

    /** 未绑定档案（profileId 为 null）同样回落全局：编辑器与 SINGLE 都可能走到这条。 */
    @Test
    void nullProfileIdFallsBackToTheGlobalImageTrio() {
        LlmConfigService.ImageRuntime runtime =
                service(profileService(null), globalConfig()).imageRuntime(null);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
    }

    /**
     * 承载档案不可用（未启用/无 Key）时回落全局。
     *
     * <p>{@code imageCarrier} 已经滤掉不可用档案，这里是第二道防线：即便它漏了，
     * 也不能把「不可用的档案」当成配图通道返回。
     */
    @Test
    void unusableProfileFallsBackToTheGlobalImageTrio() {
        LlmProfile carrier = profile(1L, "停用档", "gpt-image-1", null, true, "https://profile.example.com");
        carrier.setEnabled(false);

        LlmConfigService.ImageRuntime runtime =
                service(profileService(carrier), globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
    }

    /** 全局也没配图片模型 → 返回不可用通道，由 ImageGenerationService 抛出可读的业务异常。 */
    @Test
    void unavailableWhenNeitherProfileNorGlobalDeclaresOne() {
        LlmConfig cfg = globalConfig();
        cfg.setImageModelName(null);

        LlmConfigService.ImageRuntime runtime =
                service(profileService(null), cfg).imageRuntime(null);

        assertThat(runtime.available()).isFalse();
    }

    /**
     * 全局 LLM 被停用时，即便图片三件套齐全也返回不可用。
     *
     * <p>这是改造前 {@code imageAvailable()} 就有的门禁（{@code enabled} 是全局开关），
     * 本次重构把它原样搬到 {@code ImageRuntime} 上——关掉 LLM 的部署不该还能生图。
     * 漏掉它不会报错，只会让「已停用」的设置悄悄继续花钱。
     */
    @Test
    void disabledGlobalLlmMakesImageUnavailable() {
        LlmConfig cfg = globalConfig();
        cfg.setEnabled(false);

        LlmConfigService.ImageRuntime runtime =
                service(profileService(null), cfg).imageRuntime(null);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
        assertThat(runtime.available()).isFalse();
    }

    /**
     * **门禁优先**：档案勾选了图片模型、端点密钥也都齐，但全局门禁关着 ⇒ 仍然不可用。
     *
     * <p>契约 §4.3 的三段（①、①b、②）**全在 `if (config.enabled())` 之内**。为什么值得单钉：
     * 漏掉它的后果是「在设置里取消勾选『启用 AI 服务』」的部署仍会经档案继续调付费生图接口
     * ——不报错，只花钱。原先档案分支硬编码 {@code enabled=true}，这条是那次修复的回归钉。
     */
    @Test
    void tickedProfileIsBlockedWhenTheGlobalLlmIsDisabled() {
        LlmProfile carrier = profile(1L, "配图档", "gpt-image-1", null, true, "https://profile.example.com");
        LlmConfig cfg = globalConfig();
        cfg.setEnabled(false);

        LlmConfigService.ImageRuntime runtime =
                service(profileService(carrier), cfg).imageRuntime(1L);

        assertThat(runtime.available())
                .as("全局停用时，即便档案勾选了图片模型也不得放行生图")
                .isFalse();
        assertThat(runtime.baseUrl())
                .as("必须落到②的全局端点，而不是档案声明的那一个").isEqualTo("https://global.example.com");
        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
    }

    /**
     * 勾选了、但档案的 {@code modelName} 只有空白（脏行）→ 回落全局。
     *
     * <p>真实链路上创建接口有 {@code @NotBlank}，这种行只可能来自手工写库；本用例把它强行喂进
     * 第二道防线，钉住「空白不等于声明」——否则配图请求体会带上 {@code "model": " "}。
     */
    @Test
    void blankModelNameOnTheTickedCarrierFallsBackToTheGlobalTrio() {
        LlmProfile carrier = profile(1L, "空档", "   ", null, true, "https://profile.example.com");

        LlmConfigService.ImageRuntime runtime =
                service(forcedCarrier(carrier), globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
        assertThat(runtime.baseUrl()).isEqualTo("https://global.example.com");
    }

    /** 档案路径下不需要 llm_config 的图片模型：档案声明了就不该读全局图片模型。 */
    @Test
    void profilePathDoesNotLeakTheGlobalModel() {
        LlmProfile carrier = profile(1L, "配图档", "gpt-image-1", null, true, "https://profile.example.com");

        LlmConfigService.ImageRuntime runtime =
                service(profileService(carrier), globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName())
                .as("必须正好是这条档案声明的模型，既不是全局模型也不能是空值")
                .isNotEqualTo(GLOBAL_MODEL);
        assertThat(runtime.baseUrl()).isEqualTo("https://profile.example.com");
    }

    // ---------- 构造 ----------

    /**
     * 与真实 {@code LlmProfileService.imageCarrier} 同判据的替身。
     *
     * <p>替身必须复刻真实判据，否则「档案不是图片模型 → 回落全局」的用例会退化成与
     * 「没绑定档案」同一条路径，两段分支就失去了区分力。真实判据（契约 §4.3）是两段：
     * ①链上第一个 {@code isImageModel=true} 且可用的档案；①b 没有勾选的才看 {@code imageModelName}。
     */
    private static LlmProfileService profileService(LlmProfile carrier) {
        LlmProfileService service = stub(carrier);
        when(service.imageCarrier(any())).thenAnswer(invocation -> {
            LlmProfile candidate = invocation.getArgument(0);
            if (candidate == null || !usable(candidate)) return null;
            if (Boolean.TRUE.equals(candidate.getIsImageModel())) return candidate;
            String legacy = candidate.getImageModelName();
            return legacy == null || legacy.isBlank() ? null : candidate;
        });
        return service;
    }

    /** 强行让 {@code imageCarrier} 返回该档案，用于打第二道防线（真实链路上它已被滤掉）。 */
    private static LlmProfileService forcedCarrier(LlmProfile carrier) {
        LlmProfileService service = stub(carrier);
        when(service.imageCarrier(any())).thenReturn(carrier);
        return service;
    }

    private static boolean usable(LlmProfile profile) {
        return Boolean.TRUE.equals(profile.getEnabled())
                && profile.getApiKeyEncrypted() != null && !profile.getApiKeyEncrypted().isBlank();
    }

    private static LlmProfileService stub(LlmProfile carrier) {
        LlmProfileService service = mock(LlmProfileService.class);
        when(service.findById(any())).thenReturn(carrier);
        when(service.runtime(any())).thenAnswer(invocation -> {
            LlmProfile p = invocation.getArgument(0);
            if (p == null) return null;
            return new LlmProfileService.RuntimeProfile(Boolean.TRUE.equals(p.getEnabled()),
                    p.getProvider(), p.getBaseUrl(), p.getModelName(),
                    PROFILE_KEY, new BigDecimal("0.70"), 4096,
                    Boolean.TRUE.equals(p.getIsDefault()), p.getImageModelName());
        });
        // 与真实实现同判据：勾选看 modelName，否则看 imageModelName（空白一律折算成 null）
        when(service.imageModelNameOf(any())).thenAnswer(invocation -> {
            LlmProfile p = invocation.getArgument(0);
            if (p == null) return null;
            String raw = Boolean.TRUE.equals(p.getIsImageModel()) ? p.getModelName() : p.getImageModelName();
            return raw == null || raw.isBlank() ? null : raw.trim();
        });
        when(service.defaultProfile()).thenReturn(null);
        return service;
    }

    /** llm_config 单行：图片三件套就是改造前唯一的来源。 */
    private static LlmConfig globalConfig() {
        LlmConfig config = new LlmConfig();
        config.setId(1L);
        config.setProvider("OPENAI_COMPATIBLE");
        config.setBaseUrl("https://global.example.com");
        config.setModelName("global-text-model");
        config.setApiKeyEncrypted("global-encrypted");
        config.setImageBaseUrl(null);
        config.setImageModelName(GLOBAL_MODEL);
        config.setImageApiKeyEncrypted(null);
        config.setEnabled(true);
        config.setTemperature(new BigDecimal("0.70"));
        config.setMaxTokens(4096);
        return config;
    }

    private static LlmConfigService service(LlmProfileService profileService, LlmConfig config) {
        LlmConfigMapper mapper = mock(LlmConfigMapper.class);
        when(mapper.current()).thenReturn(config);
        ink.icoding.wechat.article.common.CryptoService crypto =
                mock(ink.icoding.wechat.article.common.CryptoService.class);
        // 全局路径：llm_config 的 key 与图片 key 都为空 → 复用 LLM key；这里让它解出可读值
        when(crypto.decrypt(any())).thenReturn(GLOBAL_KEY);
        when(crypto.decrypt(null)).thenReturn(null);
        return new LlmConfigService(mapper, crypto,
                mock(ink.icoding.wechat.article.auth.CurrentUserService.class), profileService);
    }

    private static LlmProfile profile(Long id, String name, String modelName, String imageModelName,
                                      boolean isImageModel, String baseUrl) {
        LlmProfile profile = new LlmProfile();
        profile.setId(id);
        profile.setName(name);
        profile.setProvider("OPENAI_COMPATIBLE");
        profile.setBaseUrl(baseUrl);
        profile.setModelName(modelName);
        profile.setApiKeyEncrypted("encrypted");
        profile.setEnabled(true);
        profile.setIsDefault(false);
        profile.setIsFallback(false);
        profile.setImageModelName(imageModelName);
        profile.setIsImageModel(isImageModel);
        return profile;
    }
}