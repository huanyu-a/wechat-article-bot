package ink.icoding.wechat.article.common;

import ink.icoding.smartmybatis.entity.po.PO;
import ink.icoding.smartmybatis.entity.po.enums.ID;
import ink.icoding.smartmybatis.entity.po.enums.PrimaryGenerateType;
import ink.icoding.smartmybatis.entity.po.enums.SmartMeta;
import ink.icoding.smartmybatis.entity.po.enums.TableField;
import ink.icoding.smartmybatis.entity.po.enums.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跨实例 LLM 并发名额租约（I1）。
 *
 * <p>为什么需要落库：{@link InFlightGate} 原先是进程内信号量，Quartz 以
 * {@code isClustered=true} 多实例部署时，N 个实例各自放行 4 个，合计可能远超上游网关的并发上限 6。
 * 这里用一张固定槽位表把「谁在飞」变成跨实例可见的事实：槽位号 {@code SLOT_NO} 从 1 到上限，
 * 主键保证同槽位只能插一行，因此多个实例不会重复占用同一名额。
 *
 * <p>{@code HEARTBEAT_AT} 周期性刷新，实例崩溃后心跳停止，其它实例可据 TTL 回收该槽位——
 * 这是 agent4j 无 cancel（U1）前提下唯一能让名额最终归还的机制。
 */
@Data
@SmartMeta
@TableName("llm_lease")
public class LlmLease extends PO {
    /** 槽位号（1..上限），由申请方显式指定，故 generateType=INPUT。 */
    @ID(generateType = PrimaryGenerateType.INPUT)
    private Long slotNo;
    /** 租约令牌（UUID），释放/心跳按它定位。 */
    @TableField(length = 64)
    private String token;
    /** 申请方描述（任务/会话标签），仅用于诊断。 */
    @TableField(length = 255)
    private String owner;
    private LocalDateTime acquiredAt;
    private LocalDateTime heartbeatAt;
}
