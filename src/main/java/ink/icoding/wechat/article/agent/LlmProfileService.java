package ink.icoding.wechat.article.agent;

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
import java.util.List;
import java.util.Set;

/**
 * 模型档案 CRUD（skills-agent-plan 4.3 / 5.6，权限全 ADMIN）。
 * 兼容设计：llm_profile 为空且 llm_config 有数据时由 LlmProfileMigrationRunner 迁移为默认档案；
 * LlmConfigService.runtime() 内部改读默认档案，存量调用点零改动。
 */
@Service
public class LlmProfileService {
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "OPENAI_COMPATIBLE", "OPENAI_RESPONSES", "ANTHROPIC");
    private static final String DEFAULT_PROFILE_NAME = "默认配置";

    private final LlmProfileMapper mapper;
    private final CryptoService cryptoService;
    private final CurrentUserService currentUserService;

    public LlmProfileService(LlmProfileMapper mapper, CryptoService cryptoService,
                             CurrentUserService currentUserService) {
        this.mapper = mapper;
        this.cryptoService = cryptoService;
        this.currentUserService = currentUserService;
    }

    public List<ProfileView> list() {
        return mapper.findAll().stream().map(this::view).toList();
    }

    public ProfileView get(Long id) {
        return view(required(id));
    }

    /** 可空查找（软引用容错：绑定的档案被删除时返回 null，由调用方回落默认档案）。 */
    public LlmProfile findById(Long id) {
        return id == null ? null : mapper.findById(id);
    }

    public LlmProfile required(Long id) {
        LlmProfile profile = mapper.findById(id);
        if (profile == null) throw new BusinessException("模型档案不存在");
        return profile;
    }

    /** 默认档案：is_default=true；无标记时回落第一条；全表为空返回 null（由调用方回落 llm_config）。 */
    public LlmProfile defaultProfile() {
        LlmProfile profile = mapper.findDefault();
        return profile == null ? mapper.findFirst() : profile;
    }

    @Transactional
    public ProfileView create(ProfileRequest request, Long userId) {
        LlmProfile profile = new LlmProfile();
        apply(profile, request);
        validateNameUnique(profile.getName(), null);
        profile.setIsDefault(false);
        profile.setCreatedBy(userId);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        mapper.insert(profile);
        return view(required(profile.getId()));
    }

    @Transactional
    public ProfileView update(Long id, ProfileRequest request) {
        LlmProfile profile = required(id);
        apply(profile, request);
        validateNameUnique(profile.getName(), id);
        profile.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(profile);
        return view(required(id));
    }

    @Transactional
    public void delete(Long id) {
        LlmProfile profile = required(id);
        if (Boolean.TRUE.equals(profile.getIsDefault())) {
            throw new BusinessException("默认档案不可删除，请先把其他档案设为默认");
        }
        mapper.deleteById(id);
    }

    /** 设为默认：同一事务内先清空其他档案的默认标记（应用层保证全局唯一）。 */
    @Transactional
    public ProfileView setDefault(Long id) {
        LlmProfile target = required(id);
        for (LlmProfile profile : mapper.findAll()) {
            boolean shouldDefault = profile.getId().equals(id);
            if (!java.util.Objects.equals(profile.getIsDefault(), shouldDefault)) {
                profile.setIsDefault(shouldDefault);
                profile.setUpdatedAt(LocalDateTime.now());
                mapper.updateById(profile);
            }
        }
        return view(required(target.getId()));
    }

    private void apply(LlmProfile profile, ProfileRequest request) {
        if (request.name() == null || request.name().isBlank()) throw new BusinessException("请填写档案名称");
        if (!SUPPORTED_PROVIDERS.contains(request.provider())) {
            throw new BusinessException("不支持的 LLM 服务类型：" + request.provider());
        }
        profile.setName(request.name().strip());
        profile.setProvider(request.provider());
        profile.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        profile.setModelName(request.modelName().strip());
        profile.setTemperature(request.temperature() == null ? new BigDecimal("0.70") : request.temperature());
        profile.setMaxTokens(request.maxTokens() == null ? 4096 : request.maxTokens());
        profile.setEnabled(request.enabled() == null || request.enabled());
        if (Boolean.TRUE.equals(request.clearApiKey())) profile.setApiKeyEncrypted(null);
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            profile.setApiKeyEncrypted(cryptoService.encrypt(request.apiKey().trim()));
        }
        if (Boolean.TRUE.equals(profile.getEnabled()) && profile.getApiKeyEncrypted() == null) {
            throw new BusinessException("启用模型档案前必须配置 API Key");
        }
    }

    private void validateNameUnique(String name, Long excludeId) {
        LlmProfile existing = mapper.findByName(name);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("档案名称已存在：" + name);
        }
    }

    /** Base URL 归一化：去尾斜杠并剥离常见端点后缀（与 LlmConfigService 逻辑一致）。 */
    static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) throw new BusinessException("Base URL 不能为空");
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
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            throw new BusinessException("Base URL 必须以 http:// 或 https:// 开头");
        }
        return result;
    }

    private ProfileView view(LlmProfile profile) {
        String apiKey = profile.getApiKeyEncrypted() == null
                ? null : cryptoService.decrypt(profile.getApiKeyEncrypted());
        // 掩码：key 长度 < 12 时全掩码（避免短 key 通过末 4 位泄露大部分内容）
        String masked = apiKey == null || apiKey.isBlank()
                ? "未配置"
                : apiKey.length() >= 12 ? "••••••••" + apiKey.substring(apiKey.length() - 4) : "••••••••••••";
        return new ProfileView(profile.getId(), profile.getName(), profile.getProvider(),
                normalizeBaseUrl(profile.getBaseUrl()), profile.getModelName(),
                profile.getApiKeyEncrypted() != null, masked, profile.getTemperature(), profile.getMaxTokens(),
                profile.getEnabled(), Boolean.TRUE.equals(profile.getIsDefault()), profile.getUpdatedAt());
    }

    /** 供 LlmConfigService 与 AgentFactory 使用的运行时视图（含解密后的 apiKey）。 */
    public record RuntimeProfile(boolean enabled, String provider, String baseUrl, String modelName, String apiKey,
                                 BigDecimal temperature, Integer maxTokens, boolean isDefault) {
        public boolean available() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * 存量兼容：把 llm_config 的 LLM 字段写透到默认档案（不存在则创建）。
     * 存量 GET/PUT /api/settings/llm 保留，读写映射到默认档案 → 现有设置页不破坏（方案 4.3）。
     * 图片模型三件套继续留在 llm_config，不参与同步。
     */
    @Transactional
    public void syncDefaultFromConfig(String provider, String baseUrl, String modelName, String apiKeyEncrypted,
                                      BigDecimal temperature, Integer maxTokens, Boolean enabled) {
        LlmProfile profile = defaultProfile();
        boolean created = false;
        if (profile == null) {
            profile = new LlmProfile();
            profile.setName(DEFAULT_PROFILE_NAME);
            profile.setIsDefault(true);
            profile.setCreatedAt(LocalDateTime.now());
            created = true;
        }
        profile.setProvider(provider);
        profile.setBaseUrl(baseUrl);
        profile.setModelName(modelName);
        profile.setApiKeyEncrypted(apiKeyEncrypted);
        profile.setTemperature(temperature);
        profile.setMaxTokens(maxTokens);
        profile.setEnabled(enabled);
        profile.setUpdatedAt(LocalDateTime.now());
        if (created) {
            mapper.insert(profile);
        } else {
            mapper.updateById(profile);
        }
    }

    /** 读取运行时档案（解密 apiKey）；profile 为空返回 null。 */
    public RuntimeProfile runtime(LlmProfile profile) {
        if (profile == null) return null;
        String apiKey = profile.getApiKeyEncrypted() == null
                ? null : cryptoService.decrypt(profile.getApiKeyEncrypted());
        return new RuntimeProfile(Boolean.TRUE.equals(profile.getEnabled()), profile.getProvider(),
                normalizeBaseUrl(profile.getBaseUrl()), profile.getModelName(), apiKey,
                profile.getTemperature(), profile.getMaxTokens(), Boolean.TRUE.equals(profile.getIsDefault()));
    }

    public record ProfileRequest(@NotBlank String name, @NotBlank String provider, @NotBlank String baseUrl,
                                 @NotBlank String modelName, String apiKey, Boolean clearApiKey, Boolean enabled,
                                 @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
                                 @Min(256) @Max(32768) Integer maxTokens) {
    }

    public record ProfileView(Long id, String name, String provider, String baseUrl, String modelName,
                              boolean hasApiKey, String apiKeyMasked, BigDecimal temperature, Integer maxTokens,
                              Boolean enabled, boolean isDefault, LocalDateTime updatedAt) {
    }
}
