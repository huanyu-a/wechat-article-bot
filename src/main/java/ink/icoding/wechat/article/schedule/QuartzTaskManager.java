package ink.icoding.wechat.article.schedule;

import org.quartz.*;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

@Service
public class QuartzTaskManager {
    private final Scheduler scheduler;
    private final ScheduleTaskMapper mapper;

    public QuartzTaskManager(Scheduler scheduler, ScheduleTaskMapper mapper) {
        this.scheduler = scheduler;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        for (ScheduleTask task : mapper.findEnabled()) schedule(task);
    }

    public void schedule(ScheduleTask task) {
        try {
            JobKey jobKey = jobKey(task.getId());
            if (scheduler.checkExists(jobKey)) scheduler.deleteJob(jobKey);
            if (!Boolean.TRUE.equals(task.getEnabled())) {
                mapper.updateNextRun(task.getId(), null);
                return;
            }
            JobDetail detail = JobBuilder.newJob(ScheduleTaskJob.class).withIdentity(jobKey)
                    .usingJobData("taskId", String.valueOf(task.getId())).build();
            // 停机错过触发时**跳过、不补跑**（次日按正常计划走）。这行是**显式**配置，不能删：
            // Quartz 的默认 SMART_POLICY 对 cron 触发器映射为 FIRE_ONCE_NOW（立即补跑一次），
            // 与 DO_NOTHING 行为相反。删掉它，重启后会把停机期间错过的任务立刻补跑一遍。
            // 反证见 QuartzMisfirePolicyTests（含真实 Quartz 的两种策略对照）。
            CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(task.getCronExpression())
                    .inTimeZone(TimeZone.getTimeZone(task.getTimezone()))
                    .withMisfireHandlingInstructionDoNothing();
            CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity("task-trigger-" + task.getId(), "article-tasks")
                    .forJob(detail).withSchedule(schedule).build();
            Date next = scheduler.scheduleJob(detail, trigger);
            mapper.updateNextRun(task.getId(), LocalDateTime.ofInstant(next.toInstant(), ZoneId.of(task.getTimezone())));
        } catch (Exception exception) {
            throw new IllegalArgumentException("定时表达式无效：" + exception.getMessage(), exception);
        }
    }

    public void delete(Long id) {
        try {
            scheduler.deleteJob(jobKey(id));
        } catch (SchedulerException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void completeFire(Long taskId, Date nextFireTime) {
        ScheduleTask task = mapper.findById(taskId);
        if (task == null) return;
        LocalDateTime next = nextFireTime == null ? null
                : LocalDateTime.ofInstant(nextFireTime.toInstant(), ZoneId.of(task.getTimezone()));
        mapper.completeScheduledFire(taskId, next);
    }

    private JobKey jobKey(Long id) {
        return new JobKey("article-task-" + id, "article-tasks");
    }
}
