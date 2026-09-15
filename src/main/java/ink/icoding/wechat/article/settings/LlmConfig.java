package ink.icoding.wechat.article.settings;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("llm_config")
public class LlmConfig extends PO {
    @ID
    private Long id;
    /**
     * 上游供应商。显式声明 50（与 {@code LlmProfile.provider} / {@code RenderConfig.provider} 一致）：
     * 三处同名，而 smart-mybatis 的列声明缓存按**字段名**单键共享，声明文本不一致时结果取决于
     * 实体初始化顺序，且同步会发 {@code MODIFY COLUMN} 把真实列改向赢家。
     * 实测三张表都是 varchar(50)，显式写出即与真实列宽同向。
     */
    @TableField(length = 50)
    private String provider;
    @TableField(length = 500)
    private String baseUrl;
    /**
     * 模型名。显式声明 200（与 {@code LlmProfile.modelName} 一致）：两处同名，
     * 实测 LLM_CONFIG / LLM_PROFILE 的 MODEL_NAME 都是 varchar(200)；
     * 此前这里靠默认值 255、LlmProfile 写 200，两者不一致。
     */
    @TableField(length = 200)
    private String modelName;
    @TableField(length = 65535)
    private String apiKeyEncrypted;
    @TableField(length = 500)
    private String imageBaseUrl;
    private String imageModelName;
    @TableField(length = 65535)
    private String imageApiKeyEncrypted;
    private Boolean enabled;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
