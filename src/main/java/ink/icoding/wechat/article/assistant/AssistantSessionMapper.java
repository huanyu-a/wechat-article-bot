package ink.icoding.wechat.article.assistant;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssistantSessionMapper extends SmartMapper<AssistantSession> {
    /** 一份会话/用户；取最早一行，存在重复行时不会因 selectOne 抛异常。 */
    default AssistantSession findByUserId(Long userId) {
        return selectFirst(Where.where(AssistantSession::getUserId).eq(userId)
                .orderBy(AssistantSession::getId).asc());
    }
}