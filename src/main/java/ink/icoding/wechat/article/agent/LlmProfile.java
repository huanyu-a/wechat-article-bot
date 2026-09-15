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
    /**
     * 档案名称。声明 255 而非 100：与 {@code AgentDefinition.name} / {@code Skill.name} /
     * {@code WechatAccount.name} / {@code ScheduleTask.name} 共用 smart-mybatis 的字段名级列声明缓存
     * （键是字段名单键），五处不一致时结果取决于初始化顺序，且同步会发 {@code MODIFY COLUMN}
     * 把真实列改向赢家。实测 5 张表都是 varchar(255)，统一成 255 即与真实列宽一致，不发 DDL。
     */
    @TableField(length = 255)
    private String name;
    /**
     * 上游供应商。声明 50（与 {@code LlmConfig.provider} / {@code RenderConfig.provider} 一致）：
     * 三处同名，声明文本必须逐字相同，否则列声明缓存会在三者间二选一。
     * 实测三张表都是 varchar(50)，与声明同向。
     */
    @TableField(length = 50)
    private String provider;
    @TableField(length = 500)
    private String baseUrl;
    /**
     * 上游模型名。声明 200（与 {@code LlmConfig.modelName} 一致）：两处同名，
     * 实测 LLM_CONFIG 与 LLM_PROFILE 的 MODEL_NAME 都是 varchar(200)。
     * {@code LlmConfig} 那处此前靠默认值 255，与本类不一致——不一致即意味着
     * 同步逻辑会按赢家对另一张表发 {@code MODIFY COLUMN}。
     */
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
    /**
     * 兜底档案：主用档案全部不可用时的最后安全网（全局唯一，应用层保证，同 {@link #isDefault}）。
     *
     * <p>与默认档案的区别：默认档案是「没绑定档案的智能体用它」，兜底档案是
     * 「绑定档案和默认档案都失败了才用它」。因此兜底档案应当选**最稳、最便宜**的通道
     * （当前是免费的 hy4-preview），而不是最快的。
     */
    @JsonProperty("isFallback")
    private Boolean isFallback;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
