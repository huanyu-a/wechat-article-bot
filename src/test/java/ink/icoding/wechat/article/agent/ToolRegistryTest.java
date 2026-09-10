package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工具注册表与协议常量纯单测（skills-agent-plan 8.1）。
 */
class ToolRegistryTest {
    private final ToolRegistry registry = new ToolRegistry();

    @Test
    void groupsCoverAllEightKeys() {
        assertThat(registry.groupKeys()).containsExactlyInAnyOrder(
                ToolRegistry.BROWSER_EDITOR, ToolRegistry.MEDIA, ToolRegistry.DRAFT_READ,
                ToolRegistry.DRAFT_WRITE, ToolRegistry.RESEARCH, ToolRegistry.REVIEW,
                ToolRegistry.DELEGATE, ToolRegistry.RENDER);
    }

    @Test
    void draftGroupsAreSplitByWritePermission() {
        assertThat(registry.toolNames(List.of(ToolRegistry.DRAFT_READ)))
                .containsExactly("read_article_draft");
        assertThat(registry.toolNames(List.of(ToolRegistry.DRAFT_WRITE)))
                .containsExactly("save_article_draft", "set_article_draft_cover");
    }

    @Test
    void unknownGroupKeyRejected() {
        assertThatThrownBy(() -> registry.validate(List.of("NOT_A_GROUP")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知的工具组");
    }

    @Test
    void nullToolKeysPassValidation() {
        registry.validate(null);
        assertThat(registry.toolNames(null)).isEmpty();
    }

    @Test
    void browserEditorGroupMarkedBrowserSide() {
        assertThat(registry.groups())
                .filteredOn(ToolRegistry.Group::browserSide)
                .extracting(ToolRegistry.Group::key)
                .containsExactly(ToolRegistry.BROWSER_EDITOR);
    }

    @Test
    void expandGroupsDeduplicatesToolNames() {
        Set<String> names = registry.toolNames(List.of(ToolRegistry.DRAFT_READ, ToolRegistry.DRAFT_READ));
        assertThat(names).hasSize(1);
    }

    @Test
    void protocolByStageCoversAllStages() {
        for (String stage : List.of("EDITOR", "SCHEDULED_SINGLE", "RESEARCH", "WRITING",
                "ILLUSTRATION", "REVIEW", "COORDINATE")) {
            assertThat(AgentProtocols.byStage(stage)).isNotBlank();
        }
        // 未知 stage 回落 SCHEDULED_SINGLE，保证自定义 stage 可用
        assertThat(AgentProtocols.byStage("CUSTOM_STAGE")).isEqualTo(AgentProtocols.SCHEDULED_SINGLE);
    }

    @Test
    void chiefProtocolForbidsWritingDraft() {
        assertThat(AgentProtocols.COORDINATE).contains("只读草稿");
        assertThat(AgentProtocols.REVIEW).contains("不要修改草稿");
    }

    @Test
    void fallbackGroupsAreStageSpecific() {
        assertThat(AgentFactory.defaultGroupsFor("EDITOR"))
                .contains(ToolRegistry.BROWSER_EDITOR, ToolRegistry.MEDIA,
                        ToolRegistry.RENDER, ToolRegistry.DELEGATE);
        assertThat(AgentFactory.defaultGroupsFor("SCHEDULED_SINGLE"))
                .containsExactly(ToolRegistry.DRAFT_READ, ToolRegistry.DRAFT_WRITE, ToolRegistry.MEDIA);
        // 审稿人兜底不得获得写草稿权限（5.3 工具组拆分的目的）
        assertThat(AgentFactory.defaultGroupsFor("REVIEW"))
                .contains(ToolRegistry.DRAFT_READ, ToolRegistry.REVIEW)
                .doesNotContain(ToolRegistry.DRAFT_WRITE);
        assertThat(AgentFactory.defaultGroupsFor("COORDINATE"))
                .contains(ToolRegistry.DELEGATE, ToolRegistry.DRAFT_READ)
                .doesNotContain(ToolRegistry.DRAFT_WRITE);
    }

    @Test
    void seederMergesSystemGroupsWithoutDroppingUserChoices() {
        String existing = AgentDefinitionService.toJson(List.of(ToolRegistry.BROWSER_EDITOR,
                ToolRegistry.MEDIA));
        String seed = AgentDefinitionService.toJson(List.of(ToolRegistry.BROWSER_EDITOR,
                ToolRegistry.MEDIA, ToolRegistry.RENDER, ToolRegistry.DELEGATE));
        List<String> merged = AgentDefinitionService.parseToolKeys(
                ink.icoding.wechat.article.agent.AgentSeeder.mergeSystemGroups(existing, seed));
        assertThat(merged).containsExactly(ToolRegistry.BROWSER_EDITOR, ToolRegistry.MEDIA,
                ToolRegistry.RENDER, ToolRegistry.DELEGATE);
        // 用户新增的组不被移除
        String userExtra = AgentDefinitionService.toJson(List.of(ToolRegistry.BROWSER_EDITOR,
                ToolRegistry.MEDIA, ToolRegistry.REVIEW));
        assertThat(AgentDefinitionService.parseToolKeys(
                ink.icoding.wechat.article.agent.AgentSeeder.mergeSystemGroups(userExtra, seed)))
                .contains(ToolRegistry.REVIEW, ToolRegistry.RENDER);
    }

    @Test
    void toolKeysJsonRoundTrip() {
        String json = AgentDefinitionService.toJson(List.of(ToolRegistry.MEDIA, ToolRegistry.DRAFT_READ));
        assertThat(AgentDefinitionService.parseToolKeys(json))
                .containsExactly(ToolRegistry.MEDIA, ToolRegistry.DRAFT_READ);
        assertThat(AgentDefinitionService.parseToolKeys(null)).isEmpty();
        assertThat(AgentDefinitionService.parseToolKeys("not-json")).isEmpty();
    }

    @Test
    void normalizeBaseUrlStripsEndpointSuffixAndValidatesScheme() {
        assertThat(LlmProfileService.normalizeBaseUrl("https://api.openai.com/v1/responses/"))
                .isEqualTo("https://api.openai.com");
        assertThatThrownBy(() -> LlmProfileService.normalizeBaseUrl("api.openai.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("http://");
    }
}
