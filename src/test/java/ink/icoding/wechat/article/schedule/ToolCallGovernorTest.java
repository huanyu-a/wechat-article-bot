package ink.icoding.wechat.article.schedule;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具调用治理单测（Phase 4）：只读去重、循环检测、预算提示。
 *
 * <p>依据（{@code docs/dev/known-issues-handoff.md} 与运行历史）：run#46/#62/#85/#89 都是
 * 「同一工具、同一参数、反复调用」把预算一路烧完的形态，而此前的
 * {@code ToolMutationDeduplicator} 只覆盖**有副作用**的工具（生图/导入/改图），
 * 对只读检索完全没有约束。
 */
class ToolCallGovernorTest {

    // ==================== 只读去重 ====================

    /**
     * 同参数重复调用只读工具：不重复请求上游，直接返回首次结果 + 缓存标记。
     *
     * <p>断言的关键是 {@code calls} 保持 1——省掉的正是那一次真实网络往返与上游延迟。
     */
    @Test
    void repeatedReadOnlyCallReusesFirstResultWithoutHittingUpstream() {
        ToolCallGovernor governor = new ToolCallGovernor();
        AtomicInteger calls = new AtomicInteger();

        String first = governor.execute("search_web", "{\"query\":\"AI\"}",
                () -> json(calls.incrementAndGet()));
        String second = governor.execute("search_web", "{\"query\":\"AI\"}",
                () -> json(calls.incrementAndGet()));

        assertThat(calls).as("第二次同参数调用不该再打上游").hasValue(1);
        assertThat(second).contains("\"cached\": true").contains(json(1));
    }

    /** 参数不同就是不同请求：换关键词检索是正常工作量，必须照常执行。 */
    @Test
    void differentParametersAreExecutedNormally() {
        ToolCallGovernor governor = new ToolCallGovernor();
        AtomicInteger calls = new AtomicInteger();

        governor.execute("search_web", "{\"query\":\"AI\"}", () -> json(calls.incrementAndGet()));
        String second = governor.execute("search_web", "{\"query\":\"大模型\"}",
                () -> json(calls.incrementAndGet()));

        assertThat(calls).hasValue(2);
        assertThat(second).doesNotContain("\"cached\": true");
    }

    /**
     * 字段顺序不同的同一参数视为同一调用。
     *
     * <p>模型完全可能把 {@code {"query":"AI","maxResults":5}} 写成
     * {@code {"maxResults":5,"query":"AI"}}，不归一化的话去重与循环检测会双双失效——
     * 而这两个机制的价值恰恰在于识别「换个写法、其实一样」的重复。
     */
    @Test
    void fieldOrderDoesNotDefeatDeduplication() {
        ToolCallGovernor governor = new ToolCallGovernor();
        AtomicInteger calls = new AtomicInteger();

        governor.execute("search_web", "{\"query\":\"AI\",\"maxResults\":5}",
                () -> json(calls.incrementAndGet()));
        governor.execute("search_web", "{ \"maxResults\" : 5 , \"query\" : \"AI\" }",
                () -> json(calls.incrementAndGet()));

        assertThat(calls).hasValue(1);
    }

    /** 有副作用的工具绝不缓存：重复生图/导入就是重复计费。 */
    @Test
    void mutatingToolsAreNeverCached() {
        ToolCallGovernor governor = new ToolCallGovernor();
        AtomicInteger calls = new AtomicInteger();

        governor.execute("generate_image", "{\"prompt\":\"猫\"}", () -> json(calls.incrementAndGet()));
        governor.execute("generate_image", "{\"prompt\":\"猫\"}", () -> json(calls.incrementAndGet()));

        assertThat(calls).hasValue(2);
    }

    /** 只读工具清单必须覆盖四类检索；{@code read_article_draft} 有意不在其中（返回值随草稿变化）。 */
    @Test
    void readOnlyToolSetCoversEverySearchToolButNotDraftRead() {
        assertThat(ToolCallGovernor.READ_ONLY_TOOLS).containsExactlyInAnyOrder(
                "search_web", "browse_webpage", "search_web_images", "list_image_assets");
        assertThat(ToolCallGovernor.isReadOnly("read_article_draft"))
                .as("草稿读取的返回值随写工具变化，缓存它会让模型基于过期状态决策")
                .isFalse();
        assertThat(ToolCallGovernor.isReadOnly(null)).isFalse();
    }

    // ==================== 循环检测 ====================

    /**
     * 超过提示阈值后注入**可执行**的收手指令。
     *
     * <p>依据：实测（run#62）模型读到「已中止」后并不明白该收手，又连调 7 次工具。
     * 因此提示必须给出下一步该做什么（提交成果 / 换关键词），而不是只说「不许重复」。
     *
     * <p>计数由**运行器**做（{@code noteCall}），工具侧只负责取提示——这里用
     * {@link #callLikeRunner} 复刻运行器的「先计数、再执行」顺序。
     */
    @Test
    void repeatedCallsGetAnActionableWrapUpInstruction() {
        ToolCallGovernor governor = new ToolCallGovernor();
        String param = "{\"query\":\"AI\"}";

        for (int index = 0; index <= ToolCallGovernor.REPEAT_HINT_THRESHOLD; index++) {
            callLikeRunner(governor, "search_web", param);
        }

        String hinted = callLikeRunner(governor, "search_web", param);
        assertThat(hinted)
                .contains("完全相同的参数")
                .contains("save_research_notes")
                .as("提示必须可执行：告诉模型接下来做什么，而不是只说「不许重复」")
                .contains("换一个不同的关键词");
    }

