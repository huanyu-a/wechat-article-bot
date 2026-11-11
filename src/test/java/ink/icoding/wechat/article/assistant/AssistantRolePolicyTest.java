package ink.icoding.wechat.article.assistant;

import ink.icoding.wechat.article.agent.AgentFactory;
import ink.icoding.wechat.article.agent.AgentProtocols;
import ink.icoding.wechat.article.agent.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局助手角色裁剪矩阵纯单测（docs/dev/global-assistant-plan.md §4.3 / §7.2）。
 *
 * <p>不启 Spring 上下文：被测对象是纯静态函数，装配点（AssistantService）才持有 Spring 依赖。
 * 这里固化的是**授权表本身**，改动它必须同时改本测试——这正是我们要的行为。
 */
class AssistantRolePolicyTest {

    /** 四组的「注册表声明顺序」基准：策略输出必须跟注册表顺序一致，而不是各角色各排一套。 */
    private static final List<String> ALL_FOUR = List.of(
            ToolRegistry.ASSISTANT_READ, ToolRegistry.ASSISTANT_ARTICLE_WRITE,
            ToolRegistry.ASSISTANT_TASK, ToolRegistry.ASSISTANT_MODEL);

    // ---------------------------------------------------------------- groupsFor：五个角色

    @Test
    void adminGetsAllFourGroupsInRegistryOrder() {
        assertThat(AssistantRolePolicy.groupsFor("ADMIN")).containsExactlyElementsOf(ALL_FOUR);
    }

    @Test
    void operatorGetsEverythingExceptModel() {
        // 模型档案是 ADMIN 专属：改默认/兜底档案会影响所有人的出稿质量，不该由运营决定
        assertThat(AssistantRolePolicy.groupsFor("OPERATOR")).containsExactly(
                ToolRegistry.ASSISTANT_READ, ToolRegistry.ASSISTANT_ARTICLE_WRITE, ToolRegistry.ASSISTANT_TASK);
    }

    @Test
    void editorGetsReadAndArticleWriteOnly() {
        assertThat(AssistantRolePolicy.groupsFor("EDITOR")).containsExactly(
                ToolRegistry.ASSISTANT_READ, ToolRegistry.ASSISTANT_ARTICLE_WRITE);
    }

    /**
     * REVIEWER / VIEWER 得到空集（不是只读组）。
     *
     * <p>与 {@code /api/assistant/chat} 的 {@code hasAnyRole('ADMIN','OPERATOR','EDITOR')} 闸门同口径
     * （plan §4.1 与 §4.3）：闸门既已拒绝它们发起对话，授权表就不得比闸门更宽——
     * 否则一旦将来放宽闸门，这里会静默变成一次提权。
     */
    @Test
    void reviewerAndViewerGetNoGroupsBecauseTheChatGateDeniesThem() {
        assertThat(AssistantRolePolicy.groupsFor("REVIEWER")).isEmpty();
        assertThat(AssistantRolePolicy.groupsFor("VIEWER")).isEmpty();
    }

    // ---------------------------------------------------------------- groupsFor：fail-closed

    /**
     * 未知角色 fail-closed：空列表而不是全集。
     *
     * <p>断言「等于空列表」而不是「不含写组」是刻意的：只要还有任何一个组放出去，
     * 拼错角色名就可能换来一项能力（如 ASSISTANT_TASK 能建定时任务）。一个都不给才是唯一安全的下界。
     */
    @Test
    void unknownAndNullOrBlankRolesGetNoTools() {
        assertThat(AssistantRolePolicy.groupsFor(null)).isEmpty();
        assertThat(AssistantRolePolicy.groupsFor("")).isEmpty();
        assertThat(AssistantRolePolicy.groupsFor("   ")).isEmpty();
        assertThat(AssistantRolePolicy.groupsFor("ADMINN")).isEmpty();   // 拼错
        assertThat(AssistantRolePolicy.groupsFor("SUPERUSER")).isEmpty(); // 未登记
        assertThat(AssistantRolePolicy.groupsFor("ADMIN ")).isNotEmpty(); // 仅首尾空白不算未知
    }

    // ---------------------------------------------------------------- groupsFor：大小写与空白

    @Test
    void roleComparisonIgnoresCaseAndSurroundingWhitespace() {
        assertThat(AssistantRolePolicy.groupsFor("admin")).containsExactlyElementsOf(ALL_FOUR);
        assertThat(AssistantRolePolicy.groupsFor("  Admin\t")).containsExactlyElementsOf(ALL_FOUR);
        assertThat(AssistantRolePolicy.groupsFor("Operator")).containsExactly(
                ToolRegistry.ASSISTANT_READ, ToolRegistry.ASSISTANT_ARTICLE_WRITE, ToolRegistry.ASSISTANT_TASK);
        assertThat(AssistantRolePolicy.groupsFor("Editor")).containsExactly(
                ToolRegistry.ASSISTANT_READ, ToolRegistry.ASSISTANT_ARTICLE_WRITE);
        // viewer/REVIEWER 的空集不随大小写变化（归一化仍生效，只是结果本就为空）
        assertThat(AssistantRolePolicy.groupsFor("viewer")).isEmpty();
        assertThat(AssistantRolePolicy.groupsFor("  RevIEWER ")).isEmpty();
    }

    // ---------------------------------------------------------------- intersect

