package ink.icoding.wechat.article.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时创作智能体「一次性改绑」迁移的单测。
 *
 * <p>这个类要钉住的核心不是「改绑会发生」，而是**它不会误伤用户的选择**：
 * 迁移只在当前绑定恰好等于旧种子默认值（glm-5.3-flash 的 id）时才动手。
 * 一旦判断写宽（例如「只要不是目标档案就改」），用户手工换的模型会在下次重启被悄悄改掉——
 * 而这正是 {@link AgentSeeder} 明确承诺不做的事。
 */
class ScheduledCreatorProfileRebindRunnerTest {

    private static final Long GLM_ID = 5L;
    private static final Long DEEPSEEK_ID = 3L;

    // ---------- 纯函数：改绑判据 ----------

    /** 绑定还是旧种子默认值 → 这是种子写的、用户没动过，改绑。 */
    @Test
    void seedDefaultBindingIsRebound() {
        assertThat(ScheduledCreatorProfileRebindRunner.shouldRebind(GLM_ID, GLM_ID, DEEPSEEK_ID)).isTrue();
    }

    /** **最重要的一条**：用户换成了别的档案 → 绝不覆盖。 */
    @Test
    void userChosenBindingIsNeverOverwritten() {
        assertThat(ScheduledCreatorProfileRebindRunner.shouldRebind(99L, GLM_ID, DEEPSEEK_ID))
                .as("绑定不是旧种子默认值，说明用户改过，不能动").isFalse();
    }

    /** 已经是目标档案（改过了）→ 幂等跳过。 */
    @Test
    void alreadyReboundIsIdempotent() {
        assertThat(ScheduledCreatorProfileRebindRunner.shouldRebind(DEEPSEEK_ID, GLM_ID, DEEPSEEK_ID)).isFalse();
    }

    /** 绑定为空 → 交给 AgentSeeder 按新种子补，迁移不插手。 */
    @Test
    void emptyBindingIsLeftToTheSeeder() {
        assertThat(ScheduledCreatorProfileRebindRunner.shouldRebind(null, GLM_ID, DEEPSEEK_ID)).isFalse();
    }

    /** 旧档案已不存在 → 无法判断「是不是种子写的」，宁可不改。 */
    @Test
    void unknownSupersededProfileDisablesTheRebind() {
        assertThat(ScheduledCreatorProfileRebindRunner.shouldRebind(GLM_ID, null, DEEPSEEK_ID)).isFalse();
    }

    /** 目标档案不可用 → 改了会让绑定悬空。 */
    @Test
    void unavailableTargetProfileDisablesTheRebind() {
        assertThat(ScheduledCreatorProfileRebindRunner.shouldRebind(GLM_ID, GLM_ID, null)).isFalse();
    }

    // ---------- run()：与 mapper 的接线 ----------

    /** 存量库场景：智能体绑着 glm，两个档案都在 → 落库改绑为 deepseek。 */
    @Test
    void staleSeedBindingIsPersistedAsTheTargetProfile() {
        AgentDefinition agent = agent(GLM_ID);
        AgentDefinitionMapper agentMapper = mock(AgentDefinitionMapper.class);
        when(agentMapper.findByBuiltinKey(ScheduledCreatorProfileRebindRunner.AGENT_BUILTIN_KEY)).thenReturn(agent);
        LlmProfileMapper profileMapper = profileMapper(GLM_ID, DEEPSEEK_ID);

        new ScheduledCreatorProfileRebindRunner(agentMapper, profileMapper).run(null);

        assertThat(agent.getLlmProfileId()).isEqualTo(DEEPSEEK_ID);
        verify(agentMapper).updateById(agent);
    }

    /** 用户改过绑定 → run() 既不写库也不改内存对象。 */
    @Test
    void userChosenBindingIsNotPersistedOver() {
        AgentDefinition agent = agent(99L);
        AgentDefinitionMapper agentMapper = mock(AgentDefinitionMapper.class);
        when(agentMapper.findByBuiltinKey(ScheduledCreatorProfileRebindRunner.AGENT_BUILTIN_KEY)).thenReturn(agent);
        LlmProfileMapper profileMapper = profileMapper(GLM_ID, DEEPSEEK_ID);

        new ScheduledCreatorProfileRebindRunner(agentMapper, profileMapper).run(null);

        assertThat(agent.getLlmProfileId()).as("用户换过的模型不该被迁移改回").isEqualTo(99L);
        verify(agentMapper, never()).updateById(any());
    }

