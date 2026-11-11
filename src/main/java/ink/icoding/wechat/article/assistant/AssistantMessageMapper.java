package ink.icoding.wechat.article.assistant;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mapper
public interface AssistantMessageMapper extends SmartMapper<AssistantMessage> {
    /** 本人的全部助手消息（时间正序，供历史页与上下文重建）。 */
    default List<AssistantMessage> findByUserId(Long userId) {
        return select(Where.where(AssistantMessage::getUserId).eq(userId)
                .orderBy(AssistantMessage::getCreatedAt).asc()
                .orderBy(AssistantMessage::getId).asc());
    }

    /**
     * 本人最近的 {@code limit} 条消息，**按时间正序**返回（最早的在前）。
     *
     * <p>为什么先 desc + limit 再 reverse：要的是「最近 N 条」，SQL 必须先取新的一端
     * （{@code asc + limit} 取到的是最早 N 条，续接会话时会丢掉刚说的话）；而调用方需要
     * 时间正序的对话序列。排序方向只在这一处定义，避免调用方各自再排一遍导致两处方向不一致。
     */
    default List<AssistantMessage> findRecent(Long userId, int limit) {
        List<AssistantMessage> newestFirst = select(Where.where(AssistantMessage::getUserId).eq(userId)
                .orderBy(AssistantMessage::getCreatedAt).desc()
                .orderBy(AssistantMessage::getId).desc()
                .limit(Math.max(1, limit)));
        if (newestFirst == null || newestFirst.isEmpty()) return List.of();
        List<AssistantMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    /** 全表 token 合计（prompt + completion），空值按 0 计。 */
    default long totalTokens() {
        return selectAll().stream().mapToLong(message ->
                value(message.getPromptTokens()) + value(message.getCompletionTokens())).sum();
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}