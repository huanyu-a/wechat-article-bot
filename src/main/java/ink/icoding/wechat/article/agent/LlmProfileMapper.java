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

    /** 全局唯一兜底档案（应用层保证唯一，取第一条容错）；未设置返回 null。 */
    default LlmProfile findFallback() {
        return selectFirst(Where.where(LlmProfile::getIsFallback).eq(true)
                .orderBy(LlmProfile::getId).asc());
    }

    /** 已启用档案（按 id 升序）：故障切换链的最后一段，把「剩下的可能性」都用上。 */
    default List<LlmProfile> findEnabled() {
        return select(Where.where(LlmProfile::getEnabled).eq(true).orderBy(LlmProfile::getId).asc());
    }

    default LlmProfile findFirst() {
        return selectFirst(Where.where().orderBy(LlmProfile::getId).asc());
    }
}
