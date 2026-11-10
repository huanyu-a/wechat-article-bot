package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import ink.icoding.smartmybatis.entity.expression.Where;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 模型供应商 CRUD 与「按三元组归组」（llm-provider-refactor §3.1 / §5.1 / §5.4，权限全 ADMIN）。
 *
 * <p>供应商承载端点与密钥，{@link LlmProfile} 只声明「哪个模型」。因此本类是「改一次 key 全部生效」
 * 这个诉求的落点：档案侧不再有 baseUrl/apiKey 输入框（旧列保留为存量快照，见 {@link LlmProfile}）。
 *
 * <p><b>为什么「按三元组归组」的第三段必须用解密后的明文 key，而不是密文</b>：
 * {@code CryptoService} 用 AES-GCM + 每次随机 IV，**同一明文加密两次得到不同密文**（Lead 实测：
 * 3 条同 key 档案的 {@code MD5(API_KEY_ENCRYPTED)} 三个不同值）。按密文归组 ⇒ 每条手工建的档案
 * 各分到一个供应商，本次改造的意义直接归零（那不是「收敛」，只是「换个地方重复」）。
 * 明文只在内存里做相等比较，**不写日志、不进异常消息、不进任何响应、不新增列**，比完即弃
 * （因此下文的归组暂存类故意不用 record：record 自动生成的 {@code toString()} 会把明文带进日志）。
 */
@Service
public class LlmProviderService {
    private static final Logger log = LoggerFactory.getLogger(LlmProviderService.class);

    /**
     * 支持的 API 协议。
     *
     * <p>与 {@code LlmProfileService} / {@code LlmConfigService} 各持一份同样的清单（既有风格如此）：
     * 三处的**列声明**必须逐字相同，而校验清单是各自的入参契约，合并反而会让三处互相绑架。
     */
    static final Set<String> SUPPORTED_PROVIDERS = Set.of(
            "OPENAI_COMPATIBLE", "OPENAI_RESPONSES", "ANTHROPIC");

    /** 存量设置页（{@code PUT /api/settings/llm}）写透用的供应商名（契约 §5.3 第 2 条）。 */
    public static final String DEFAULT_PROVIDER_NAME = "默认供应商";

    /** 按 host 命名时取不到 host 的兜底名（baseUrl 能过归一化却解析不出 host，例如含非法字符的极少数情形）。 */
    private static final String FALLBACK_PROVIDER_NAME = "供应商";

    /** 重名追加序号的上限：真实场景个位数，给出上界只是为了不写出无界循环。 */
    private static final int MAX_NAME_SUFFIX = 100;

    /**
     * 密文存在却解不开时给管理员的提示（复查项 M-2）。
     *
     * <p>不许退化成「未配置」：那会把人引向「我没填过 key」的方向，而真正的原因是
     * {@code APP_SECRET_KEY} 变了——两个方向的修复动作完全不同。
     */
    public static final String UNDECRYPTABLE_MASK = "无法解密（请检查 APP_SECRET_KEY）";

    private final LlmProviderMapper mapper;
    private final LlmProfileMapper profileMapper;
    private final CryptoService cryptoService;
    private final ModelDiscoveryClient modelDiscovery;

    public LlmProviderService(LlmProviderMapper mapper, LlmProfileMapper profileMapper,
                              CryptoService cryptoService, ModelDiscoveryClient modelDiscovery) {
        this.mapper = mapper;
        this.profileMapper = profileMapper;
        this.cryptoService = cryptoService;
        this.modelDiscovery = modelDiscovery;
    }

    /**
     * 供应商列表。
     *
     * <p>{@code modelCount} 是逐行 count（N 次查询），不做 join：供应商行数是个位数，
     * 而这份列表本身就是「给管理员看的一屏」，多一条 join 只会多一处需要跟着实体改的地方。
     */
    public List<ProviderView> list() {
        return mapper.findAll().stream().map(this::view).toList();
    }

    public ProviderView get(Long id) {
        return view(required(id));
    }

