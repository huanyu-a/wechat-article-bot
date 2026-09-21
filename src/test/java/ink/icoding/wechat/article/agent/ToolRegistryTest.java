package ink.icoding.wechat.article.agent;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.wechat.article.ai.ArticleMediaTools;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
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

    /** create() 只 new 工具、不碰依赖的服务，因此可以传 null 直接拿到真实的 7 件产物。 */
    private static List<Tool> mediaToolsCreated() {
        return new ArticleMediaTools(null, null, null).create(1L, 1L);
    }

    private static List<String> namesOf(List<Tool> tools) {
        return tools.stream().map(ToolDescriptor::fromTool).map(ToolDescriptor::getName).toList();
    }

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

    /**
     * D55 图片来源硬约束落地（2026-09-19）：组声明即暴露名单。正常路径——
     * create() 的 7 件真实产物按 MEDIA 名单过滤后只剩 5 件，两个网络图工具被剔除且顺序保持。
     */
    @Test
    void mediaGroupFilterDropsWebImageTools() {
        List<Tool> created = mediaToolsCreated();

        List<Tool> exposed = registry.filterByGroup(ToolRegistry.MEDIA, created);

        assertThat(namesOf(exposed))
                .containsExactly("search_web", "browse_webpage", "list_image_assets",
                        "generate_image", "edit_image");
        // 被剔除的正是两个网络图工具（按实例身份核对，而不是只看名字）
        assertThat(exposed).doesNotContain(created.get(2), created.get(4));
        assertThat(namesOf(created)).hasSize(7);
    }

    /** 边界：过滤只按名单收窄，其他组（草稿写）的合法工具一件不少。 */
    @Test
    void groupFilterDoesNotTouchOtherGroups() {
        List<Tool> draftTools = ScheduledArticleTools.all(new ScheduledArticleTools.DraftState(null));

        List<Tool> writeExposed = registry.filterByGroup(ToolRegistry.DRAFT_WRITE, draftTools);
        assertThat(namesOf(writeExposed))
                .containsExactly("save_article_draft", "set_article_draft_cover");

        List<Tool> readExposed = registry.filterByGroup(ToolRegistry.DRAFT_READ, draftTools);
        assertThat(namesOf(readExposed)).containsExactly("read_article_draft");

        // 草稿工具在 MEDIA 组下一件都不该出现（组名单互不串门）
        assertThat(registry.filterByGroup(ToolRegistry.MEDIA, draftTools)).isEmpty();
    }

    /** 边界：未知组键 fail-closed（空列表）；null / 空入参同样是空列表，不抛异常。 */
    @Test
    void groupFilterIsFailClosedForUnknownKeyAndEmptyInput() {
        assertThat(registry.filterByGroup("NOT_A_GROUP", mediaToolsCreated())).isEmpty();
        assertThat(registry.filterByGroup(null, mediaToolsCreated())).isEmpty();
        assertThat(registry.filterByGroup(ToolRegistry.MEDIA, null)).isEmpty();
        assertThat(registry.filterByGroup(ToolRegistry.MEDIA, List.of())).isEmpty();
    }

    /** 组名单与 D55 约束保持一致：MEDIA 不含两个网络图工具，文字检索通道不受影响。 */
    @Test
    void mediaGroupDeclarationOmitsWebImageToolsOnly() {
        assertThat(registry.toolNames(List.of(ToolRegistry.MEDIA)))
                .contains("search_web", "browse_webpage", "list_image_assets",
                        "generate_image", "edit_image")
                .doesNotContain("search_web_images", "import_web_image");
    }

    /** 提示栈与工具层一致：两条链路都明确禁止网络图，且不再声称可以导入外链图片。 */
    @Test
    void protocolsForbidWebImageToolsConsistentWithToolLayer() {
        for (String protocol : List.of(AgentProtocols.EDITOR, AgentProtocols.SCHEDULED_SINGLE)) {
            assertThat(protocol).contains("search_web_images / import_web_image");
            assertThat(protocol).doesNotContain("import_web_image 保存来源");
            assertThat(protocol).doesNotContain("网络图片导入");
        }
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
