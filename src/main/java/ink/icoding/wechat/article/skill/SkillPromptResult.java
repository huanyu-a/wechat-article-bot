package ink.icoding.wechat.article.skill;

import java.util.List;

/**
 * Skill 注入结果（skills-agent-plan 5.2 输出）。
 */
public record SkillPromptResult(
        /** 组装完成的 skill 注入块（含维度标题），空集合时为保底 default_layout 内容。 */
        String prompt,
        /** 生效排版引擎：排序最前 LAYOUT skill 的 engine；无任何 LAYOUT skill 时为 PROMPT（保底）。 */
        LayoutEngine engine,
        /** 被丢弃的 LAYOUT skill（多枚时仅优先级最高一枚生效）+ EDITOR 场景下被过滤的 MARKFLOW 技能。 */
        List<Skill> ignoredLayoutSkills) {
}
