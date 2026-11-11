package ink.icoding.wechat.article.assistant;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局助手的对话消息（按用户归档）。
 *
 * <p>为什么不复用 {@code ai_message}：那张表以 {@code article_id} 为外键语义，
 * 全局助手的消息没有文章可挂——塞 {@code NULL} 会让 {@code findByArticleId} 的匹配行为变得可疑。
 * 两表字段基本一致，但归属维度不同（用户 vs 文章），合并会让两个域互相污染。
 */
@Data
@SmartMeta
@TableName("assistant_message")
public class AssistantMessage extends PO {
    @ID
    private Long id;
    private Long userId;
    private String role;
    @TableField(length = 65535)
    private String content;
    private String status;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createdAt;
}