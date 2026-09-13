package ink.icoding.wechat.article.common;

import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 跨实例 LLM 名额租约 Mapper（I1，见 {@link LlmLease}）。
 *
 * <p>时间列按 smart-mybatis 存储 datetime 的格式（varchar，微秒精度）写入，字符串比较即时间先后的比较，
 * 因此 TTL 回收可以直接用 {@code HEARTBEAT_AT < ?} 完成——与 {@code TaskRunMapper.abortStale} 同源取舍。
 */
@Mapper
public interface LlmLeaseMapper extends SmartMapper<LlmLease> {
    DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    /** 占用槽位；槽位已被占用（主键冲突）时 INSERT IGNORE 返回 0。 */
    default boolean tryOccupy(long slotNo, String token, String owner, LocalDateTime now) {
        return executeSql("INSERT IGNORE INTO LLM_LEASE (SLOT_NO, TOKEN, OWNER, ACQUIRED_AT, HEARTBEAT_AT) "
                        + "VALUES (?, ?, ?, ?, ?)",
                slotNo, token, owner, format(now), format(now)) > 0;
    }

    /** 释放本人持有的槽位（按令牌，避免误删别人回收后重新占用的同一槽位）。 */
    default int releaseByToken(String token) {
        return executeSql("DELETE FROM LLM_LEASE WHERE TOKEN = ?", token);
    }

    /** 刷新心跳，向其它实例宣告本租约仍存活。 */
    default int heartbeat(String token, LocalDateTime now) {
        return executeSql("UPDATE LLM_LEASE SET HEARTBEAT_AT = ? WHERE TOKEN = ?", format(now), token);
    }

    /** 回收心跳早于 before 的租约（属主实例已崩溃/失联）。 */
    default int reclaimExpired(LocalDateTime before) {
        return executeSql("DELETE FROM LLM_LEASE WHERE HEARTBEAT_AT < ?", format(before));
    }

    /** 当前在飞租约数（观测用；DB 不可用时返回 -1）。 */
    @Select("SELECT COUNT(*) FROM LLM_LEASE")
    int leaseCount();

    static String format(LocalDateTime value) {
        return value == null ? null : value.format(TIMESTAMP_FORMAT);
    }
}
