package ink.icoding.wechat.article.skill;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SkillMapper extends SmartMapper<Skill> {
    default List<Skill> findAll() {
        return select(Where.where().orderBy(Skill::getId).asc());
    }

    default List<Skill> findByDimension(String dimension) {
        return select(Where.where(Skill::getDimension).eq(dimension).orderBy(Skill::getId).asc());
    }

    default List<Skill> findEnabled() {
        return select(Where.where(Skill::getEnabled).eq(true).orderBy(Skill::getId).asc());
    }

    default Skill findById(Long id) {
        return selectById(id);
    }

    default Skill findByName(String name) {
        return selectFirst(Where.where(Skill::getName).eq(name));
    }

    default Skill findByBuiltinKey(String builtinKey) {
        return selectFirst(Where.where(Skill::getBuiltinKey).eq(builtinKey));
    }

    default List<Skill> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return select(Where.where(Skill::getId).in(ids));
    }
}
