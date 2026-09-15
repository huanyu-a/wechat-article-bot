package ink.icoding.wechat.article.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模型档案故障切换链单测（Phase 1）：顺序、去重、跳过不可用者。
 *
 * <p>为什么这条链的顺序值得单测：它是「主用模型挂了之后谁来接手」的全部逻辑。
 * 顺序错了不会报错，只会让整轮运行去用一个更差/更慢的档案，或者在还有可用档案时直接失败——
 * 属于典型的「静默劣化」，只有断言才能钉住。
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
