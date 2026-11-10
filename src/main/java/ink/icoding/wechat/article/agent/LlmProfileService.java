package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 模型档案 CRUD（skills-agent-plan 4.3 / 5.6，权限全 ADMIN）。
 * 兼容设计：llm_profile 为空且 llm_config 有数据时由 LlmProfileMigrationRunner 迁移为默认档案；
 * LlmConfigService.runtime() 内部改读默认档案，存量调用点零改动。
 *
 * <p><b>本轮（llm-provider-refactor §3.2 / §4）：端点与密钥搬到供应商</b>。档案只声明「用哪个模型」，
 * 多一个 {@code providerId} 外键；{@code provider} / {@code baseUrl} / {@code apiKeyEncrypted} 三列
 * **保留但冻结为存量快照**，仅在 {@code providerId} 为空时参与运行时解析。因此本类的核心是
 * {@link #runtime(LlmProfile)} 一处口径，链（{@link #addIfUsable}）、配图（{@link #imageCarrier}）、
 * {@code AgentFactory} 全部走它。
 */
@Service
public class LlmProfileService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LlmProfileService.class);
    private static final String DEFAULT_PROFILE_NAME = "默认配置";

    private final LlmProfileMapper mapper;
    private final LlmProviderService providerService;
    private final CryptoService cryptoService;
    private final CurrentUserService currentUserService;

    public LlmProfileService(LlmProfileMapper mapper, LlmProviderService providerService,
                             CryptoService cryptoService, CurrentUserService currentUserService) {
        this.mapper = mapper;
        this.providerService = providerService;
        this.cryptoService = cryptoService;
        this.currentUserService = currentUserService;
    }

    /**
     * 档案列表。
     *
     * <p>供应商先一次性读全建索引（{@link LlmProviderService#findAll()}），而不是在 {@link #view} 里
     * 逐条 {@code findById}：这份列表就是档案页的首屏，供应商行数是个位数，
     * 一次查询换掉 N 次查询是白拿的（N+1 在手工建过几十条档案的库里会真的慢到看得见）。
     */
    public List<ProfileView> list() {
        Map<Long, LlmProvider> providers = new HashMap<>();
        for (LlmProvider provider : providerService.findAll()) providers.put(provider.getId(), provider);
        return mapper.findAll().stream().map(profile -> view(profile, providers)).toList();
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
     * <p><b>这四跳的调用序列不得改动</b>：{@code webui/scripts/check-profile-chain-parity.mjs} 的
     * A 段结构自检按正则匹配这四个实参名（{@code bound} / {@code defaultProfile()} /
     * {@code fallbackProfile()} / {@code profile}），用来发现前后端档案链的漂移。允许改的只有
     * {@link #addIfUsable} 的**判定体**。
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
     * 候选可用性：**跑一遍运行时解析**（{@link #runtime(LlmProfile)}）后仍可用，且尚未入链。
     *
     * <p>为什么判定体改成走运行时解析，而不是继续只看「档案自己的 enabled + 那一列的密文」：
     * 供应商被停用、供应商没配 key、供应商被删（悬空）这三种情况下，档案自身的两列**看起来完全正常**，
     * 只有运行时解析知道它其实不可用。判定体若各写一套，「链上算可用」与「真要发请求时算可用」
     * 就会漂移——而漂移的后果正是这条链最忌讳的：选中一个必然失败的档案，白等一轮超时。
     *
     * <p>为什么外面套一层 catch：链的职责是「总还有一条能用」，一条脏行（密钥解不开——例如
     * APP_SECRET_KEY 换过、或手工写入过明文）不该把**整条链**带走。这里跳过它，链上其余档案照常可用，
     * 而且它也就不会流到 {@code AgentFactory} 里再抛一次。
     *
     * <p>去重按 id：绑定档案常常就是默认档案（7 个内置智能体当前全部如此），
     * 不去重会让档案链里出现两个同样的模型，白白浪费一次切换机会。
     */
    private void addIfUsable(List<LlmProfile> chain, Set<Long> seen, LlmProfile profile) {
        if (profile == null || profile.getId() == null) return;
        RuntimeProfile runtime;
        try {
            runtime = runtime(profile);
        } catch (RuntimeException exception) {
            log.warn("模型档案「{}」(id={}) 的运行时解析失败（跳过该档案，链上其余档案不受影响）",
                    profile.getName(), profile.getId(), exception);
            return;
        }
        if (runtime == null || !runtime.available()) return;
        if (!seen.add(profile.getId())) return;
        chain.add(profile);
    }

    /**
     * 图片模型来源档案：沿着**同一条故障切换链**按两段找。
     *
     * <p><b>第一段（主路径，契约 §4.3 定稿）</b>：第一个勾选了「这是图片模型」
     * （{@code isImageModel=true}）的档案。它的 {@code modelName} 就是图片模型名，
     * 端点与密钥来自它所属的供应商。
     *
     * <p><b>第二段（①b 旧数据兼容，只读不改）</b>：链上没人勾选时，再看有没有档案声明了非空
     * {@code imageModelName}。那是第二版设计（卡片上填「图片模型名称」文本框）的落点，
     * 界面已经不再暴露这个输入框，但**已经这样配过的部署不能炸**——所以这一段只读，
     * 且必须排在第一段之后：勾选是用户现在的表达方式，应当优先于历史残留值。
     *
     * <p>为什么复用失败切换链而不是只看绑定档案：配图模型与文本模型面对的是同一个现实——
     * 某个档案被停用/删掉/没配 key 时它就不该再被使用。链上的每一环都已经过
     * {@link #addIfUsable} 过滤，直接复用比另写一套「可用性」判断更不容易漂移。
     * 「勾选但不可用 ⇒ 跳过」因此是**免费**的：它压根不在链里。
     *
     * <p>两段都为空的链返回 {@code null}，由调用方回落 {@code llm_config} 图片三件套
     * （改造前的唯一来源）。
     *
     * @param bound 智能体绑定的档案，可为 null（未绑定）
     */
    public LlmProfile imageCarrier(LlmProfile bound) {
        List<LlmProfile> chain = failoverChain(bound);
        for (LlmProfile profile : chain) {
            if (Boolean.TRUE.equals(profile.getIsImageModel())) return profile;
        }
        // ①b：第二版留下的 imageModelName 落点。只读，保证老部署升级后配图行为不变
        for (LlmProfile profile : chain) {
            if (profile.getImageModelName() != null && !profile.getImageModelName().isBlank()) return profile;
        }
        return null;
    }

    /**
     * 承载档案的**图片模型名**：① 用它自己的 {@code modelName}；①b 用它的 {@code imageModelName}。
     *
     * <p>为什么把这条判断收在这里而不是让调用方自己看 {@code isImageModel}：两种来源的区分只存在于
     * 本类内部（哪一段选中了它），调用方（{@code LlmConfigService}）只该问「用哪个模型名」。
     * 让它去判 {@code isImageModel} 等于把 §4.3 的两段语义复制到第二个文件里——两处一旦漂移，
     * 症状是配图拿着 {@code imageModelName} 的旧值去打一个已经改名的模型，报错在很远的上游。
     *
     * @param carrier {@link #imageCarrier} 的返回值，可为 null
     * @return 图片模型名；{@code carrier} 为 null 或无可用模型名时返回 null
     */
    public String imageModelNameOf(LlmProfile carrier) {
        if (carrier == null) return null;
        return Boolean.TRUE.equals(carrier.getIsImageModel())
                ? blankToNull(carrier.getModelName())
                : blankToNull(carrier.getImageModelName());
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
            if (!Objects.equals(profile.getIsDefault(), shouldDefault)) {
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
            if (!Objects.equals(profile.getIsFallback(), shouldFallback)) {
                profile.setIsFallback(shouldFallback);
                profile.setUpdatedAt(LocalDateTime.now());
                mapper.updateById(profile);
            }
        }
        return view(required(target.getId()));
    }

    /**
     * 写入档案（新建与更新共用）。
     *
     * <p><b>为什么不把供应商的 provider/baseUrl/apiKeyEncrypted 回写进档案的三列</b>
     * （契约 §3.2 说这三列「冻结为存量快照」）：回写等于把端点与密钥又复制了一份，而且是一份
     * **会过期的副本**——用户改完供应商的 key 之后，档案里还留着上一把；将来清空 {@code providerId}
     * （或回滚到改造前版本）时，旧 key 与旧端点会静默复活。冻结则相反：老行的三列保持原样供回退，
     * 新 API 建的行这三列为空，谁都不读它们（{@code providerId} 非空时）。
     *
     * <p><b>为什么不再校验「启用前必须有 key」</b>：档案已经没有 key 了，这条校验搬到了供应商
     * （{@code LlmProviderService.apply}）。启用了一条挂在没有 key 的供应商下的档案不会报错，
     * 它只是**在故障切换链上自动消失**（{@link #runtime} 的 available() 为假），这正是契约 §4 的口径。
     */
    private void apply(LlmProfile profile, ProfileRequest request) {
        if (request.name() == null || request.name().isBlank()) throw new BusinessException("请填写档案名称");
        // 指向不存在的供应商 ⇒ BusinessException("供应商不存在")：档案不能挂在空气上，
        // 否则它一出生就是「悬空」——列表接口要能列出悬空的**存量**坏数据，但新写入不该制造它们
        LlmProvider provider = providerService.required(request.providerId());
        profile.setName(request.name().strip());
        profile.setProviderId(provider.getId());
        profile.setModelName(request.modelName().strip());
        // 图片模型名（①b 旧数据兼容的落点）：界面不再传它，但旧请求与旧数据仍可能带着值。
        // 传了就必须写回——否则一次普通保存会把用户历史配置的图片模型悄悄抹掉。
        profile.setImageModelName(blankToNull(request.imageModelName()));
        // 「这是图片模型」复选框：null = 本次不动（与 enabled 同一套三态语义）。
        // 新建行没有旧值可保留，落成 false——「没有勾选」就是默认状态，不需要写入 NULL。
        if (request.isImageModel() != null) {
            profile.setIsImageModel(request.isImageModel());
        } else if (profile.getIsImageModel() == null) {
            profile.setIsImageModel(false);
        }
        profile.setTemperature(request.temperature() == null ? new BigDecimal("0.70") : request.temperature());
        profile.setMaxTokens(request.maxTokens() == null ? 4096 : request.maxTokens());
        profile.setEnabled(request.enabled() == null || request.enabled());
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

    /** 归一化的读取版：空/不能归一化 ⇒ null（读路径不抛，理由见 {@link #runtime}）。 */
    private static String normalizeBaseUrlOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return normalizeBaseUrl(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 档案视图（单条读取用）。
     *
     * <p>{@code hasApiKey} / {@code apiKeyMasked} 的取值口径按「**该档案在运行时拿得到密钥**」：
     * 供应商行有 key ⇒ 是；存量回退行 ⇒ 看它自己的旧列；供应商已不存在（悬空）⇒ 否。
     * 供应商名与启用状态同理（契约 §5.2 与 Lead 2026-11-10 的两条补充）：
     * <ul>
     *   <li>悬空 ⇒ {@code providerName=null}、{@code providerEnabled=false}、{@code hasApiKey=false}、
     *       掩码 {@code 未配置}。**不抛异常**：列表接口必须能列出坏数据，否则用户看不到问题、
     *       也就无从修复（前端据此渲染成管理员告警态）。</li>
     *   <li>回退行（{@code providerId} 为空）⇒ {@code providerName=null}、{@code providerEnabled=true}：
     *       没有供应商不等于停用，把判断留给档案自己的 {@code enabled}。若这里填 false，
     *       升级窗口里所有存量档案会在前端链上凭空消失，而后端仍然可用——前端报的模型名与后端
     *       实际用的不一致，正是这条链最忌讳的失效模式。</li>
     * </ul>
     */
    private ProfileView view(LlmProfile profile) {
        return view(profile, null);
    }

    private ProfileView view(LlmProfile profile, Map<Long, LlmProvider> providerCache) {
        Long providerId = profile.getProviderId();
        String providerName = null;
        // 缺省 true：见上面第 2 条（回退行不得被当成「供应商已停用」）
        boolean providerEnabled = true;
        LlmProvider providerRow = null;
        if (providerId != null) {
            providerRow = providerCache != null
                    ? providerCache.get(providerId) : providerService.findById(providerId);
            if (providerRow == null) {
                providerEnabled = false;
            } else {
                providerName = providerRow.getName();
                providerEnabled = Boolean.TRUE.equals(providerRow.getEnabled());
            }
        }
        // 可用性不在这里重新推导：直接问 runtime(...)，保证与故障切换链用的是同一段判据（契约 §3.3）
        RuntimeProfile runtime = runtime(profile, providerRow);
        String apiKey = runtime == null ? null : runtime.apiKey();
        String masked = maskedKey(profile, providerRow);
        return new ProfileView(profile.getId(), profile.getName(), providerId, providerName, providerEnabled,
                profile.getModelName(), apiKey != null && !apiKey.isBlank(), masked,
                profile.getTemperature(), profile.getMaxTokens(),
                profile.getEnabled(), Boolean.TRUE.equals(profile.getIsDefault()),
                Boolean.TRUE.equals(profile.getIsFallback()), profile.getImageModelName(),
                // 视图里给基本类型：前端只需要「勾没勾」，NULL 与 false 对它没有区别
                Boolean.TRUE.equals(profile.getIsImageModel()),
                profile.getUpdatedAt(), runtime != null && runtime.available());
    }

    /**
     * 该档案在界面上显示的密钥掩码。
     *
     * <p>公开出来的唯一理由是 {@code LlmConfigService.view} 的「默认档案存在」分支：
     * 设置页与档案页显示的是同一份密钥，两处必须得到**同一个**字符串。各算一套的话会出现
     * 一页说「无法解密」、另一页说「未配置」——管理员按任一条走都会走错。
     *
     * <p>判据的来源是**密文**而不是解密结果：只有看到密文才知道该显示「未配置」还是
     * 「无法解密（请检查 APP_SECRET_KEY）」（复查项 M-2）。
     */
    public String maskedKey(LlmProfile profile) {
        if (profile == null) return LlmProviderService.maskApiKey(null);
        LlmProvider providerRow = profile.getProviderId() == null ? null
                : providerService.findById(profile.getProviderId());
        return maskedKey(profile, providerRow);
    }

    private String maskedKey(LlmProfile profile, LlmProvider providerRow) {
        Long providerId = profile.getProviderId();
        String ciphertext;
        String subject;
        if (providerId == null) {
            ciphertext = profile.getApiKeyEncrypted();
            subject = "模型档案 id=" + profile.getId();
        } else {
            ciphertext = providerRow == null ? null : providerRow.getApiKeyEncrypted();
            subject = "供应商 id=" + providerId;
        }
        return LlmProviderService.maskCiphertext(cryptoService, ciphertext, subject);
    }

    /**
     * 供 LlmConfigService 与 AgentFactory 使用的运行时视图（含解密后的 apiKey）。
     *
     * <p>记录形状与改造前**逐字相同**（{@code AgentFactory} / {@code LlmConfigService} 的调用点因此零改动），
     * 只有取值口径变成本轮的唯一口径（契约 §4）：
     * <pre>
     * providerId != null → 取供应商：
     *     供应商不存在（被删/脏数据） ⇒ enabled=false ⇒ available()==false（调用方跳过）
     *     否则 provider/baseUrl 取自供应商行；enabled = 档案.enabled &amp;&amp; 供应商.enabled
     * providerId == null → 存量回退：provider/baseUrl/apiKey 取档案自身的旧列，enabled = 档案.enabled
     * </pre>
     *
     * <p><b>供应商不存在时为什么不回退档案自身的旧列</b>：那会变成「静默用错端点」——档案里的旧端点
     * 与旧 key 可能是几个月前的快照，请求会打到一个用户早已不打算用的网关上，而且**不报错**。
     * 宁可判它不可用（管理员在档案页会看到「供应商已不存在」），也不替用户猜一个端点。
     *
     * <p>同理，回退路径上的空/非法 baseUrl 返回 null 而不是抛异常：这条口径在
     * {@link #addIfUsable} 里会对**链上每一条**已启用档案跑一遍，任何一条脏行都不该让整条链抛
     * ——失败切换的全部意义就是「总还有一条能用」。筛掉它由
     * {@link RuntimeProfile#available()} 完成（要求 provider/baseUrl/apiKey 三者非空）。
     */
    public RuntimeProfile runtime(LlmProfile profile) {
        if (profile == null) return null;
        LlmProvider providerRow = profile.getProviderId() == null
                ? null : providerService.findById(profile.getProviderId());
        return runtime(profile, providerRow);
    }

    /**
     * 与 {@link #runtime(LlmProfile)} **同一口径**，只是供应商行由调用方预先取到/缓存。
     *
     * <p>为什么必须有这个重载：{@link #view} 要输出 {@code runtimeAvailable}（契约 §3.3），
     * 而「可用性」的判据只许有一个真源（{@link RuntimeProfile#available()}）。若 view 里另写一套
     * {@code enabled && 供应商.enabled && 有 key}，两处就会漂移——漂移的后果是列表页显示「可用」、
     * 请求却必然失败。走这个重载，view 与链上的判断**真的是同一段代码**，同时避免列表接口 N+1。
     *
     * @param providerRow 已挂供应商的行；{@code null} 表示「悬空（providerId 非空）」或「无供应商」，由 profile 区分
     */
    private RuntimeProfile runtime(LlmProfile profile, LlmProvider providerRow) {
        if (profile == null) return null;
        boolean profileEnabled = Boolean.TRUE.equals(profile.getEnabled());
        boolean isDefault = Boolean.TRUE.equals(profile.getIsDefault());
        if (profile.getProviderId() != null) {
            if (providerRow == null) {
                return new RuntimeProfile(false, null, null, profile.getModelName(), null,
                        profile.getTemperature(), profile.getMaxTokens(), isDefault, profile.getImageModelName());
            }
            LlmProviderService.RuntimeProvider provider = providerService.runtime(providerRow);
            return new RuntimeProfile(profileEnabled && provider.enabled(), provider.provider(), provider.baseUrl(),
                    profile.getModelName(), provider.apiKey(), profile.getTemperature(), profile.getMaxTokens(),
                    isDefault, profile.getImageModelName());
        }
        // 读路径的容错解密（B3 / M-2）：解不开按「未配置」降级，绝不让整页 500
        String apiKey = LlmProviderService.decryptOrNull(cryptoService, profile.getApiKeyEncrypted(),
                "模型档案 id=" + profile.getId());
        return new RuntimeProfile(profileEnabled, profile.getProvider(),
                normalizeBaseUrlOrNull(profile.getBaseUrl()), profile.getModelName(), apiKey,
                profile.getTemperature(), profile.getMaxTokens(), isDefault, profile.getImageModelName());
    }

    /**
     * 该档案在运行时能否拿到密钥（供应商有 key，或存量回退行自身旧列有 key）。
     *
     * <p>为什么种子需要它（{@code LlmProfileSeeder.defaultSource()}）：种子的来源选择判据一直是
     * 「一条**有 key** 的档案」——库里的空档案不该被当成来源，否则复制出来的标准档案同样不可用。
     * 改造后档案的 key 搬到了供应商上：**用户改走新 UI 建默认档案时，档案自己的旧列不再写入**，
     * 若判据仍只看那一列，种子会认为「库里没有可用来源」而静默停机——症状是开箱只有一条默认档案，
     * 7 个内置智能体全挤在同一个模型上，故障切换链退回一环，而且**不报任何错**。
     */
    public boolean hasReachableKey(LlmProfile profile) {
        if (profile == null) return false;
        String ciphertext;
        String subject;
        if (profile.getProviderId() != null) {
            LlmProvider provider = providerService.findById(profile.getProviderId());
            ciphertext = provider == null ? null : provider.getApiKeyEncrypted();
            subject = "供应商 id=" + profile.getProviderId();
        } else {
            ciphertext = profile.getApiKeyEncrypted();
            subject = "模型档案 id=" + profile.getId();
        }
        String apiKey = LlmProviderService.decryptOrNull(cryptoService, ciphertext, subject);
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 存量兼容：把 llm_config 的 LLM 字段写透到默认档案（不存在则创建），并把默认档案挂到
     * 「默认供应商」上（契约 §5.3）。
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
     *
     * <p><b>供应商侧</b>：先按 {@code (provider, 归一化 baseUrl, 解密后的明文 key)} 三元组解析
     * （命中即复用，否则复用/创建「{@value LlmProviderService#DEFAULT_PROVIDER_NAME}」那一行），
     * 再写档案。顺序如此是为了「供应商解析失败时档案不被改动」——设置页保存要么整件事生效，
     * 要么一点都不动。注意匹配用的是**明文** key：AES-GCM 每次随机 IV，密文相等并不代表同一个 key
     * （见 {@link LlmProviderService}）。
     */
    @Transactional
    public void syncDefaultFromConfig(String provider, String baseUrl, String modelName, String apiKeyEncrypted,
                                      BigDecimal temperature, Integer maxTokens, Boolean enabled) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        Long providerId = providerService.resolveDefaultProviderId(provider, normalizedBaseUrl,
                apiKeyEncrypted, enabled);
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
        profile.setProviderId(providerId);
        // 旧三列继续写：这条路径是设置页的快捷入口，而存量回退口径（providerId 为空）恰恰要读它们，
        // 保留写入等于「万一以后清空 providerId，端点与密钥还在原处」
        profile.setProvider(provider);
        profile.setBaseUrl(normalizedBaseUrl);
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

    /**
     * 档案写入请求（契约 §5.2）。
     *
     * <p>{@code provider} / {@code baseUrl} / {@code apiKey} / {@code clearApiKey} 四个字段被**移除**：
     * 端点与密钥归供应商，档案只声明「哪个模型」。{@code providerId} 必填——它取代了原先那三个字段
     * 所表达的「用哪个通道」。
     */
    public record ProfileRequest(@NotBlank String name, @NotNull Long providerId, @NotBlank String modelName,
                                 Boolean enabled, @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
                                 @Min(256) @Max(32768) Integer maxTokens, String imageModelName,
                                 Boolean isImageModel) {
    }

    /**
     * 档案视图（契约 §5.2）。
     *
     * <p>与改造前的差异：去掉 {@code provider} / {@code baseUrl}（前端改从供应商行取），
     * 增加 {@code providerId} / {@code providerName} / {@code providerEnabled}。
     * {@code hasApiKey} 的语义仍是「运行时拿得到密钥」，只是来源从档案自己的列变成了供应商。
     */
    public record ProfileView(Long id, String name, Long providerId, String providerName, boolean providerEnabled,
                              String modelName, boolean hasApiKey, String apiKeyMasked, BigDecimal temperature,
                              Integer maxTokens, Boolean enabled, boolean isDefault, boolean isFallback,
                              String imageModelName, boolean isImageModel, LocalDateTime updatedAt,
                              boolean runtimeAvailable) {
    }

    /**
     * 运行时视图（含解密后的 apiKey）。字段与改造前逐字相同，调用点零改动。
     *
     * <p>{@code available()} 本轮**收紧**为「启用 + 能拼出一次真实请求」：除 {@code apiKey} 外还要求
     * {@code provider} 与 {@code baseUrl} 非空。原因是链的判定体现在会对链上每条档案跑
     * {@link #runtime}，而 {@code AgentFactory} 拿到 {@code available()==true} 的候选后会直接
     * {@code createModel(provider, baseUrl, ...)}——那里对未知/空的 provider 抛
     * {@code BusinessException("不支持的 LLM 服务类型")}。若 {@code available()} 只判 key，
     * 一条「有 key 但端点为空」的脏行就会从「扫链时被跳过」变成「选中之后把整个请求打挂」，
     * 比原来的失败形态更糟。供应商路径天然满足这两个条件（创建时 {@code @NotBlank}），
     * 因此收紧只对存量脏行生效，正是目的。
     */
    public record RuntimeProfile(boolean enabled, String provider, String baseUrl, String modelName, String apiKey,
                                 BigDecimal temperature, Integer maxTokens, boolean isDefault,
                                 String imageModelName) {
        public boolean available() {
            return enabled
                    && provider != null && !provider.isBlank()
                    && baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }

        /**
         * 手写 {@code toString()}（复查项 M-4）：record 自动生成的版本会把{@code apiKey} **明文**
         * 带进任何一次日志、异常链或调试打印。本次改造已经确保没有任何调用点主动打印它，
         * 但「不主动打印」防不住将来某次 {@code log.warn("...{}", runtime)} 或
         * {@code BusinessException("..." + runtime)}——那条通道一旦打开，密钥就直接落到日志文件。
         */
        @Override
        public String toString() {
            return "RuntimeProfile[enabled=" + enabled + ", provider=" + provider + ", baseUrl=" + baseUrl
                    + ", modelName=" + modelName + ", apiKey=" + LlmProviderService.redactSecret(apiKey)
                    + ", temperature=" + temperature + ", maxTokens=" + maxTokens
                    + ", isDefault=" + isDefault + ", imageModelName=" + imageModelName + "]";
        }
    }
}
