package ink.icoding.wechat.article.schedule;

import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.triggers.CronTriggerImpl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 错过触发（misfire）策略的确定性验证——用来取代「等一个自然日 09:00 观察」。
 *
 * <p><b>为什么需要这个类</b>：{@code scheduled-task-reliability-round.md} §八.4 把「停机错过 09:00
 * 不补跑」的根因写成「项目**未写 misfire 指令**，于是走 {@code SMART_POLICY}——对 cron 触发器
 * 它等价于 {@code DO_NOTHING}」。这句话两处都不成立：
 *
 * <ol>
 *   <li>{@link QuartzTaskManager#schedule} **显式**调用了
 *       {@code withMisfireHandlingInstructionDoNothing()}（自 initial commit 起就在）；</li>
 *   <li>Quartz 的 {@code SMART_POLICY} 对 {@code CronTrigger} **并不等价于 DO_NOTHING**，
 *       而是映射为 {@code FIRE_ONCE_NOW}（立即补跑一次）。</li>
 * </ol>
 *
 * <p>也就是说：观测到的「不补跑」不是「没配所以走默认」，而是**这行显式配置**的结果；
 * 若把它删掉，行为会**反转**成补跑。这个类用真实的 Quartz（RAMJobStore，不需要数据库）
 * 把这条钉死，比等自然日可靠得多。
 */
class QuartzMisfirePolicyTests {

    private static final String TZ = "Asia/Shanghai";
    private static final String DAILY_9AM = "0 0 9 * * ?";

    /** 接线：{@link QuartzTaskManager#schedule} 必须把 DO_NOTHING 写进真实的 trigger。 */
    @Test
    void scheduledTriggerPinsDoNothingMisfireInstruction() throws Exception {
        Scheduler scheduler = newScheduler();
        try {
            QuartzTaskManager manager = new QuartzTaskManager(scheduler, mock(ScheduleTaskMapper.class));
            manager.schedule(task(42L, DAILY_9AM));

            Trigger trigger = scheduler.getTrigger(new TriggerKey("task-trigger-42", "article-tasks"));

            assertThat(trigger).as("调度后应能查到 trigger").isNotNull();
            assertThat(trigger.getMisfireInstruction())
                    .as("错过触发必须是 DO_NOTHING（跳过、不补跑）")
                    .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
        } finally {
            scheduler.shutdown(true);
        }
    }

    /** 行为：DO_NOTHING 下错过的那一次被**跳过**，下一次触发仍在未来（不是「现在补跑」）。 */
    @Test
    void doNothingSkipsTheMissedFire() {
        CronTriggerImpl trigger = dailyTrigger(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);

        // Calendar 参数只用于跳过「被排除的时间」，这里不设排除日，传 null 即可
        trigger.updateAfterMisfire(null);

        assertThat(trigger.getNextFireTime())
                .as("跳过而不是补跑：下一次触发必须还在未来")
                .isAfter(new Date());
    }

    /**
     * **反证**：{@code SMART_POLICY} 对 cron 会**立即补跑**——所以文档里
     * 「SMART_POLICY 等价于 DO_NOTHING」的说法是错的，两者行为相反。
     *
     * <p>这条同时说明：删掉 {@code withMisfireHandlingInstructionDoNothing()} 会真的改变行为，
     * 而不是「反正默认也一样」。
     */
    @Test
    void smartPolicyWouldHaveCaughtUpInsteadOfSkipping() {
        CronTriggerImpl trigger = dailyTrigger(Trigger.MISFIRE_INSTRUCTION_SMART_POLICY);

        // Calendar 参数只用于跳过「被排除的时间」，这里不设排除日，传 null 即可
        trigger.updateAfterMisfire(null);

        assertThat(trigger.getNextFireTime())
                .as("SMART_POLICY 对 cron 映射为 FIRE_ONCE_NOW：应立即补跑（与 DO_NOTHING 相反）")
                .isCloseTo(new Date(), 10_000L);
    }

    /** 一个已错过一次触发的每日 09:00 触发器。 */
    private static CronTriggerImpl dailyTrigger(int misfireInstruction) {
        CronTriggerImpl trigger = new CronTriggerImpl();
        try {
            trigger.setCronExpression(DAILY_9AM);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        trigger.setTimeZone(TimeZone.getTimeZone(TZ));
        trigger.setMisfireInstruction(misfireInstruction);
        // 模拟「昨天的 09:00 已经触发过，今天 09:00 因为停机被错过」
        LocalDateTime missed = LocalDateTime.now(ZoneId.of(TZ)).minusDays(1).withHour(9)
                .withMinute(0).withSecond(0).withNano(0);
        trigger.setStartTime(Date.from(missed.atZone(ZoneId.of(TZ)).toInstant()));
        trigger.setNextFireTime(Date.from(missed.atZone(ZoneId.of(TZ)).toInstant()));
        return trigger;
    }

    private static ScheduleTask task(Long id, String cron) {
        ScheduleTask task = new ScheduleTask();
        task.setId(id);
        task.setName("测试任务");
        task.setCronExpression(cron);
        task.setTimezone(TZ);
        task.setEnabled(true);
        return task;
    }

    /** 真实的 Quartz 调度器，用 RAMJobStore：不依赖数据库、不依赖集群。 */
    private static Scheduler newScheduler() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "MisfirePolicyTestScheduler");
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        return new StdSchedulerFactory(properties).getScheduler();
    }
}
