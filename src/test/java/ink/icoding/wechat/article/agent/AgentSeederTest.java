package ink.icoding.wechat.article.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能体种子的模型绑定策略单测（Phase 2）。
 *
 * <p>核心策略：{@code llm_profile_id} **仅在为空时**写入默认绑定——用户给某个智能体换过模型，
 * 种子不该在下次启动时把它改回去。这与现有 skill_ids 的策略一致（种子只管「补齐」，
 * 不做「覆盖」），但绑定错了不会报错、只会静默用错模型，因此必须有断言钉住。
 */
class AgentSeederTest {

    /**
     * 已有绑定时**不覆盖**：用户的选择优先。
     *
     * <p>这是本类最重要的一条——没有它，用户在 UI 上换的模型会在下次重启时被悄悄改回默认映射。
     */
    @Test
    void existingProfileBindingIsNeverOverwritten() {
        AgentDefinition existing = new AgentDefinition();
        existing.setId(1L);
        existing.setCode("builtin_writer");
        existing.setBuiltinKey("builtin_writer");
        existing.setName("撰稿人");
        existing.setStage("WRITING");
        existing.setEnabled(true);
        existing.setToolKeys(AgentDefinitionService.toJson(List.of(ToolRegistry.DRAFT_READ)));
        existing.setSkillIds("[1]");
        existing.setLlmProfileId(99L); // 用户手工换过的模型

        AgentDefinitionMapper mapper = mock(AgentDefinitionMapper.class);
        when(mapper.findByBuiltinKey("builtin_writer")).thenReturn(existing);
        LlmProfileMapper profileMapper = mock(LlmProfileMapper.class);
        when(profileMapper.findByName(any())).thenReturn(null);
        AgentSeeder seeder = new AgentSeeder(mapper, mock(ink.icoding.wechat.article.skill.SkillMapper.class),
                profileMapper);

        seeder.run(null);

        assertThat(existing.getLlmProfileId()).as("用户换过的模型不该被种子改回").isEqualTo(99L);
    }

    /** 未绑定时补上默认映射（内置档案存在的前提下）。 */
    @Test
    void emptyProfileBindingIsFilledWithTheSeedDefault() {
        AgentDefinition existing = new AgentDefinition();
        existing.setId(2L);
        existing.setCode("builtin_researcher");
        existing.setBuiltinKey("builtin_researcher");
        existing.setName("调研员");
        existing.setStage("RESEARCH");
        existing.setEnabled(true);
        existing.setToolKeys(AgentDefinitionService.toJson(List.of(ToolRegistry.RESEARCH)));
        existing.setSkillIds("[1]");
        existing.setLlmProfileId(null);

        AgentDefinitionMapper mapper = mock(AgentDefinitionMapper.class);
        when(mapper.findByBuiltinKey("builtin_researcher")).thenReturn(existing);
        LlmProfileMapper profileMapper = mock(LlmProfileMapper.class);
        LlmProfile deepseek = new LlmProfile();
        deepseek.setId(11L);
        deepseek.setName("deepseek-flash");
        deepseek.setEnabled(true);
        when(profileMapper.findByName("deepseek-flash")).thenReturn(deepseek);
        AgentSeeder seeder = new AgentSeeder(mapper, mock(ink.icoding.wechat.article.skill.SkillMapper.class),
                profileMapper);

        seeder.run(null);

        assertThat(existing.getLlmProfileId()).isEqualTo(11L);
    }

    /**
     * 档案不存在（{@link LlmProfileSeeder} 未跑或用户删了）时绑定留空，该智能体回落默认档案。
     *
     * <p>留空是**正确**的降级：写一个指向不存在档案的 id 会让 {@code AgentFactory} 每次都走
     * 「绑定档案已删除」的告警分支。
     */
    @Test
    void missingProfileLeavesTheBindingEmpty() {
        AgentDefinition existing = new AgentDefinition();
        existing.setId(3L);
        existing.setCode("builtin_writer");
        existing.setBuiltinKey("builtin_writer");
        existing.setName("撰稿人");
        existing.setStage("WRITING");
        existing.setEnabled(true);
        existing.setToolKeys(AgentDefinitionService.toJson(List.of(ToolRegistry.DRAFT_READ)));
        existing.setSkillIds("[1]");
        existing.setLlmProfileId(null);

        AgentDefinitionMapper mapper = mock(AgentDefinitionMapper.class);
        when(mapper.findByBuiltinKey("builtin_writer")).thenReturn(existing);
        LlmProfileMapper profileMapper = mock(LlmProfileMapper.class);
        when(profileMapper.findByName(any())).thenReturn(null);
        AgentSeeder seeder = new AgentSeeder(mapper, mock(ink.icoding.wechat.article.skill.SkillMapper.class),
                profileMapper);

        seeder.run(null);

        assertThat(existing.getLlmProfileId()).isNull();
    }

