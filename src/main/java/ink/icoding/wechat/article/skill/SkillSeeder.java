package ink.icoding.wechat.article.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内置 Skill 种子（skills-agent-plan 5.9 / 附录A）。
 * 按 builtin_key 幂等 upsert：存在则更新 content/engine/engine_config（内置 skill 内容随版本 seed 更新，
 * 排版模板属系统行为）；name 不覆盖（用户可能已改名，但内置项名唯一校验以 name 唯一约束保障）。
 * @Order(10) 保证先于 Quartz 任务恢复（ApplicationReadyEvent）。
 */
@Component
@Order(10)
public class SkillSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SkillSeeder.class);
    private static final String MARKFLOW_DEFAULT_CONFIG = "{\"accentMode\":\"AUTO\"}";

    private final SkillMapper mapper;

    public SkillSeeder(SkillMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Seed seed : seeds()) {
            try {
                upsert(seed);
            } catch (Exception exception) {
                log.warn("内置技能 {} 种子写入失败（跳过，不影响启动）", seed.builtinKey(), exception);
            }
        }
    }

    private void upsert(Seed seed) {
        Skill existing = mapper.findByBuiltinKey(seed.builtinKey());
        if (existing != null) {
            // 已存在：仅同步内置内容（content/engine/engineConfig），保留用户对 name/enabled 的修改
            existing.setContent(seed.content());
            existing.setEngine(seed.engine());
            existing.setEngineConfig(seed.engineConfig());
            existing.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(existing);
            return;
        }
        Skill skill = new Skill();
        skill.setName(seed.name());
        skill.setDimension(seed.dimension());
        skill.setDescription(seed.description());
        skill.setContent(seed.content());
        skill.setEngine(seed.engine());
        skill.setEngineConfig(seed.engineConfig());
        skill.setEnabled(true);
        skill.setIsBuiltin(true);
        skill.setBuiltinKey(seed.builtinKey());
        LocalDateTime now = LocalDateTime.now();
        skill.setCreatedAt(now);
        skill.setUpdatedAt(now);
        mapper.insert(skill);
    }

    record Seed(String builtinKey, String name, String dimension, String description,
                String engine, String engineConfig, String content) {
    }

    static final String MARKFLOW_CONTENT = """
            MarkFlow 渲染式排版风格指引（引擎 = MARKFLOW，正文产出 MarkFlow 语法 Markdown，语法指令见系统提示中的实时语法部分）：

            1. 标题层级：文章主标题放在标题字段中，正文从「## 章节标题」开始组织；一级标题（#）不要在正文重复主标题。
            2. 组件以系统提示中的**实时语法指令（guide）为准**：先按 guide 给出的完整语法清单挑出适合本文的组件
               （步骤、标签徽章、提示框、双栏对比、公式、代码块、流程图、行内强调等），再按内容需要决定用几处。
               不要凭记忆发明标签或容器；也不要因为「只用最保险的几种」而放弃 guide 里本可以表达内容的结构——
               渲染能力没被用上，成品就会比 MarkFlow 本身能做到的素。
               **唯一需要背下来的「禁止写」名单是 `layout-*` 家族**（`:::layout-hero`、`<layout-hero>`、
               `:::layout-toc`、`:::layout-metrics`、`layout-cards` / `layout-steps` / `layout-timeline` 等）：
               这一族来自 MarkFlow Web 端另一套组件库，服务端渲染器一个都没实现——**注册表里共 38 个名字，
               容器式与标签式 76 组实测全部把语法原样留在产物里**（上游不报错也不给 warnings）。
               对应的版式按 ⑥ 换写法。
               **下面几条是实测结论**：①–⑤ 是 guide 写得不准的地方，⑥ 是 guide **漏写**、但渲染器确实支持的组件，
               两者都照下面写；guide 覆盖到的其余语法以 guide 为准。
              ① 步骤流用 `<steps>` 标签，每步一行 `- 名称 | 描述`（guide 六.9 的官方行格式）。
                 实测 3 步是 3 列各 33% 的横向卡，**4 步及以上会自动切换成竖向卡片**（产物与显式 `type="DA02"` 完全一致），
                 步数多了不用手动加 type。**步骤内不要写 `###` 小标题**：渲染器不把它当标题，会把「### 第一步」
                 原样当成一步的文字，还把这一步拆成两个格子。
              ② `:::steps` 容器能渲染，但行格式与 `<steps>` 不同：每行必须是「序号 | 步骤名 | 说明」三列，
                 缺竖线的行会让**整个容器降级为普通段落**（步骤文字还在、步骤版式全丢，上游只在 meta.warnings 里报一句）。
                 没把握就用 guide 记载的 `<steps>`。
              ③ 行内徽章用 guide 的 `<Badge type="tip" text="推荐" />`（`<badge type="tip" title="推荐" />` 实测也能出文字，
                 白名单外的 type 回落成默认样式）；标签组用 `<badges type="accent">标签A|标签B</badges>`。
                 **`<badge>` / `<icon>` 必须自闭合**：写成 `<badge type="tip">推荐</badge>` 会把收尾标签 `</badge>` 吐进正文。
              ④ 提示框首选引用块 `> [TIP] 提示标题` + 正文行；等价的容器写法 `:::tip 提示标题` 也可用
                 （`:::note` / `:::info` / `:::warning` / `:::caution` / `:::important` 同理）。
                 **容器名 `:::danger` / `:::success` 渲染器不认**：`:::danger` 这一行会**字面留在正文里**
                 （实测产物可见文字就是 `:::danger标题正文:::`，开、收两行都留），正文照排但提示框版式没有；
                 上游不报错、`meta.warnings` 也是空的。需要危险 / 成功配色时改用
                 `:::callout type="danger"` / `:::callout type="success"`（实测渲成红 ❌ / 绿 ✅ 提示卡，有效）；
                 `:::callout` 的 type 写白名单外的值会静默回落成 info。
                 双栏对比用 `:::compare`：每行写 3 或 4 列，如 `维度 | A方 | B方 | accent`。
                 第 4 格是**强调标记**，**只认英文 `accent` 或 `default`（也可以整格留空）**——
                 写中文（如「强调」「高亮」）或别的英文词（如 `highlight`）会被判成坏行**整行忽略**
                 （上游的告警文本说「列数不是…」，实际是取值不在白名单里，照着它去数数会改错方向）；
                 实测 run#81 / run#82 连续两轮的降级警告都出在这里，两篇稿子的第 4 格写的都是「强调」。
                 列数少于 3 或多于 4 同样整行忽略，列少的时候末尾那一方的整列对比内容会一起丢；
                 开头大卡、时间线、表格容器分别是 `:::breaking`、
                 `:::timeline`（每行 `- 时间 | 标题 | 说明`，缺列的行会被忽略）、`:::table`。
              ⑤ guide 与实现不一致的两处（都实测核对过，可以反证）：a) guide 第一节说 mermaid 代码块
                 「自动渲染为 SVG 图表」——实测只做代码高亮、不产出任何图形，流程图要么另配文字说明、
                 要么改用 `<steps>` 表达，别只靠它；b) 表格（含 `:::table`）后必须空一行再写正文，
                 否则紧随其后的文字会被系统当成表格注释（渲染成小字灰色）。
              ⑥ **guide 没写全、但渲染器确实支持的容器组件**（每一条都用真实渲染 API 核过产物，可反证）。
                 这是「成品比 MarkFlow 官网示例素」的主因：guide 的块级组件清单只列了 11 个标签，
                 `:::reading-path` 等 8 个容器**全文一次都没提**（`:::breaking` / `:::timeline` / `:::table`
                 guide 有记载，下面给的是它们的踩坑点），不写进提示词模型就只会用最朴素的那几种：
                 · 阅读路线（开头的章节导航，编号圆点 + 连接线，读长文的路径感来源）：
                   `:::reading-path` + 每行 `- 章节名 | 一句话说明` + `:::`（说明段可省，只写章节名也行）。
                   **每行都必须以 `-` 开头**：只要有一行写成裸文字、或整块换成 `*` 列表符号，
                   导航区会整块渲染为空、一个字都不剩（上游不报错），这是最容易踩的一个坑。
                 · 横向 / 纵向步骤流（带 label 小标、title、hint 的步骤卡，比 `<steps>` 多一层标题栏）：
                   `:::steps-horizontal label="HOW IT WORKS" title="从草稿到发布" hint="按顺序完成即可" active="2"`
                   + 每行 `- 名称 | 描述` + `:::`；把名字换成 `:::steps-vertical` 即竖向卡片。
                   （`active="2"` 指定强调第 2 步，`active="all"` / `"none"` 同理；`color` 可指定主色。）
                 · 案例流：`:::case-flow` + 每行 `- [案例 01] 标题` + `:::`（标签式 `<case-flow label="…">` 也行，
                   行格式完全相同）。
                   **每行都必须是「行首的 `-` + `[标签]`」**：写成 `*` / `+` / `1.` 列表、行首漏掉 `-`、
                   或漏掉 `[标签]`，**五种写法整块都是 0 字符**（内容静默消失、上游不报错）——比「少了标签」
                   宽得多，实测见 `target/probe/round8_caseflow_bullet.txt`。
                 · 轮播图：`:::slider images="图1直链,图2直链" interval="3" width="600" height="200" type="1"`
                   + `:::`，渲染成 SVG 动画（公众号里也能动）。**用容器式**；标签式 `<slider>` 必须配对
                   `</slider>`，写成自闭合 `<slider … />`（或只开不闭）整行标签会原样留在正文、轮播完全不出现
                   ——注意这条与 ③ 的 `<badge>` / `<icon>` **方向相反**，那两个必须自闭合。
                 · 提示卡片：`:::callout type="tip" title="排版小技巧"` + 正文 + `:::`
                   （type 支持 tip / note / info / warning / caution / important / danger / success；
                   比 `> [TIP]` 多一个自定义标题）。另有一个 `:::hint` 容器，渲染成蓝色信息卡（等同 `:::info`）；
                   **它只有容器写法，标签式 `<hint>` 不认**——标签会作为未知元素原样留在产物里，
                   屏幕上什么都不显示（比留一行字更隐蔽）。
                 · 时间线：`:::timeline` + 每行 `- 时间 | 标题 | 说明` + `:::`。**三列缺一不可**，缺列的行会被整行忽略。
                 · 表格容器：`:::table style="card"` + 标准 Markdown 表格 + `:::`
                   （比裸表格多一层卡片底与投影）。**表格没有标题位**——`title="…"` 渲染器不输出
                   （`:::timeline` / `:::slider` / `:::compare` 同理），表格标题请写在表格前的一句正文里，
                   或用上面的 `:::callout type="tip" title="…"` / `<p-title>`。
                 · 开头大卡：`:::breaking badge="NEW" title="…" subtitle="…" chips="甲|乙"` + 正文一句话 + `:::`。
                 · 对齐容器：`:::align align="center"` + 文字 + `:::`（居中引用语、诗歌、金句）。
                 · 代码块容器：`:::code-block lang="js" title="示例"` 包住 ``` 代码块 + `:::`。
                 用法判断（对着真实产物量化过，见下）：**每个二级章节（`##`）的标题都用 `<p-title>` 承接**，
                 不要只留裸 Markdown 标题——这是成品「像官网示例」最直接的来源；
                 `<p-title title="…" subtitle="…" level="1"></p-title>` 是官网示例的写法，
                 多写 `number="01"` 会加一条 CHAPTER 01 小标与巨型浅色编号，也好看，二选一即可。
                 另外：长文（5 个以上章节）开篇加一处 `:::reading-path`；步骤/流程用 `:::steps-horizontal`
                 （2–3 步）或 `:::steps-vertical`（4 步以上），不要写成纯 bullet 列表；时间线/演进用
                 `:::timeline`；案例用 `:::case-flow`；开篇标题区用 `:::breaking`；关键结论用 `<statement>`
                 （一篇 1–2 处，克制）；数据/参数对照用 `:::table` 或 `:::compare`；注意事项用 `:::callout`；
                 结尾用 `<engage-card>` / `<engage-label>`。
                 密度参考（2026-09-13 用真实渲染产物量化，脚本与样本见 `target/probe/density_check.py`）：
                 **不要拿「官网示例每千字 172 个带样式节点」当目标**——那是组件能力展示体（2253 字里塞了
                 9 个 p-title、11 种组件，每 250 字一个标题），不是新闻稿的形态。同一台渲染器、按上面这套
                 清单写、篇幅 1300–4400 字的稿子，实测区间是**每千字 85–99**。
                 可操作口径：**每个二级章节都用 `<p-title>` 承接 + 每节至少配一个结构组件（表格/时间线/
                 步骤/提示卡/金句）+ 每千字不少于 80 个带样式节点 + 组件种类不少于 6 种**。
                 我们此前的成稿只有 4–5 种、每千字 55–60，成品显得「素」就素在这里——不是渲染器少给了
                 样式（同一组件两边的产物几何完全一致，差异只在用没用）。
                 3000 字以上的长文，密度会被篇幅摊薄，靠**多分节**补：每节控制在 400 字以内。
            3. 正文以自然段为主，列表仅在真正的并列项时使用；组件服务于内容表达，不要为排版炫技堆砌，但该用的时候要用足。
            4. 正文图片一律使用素材工具返回的 publicUrl 直链（http/https 相对路径会被渲染服务忽略），图注紧跟图片；不得引用外链图片或占位图。
            5. 文末以一句与主题相关的收束语自然结束。

            主题色策略：accentMode 决定。
            - AUTO 模式：依据文章主题从下面的预设主题色对照表中就近选择一组，把 accent（和可选的 dark）作为 save_article_draft 的参数提交：
              翡翠绿 accent #27ae60 / dark #1e8449 —— 健康、养生、自然、通用
              科技蓝 accent #0984e3 / dark #0769b5 —— 科技、数码、互联网、AI
              深藏蓝 accent #1e3a5f / dark #0f2744 —— 财经、商务、职场、深度分析
              靛蓝 accent #667eea / dark #536DFE —— 教育、知识科普、个人成长
              商务红 accent #e74c3c / dark #c0392b —— 节日、促销、餐饮
              活力橙 accent #f39c12 / dark #e67e22 —— 美食、生活方式、亲子
              玫红 accent #e84393 / dark #d63384 —— 情感、女性向、美妆
              薄荷绿 accent #00b894 / dark #00a381 —— 环保、旅行、轻生活
              橄榄绿 accent #556B2F / dark #3d4f1f —— 历史、人文、茶文化
              酒红 accent #722f37 / dark #5a252c —— 历史厚重、文化、高端品牌
              紫罗兰 accent #6c5ce7 / dark #5a4bd1 —— 创意、设计、灵感
              中性灰 accent #888888 / dark #666666 —— 通知、公告、极简
              纯黑 accent #000000 / dark #1a1a1a —— 摄影集、极简主义、正式
            - FIXED 模式：使用技能配置中固定的主题色，不要自行更换。
            """;

    /** 本版内置技能清单（公开以便用例按清单推导数量，避免每次新增技能都要手工改断言）。 */
    public static List<Seed> seeds() {
        return List.of(
                new Seed("default_layout", "默认公众号版式", "LAYOUT",
                        "微信公众号默认版式：绿色 #07C160 体系、居中章节号与短横线、figure 图注、纯自然段。", "PROMPT", null,
                        DEFAULT_LAYOUT_CONTENT),
                new Seed("minimal_layout", "极简黑白版式", "LAYOUT",
                        "极简黑白灰配色版式：无彩色装饰线，章节号改用细体数字，其余结构同默认版式，正文纯自然段。", "PROMPT", null,
                        MINIMAL_LAYOUT_CONTENT),
                new Seed("magazine_layout", "杂志图文版式", "LAYOUT",
                        "杂志风图文版式：大图开篇、章节标题左对齐、段首强调色首字下沉风格说明，保持内联样式约束。", "PROMPT", null,
                        MAGAZINE_LAYOUT_CONTENT),
                new Seed("markflow_default", "MarkFlow 精排版式", "LAYOUT",
                        "渲染式排版：AI 产出 MarkFlow 语法 Markdown，由 MarkFlow 渲染服务生成公众号版式；主题色支持 AUTO/FIXED。", "MARKFLOW", MARKFLOW_DEFAULT_CONFIG,
                        MARKFLOW_CONTENT),
                new Seed("photo_documentary", "纪实摄影风", "IMAGE",
                        "真实摄影质感配图：自然光与可信场景，生图提示词按主体/环境/光线/构图/质感组织，图注一句话点明画面与正文的关系。", null, null,
                        PHOTO_DOCUMENTARY_CONTENT),
                new Seed("flat_illustration", "扁平插画风", "IMAGE",
                        "扁平矢量插画配图：几何色块、克制留白、配色跟随文章主题色；禁止照片质感、3D 立体与写实光影。", null, null,
                        FLAT_ILLUSTRATION_CONTENT),
                new Seed("soft_three_d", "柔和 3D 渲染风", "IMAGE",
                        "柔和 3D 渲染配图：圆角造型、哑光材质、柔和漫射光与简洁背景；禁止写实摄影与强对比商业光效。", null, null,
                        SOFT_THREE_D_CONTENT),
                new Seed("fact_check_default", "事实核查基线", "FACT_CHECK",
                        "关键事实多源交叉验证；数据与引语必须注明出处；时效敏感信息标注核实时间；无法核实的信息降级为「据报道」措辞并显式说明。", null, null,
                        FACT_CHECK_CONTENT),
                new Seed("practical_tutorial", "干货教程体", "WRITING",
                        "口语化表达，每章聚焦一个可执行要点，多用数字与案例；开头先给出「读完能获得什么」。", null, null,
                        PRACTICAL_TUTORIAL_CONTENT),
                new Seed("professional_analysis", "深度分析体", "WRITING",
                        "克制冷静的论述语气，论证链完整，数据引用规范；结尾给出明确判断。", null, null,
                        PROFESSIONAL_ANALYSIS_CONTENT));
    }

    /** default_layout 内容：原 ARTICLE_STYLE_GUIDE 原文迁移 + 「禁止 ul/ol/dl/table」规则（附录A）。 */
    static final String DEFAULT_LAYOUT_CONTENT = """

            【公众号正文视觉模板】
            新创作整篇文章或整篇重写时，正文必须采用下面的版式。局部修改已有文章时保持原有版式，不要为无关段落重排全文。

            版式要求：
            1. 文章标题放在标题字段中，正文不要机械重复主标题。正文依次由引言、导语、若干章节、配图/图注和收束语组成。
            2. 开头用一段简短引言概括全文核心，视觉上使用浅灰文字和绿色左边线；随后用一个自然段承接正文。
            3. 每章使用两位数字01、02、03……作为视觉章节号；章节号居中、绿色，下面有一条绿色短横线，再放居中的章节标题。这里的数字是章节装饰，不是编号列表。
            4. 正文使用简洁自然段，字号16px、行高1.9、深灰色、段间距16px；不要使用ul、ol、dl、table，也不要写成条目清单。
            5. 图片放在相关段落之后，宽度100%、高度自适应；需要说明时在图片下方使用居中的浅灰小字图注。图片必须来自素材工具返回的publicUrl，不得保留占位图片或外链图片。
            6. 全部样式写在style内联属性中，不依赖class、style标签、脚本或外部CSS。绿色统一使用#07C160，正文颜色使用#333333，辅助文字使用#888888。
            7. 章节通常为2至5个，数量由内容决定。最后用一句与主题相关的简短文字居中收束；不要照抄示例文案。

            HTML结构示例（只参考结构与样式，必须根据实际主题替换所有文字、章节数量、图片和链接）：
            <section style="margin:0 0 30px 0;">
              <blockquote style="margin:0;padding:0 0 0 14px;border-left:3px solid #07C160;color:#888888;font-size:15px;line-height:1.8;">“用一句话概括全文的核心内容。”</blockquote>
            </section>
            <p style="margin:0 0 16px 0;color:#333333;font-size:16px;line-height:1.9;text-align:justify;">正文内容从这里开始，用自然段完成导入。</p>
            <section style="margin:42px 0 28px 0;text-align:center;">
              <div style="color:#07C160;font-size:20px;line-height:1.2;">01</div>
              <div style="width:18px;height:2px;margin:7px auto 16px auto;background:#07C160;"></div>
              <h2 style="margin:0;color:#222222;font-size:20px;font-weight:400;line-height:1.6;text-align:center;">章节标题</h2>
            </section>
            <p style="margin:0 0 16px 0;color:#333333;font-size:16px;line-height:1.9;text-align:justify;">本章正文使用连贯的自然段。</p>
            <figure style="margin:24px 0 10px 0;">
              <img src="素材工具返回的publicUrl" alt="与正文有关的准确描述" style="display:block;width:100%;height:auto;margin:0;" />
              <figcaption style="margin-top:8px;color:#999999;font-size:13px;line-height:1.6;text-align:center;">必要时填写简短图注</figcaption>
            </figure>
            <p style="margin:0 0 16px 0;color:#555555;font-size:14px;line-height:1.8;">需要引用时，用自然段写“参考：来源名称”，并为来源名称添加链接。</p>
            <section style="margin:48px 0 20px 0;text-align:center;">
              <div style="color:#999999;font-size:14px;line-height:1.8;">根据文章主题创作一句简短收束语</div>
            </section>

            8. 禁止使用项目符号列表（ul）、编号列表（ol）、定义列表（dl）或表格（table）；需要表达多项内容时写成连贯的自然段。
            """;

    static final String MINIMAL_LAYOUT_CONTENT = """
            极简黑白版式（指令式排版，正文为内联样式 HTML，颜色仅使用黑、白、灰三阶）：

            1. 文章标题放在标题字段中，正文不要机械重复主标题。正文由引言、导语、若干章节、配图/图注和收束语组成。
            2. 开头用一段简短引言概括全文核心，视觉上使用浅灰文字和黑色左边线（#222222）；随后用一个自然段承接正文。
            3. 每章使用细体数字（font-weight:300）作为章节装饰号（01、02、03……），居中、黑色，下方一条 1px 黑色细横线，再放居中的章节标题；这里的数字是装饰，不是编号列表。
            4. 正文使用简洁自然段，字号16px、行高1.9、深灰色（#333333）、段间距16px；不要使用 ul、ol、dl、table，也不要写成条目清单。
            5. 图片放在相关段落之后，宽度100%；图注为居中浅灰小字。图片必须来自素材工具返回的 publicUrl。
            6. 全部样式写在 style 内联属性中，不依赖 class、style 标签、脚本或外部 CSS。装饰线用 #222222，正文 #333333，辅助文字 #888888，背景一律不加色块。
            7. 章节通常为 2 至 5 个。最后用一句与主题相关的简短文字居中收束。
            8. 禁止使用项目符号列表（ul）、编号列表（ol）、定义列表（dl）或表格（table）；需要表达多项内容时写成连贯的自然段。
            """;

    static final String MAGAZINE_LAYOUT_CONTENT = """
            杂志图文版式（指令式排版，正文为内联样式 HTML）：

            1. 大图开篇：正文第一元素为一张全宽主图（宽度100%、圆角可选），图片必须来自素材工具返回的 publicUrl，主图下方放居中的浅灰小字导语式图注；随后用一个自然段承接正文。
            2. 章节标题左对齐：每章标题左对齐、加粗、字号18-20px，标题下方放置一条完整的强调色细横线（宽度约 48px、高 3px）；章节不再使用居中数字装饰号。
            3. 段首强调色首字下沉风格说明：每章首个自然段可使用首字下沉效果（float 的 span，强调色、字号约 32px、加粗），如上游编辑器不支持 float 时改为段首强调色加粗首词，保持视觉层次。
            4. 正文使用简洁自然段，字号16px、行高1.9、深灰色 #333333、段间距16px；不要使用 ul、ol、dl、table。
            5. 正文配图穿插于相关章节内，宽度100%、图注居中浅灰小字；图文节奏约为每章 1-2 图。
            6. 全部样式写在 style 内联属性中，不依赖 class、style 标签、脚本或外部 CSS。强调色统一使用 #07C160，正文 #333333，辅助文字 #888888。
            7. 章节通常为 2 至 5 个。最后用一句与主题相关的简短文字居中收束。
            8. 禁止使用项目符号列表（ul）、编号列表（ol）、定义列表（dl）或表格（table）；需要表达多项内容时写成连贯的自然段。
            """;

    static final String PHOTO_DOCUMENTARY_CONTENT = """
            纪实摄影风格（图片风格；本条只约束配图审美、生图提示词与图注，不改变正文文字）：

            1. 配图审美：真实摄影质感——自然光、可信的场景与人物状态、真实材质；不使用插画、卡通、3D 卡通与超现实拼贴。
            2. 生图提示词脚手架：按「主体 + 场景环境 + 光线 + 构图镜头 + 质感 + 负面约束」的顺序写成一句中文提示词，
               例如「清晨的社区健康服务台，护士为老人测量血压，室内自然光，中景、三分法构图，真实摄影质感、浅景深，
               无文字、无水印、无拼贴」。提示词中必须显式写出「真实摄影」「无文字」「无水印」。
            3. 画面内禁止出现文字、数字、logo 与水印：图像模型的字形不可控，图中的中文几乎必然是错字；需要传达信息时改用图注。
            4. 图注习惯：每张图配一句不超过 20 字的图注，说明画面与所在段落的关系（例如「社区血压筛查现场」）；
               不要写「图片来源：网络」这类无信息量的说明，来源统一在文末自然段交代。
            5. 数量与位置：每章至多 1 张，紧跟其所服务的段落；不要把图片集中堆在文末。
            6. 封面：选主体明确、留白充足的一张；顶部与右上角不要放置主体或文字（标题安全区）。
            7. 一致性：同一篇文章的配图保持相近的色调与光线方向，不要在同一篇内混用摄影与插画风格。
            """;

    static final String FLAT_ILLUSTRATION_CONTENT = """
            扁平插画风格（图片风格；本条只约束配图审美、生图提示词与图注，不改变正文文字）：

            1. 配图审美：扁平矢量插画——几何色块、简洁轮廓、克制留白；不使用照片质感、3D 立体与写实光影。
            2. 生图提示词脚手架：按「主题概念 + 场景隐喻 + 扁平矢量插画 + 几何色块 + 配色 + 负面约束」的顺序写成一句中文提示词，
               例如「远程办公带来的效率提升，一个人与悬浮信息窗口的抽象场景，扁平矢量插画，几何色块，蓝绿双色为主，
               无渐变、无阴影、无文字」。提示词中必须显式写出「扁平矢量插画」「无文字」「无渐变阴影」。
            3. 配色跟随文章：文章已确定主题色时以该色为主色并搭配中性灰；没有主题色时使用中性灰蓝；同一篇内主色不超过三种。
            4. 画面内禁止出现文字、数字、logo 与水印：矢量风格中的文字更容易糊成色块；信息一律交给图注表达。
            5. 图注习惯：每张图配一句不超过 20 字的图注，点明插画表达的抽象概念（例如「多任务并行示意」）。
            6. 数量与位置：每章至多 1 张，紧跟其所服务的段落；不要把图片集中堆在文末。
            7. 封面：选主体居中、四边留白充足的一张；顶部与右上角留空（标题安全区）。
            """;

    static final String SOFT_THREE_D_CONTENT = """
            柔和 3D 渲染风格（图片风格；本条只约束配图审美、生图提示词与图注，不改变正文文字）：

            1. 配图审美：柔和 3D 渲染——圆角造型、哑光或磨砂材质、柔和渐变光、浅景深与简洁背景；
               不使用写实摄影、扁平矢量插画与强对比的商业海报光效。
            2. 生图提示词脚手架：按「主体造型 + 材质 + 打光 + 背景 + 视角景深 + 负面约束」的顺序写成一句中文提示词，
               例如「一台轻量健康监测设备的圆角造型，哑光塑料材质，柔和顶部漫射光，浅灰纯色背景，微俯视角、浅景深，
               柔和 3D 渲染，无文字、无水印」。提示词中必须显式写出「柔和 3D 渲染」「无文字」「无水印」。
            3. 配色跟随文章：以文章主题色作为主体色或点缀色，背景保持低饱和浅色，避免背景与主体争夺视觉重心。
            4. 画面内禁止出现文字、数字、logo 与水印：需要说明的信息交给图注，不要让模型渲染品牌或产品标识。
            5. 图注习惯：每张图配一句不超过 20 字的图注，说明该物件或场景在文中的角色（例如「居家监测设备示意」）。
            6. 数量与位置：每章至多 1 张，紧跟其所服务的段落；不要把图片集中堆在文末。
            7. 封面：选单一主体、背景干净的一张；主体不要贴边，顶部与右上角留空（标题安全区）。
            """;

    static final String FACT_CHECK_CONTENT = """
            事实核查基线（所有创作场景默认引用规范）：

            1. 关键事实（数据、时间、人名、机构名、事件因果）必须至少两个独立来源交叉验证；无法交叉验证的关键事实不得作为确定性结论陈述。
            2. 数据与引语必须注明出处：正文用自然段说明「参考：来源名称」，来源名称附原文链接；不得虚构来源或链接。
            3. 时效敏感信息（价格、版本号、政策、排名）标注核实时间，例如「截至 2026 年 9 月」。
            4. 无法核实的信息降级为「据报道」「有消息称」等措辞，并显式说明该信息尚未获得权威确认；不要把传闻写成事实。
            5. 来源与观点分离：引用他人观点时明确归属；自己的判断用「本文认为」等措辞与事实性陈述区分。
            """;

    static final String PRACTICAL_TUTORIAL_CONTENT = """
            干货教程体（写作风格）：

            1. 口语化表达：像有经验的同事在讲解，避免论文腔与华丽修辞；术语首次出现时用一句话解释。
            2. 开头先给出「读完能获得什么」：用一段话明确列出读者读完能掌握的技能或解决的问题，再进入正文。
            3. 每章聚焦一个可执行要点：章节按操作顺序或认知递进组织，每章结尾用 1-2 句自然段小结本章要点。
            4. 多用数字与案例：步骤给出具体数字（几步、几分钟、几条），关键论点配一个真实或高度可信的示例；案例必须具体，不要用「某公司」式的模糊表述。
            5. 行动导向：正文结尾给出明确的下一步行动建议；不要用空泛的口号收尾。
            6. 篇幅节奏：全文 1200-2500 字为宜，段落长度均匀，避免超长段落。
            """;

    static final String PROFESSIONAL_ANALYSIS_CONTENT = """
            深度分析体（写作风格）：

            1. 克制冷静的论述语气：避免情绪化用词与夸张修辞，判断建立在证据之上；不确定的部分明确说明不确定性。
            2. 论证链完整：每章按「现象 -> 机制 -> 影响 -> 边界条件」推进，前后章节之间有清晰的逻辑承接；不要罗列互不衔接的论点。
            3. 数据引用规范：数据注明来源与统计口径；不同来源数据冲突时并列展示并解释差异原因。
            4. 观点与事实分离：事实性陈述可验证，观点性陈述明确归属（「本文认为」「业内普遍认为」）。
            5. 结尾给判断：以一段完整的判断收尾——给出明确的结论、成立的前提条件，以及可能推翻结论的关键变量；不要含糊收尾。
            6. 篇幅节奏：全文 1800-3500 字为宜，段落之间留白均匀；宁缺毋滥，不为凑字数注水。
            """;
}
