package ink.icoding.wechat.article.agent;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentDefinitionMapper extends SmartMapper<AgentDefinition> {
    default List<AgentDefinition> findAll() {
        return select(Where.where().orderBy(AgentDefinition::getId).asc());
    }

    default AgentDefinition findById(Long id) {
        return selectById(id);
    }

    default AgentDefinition findByCode(String code) {
        return selectFirst(Where.where(AgentDefinition::getCode).eq(code));
    }

    default AgentDefinition findByBuiltinKey(String builtinKey) {
        return selectFirst(Where.where(AgentDefinition::getBuiltinKey).eq(builtinKey));
    }

    default List<AgentDefinition> findByStage(String stage) {
        return select(Where.where(AgentDefinition::getStage).eq(stage)
                .orderBy(AgentDefinition::getId).asc());
    }
}
