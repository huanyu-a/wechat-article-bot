package ink.icoding.wechat.article.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
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
     * 内置映射覆盖全部 7 个智能体，且只使用两个主力档案名。
     *
     * <p>这条断言管的是**智能体绑定策略**，不是「模型可不可用」：7 个内置智能体只按
     * 「速度优先 / 能力优先」两类负载分流，所以绑定集合恰好是 deepseek-flash 与 glm-5.3-flash。
     *
     * <p>修正记录（2026-09-18）：本条注释原文写的是「实测不可用的模型（glm-5.3 返回 503、
     * dots3-note-prev、sensenova-6.8-flash-lite、step-router-v1、glm-4.7）不得出现在种子里」。
     * 这层因果已经不成立——当日复测中 {@code glm-5.3}、{@code dots3-note-prev} 均正常出字
     * （见 {@code docs/dev/model-availability-probe.md}），它们现在**会**出现在
     * {@link LlmProfileSeeder#seeds()} 的档案清单里。把「模型不可用」当成「智能体不绑定它」
     * 的理由已经不对了：真正的原因是绑定策略只挑两个主力，其余档案作为故障切换链的备选存在。
     */
    @Test
    void everyBuiltinAgentHasAUsableDefaultProfile() {
        List<AgentSeeder.Seed> seeds = AgentSeeder.seeds();

        assertThat(seeds).hasSize(7);
        assertThat(seeds).allSatisfy(seed ->
                assertThat(seed.profileSeedName()).as("%s 应有默认绑定", seed.code()).isNotBlank());
        assertThat(seeds).extracting(AgentSeeder.Seed::profileSeedName)
                .containsOnly("deepseek-flash", "glm-5.3-flash");
    }

    /**
     * 速度优先的智能体（工具密集）绑 deepseek-flash；能力优先的（成稿/判断）绑 glm-5.3-flash。
     *
     * <p>定时创作智能体归「速度优先」：它的输出几乎全是工具参数（{@code save_article_draft}
     * 的 {@code content} 就是整篇文章），实测生成速率 deepseek-flash 273 字符/秒、
     * glm-5.3-flash 仅 69~125（见 {@code scheduled-task-reliability-round.md} §4.1）。
     */
    @Test
    void toolHeavyAgentsGetTheFastProfileAndJudgementAgentsTheCapableOne() {
        List<AgentSeeder.Seed> seeds = AgentSeeder.seeds();

        assertThat(seeds).filteredOn(seed -> List.of("builtin_researcher", "builtin_illustrator",
                        "builtin_editor", "builtin_scheduled_creator").contains(seed.code()))
                .allSatisfy(seed -> assertThat(seed.profileSeedName())
                        .as("%s 以速度优先", seed.code()).isEqualTo("deepseek-flash"));
        assertThat(seeds).filteredOn(seed -> List.of("builtin_writer", "builtin_reviewer",
                        "builtin_chief").contains(seed.code()))
                .allSatisfy(seed -> assertThat(seed.profileSeedName())
                        .as("%s 以能力优先", seed.code()).isEqualTo("glm-5.3-flash"));
    }

    /**
     * 档案种子清单必须**覆盖**智能体绑定的每一个档案名，且不建实测不可用的模型。
     *
     * <p>为什么要钉这条：{@link AgentSeeder} 是按**名称**解析绑定的，若它绑的名字不在
     * {@link LlmProfileSeeder#seeds()} 里，解析就返回 null、绑定留空，该智能体静默回落到默认档案
     * ——不报错、不告警，只是模型悄悄换了一个。两处清单分散在两个类里，正是最容易漂移的地方。
     */
    @Test
    void profileSeedsCoverEveryAgentBindingAndExcludeUnusableModels() {
        java.util.Set<String> seeded = LlmProfileSeeder.seeds().stream()
                .map(LlmProfileSeeder.Seed::name)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(seeded)
                .as("AgentSeeder 绑定的档案名必须都被档案种子创建出来，否则绑定静默失效")
                .containsAll(AgentSeeder.seeds().stream()
                        .map(AgentSeeder.Seed::profileSeedName)
                        .collect(java.util.stream.Collectors.toSet()));

        // 2026-09-18 实测：deepseek-v4-pro-0813 网关报「模型未找到」；
        // kimi-k2.8-preview 只接受 temperature=1，而档案默认下发 0.70 → 必然 400。
        assertThat(seeded)
                .as("实测不可用的模型不得建档案：调用只会白耗一次切换")
                .doesNotContain("deepseek-v4-pro-0813", "kimi-k2.8-preview");
    }

    /**
     * 兜底档案必须是**声明的**那一条，且不能是智能体主用的档案。
     *
     * <p>为什么钉这条（2026-09-18 实机复现的真实缺陷）：兜底原先靠「把默认档案标为兜底」实现，
     * 而默认档案是可被用户改成主力的。本轮把默认档案设成 {@code deepseek-flash}（主力）之后，
     * 主用与兜底指向了同一条档案；{@code failoverChain} 按 id 去重，于是档案链**少掉一跳**，
     * 兜底那段形同虚设——不报错、不告警，只是主用挂掉时少一次救场机会。
     */
    @Test
    void exactlyOneDeclaredFallbackProfileAndItIsNotAPrimaryBinding() {
        List<LlmProfileSeeder.Seed> profiles = LlmProfileSeeder.seeds();

        assertThat(profiles).filteredOn(LlmProfileSeeder.Seed::fallback)
                .as("兜底档案必须恰好一条，多了说明链尾语义不清")
                .hasSize(1);
        assertThat(profiles).filteredOn(LlmProfileSeeder.Seed::fallback)
                .extracting(LlmProfileSeeder.Seed::name)
                .as("兜底应取 hy4-preview：限时免费 + Tier S，才配当最后一道安全网")
                .containsExactly("hy4-preview");

        // 智能体主用的档案不能同时是兜底，否则链上两段指向同一条、去重后少一跳
        java.util.Set<String> primaryBindings = AgentSeeder.seeds().stream()
                .map(AgentSeeder.Seed::profileSeedName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(primaryBindings)
                .as("兜底档案不得同时是智能体主用档案（否则故障切换链会因去重少一跳）")
                .doesNotContain("hy4-preview");
    }

    /**
     * 声明的兜底档案若不存在（被用户删了），退回「标记默认档案」——链尾必须有东西可用。
     *
     * <p>这条覆盖 {@code markFallbackIfUnset} 的 {@code orElse(source)} 分支：宁可兜底是个次优档案，
     * 也不能让档案链的最后一段是 nullptr——那等于没有兜底。
     */
    @Test
    void fallsBackToMarkingTheDefaultWhenDeclaredFallbackIsMissing() {
        LlmProfile source = new LlmProfile();
        source.setId(1L);
        source.setName("deepseek-flash");
        source.setProvider("OPENAI_COMPATIBLE");
        source.setBaseUrl("https://nexus.bx9y.com.cn");
        source.setModelName("deepseek-flash");
        source.setApiKeyEncrypted("encrypted-key");
        source.setEnabled(true);
        source.setIsDefault(true);

        LlmProfileMapper mapper = mock(LlmProfileMapper.class);
        when(mapper.findDefault()).thenReturn(source);
        when(mapper.findAll()).thenReturn(List.of(source));
        // 任何名字都查不到 → 声明的兜底 hy4-preview 视为不存在
        when(mapper.findByName(any())).thenReturn(null);
        when(mapper.findFallback()).thenReturn(null); // 库里没有兜底

        new LlmProfileSeeder(mapper).run(null);

        // 声明的兜底缺失，于是把默认档案标为兜底
        verify(mapper, atLeastOnce()).updateById(argThat(profile ->
                Boolean.TRUE.equals(profile.getIsFallback()) && "deepseek-flash".equals(profile.getName())));
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

        // 每个标准档案各插一次（条数由 LlmProfileSeeder.seeds() 决定，写死数字会在加档时假红）
        verify(mapper, times(LlmProfileSeeder.seeds().size())).insert(any(LlmProfile.class));
        // 兜底档案已存在（默认档案被标为兜底），不再重复标记
        verify(mapper, never()).updateById(any(LlmProfile.class));
    }
}
