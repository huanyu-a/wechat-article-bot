package ink.icoding.wechat.article.agent;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.model.LLMModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * agent4j 未知工具 NPE 的兜底（known-issues-handoff.md D56）。
 *
 * <p>症状（run#35）：模型返回了一个本项目没有广告过的工具名，agent4j 的
 * {@code handleToolCallsAndContinue} 对 {@code toolMap.get(name)} 的返回值不判空，
 * 直接 {@code ToolDescriptor.fromTool(null)} → NPE。AgentInvoker 把它当瞬时错误重试 5 次，
 * 但同样的上下文大概率再产出同样的未知工具名——5 次白烧后整轮 FAILED（run#35 实测 24 次工具调用后失败）。
 *
 * <p>修法：agent4j 的 {@code toolMap} 在构造器里创建（ConcurrentHashMap）之后**实例永不替换**，
 * 后续注册都是对同一实例的 put——所以可以在模型创建后用反射把字段整体换成这里的包装 Map：
 * 已注册的工具原样返回；**未注册的名字返回一个真实的 fallback 工具**，其执行结果是
 * 「工具不存在 + 可用工具列表」，模型在下一轮就能自纠，而不是让整轮死在 NPE 上。
 *
 * <p>为什么不用网线层（okhttp Interceptor）改响应：工具调用的 name 在 SSE delta 分片里可能被
 * 拆成多段，重组后再改写等价于重写 agent4j 的流式解析，复杂且脆；Map 层是它自己查表的唯一入口。
 */
public final class UnknownToolFallbackMap {

    private static final Logger log = LoggerFactory.getLogger(UnknownToolFallbackMap.class);
    /** fallback 工具在描述符里的名字：AgentInvokerTest 等处用它断言「这是兜底而不是真工具」。 */
    public static final String FALLBACK_TOOL_NAME = "unknown_tool_fallback";

    private UnknownToolFallbackMap() {
    }

    /** 把 model 的 toolMap 字段换成带兜底的包装；找不到字段（上游结构变化）时打日志跳过，不影响启动。 */
    public static void attach(LLMModel model) {
        Class<?> type = model.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("toolMap");
                field.setAccessible(true);
                Map<String, Tool> original = (Map<String, Tool>) field.get(model);
                FallbackToolMap wrapper = new FallbackToolMap();
                if (original != null) wrapper.putAll(original);
                field.set(model, wrapper);
                log.info("未知工具兜底已挂载（工具注册数 {}）：未知工具名将得到「工具不存在 + 可用列表」的代答，不再触发 NPE",
                        wrapper.registeredCount());
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Exception e) {
                log.warn("未知工具兜底挂载失败（不影响启动，NPE 风险回到重试兜底）：{}", e.toString());
                return;
            }
        }
        log.warn("未知工具兜底挂载失败：{} 及其父类没有 toolMap 字段", model.getClass().getName());
    }

    /**
     * 包装 Map：注册（put/putAll）原样透传；{@code get} 对未注册的名字返回 fallback 工具
     * （其执行结果里带**当前已注册**的工具列表，模型据此自纠）。
     */
    static final class FallbackToolMap extends ConcurrentHashMap<String, Tool> {

        private final Tool fallback = new FallbackTool(this::availableNames);

        FallbackToolMap() {
            super();
        }

        String availableNames() {
            return keySet().stream().sorted().collect(Collectors.joining("、"));
        }

        int registeredCount() {
            return size();
        }

        @Override
        public Tool get(Object key) {
            Tool tool = super.get(key);
            return tool != null ? tool : fallback;
        }
    }

    @ToolInfo(name = FALLBACK_TOOL_NAME,
            description = "系统兜底工具：模型调用了本项目不存在的工具时由系统代答，列出真实可用的工具，模型应从中选择后重新调用。")
    static class FallbackTool implements Tool<FallbackTool.Args> {

        private final java.util.function.Supplier<String> availableNames;

        FallbackTool(java.util.function.Supplier<String> availableNames) {
            this.availableNames = availableNames;
        }

        @Override
        public String execute(Args param) {
            return "调用的工具在本项目不存在，本次调用没有执行。当前真实可用的工具："
                    + availableNames.get()
                    + "。请从上面的列表里选择正确的工具重新调用；如果原任务确实无法用现有工具完成，请直接用文字说明并收尾。";
        }

        /** 模型给未知工具的原始参数原样承接（内容不影响代答文本，但保留参数形状让解析不挑）。 */
        @lombok.Data
        public static class Args extends ToolParam {
            @Param(description = "被调用工具的原始参数（原样保留，不做处理）")
            private Object arguments;
        }
    }
}
