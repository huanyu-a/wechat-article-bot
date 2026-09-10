package ink.icoding.wechat.article.schedule;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import ink.icoding.wechat.article.account.WechatAccount;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("schedule_task")
public class ScheduleTask extends PO {
    @ID
    private Long id;
    private String name;
    private Long accountId;
    @TableField(exist = false, link = WechatAccount.class, linkField = "name", self = "accountId", target = "id")
    private String accountName;
    private Long coverAssetId;
    private String cronExpression;
    private String timezone;
    @TableField(length = 65535)
    private String aiPrompt;
    private String outputMode;
    /** 任务级创作技能 id 的 JSON 数组字符串（如 "[1,3]"），skills-agent-plan 4.5。 */
    @TableField(length = 2000)
    private String skillIds;
    /** 执行模式：SINGLE（默认，存量兼容）/ PIPELINE / COORDINATOR，skills-agent-plan 4.5。 */
    @TableField(length = 30)
    private String executionMode;
    /** 阶段编排：{"research":1,"writing":2,"illustration":3,"review":4} → agent_definition id；null=内置默认，0=跳过（仅配图/审核可跳过）。 */
    @TableField(length = 2000)
    private String stageAgents;
    /** 审核不通过的返工上限（默认 2）。 */
    private Integer maxRevisionRounds;
    private Boolean enabled;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
