package ink.icoding.wechat.article.agent;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.settings.LlmConfigService;
import ink.icoding.wechat.article.skill.SkillContext;
import ink.icoding.wechat.article.skill.SkillPromptAssembler;
import ink.icoding.wechat.article.skill.SkillPromptResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentFactory 装配与模型解析单测（skills-agent-plan 5.3 / 5.7 / 8.1）：
 * 定义缺失回落、按定义装配（协议+人设+技能）、模型档案软引用回落、工具组解析。
 */
class AgentFactoryTest {
    private AgentDefinitionMapper definitionMapper;
    private LlmProfileService llmProfileService;
    private LlmConfigService llmConfigService;
    private SkillPromptAssembler assembler;
    private AgentFactory factory;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(AgentDefinitionMapper.class);
        llmProfileService = mock(LlmProfileService.class);
        llmConfigService = mock(LlmConfigService.class);
        assembler = mock(SkillPromptAssembler.class);
        factory = new AgentFactory(definitionMapper, llmProfileService, llmConfigService, assembler);
        when(assembler.assemble(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SkillPromptResult("【排版模板】\n模板内容\n", ink.icoding.wechat.article.skill.LayoutEngine.PROMPT,
                        List.of()));
    }

    private static LlmProfileService.RuntimeProfile profile(String provider, String model) {
        return new LlmProfileService.RuntimeProfile(true, provider, "https://api.example.com", model,
                "key-123", new BigDecimal("0.7"), 4096, true, null);
    }

    private static AgentDefinition definition(String code, String stage, String persona, String toolKeys) {
        AgentDefinition definition = new AgentDefinition();
        definition.setCode(code);
        definition.setName("测试智能体");
        definition.setStage(stage);
        definition.setPersona(persona);
        definition.setToolKeys(toolKeys);
        definition.setEnabled(true);
        return definition;
    }

    private SkillContext context() {
        return new SkillContext(List.of(), List.of(), List.of(), List.of(), null,
                SkillContext.Scene.SCHEDULED);
    }

    @Test
    void buildsAgentFromDefinitionWithProtocolAndPersona() {
        when(definitionMapper.findByCode("builtin_writer"))
                .thenReturn(definition("builtin_writer", "WRITING", "你是测试撰稿人。", "[\"DRAFT_READ\"]"));
        when(llmProfileService.defaultProfile()).thenReturn(null);
        when(llmConfigService.runtime()).thenReturn(new LlmConfigService.RuntimeConfig(
                true, "OPENAI_COMPATIBLE", "https://api.openai.com", "gpt-test", "sk-test",
                null, null, null, null, null));

        AgentClient agent = factory.buildByCode("builtin_writer", "WRITING", context(), groups -> List.of());

        assertThat(agent.getName()).isEqualTo("测试智能体");
        // 系统提示 = 核心协议（代码常量）+ persona（DB）+ 技能注入块
        assertThat(agent.getDescription()).contains("公众号撰稿人").contains("你是测试撰稿人。")
                .contains("模板内容");
        // 回放守卫必须真的包在模型外层，否则历史里的空 arguments 会再次触发网关 400
        assertThat(agent.getModel()).isInstanceOf(ToolCallArgumentGuard.class);
    }

    @Test
    void missingDefinitionFallsBackToBuiltinDefault() {
        when(definitionMapper.findByCode("builtin_editor")).thenReturn(null);
        when(llmProfileService.defaultProfile()).thenReturn(null);
        when(llmConfigService.runtime()).thenReturn(new LlmConfigService.RuntimeConfig(
                true, "ANTHROPIC", "https://api.anthropic.com", "claude-test", "sk-test",
                null, null, null, null, null));

        AgentClient agent = factory.buildByCode("builtin_editor", "EDITOR", context(), groups -> List.of());

        assertThat(agent.getName()).isEqualTo("墨舟智能体");
        assertThat(agent.getDescription()).contains("浏览器中的富文本编辑器");
    }

    @Test
    void deletedProfileFallsBackToDefaultProfile() {
        AgentDefinition definition = definition("builtin_writer", "WRITING", "人设", "[\"DRAFT_READ\"]");
        definition.setLlmProfileId(99L);
        when(definitionMapper.findByCode("builtin_writer")).thenReturn(definition);
        when(llmProfileService.findById(99L)).thenReturn(null); // 档案已删除
        when(llmProfileService.defaultProfile()).thenReturn(new LlmProfile());
        when(llmProfileService.runtime(org.mockito.ArgumentMatchers.any()))
                .thenReturn(profile("OPENAI_RESPONSES", "default-model"));

        AgentClient agent = factory.buildByCode("builtin_writer", "WRITING", context(), groups -> List.of());

        assertThat(agent.getModel()).isNotNull();
    }

