package ink.icoding.wechat.article.assistant;

import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import ink.icoding.smartmybatis.entity.expression.Where;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局助手用户偏好（目前只存「用哪个模型档案」）。
 *
 * <p>{@code selectFirst} 而不是 {@code select}：一个用户只该有一行。历史上若因并发首次写入
 * 产生了两行，{@code selectFirst} 也能取到一条而不会抛「结果不唯一」把整个面板卡死——
 * 偏好丢了的后果（回去用默认模型）远轻于面板打不开。
 */
@Mapper
public interface AssistantPreferenceMapper extends SmartMapper<AssistantPreference> {

    default AssistantPreference findByUserId(Long userId) {
        if (userId == null) return null;
        return selectFirst(Where.where(AssistantPreference::getUserId).eq(userId));
    }
}