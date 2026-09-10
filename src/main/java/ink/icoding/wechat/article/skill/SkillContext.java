package ink.icoding.wechat.article.skill;

import java.util.List;

/**
 * Skill prompt 组装输入（skills-agent-plan 5.2）。
 *
 * @param articleSkillIds      文章级 skill id（仅 EDITOR 场景注入，最高优先级）
 * @param taskSkillIds         任务级 skill id
 * @param accountSkillIds      账号级 skill id
 * @param agentDefaultSkillIds 智能体默认 skill id（底座，本期两条链路无 agent 定义传空）
 * @param accountDefaultStyle  账号默认风格（default_style 非空时注入一行）
 * @param scene                SCHEDULED / EDITOR
 */
public record SkillContext(List<Long> articleSkillIds, List<Long> taskSkillIds, List<Long> accountSkillIds,
                           List<Long> agentDefaultSkillIds, String accountDefaultStyle, Scene scene) {

    public enum Scene {SCHEDULED, EDITOR}
}
