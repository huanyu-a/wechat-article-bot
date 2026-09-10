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
    @TableField(length = 100)
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