    /** 智能体还不存在（全新库、种子未跑）→ 静默跳过，不抛异常、不写库。 */
    @Test
    void missingAgentIsSkippedQuietly() {
        AgentDefinitionMapper agentMapper = mock(AgentDefinitionMapper.class);
        when(agentMapper.findByBuiltinKey(any())).thenReturn(null);
        LlmProfileMapper profileMapper = profileMapper(GLM_ID, DEEPSEEK_ID);

        new ScheduledCreatorProfileRebindRunner(agentMapper, profileMapper).run(null);

        verify(agentMapper, never()).updateById(any());
    }

    /** 旧档案已被禁用 → 视为「不存在」，不改绑（避免误判成种子写的）。 */
    @Test
    void disabledSupersededProfileDisablesTheRebind() {
        AgentDefinition agent = agent(GLM_ID);
        AgentDefinitionMapper agentMapper = mock(AgentDefinitionMapper.class);
        when(agentMapper.findByBuiltinKey(any())).thenReturn(agent);
        LlmProfileMapper profileMapper = mock(LlmProfileMapper.class);
        when(profileMapper.findByName(ScheduledCreatorProfileRebindRunner.SUPERSEDED_PROFILE_NAME))
                .thenReturn(profile(GLM_ID, false));
        when(profileMapper.findByName(ScheduledCreatorProfileRebindRunner.TARGET_PROFILE_NAME))
                .thenReturn(profile(DEEPSEEK_ID, true));

        new ScheduledCreatorProfileRebindRunner(agentMapper, profileMapper).run(null);

        assertThat(agent.getLlmProfileId()).isEqualTo(GLM_ID);
        verify(agentMapper, never()).updateById(any());
    }

    /** mapper 抛异常 → 只吞掉记 WARN，不影响启动（与其它迁移 Runner 同款约定）。 */
    @Test
    void mapperFailureDoesNotPropagate() {
        AgentDefinitionMapper agentMapper = mock(AgentDefinitionMapper.class);
        when(agentMapper.findByBuiltinKey(any())).thenThrow(new IllegalStateException("boom"));

        new ScheduledCreatorProfileRebindRunner(agentMapper, profileMapper(GLM_ID, DEEPSEEK_ID)).run(null);
        // 不抛异常即通过：启动不能被迁移失败拖垮
    }

    private static AgentDefinition agent(Long llmProfileId) {
        AgentDefinition agent = new AgentDefinition();
        agent.setId(2L);
        agent.setCode(ScheduledCreatorProfileRebindRunner.AGENT_BUILTIN_KEY);
        agent.setBuiltinKey(ScheduledCreatorProfileRebindRunner.AGENT_BUILTIN_KEY);
        agent.setName("墨舟定时创作智能体");
        agent.setEnabled(true);
        agent.setLlmProfileId(llmProfileId);
        return agent;
    }

    private static LlmProfile profile(Long id, boolean enabled) {
        LlmProfile profile = new LlmProfile();
        profile.setId(id);
        profile.setEnabled(enabled);
        return profile;
    }

    private static LlmProfileMapper profileMapper(Long glmId, Long deepseekId) {
        LlmProfileMapper mapper = mock(LlmProfileMapper.class);
        LlmProfile glm = profile(glmId, true);
        glm.setName(ScheduledCreatorProfileRebindRunner.SUPERSEDED_PROFILE_NAME);
        LlmProfile deepseek = profile(deepseekId, true);
        deepseek.setName(ScheduledCreatorProfileRebindRunner.TARGET_PROFILE_NAME);
        when(mapper.findByName(ScheduledCreatorProfileRebindRunner.SUPERSEDED_PROFILE_NAME)).thenReturn(glm);
        when(mapper.findByName(ScheduledCreatorProfileRebindRunner.TARGET_PROFILE_NAME)).thenReturn(deepseek);
        return mapper;
    }
}
