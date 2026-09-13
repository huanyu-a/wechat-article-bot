package ink.icoding.wechat.article.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置 MarkFlow 技能文案（{@link SkillSeeder#MARKFLOW_CONTENT}）的**源码侧**回归钉子。
 *
 * <p><b>为什么要有这条测试</b>：第九、十轮把「D43 / D44 / D45 要求的写法说明只在源码里、
 * 还没进数据库」记成了一处真实缺口（{@code docs/dev/known-issues-handoff.md} §3.13⑦）——
 * 它没有自动化证据，只能靠肉眼比对源码。这条测试把「源码确实写了这几段」变成可执行断言。
 *
 * <p><b>它断言的是源码常量，不是数据库</b>：{@code SKILL} 表里的内容是 {@code SkillSeeder}
 * 在**应用启动时**按 {@code builtin_key} upsert 进去的（§4.2 第 5 条），重启前库里一直是旧版。
 * 因此本类**不启动 Spring、不连数据库**：库里的内容比源码旧是**预期状态**，
 * 这条测试用它来判失败就是把断言对象搞错了。库 ↔ 源码的一致性由 §八 第 7 条那组只读命令复核。
 *
 * <p><b>覆盖的三段（对应 D44 / D43 / D45）</b>：
 * <ol>
 *   <li>{@code layout-*} 家族禁写名单（D44：全族 38 个名字 / 76 组实测全部上游未渲染）；</li>
 *   <li>{@code case-flow} 每行必须「行首 {@code -} + {@code [标签]}」（D43：五种写法整块归零）；</li>
 *   <li>{@code :::hint} 只有容器写法、标签式不认（D45：{@code CONTAINER_ONLY_AS_TAG} 名单 6→7）。</li>
 * </ol>
 * 断言里同时钉住**数字**（38 / 76 / 五种）——这些数字都是实测值，改文案时不能顺手改小。
 */
class SkillSeederMarkflowContentTest {

    /** 库里旧版（D43/D44/D45 之前）的字符数是 5635；源码新版必须显著更长，否则说明改的是库不是源码。 */
    private static final int LEGACY_DB_LENGTH = 5635;
    /** 2026-09-13 第十一轮实测：当前源码常量 {@code String.length()} = 6649（库里应为同一值，见 §八 第 7 条）。 */
    private static final int CURRENT_SOURCE_LENGTH = 6649;
    private static final int MIN_EXPECTED_LENGTH = 6200;
    private static final int MAX_EXPECTED_LENGTH = 7500;

    @Test
    @DisplayName("文案长度是 D43/D44/D45 之后的版本，而不是库里那份旧文案")
    void contentIsTheExpandedVersion() {
        String content = SkillSeeder.MARKFLOW_CONTENT;

        assertThat(content).isNotNull().isNotBlank();
        assertThat(content.length())
                .as("源码文案长度应在 [%d, %d]（第十一轮实测 %d）；库里旧版是 %d 字符，"
                        + "若这里落在旧值附近，说明断言的是库里的内容而不是源码",
                        MIN_EXPECTED_LENGTH, MAX_EXPECTED_LENGTH, CURRENT_SOURCE_LENGTH, LEGACY_DB_LENGTH)
                .isBetween(MIN_EXPECTED_LENGTH, MAX_EXPECTED_LENGTH);
        assertThat(content.length())
                .as("必须明显长于库里旧版（%d 字符）", LEGACY_DB_LENGTH)
                .isGreaterThan(LEGACY_DB_LENGTH);
        // 段落数目：1–5 条 + 主题色策略，缺任何一段都说明文案被截断过
        assertThat(content).contains("1. 标题层级").contains("2. 组件以系统提示")
                .contains("5. 文末以一句与主题相关的收束语自然结束")
                .contains("主题色策略").contains("accentMode");
    }

    @Test
    @DisplayName("D44：layout-* 家族禁写名单在文案里，且带全族实测数字")
    void layoutFamilyIsBannedWithFullFamilyEvidence() {
        String content = SkillSeeder.MARKFLOW_CONTENT;

        assertThat(content)
                .as("必须有明确的禁写表述与家族通配")
                .contains("禁止写").contains("`layout-*` 家族")
                .as("容器式与标签式两种写法都要点名")
                .contains(":::layout-hero").contains("<layout-hero>")
                .contains(":::layout-toc").contains(":::layout-metrics")
                .as("代表名字至少要覆盖 registry 的多个 category")
                .contains("layout-cards").contains("layout-steps").contains("layout-timeline")
                .as("第十轮实测是全族 38 个名字 / 76 组，文案里的数字必须与此一致")
                .contains("38 个名字").contains("76 组")
                .as("必须说明失败形态是「原样留在产物里」而不是「报错」")
                .contains("原样留在产物里")
                .as("必须给出替代写法的指引（⑥ 那一节）")
                .contains("对应的版式按 ⑥ 换写法");
    }

    @Test
    @DisplayName("D43：case-flow 每行必须「行首的 - + [标签]」，且注明五种归零写法")
    void caseFlowDashRuleIsDocumented() {
        String content = SkillSeeder.MARKFLOW_CONTENT;

        assertThat(content)
                .as("必须点名容器式与行格式")
                .contains(":::case-flow").contains("[案例 01]")
                .as("必须写死「行首的 -」这条规则")
                .contains("行首的 `-`").contains("[标签]")
                .as("必须点出反例：列表符号与缺标签")
                .contains("`*` / `+` / `1.` 列表").contains("五种写法整块都是 0 字符")
                .as("必须说明后果是静默归零（上游不报错）")
                .contains("整块都是 0 字符").contains("上游不报错");
    }

    @Test
    @DisplayName("D45：:::hint 只有容器写法，标签式会原样透传")
    void hintIsContainerOnly() {
        String content = SkillSeeder.MARKFLOW_CONTENT;

        assertThat(content)
                .as("必须给容器式写法与它的渲染效果")
                .contains(":::hint").contains("蓝色信息卡")
                .as("必须点明标签式不被识别、且失败形态是元素透传（屏幕上不可见）")
                .contains("<hint>").contains("只有容器写法")
                .contains("未知元素原样留在产物里");
    }

    @Test
    @DisplayName("D45 的另一半：<slider> 必须配对闭合，且与 <badge>/<icon> 方向相反")
    void sliderClosingRuleIsDocumented() {
        String content = SkillSeeder.MARKFLOW_CONTENT;

        assertThat(content)
                .as("自闭合 <slider /> 必须被点名")
                .contains("<slider … />")
                .as("必须写清方向与 ③ 相反，避免模型把自闭合规则当通用规则")
                .contains("方向相反")
                .contains("`<badge>` / `<icon>`");
    }
}
