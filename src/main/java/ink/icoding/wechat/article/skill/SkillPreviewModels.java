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

    /**
     * preview 组装上下文（无账号实体时 accountDefaultStyle 可为空）。
     *
     * <p>待预览的技能放进 **taskSkillIds**：articleSkillIds 只在 EDITOR 场景参与并集，
     * 若放进该槽位，SCHEDULED 预览（前端预览按钮用的就是 SCHEDULED）会把请求的技能全部丢掉，
     * 只回落到内置 default_layout——用户点哪个技能看到的都是同一段默认版式。
     * task 槽位在两种场景下都会参与并集，因此用它模拟「按任务级绑定预览」。
     */
    public static SkillContext context(PreviewRequest request) {
        SkillContext.Scene scene = "EDITOR".equalsIgnoreCase(request.scene())
                ? SkillContext.Scene.EDITOR : SkillContext.Scene.SCHEDULED;
        return new SkillContext(java.util.List.of(),
                request.skillIds() == null ? java.util.List.of() : request.skillIds(),
                java.util.List.of(), java.util.List.of(),
                request.accountDefaultStyle(), scene);
    }
}
