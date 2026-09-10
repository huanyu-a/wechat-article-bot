package ink.icoding.wechat.article.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前后端 JSON 契约回归（skills-agent-plan 6.6 / 8.2）：
 * 前端读取 `isDefault` / `isBuiltin` 驼峰字段，record 的 `isXxx` 访问器若被 Jackson 剥离前缀会静默变成
 * `default`，导致「默认档案」徽标与删除保护失效。这里用真实 ObjectMapper 锁死字段名。
 */
class AgentJsonContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void profileViewSerializesIsDefault() throws Exception {
        LlmProfileService.ProfileView view = new LlmProfileService.ProfileView(
                1L, "默认配置", "OPENAI_COMPATIBLE", "https://api.openai.com", "gpt-4.1-mini",
                true, "••••••••abcd", new BigDecimal("0.7"), 4096, true, true, null);
        String json = mapper.writeValueAsString(view);
        assertThat(json).contains("\"isDefault\":true").doesNotContain("\"default\":");
        assertThat(json).doesNotContain("apiKey\""); // 不得回传完整 key
    }

    @Test
    void agentDefinitionSerializesIsBuiltin() throws Exception {
        AgentDefinition definition = new AgentDefinition();
        definition.setId(1L);
        definition.setCode("builtin_editor");
        definition.setName("墨舟编辑智能体");
        definition.setIsBuiltin(true);
        String json = mapper.writeValueAsString(definition);
        assertThat(json).contains("\"isBuiltin\":true").doesNotContain("\"builtin\":");
    }

    @Test
    void profileViewWithoutApiKeyReportsMaskedState() throws Exception {
        LlmProfileService.ProfileView view = new LlmProfileService.ProfileView(
                2L, "无 key 档案", "ANTHROPIC", "https://api.anthropic.com", "claude", false, "未配置",
                null, null, false, false, null);
        String json = mapper.writeValueAsString(view);
        assertThat(json).contains("\"hasApiKey\":false").contains("\"isDefault\":false");
    }
}
