package ink.icoding.wechat.article.settings;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class LlmConfigService {
    private static final Logger log = LoggerFactory.getLogger(LlmConfigService.class);

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "OPENAI_COMPATIBLE", "OPENAI_RESPONSES", "ANTHROPIC");
    private final LlmConfigMapper mapper;
    private final CryptoService cryptoService;
    private final CurrentUserService currentUserService;
    private final ink.icoding.wechat.article.agent.LlmProfileService llmProfileService;

    public LlmConfigService(LlmConfigMapper mapper, CryptoService cryptoService, CurrentUserService currentUserService,
                            ink.icoding.wechat.article.agent.LlmProfileService llmProfileService) {
        this.mapper = mapper;
        this.cryptoService = cryptoService;
        this.currentUserService = currentUserService;
        this.llmProfileService = llmProfileService;
    }

    public ConfigView get() {
        return view(required());
    }

    @Transactional
    public ConfigView update(UpdateRequest request) {
        if (!SUPPORTED_PROVIDERS.contains(request.provider())) {
            throw new BusinessException("不支持的 LLM 服务类型：" + request.provider());
        }
        LlmConfig config = required();
        config.setProvider(request.provider());
        config.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        config.setModelName(request.modelName());
        config.setImageBaseUrl(blankToNull(request.imageBaseUrl()) == null
                ? null : normalizeBaseUrl(request.imageBaseUrl()));
        config.setImageModelName(blankToNull(request.imageModelName()));
        config.setEnabled(Boolean.TRUE.equals(request.enabled()));
        config.setTemperature(request.temperature() == null ? new BigDecimal("0.70") : request.temperature());
        config.setMaxTokens(request.maxTokens() == null ? 4096 : request.maxTokens());
        if (Boolean.TRUE.equals(request.clearApiKey())) config.setApiKeyEncrypted(null);
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.setApiKeyEncrypted(cryptoService.encrypt(request.apiKey().trim()));
        }
        if (Boolean.TRUE.equals(request.clearImageApiKey())) config.setImageApiKeyEncrypted(null);
        if (request.imageApiKey() != null && !request.imageApiKey().isBlank()) {
            config.setImageApiKeyEncrypted(cryptoService.encrypt(request.imageApiKey().trim()));
        }
        if (Boolean.TRUE.equals(config.getEnabled()) && config.getApiKeyEncrypted() == null) {
            throw new BusinessException("启用 LLM 前必须配置 API Key");
        }
        config.setUpdatedBy(currentUserService.required().id());
        config.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(config);
        // 存量 API 兼容映射（方案 4.3）：LLM 字段同步到默认模型档案，图片三件套留在 llm_config
        llmProfileService.syncDefaultFromConfig(config.getProvider(), normalizeBaseUrl(config.getBaseUrl()),
                config.getModelName(), config.getApiKeyEncrypted(), config.getTemperature(),
                config.getMaxTokens(), config.getEnabled());
        return view(config);
    }

    public RuntimeConfig runtime() {
        // 第②期：优先读默认模型档案（方案 4.3 —— 签名不变，存量调用点零改动）。
        // 口径与 view() 保持一致：档案存在即以档案为准（含「存在但未启用/无 key」→ 返回不可用），
        // 只有档案表为空（未迁移部署）才回落 llm_config，避免设置页显示 A 而运行时用 B。
        ink.icoding.wechat.article.agent.LlmProfileService.RuntimeProfile profile = defaultProfileRuntime();
        if (profile != null) {
            LlmConfig config = required();
            String imageApiKey = config.getImageApiKeyEncrypted() == null
                    ? profile.apiKey()
                    : ink.icoding.wechat.article.agent.LlmProviderService.decryptOrNull(
                            cryptoService, config.getImageApiKeyEncrypted(), "llm_config 图片密钥");
            return new RuntimeConfig(profile.available(), profile.provider(), profile.baseUrl(),
                    profile.modelName(), profile.apiKey(), profile.temperature(), profile.maxTokens(),
                    blankToNull(config.getImageBaseUrl()) == null ? profile.baseUrl()
                            : normalizeBaseUrl(config.getImageBaseUrl()),
                    blankToNull(config.getImageModelName()), imageApiKey);
        }
        LlmConfig config = required();
        String apiKey = ink.icoding.wechat.article.agent.LlmProviderService.decryptOrNull(
                cryptoService, config.getApiKeyEncrypted(), "llm_config LLM 密钥");
        String imageApiKey = config.getImageApiKeyEncrypted() == null
                ? apiKey : ink.icoding.wechat.article.agent.LlmProviderService.decryptOrNull(
                        cryptoService, config.getImageApiKeyEncrypted(), "llm_config 图片密钥");
        return new RuntimeConfig(Boolean.TRUE.equals(config.getEnabled()), config.getProvider(), normalizeBaseUrl(config.getBaseUrl()),
                config.getModelName(), apiKey, config.getTemperature(), config.getMaxTokens(),
                blankToNull(config.getImageBaseUrl()) == null ? normalizeBaseUrl(config.getBaseUrl())
                        : normalizeBaseUrl(config.getImageBaseUrl()),
                blankToNull(config.getImageModelName()), imageApiKey);
    }

    /**
     * 配图通道解析：**链上有档案标了「这是图片模型」就用它**，否则回落全局设置
     * （{@code llm_config} 图片三件套，契约 §4.3 第②跳）。
     *
     * <p>为什么要单独一个入口而不是扩展 {@link #runtime()}：{@code runtime()} 回答的是
     * 「文本模型用什么」，它只认默认档案——因为文本链路的调用点没有「智能体」这个上下文。
     * 配图不同：它总是由某个具体智能体（配图师 / 编辑 / SINGLE 主智能体）发起，
     * 因此有机会把**该智能体的档案**带进来。硬塞进 {@code runtime()} 只会让两者互相污染。
     *
     * <p>为什么图片通道是「档案优先、全局兜底」而不是「档案独占」：改造前图片模型只能配在
     * 全局设置里，存量部署就是这么用的（{@code LLM_CONFIG.IMAGE_MODEL_NAME} 有值）。若档案
     * 一出现就无视全局设置，那些部署的配图会突然不可用；而「只有链上确实标了图片模型才用它」
     * 让存量行为逐字不变——老档案既没勾选、{@code image_model_name} 也是空的，走的仍是全局那一条。
     *
     * <p>端点与密钥跟着**承载档案**走，不与全局三件套交叉拼接：图片模型名属于某个供应商，
     * 把它塞进另一个供应商的端点和密钥里只会得到一个必然失败的请求。
     *
     * @param profileId 发起配图的智能体所绑定的档案，可为 null（未绑定/定义缺失）
     */
    public ImageRuntime imageRuntime(Long profileId) {
        RuntimeConfig config = runtime();
        // 全局门禁先行：改造前 imageAvailable() 要求 RuntimeConfig.enabled 为真，
        // 而这个 enabled 在有默认档案时就是默认档案的可用性（见 record 注释）。
        // **三跳（①、①b、②）全部在门禁块内**：任何一条路径都不得绕过它——否则「关掉 AI 服务」
        // 的部署仍会经档案继续调付费生图接口，而这条漏是不报错的那种。
        if (config.enabled()) {
            ink.icoding.wechat.article.agent.LlmProfile bound = llmProfileService.findById(profileId);
            ImageRuntime fromProfile = imageRuntimeFromProfile(bound);
            if (fromProfile != null) return fromProfile;
        }
        // ② 回落 llm_config 图片三件套：存量部署就是这么配的，链上既没人勾选图片模型、
        // 也没有历史 imageModelName 时，行为必须与改造前逐字相同（契约 §4.5 回滚路径同样依赖它）
        return new ImageRuntime(config.enabled(), config.imageBaseUrl(), config.imageModelName(),
                config.imageApiKey());
    }

    /**
     * 第①跳（含 ①b 旧数据兼容）：**该档案（沿故障切换链）标了图片模型**就用它。
     *
     * <p>两段的区分（勾选 vs 历史 {@code imageModelName}）留在
     * {@link ink.icoding.wechat.article.agent.LlmProfileService#imageCarrier} 与
     * {@link ink.icoding.wechat.article.agent.LlmProfileService#imageModelNameOf} 里，
     * 这里只消费它们的结论。把判据抄一份到这里来的风险很具体：两处一旦漂移，配图会拿着
     * {@code imageModelName} 的历史值去打一个已经改名的模型，而报错发生在很远的上游。
     *
     * <p>端点与密钥跟着**承载档案**走，不与全局三件套交叉拼接：图片模型名属于某个供应商，
     * 把它塞进另一个供应商的端点与密钥里只会得到一个必然失败的请求。
     */
    private ImageRuntime imageRuntimeFromProfile(ink.icoding.wechat.article.agent.LlmProfile bound) {
        ink.icoding.wechat.article.agent.LlmProfile carrier = llmProfileService.imageCarrier(bound);
        if (carrier == null) return null;
        String imageModel = llmProfileService.imageModelNameOf(carrier);
        if (imageModel == null) return null;
        ink.icoding.wechat.article.agent.LlmProfileService.RuntimeProfile profile =
                llmProfileService.runtime(carrier);
        if (profile == null || !profile.available()) return null;
        return new ImageRuntime(true, profile.baseUrl(), imageModel, profile.apiKey());
    }

    /**
     * 默认档案的运行时视图，**任何异常都降级成 {@code null}**（缺陷 B3）。
     *
     * <p>为什么不靠「链里的解密已经容错」：默认档案→供应商→密钥这条链上还有别的可能抛点
     * （脏行、mapper 异常、将来新增的校验），而这里失败的后果是**整个读接口 500**——
     * 管理员因此连唯一的修复入口都打不开（Lead 2026-11-10 实测：坏密钥下
     * {@code GET /api/settings/llm} 与 {@code GET /api/llm-profiles} 都是 500）。
     * 宁可按「没有可用档案」回落 llm_config，也不能让一页配置把人锁在门外。
     */
    private ink.icoding.wechat.article.agent.LlmProfileService.RuntimeProfile defaultProfileRuntime() {
        try {
            return llmProfileService.runtime(llmProfileService.defaultProfile());
        } catch (RuntimeException exception) {
            log.warn("默认模型档案的运行时解析失败（按「没有可用档案」回落 llm_config）", exception);
            return null;
        }
    }

    private synchronized LlmConfig required() {
        LlmConfig config = mapper.current();
        if (config == null) {
            LocalDateTime now = LocalDateTime.now();
            config = new LlmConfig();
            config.setProvider("OPENAI_COMPATIBLE");
            config.setBaseUrl("https://api.openai.com");
            config.setModelName("gpt-4.1-mini");
            config.setEnabled(false);
            config.setTemperature(new BigDecimal("0.70"));
            config.setMaxTokens(4096);
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            mapper.insert(config);
        }
        return config;
    }

    private ConfigView view(LlmConfig config) {
        // 存量 GET /api/settings/llm 兼容映射：默认档案存在时以其值回显（方案 4.3）
        ink.icoding.wechat.article.agent.LlmProfileService.RuntimeProfile profile = defaultProfileRuntime();
        String imageCiphertext = config.getImageApiKeyEncrypted();
        boolean hasImageKey = imageCiphertext != null && !imageCiphertext.isBlank();
        // 掩码统一走 LlmProviderService（同一 key 在两个页面显示不同长度本身就是缺陷）
        String imageMasked = hasImageKey
                ? ink.icoding.wechat.article.agent.LlmProviderService.maskCiphertext(
                        cryptoService, imageCiphertext, "llm_config 图片密钥")
                : "复用 LLM API Key";
        if (profile != null) {
            // 设置页显示的是**默认档案**的密钥，因此掩码直接问档案服务：
            // 两处各算一套会出现「一页说无法解密、另一页说未配置」，而管理员按任一条走都会走错
            ink.icoding.wechat.article.agent.LlmProfile defaultProfile =
                    llmProfileService.defaultProfile();
            return new ConfigView(profile.provider(), profile.baseUrl(), profile.modelName(),
                    profile.apiKey() != null && !profile.apiKey().isBlank(),
                    defaultProfile == null ? ink.icoding.wechat.article.agent.LlmProviderService.maskApiKey(null)
                            : llmProfileService.maskedKey(defaultProfile),
                    profile.enabled(), profile.temperature(), profile.maxTokens(), config.getUpdatedAt(),
                    config.getImageBaseUrl(), config.getImageModelName(), hasImageKey, imageMasked);
        }
        String apiKey = ink.icoding.wechat.article.agent.LlmProviderService.decryptOrNull(
                cryptoService, config.getApiKeyEncrypted(), "llm_config LLM 密钥");
        return new ConfigView(config.getProvider(), normalizeBaseUrl(config.getBaseUrl()), config.getModelName(),
                apiKey != null && !apiKey.isBlank(),
                ink.icoding.wechat.article.agent.LlmProviderService.maskCiphertext(
                        cryptoService, config.getApiKeyEncrypted(), "llm_config LLM 密钥"),
                config.getEnabled(), config.getTemperature(), config.getMaxTokens(), config.getUpdatedAt(),
                config.getImageBaseUrl(), config.getImageModelName(), hasImageKey, imageMasked);
    }

    private String normalizeBaseUrl(String value) {
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        String[] endpointSuffixes = {"/v1/chat/completions", "/v1/responses", "/v1/messages", "/v1"};
        for (String suffix : endpointSuffixes) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
                break;
            }
        }
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UpdateRequest(@NotBlank String provider, @NotBlank String baseUrl, @NotBlank String modelName,
                                String apiKey, Boolean clearApiKey, Boolean enabled,
                                @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
                                @Min(256) @Max(32768) Integer maxTokens,
                                String imageBaseUrl, String imageModelName,
                                String imageApiKey, Boolean clearImageApiKey) {
        /** 手写 {@code toString()}（复查项 M-4）：record 默认版本会打印两个明文密钥。 */
        @Override
        public String toString() {
            return "UpdateRequest[provider=" + provider + ", baseUrl=" + baseUrl + ", modelName=" + modelName
                    + ", apiKey=" + ink.icoding.wechat.article.agent.LlmProviderService.redactSecret(apiKey)
                    + ", clearApiKey=" + clearApiKey + ", enabled=" + enabled
                    + ", temperature=" + temperature + ", maxTokens=" + maxTokens
                    + ", imageBaseUrl=" + imageBaseUrl + ", imageModelName=" + imageModelName
                    + ", imageApiKey=" + ink.icoding.wechat.article.agent.LlmProviderService.redactSecret(imageApiKey)
                    + ", clearImageApiKey=" + clearImageApiKey + "]";
        }
    }

    public record ConfigView(String provider, String baseUrl, String modelName, boolean hasApiKey, String apiKeyMasked,
                             Boolean enabled, BigDecimal temperature, Integer maxTokens, LocalDateTime updatedAt,
                             String imageBaseUrl, String imageModelName, boolean hasImageApiKey,
                             String imageApiKeyMasked) {}

    public record RuntimeConfig(boolean enabled, String provider, String baseUrl, String modelName, String apiKey,
                                BigDecimal temperature, Integer maxTokens,
                                String imageBaseUrl, String imageModelName, String imageApiKey) {
        public boolean available() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }

        /**
         * 手写 {@code toString()}（复查项 M-4）：record 默认版本会把两个明文密钥打进日志。
         *
         * <p>record 形状本身**不变**（契约 §5：{@code AgentFactory} 等调用点零改动），
         * 加 {@code toString()} 不属于形状变更。
         */
        @Override
        public String toString() {
            return "RuntimeConfig[enabled=" + enabled + ", provider=" + provider + ", baseUrl=" + baseUrl
                    + ", modelName=" + modelName
                    + ", apiKey=" + ink.icoding.wechat.article.agent.LlmProviderService.redactSecret(apiKey)
                    + ", temperature=" + temperature + ", maxTokens=" + maxTokens
                    + ", imageBaseUrl=" + imageBaseUrl + ", imageModelName=" + imageModelName
                    + ", imageApiKey=" + ink.icoding.wechat.article.agent.LlmProviderService.redactSecret(imageApiKey)
                    + "]";
        }
    }

    /**
     * 配图通道（见 {@link #imageRuntime(Long)}）：端点 + 图片模型名 + 密钥三件套，
     * 三者同源，调用方直接拿去拼 {@code /v1/images/generations}。
     *
     * <p>{@code enabled} 保留的是改造前那道路径判断，但要说清它到底判的是什么：
     * 旧 {@code RuntimeConfig.imageAvailable()} 读的 {@code enabled} 是
     * {@code runtime()} 的产物，而 {@code runtime()} 在有默认档案时取的是**默认档案的
     * {@code available()}**（{@code enabled && apiKey}），**不是** {@code llm_config.enabled}
     * ——后者只在档案表为空（未迁移部署）那条回落路径上才直接生效。
     * 所以门禁的真实语义是「得有一个可用档案」。
     *
     * <p>档案路径**也过这道门**（{@link #imageRuntime(Long)} 里先判 {@code config.enabled()}
     * 再找承载档案）：载体档案自己可用，不等于整站允许生图——否则「关掉 AI 服务」的部署
     * 仍会经档案继续调付费生图接口，而这条门禁偏偏是不报错的那种漏。
     */
    public record ImageRuntime(boolean enabled, String baseUrl, String modelName, String apiKey) {
        public boolean available() {
            return enabled && modelName != null && !modelName.isBlank() && apiKey != null && !apiKey.isBlank();
        }

        /** 手写 {@code toString()}（复查项 M-4）：record 默认版本会把明文 {@code apiKey} 打进日志。 */
        @Override
        public String toString() {
            return "ImageRuntime[enabled=" + enabled + ", baseUrl=" + baseUrl + ", modelName=" + modelName
                    + ", apiKey=" + ink.icoding.wechat.article.agent.LlmProviderService.redactSecret(apiKey) + "]";
        }
    }
}
