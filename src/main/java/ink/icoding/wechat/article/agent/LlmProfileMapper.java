package ink.icoding.wechat.article.agent;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LlmProfileMapper extends SmartMapper<LlmProfile> {
    default List<LlmProfile> findAll() {
        return select(Where.where().orderBy(LlmProfile::getId).asc());
    }

    default LlmProfile findById(Long id) {
        return selectById(id);
    }

    default LlmProfile findByName(String name) {
        return selectFirst(Where.where(LlmProfile::getName).eq(name));
    }

    /** 全局唯一默认档案（应用层保证唯一，取第一条容错）。 */
    default LlmProfile findDefault() {
        return selectFirst(Where.where(LlmProfile::getIsDefault).eq(true)
                .orderBy(LlmProfile::getId).asc());
    }

    default LlmProfile findFirst() {
        return selectFirst(Where.where().orderBy(LlmProfile::getId).asc());
    }
}