    /** 交集保持 declared 的顺序（不是角色策略的顺序）：定义里的声明顺序即期望的暴露顺序。 */
    @Test
    void intersectPreservesDeclaredOrderNotPolicyOrder() {
        List<String> declared = List.of(ToolRegistry.ASSISTANT_MODEL, ToolRegistry.ASSISTANT_TASK,
                ToolRegistry.ASSISTANT_READ);

        // 管理员四组全允许 → 顺序与 declared 完全一致（策略顺序是 READ/WRITE/TASK/MODEL）
        assertThat(AssistantRolePolicy.intersect(declared, "ADMIN")).containsExactly(
                ToolRegistry.ASSISTANT_MODEL, ToolRegistry.ASSISTANT_TASK, ToolRegistry.ASSISTANT_READ);

        // 运营只有 TASK + READ 可用 → 丢掉 MODEL，剩余两项仍按 declared 顺序
        assertThat(AssistantRolePolicy.intersect(declared, "OPERATOR")).containsExactly(
                ToolRegistry.ASSISTANT_TASK, ToolRegistry.ASSISTANT_READ);

        // 只读角色：闸门不允许发起对话，因此交集为空（与 groupsFor 同口径）
        assertThat(AssistantRolePolicy.intersect(declared, "VIEWER")).isEmpty();
    }

    /** 声明的全部是越权组 → 空集（而不是「原样返回」或「回落到全集」）。 */
    @Test
    void intersectDropsEveryGroupTheRoleMayNotUse() {
        List<String> declared = List.of(ToolRegistry.ASSISTANT_MODEL, ToolRegistry.ASSISTANT_TASK);
        assertThat(AssistantRolePolicy.intersect(declared, "EDITOR")).isEmpty();
        assertThat(AssistantRolePolicy.intersect(declared, "VIEWER")).isEmpty();
    }

    /** null 与未知组键被忽略：历史数据里的残留组键不该让整条助手链路不可用。 */
    @Test
    void intersectIgnoresNullAndUnknownKeys() {
        List<String> declared = Arrays.asList(null, "NOT_A_GROUP", ToolRegistry.ASSISTANT_READ,
                ToolRegistry.MEDIA, ToolRegistry.ASSISTANT_TASK);
        assertThat(AssistantRolePolicy.intersect(declared, "ADMIN")).containsExactly(
                ToolRegistry.ASSISTANT_READ, ToolRegistry.ASSISTANT_TASK);
        // 非助手组的合法组键（如 MEDIA）同样不在助手策略里，必须被丢掉
        assertThat(AssistantRolePolicy.intersect(declared, "ADMIN")).doesNotContain(ToolRegistry.MEDIA);
    }

    /** 边界：null / 空声明、未知角色一律空集，且不抛异常。 */
    @Test
    void intersectIsEmptyForNullDeclaredOrEmptyRole() {
        assertThat(AssistantRolePolicy.intersect(null, "ADMIN")).isEmpty();
        assertThat(AssistantRolePolicy.intersect(List.of(), "ADMIN")).isEmpty();
        assertThat(AssistantRolePolicy.intersect(List.of(ToolRegistry.ASSISTANT_READ), null)).isEmpty();
        assertThat(AssistantRolePolicy.intersect(List.of(ToolRegistry.ASSISTANT_READ), "SUPERUSER")).isEmpty();
    }

    /** declared 里的组键带空白：与角色名同一套规范化，避免 String 直接比较漏掉合法项。 */
    @Test
    void intersectTrimsDeclaredKeys() {
        assertThat(AssistantRolePolicy.intersect(List.of("  " + ToolRegistry.ASSISTANT_READ + " "), "EDITOR"))
                .containsExactly(ToolRegistry.ASSISTANT_READ);
    }

    // ---------------------------------------------------------------- 与注册表 / 装配的一致性

    /**
     * 策略表只可能收窄注册表声明的组：任何角色返回的组都必须存在于 {@code ASSISTANT_GROUPS}。
     *
     * <p>反过来不成立（注册表可以有组不被任何角色允许），这正是 fail-closed 的表现：
     * 以后往注册表加组，不会自动给某个角色放权。
     */
    @Test
    void everyReturnedGroupIsDeclaredInRegistry() {
        for (String role : List.of("ADMIN", "OPERATOR", "EDITOR")) {
            assertThat(AssistantRolePolicy.groupsFor(role))
                    .isNotEmpty()
                    .isSubsetOf(ToolRegistry.ASSISTANT_GROUPS);
        }
        // REVIEWER / VIEWER 是刻意的空集（见上一条用例），只校验「不会是全集」
        for (String role : List.of("REVIEWER", "VIEWER")) {
            assertThat(AssistantRolePolicy.groupsFor(role)).isEmpty();
        }
    }

    /** 装配链路打通：ASSISTANT stage 有协议、内置 code 常量存在、四组都在注册表里。 */
    @Test
    void assistantStageIsWiredThroughPublicSurface() {
        assertThat(AgentProtocols.byStage("ASSISTANT")).isEqualTo(AgentProtocols.ASSISTANT);
        // 未知 stage 仍回落 SCHEDULED_SINGLE（自定义 stage 可用这条老语义不能被助手阶段破坏）
        assertThat(AgentProtocols.byStage("CUSTOM_STAGE")).isEqualTo(AgentProtocols.SCHEDULED_SINGLE);

        assertThat(AgentProtocols.ASSISTANT)
                .contains("服务端数据库")   // 作用域声明：库，不是浏览器文档
                .contains("MARKFLOW")       // 渲染式排版拒写正文
                .contains("API Key")        // 密钥不进对话
                .contains("revision")       // 写工具必须带读到的版本
                .startsWith("你是墨舟的项目助手");

        assertThat(AgentFactory.CODE_ASSISTANT).isEqualTo("builtin_assistant");
        assertThat(ToolRegistry.ASSISTANT_GROUPS).containsExactlyElementsOf(ALL_FOUR);
    }
}