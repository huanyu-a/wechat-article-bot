package ink.icoding.wechat.article.agent;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.wechat.article.ai.TaskWorkspaceTools.SubmitReviewParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 参数形状归一化的回归测试。全部样本取自**真实运行日志**（run#129 审核阶段 7 次
 * {@code submit_review} 全失败，把整个审核阶段烧到 300 秒硬超时）。
 *
 * <p>判据不是「归一化后的 JSON 长什么样」，而是<b>它能不能被 agent4j 真正解析</b>——
 * 因为缺陷的本质就是 {@code ToolParam.fromJsonString} 抛异常
 * （{@code Failed to parse tool param JSON: <原文>}），一旦抛了就没有第二次机会。
 * 所以每个畸形样本都用真实的 {@code SubmitReviewParam} 过一遍解析。
 */
class ToolParamRepairTest {

    /** 畸形样本必须能解析成功——这是修复的全部目的。 */
    private static SubmitReviewParam parseAfterRepair(String malformed) {
        String repaired = ToolParamRepair.repair("submit_review", malformed, SubmitReviewParam.class);
        return ToolParam.fromJsonString(repaired, SubmitReviewParam.class);
    }

    /**
     * 形状一：把整份结论原封不动嵌套进 {@code issues}（run#129 第 1/2/3 次尝试，日志 L85/L87/L89）。
     * 内层才是完整参数，必须把内层字段提到顶层。
     */
    @Test
    void liftsWholePayloadMisplacedInsideIssuesField() {
        String malformed = "{\"issues\":{\"suggestions\":[\"建议撰稿人确认是否执行过 save_article_draft\"],"
                + "\"summary\":\"草稿不存在：read_article_draft 两次读取均返回空内容\","
                + "\"passed\":false,\"issues\":[\"草稿为空，无法审核\"]}}";

        SubmitReviewParam parsed = parseAfterRepair(malformed);

        assertThat(parsed.getPassed()).as("嵌套里的 passed 必须被提到顶层").isFalse();
        assertThat(parsed.getIssues()).containsExactly("草稿为空，无法审核");
        assertThat(parsed.getSuggestions()).containsExactly("建议撰稿人确认是否执行过 save_article_draft");
        assertThat(parsed.getSummary()).contains("草稿不存在");
    }

    /**
     * 形状二：数组写成「序号 → 文本」的映射（run#129 第 6 次尝试，日志 L93）。
     * 键是纯数字时只取文本，不能把序号拼进结论里。
     */
    @Test
    void turnsNumericKeyedMapIntoTextArray() {
        SubmitReviewParam parsed = parseAfterRepair(
                "{\"issues\":{\"1\":\"草稿为空，无法审核：全文从未保存或保存失败。\"},"
                        + "\"passed\":false,\"suggestions\":{\"1\":\"退回撰稿环节\",\"2\":\"重新送审时逐条核对\"}}");

        assertThat(parsed.getPassed()).isFalse();
        assertThat(parsed.getIssues()).containsExactly("草稿为空，无法审核：全文从未保存或保存失败。");
        assertThat(parsed.getSuggestions()).containsExactly("退回撰稿环节", "重新送审时逐条核对");
    }

    /**
     * 形状三：{@code issues} 与 {@code suggestions} 整体写成对象，且键是<b>一整句问题描述</b>、
     * 值为空（run#129 日志 L97）。这种必须保住原文——键本身就是结论。
     */
    @Test
    void keepsSentenceKeyWhenValueIsBlank() {
        SubmitReviewParam parsed = parseAfterRepair(
                "{\"issues\":{\"草稿为空，无法审核：文章从未保存，全文与配图缺失。\":\"\"},"
                        + "\"passed\":false,"
                        + "\"suggestions\":{\"排查 save_article_draft 是否执行。\":\"\"},"
                        + "\"summary\":\"草稿为空，无法审核，判不通过。\"}");

        assertThat(parsed.getIssues()).containsExactly("草稿为空，无法审核：文章从未保存，全文与配图缺失。");
        assertThat(parsed.getSuggestions()).containsExactly("排查 save_article_draft 是否执行。");
        assertThat(parsed.getSummary()).isEqualTo("草稿为空，无法审核，判不通过。");
    }

    /** 文本布尔必须还原：{@code "passed":"false"} 会被 Jackson 判为类型不符。 */
    @Test
    void restoresTextualBoolean() {
        SubmitReviewParam parsed = parseAfterRepair(
                "{\"passed\":\"false\",\"issues\":[\"实质问题\"],\"summary\":\"不通过\"}");

        assertThat(parsed.getPassed()).isFalse();
        assertThat(parsed.getIssues()).containsExactly("实质问题");
    }

    /**
     * 合法输入必须**逐字原样返回**——归一化只在形状确实与声明不符时才动手，
     * 否则会把模型本来正确的参数改坏（这是「声明与真实列必须同向」的同一纪律）。
     */
    @Test
    void leavesValidInputByteIdentical() {
        String valid = "{\"passed\":false,\"issues\":[\"问题一\",\"问题二\"],"
                + "\"suggestions\":[\"建议一\"],\"summary\":\"摘要\"}";

        assertThat(ToolParamRepair.repair("submit_review", valid, SubmitReviewParam.class))
                .as("已符合声明的输入不得被改写")
                .isEqualTo(valid);
    }

    /** 无法解析、或参数类不可用时原样透传：归一化自己绝不能成为新的失败点。 */
    @Test
    void passesThroughUnrepairableInput() {
        // 非 JSON：原样返回，交给 agent4j 报它自己的错
        assertThat(ToolParamRepair.repair("submit_review", "not json at all", SubmitReviewParam.class))
                .isEqualTo("not json at all");
        // 参数类未知（ToolCallArgumentGuardTest 里的 new ToolDescriptor() 就是这种）：不猜、不改
        assertThat(ToolParamRepair.repair("submit_review", "{\"issues\":{\"1\":\"x\"}}", null))
                .isEqualTo("{\"issues\":{\"1\":\"x\"}}");
        // 空白与 null
        assertThat(ToolParamRepair.repair("submit_review", "   ", SubmitReviewParam.class)).isEqualTo("   ");
        assertThat(ToolParamRepair.repair("submit_review", null, SubmitReviewParam.class)).isNull();
        // JSON 数组（不是对象）：不适用，原样返回
        assertThat(ToolParamRepair.repair("submit_review", "[1,2]", SubmitReviewParam.class)).isEqualTo("[1,2]");
    }

    /**
     * 归一化必须**幂等**：修好的结果再修一次不得继续变化（否则重试路径会反复改写参数）。
     */
    @Test
    void repairIsIdempotent() {
        String malformed = "{\"issues\":{\"1\":\"草稿为空\"},\"passed\":\"false\"}";

        String once = ToolParamRepair.repair("submit_review", malformed, SubmitReviewParam.class);
        String twice = ToolParamRepair.repair("submit_review", once, SubmitReviewParam.class);

        assertThat(twice).isEqualTo(once);
        assertThatCode(() -> ToolParam.fromJsonString(twice, SubmitReviewParam.class))
                .doesNotThrowAnyException();
    }
}
