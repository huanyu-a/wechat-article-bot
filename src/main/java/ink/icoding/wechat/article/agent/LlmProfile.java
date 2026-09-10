package ink.icoding.wechat.article.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模型档案（llm_profile 表，skills-agent-plan 4.3）。
 * api_key 经 CryptoService（APP_SECRET_KEY）加密入库，API 仅返回掩码；
 * 全局唯一一条 is_default=true（应用层保证，LlmProfileService.setDefault 事务内重置）。
 */
@Data
@SmartMeta
@TableName("llm_profile")
public class LlmProfile extends PO {
    @ID
    private Long id;
    @TableField(length = 100)
    private String name;
    @TableField(length = 50)
    private String provider;
    @TableField(length = 500)
    private String baseUrl;
    @TableField(length = 200)
    private String modelName;
    @TableField(length = 65535)
    private String apiKeyEncrypted;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Boolean enabled;
    /** 序列化为 isDefault（Boolean 包装类型避免 is 前缀 getter 歧义）。 */
    @JsonProperty("isDefault")
    private Boolean isDefault;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
