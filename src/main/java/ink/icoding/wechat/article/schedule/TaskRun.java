package ink.icoding.wechat.article.schedule;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@SmartMeta
@TableName("task_run")
public class TaskRun extends PO {
    @ID
    private Long id;
    private Long taskId;
    private String triggerType;
    private String status;
    private Integer fetchedCount;
    private Integer generatedCount;
    private Long articleId;
    private Integer toolCallCount;
    /** 本次运行实际使用的执行模式（SINGLE/PIPELINE/COORDINATOR）。 */
    @TableField(length = 30)
    private String mode;
    /**
     * 执行该运行的实例标识（I2）：进程被杀后，没有实例标识就只能按「开始时间超过阈值」猜孤儿，
     * 自愈窗口是小时级；有了它（配心跳）才能区分「属主还活着」与「属主已崩溃」。
     */
    @TableField(length = 64)
    private String instanceId;
    /**
     * 运行中心跳（I2）：执行线程每 {@code progress-flush-seconds} 秒刷新一次。
     * 心跳停止即证明属主实例已失联，其它实例可据此把自愈窗口压到分钟级。
     */
    private LocalDateTime heartbeatAt;
    /** 各阶段 best-effort 摘要：[{stage,agentName,toolCalls,durationMs,status}]。 */
    @TableField(length = 65535)
    private String stagesSummary;
    @TableField(length = 65535)
    private String message;
    @TableField(length = 65535)
    private String executionLog;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
