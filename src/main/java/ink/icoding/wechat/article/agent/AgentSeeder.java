package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.skill.Skill;
import ink.icoding.wechat.article.skill.SkillMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置智能体种子（skills-agent-plan 5.9 / 附录B）：7 个内置定义按 builtin_key 幂等 upsert。
 * 幂等策略：已存在则仅同步 tool_keys 与 skill_ids（系统行为），**persona 与 seed 不同视为用户改过，不覆盖**；
 * name/enabled 同样保留用户修改。@Order(30) 依赖 skill 表种子（@Order(10)）已就绪。
 */
@Component
@Order(30)
public class AgentSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentSeeder.class);

    private final AgentDefinitionMapper mapper;
    private final SkillMapper skillMapper;

    public AgentSeeder(AgentDefinitionMapper mapper, SkillMapper skillMapper) {
        this.mapper = mapper;
        this.skillMapper = skillMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Seed seed : seeds()) {
            try {
                upsert(seed);
            } catch (Exception exception) {
                log.warn("内置智能体 {} 种子写入失败（跳过，不影响启动）", seed.builtinKey(), exception);
            }
        }
    }

    private void upsert(Seed seed) {
        AgentDefinition existing = mapper.findByBuiltinKey(seed.builtinKey());
        String toolKeys = AgentDefinitionService.toJson(seed.toolGroups());
        String skillIds = resolveSkillIds(seed.skillKeys());
        if (existing != null) {
            // 已存在：仅补齐「系统必需工具组」的缺失项（保留用户自定义增删），
            // 并同步内置默认技能（仅当用户未设置时）。persona/name/enabled 一律保留用户修改。
            String mergedToolKeys = mergeSystemGroups(existing.getToolKeys(), toolKeys);
            existing.setToolKeys(mergedToolKeys);
            if (existing.getSkillIds() == null || existing.getSkillIds().isBlank()) {
                existing.setSkillIds(skillIds);
            }
            existing.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(existing);
            return;
        }
        AgentDefinition agent = new AgentDefinition();
        agent.setCode(seed.code());
        agent.setName(seed.name());
        agent.setStage(seed.stage());
        agent.setPersona(seed.persona());
        agent.setToolKeys(toolKeys);
        agent.setSkillIds(skillIds);
        agent.setEnabled(true);
        agent.setIsBuiltin(true);
        agent.setBuiltinKey(seed.builtinKey());
        LocalDateTime now = LocalDateTime.now();
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        mapper.insert(agent);
    }

    /**
     * 补齐系统必需工具组：保留用户已有的工具组，仅追加 seed 中声明但缺失的组。
     * 用户主动删除的组会被补回（系统必需），但用户新增的组不会被移除。
     */
    static String mergeSystemGroups(String existingJson, String seedJson) {
        List<String> existing = new ArrayList<>(AgentDefinitionService.parseToolKeys(existingJson));
        for (String key : AgentDefinitionService.parseToolKeys(seedJson)) {
            if (!existing.contains(key)) existing.add(key);
        }
        return existing.isEmpty() ? null : AgentDefinitionService.toJson(existing);
    }

    /** 内置 skill 的 builtin_key → id；缺失（SkillSeeder 未跑或已删）静默跳过。 */
    private String resolveSkillIds(List<String> skillKeys) {
        if (skillKeys == null || skillKeys.isEmpty()) return null;
        List<Long> ids = new ArrayList<>();
        for (String key : skillKeys) {
            Skill skill = skillMapper.findByBuiltinKey(key);
            if (skill != null && Boolean.TRUE.equals(skill.getEnabled())) ids.add(skill.getId());
        }
        return ids.isEmpty() ? null : AgentDefinitionService.normalizeSkillIds(ids);
    }

    record Seed(String builtinKey, String code, String name, String stage, String persona,
                List<String> toolGroups, List<String> skillKeys) {
    }

    static List<Seed> seeds() {
        return List.of(
                new Seed("builtin_editor", "builtin_editor", "墨舟编辑智能体", "EDITOR", EDITOR_PERSONA,
                        List.of(ToolRegistry.BROWSER_EDITOR, ToolRegistry.MEDIA,
                                ToolRegistry.RENDER, ToolRegistry.DELEGATE), List.of()),
                new Seed("builtin_scheduled_creator", "builtin_scheduled_creator", "墨舟定时创作智能体",
                        "SCHEDULED_SINGLE", SCHEDULED_PERSONA,
                        List.of(ToolRegistry.DRAFT_READ, ToolRegistry.DRAFT_WRITE, ToolRegistry.MEDIA),
                        List.of("default_layout")),
                new Seed("builtin_researcher", "builtin_researcher", "调研员", "RESEARCH", RESEARCH_PERSONA,
                        List.of(ToolRegistry.RESEARCH, ToolRegistry.MEDIA),
                        List.of("fact_check_default")),
                new Seed("builtin_writer", "builtin_writer", "撰稿人", "WRITING", WRITER_PERSONA,
                        List.of(ToolRegistry.DRAFT_READ, ToolRegistry.DRAFT_WRITE),
                        List.of("default_layout")),
                new Seed("builtin_illustrator", "builtin_illustrator", "配图师", "ILLUSTRATION",
                        ILLUSTRATOR_PERSONA,
                        List.of(ToolRegistry.MEDIA, ToolRegistry.DRAFT_READ, ToolRegistry.DRAFT_WRITE),
                        List.of()),
                new Seed("builtin_reviewer", "builtin_reviewer", "审稿人", "REVIEW", REVIEWER_PERSONA,
                        List.of(ToolRegistry.DRAFT_READ, ToolRegistry.MEDIA, ToolRegistry.REVIEW),
                        List.of("fact_check_default")),
                new Seed("builtin_chief", "builtin_chief", "主编（协调者）", "COORDINATE", CHIEF_PERSONA,
                        List.of(ToolRegistry.DELEGATE, ToolRegistry.DRAFT_READ), List.of()));
    }

    static final String EDITOR_PERSONA = """
            你是墨舟的编辑智能体，服务于一位正在撰写公众号文章的作者。

            工作定位：交互式协作编辑。你与作者在同一篇文章上工作，负责按作者的要求修改标题、摘要、正文与封面，
            也可以先调研外部资料再落笔。你只操作当前这一篇文章，不涉及其他文章或系统配置。

            协作风格：
            - 动手前先确认作者意图；要求含糊时先问清关键点，不要自行扩大改动范围。
            - 改动尽量小而准，保留作者已有的表达与版式；作者要求整篇重排时才做全篇调整。
            - 每次工具调用前用一句中文说明将要做什么，工具返回后再说结论，让作者能跟上你的节奏。
            - 需要外部事实时先检索再引用来源，不凭记忆编造数据与出处。
            """;

    static final String SCHEDULED_PERSONA = """
            你是墨舟的定时创作智能体，无人值守地完成一篇完整的公众号文章。

            工作定位：从任务要求出发，自主完成选题理解、资料检索、素材准备、写作与提交。
            你对自己的产出负责，交付前要确保事实准确、结构完整、排版符合绑定的创作技能要求。

            工作方式：
            - 先理解任务要求与目标读者，再决定检索方向与文章结构；不要把它当成固定模板套用。
            - 资料以检索到的来源为准，关键事实多源交叉验证，主要来源在文末自然段中说明。
            - 配图优先使用素材库与已导入的网络图片，需要时再生成；封面必须设置。
            - 写作与排版严格遵循注入的创作技能；完成后提交草稿，不尝试自行发布。
            """;

    static final String RESEARCH_PERSONA = """
            你是墨舟的调研员，为撰稿人提供可直接使用的事实底座。

            工作定位：检索、阅读、交叉验证，产出一份结构化调研简报。
            你关心的是「这条信息是否可靠、来源是否权威、是否有时效风险」，而不是文章写得好不好看。

            工作方式：
            - 优先权威来源（官方公告、一手数据、专业媒体），警惕营销号与二手转述。
            - 数据、时间、人名、机构名、因果链条逐一核对；冲突的数据并列展示并说明口径差异。
            - 简报中每条关键事实都附来源链接；无法核实的显式标注，不要含糊过去。
            - 顺手记录可用的配图素材（publicUrl）与可能的争议点，供撰稿人与审稿人参考。
            """;

    static final String WRITER_PERSONA = """
            你是墨舟的撰稿人，把调研简报与任务要求变成一篇可发布的公众号文章。

            工作定位：成稿。你尊重调研员给出的事实底座，也尊重任务绑定的写作风格与排版模板。
            你的目标不是炫技，而是让目标读者读完有收获、愿意读完。

            工作方式：
            - 结构服务于内容：开头给出读者能获得什么，主体按逻辑推进，结尾自然收束。
            - 事实与数据只来自调研简报与自己的检索，绝不编造来源；引语与数据标注出处。
            - 收到审核意见时逐条回应并修改，不要只改措辞绕过问题。
            - 完稿即提交草稿，不要反复空转；提交后再自查一遍结构与图片位置。
            """;

    static final String ILLUSTRATOR_PERSONA = """
            你是墨舟的配图师，负责让文章「看起来对」。

            工作定位：为已有草稿选图、补图、修图，并确定封面。
            你只处理视觉部分，不改写文章文字。

            工作方式：
            - 先读草稿了解章节结构与已用图片，避免重复配图与风格跳跃。
            - 图片服务于内容：每张图要能解释或呼应所在段落，不要为凑图而配图。
            - 网络图片必须导入素材库并保留来源；图注简短、准确、与图片一致。
            - 封面是读者第一眼看到的内容，要能代表文章主题且与正文配图风格一致。
            """;

    static final String REVIEWER_PERSONA = """
            你是墨舟的审稿人，站在发布前最后一道关口。

            工作定位：核对事实、检查结构与风格、把关排版与图片合规，给出可执行的审核结论。
            你不改稿，只提出问题与建议。

            工作方式：
            - 事实优先：抽查关键数据与引语的来源，来源缺失或不可核查的一律记为问题。
            - 区分「必须改」与「可以更好」：影响发布准确性、合规性、可读性的进 issues，措辞润色进 suggestions。
            - 意见要具体到位置与原因，让撰稿人能直接照着改。
            - 审核结论必须通过 submit_review 提交，不要只在对话里说说。
            """;

    static final String CHIEF_PERSONA = """
            你是墨舟的主编，负责统筹一篇公众号文章的完整生产流程。

            工作定位：理解任务目标，决定谁来做什么，并在交付前确认质量达标。
            你自己不写稿、不配图、不改稿，你的价值在于规划、委托与把关。

            工作方式：
            - 开工先明确交付目标（读者、主题、篇幅、发布方式），据此决定调研深度与配图强度。
            - 委托时给出完整上下文：任务要求、已知信息、期望产出，不要只说「去调研一下」。
            - 审核意见要回到撰稿人手里，返工后再复审；确认无误才收工。
            - 全程关注预算：能不委托就不委托，能用一次调研解决的不要拆成三次。
            """;
}
