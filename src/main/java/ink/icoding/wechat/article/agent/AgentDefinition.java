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
    /**
     * 智能体名称。声明 255 而非 100：{@code name} 这个字段名在 5 个实体里存在
     * （{@code WechatAccount} / {@code ScheduleTask} 用默认 255，本类与 {@code LlmProfile} / {@code Skill} 曾写 100），
     * 而 smart-mybatis 的列声明缓存按**字段名**单键共享——声明不一致时谁先初始化谁说了算，
     * 且 {@code MysqlDialect.buildAlterColumn} 会发 {@code MODIFY COLUMN}，赢家若是 100 就会把
     * 公众号名 / 任务名这些**用户输入**的列一起收窄到 100 字符。
     * 实测 5 张表的 NAME 列都已是 varchar(255)，统一成 255 与真实列宽一致：不发任何 DDL，结果与初始化顺序无关。
     */
    @TableField(length = 255)
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
