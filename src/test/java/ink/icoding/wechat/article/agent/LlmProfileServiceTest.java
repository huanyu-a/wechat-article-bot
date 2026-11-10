package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.common.CryptoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型档案单测：故障切换链（Phase 1）的顺序/去重/跳过不可用者，
 * 存量 {@code /api/settings/llm} 写透的目标选择（{@link LlmProfileService#syncDefaultFromConfig}），
 * 以及本轮（llm-provider-refactor）「供应商优先、provider_id 为空时回退档案自身旧列」的运行时口径。
 *
 * <p>为什么这条链的顺序值得单测：它是「主用模型挂了之后谁来接手」的全部逻辑。
 * 顺序错了不会报错，只会让整轮运行去用一个更差/更慢的档案，或者在还有可用档案时直接失败——
 * 属于典型的「静默劣化」，只有断言才能钉住。
 *
 * <p>为什么写透目标也值得单测：它一旦取错档案，就会**静默改写用户自建的档案**
 * （provider/baseUrl/modelName/apiKey 全被覆盖），且不留任何 is_default 标记——
 * 两者都不会让请求失败，只会让用户下次打开设置页时发现资产被动过。
 *
 * <p>为什么运行时口径也要在这里钉：改造后「档案看起来正常」与「运行时真的可用」是两件事
 * （供应商被停用/被删/没配 key 时档案自身两列毫无异常）。判定体散成两套就会漂移，
 * 漂移的后果是链上选中一个必然失败的档案、白等一轮超时。
 */
class LlmProfileServiceTest {

    /**
     * 只覆盖 failoverChain：其余依赖（加密、当前用户、供应商表）与这条链无关，用空供应商表。
     */
    private static LlmProfileService service(LlmProfileMapper mapper) {
        return service(mapper, emptyProviderMapper());
    }

    private static LlmProfileService service(LlmProfileMapper mapper, LlmProviderMapper providerMapper) {
        CryptoService crypto = crypto();
        return new LlmProfileService(mapper,
                new LlmProviderService(providerMapper, mapper, crypto, new ModelDiscoveryClient()), crypto,
                mock(ink.icoding.wechat.article.auth.CurrentUserService.class));
    }

    /**
     * 「明文 = 密文」的加解密替身。
     *
     * <p>为什么需要它：判定体改成走 {@link LlmProfileService#runtime}（同一口径）之后，链的可用性
     * 判定会真的去解密——而裸 mock 的 {@code decrypt} 返回 null，于是所有档案都会变成「无 key」、
     * 全部用例假红。用恒等替身既保留了「有密文 ⇒ 解出非空明文」这条真实性质，
     * 又不必在断言里掺入真实密钥（真加解密的行为由 {@code LlmProviderServiceTest} 用真实
     * {@code CryptoService} 钉住）。
     */
    private static CryptoService crypto() {
        CryptoService crypto = mock(CryptoService.class);
        when(crypto.decrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(crypto.encrypt(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return crypto;
    }

    private static LlmProviderMapper emptyProviderMapper() {
        LlmProviderMapper mapper = mock(LlmProviderMapper.class);
        when(mapper.findAll()).thenReturn(List.of());
        when(mapper.findByName(any())).thenReturn(null);
        when(mapper.insert(any(LlmProvider.class))).thenAnswer(invocation -> {
            ((LlmProvider) invocation.getArgument(0)).setId(900L);
            return 1;
        });
        return mapper;
    }

    /**
     * 顺序必须是「绑定 → 默认 → 兜底 → 其余已启用」。
     *
     * <p>每一环都是「更差但更可能活着」的退路：绑定是该智能体的最优选择，
     * 默认是没绑定时的事实首选，兜底是最后的安全网。
     */
    @Test
    void chainFollowsBoundThenDefaultThenFallbackThenTheRest() {
        LlmProfile bound = profile(1L, "绑定档", true, false);
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfile fallback = profile(3L, "兜底档", true, true);
        LlmProfile other = profile(4L, "其他档", true, false);
        LlmProfileMapper mapper = mapper(List.of(bound, def, fallback, other), def, fallback);

        List<LlmProfile> chain = service(mapper).failoverChain(bound);

        assertThat(chain).extracting(LlmProfile::getName)
                .containsExactly("绑定档", "默认档", "兜底档", "其他档");
    }

    /**
     * 去重：绑定档案常常就是默认档案（7 个内置智能体当前全部如此），
     * 不去重会让链里出现两个同样的模型，白白浪费一次切换机会。
     */
    @Test
    void chainDeduplicatesByProfileId() {
        LlmProfile same = profile(1L, "同一个档", true, false);
        LlmProfile other = profile(2L, "另一个档", true, false);
        LlmProfileMapper mapper = mapper(List.of(same, other), same, null);

        List<LlmProfile> chain = service(mapper).failoverChain(same);

        assertThat(chain).extracting(LlmProfile::getId).containsExactly(1L, 2L);
    }

    /**
     * 跳过未启用、无 Key 的档案。
     *
     * <p>拿一个没配 Key 的档案当退路等于没退路——切过去只会立刻再失败一次，
     * 还多花一轮时间（SINGLE 的一次失败可能就是一整个超时）。
     */
    @Test
    void chainSkipsDisabledAndKeylessProfiles() {
        LlmProfile bound = profile(1L, "绑定档", true, false);
        LlmProfile disabled = profile(2L, "已停用", false, false);
        LlmProfile keyless = profile(3L, "无Key", true, false);
        keyless.setApiKeyEncrypted("  ");
        LlmProfile usable = profile(4L, "可用", true, false);
        LlmProfileMapper mapper = mapper(List.of(bound, disabled, keyless, usable), bound, null);

        List<LlmProfile> chain = service(mapper).failoverChain(bound);

        assertThat(chain).extracting(LlmProfile::getName).containsExactly("绑定档", "可用");
    }

    /** 未绑定档案时：默认档案成为首选，其后是兜底与其余。 */
    @Test
    void unboundAgentStartsWithTheDefaultProfile() {
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfile fallback = profile(3L, "兜底档", true, true);
        LlmProfileMapper mapper = mapper(List.of(def, fallback), def, fallback);

        List<LlmProfile> chain = service(mapper).failoverChain(null);

        assertThat(chain).extracting(LlmProfile::getName).containsExactly("默认档", "兜底档");
    }

    /** 绑定档案已被删除（调用方传 null）时不得中断：退回默认 → 兜底 → 其余。 */
    @Test
    void deletedBindingFallsBackToTheRestOfTheChain() {
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfile fallback = profile(3L, "兜底档", true, true);
        LlmProfileMapper mapper = mapper(List.of(def, fallback), def, fallback);

        assertThat(service(mapper).failoverChain(null)).hasSize(2);
    }

    /**
     * 全表无可用档案时返回空列表，由调用方回落 {@code llm_config}（存量兼容路径）。
     *
     * <p>这里**不能**抛异常：全新库（档案还没建、llm_config 有数据）是合法状态，
     * 抛异常会让应用起不来。
     */
    @Test
    void chainIsEmptyWhenNothingIsUsable() {
        LlmProfileMapper mapper = mapper(List.of(), null, null);

        assertThat(service(mapper).failoverChain(null)).isEmpty();
    }

    /** 兜底档案未设置时链自然少一环，不影响其余顺序。 */
    @Test
    void missingFallbackSimplyShortensTheChain() {
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfile other = profile(5L, "其他档", true, false);
        LlmProfileMapper mapper = mapper(List.of(def, other), def, null);

        assertThat(service(mapper).failoverChain(def)).extracting(LlmProfile::getName)
                .containsExactly("默认档", "其他档");
    }

    // ---------- §4.3 定稿：图片模型是模型卡片上的复选框 ----------

    /**
     * ① 主路径：勾选了「这是图片模型」的档案就是承载者，图片模型名 = **它自己的 modelName**。
     *
     * <p>这是用户两次否决前两版之后定下的形状：一个网关同时供文本与图片模型，所以在**同一个供应商**
     * 下建两条档案——{@code deepseek-chat}（不勾）与 {@code gpt-image-1}（勾上）。勾上的那条
     * 不必再填什么「图片模型名称」，它的 {@code modelName} 就是图片模型名。
     */
    @Test
    void imageCarrierPicksTheTickedProfileAndItsOwnModelName() {
        LlmProfile bound = profile(1L, "写正文", true, false);
        LlmProfile def = profile(2L, "配图", true, false);
        def.setModelName("gpt-image-1");
        def.setIsImageModel(true);
        LlmProfileMapper mapper = mapper(List.of(bound, def), def, null);

        LlmProfileService service = service(mapper);
        LlmProfile carrier = service.imageCarrier(bound);

        assertThat(carrier.getId()).isEqualTo(2L);
        assertThat(service.imageModelNameOf(carrier))
                .as("勾选档案的图片模型名就是它自己的 modelName，不再另填")
                .isEqualTo("gpt-image-1");
    }

    /** 勾选的档案优先于「只声明了 imageModelName」的老档案（① 先于 ①b）。 */
    @Test
    void tickedProfileWinsOverTheLegacyImageModelName() {
        LlmProfile legacy = profile(1L, "老配图档", true, false);
        legacy.setImageModelName("step-image-edit-2");
        LlmProfile ticked = profile(2L, "新配图档", true, false);
        ticked.setModelName("gpt-image-1");
        ticked.setIsImageModel(true);
        LlmProfileMapper mapper = mapper(List.of(legacy, ticked), ticked, null);

        LlmProfileService service = service(mapper);
        LlmProfile carrier = service.imageCarrier(ticked);

        assertThat(carrier.getId())
                .as("复选框是用户现在的表达方式，应当压过历史残留的文本框值")
                .isEqualTo(2L);
        assertThat(service.imageModelNameOf(carrier)).isEqualTo("gpt-image-1");
    }

    /**
     * ①b 旧数据兼容：全链没人勾选，但有一条老档案声明了 {@code imageModelName} ⇒ 仍用它。
     *
     * <p>为什么这条不能删：第二版设计（卡片上填「图片模型名称」）虽然被用户否决，但**已经这样
     * 配过的部署**必须继续能配图。界面不再暴露那个输入框，读取端保留即可。
     */
    @Test
    void legacyImageModelNameStillCarriesWhenNothingIsTicked() {
        LlmProfile bound = profile(1L, "老配图档", true, false);
        bound.setImageModelName("step-image-edit-2");
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfileMapper mapper = mapper(List.of(bound, def), def, null);

        LlmProfileService service = service(mapper);
        LlmProfile carrier = service.imageCarrier(bound);

        assertThat(carrier.getId()).isEqualTo(1L);
        assertThat(service.imageModelNameOf(carrier))
                .as("①b 用历史字段的值，而不是这条档案的 modelName")
                .isEqualTo("step-image-edit-2");
    }

    /**
     * 勾选但**不可用**（停用）⇒ 跳过它，继续往下找。
     *
     * <p>「勾选但不可用要跳过」不需要额外代码：它压根不在 {@code failoverChain} 里，
     * 因为链上每一环都过了 {@code addIfUsable}。这里钉住的就是这件事。
     */
    @Test
    void tickedButUnusableProfileIsSkipped() {
        LlmProfile disabled = profile(1L, "已停用的配图档", false, false);
        disabled.setModelName("gpt-image-1");
        disabled.setIsImageModel(true);
        LlmProfile def = profile(2L, "默认档", true, false);
        def.setIsImageModel(true);
        LlmProfileMapper mapper = mapper(List.of(disabled, def), def, null);

        LlmProfileService service = service(mapper);
        LlmProfile carrier = service.imageCarrier(disabled);

        assertThat(carrier.getId()).as("停用的勾选档案被链滤掉，轮到默认档").isEqualTo(2L);
        assertThat(service.imageModelNameOf(carrier)).isEqualTo(def.getModelName());
    }

    /**
     * 全链既没勾选、也没有历史 {@code imageModelName} ⇒ 返回 null，由调用方回落全局图片设置。
     *
     * <p>这条路径就是**改造前的存量行为**：所有档案的两列都是空的（补列时老行只能是 NULL），
     * 配图必须继续走 {@code LLM_CONFIG.IMAGE_MODEL_NAME}。返回 null 而不是随便挑一个档案，
     * 是「存量部署升级后配图行为不变」的唯一保证。
     */
    @Test
    void imageCarrierIsNullWhenNobodyDeclaresOne() {
        LlmProfile bound = profile(1L, "绑定档", true, false);
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfileMapper mapper = mapper(List.of(bound, def), def, null);

        assertThat(service(mapper).imageCarrier(bound)).isNull();
    }

    /** 空白字符串不算「声明了历史图片模型名」：只填了空格应等同于没填。 */
    @Test
    void blankImageModelNameCountsAsUndeclared() {
        LlmProfile bound = profile(1L, "绑定档", true, false);
        bound.setImageModelName("   ");
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfileMapper mapper = mapper(List.of(bound, def), def, null);

        assertThat(service(mapper).imageCarrier(bound)).isNull();
    }

    /** 档案被删（调用方传 null）：不中断，从默认档案继续找。 */
    @Test
    void imageCarrierWithNoBindingStartsFromTheDefault() {
        LlmProfile def = profile(2L, "默认档", true, false);
        def.setIsImageModel(true);
        LlmProfileMapper mapper = mapper(List.of(def), def, null);

        assertThat(service(mapper).imageCarrier(null).getId()).isEqualTo(2L);
    }

    /** {@code imageModelNameOf} 对 null 输入返回 null（回落由调用方负责）。 */
    @Test
    void imageModelNameOfIsNullForNoCarrier() {
        assertThat(service(mapper(List.of(), null, null)).imageModelNameOf(null)).isNull();
    }

    /** 勾选档案的 {@code modelName} 为空时拼不出请求 ⇒ {@code imageModelNameOf} 返回 null。 */
    @Test
    void imageModelNameOfIsNullWhenTheTickedProfileHasNoModelName() {
        LlmProfile ticked = profile(1L, "配图档", true, false);
        ticked.setModelName("   ");
        ticked.setIsImageModel(true);

        assertThat(service(mapper(List.of(ticked), ticked, null)).imageModelNameOf(ticked)).isNull();
    }

    /**
     * 存量 {@code PUT /api/settings/llm} 必须写进**真正的默认档案**，不能去改一条用户自建的普通档案。
     *
     * <p>这是本轮查出的缺陷：{@code syncDefaultFromConfig} 用的是 {@code defaultProfile()}，
     * 而它是 {@code findDefault() ?? findFirst()}。于是「表里有档案、但一条都不是默认」时，
     * 它会落到**第一条用户档案**上，把 provider/baseUrl/modelName/apiKey 全部覆盖掉，
     * 且**不给它打 is_default 标记**——用户下次打开档案页会发现自己的档案被动过，
     * 而设置页显示的值又确实来自那条档案（因为读路径同样回落 findFirst），
     * 于是「看起来对、实际改错了对象」。
     *
     * <p>javadoc 写的是「不存在则创建」，所以正确行为是：找不到默认档案时**新建**一条默认档案，
     * 既有档案一律不动。
     */
    @Test
    void legacyWriteThroughCreatesDefaultInsteadOfHijackingTheFirstProfile() {
        LlmProfile userProfile = profile(1L, "用户的档案", true, false);
        userProfile.setModelName("用户原来的模型");
        userProfile.setBaseUrl("https://user.example.com");
        LlmProfileMapper mapper = mapper(List.of(userProfile), null, null);

        service(mapper).syncDefaultFromConfig("OPENAI_COMPATIBLE", "https://from.settings.test",
                "settings-model", "encrypted-settings-key", new BigDecimal("0.30"), 1024, true);

        assertThat(userProfile.getModelName())
                .as("用户自建的普通档案不得被存量设置页写透覆盖").isEqualTo("用户原来的模型");
        assertThat(userProfile.getBaseUrl())
                .as("用户自建的普通档案不得被存量设置页写透覆盖").isEqualTo("https://user.example.com");

        ArgumentCaptor<LlmProfile> inserted = ArgumentCaptor.forClass(LlmProfile.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getIsDefault())
                .as("新建的那条必须带 is_default，否则读路径（defaultProfile）仍会回落 findFirst").isTrue();
        assertThat(inserted.getValue().getModelName()).isEqualTo("settings-model");
        assertThat(inserted.getValue().getBaseUrl()).isEqualTo("https://from.settings.test");
        assertThat(inserted.getValue().getProviderId())
                .as("写透必须把默认档案挂到供应商上（脚手架替身里 id=900）").isEqualTo(900L);
    }

    /**
     * 已有默认档案时，存量设置页照旧写透到它，且**不新建**第二条。
     *
     * <p>这条是上一条的对照：修复不能把正常路径也改成「每次都新建」。
     */
    @Test
    void legacyWriteThroughUpdatesTheExistingDefault() {
        LlmProfile def = profile(2L, "默认配置", true, false);
        def.setIsDefault(true);
        LlmProfile other = profile(5L, "另一个档", true, false);
        LlmProfileMapper mapper = mapper(List.of(def, other), def, null);

        service(mapper).syncDefaultFromConfig("OPENAI_COMPATIBLE", "https://from.settings.test",
                "settings-model", "encrypted-settings-key", new BigDecimal("0.30"), 1024, true);

        assertThat(def.getModelName()).isEqualTo("settings-model");
        assertThat(other.getModelName()).as("非默认档案不得被改动").isNotEqualTo("settings-model");
        verify(mapper, never()).insert(any(LlmProfile.class));
    }

    /**
     * 已存在一条**名为「默认配置」但没有 is_default 标记**的档案时，收养它并补标记，而不是新建重名档案。
     *
     * <p>为什么不能直接新建：{@code findByName} 是种子解析绑定（{@code AgentSeeder.resolveProfileId}）
     * 与名称唯一性校验的入口，重名会让它们产生歧义。
     */
    @Test
    void legacyWriteThroughAdoptsAnExistingSameNamedProfile() {
        LlmProfile stranded = profile(7L, "默认配置", true, false);
        stranded.setIsDefault(false);
        LlmProfileMapper mapper = mapper(List.of(stranded), null, null);
        when(mapper.findByName("默认配置")).thenReturn(stranded);

        service(mapper).syncDefaultFromConfig("OPENAI_COMPATIBLE", "https://from.settings.test",
                "settings-model", "encrypted-settings-key", new BigDecimal("0.30"), 1024, true);

        assertThat(stranded.getIsDefault()).as("被收养的档案必须补上默认标记").isTrue();
        assertThat(stranded.getModelName()).isEqualTo("settings-model");
        verify(mapper, never()).insert(any(LlmProfile.class));
    }

    // ---------- 本轮新增：供应商口径（llm-provider-refactor §4） ----------

    /**
     * 挂靠供应商的档案：端点、密钥、启用位都取自供应商；档案自身的旧列被**忽略**。
     *
     * <p>这里特意让档案的旧列与供应商行**指向不同的网关**：若实现里漏了一处、回退到旧列，
     * 断言里的 baseUrl 就会变成 {@code https://stale.example.com}。这是本改造最核心的一条
     * ——「改一次 key 全部生效」的前提就是运行时不再读那份旧快照。
     */
    @Test
    void runtimeTakesEndpointAndKeyFromTheProvider() {
        LlmProfile attached = profile(1L, "挂供应商的档案", true, false);
        attached.setProviderId(7L);
        attached.setBaseUrl("https://stale.example.com");
        attached.setApiKeyEncrypted("stale-key");
        LlmProviderMapper providerMapper = providerMapper(provider(7L, "nexus", true, "provider-key", "https://nexus.bx9y.com.cn"));

        LlmProfileService.RuntimeProfile runtime = service(mapper(List.of(attached), attached, null), providerMapper)
                .runtime(attached);

        assertThat(runtime.baseUrl()).isEqualTo("https://nexus.bx9y.com.cn");
        assertThat(runtime.apiKey()).isEqualTo("provider-key");
        assertThat(runtime.provider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(runtime.available()).isTrue();
    }

    /**
     * 供应商被停用 ⇒ 其下档案一律不可用（{@code enabled = 档案.enabled && 供应商.enabled}）。
     *
     * <p>这就是契约 §4 说的「供应商被停用 ⇒ 其全部模型在故障切换链上消失」——不需要在链上
     * 再加判断，链自动成立。停用供应商是运维用来「整组下线」的开关，漏掉它等于这个开关失效。
     */
    @Test
    void disabledProviderMakesItsProfilesUnavailable() {
        LlmProfile attached = profile(1L, "挂停用供应商的档案", true, false);
        attached.setProviderId(7L);
        LlmProfileMapper mapper = mapper(List.of(attached), attached, null);
        LlmProviderMapper providerMapper = providerMapper(provider(7L, "nexus", false, "provider-key", "https://nexus.bx9y.com.cn"));

        LlmProfileService service = service(mapper, providerMapper);

        assertThat(service.runtime(attached).available()).isFalse();
        assertThat(service.failoverChain(attached))
                .as("停用供应商之后，它的模型必须不在链上（链不能凭空多出一跳失败）")
                .isEmpty();
    }

    /**
     * 供应商**被删**（{@code providerId} 悬空）：判为不可用，且**不回退档案自身的旧列**。
     *
     * <p>为什么不回退：档案里的旧端点是几个月前的快照，回退等于「静默用错端点」——请求会打到用户
     * 早已不打算用的网关，而且不报错。宁可判它不可用（管理员在档案页会看到「供应商已不存在」），
     * 也不替用户猜一个端点。这里断言 provider/baseUrl 都是 null，正是把「不猜」钉住。
     */
    @Test
    void danglingProviderIdIsUnavailableAndNeverFallsBackToStaleColumns() {
        LlmProfile dangling = profile(1L, "供应商被删的档案", true, false);
        dangling.setProviderId(99L);
        dangling.setBaseUrl("https://stale.example.com");
        dangling.setApiKeyEncrypted("stale-key");

        LlmProfileService.RuntimeProfile runtime = service(mapper(List.of(dangling), dangling, null)).runtime(dangling);

        assertThat(runtime.available()).isFalse();
        assertThat(runtime.provider()).isNull();
        assertThat(runtime.baseUrl()).as("不得回退到档案自身那份过期端点").isNull();
        assertThat(runtime.apiKey()).as("不得回退到档案自身那份过期密钥").isNull();
        assertThat(service(mapper(List.of(dangling), dangling, null)).failoverChain(dangling))
                .as("悬空档案不得留在链上（否则选中它必然失败）").isEmpty();
    }

    /**
     * 存量回退行（{@code providerId} 为空）必须继续可用——这是升级窗口的底线。
     *
     * <p>回填器还没跑、或 baseUrl 为空造不出供应商的行，都走这条路径；它们的行为必须与改造前
     * 逐字一致，否则「升级当天所有智能体不可用」。
     */
    @Test
    void legacyRowsWithoutProviderStayUsableFromTheirOwnColumns() {
        LlmProfile legacy = profile(1L, "存量回退行", true, false);

        LlmProfileService.RuntimeProfile runtime = service(mapper(List.of(legacy), legacy, null)).runtime(legacy);

        assertThat(runtime.available()).isTrue();
        assertThat(runtime.baseUrl()).isEqualTo("https://fixture.invalid");
        assertThat(runtime.apiKey()).isEqualTo("encrypted-key");
    }

    /**
     * **有 key 但端点为空的脏行**：不可用（被链跳过），而且不得抛异常。
     *
     * <p>这是本轮新增的防护（Lead 2026-11-10 收紧 {@code available()}）。原口径只判
     * {@code enabled && apiKey}，于是这样一条行会「可用」——而 {@code AgentFactory} 拿到可用候选后
     * 会直接 {@code createModel(provider, baseUrl, ...)}，那里对空 provider 抛
     * {@code BusinessException("不支持的 LLM 服务类型")}，**整个请求被打挂**。
     * 也就是说：脏行从「扫链时被跳过」变成「选中之后炸掉」，比原实现更危险。
     * 收紧后它被 {@code available()} 挡在链外，这里同时钉住「不抛」。
     */
    @Test
    void legacyRowWithKeyButBlankBaseUrlIsSkippedWithoutThrowing() {
        LlmProfile dirty = profile(1L, "端点空的脏行", true, false);
        dirty.setBaseUrl("   ");
        dirty.setProvider(null);
        LlmProfile usable = profile(2L, "正常档", true, false);
        LlmProfileMapper mapper = mapper(List.of(dirty, usable), dirty, null);
        LlmProfileService service = service(mapper);

        assertThat(service.runtime(dirty).available()).isFalse();
        assertThatCode(() -> service.failoverChain(dirty))
                .as("一条脏行不得让整条链抛异常——失败切换的全部意义是「总还有一条能用」")
                .doesNotThrowAnyException();
        assertThat(service.failoverChain(dirty)).extracting(LlmProfile::getName)
                .as("链应跳过脏行、保留正常档").containsExactly("正常档");
    }

    /**
     * 档案视图：供应商被删（悬空）时给「管理员告警态」，而不是抛异常。
     *
     * <p>列表接口必须能列出坏数据——否则用户看不到问题、也就无从修复。
     * 四个取值（{@code providerName=null} / {@code providerEnabled=false} / {@code hasApiKey=false} /
     * 掩码 {@code 未配置}）共同表达「该档案运行时不可用」，与 {@code runtime().available()==false} 同源。
     */
    @Test
    void viewFlagsADanglingProviderInsteadOfThrowing() {
        LlmProfile dangling = profile(1L, "悬空档案", true, false);
        dangling.setProviderId(99L);
        dangling.setApiKeyEncrypted("stale-key");
        LlmProfileMapper mapper = mapper(List.of(dangling), dangling, null);
        when(mapper.findById(1L)).thenReturn(dangling);

        LlmProfileService.ProfileView view = service(mapper).get(1L);

        assertThat(view.providerId()).isEqualTo(99L);
        assertThat(view.providerName()).isNull();
        assertThat(view.providerEnabled()).isFalse();
        assertThat(view.hasApiKey()).isFalse();
        assertThat(view.apiKeyMasked()).isEqualTo("未配置");
    }

    /**
     * 档案视图：存量回退行必须给 {@code providerEnabled=true}。
     *
     * <p>「没有供应商」不等于「已停用」。若这里填 false，前端档案链（{@code usable()} 里
     * {@code providerEnabled !== false}）会认为所有存量档案都不可用——升级窗口里前端报的模型名
     * 与后端实际用的不一致，正是这条链最忌讳的失效模式。
     */
    @Test
    void viewKeepsLegacyRowsEnabledWithTheirOwnKeyState() {
        LlmProfile legacy = profile(1L, "存量行", true, false);
        LlmProfileMapper mapper = mapper(List.of(legacy), legacy, null);
        when(mapper.findById(1L)).thenReturn(legacy);

        LlmProfileService.ProfileView view = service(mapper).get(1L);

        assertThat(view.providerId()).isNull();
        assertThat(view.providerName()).isNull();
        assertThat(view.providerEnabled()).isTrue();
        assertThat(view.hasApiKey()).isTrue();
    }

    /**
     * 档案视图：{@code hasApiKey}/{@code apiKeyMasked} 取的是**供应商的**密钥，且只给掩码。
     *
     * <p>密钥归供应商之后，档案页显示的掩码必须来自供应商行——否则用户改了供应商的 key，
     * 档案页还显示旧掩码，他会以为没生效。同时断言响应里不出现明文。
     */
    @Test
    void viewReportsTheProviderKeyAsMasked() {
        LlmProfile attached = profile(1L, "挂供应商的档案", true, false);
        attached.setProviderId(7L);
        attached.setApiKeyEncrypted(null);
        LlmProfileMapper mapper = mapper(List.of(attached), attached, null);
        when(mapper.findById(1L)).thenReturn(attached);
        LlmProviderMapper providerMapper = providerMapper(provider(7L, "nexus", true, "sk-abcdefghijklmn", "https://nexus.bx9y.com.cn"));

        LlmProfileService.ProfileView view = service(mapper, providerMapper).get(1L);

        assertThat(view.providerName()).isEqualTo("nexus");
        assertThat(view.providerEnabled()).isTrue();
        assertThat(view.hasApiKey()).isTrue();
        assertThat(view.apiKeyMasked()).isEqualTo("••••••••klmn");
        assertThat(view.apiKeyMasked()).doesNotContain("sk-abcdefghijklmn");
    }

    // ---------- §3.3：视图新增的计算字段 ----------

    /**
     * §3.3：{@code runtimeAvailable} 必须是 {@code RuntimeProfile.available()} 的求值结果。
     *
     * <p>为什么钉这条：前端拿它渲染「可用 / 不可用」徽标。若视图里另写一套判据
     * （{@code enabled && 供应商.enabled && 有 key}），两处迟早漂移——漂移的后果是
     * 列表页显示「可用」、请求却必然失败，而故障切换链其实早就跳过了它。
     *
     * <p>契约 §4.3 修订后这里**只剩 runtimeAvailable 一个**计算字段：{@code providerPurpose}
     * 随供应商级用途一起删了（用户看图后否决：一个网关同时供应文本与图片模型，
     * 用途是模型的属性，不是供应商的）。
     */
    @Test
    void viewExposesRuntimeAvailabilityFromTheSamePredicateAsTheChain() {
        LlmProfile attached = profile(1L, "挂供应商的档案", true, false);
        attached.setProviderId(7L);
        attached.setApiKeyEncrypted(null);
        LlmProfileMapper mapper = mapper(List.of(attached), attached, null);
        when(mapper.findById(1L)).thenReturn(attached);

        LlmProfileService.ProfileView view = service(mapper,
                providerMapper(provider(7L, "nexus", true, "sk-abcdefghijklmn", "https://nexus.bx9y.com.cn"))).get(1L);

        assertThat(view.runtimeAvailable()).isTrue();
        assertThat(view.apiKeyMasked()).as("同一个供应商的 key，档案页与供应商页掩码一致").isEqualTo("••••••••klmn");
    }

    /** 悬空供应商：{@code runtimeAvailable=false}（与 providerName/providerEnabled 同源）。 */
    @Test
    void viewReportsUnavailableForADanglingProvider() {
        LlmProfile dangling = profile(1L, "悬空档案", true, false);
        dangling.setProviderId(99L);
        LlmProfileMapper mapper = mapper(List.of(dangling), dangling, null);
        when(mapper.findById(1L)).thenReturn(dangling);

        LlmProfileService.ProfileView view = service(mapper).get(1L);

        assertThat(view.runtimeAvailable()).isFalse();
        assertThat(view.providerName()).isNull();
    }

    /** M-4：{@code RuntimeProfile} 的 {@code toString()} 不得带出明文密钥。 */
    @Test
    void runtimeProfileToStringHidesTheKey() {
        LlmProfile attached = profile(1L, "档案", true, false);
        attached.setProviderId(7L);
        attached.setApiKeyEncrypted(null);
        String key = "sk-abcdefghijklmn";

        String rendered = service(mapper(List.of(attached), attached, null),
                providerMapper(provider(7L, "nexus", true, key, "https://nexus.bx9y.com.cn")))
                .runtime(attached).toString();

        assertThat(rendered).doesNotContain(key).contains("apiKey=***");
    }

    private static LlmProfile profile(Long id, String name, boolean enabled, boolean fallback) {
        LlmProfile profile = new LlmProfile();
        profile.setId(id);
        profile.setName(name);
        // 端点是「能拼出一次真实请求」的必要条件（available() 要求 provider/baseUrl/apiKey 三者非空），
        // 这里给一组占位值，让「链条本身」的用例只关心顺序，不关心端点
        profile.setProvider("OPENAI_COMPATIBLE");
        profile.setBaseUrl("https://fixture.invalid");
        profile.setEnabled(enabled);
        profile.setIsFallback(fallback);
        profile.setIsDefault(false);
        profile.setApiKeyEncrypted("encrypted-key");
        return profile;
    }

    private static LlmProvider provider(Long id, String name, boolean enabled, String apiKeyEncrypted, String baseUrl) {
        LlmProvider provider = new LlmProvider();
        provider.setId(id);
        provider.setName(name);
        provider.setProvider("OPENAI_COMPATIBLE");
        provider.setBaseUrl(baseUrl);
        provider.setApiKeyEncrypted(apiKeyEncrypted);
        provider.setEnabled(enabled);
        return provider;
    }

    private static LlmProviderMapper providerMapper(LlmProvider... providers) {
        LlmProviderMapper mapper = mock(LlmProviderMapper.class);
        List<LlmProvider> all = List.of(providers);
        when(mapper.findAll()).thenReturn(all);
        when(mapper.findById(any())).thenAnswer(invocation -> all.stream()
                .filter(provider -> provider.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        return mapper;
    }

    private static LlmProfileMapper mapper(List<LlmProfile> all, LlmProfile def, LlmProfile fallback) {
        LlmProfileMapper mapper = mock(LlmProfileMapper.class);
        when(mapper.findEnabled()).thenReturn(all);
        when(mapper.findDefault()).thenReturn(def);
        when(mapper.findFirst()).thenReturn(def == null && all.isEmpty() ? null : all.get(0));
        when(mapper.findFallback()).thenReturn(fallback);
        when(mapper.findById(any())).thenReturn(null);
        return mapper;
    }
}
