package ink.icoding.wechat.article.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创作技能（skill 表，skills-agent-plan 4.1）。
 * dimension 必须在白名单内（SkillDimensions）；engine 仅 LAYOUT 维度生效（PROMPT/MARKFLOW 双轨，5.10.2）。
 */
@Data
@SmartMeta
@TableName("skill")
public class Skill extends PO {
    @ID
    private Long id;
    /**
     * 技能名称。声明 255 而非 100：{@code name} 在 5 个实体里同名，共用 smart-mybatis 的
     * 字段名级列声明缓存，不一致时按初始化顺序二选一并可能发 {@code MODIFY COLUMN}。
     * 实测 5 张表都是 varchar(255)，统一成 255 与真实列宽一致。
     */
    @TableField(length = 255)
    private String name;
    @TableField(length = 30)
    private String dimension;
    @TableField(length = 500)
    private String description;
    @TableField(length = 65535)
    private String content;
    @TableField(length = 20)
    private String engine;
    @TableField(length = 65535)
    private String engineConfig;
    private Boolean enabled;
    /** 序列化为 isBuiltin（Boolean 包装类型避免 is 前缀 getter 歧义）。 */
    @JsonProperty("isBuiltin")
    private Boolean isBuiltin;
    @TableField(length = 50)
    private String builtinKey;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
