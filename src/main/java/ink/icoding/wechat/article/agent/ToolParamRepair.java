package ink.icoding.wechat.article.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具参数形状归一化：在 agent4j 解析参数**之前**把模型常见的畸形 JSON 修成声明形状。
 *
 * <h2>为什么必须在解析之前</h2>
 * agent4j 的 {@code ToolExecutor.defaultExecute} 用
 * {@code ToolParam.fromJsonString(paramJson, descriptor.getParamClass())} 解析参数，
 * 内部是 Jackson 的 {@code readValue}：形状不匹配就抛异常，被包成
 * {@code Failed to parse tool param JSON: <原文>} 作为工具失败回给模型。
 * <b>一旦抛了就没有第二次机会</b>——只能等模型下一轮换个写法，所以只能在调用 {@code execute} 之前修。
 *
 * <h2>为什么需要（真实缺陷，run#129 审核阶段 7/7 全失败）</h2>
 * {@code submit_review} 声明的 {@code issues} / {@code suggestions} 是 {@code List<String>}，
 * 但实测模型稳定地传成<b>对象</b>而不是数组，7 次尝试 7 次解析失败，把整个审核阶段烧到 300 秒硬超时、
 * 审核拿不出结论，写作阶段被迫返工再撞 429。观测到的畸形形状：
 * <ul>
 *   <li>{@code {"issues":{"suggestions":[…],"summary":"…","passed":false,"issues":[…]}}}
 *       —— 把整个结论原封不动嵌套进 {@code issues}；</li>
 *   <li>{@code {"issues":{"1":"草稿为空，无法审核：…"}}} —— 数组写成「序号 → 文本」的映射；</li>
 *   <li>{@code {"issues":{"<某句问题描述>":""},"passed":false,"suggestions":{…}}}
 *       —— {@code issues} 与 {@code suggestions} 整体互换、且都写成对象。</li>
 * </ul>
 * {@code AgentProtocols.REVIEW} 里已经写明了这个错误原文和「必须是数组字面量」的要求，
 * 仍然 7/7 失败——说明**光靠提示词消不掉它**，必须在解析侧容错。
 *
 * <h2>策略</h2>
 * 按 {@code descriptor.getParamClass()} 的**真实字段声明**驱动，而不是硬编码字段名：
 * <ol>
 *   <li><b>解嵌套</b>：某个声明字段的值是对象、且该对象里出现了**其它声明字段名**，
 *       说明模型把整份参数错位地包进了这个字段——把内层字段提到顶层；</li>
 *   <li><b>收形状</b>：声明为 {@code List<String>} 的字段若拿到对象/单值，收成文本数组（保信息、不丢内容）；</li>
 *   <li><b>纠字面量</b>：声明为 {@code Boolean} 的字段若拿到 {@code "true"}/{@code "false"} 文本，还原为布尔。</li>
 * </ol>
 * 只在形状确实与声明不符时才改写（合法输入逐字原样返回），只做保信息的收窄，不臆造结论。
 */
