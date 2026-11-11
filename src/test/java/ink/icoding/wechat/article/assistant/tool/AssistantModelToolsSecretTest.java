package ink.icoding.wechat.article.assistant.tool;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密钥不进对话的固化测试（docs/dev/global-assistant-plan.md §5.4）。
 *
 * <p>为什么用反射而不是「读一遍代码确认」：对话内容会落 {@code assistant_message} 表、进 SSE 帧、
 * 进第三方模型上下文。用户一旦在聊天框里贴出 API Key，它就同时出现在这三处，其中两处不受本项目的
 * {@code CryptoService} 保护。这条约束必须由会变红的测试守着——否则某天有人为了"顺手支持一下"
 * 给参数类加一个 {@code apiKey} 字段，评审时很难看出来。
 */
class AssistantModelToolsSecretTest {

    /** 工厂只 new 工具、不碰依赖的服务，因此可以传 null（与 {@code ToolRegistryTest} 同一手法）。 */
    private static List<Tool> modelTools() {
        return AssistantModelTools.all(null, null, 1L);
    }

    private static List<String> namesOf(List<Tool> tools) {
        return tools.stream().map(ToolDescriptor::fromTool).map(ToolDescriptor::getName).toList();
    }

    @Test
    void factoryProducesExactlyTheDeclaredGroupMembers() {
        // 工具名是契约：ToolRegistry 的四组按名字过滤实例，名字对不上等于工具凭空消失
        assertThat(namesOf(modelTools())).containsExactly(
                "list_llm_providers", "list_llm_profiles", "discover_llm_models",
                "create_llm_provider", "update_llm_provider", "create_llm_profile",
                "update_llm_profile", "set_default_llm_profile", "set_fallback_llm_profile",
                "delete_llm_profile", "delete_llm_provider");
    }

    /**
     * 工厂产出的每个工具都必须登记在 {@code ASSISTANT_MODEL} 组里。
     *
     * <p>这条替代了原来的「一个删除工具都不许有」：策略已改为「助手可以操作全部功能」，
     * 但**能不能被调到的唯一依据是注册表**——工厂里多出一个工具却没登记，它就永远不会出现在
     * 任何智能体的工具箱里（用户看到的是「助手说它不会」）；反过来，登记了却没有对应实现，
     * 名字过滤会静默丢弃它。两个方向的不一致都该让构建变红。
     */
    @Test
    void everyFactoryToolIsRegisteredInTheConfigGroup() {
        List<String> declared = new ink.icoding.wechat.article.agent.ToolRegistry()
                .groups().stream()
                .filter(group -> ink.icoding.wechat.article.agent.ToolRegistry.ASSISTANT_MODEL
                        .equals(group.key()))
                .findFirst().orElseThrow().tools();
        assertThat(declared).containsAll(namesOf(modelTools()));
    }

    /**
     * 所有参数类都不得含有 apiKey / clearApiKey 字段。
     *
     * <p>覆盖整个文件里**每一个** {@code ToolParam} 子类，而不只是两个写工具：读工具将来若加一个
     * 「顺便把 key 回传」的字段，同样是把密钥送进对话。
     */
    @Test
    void noParamCarriesAKeyField() {
        List<Class<?>> paramClasses = Arrays.stream(AssistantModelTools.class.getDeclaredClasses())
                .filter(ink.icoding.llm.core.tool.ToolParam.class::isAssignableFrom)
                .toList();
        // 每个工具恰好一个参数类；数量对不上说明有工具漏了参数类或多了内部类
        assertThat(paramClasses).hasSize(namesOf(modelTools()).size());
        for (Class<?> paramClass : paramClasses) {
            for (Field field : paramClass.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                // maxTokens 是生成长度上限，字面含 token 但与凭据无关；白名单只放这一个
                if (name.equals("maxtokens")) continue;
                assertThat(name)
                        .as("%s 不应携带密钥字段", paramClass.getSimpleName())
                        .doesNotContain("apikey")
                        .doesNotContain("clearapikey")
                        .doesNotContain("secret")
                        .doesNotContain("token");
            }
        }
    }

    /**
     * 供应商视图/档案视图这两个 {@code record} 不含可能的明文字段。
     *
     * <p>工具把视图原样序列化给模型，所以「不回显密钥」首先取决于视图类型里没有那个字段——
     * 而不是取决于工具作者记得删。这里把这个前提也钉住。
     */
    @Test
    void viewsExposeOnlyMaskedKeys() {
        List<String> providerFields = Arrays.stream(
                        ink.icoding.wechat.article.agent.LlmProviderService.ProviderView.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT)).toList();
        assertThat(providerFields).contains("apikeymasked").doesNotContain("apikey");

        List<String> profileFields = Arrays.stream(
                        ink.icoding.wechat.article.agent.LlmProfileService.ProfileView.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT)).toList();
        assertThat(profileFields).contains("apikeymasked").doesNotContain("apikey");
    }
}