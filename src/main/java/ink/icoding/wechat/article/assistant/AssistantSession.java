package ink.icoding.wechat.article.assistant;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent4j 全局助手会话的完整序列化状态（一份/用户，reset 即清空这一行）。
 *
 * <p>与 {@code article_agent_session} 结构相同但归属维度不同：那边按 {@code article_id}，
 * 这边按 {@code user_id}——全局助手的上下文跨文章、跨页面，不能挂在某一篇文章上。
 */
@Data
@SmartMeta
@TableName("assistant_session")
public class AssistantSession extends PO {
    @ID
    private Long id;
    private Long userId;
    @TableField(length = 16_777_216)
    private String serializedSession;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}