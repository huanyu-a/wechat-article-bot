package ink.icoding.wechat.article.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I1：跨实例 LLM 名额租约的落库行为（{@link LlmLeaseMapper}）。
 *
 * <p>单测（{@code InFlightGateTest}）用 mock 验证了闸门逻辑，这里补真实数据库上的两条关键契约：
 * 固定槽位主键保证「同槽位只有一个赢家」（多实例不会各自放行），以及心跳超时的租约可被回收
 * （实例崩溃后名额最终归还）。TTL 回收依赖 HEARTBEAT_AT 的字符串比较，必须由真实写入格式兜底。
 */
@SpringBootTest
@ContextConfiguration(initializers = ink.icoding.wechat.article.MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class LlmLeaseMapperIntegrationTests {
    @Autowired
    private LlmLeaseMapper leaseMapper;

    @AfterEach
    void cleanUp() {
        leaseMapper.executeSql("DELETE FROM LLM_LEASE");
    }

    @Test
    void fixedSlotAdmitsExactlyOneOwnerAcrossInstances() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(leaseMapper.tryOccupy(1L, "token-a", "实例A", now)).isTrue();
        // 另一个实例抢同一槽位：主键冲突 → INSERT IGNORE 返回 0，不得顶掉已有租约
        assertThat(leaseMapper.tryOccupy(1L, "token-b", "实例B", now)).isFalse();
        assertThat(leaseMapper.leaseCount()).isEqualTo(1);
        assertThat(leaseMapper.releaseByToken("token-b")).isZero();
        assertThat(leaseMapper.releaseByToken("token-a")).isEqualTo(1);
    }

    @Test
    void expiredLeaseIsReclaimedAndSlotBecomesAvailableAgain() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(leaseMapper.tryOccupy(2L, "token-a", "实例A", now)).isTrue();

        // 相当于 TTL 已过：回收早于「now + 60s」的所有租约
        assertThat(leaseMapper.reclaimExpired(now.plusSeconds(60))).isEqualTo(1);
        assertThat(leaseMapper.leaseCount()).isZero();
        // 槽位归还后可被重新占用（这正是实例崩溃后名额最终归还的机制）
        assertThat(leaseMapper.tryOccupy(2L, "token-c", "实例C", now)).isTrue();
    }

    @Test
    void freshHeartbeatSurvivesReclaim() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(leaseMapper.tryOccupy(3L, "token-a", "实例A", now.minusSeconds(300))).isTrue();
        assertThat(leaseMapper.heartbeat("token-a", now)).isEqualTo(1);

        // 心跳新鲜（= 属主还活着）的租约不得被回收
        assertThat(leaseMapper.reclaimExpired(now.minusSeconds(120))).isZero();
        assertThat(leaseMapper.leaseCount()).isEqualTo(1);
    }
}
