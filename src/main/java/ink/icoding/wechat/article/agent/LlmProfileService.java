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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LlmProfileService.class);
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

    /** 兜底档案：is_fallback=true；未设置返回 null（此时档案链自然少一环）。 */
    public LlmProfile fallbackProfile() {
        return mapper.findFallback();
    }

    /**
     * 模型档案故障切换链：按优先级返回可用的候选档案（已去重、已剔除不可用者）。
     *
     * <p>为什么需要它：{@code AgentInvoker} 的重试只会重建会话，而会话绑定的模型是在
     * {@code AgentClient.setModel} 时就定死的——同一个模型报 {@code model_not_found} /
     * {@code no available channel} / {@code invalid_api_key} 时，重发多少次结果都一样。
     * 这类错误此前被归为「永久错误」直接判死（见 {@code AgentInvoker.isPermanent}），
     * 于是上游下线一个模型（实测 agnes-3.0-flash）就得**手工改库**才能恢复。
     *
     * <p>顺序（每一环都是「更差但更可能活着」的退路）：
     * <ol>
     *   <li>智能体绑定的档案——该智能体的最优选择；</li>
     *   <li>默认档案——没绑定时它就是首选，绑定失效时它是第一退路；</li>
     *   <li>兜底档案——主用全部不可用时的安全网；</li>
     *   <li>其余已启用档案（按 id 升序）——最后把剩下的可能性都用上。</li>
     * </ol>
     *
     * @param bound 智能体绑定的档案，可为 null（未绑定）
     * @return 至少包含一项的有序候选；全表无可用档案时返回空列表（由调用方回落 llm_config）
     */
    public List<LlmProfile> failoverChain(LlmProfile bound) {
        List<LlmProfile> chain = new java.util.ArrayList<>();
        Set<Long> seen = new java.util.LinkedHashSet<>();
        addIfUsable(chain, seen, bound);
        addIfUsable(chain, seen, defaultProfile());
        addIfUsable(chain, seen, fallbackProfile());
        for (LlmProfile profile : mapper.findEnabled()) addIfUsable(chain, seen, profile);
        return chain;
    }

    /**
     * 候选可用性：已启用、有 API Key、且尚未入链。
     *
     * <p>去重按 id：绑定档案常常就是默认档案（7 个内置智能体当前全部如此），
     * 不去重会让档案链里出现两个同样的模型，白白浪费一次切换机会。
     */
    private void addIfUsable(List<LlmProfile> chain, Set<Long> seen, LlmProfile profile) {
        if (profile == null || profile.getId() == null) return;
        if (!Boolean.TRUE.equals(profile.getEnabled())) return;
        if (profile.getApiKeyEncrypted() == null || profile.getApiKeyEncrypted().isBlank()) return;
        if (!seen.add(profile.getId())) return;
        chain.add(profile);
    }

    /**
     * 图片模型来源档案：沿着**同一条故障切换链**找第一个声明了 {@code imageModelName} 的档案。
     *
     * <p>为什么复用失败切换链而不是只看绑定档案：配图模型与文本模型面对的是同一个现实——
     * 某个档案被停用/删掉/没配 key 时它就不该再被使用。链上的每一环都已经过
     * {@link #addIfUsable} 过滤，直接复用比另写一套「可用性」判断更不容易漂移。
     *
     * <p>顺序因此是：绑定档案 → 默认档案 → 兜底档案 → 其余已启用档案。绑定档案声明了图片模型时
     * 它自然胜出；没声明就往下找，全链无人声明则返回 {@code null}，由调用方回落全局图片模型
     * （{@code LLM_CONFIG.IMAGE_MODEL_NAME}，也就是改造前的唯一来源）。
     *
     * @param bound 智能体绑定的档案，可为 null（未绑定）
     */
    public LlmProfile imageCarrier(LlmProfile bound) {
        for (LlmProfile profile : failoverChain(bound)) {
            if (profile.getImageModelName() != null && !profile.getImageModelName().isBlank()) return profile;
        }
        return null;
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

    /**
     * 设为兜底档案：语义同 {@link #setDefault}，全局唯一。
     *
     * <p>兜底档案不应与默认档案是同一条：默认档案是「没绑定就用它」的日常主力，
     * 兜底档案是「主力全挂才用它」的最后退路；两者重合时档案链会少一环保护。
     * 这里只告警不拒绝——运营可能有意为之（例如只配一条档案），拒绝会让设置页卡住。
     */
    @Transactional
    public ProfileView setFallback(Long id) {
        LlmProfile target = required(id);
        if (Boolean.TRUE.equals(target.getIsDefault())) {
            log.warn("把默认档案 {} 同时设为兜底档案：档案链会少一环保护", target.getName());
        }
        for (LlmProfile profile : mapper.findAll()) {
            boolean shouldFallback = profile.getId().equals(id);
            if (!java.util.Objects.equals(profile.getIsFallback(), shouldFallback)) {
                profile.setIsFallback(shouldFallback);
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
        // 图片模型可空：空 = 该档案不指定（配图回落全局设置），因此不走 strip 之外的任何校验
        profile.setImageModelName(blankToNull(request.imageModelName()));
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
                profile.getEnabled(), Boolean.TRUE.equals(profile.getIsDefault()),
                Boolean.TRUE.equals(profile.getIsFallback()), profile.getImageModelName(),
                profile.getUpdatedAt());
    }

    /** 供 LlmConfigService 与 AgentFactory 使用的运行时视图（含解密后的 apiKey）。 */
    public record RuntimeProfile(boolean enabled, String provider, String baseUrl, String modelName, String apiKey,
                                 BigDecimal temperature, Integer maxTokens, boolean isDefault,
                                 String imageModelName) {
        public boolean available() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * 存量兼容：把 llm_config 的 LLM 字段写透到默认档案（不存在则创建）。
     * 存量 GET/PUT /api/settings/llm 保留，读写映射到默认档案 → 现有设置页不破坏（方案 4.3）。
     * 图片模型三件套继续留在 llm_config，不参与同步。
     *
     * <p><b>为什么这里用 {@code mapper.findDefault()} 而不是 {@link #defaultProfile()}：</b>
     * 后者是 {@code findDefault() ?? findFirst()}，那个「回落第一条」是**读**语义（运行时
     * 总得挑一条来用），拿来**写**就会改错对象——「表里有档案但一条都不是默认」时
     * （用户先在档案页自建了档案、却从没设过默认），它会把**第一条用户档案**的
     * provider/baseUrl/modelName/apiKey 全部覆盖，且**不给它打 is_default 标记**。
     * 后果是静默的：请求成功、设置页回显也对（读路径同样回落 findFirst），
     * 但用户下次打开档案页会发现自己的档案被动过。
     *
     * <p>找不到默认档案时的正确动作是 javadoc 说的「创建」：
     * ①若已存在一条**名为 {@value #DEFAULT_PROFILE_NAME}** 的档案（例如迁移来的那条被手工清掉了
     * 默认标记），就**收养**它并补上标记——否则会建出重名档案，而
     * {@code findByName}（种子解析绑定用的就是它）会变得有歧义；
     * ②否则新建一条带 is_default 的档案。
     *
     * <p><b>为什么不「把第一条就地提升为默认」</b>（那样能避免多出一条档案）：
     * {@link #delete} 拒绝删除默认档案，就地提升会把用户自建的档案变成**不可删**，
     * 等于替用户做了一个他从没同意过的决定。新建一条只多一行、且随时可删，代价更小。
     *
     * <p>同类教训在 {@code LlmProfileSeeder.defaultSource()} 里已经写过一次
     * （「不直接用 defaultProfile() 的回落语义」）——本方法当时漏用了这条结论。
     */
    @Transactional
    public void syncDefaultFromConfig(String provider, String baseUrl, String modelName, String apiKeyEncrypted,
                                      BigDecimal temperature, Integer maxTokens, Boolean enabled) {
        LlmProfile profile = mapper.findDefault();
        boolean created = false;
        if (profile == null) {
            profile = mapper.findByName(DEFAULT_PROFILE_NAME);
            if (profile != null) {
                profile.setIsDefault(true);
            } else {
                profile = new LlmProfile();
                profile.setName(DEFAULT_PROFILE_NAME);
                profile.setIsDefault(true);
                profile.setCreatedAt(LocalDateTime.now());
                created = true;
            }
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
                profile.getTemperature(), profile.getMaxTokens(), Boolean.TRUE.equals(profile.getIsDefault()),
                profile.getImageModelName());
    }

    public record ProfileRequest(@NotBlank String name, @NotBlank String provider, @NotBlank String baseUrl,
                                 @NotBlank String modelName, String apiKey, Boolean clearApiKey, Boolean enabled,
                                 @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
                                 @Min(256) @Max(32768) Integer maxTokens, String imageModelName) {
    }

    public record ProfileView(Long id, String name, String provider, String baseUrl, String modelName,
                              boolean hasApiKey, String apiKeyMasked, BigDecimal temperature, Integer maxTokens,
                              Boolean enabled, boolean isDefault, boolean isFallback, String imageModelName,
                              LocalDateTime updatedAt) {
    }
}
