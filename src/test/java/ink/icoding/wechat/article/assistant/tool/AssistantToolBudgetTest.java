package ink.icoding.wechat.article.assistant.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具输出预算（{@link AssistantToolBudget}）。
 *
 * <p>这组用例的由来是一次真实故障：加入 34 个模型档案后，用户在助手问「当前有多少个模型」，
 * {@code list_llm_profiles} 返回 47 条全字段（14.8 KB）直接进上下文，上游随即
 * {@code HTTP 200, Socket closed}，整轮失败。也就是说，**没有预算的工具输出本身就是缺陷**，
 * 不该等到用户撞上才去猜「是不是模型不行」。
 */
class AssistantToolBudgetTest {

    private static List<Map<String, Object>> rows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", (long) i);
            row.put("name", "模型档案-" + i);
            rows.add(row);
        }
        return rows;
    }

    @Test
    void defaultLimitCutsAnUnboundedListDown() {
        Map<String, Object> page = AssistantToolBudget.page(rows(47), null, "模型档案");

        assertThat((List<?>) page.get("items")).hasSize(AssistantToolBudget.DEFAULT_LIMIT);
        assertThat(page.get("total")).isEqualTo(47);
        assertThat(page.get("returned")).isEqualTo(AssistantToolBudget.DEFAULT_LIMIT);
    }

    /**
     * 总数必须始终给出。
     *
     * <p>只返回 20 条却不给 total，模型会回答「你只有 20 个模型」——一个自信的错误答案，
     * 比直接报错更难发现。
     */
    @Test
    void totalIsAlwaysReportedEvenWhenTruncated() {
        Map<String, Object> page = AssistantToolBudget.page(rows(47), 5, "模型档案");

        assertThat(page.get("total")).isEqualTo(47);
        assertThat(page.get("returned")).isEqualTo(5);
        assertThat((String) page.get("truncationHint")).contains("47").contains("5");
    }

    /** 没超出上限时不出现提示语：没有截断还提示，会让模型以为数据不全。 */
    @Test
    void noHintWhenEverythingFits() {
        Map<String, Object> page = AssistantToolBudget.page(rows(3), 20, "模型档案");

        assertThat(page.get("total")).isEqualTo(3);
        assertThat(page).doesNotContainKey("truncationHint");
    }

    @Test
    void limitIsClampedIntoTheAllowedRange() {
        assertThat(AssistantToolBudget.clampLimit(null)).isEqualTo(AssistantToolBudget.DEFAULT_LIMIT);
        assertThat(AssistantToolBudget.clampLimit(0)).isEqualTo(1);
        assertThat(AssistantToolBudget.clampLimit(-7)).isEqualTo(1);
        assertThat(AssistantToolBudget.clampLimit(9_999)).isEqualTo(AssistantToolBudget.MAX_LIMIT);
    }

    /** 兜底：漏用 page 的工具也不会把超长字符串交给模型。 */
    @Test
    void capTruncatesOversizedOutputAndSaysHowToNarrowIt() {
        String huge = "x".repeat(AssistantToolBudget.MAX_CHARS + 500);

        String capped = AssistantToolBudget.cap(huge);

        assertThat(capped.length()).isGreaterThan(AssistantToolBudget.MAX_CHARS);
        assertThat(capped).contains("已截断").contains("缩小范围");
        assertThat(capped).startsWith("x".repeat(100));
    }

    @Test
    void capLeavesNormalOutputUntouched() {
        String small = "{\"count\":1}";

        assertThat(AssistantToolBudget.cap(small)).isEqualTo(small);
    }
}