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
 * 用错模型不会报错，只会让配图变差或变贵，甚至悄悄花掉付费额度。两种错法尤其要紧：
 * <ul>
 *   <li>档案没声明图片模型时**不能**无视全局设置（存量部署的图片模型只配在全局里，
 *       无视它 = 升级后配图立刻不可用）；</li>
 *   <li>档案声明了图片模型时端点/密钥必须**跟着这个档案走**，不能与全局三件套交叉拼接
 *       （模型名属于某个供应商，塞进另一个供应商的端点里必然失败）。</li>
 * </ul>
 */
class LlmConfigServiceImageRuntimeTest {

    private static final String GLOBAL_MODEL = "global-image-model";
    private static final String GLOBAL_KEY = "global-key";
    private static final String PROFILE_KEY = "profile-key";

    /**
     * 档案声明了图片模型 → 用它，且端点与密钥都取该档案的（不是全局的）。
     *
     * <p>「端点跟着档案走」是本用例的重点：档案可以指向完全不同的网关，
     * 若这里回落到全局 baseUrl，请求会打到错误的供应商上。
     */
    @Test
    void profileImageModelWinsTogetherWithItsOwnEndpointAndKey() {
        LlmProfile carrier = profile(1L, "配图档", "profile-image-model", "https://profile.example.com");
        LlmProfileService profileService = profileService(carrier, null);

        LlmConfigService.ImageRuntime runtime = service(profileService, globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName()).isEqualTo("profile-image-model");
        assertThat(runtime.baseUrl()).isEqualTo("https://profile.example.com");
        assertThat(runtime.apiKey()).isEqualTo(PROFILE_KEY);
        assertThat(runtime.available()).isTrue();
    }

    /**
     * 档案存在但没声明图片模型 → 逐字回落到全局图片三件套（**改造前的存量行为**）。
     *
     * <p>这是本次改动最要紧的一条：默认档案的 image_model_name 在存量库里是 NULL，
     * 若这里返回一个不可用的通道，升级当天所有配图都会失败。
     */
    @Test
    void undeclaredProfileFallsBackToTheGlobalImageTrio() {
        LlmProfile carrier = null;
        LlmProfileService profileService = profileService(carrier, null);

        LlmConfigService.ImageRuntime runtime = service(profileService, globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
        assertThat(runtime.apiKey()).isEqualTo(GLOBAL_KEY);
        assertThat(runtime.available()).isTrue();
    }

    /** 未绑定档案（profileId 为 null）同样回落全局：编辑器与 SINGLE 都可能走到这条。 */
    @Test
    void nullProfileIdFallsBackToTheGlobalImageTrio() {
        LlmProfileService profileService = profileService(null, null);

        LlmConfigService.ImageRuntime runtime = service(profileService, globalConfig()).imageRuntime(null);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
    }

    /**
     * 档案声明了图片模型但该档案不可用（未启用/无 Key）时回落全局。
     *
     * <p>{@code imageCarrier} 已经滤掉不可用档案，这里是第二道防线：即便它漏了，
     * 也不能把「不可用的档案」当成配图通道返回。
     */
    @Test
    void unusableProfileFallsBackToTheGlobalImageTrio() {
        LlmProfile carrier = profile(1L, "停用档", "profile-image-model", "https://profile.example.com");
        carrier.setEnabled(false);
        LlmProfileService profileService = profileService(carrier, null);

        LlmConfigService.ImageRuntime runtime = service(profileService, globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
    }

    /** 全局也没配图片模型 → 返回不可用通道，由 ImageGenerationService 抛出可读的业务异常。 */
    @Test
    void unavailableWhenNeitherProfileNorGlobalDeclaresOne() {
        LlmConfig cfg = globalConfig();
        cfg.setImageModelName(null);
        LlmProfileService profileService = profileService(null, null);

        LlmConfigService.ImageRuntime runtime = service(profileService, cfg).imageRuntime(null);

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
        LlmProfileService profileService = profileService(null, null);

        LlmConfigService.ImageRuntime runtime = service(profileService, cfg).imageRuntime(null);

        assertThat(runtime.modelName()).isEqualTo(GLOBAL_MODEL);
        assertThat(runtime.available()).isFalse();
    }

    /** 档案路径下不需要 llm_config 的图片模型：档案声明了就不该读全局图片模型。 */
    @Test
    void profilePathDoesNotLeakTheGlobalModel() {
        LlmProfile carrier = profile(1L, "配图档", "profile-image-model", "https://profile.example.com");
        LlmConfigService.ImageRuntime runtime =
                service(profileService(carrier, null), globalConfig()).imageRuntime(1L);

        assertThat(runtime.modelName()).isNotEqualTo(GLOBAL_MODEL);
    }

    // ---------- 构造 ----------

    private static LlmProfileService profileService(LlmProfile carrier, LlmProfile unused) {
        LlmProfileService service = mock(LlmProfileService.class);
        when(service.findById(any())).thenReturn(carrier);
        when(service.imageCarrier(any())).thenReturn(carrier);
        when(service.runtime(any())).thenAnswer(invocation -> {
            LlmProfile p = invocation.getArgument(0);
            if (p == null) return null;
            return new LlmProfileService.RuntimeProfile(Boolean.TRUE.equals(p.getEnabled()),
                    p.getProvider(), p.getBaseUrl(), p.getModelName(),
                    PROFILE_KEY, new BigDecimal("0.70"), 4096,
                    Boolean.TRUE.equals(p.getIsDefault()), p.getImageModelName());
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

    private static LlmProfile profile(Long id, String name, String imageModelName, String baseUrl) {
        LlmProfile profile = new LlmProfile();
        profile.setId(id);
        profile.setName(name);
        profile.setProvider("OPENAI_COMPATIBLE");
        profile.setBaseUrl(baseUrl);
        profile.setModelName("text-model");
        profile.setApiKeyEncrypted("encrypted");
        profile.setEnabled(true);
        profile.setIsDefault(false);
        profile.setIsFallback(false);
        profile.setImageModelName(imageModelName);
        return profile;
    }
}
