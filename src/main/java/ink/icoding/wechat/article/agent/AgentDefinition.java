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
 * 智能体定义（agent_definition 表，skills-agent-plan 4.2）。
 * 人设 persona 可编辑；核心工具协议为代码常量（AgentProtocols），物理隔离防用户改坏工具调用规则。
 * tool_keys 为工具组键 JSON 数组字符串（ToolRegistry 校验）；skill_ids 为该 agent 默认技能。
 */
@Data
@SmartMeta
@TableName("agent_definition")
public class AgentDefinition extends PO {
    @ID
    private Long id;
    @TableField(length = 50)
    private String code;
    @TableField(length = 100)
    private String name;
    @TableField(length = 30)
    private String stage;
    @TableField(length = 65535)
    private String persona;
    @TableField(length = 2000)
    private String toolKeys;
    @TableField(length = 2000)
    private String skillIds;
    private Long llmProfileId;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Boolean enabled;
    /** 序列化为 isBuiltin。 */
    @JsonProperty("isBuiltin")
    private Boolean isBuiltin;
    @TableField(length = 50)
    private String builtinKey;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
