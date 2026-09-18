package ink.icoding.wechat.article.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「工具推荐」技能包（{@link SkillSeeder#seeds()} 里 {@code tool_rec_*} 那 8 枚）的**源码侧**回归钉子。
 *
 * <p><b>这个包从哪来</b>：一份单文件模板库 {@code wechat-viral-structure/SKILL.md}（约 13k 字，
 * 模块化五环节 + 五套预设组合）被拆成 8 枚按维度可组合的内置技能，覆盖
 * 选题 → 标题 → 开头 → 中段 → 语言 → 结尾 → 摘要 → 合规。
 *
 * <p><b>为什么要拆</b>：{@link SkillPromptAssembler} 是按**维度**注入的，一整份 13k 字文档绑上去
 * 就是整份进 prompt；拆开后用户只吃自己绑的维度（例如只写快报文的人不必背上亲历测评的语气规范）。
 *
 * <p><b>为什么要有这条测试</b>：种子清单是「多枚技能 + 各自内容常量」的形态，最容易出的错不是编译失败，
 * 而是<b>清单与常量对不上</b>——加了一枚 seed 却忘了写常量（或反过来写了常量忘了登记）、
 * 两枚 seed 用了同一个 builtin_key（upsert 会互相覆盖、其中一枚静默消失）、
 * 或维度写错导致绑定校验/注入顺序失效。这些在源码里都看不出来，只能靠断言钉住。
 *
 * <p><b>断言的是源码常量，不是数据库</b>：{@code SKILL} 表的内容由 {@link SkillSeeder} 在应用启动时
 * 按 {@code builtin_key} upsert（见 {@link SkillSeederMarkflowContentTest} 的同类说明），
 * 本类不启动 Spring、不连数据库。
 */
class SkillSeederToolRecommendationTest {

    /** 本包全部 builtin_key，顺序即清单顺序。 */
    private static final List<String> PACKAGE_KEYS = List.of(
            "tool_rec_topic", "tool_rec_title", "tool_rec_opening", "tool_rec_playbook",
            "tool_rec_language", "tool_rec_ending", "tool_rec_digest", "tool_rec_compliance");

    /**
     * 本包内容合计的字符预算。单枚技能上限是
     * {@link SkillPromptAssembler#MAX_SKILL_CONTENT_LENGTH}（60000），但那是「把所有技能绑满」的
     * 极端值；一个包的合理体量应是它的零头——超了说明在往技能里堆本该属于文章的东西。
     */
    private static final int PACKAGE_BUDGET = 16000;

    private static List<SkillSeeder.Seed> pkg() {
        return SkillSeeder.seeds().stream()
                .filter(seed -> PACKAGE_KEYS.contains(seed.builtinKey()))
                .toList();
    }

    private static SkillSeeder.Seed seed(String builtinKey) {
        return SkillSeeder.seeds().stream()
                .filter(item -> item.builtinKey().equals(builtinKey))
                .findFirst()
                .orElseThrow(() -> new AssertionError("种子清单里缺少 " + builtinKey));
    }

    @Test
    @DisplayName("包内 8 枚技能齐备，且与清单一一对应（不多不少、无重复 key）")
    void packageIsCompleteAndKeyedUniquely() {
        List<SkillSeeder.Seed> seeds = pkg();

        assertThat(seeds).as("8 枚技能都必须在 seeds() 里登记").hasSize(PACKAGE_KEYS.size());
        assertThat(seeds).extracting(SkillSeeder.Seed::builtinKey)
                .as("builtin_key 是 upsert 的幂等键，重复会让其中一枚静默覆盖另一枚")
                .containsExactlyElementsOf(PACKAGE_KEYS);

        // builtin_key 与 name 都必须全局唯一（name 有库级唯一约束，重名会在启动时写库失败）
        List<SkillSeeder.Seed> all = SkillSeeder.seeds();
        assertThat(all).extracting(SkillSeeder.Seed::builtinKey).doesNotHaveDuplicates();
        assertThat(all).extracting(SkillSeeder.Seed::name).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("每枚技能都登记了非空内容常量与合规的 description")
    void everySeedHasContentAndDescription() {
        for (SkillSeeder.Seed seed : pkg()) {
            assertThat(seed.content())
                    .as("%s 的内容常量不得为空（漏写常量会在启动时把空内容 upsert 进库）", seed.builtinKey())
                    .isNotNull().isNotBlank();
            // 文本块总以换行结尾（组装时会 strip），这里只管开头：前导空行会让注入的段落标题与正文脱开
            assertThat(seed.content())
                    .as("%s 的内容不应以空行或空白开头", seed.builtinKey())
                    .doesNotStartWith("\n").doesNotStartWith(" ");
            assertThat(seed.description())
                    .as("%s 的 description 必填且不超过 500 字（SkillService 校验）", seed.builtinKey())
                    .isNotNull().isNotBlank();
            assertThat(seed.description().length())
                    .as("%s 的 description 超过 500 字会被 SkillService 拒绝", seed.builtinKey())
                    .isLessThanOrEqualTo(500);
        }
    }

    @Test
    @DisplayName("维度落在白名单内，且不占用 LAYOUT / IMAGE（排版与配图由既有技能承担）")
    void dimensionsAreValidAndDoNotCollideWithLayoutOrImage() {
        Map<String, String> dimensions = pkg().stream().collect(Collectors.toMap(
                SkillSeeder.Seed::builtinKey, SkillSeeder.Seed::dimension));

        assertThat(dimensions).containsExactlyInAnyOrderEntriesOf(Map.of(
                "tool_rec_topic", "TOPIC",
                "tool_rec_title", "TITLE",
                "tool_rec_opening", "OPENING",
                "tool_rec_playbook", "WRITING",
                "tool_rec_language", "LANGUAGE",
                "tool_rec_ending", "ENDING",
                "tool_rec_digest", "DIGEST",
                "tool_rec_compliance", "FACT_CHECK"));

        assertThat(dimensions.values()).allSatisfy(dimension ->
                assertThat(SkillDimensions.isValid(dimension))
                        .as("维度 %s 必须在白名单内，否则绑定与注入都会失效", dimension).isTrue());

        // LAYOUT 维度按优先级**单注入**（SkillPromptAssembler），再塞一枚只会与既有版式打架；
        // IMAGE 维度绑定侧只允许一枚（SkillBindingValidator），本包不该去占这两个维度。
        assertThat(dimensions.values())
                .as("本包不应新增 LAYOUT / IMAGE 技能")
                .doesNotContain("LAYOUT", "IMAGE");
        assertThat(pkg()).allSatisfy(seed ->
                assertThat(seed.engine()).as("%s 不是排版技能，engine 必须留空", seed.builtinKey()).isNull());
    }

    @Test
    @DisplayName("包内容合计在预算内，单枚也远低于 60000 上限")
    void packageStaysWithinBudget() {
        int total = pkg().stream().mapToInt(seed -> seed.content().length()).sum();

        assertThat(total)
                .as("本包内容合计 %d 字符，应在 %d 以内；超了说明在往技能里堆本该属于文章的东西",
                        total, PACKAGE_BUDGET)
                .isLessThanOrEqualTo(PACKAGE_BUDGET);
        assertThat(pkg()).allSatisfy(seed ->
                assertThat(seed.content().length())
                        .as("%s 单枚内容不得超过 %d", seed.builtinKey(),
                                SkillPromptAssembler.MAX_SKILL_CONTENT_LENGTH)
                        .isLessThanOrEqualTo(SkillPromptAssembler.MAX_SKILL_CONTENT_LENGTH));
    }

    @Test
    @DisplayName("选题技能：五套预设组合齐备，并给出读者维度的收窄规则")
    void topicSkillKeepsAllFivePlaybooks() {
        String content = seed("tool_rec_topic").content();

        assertThat(content)
                .as("五套预设组合是本包的核心资产，拆包时不得丢")
                .contains("快报型").contains("共鸣型").contains("评测型")
                .contains("升级型").contains("亲历测评型")
                .as("判定顺序必须显式写出，否则模型会先看读者再看目标")
                .contains("推荐什么").contains("这次要什么").contains("谁在读")
                .as("转发理由（转发 = 自我表达）是选题收敛的判据")
                .contains("转发");
    }

    @Test
    @DisplayName("标题技能：五类公式齐备，且钉住折叠线与「承诺必须兑现」")
    void titleSkillKeepsFiveFormulasAndFoldLineRule() {
        String content = seed("tool_rec_title").content();

        assertThat(content)
                .as("五类公式的名称必须都在")
                .contains("降维型").contains("提问型").contains("评测型")
                .contains("升级型").contains("亲历型")
                .as("折叠线（列表页只显示前十几个字）是公众号特有的硬约束")
                .contains("折叠线")
                .as("标题承诺必须与正文结构同型、且能兑现")
                .contains("兑现");
    }

    @Test
    @DisplayName("开头技能：五式齐备，且首屏折叠线规则覆盖全部五种结构")
    void openingSkillKeepsFiveStylesAndFoldLineRule() {
        String content = seed("tool_rec_opening").content();

        assertThat(content)
                .as("A1-A5 五种开头写法必须都在")
                .contains("A1").contains("A2").contains("A3").contains("A4").contains("A5")
                .contains("技术降维").contains("痛点提问").contains("叙事代入")
                .contains("感受描摹").contains("行业痛苦")
                .as("首屏折叠线是硬规则，且必须逐结构给出「折叠线内放什么」")
                .contains("折叠线")
                .as("原模板库只覆盖了 4 种结构的首屏规则，亲历测评型是补上的第 5 种")
                .contains("亲历测评型");
    }

    @Test
    @DisplayName("总纲：五环节节奏、信任/价值/顾虑三模块与视觉节奏规则齐备")
    void playbookKeepsFiveStageRhythmAndModules() {
        String content = seed("tool_rec_playbook").content();

        assertThat(content)
                .as("五环节是全文的骨架")
                .contains("痛点激活").contains("信任建立").contains("价值展示")
                .contains("顾虑消除").contains("行动触发")
                .as("B / C / D 三模块的代号必须保留，便于与预设组合对照")
                .contains("B1").contains("B2").contains("B3").contains("B4").contains("B5")
                .contains("C1").contains("C2").contains("C3").contains("C4").contains("C5")
                .contains("D1").contains("D2").contains("D3").contains("D4")
                .as("视觉锚点与段落呼吸是阅读完成率的来源，不能省")
                .contains("150-200 字").contains("300 字")
                .as("D1 预判质疑是必选项，工具推荐最常见的失败是只夸不答疑")
                .contains("必选");
    }

    @Test
    @DisplayName("语言技能：六类金句齐备，并含插入频率与「真话原则」")
    void languageSkillKeepsSixSentenceTypes() {
        String content = seed("tool_rec_language").content();

        assertThat(content)
                .as("六类金句是原模板库的金句系统")
                .contains("翻译型").contains("痛点型").contains("比喻型")
                .contains("认知型").contains("爽点型").contains("体感型")
                .as("插入频率是唯一可执行的量化口径")
                .contains("300-400 字")
                .as("主动写短板是工具推荐与软广的分界线")
                .contains("真话原则");
    }

    @Test
    @DisplayName("结尾技能：五式齐备，且评论区钩子覆盖全部结尾类型")
    void endingSkillKeepsFiveStylesAndCommentHooks() {
        String content = seed("tool_rec_ending").content();

        assertThat(content)
                .as("E1-E5 五种结尾必须都在")
                .contains("E1").contains("E2").contains("E3").contains("E4").contains("E5")
                .contains("人群匹配").contains("态度 + 行动").contains("反思定性")
                .contains("结构总结").contains("收益定性")
                .as("评论区钩子要按结尾类型配，不能只给一两条")
                .contains("评论区钩子")
                .contains("投票型").contains("分享型").contains("补充型").contains("认同型")
                .as("诱导分享是平台规则红线")
                .contains("诱导分享");
    }

    @Test
    @DisplayName("摘要技能：公式、字数区间与「必须含一条具体信息」齐备")
    void digestSkillKeepsFormulaAndLength() {
        String content = seed("tool_rec_digest").content();

        assertThat(content)
                .contains("40-80 字")
                .as("摘要要补足标题没说清的信息，而不是复述标题")
                .contains("不重复标题")
                .as("摘要同样要兑现承诺")
                .contains("兑现");
    }

    @Test
    @DisplayName("合规技能：绝对化用语、利益披露、时效核实、诱导分享四条红线齐备")
    void complianceSkillKeepsAllFourRedLines() {
        String content = seed("tool_rec_compliance").content();

        assertThat(content)
                .as("广告法绝对化用语是工具推荐的重灾区，必须点名并给出替代写法")
                .contains("绝对化用语").contains("最好").contains("国家级")
                .as("「免费」必须写明边界，否则是虚假宣传")
                .contains("免费")
                .as("利益关系披露（返佣/推广/邀请码）是硬性要求")
                .contains("利益关系披露").contains("返佣").contains("邀请码")
                .as("价格与版本属时效敏感信息，必须标注核实时间")
                .contains("核实时间")
                .as("竞品对比必须可验证，不得贬损竞品")
                .contains("竞品")
                .as("效果截图不得伪造、且要处理隐私信息")
                .contains("截图")
                .as("平台规则：禁诱导分享、禁破解版")
                .contains("诱导分享").contains("破解")
                .as("本条与通用事实核查基线是叠加关系，不是替代")
                .contains("事实核查基线");
    }

    @Test
    @DisplayName("包内技能与既有内置技能不重名、不抢占既有 builtin_key")
    void packageDoesNotCollideWithExistingBuiltins() {
        Set<String> existingKeys = Set.of(
                "default_layout", "minimal_layout", "magazine_layout", "markflow_default",
                "photo_documentary", "flat_illustration", "soft_three_d",
                "fact_check_default", "practical_tutorial", "professional_analysis");

        assertThat(SkillSeeder.seeds()).extracting(SkillSeeder.Seed::builtinKey)
                .as("新增技能不得覆盖既有 builtin_key（会改写用户已绑定的技能内容）")
                .containsAll(existingKeys);
        assertThat(PACKAGE_KEYS)
                .as("本包的 key 必须与既有技能错开")
                .doesNotContainAnyElementsOf(existingKeys);
    }
}
