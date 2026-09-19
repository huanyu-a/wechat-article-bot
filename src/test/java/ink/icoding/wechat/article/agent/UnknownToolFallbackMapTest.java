package ink.icoding.wechat.article.agent;

import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.entity.ModelType;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * agent4j 未知工具 NPE 兜底（D56）的红绿锚点：拆掉包装（get 回到原样），未知名字重新变 null。
 */
class UnknownToolFallbackMapTest {

    @Test
    void unknownNameGetsFallbackToolInsteadOfNull() {
        UnknownToolFallbackMap.FallbackToolMap map = new UnknownToolFallbackMap.FallbackToolMap();
        Tool known = mock(Tool.class);
        map.put("read_article_draft", known);

        assertThat(map.get("read_article_draft")).isSameAs(known);
        // 模型幻觉出的工具名：拿到的是 fallback 工具而不是 null——NPE 的直接根源被消除
        Tool fallback = map.get("make_me_a_sandwich");
        assertThat(fallback).isNotNull();
        // fallback 能被 agent4j 的描述符解析（此前正是 fromTool(null) 在 NPE）
        assertThat(ToolDescriptor.fromTool(fallback).getName())
                .isEqualTo(UnknownToolFallbackMap.FALLBACK_TOOL_NAME);
    }

    @Test
    void fallbackExecuteListsCurrentlyRegisteredTools() {
        UnknownToolFallbackMap.FallbackToolMap map = new UnknownToolFallbackMap.FallbackToolMap();
        map.put("search_web", mock(Tool.class));
        map.put("save_article_draft", mock(Tool.class));

        UnknownToolFallbackMap.FallbackTool fallback = new UnknownToolFallbackMap.FallbackTool(map::availableNames);
        String result = fallback.execute(new UnknownToolFallbackMap.FallbackTool.Args());

        assertThat(result).contains("不存在").contains("search_web").contains("save_article_draft")
                .contains("重新调用");
    }

    @Test
    void attachReplacesTheModelToolMapField() throws Exception {
        LLMModel model = LLMModel.create(ModelType.OpenAI, "http://localhost/", "unit-test-model", "key");
        UnknownToolFallbackMap.attach(model);

        Field field = findField(model.getClass(), "toolMap");
        field.setAccessible(true);
        Map<String, Tool> map = (Map<String, Tool>) field.get(model);
        assertThat(map).isInstanceOf(UnknownToolFallbackMap.FallbackToolMap.class);
        // 挂载后注册的工具仍能查到；未注册的名字拿到 fallback
        map.put("read_article_draft", mock(Tool.class));
        assertThat(map.get("read_article_draft")).isNotNull();
        assertThat(map.get("hallucinated_tool")).isNotNull();
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> t = type; t != null; t = t.getSuperclass()) {
            try {
                return t.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向上找
            }
        }
        throw new NoSuchFieldException(name);
    }
}
