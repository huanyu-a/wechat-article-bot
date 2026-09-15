package ink.icoding.wechat.article.account;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("wechat_account")
public class WechatAccount extends PO {
    @ID
    private Long id;
    /**
     * 公众号名称。显式声明 255：{@code name} 在 5 个实体里同名，共用 smart-mybatis 的
     * 字段名级列声明缓存，声明文本不一致时结果取决于初始化顺序（且可能发 {@code MODIFY COLUMN}
     * 把用户输入的这一列收窄）。255 与实测列宽一致，显式写出即让「巧合」变成保证。
     */
    @TableField(length = 255)
    private String name;
    private String appId;
    @TableField(length = 65535)
    private String appSecretEncrypted;
    private String originalId;
    private String accountType;
    private Boolean verified;
    @TableField(length = 500)
    private String avatarUrl;
    private String defaultAuthor;
    private String defaultStyle;
    /** 账号默认创作技能 id 的 JSON 数组字符串，skills-agent-plan 4.5。 */
    @TableField(length = 2000)
    private String skillIds;
    private String status;
    private String connectionStatus;
    @TableField(length = 65535)
    private String capabilities;
    @TableField(length = 65535)
    private String tokenEncrypted;
    private LocalDateTime tokenExpiresAt;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