    @Test
    void toolResolverReceivesGroupKeysFromDefinition() {
        when(definitionMapper.findByCode("builtin_writer"))
                .thenReturn(definition("builtin_writer", "WRITING", "人设",
                        "[\"DRAFT_READ\",\"DRAFT_WRITE\"]"));
        when(llmProfileService.defaultProfile()).thenReturn(null);
        when(llmConfigService.runtime()).thenReturn(new LlmConfigService.RuntimeConfig(
                true, "OPENAI_COMPATIBLE", "https://api.openai.com", "gpt-test", "sk",
                null, null, null, null, null));

        List<List<String>> captured = new java.util.ArrayList<>();
        factory.buildByCode("builtin_writer", "WRITING", context(), groups -> {
            captured.add(groups);
            return List.of();
        });

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).containsExactly("DRAFT_READ", "DRAFT_WRITE");
    }

    /**
     * 缺陷 B3：模型档案的密钥解不开（``APP_SECRET_KEY`` 轮换过）时，装配必须**降级**到 llm_config。
     *
     * <p>这是本轮最要紧的一条回归钉。实测形态：坏密钥的库上 ``GET /api/settings/llm`` 与
     * ``GET /api/llm-profiles`` 双双 500，管理员连唯一的修复入口都打不开；如果装配路径也抛，
     * 症状就变成「所有智能体请求 500」，比配置读不出来更糟。
     */
    @Test
    void brokenProfileKeyDegradesToLlmConfigInsteadOfFailing() {
        AgentDefinition definition = definition("builtin_writer", "WRITING", "人设", null);
        when(llmProfileService.failoverChain(any())).thenReturn(List.of());
        when(llmProfileService.defaultProfile()).thenReturn(new LlmProfile());
        when(llmProfileService.runtime(any()))
                .thenThrow(new IllegalStateException("敏感信息解密失败，请检查 APP_SECRET_KEY"));
        when(llmConfigService.runtime()).thenReturn(new LlmConfigService.RuntimeConfig(
                true, "OPENAI_COMPATIBLE", "https://llm.example.test", "compat-model", "sk-compat",
                null, null, null, null, null));

        List<AgentFactory.ModelCandidate> candidates = factory.createModelCandidates(definition);

        assertThat(candidates).as("必须降级到 llm_config，而不是把请求打成 500").hasSize(1);
        assertThat(candidates.get(0).label()).isEqualTo("llm_config 默认配置");
    }

    /**
     * 缺陷 B3 的另一半：档案与 llm_config 两跳都解不开时，给一条**可读的业务错误**
     * （400 + 指向「模型档案」页的新文案），而不是 500 的「服务暂时不可用」。
     */
    @Test
    void brokenProfileAndConfigProduceTheReadableBusinessMessage() {
        AgentDefinition definition = definition("builtin_writer", "WRITING", "人设", null);
        when(llmProfileService.failoverChain(any())).thenReturn(List.of());
        when(llmProfileService.defaultProfile()).thenReturn(null);
        when(llmConfigService.runtime())
                .thenThrow(new IllegalStateException("敏感信息解密失败，请检查 APP_SECRET_KEY"));

        assertThatThrownBy(() -> factory.createModelCandidates(definition))
                .isInstanceOf(BusinessException.class)
                .as("文案必须把用户指向「模型档案」页（§4.4b），不能被全局兜底吞成 500")
                .hasMessageContaining("模型档案");
    }

    @Test
    void llmConfigFallbackWhenNoProfilesExist() {
        when(definitionMapper.findByCode("builtin_writer"))
                .thenReturn(definition("builtin_writer", "WRITING", "人设", null));
        when(llmProfileService.defaultProfile()).thenReturn(null);
        when(llmConfigService.runtime()).thenReturn(new LlmConfigService.RuntimeConfig(
                true, "OPENAI_COMPATIBLE", "https://llm.example.test", "compat-model", "sk-compat",
                null, null, null, null, null));

        AgentClient agent = factory.buildByCode("builtin_writer", "WRITING", context(), groups -> List.of());

        assertThat(agent.getModel()).isNotNull();
    }
}
