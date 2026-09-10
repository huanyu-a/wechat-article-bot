package ink.icoding.wechat.article.settings;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class LlmConfigService {
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
        ink.icoding.wechat.article.agent.LlmProfileService.RuntimeProfile profile =
                llmProfileService.runtime(llmProfileService.defaultProfile());
        if (profile != null) {
            LlmConfig config = required();
            String imageApiKey = config.getImageApiKeyEncrypted() == null
                    ? profile.apiKey() : cryptoService.decrypt(config.getImageApiKeyEncrypted());
            return new RuntimeConfig(profile.available(), profile.provider(), profile.baseUrl(),
                    profile.modelName(), profile.apiKey(), profile.temperature(), profile.maxTokens(),
                    blankToNull(config.getImageBaseUrl()) == null ? profile.baseUrl()
                            : normalizeBaseUrl(config.getImageBaseUrl()),
                    blankToNull(config.getImageModelName()), imageApiKey);
        }
        LlmConfig config = required();
        String apiKey = config.getApiKeyEncrypted() == null ? null : cryptoService.decrypt(config.getApiKeyEncrypted());
        String imageApiKey = config.getImageApiKeyEncrypted() == null
                ? apiKey : cryptoService.decrypt(config.getImageApiKeyEncrypted());
        return new RuntimeConfig(Boolean.TRUE.equals(config.getEnabled()), config.getProvider(), normalizeBaseUrl(config.getBaseUrl()),
                config.getModelName(), apiKey, config.getTemperature(), config.getMaxTokens(),
                blankToNull(config.getImageBaseUrl()) == null ? normalizeBaseUrl(config.getBaseUrl())
                        : normalizeBaseUrl(config.getImageBaseUrl()),
                blankToNull(config.getImageModelName()), imageApiKey);
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
        ink.icoding.wechat.article.agent.LlmProfileService.RuntimeProfile profile =
                llmProfileService.runtime(llmProfileService.defaultProfile());
        if (profile != null) {
            return new ConfigView(profile.provider(), profile.baseUrl(), profile.modelName(),
                    profile.apiKey() != null, profile.apiKey() == null ? "未配置" : "••••••••••••",
                    profile.enabled(), profile.temperature(), profile.maxTokens(), config.getUpdatedAt(),
                    config.getImageBaseUrl(), config.getImageModelName(),
                    config.getImageApiKeyEncrypted() != null,
                    config.getImageApiKeyEncrypted() == null ? "复用 LLM API Key" : mask(config.getImageApiKeyEncrypted()));
        }
        return new ConfigView(config.getProvider(), normalizeBaseUrl(config.getBaseUrl()), config.getModelName(),
                config.getApiKeyEncrypted() != null, mask(config.getApiKeyEncrypted()), config.getEnabled(),
                config.getTemperature(), config.getMaxTokens(), config.getUpdatedAt(),
                config.getImageBaseUrl(), config.getImageModelName(),
                config.getImageApiKeyEncrypted() != null,
                config.getImageApiKeyEncrypted() == null ? "复用 LLM API Key" : mask(config.getImageApiKeyEncrypted()));
    }

    private String mask(String encrypted) {
        return encrypted == null ? "未配置" : "••••••••••••";
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
                                String imageApiKey, Boolean clearImageApiKey) {}

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

        public boolean imageAvailable() {
            return enabled && imageModelName != null && !imageModelName.isBlank()
                    && imageApiKey != null && !imageApiKey.isBlank();
        }
    }
}
