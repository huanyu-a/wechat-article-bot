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
            2. 步骤类内容（操作指南、流程说明）优先使用 :::steps 步骤卡；多方案对比（优劣分析、选项对比）优先使用 :::compare 对比卡；需要突出强调的提示、注意事项、总结使用 :::tip / :::warning 提示框。
            3. 正文以自然段为主，列表仅在真正的并列项时使用；不要为排版炫技堆砌组件，组件种类与数量以内容需要为准。
            4. 正文图片一律使用素材工具返回的 publicUrl 直链（http/https 相对路径会被渲染服务忽略），图注紧跟图片；不得引用外链图片或占位图。
            5. 文末以一句与主题相关的收束语自然结束。

            主题色策略：accentMode 决定。
            - AUTO 模式：依据文章主题从下面的预设主题色对照表中就近选择一组，把 accent（和可选的 dark）作为 save_article_draft 的参数提交：
              翡翠绿 accent #27ae60 / dark #1e8449 —— 自然、健康、生活方式、环保
              科技蓝 accent #0984e3 / dark #0769b5 —— 科技、互联网、数码、效率工具
              深藏蓝 accent #1e3a5f / dark #0f2744 —— 财经、政治、深度报道、行业研究
              商务红 accent #e74c3c / dark #c0392b —— 餐饮、零售、促销、节日热点
              活力橙 accent #f39c12 / dark #e67e22 —— 教育、亲子、创意、轻内容
              玫红 accent #e84393 / dark #d63384 —— 情感、时尚、美妆、女性向内容
              纯黑 accent #000000 / dark #1a1a1a —— 极简、人文、严肃文学、讣告
            - FIXED 模式：使用技能配置中固定的主题色，不要自行更换。
            """;

    static List<Seed> seeds() {
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
