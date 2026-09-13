package ink.icoding.wechat.article.skill;

import ink.icoding.wechat.article.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I5：绑定侧的同维度冲突校验（产品决策：在绑定处提示，而不是在组装层静默丢弃）。
 *
 * <p>只有 IMAGE 维度受限——两枚【图片风格】必然给出互斥的审美取向；其余维度（如多个写作风格）
 * 可能是有意的叠加表达，必须保持可并行绑定。
 */
class SkillBindingValidatorTest {

    @Test
    void rejectsTwoEnabledImageSkills() {
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.findByIds(anyList())).thenReturn(List.of(
                skill(1L, "纪实摄影", "IMAGE", true), skill(2L, "扁平插画", "IMAGE", true)));
        SkillBindingValidator validator = new SkillBindingValidator(mapper);

        assertThatThrownBy(() -> validator.validate(List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("纪实摄影")
                .hasMessageContaining("扁平插画");
    }

    @Test
    void allowsSingleImageSkill() {
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.findByIds(anyList())).thenReturn(List.of(skill(1L, "纪实摄影", "IMAGE", true)));
        SkillBindingValidator validator = new SkillBindingValidator(mapper);

        assertThatCode(() -> validator.validate(List.of(1L))).doesNotThrowAnyException();
    }

    @Test
    void duplicateIdOfTheSameSkillIsNotAConflict() {
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.findByIds(anyList())).thenReturn(List.of(skill(1L, "纪实摄影", "IMAGE", true)));
        SkillBindingValidator validator = new SkillBindingValidator(mapper);

        assertThatCode(() -> validator.validate(List.of(1L, 1L))).doesNotThrowAnyException();
    }

    @Test
    void disabledImageSkillDoesNotCountAsBound() {
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.findByIds(anyList())).thenReturn(List.of(
                skill(1L, "纪实摄影", "IMAGE", true), skill(2L, "扁平插画", "IMAGE", false)));
        SkillBindingValidator validator = new SkillBindingValidator(mapper);

        assertThatCode(() -> validator.validate(List.of(1L, 2L))).doesNotThrowAnyException();
    }

    @Test
    void otherDimensionsMayStillBeStacked() {
        // 多个写作风格是有意叠加，不能在绑定处被拦下
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.findByIds(anyList())).thenReturn(List.of(
                skill(1L, "深度长文", "WRITING", true), skill(2L, "口语化", "WRITING", true)));
        SkillBindingValidator validator = new SkillBindingValidator(mapper);

        assertThatCode(() -> validator.validate(List.of(1L, 2L))).doesNotThrowAnyException();
    }

    @Test
    void readFailureSkipsValidationInsteadOfBlockingBinding() {
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.findByIds(anyList())).thenThrow(new RuntimeException("db down"));
        SkillBindingValidator validator = new SkillBindingValidator(mapper);

        assertThatCode(() -> validator.validate(List.of(1L, 2L))).doesNotThrowAnyException();
    }

    @Test
    void emptyBindingDoesNotTouchTheDatabase() {
        SkillMapper mapper = mock(SkillMapper.class);
        SkillBindingValidator validator = new SkillBindingValidator(mapper);

        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of())).doesNotThrowAnyException();
        verify(mapper, never()).findByIds(anyList());
    }

    private static Skill skill(Long id, String name, String dimension, boolean enabled) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        skill.setDimension(dimension);
        skill.setEnabled(enabled);
        return skill;
    }
}
