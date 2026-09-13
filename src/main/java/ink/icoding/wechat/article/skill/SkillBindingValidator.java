package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能绑定校验（I5，产品决策在绑定侧落地）。
 *
 * <p>为什么放在绑定处而不是组装层：同一维度的多枚风格类技能会**并列注入**（{@link SkillPromptAssembler}），
 * 典型冲突是两个互斥的【图片风格】（如「纪实摄影」+「扁平插画」）。若在组装层改成「首个生效、其余丢弃」，
 * 用户会在看不见的情况下丢掉一半绑定，比冲突本身更糟；正确做法是在绑定时就告诉用户只能保留一枚。
 *
 * <p>范围：仅校验 {@code IMAGE} 维度（多枚必然给出互斥的审美取向）。其余维度可能是有意的叠加表达
 * （如多个写作风格），保持现有并列注入行为不变。
 *
 * <p>容错：技能读取失败时跳过校验（不因校验设施故障阻断绑定）。
 */
@Component
public class SkillBindingValidator {
    private static final Logger log = LoggerFactory.getLogger(SkillBindingValidator.class);
    private static final String CONFLICT_DIMENSION = "IMAGE";

    private final SkillMapper skillMapper;

    public SkillBindingValidator(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    /**
     * @throws BusinessException 同一绑定点绑定了多枚启用中的 IMAGE 技能
     */
    public void validate(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) return;
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : skillIds) {
            if (id != null) unique.add(id);
        }
        if (unique.isEmpty()) return;
        List<Skill> skills;
        try {
            skills = skillMapper.findByIds(List.copyOf(unique));
        } catch (Exception exception) {
            log.warn("技能绑定校验跳过：读取技能失败", exception);
            return;
        }
        List<String> conflicting = new ArrayList<>();
        for (Long id : unique) {
            Skill skill = skills.stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null);
            if (skill == null || !Boolean.TRUE.equals(skill.getEnabled())) continue;
            if (CONFLICT_DIMENSION.equals(skill.getDimension())) conflicting.add(skill.getName());
        }
        if (conflicting.size() > 1) {
            throw new BusinessException("同一绑定点只能选择一枚【图片风格】技能，当前选择了 "
                    + conflicting.size() + " 枚：" + String.join("、", conflicting)
                    + "。它们会并列注入且风格可能互斥，请只保留一枚。");
        }
    }

    /** 维度 → 启用中的技能名（供测试与诊断）。 */
    Map<String, List<String>> describe(List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) return Map.of();
        return skillMapper.findByIds(skillIds.stream().filter(java.util.Objects::nonNull).toList()).stream()
                .filter(skill -> Boolean.TRUE.equals(skill.getEnabled()))
                .collect(Collectors.groupingBy(Skill::getDimension,
                        Collectors.mapping(Skill::getName, Collectors.toList())));
    }
}