    /** 未超过阈值时不打扰模型（前两次调用是正常检索，第二次可能只是没意识到参数一样）。 */
    @Test
    void firstCallsGetNoHint() {
        ToolCallGovernor governor = new ToolCallGovernor();
        String param = "{\"query\":\"AI\"}";

        assertThat(callLikeRunner(governor, "search_web", param)).doesNotContain("完全相同的参数");
        assertThat(callLikeRunner(governor, "search_web", param)).doesNotContain("完全相同的参数");
    }

    /** 复刻运行器的调用顺序：先 noteCall（计数/循环判定），再走工具侧的 execute。 */
    private static String callLikeRunner(ToolCallGovernor governor, String toolName, String paramJson) {
        governor.noteCall(toolName, paramJson);
        return governor.execute(toolName, paramJson, () -> "结果");
    }

    /**
     * 重复到中止阈值即判为无进展循环。
     *
     * <p>提示阈值（2）之后模型仍有 4 次机会自行纠正；到 6 次还在重复同一参数，说明已经进入循环。
     */
    @Test
    void repeatedCallsPastAbortThresholdAreReportedAsNoProgress() {
        ToolCallGovernor governor = new ToolCallGovernor();
        String param = "{\"query\":\"AI\"}";

        for (int index = 0; index < ToolCallGovernor.NO_PROGRESS_ABORT_THRESHOLD - 1; index++) {
            assertThat(governor.noteCall("search_web", param))
                    .as("第 %d 次调用尚未达到中止阈值", index + 1)
                    .isFalse();
        }
        assertThat(governor.noteCall("search_web", param))
                .as("第 %d 次同参数调用应判为无进展", ToolCallGovernor.NO_PROGRESS_ABORT_THRESHOLD)
                .isTrue();
        assertThat(governor.noProgressReason("search_web", param))
                .contains("陷入循环")
                .contains("已有成果请在下次运行中提交");
    }

    /** 有副作用的工具不参与循环检测：重复调用由 ToolMutationDeduplicator 拦，判据不同。 */
    @Test
    void mutatingToolsAreExemptFromLoopDetection() {
        ToolCallGovernor governor = new ToolCallGovernor();
        String param = "{\"prompt\":\"猫\"}";

        for (int index = 0; index < ToolCallGovernor.NO_PROGRESS_ABORT_THRESHOLD; index++) {
            assertThat(governor.noteCall("generate_image", param)).isFalse();
        }
        assertThat(governor.noProgressReason("generate_image", param)).isNull();
    }

    // ==================== 预算提示 ====================

    /**
     * 剩余额度不多时，工具结果里带一句剩余次数，让模型自己安排收尾。
     *
     * <p>依据：{@code AgentProtocols.RESEARCH} 里事先告知预算后，调研阶段不再「先搜满再交简报」。
     * 把这条信息放进**每次都能看到的工具结果**比只写在系统提示里更有效。
     */
    @Test
    void budgetHintAppearsOnlyWhenRemainingIsLow() {
        assertThat(ToolCallGovernor.budgetHint(ToolCallGovernor.BUDGET_HINT_REMAINING + 1)).isNull();
        assertThat(ToolCallGovernor.budgetHint(ToolCallGovernor.BUDGET_HINT_REMAINING))
                .contains("剩余工具调用额度：" + ToolCallGovernor.BUDGET_HINT_REMAINING);
        assertThat(ToolCallGovernor.budgetHint(1)).contains("剩余工具调用额度：1 次");
        // 已用尽时不提示：此时除了收尾工具都会被拒，提示「优先提交成果」已无意义
        assertThat(ToolCallGovernor.budgetHint(0)).isNull();
        assertThat(ToolCallGovernor.budgetHint(-1)).isNull();
    }

    /** 预算计数由运行器喂入：每次尝试重置，剩余额度随之递减。 */
    @Test
    void remainingBudgetTracksTheRunnerCounter() {
        ToolCallGovernor governor = new ToolCallGovernor();
        governor.beginSession(10);
        assertThat(governor.remainingBudget()).isEqualTo(10);

        governor.countCall();
        governor.countCall();
        assertThat(governor.remainingBudget()).isEqualTo(8);

        // 重试会重建会话：上一轮用量不该延续到新会话（否则重试一开始就显示「剩余 0 次」）
        governor.beginSession(10);
        assertThat(governor.remainingBudget()).isEqualTo(10);
    }

    /** 未设上限（≤0 表示不限制）时永不提示。 */
    @Test
    void unlimitedBudgetNeverHints() {
        ToolCallGovernor governor = new ToolCallGovernor();
        governor.beginSession(0);
        governor.countCall();

        assertThat(governor.remainingBudget()).isEqualTo(Integer.MAX_VALUE);
    }

    private static String json(int value) {
        return "{\"value\":" + value + "}";
    }
}
