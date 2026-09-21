package ink.icoding.wechat.article;

import ink.icoding.llm.core.entity.ModelType;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.wechat.article.ai.ArticleEditorTools;
import ink.icoding.wechat.article.ai.ArticleMediaTools;
import ink.icoding.wechat.article.ai.SafeWebService;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class WechatArticleBotApplicationTests {
    @Autowired
    private ArticleMediaTools mediaTools;
    @Autowired
    private SafeWebService safeWebService;

    @Test
    void contextLoads() {
    }

    @Test
    void articleAgentExposesOnlyScopedEditorTools() {
        var descriptors = ArticleEditorTools.all((name, json) -> "test")
                .stream().map(ToolDescriptor::fromTool).toList();
        Set<String> names = descriptors.stream().map(ToolDescriptor::getName).collect(Collectors.toSet());
        assertEquals(Set.of("read_article", "read_blocks", "delete_blocks", "insert_blocks",
                "replace_blocks", "update_metadata", "update_cover"), names);
        assertTrue(descriptors.stream().allMatch(descriptor -> descriptor.toLLMContent(ModelType.OpenAIResponse) != null));
        assertTrue(descriptors.stream().allMatch(descriptor -> descriptor.toLLMContent(ModelType.Anthropic) != null));
    }

    /**
     * MEDIA 组声明的名单成为工具暴露的唯一事实源（D55 图片来源硬约束，2026-09-19）：
     * create() 仍会造出 7 件工具（兜底映射/预算还按名索引它们），但 assemble/filterByGroup 之后
     * 暴露给模型的只剩组内 5 件——网络搜图与外链导入从工具层面就不存在。
     * 这条断言从「create() 原样返回 7 件」改成「按组过滤后 5 件，且不含两个网络图工具」。
     */
    @Test
    void articleAgentExposesMediaAndWebToolsForBothProtocols() {
        var registry = new ink.icoding.wechat.article.agent.ToolRegistry();
        var exposed = registry.filterByGroup(ink.icoding.wechat.article.agent.ToolRegistry.MEDIA,
                mediaTools.create(null, 1L));
        var descriptors = exposed.stream().map(ToolDescriptor::fromTool).toList();
        Set<String> names = descriptors.stream().map(ToolDescriptor::getName).collect(Collectors.toSet());
        assertEquals(Set.of("search_web", "browse_webpage", "list_image_assets",
                "generate_image", "edit_image"), names);
        assertTrue(descriptors.stream().allMatch(descriptor -> descriptor.toLLMContent(ModelType.OpenAIResponse) != null));
        assertTrue(descriptors.stream().allMatch(descriptor -> descriptor.toLLMContent(ModelType.Anthropic) != null));
    }

    /**
     * 组过滤是「收窄暴露」而非「改变产物」：create() 自身仍返回 7 件（含两个网络图工具），
     * 被剔除只发生在 filterByGroup 这一层——保证按名索引 create() 产物的逻辑不受影响。
     */
    @Test
    void mediaToolsCreateStillBuildsAllSevenToolsBeforeGroupFiltering() {
        var names = mediaTools.create(null, 1L).stream()
                .map(ToolDescriptor::fromTool).map(ToolDescriptor::getName).collect(Collectors.toSet());
        assertEquals(Set.of("search_web", "browse_webpage", "search_web_images", "list_image_assets",
                "import_web_image", "generate_image", "edit_image"), names);
    }

    @Test
    void scheduledAgentExposesServerSideDraftTools() {
        var descriptors = ScheduledArticleTools.all(new ScheduledArticleTools.DraftState(null))
                .stream().map(ToolDescriptor::fromTool).toList();
        Set<String> names = descriptors.stream().map(ToolDescriptor::getName).collect(Collectors.toSet());
        assertEquals(Set.of("read_article_draft", "save_article_draft", "set_article_draft_cover"), names);
        assertTrue(descriptors.stream().allMatch(descriptor -> descriptor.toLLMContent(ModelType.OpenAIResponse) != null));
        assertTrue(descriptors.stream().allMatch(descriptor -> descriptor.toLLMContent(ModelType.Anthropic) != null));
    }

    @Test
    void webBrowserStillRejectsLiteralPrivateAndLocalAddresses() {
        assertThrows(BusinessException.class, () -> safeWebService.browse("http://127.0.0.1/secret"));
        assertThrows(BusinessException.class, () -> safeWebService.browse("http://192.168.1.10/secret"));
        assertThrows(BusinessException.class, () -> safeWebService.browse("http://100.73.43.10/secret"));
        assertThrows(BusinessException.class, () -> safeWebService.browse("http://[fdfe:dcba:9876::20]/secret"));
        assertThrows(BusinessException.class, () -> safeWebService.browse("http://service.internal/secret"));
    }

    @Test
    void mediaMutationToolsReuseTheFirstResultForIdenticalConcurrentCalls() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var deduplicator = new ink.icoding.wechat.article.ai.ToolMutationDeduplicator();

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> deduplicator.execute(
                "generate_image", "{\"prompt\":\"cover\",\"filename\":\"cover.png\"}", () -> {
                    executions.incrementAndGet();
                    started.countDown();
                    try {
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    return "asset-14";
                }));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        CompletableFuture<String> duplicate = CompletableFuture.supplyAsync(() -> deduplicator.execute(
                "generate_image", "{\"prompt\":\"cover\",\"filename\":\"cover.png\"}", () -> {
                    executions.incrementAndGet();
                    return "asset-15";
                }));

        release.countDown();
        assertEquals("asset-14", first.get(5, TimeUnit.SECONDS));
        assertEquals("asset-14", duplicate.get(5, TimeUnit.SECONDS));
        assertEquals(1, executions.get());
        assertEquals("asset-16", deduplicator.execute(
                "generate_image", "{\"prompt\":\"another cover\",\"filename\":\"cover.png\"}",
                () -> "asset-16"));
    }

}
