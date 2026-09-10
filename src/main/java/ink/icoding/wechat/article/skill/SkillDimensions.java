package ink.icoding.wechat.article.skill;

import java.util.List;
import java.util.Set;

/**
 * Skill 维度白名单（skills-agent-plan 3.3）。
 * 注入顺序按本表从上到下，但 LAYOUT（排版模板）刻意排到最后、紧邻生成指令
 * （FACT_CHECK 与 OTHER 注入在 LAYOUT 之前）。key 与前端 webui/src/utils/skills.js 契约一致。
 */
public final class SkillDimensions {
    /** 维度 key 白名单。 */
    public static final Set<String> ALL = Set.of(
            "AUDIENCE", "TOPIC", "WRITING", "LANGUAGE", "TITLE", "OPENING",
            "ENDING", "DIGEST", "IMAGE", "LAYOUT", "FACT_CHECK", "OTHER");

    /** prompt 注入顺序（LAYOUT 排最后）。 */
    public static final List<String> INJECTION_ORDER = List.of(
            "AUDIENCE", "TOPIC", "WRITING", "LANGUAGE", "TITLE", "OPENING",
            "ENDING", "DIGEST", "IMAGE", "FACT_CHECK", "OTHER", "LAYOUT");

    /** 维度显示名（与前端 SKILL_DIMENSIONS label 一致）。 */
    public static final Set<String> DISPLAY_NAMES = Set.of(
            "目标读者", "选题策略", "写作风格", "语言习惯", "标题风格", "开头写法",
            "结尾写法", "摘要风格", "图片风格", "排版模板", "事实核查", "其他");

    private SkillDimensions() {
    }

    public static boolean isValid(String dimension) {
        return dimension != null && ALL.contains(dimension);
    }
}
