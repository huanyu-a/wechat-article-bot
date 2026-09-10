package ink.icoding.wechat.article.skill;


/**
 * Skill preview 请求/响应（skills-agent-plan 5.2）。
 * request.engineConfig 为已序列化的 JSON 对象（前端直接回传对象）。
 */
public final class SkillPreviewModels {
    private SkillPreviewModels() {
    }

    public record PreviewRequest(java.util.List<Long> skillIds, String scene, String accountDefaultStyle) {
    }

    public record PreviewView(String prompt, String engine, java.util.List<IgnoredSkill> ignoredLayoutSkills) {
    }

    /** 被忽略技能的轻量视图（不回传大段 content）。 */
    public record IgnoredSkill(Long id, String name, String dimension, String engine) {
        public static IgnoredSkill of(Skill skill) {
            return new IgnoredSkill(skill.getId(), skill.getName(), skill.getDimension(), skill.getEngine());
        }
    }

    /** preview 组装上下文（无账号实体时 accountDefaultStyle 可为空）。 */
    public static SkillContext context(PreviewRequest request) {
        SkillContext.Scene scene = "EDITOR".equalsIgnoreCase(request.scene())
                ? SkillContext.Scene.EDITOR : SkillContext.Scene.SCHEDULED;
        return new SkillContext(request.skillIds() == null ? java.util.List.of() : request.skillIds(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                request.accountDefaultStyle(), scene);
    }
}