    /** 已停用的档案不作为绑定目标（切过去只会立刻失败一次）。 */
    @Test
    void disabledProfileIsNotBound() {
        AgentDefinition existing = new AgentDefinition();
        existing.setId(4L);
        existing.setCode("builtin_chief");
        existing.setBuiltinKey("builtin_chief");
        existing.setName("主编（协调者）");
        existing.setStage("COORDINATE");
        existing.setEnabled(true);
        existing.setToolKeys(AgentDefinitionService.toJson(List.of(ToolRegistry.DELEGATE)));
        existing.setSkillIds("[1]");
        existing.setLlmProfileId(null);

        AgentDefinitionMapper mapper = mock(AgentDefinitionMapper.class);
        when(mapper.findByBuiltinKey("builtin_chief")).thenReturn(existing);
        LlmProfileMapper profileMapper = mock(LlmProfileMapper.class);
        LlmProfile disabled = new LlmProfile();
        disabled.setId(12L);
        disabled.setName("glm-5.3-flash");
        disabled.setEnabled(false);
        when(profileMapper.findByName("glm-5.3-flash")).thenReturn(disabled);
        AgentSeeder seeder = new AgentSeeder(mapper, mock(ink.icoding.wechat.article.skill.SkillMapper.class),
                profileMapper);

        seeder.run(null);

        assertThat(existing.getLlmProfileId()).isNull();
    }

    /**
     * 内置映射覆盖全部 7 个智能体，且只使用 Phase 0 实测可用的档案名。
     *
     * <p>实测不可用的模型（glm-5.3 返回 503、dots3-note-prev、sensenova-6.8-flash-lite、
     * step-router-v1、glm-4.7）不得出现在种子里：绑上去只会在运行时白耗一次切换。
     */
    @Test
    void everyBuiltinAgentHasAUsableDefaultProfile() {
        List<AgentSeeder.Seed> seeds = AgentSeeder.seeds();

        assertThat(seeds).hasSize(7);
        assertThat(seeds).allSatisfy(seed ->
                assertThat(seed.profileSeedName()).as("%s 应有默认绑定", seed.code()).isNotBlank());
        assertThat(seeds).extracting(AgentSeeder.Seed::profileSeedName)
                .containsOnly("deepseek-flash", "glm-5.3-flash")
                .doesNotContain("glm-5.3", "glm-4.7", "dots3-note-prev", "sensenova-6.8-flash-lite",
                        "step-router-v1");
    }

    /** 速度优先的智能体（工具密集、多轮检索）绑 deepseek-flash；能力优先的绑 glm-5.3-flash。 */
    @Test
    void toolHeavyAgentsGetTheFastProfileAndJudgementAgentsTheCapableOne() {
        List<AgentSeeder.Seed> seeds = AgentSeeder.seeds();

        assertThat(seeds).filteredOn(seed -> List.of("builtin_researcher", "builtin_illustrator",
                        "builtin_editor").contains(seed.code()))
                .allSatisfy(seed -> assertThat(seed.profileSeedName())
                        .as("%s 以速度优先", seed.code()).isEqualTo("deepseek-flash"));
        assertThat(seeds).filteredOn(seed -> List.of("builtin_writer", "builtin_reviewer",
                        "builtin_chief", "builtin_scheduled_creator").contains(seed.code()))
                .allSatisfy(seed -> assertThat(seed.profileSeedName())
                        .as("%s 以能力优先", seed.code()).isEqualTo("glm-5.3-flash"));
    }

    /** 档案种子按名称幂等：已存在一律不动，保留用户对 key/启用状态的修改。 */
    @Test
    void profileSeederCreatesEachStandardProfileOnlyOnce() {
        LlmProfile source = new LlmProfile();
        source.setId(1L);
        source.setName("默认配置");
        source.setProvider("OPENAI_COMPATIBLE");
        source.setBaseUrl("https://nexus.bx9y.com.cn");
        source.setModelName("hy4-preview");
        source.setApiKeyEncrypted("encrypted");
        source.setEnabled(true);

        LlmProfileMapper mapper = mock(LlmProfileMapper.class);
        when(mapper.findDefault()).thenReturn(source);
        when(mapper.findAll()).thenReturn(List.of(source));
        // 两个标准档案都已存在 → 一个都不该新建
        when(mapper.findByName(any())).thenAnswer(invocation -> {
            LlmProfile existing = new LlmProfile();
            existing.setId(7L);
            existing.setName(invocation.getArgument(0));
            return existing;
        });
        when(mapper.findFallback()).thenReturn(source);

        new LlmProfileSeeder(mapper).run(null);

        verify(mapper, never()).insert(any(LlmProfile.class));
    }

    /** 标准档案不存在时创建，并复制默认档案的 baseUrl/key（同一网关，只有模型名不同）。 */
    @Test
    void profileSeederCopiesGatewayCredentialsFromTheDefaultProfile() {
        LlmProfile source = new LlmProfile();
        source.setId(1L);
        source.setName("默认配置");
        source.setProvider("OPENAI_COMPATIBLE");
        source.setBaseUrl("https://nexus.bx9y.com.cn");
        source.setModelName("hy4-preview");
        source.setApiKeyEncrypted("encrypted-key");
        source.setEnabled(true);

        LlmProfileMapper mapper = mock(LlmProfileMapper.class);
        when(mapper.findDefault()).thenReturn(source);
        when(mapper.findAll()).thenReturn(List.of(source));
        when(mapper.findByName(any())).thenReturn(null);
        when(mapper.findFallback()).thenReturn(source);

        new LlmProfileSeeder(mapper).run(null);

        // 两个标准档案各插一次
        verify(mapper, times(2)).insert(any(LlmProfile.class));
        // 兜底档案已存在（默认档案被标为兜底），不再重复标记
        verify(mapper, never()).updateById(any(LlmProfile.class));
    }
}