public final class ToolParamRepair {
    private static final Logger log = LoggerFactory.getLogger(ToolParamRepair.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private ToolParamRepair() {
    }

    /**
     * 归一化某次工具调用的参数字符串。无法解析、或参数类不可用时原样返回
     * （交给 agent4j 报它自己的错，不掩盖真正的畸形）。
     *
     * @param paramClass {@code descriptor.getParamClass()}；为 null 时只做语法层面的原样透传
     */
    public static String repair(String toolName, String paramJson, Class<?> paramClass) {
        if (paramJson == null || paramJson.isBlank() || paramClass == null) return paramJson;
        ObjectNode root;
        try {
            JsonNode tree = JSON.readTree(paramJson);
            if (!tree.isObject()) return paramJson;
            root = (ObjectNode) tree;
        } catch (Exception notJson) {
            return paramJson;
        }
        try {
            List<Field> fields = declaredFields(paramClass);
            Set<String> names = new LinkedHashSet<>();
            for (Field field : fields) names.add(field.getName());

            boolean changed = unwrapMisplacedPayload(toolName, root, names);
            for (Field field : fields) {
                changed |= coerceField(toolName, root, field);
            }
            return changed ? root.toString() : paramJson;
        } catch (Exception unexpected) {
            // 归一化本身绝不能成为新的失败点：出任何意外都退回原值，让原有错误路径照旧
            log.warn("工具 {} 的参数归一化失败，按原值透传：{}", toolName, unexpected.toString());
            return paramJson;
        }
    }

    /**
     * 解嵌套：某个声明字段的值是对象、且对象里出现了其它声明字段名时，认为整份参数被错位包进了它。
     *
     * <p>实测形状 {@code {"issues":{"suggestions":[…],"summary":"…","passed":false,"issues":[…]}}}：
     * 顶层只有 {@code issues}，内层才是完整参数。把内层字段提到顶层（不覆盖已有的），
     * 并用内层同名值替换该字段本身。
     *
     * <p>注意与「数组写成映射」区分：{@code {"issues":{"1":"文本"}}} 的键是序号、不含任何声明字段名，
     * 因此不会被误判成嵌套，而是走 {@link #coerceField} 收成数组。
     */
    private static boolean unwrapMisplacedPayload(String toolName, ObjectNode root, Set<String> declared) {
        boolean changed = false;
        for (String field : new ArrayList<>(declared)) {
            JsonNode value = root.get(field);
            if (value == null || !value.isObject()) continue;
            if (!containsAnyField(value, declared)) continue;
            for (String inner : declared) {
                if (inner.equals(field)) continue;
                JsonNode lifted = value.get(inner);
                if (lifted == null || lifted.isNull()) continue;
                JsonNode existing = root.get(inner);
                if (existing != null && !existing.isNull()) continue;   // 顶层已有，不覆盖
                root.set(inner, lifted);
                changed = true;
                log.warn("工具 {} 的参数把 {} 嵌套进了 {}，已提到顶层：{}",
                        toolName, inner, field, brief(lifted.toString()));
            }
            JsonNode self = value.get(field);
            if (self != null && !self.isNull()) {
                root.set(field, self);
            } else {
                root.remove(field);
            }
            changed = true;
        }
        return changed;
    }

    /** 对象里是否出现了任一「其它声明字段名」。 */
    private static boolean containsAnyField(JsonNode object, Set<String> declared) {
        for (String name : declared) {
            if (object.has(name)) return true;
        }
        return false;
    }

    /** 按声明类型收形状：{@code List<String>} 收成文本数组，{@code Boolean} 还原文本布尔。 */
    private static boolean coerceField(String toolName, ObjectNode root, Field field) {
        JsonNode value = root.get(field.getName());
        if (value == null || value.isNull()) return false;
        Class<?> type = field.getType();
        if (List.class.isAssignableFrom(type) && isTextualElement(field)) {
            if (value.isArray() && isAllTextual(value)) return false;   // 已符合声明
            ArrayNode coerced = toTextualArray(value);
            root.set(field.getName(), coerced);
            log.warn("工具 {} 的参数 {} 形状与声明 List<String> 不符，已归一化为文本数组：{} → {}",
                    toolName, field.getName(), brief(value.toString()), brief(coerced.toString()));
            return true;
        }
        if (type == Boolean.class || type == boolean.class) {
            if (!value.isTextual()) return false;
            String text = value.asText("").trim().toLowerCase(java.util.Locale.ROOT);
            if (!text.equals("true") && !text.equals("false")) return false;
            root.put(field.getName(), Boolean.parseBoolean(text));
            log.warn("工具 {} 的参数 {} 声明为布尔但收到文本 {}，已还原为布尔值",
                    toolName, field.getName(), text);
            return true;
        }
        return false;
    }

    /** 元素类型是 String（或未指定泛型）的集合——只有这种才按「文本数组」收。 */
    private static boolean isTextualElement(Field field) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType parameterized)) return true;   // 裸 List：按文本处理
        Type[] args = parameterized.getActualTypeArguments();
        if (args.length != 1) return true;
        Type element = args[0];
        if (element instanceof Class<?> clazz) return clazz == String.class || clazz == Object.class;
        return false;
    }

    private static List<Field> declaredFields(Class<?> paramClass) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = paramClass; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (field.isSynthetic()) continue;
                fields.add(field);
            }
        }
        return fields;
    }

    private static boolean isAllTextual(JsonNode array) {
        for (JsonNode item : array) {
            if (item == null || item.isNull()) continue;
            if (item.isObject() || item.isArray()) return false;
        }
        return true;
    }

    /**
     * 把任意形状收成 JSON 文本数组（保信息、不丢内容）：
     * <ul>
     *   <li>数组 → 逐项取文本；对象项取其第一个非空文本值；</li>
     *   <li>单值 → 单元素数组；</li>
     *   <li>对象 → 逐键取文本；键是**纯数字**（{@code {"1":"文本"}} 这种序号映射）或值为空时只取文本，
     *       否则取 {@code "键：值"}，这样 {@code {"<某句问题描述>":""}} 这种错位也能还原出原文。</li>
     * </ul>
     */
    private static ArrayNode toTextualArray(JsonNode value) {
        ArrayNode out = JSON.createArrayNode();
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (item == null || item.isNull()) continue;
                String text = textOf(item);
                if (!text.isBlank()) out.add(text);
            }
            return out;
        }
        if (value.isObject()) {
            java.util.Iterator<String> keys = value.fieldNames();
            while (keys.hasNext()) {
                String key = keys.next();
                String text = textOf(value.get(key));
                if (text.isBlank()) {
                    if (!key.isBlank()) out.add(key);
                } else if (isNumericKey(key)) {
                    out.add(text);
                } else {
                    out.add(key + "：" + text);
                }
            }
            return out;
        }
        String single = textOf(value);
        if (!single.isBlank()) out.add(single);
        return out;
    }

    private static boolean isNumericKey(String key) {
        if (key == null || key.isEmpty()) return false;
        for (int index = 0; index < key.length(); index++) {
            if (!Character.isDigit(key.charAt(index))) return false;
        }
        return true;
    }

    /** 取节点文本：对象取其第一个非空文本字段值（保内容），数组递归用分号拼接。 */
    private static String textOf(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isObject()) {
            java.util.Iterator<String> keys = node.fieldNames();
            while (keys.hasNext()) {
                String inner = textOf(node.get(keys.next()));
                if (!inner.isBlank()) return inner;
            }
            return "";
        }
        if (node.isArray()) {
            StringBuilder joined = new StringBuilder();
            for (JsonNode item : node) {
                String inner = textOf(item);
                if (inner.isBlank()) continue;
                if (joined.length() > 0) joined.append("；");
                joined.append(inner);
            }
            return joined.toString();
        }
        return node.asText("").replaceAll("\\s+", " ").trim();
    }

    private static String brief(String text) {
        if (text == null) return "null";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 160 ? flat.substring(0, 160) + "…" : flat;
    }
}