    /** 可空查找（供 {@link LlmProfileService} 解析运行时口径：档案挂靠的供应商可能已被删除）。 */
    public LlmProvider findById(Long id) {
        return id == null ? null : mapper.findById(id);
    }

    /** 全部供应商（按 id 升序）：{@code LlmProfileService.list()} 据此一次性建索引，避免逐行查库。 */
    public List<LlmProvider> findAll() {
        return mapper.findAll();
    }

    public LlmProvider required(Long id) {
        LlmProvider provider = findById(id);
        if (provider == null) throw new BusinessException("供应商不存在");
        return provider;
    }

    /**
     * 模型发现（契约 §2.2）：用该供应商自己的端点与密钥拉取可用模型名列表。
     *
     * <p>三条边界：
     * <ol>
     *   <li>无 key ⇒ 直接失败并提示「请先配置 API Key」，**不发请求**：既省一次出网，
     *       也让报错指向真正的原因（而不是一个更容易被误读的 401）；</li>
     *   <li>密钥存在但解不开 ⇒ 单独一句「无法解密」，不要含糊成「未配置」（M-2 的同一个道理）；</li>
     *   <li>{@code enabled=false} 照样允许探测：用户常在启用前先验证端点与密钥，
     *       只读探测不改变任何状态。</li>
     * </ol>
     *
     * <p>端点在这里归一化（不是在校验器里）：归一化同时剥掉 {@code /v1} 等后缀，
     * 客户端才能安全地接 {@code /v1/models}，不会拼出 {@code /v1/v1/models}。
     */
    public List<String> discoverModels(Long providerId) {
        LlmProvider provider = required(providerId);
        String ciphertext = provider.getApiKeyEncrypted();
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new BusinessException("该供应商未配置 API Key，请先配置 API Key 后再获取可用模型");
        }
        String apiKey = decryptOrNull(cryptoService, ciphertext, "供应商 id=" + providerId);
        if (apiKey == null) {
            throw new BusinessException("该供应商的 API Key 无法解密（请检查 APP_SECRET_KEY），请重新填写 API Key");
        }
        String baseUrl = LlmProfileService.normalizeBaseUrl(provider.getBaseUrl());
        return modelDiscovery.discover(providerId, provider.getProvider(), baseUrl, apiKey);
    }

    @Transactional
    public ProviderView create(ProviderRequest request, Long userId) {
        LlmProvider provider = new LlmProvider();
        apply(provider, request);
        validateNameUnique(provider.getName(), null);
        provider.setCreatedBy(userId);
        provider.setCreatedAt(LocalDateTime.now());
        provider.setUpdatedAt(LocalDateTime.now());
        mapper.insert(provider);
        return view(required(provider.getId()));
    }

    @Transactional
    public ProviderView update(Long id, ProviderRequest request) {
        LlmProvider provider = required(id);
        apply(provider, request);
        validateNameUnique(provider.getName(), id);
        provider.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(provider);
        return view(required(id));
    }

    /**
     * 删除供应商：其下仍有模型档案时**拒绝**，不做级联删除。
     *
     * <p>为什么拒绝而不是连带删档案：档案是智能体绑定的对象（{@code AgentDefinition.llm_profile_id}），
     * 级联删除会**静默带走智能体的绑定**——用户点一次「删除供应商」，几个智能体就悄悄回落默认档案了，
     * 而成功响应不会提示任何事。拒绝的代价只是用户多点两下（消息里带上剩余模型数，他知道该处理几条）。
     *
     * <p>档案若被删到悬空（provider_id 指向本行已不在），运行时是「不可用」而不是「用错端点」，
     * 见 {@link LlmProfileService#runtime}。
     */
    @Transactional
    public void delete(Long id) {
        LlmProvider provider = required(id);
        int modelCount = usedModelCount(id);
        if (modelCount > 0) {
            throw new BusinessException("供应商「" + provider.getName() + "」下还有 " + modelCount
                    + " 个模型档案，请先删除它们或改挂到其它供应商");
        }
        mapper.deleteById(id);
    }

    /** 挂靠在该供应商下的档案数（{@code llm_profile.provider_id = id}）。 */
    public int usedModelCount(Long providerId) {
        if (providerId == null) return 0;
        return (int) profileMapper.count(Where.where(LlmProfile::getProviderId).eq(providerId));
    }

    /**
     * 供应商运行时视图（含**解密后**的 apiKey）：调用方（{@link LlmProfileService#runtime}）据此
     * 决定档案可用性并拼模型。
     *
     * <p>baseUrl 在此归一化（与档案口径一致）：CRUD 写入时已经归一化过，这里是**读时**的第二道，
     * 覆盖回填/手工改库/历史行残留 {@code /v1} 后缀的情况；null 时保持 null 不抛
     * ——运行时的读取路径不允许因为一条脏行就让整条故障切换链抛异常（失败切换的全部意义是「总还有一条能用」）。
     */
    public RuntimeProvider runtime(LlmProvider provider) {
        if (provider == null) return null;
        String apiKey = decryptOrNull(cryptoService, provider.getApiKeyEncrypted(),
                "供应商 id=" + provider.getId());
        String baseUrl = provider.getBaseUrl() == null || provider.getBaseUrl().isBlank()
                ? null : LlmProfileService.normalizeBaseUrl(provider.getBaseUrl());
        return new RuntimeProvider(Boolean.TRUE.equals(provider.getEnabled()), provider.getProvider(),
                baseUrl, apiKey);
    }

    /** 按 id 取运行时视图；供应商不存在（被删/悬空）返回 null，由调用方转成「不可用」。 */
    public RuntimeProvider runtimeById(Long providerId) {
        return providerId == null ? null : runtime(mapper.findById(providerId));
    }

    /**
     * 按三元组找已有供应商：{@code (provider, 归一化 baseUrl, 解密后的明文 key)}。
     *
     * <p>逐行解密比较的原因见类注释（密文相等 ≠ 同一 key）。key 为空的供应商按「无 key」归一类：
     * {@code null} 与 {@code null} 相等，与「有 key」不等。
     *
     * @param apiKeyPlain 明文 key，可为 null（表示该组「没有 key」）
     */
    LlmProvider findByTriple(String provider, String baseUrl, String apiKeyPlain) {
        for (LlmProvider candidate : mapper.findAll()) {
            if (!Objects.equals(candidate.getProvider(), provider)) continue;
            if (!Objects.equals(candidate.getBaseUrl(), baseUrl)) continue;
            String candidateKey;
            try {
                candidateKey = plaintextKeyOf(candidate);
            } catch (RuntimeException exception) {
                // 密钥解不开的行**无法证明**是同一个通道，跳过它。
                // 不能把它当成「无 key」去匹配：那会把一批无 key 的档案挂到一个坏 key 的供应商上
                log.warn("供应商「{}」(id={}) 的密钥无法解密，跳过三元组匹配",
                        candidate.getName(), candidate.getId());
                continue;
            }
            if (!Objects.equals(candidateKey, apiKeyPlain)) continue;
            return candidate;
        }
        return null;
    }

    /**
     * 设置页写透（{@code PUT /api/settings/llm}）与启动迁移用的 upsert：返回「默认供应商」那一行的 id。
     *
     * <p>顺序（契约 §5.3）：①三元组精确命中 ⇒ 复用该行；②未命中 ⇒ 复用**名为「默认供应商」**的那一行
     * 并把三元组写上去；③连它也不存在 ⇒ 新建一行。
     *
     * <p>为什么②允许**改写**已有行的三元组，而回填用的 {@link #attachOrCreateProvider} 不允许：
     * 「默认供应商」是设置页这条快捷入口的载体（契约 §8），设置页保存的值就是这个三元组，
     * 改写它等于「设置页写入生效」；若改成另建一行，设置页每保存一次就多一个供应商。
     * 回填面对的是用户自建的档案，改写别人的行会**静默改变别的档案的端点与密钥**，性质完全不同。
     *
     * <p>为什么启用位跟随设置页的开关、且没有 key 一律落成停用：启用的供应商必须有 key 是 CRUD 的
     * 不变量（契约 §5.1 规则 4），写透这条路径同样不能造出「启用却没 key」的行
     * （设置页本身允许「未启用 + 无 key」这种存量形态）。
     *
     * <p>为什么新建的行 {@code createdBy} 留空：本方法同时被启动迁移器调用，那时没有登录上下文，
     * 而 {@code CurrentUserService.required()} 在无认证时抛 401——迁移器里抛异常会被它自己的
     * try/catch 吞掉，表现为「迁移静默不发生」，比一行审计字段留空糟糕得多。
     *
     * @param apiKeyEncrypted 已加密的 key（与 {@code llm_config.api_key_encrypted} 同源），可为 null
     * @param enabled         设置页的启用开关，可为 null（null 视为未启用）
     */
    @Transactional
    public Long resolveDefaultProviderId(String provider, String baseUrl, String apiKeyEncrypted, Boolean enabled) {
        return resolveDefaultProviderId(provider, baseUrl, apiKeyEncrypted, enabled, true);
    }

    /**
     * 存量回填专用：启用位只看「有没有 key」，**不看 {@code llm_config.enabled}**（缺陷 B1）。
     *
     * <p>为什么回填不能跟随设置页的开关：{@code llm_config.enabled=false} 表达的是「整站关掉了 AI」，
     * 它不该决定一个供应商行自身的启用位。第一版把 {@code config.getEnabled()} 传了进来，
     * 结果是「AI 关着的库」里回填出来的供应商全是停用——那些档案在故障切换链上**整组静默消失**：
     * 界面上档案都在、模型名也对，但每个请求都失败，且没有任何错误指向供应商。
     * 回填的输入是用户自建的档案，判断依据只能是「档案自己有没有 key」。
     *
     * <p>与设置页写透共用同一段实现体：两处对「默认供应商」的认定必须逐字一致，
     * 否则设置页显示的端点与供应商页指向的行会漂移。
     */
    @Transactional
    public Long resolveDefaultProviderIdForBackfill(String provider, String baseUrl, String apiKeyEncrypted) {
        return resolveDefaultProviderId(provider, baseUrl, apiKeyEncrypted, null, false);
    }

    /**
     * 两条调用方的共用实现体。
     *
     * @param enabled              设置页的启用开关（仅 {@code enabledFollowsSetting=true} 时参与判断）
     * @param enabledFollowsSetting {@code true} = 设置页写透口径；{@code false} = 回填口径（有 key 即启用）
     */
    private Long resolveDefaultProviderId(String provider, String baseUrl, String apiKeyEncrypted,
                                          Boolean enabled, boolean enabledFollowsSetting) {
        String apiKeyPlain = apiKeyEncrypted == null ? null : cryptoService.decrypt(apiKeyEncrypted);
        LlmProvider matched = findByTriple(provider, baseUrl, apiKeyPlain);
        if (matched != null) return matched.getId();

        boolean effectiveEnabled = enabledFollowsSetting
                ? Boolean.TRUE.equals(enabled) && apiKeyEncrypted != null
                : apiKeyEncrypted != null;
        LlmProvider target = mapper.findByName(DEFAULT_PROVIDER_NAME);
        if (target != null && referencedByNonDefaultProfile(target.getId())) {
            // 缺陷 B2：这一行已经被别的（非默认）档案当成普通供应商在用。
            // 原地改写会**静默换掉**那些档案的端点与密钥——请求打向另一个网关，而且不报错。
            // 正确动作是另建一行承载设置页的值，把「默认供应商」这一行留给用户。
            target = null;
        }
        if (target == null) {
            target = new LlmProvider();
            target.setName(uniqueName(DEFAULT_PROVIDER_NAME));
            target.setCreatedAt(LocalDateTime.now());
            target.setProvider(provider);
            target.setBaseUrl(baseUrl);
            target.setApiKeyEncrypted(apiKeyEncrypted);
            target.setEnabled(effectiveEnabled);
            target.setUpdatedAt(LocalDateTime.now());
            mapper.insert(target);
            log.info("设置页写透：已新建供应商「{}」（协议 {}，端点 {}，{}）", target.getName(), provider, baseUrl,
                    apiKeyEncrypted == null ? "无密钥" : "含密钥");
            return target.getId();
        }
        target.setProvider(provider);
        target.setBaseUrl(baseUrl);
        target.setApiKeyEncrypted(apiKeyEncrypted);
        target.setEnabled(effectiveEnabled);
        target.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(target);
        return target.getId();
    }

    /**
     * 该供应商是否被**非默认**档案引用（缺陷 B2 的改写护栏）。
     *
     * <p>{@code is_default} 为 {@code NULL} 的存量脏行按「非默认」处理：它们确实在引用这一行
     * （{@code NULL} 不等于 {@code TRUE}，语义上就是「不是默认」）。这条谓词不写成 SQL，
     * 是因为 {@code IS_DEFAULT <> 1} 遇到 {@code NULL} 求值为 {@code NULL}，
     * 那些行会被静默漏掉——正好是本护栏最该拦住的一类。
     */
    private boolean referencedByNonDefaultProfile(Long providerId) {
        if (providerId == null) return false;
        return profileMapper.select(Where.where(LlmProfile::getProviderId).eq(providerId)).stream()
                .anyMatch(profile -> !Boolean.TRUE.equals(profile.getIsDefault()));
    }

    /**
     * 存量回填用：三元组命中则复用该行；否则按 {@code preferredName}（必要时追加 {@code  (2)}、{@code  (3)}…）
     * **新建**一行。
     *
     * <p>与 {@link #resolveDefaultProviderId} 的区别是「绝不改写别人的三元组」：回填的输入是用户自建的
     * 档案，撞名只是名字撞了，改掉对方的端点与密钥却是**静默改变另一个通道**。因此这里遇到重名就换名。
     *
     * <p>新建行的启用位 = 有没有 key：没有 key 的供应商启用与否都不可用（{@code available()} 要求 key），
     * 落成停用更诚实，也守住了「启用必须有 key」这条不变量（CRUD 拒绝的形态不该由回填造出来）。
     */
    @Transactional
    public Long attachOrCreateProvider(String provider, String baseUrl, String apiKeyEncrypted, String preferredName) {
        String apiKeyPlain = apiKeyEncrypted == null ? null : cryptoService.decrypt(apiKeyEncrypted);
        LlmProvider matched = findByTriple(provider, baseUrl, apiKeyPlain);
        if (matched != null) return matched.getId();

        LlmProvider created = new LlmProvider();
        created.setName(uniqueName(preferredName));
        created.setProvider(provider);
        created.setBaseUrl(baseUrl);
        created.setApiKeyEncrypted(apiKeyEncrypted);
        created.setEnabled(apiKeyEncrypted != null);
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        mapper.insert(created);
        log.info("供应商回填：已创建供应商「{}」（协议 {}，端点 {}，{}）",
                created.getName(), provider, baseUrl, apiKeyEncrypted == null ? "无密钥" : "含密钥");
        return created.getId();
    }

    /**
     * baseUrl 的 host 作为供应商名（契约 §5.4 规则 3）：{@code https://nexus.bx9y.com.cn} → {@code nexus.bx9y.com.cn}。
     *
     * <p>为什么用 host 而不是原文或「供应商1/2/3」：host 是用户认得出来的信息（他填端点时看的就是它），
     * 而序号完全不携带信息。解析失败（极少数脏 baseUrl）时退回一个中性名，由去重逻辑补序号，
     * 不让命名逻辑抛异常——回填过程宁可用一个难看的名字，也不能中断。
     */
    static String nameFromBaseUrl(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            if (host != null && !host.isBlank()) return host;
        } catch (RuntimeException ignored) {
            // 解析不了就用中性名，见上
        }
        return FALLBACK_PROVIDER_NAME;
    }

    private String uniqueName(String preferredName) {
        String base = preferredName == null || preferredName.isBlank() ? FALLBACK_PROVIDER_NAME : preferredName.strip();
        if (mapper.findByName(base) == null) return base;
        for (int suffix = 2; suffix <= MAX_NAME_SUFFIX; suffix++) {
            String candidate = base + " (" + suffix + ")";
            if (mapper.findByName(candidate) == null) return candidate;
        }
        throw new BusinessException("供应商名称已用尽：" + base);
    }

    private String plaintextKeyOf(LlmProvider provider) {
        return provider.getApiKeyEncrypted() == null ? null : cryptoService.decrypt(provider.getApiKeyEncrypted());
    }

    private void apply(LlmProvider provider, ProviderRequest request) {
        if (request.name() == null || request.name().isBlank()) throw new BusinessException("请填写供应商名称");
        if (!SUPPORTED_PROVIDERS.contains(request.provider())) {
            throw new BusinessException("不支持的 LLM 服务类型：" + request.provider());
        }
        provider.setName(request.name().strip());
        provider.setProvider(request.provider());
        provider.setBaseUrl(LlmProfileService.normalizeBaseUrl(request.baseUrl()));
        // 复查项 M-1：enabled == null 表示「本次不动这一位」，不是「设为启用」。
        // 新建行没有旧值可保留，落成停用——这是唯一能让「先存端点、再补 key」两步流程走通的写法：
        // 改造前把 null 当 true，无 key 的新建必然被下面的校验打成 400，
        // 于是用户永远建不出一个「等着补 key」的供应商。
        if (request.enabled() != null) {
            provider.setEnabled(request.enabled());
        } else if (provider.getEnabled() == null) {
            provider.setEnabled(false);
        }
        if (Boolean.TRUE.equals(request.clearApiKey())) provider.setApiKeyEncrypted(null);
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            provider.setApiKeyEncrypted(cryptoService.encrypt(request.apiKey().trim()));
        }
        if (Boolean.TRUE.equals(provider.getEnabled()) && provider.getApiKeyEncrypted() == null) {
            throw new BusinessException("启用供应商前必须配置 API Key");
        }
    }

    private void validateNameUnique(String name, Long excludeId) {
        LlmProvider existing = mapper.findByName(name);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("供应商名称已存在：" + name);
        }
    }

    /**
     * 供应商视图。{@code apiKeyMasked} 只由解密后的明文算出，**不回传明文也不回传密文**。
     *
     * <p>{@code baseUrl} 原样输出（不做归一化）：本接口是「我库里存的是什么」的只读视图，
     * 而归一化会在 baseUrl 为 null 时抛异常——列表接口必须能列出坏数据，否则管理员看不到问题、
     * 也就无从修复（与档案视图面对悬空供应商时必须照常返回同一个道理）。
     */
    private ProviderView view(LlmProvider provider) {
        String ciphertext = provider.getApiKeyEncrypted();
        String apiKey = decryptOrNull(cryptoService, ciphertext, "供应商 id=" + provider.getId());
        return new ProviderView(provider.getId(), provider.getName(), provider.getProvider(), provider.getBaseUrl(),
                apiKey != null && !apiKey.isBlank(),
                maskCiphertext(cryptoService, ciphertext, "供应商 id=" + provider.getId()),
                provider.getEnabled(), usedModelCount(provider.getId()), provider.getUpdatedAt());
    }

    /**
     * 读路径的容错解密（复查项 M-2 / 缺陷 B3）：解不开返回 {@code null} 并按「未配置」降级，**绝不向上抛**。
     *
     * <p>为什么读路径必须容错：{@code APP_SECRET_KEY} 一旦轮换或写错，库里所有密文都解不开。
     * 抛异常会让 {@code GET /api/settings/llm} 与 {@code GET /api/llm-profiles} 双双 500
     * ——而这两个页面正是管理员**唯一的修复入口**（Lead 2026-11-10 在坏库上实测：这两个接口 500，
     * 只有空表的 {@code /api/llm-providers} 是 200）。降级之后管理员能进去看到
     * 「无法解密（请检查 APP_SECRET_KEY）」并当场改好。
     *
     * <p><b>写路径不用它</b>：保存时必须让用户知道密钥解不开，否则一次「什么都没改」的保存
     * 会用坏 key 静默覆盖一个好 key。
     *
     * @param subject 仅用于日志定位（实体类型 + id），**不得包含密文或明文**
     */
    public static String decryptOrNull(CryptoService cryptoService, String ciphertext, String subject) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        try {
            return cryptoService.decrypt(ciphertext);
        } catch (RuntimeException exception) {
            log.warn("{} 的密钥无法解密（APP_SECRET_KEY 可能已轮换或写错），本次按「未配置」处理", subject);
            return null;
        }
    }

    /**
     * 「密文 → 给用户看的掩码」的唯一入口：区分**没配过** / **配了但解不开** / **正常**三种状态。
     *
     * <p>为什么必须区分前两者：{@code APP_SECRET_KEY} 轮换后密文还在、但解不开。若统一显示
     * 「未配置」，管理员会以为「我没填过 key」，而真正要做的是把 key 重填或把 APP_SECRET_KEY 改回去
     * ——两个方向完全不同。
     *
     * <p>抽成一个方法而不是各页各写一段 if：本轮之前供应商页与设置页的掩码规则就已经分叉过一次
     * （8 个点 + 末 4 位 vs 恒 12 个点），同一个 key 在两页显示不同长度本身就是缺陷。
     */
    public static String maskCiphertext(CryptoService cryptoService, String ciphertext, String subject) {
        if (ciphertext == null || ciphertext.isBlank()) return maskApiKey(null);
        String apiKey = decryptOrNull(cryptoService, ciphertext, subject);
        return apiKey == null || apiKey.isBlank() ? UNDECRYPTABLE_MASK : maskApiKey(apiKey);
    }

    /** {@code toString()} 用的密钥占位（复查项 M-4）：非空一律 {@code ***}，连长度都不透露。 */
    public static String redactSecret(String secret) {
        return secret == null ? "null" : "***";
    }

    /**
     * API Key 掩码，规则与档案页逐字一致：未配置 → {@code 未配置}；长度 ≥12 → {@code ••••••••} + 末 4 位；
     * 否则**全掩码**（短 key 的末 4 位会泄露大部分内容）。
     *
     * <p>抽成静态方法供 {@link LlmProfileService} 与 {@code LlmConfigService} 复用：三处各写一份必然漂移
     * （改造前正是如此：供应商页 8 个点 + 末 4 位、设置页恒 12 个点），而「掩码写错」的后果是泄密。
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return "未配置";
        return apiKey.length() >= 12 ? "••••••••" + apiKey.substring(apiKey.length() - 4) : "••••••••••••";
    }

    public record ProviderRequest(@NotBlank String name, @NotBlank String provider, @NotBlank String baseUrl,
                                  String apiKey, Boolean clearApiKey, Boolean enabled) {
        /** 手写 {@code toString()}（复查项 M-4）：record 默认版本会打印明文 {@code apiKey}。 */
        @Override
        public String toString() {
            return "ProviderRequest[name=" + name + ", provider=" + provider + ", baseUrl=" + baseUrl
                    + ", apiKey=" + LlmProviderService.redactSecret(apiKey)
                    + ", clearApiKey=" + clearApiKey + ", enabled=" + enabled + "]";
        }
    }

    public record ProviderView(Long id, String name, String provider, String baseUrl, boolean hasApiKey,
                               String apiKeyMasked, Boolean enabled, int modelCount, LocalDateTime updatedAt) {
    }

    /**
     * 供应商运行时视图（契约 §5.1）。
     *
     * <p>{@code available()} 只判「启用 + 有 key」：供应商的 provider/baseUrl 在 CRUD 层是 {@code @NotBlank}，
     * 这里不再重复判空（真正的兜底在 {@link LlmProfileService.RuntimeProfile#available()}，
     * 它要求 provider/baseUrl 同样非空）。
     */
    public record RuntimeProvider(boolean enabled, String provider, String baseUrl, String apiKey) {
        public boolean available() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }

        /** 手写 {@code toString()}（复查项 M-4）：record 默认版本会把明文 {@code apiKey} 打进日志。 */
        @Override
        public String toString() {
            return "RuntimeProvider[enabled=" + enabled + ", provider=" + provider + ", baseUrl=" + baseUrl
                    + ", apiKey=" + LlmProviderService.redactSecret(apiKey) + "]";
        }
    }
}
