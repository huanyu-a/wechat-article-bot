package ink.icoding.wechat.article.agent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型档案单测：故障切换链（Phase 1）的顺序/去重/跳过不可用者，
 * 以及存量 {@code /api/settings/llm} 写透的目标选择（{@link LlmProfileService#syncDefaultFromConfig}）。
 *
 * <p>为什么这条链的顺序值得单测：它是「主用模型挂了之后谁来接手」的全部逻辑。
 * 顺序错了不会报错，只会让整轮运行去用一个更差/更慢的档案，或者在还有可用档案时直接失败——
 * 属于典型的「静默劣化」，只有断言才能钉住。
 *
 * <p>为什么写透目标也值得单测：它一旦取错档案，就会**静默改写用户自建的档案**
 * （provider/baseUrl/modelName/apiKey 全被覆盖），且不留任何 is_default 标记——
 * 两者都不会让请求失败，只会让用户下次打开设置页时发现资产被动过。
 */
class LlmProfileServiceTest {

    /** 只覆盖 failoverChain：其余依赖（加密、当前用户）与这条链无关。 */
    private static LlmProfileService service(LlmProfileMapper mapper) {
        return new LlmProfileService(mapper, mock(ink.icoding.wechat.article.common.CryptoService.class),
                mock(ink.icoding.wechat.article.auth.CurrentUserService.class));
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

    /**
     * 图片模型来源：绑定档案声明了就它胜出，即便默认档案也声明了。
     *
     * <p>这是「配图模型可以按智能体分开配」的全部意义——配图师挂一个便宜快的图片模型，
     * 编辑器挂一个质量高的，两者不该互相覆盖。
     */
    @Test
    void imageCarrierPrefersTheBoundProfile() {
        LlmProfile bound = profile(1L, "绑定档", true, false);
        bound.setImageModelName("bound-image");
        LlmProfile def = profile(2L, "默认档", true, false);
        def.setImageModelName("default-image");
        LlmProfileMapper mapper = mapper(List.of(bound, def), def, null);

        assertThat(service(mapper).imageCarrier(bound).getImageModelName()).isEqualTo("bound-image");
    }

    /**
     * 绑定档案没声明图片模型时，沿链往下找到声明了的那个。
     *
     * <p>这就是「只给一个档案配图片模型，其余智能体自动跟随」的用法：用户不必给每个档案
     * 都填一遍图片模型。
     */
    @Test
    void imageCarrierFallsThroughToTheChain() {
        LlmProfile bound = profile(1L, "绑定档", true, false);
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfile fallback = profile(3L, "兜底档", true, true);
        fallback.setImageModelName("fallback-image");
        LlmProfileMapper mapper = mapper(List.of(bound, def, fallback), def, fallback);

        assertThat(service(mapper).imageCarrier(bound).getId()).isEqualTo(3L);
    }

    /**
     * 全链没人声明图片模型时返回 null，由调用方回落全局图片设置。
     *
     * <p>这条路径就是**改造前的存量行为**：所有档案的 image_model_name 都是 NULL（补列时老行
     * 只能是 NULL），配图必须继续走 {@code LLM_CONFIG.IMAGE_MODEL_NAME}。返回 null 而不是
     * 随便挑一个档案，是「存量部署升级后配图行为不变」的唯一保证。
     */
    @Test
    void imageCarrierIsNullWhenNobodyDeclaresOne() {
        LlmProfile bound = profile(1L, "绑定档", true, false);
        LlmProfile def = profile(2L, "默认档", true, false);
        LlmProfileMapper mapper = mapper(List.of(bound, def), def, null);

        assertThat(service(mapper).imageCarrier(bound)).isNull();
    }

    /**
     * 未绑定时从默认档案开始找；绑定的档案已停用/无 Key 时它压根不在链上，
     * 声明的图片模型也随之失效——可用性判断只有一处（{@link LlmProfileService#addIfUsable}）。
     */
    @Test
    void imageCarrierSkipsUnusableProfilesEvenIfTheyDeclareOne() {
        LlmProfile disabled = profile(1L, "已停用档", false, false);
        disabled.setImageModelName("disabled-image");
        LlmProfile def = profile(2L, "默认档", true, false);
        def.setImageModelName("default-image");
        LlmProfileMapper mapper = mapper(List.of(disabled, def), def, null);

        assertThat(service(mapper).imageCarrier(disabled).getImageModelName()).isEqualTo("default-image");
    }

    /** 空白字符串不算「声明了图片模型」：只填了空格应等同于没填。 */
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
        def.setImageModelName("default-image");
        LlmProfileMapper mapper = mapper(List.of(def), def, null);

        assertThat(service(mapper).imageCarrier(null).getId()).isEqualTo(2L);
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

    private static LlmProfile profile(Long id, String name, boolean enabled, boolean fallback) {
        LlmProfile profile = new LlmProfile();
        profile.setId(id);
        profile.setName(name);
        profile.setEnabled(enabled);
        profile.setIsFallback(fallback);
        profile.setIsDefault(false);
        profile.setApiKeyEncrypted("encrypted-key");
        return profile;
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
