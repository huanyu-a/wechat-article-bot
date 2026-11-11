package ink.icoding.wechat.article;

import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 定时任务的「启动 / 停止」。
 *
 * <p>这类开关最容易写成「只改了数据库里的一个布尔值，调度器却照跑」——界面显示已停止，
 * 后台每天照样创作并扣费。所以每个用例都**同时**断言三处：接口返回、库里的 {@code ENABLED}、
 * 以及 Quartz 里那个 Job 是否真的存在。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ScheduleTaskEnabledApiTests {

    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Scheduler scheduler;

    private String authorization() throws Exception {
        // 与其它集成测试共用同一个引导管理员口令（BootstrapAdminRunner 的默认配置）
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = TOKEN.matcher(json);
        assertThat(matcher.find()).as("登录应返回 token：" + json).isTrue();
        return "Bearer " + matcher.group(1);
    }

    private long createTask(String authorization, String cron) throws Exception {
        String created = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "启停验收任务",
                                  "cronExpression": "%s",
                                  "timezone": "Asia/Shanghai",
                                  "aiPrompt": "每天写一篇关于行业动态的文章。",
                                  "outputMode": "LOCAL_DRAFT",
                                  "enabled": true
                                }
                                """.formatted(cron)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher id = Pattern.compile("\"id\":(\\d+)").matcher(created);
        assertThat(id.find()).as("创建应返回 id：" + created).isTrue();
        return Long.parseLong(id.group(1));
    }

    /** 与 {@code QuartzTaskManager.jobKey} 保持一致：名字 + 分组都要对，只写名字查不到。 */
    private boolean jobExists(long taskId) throws Exception {
        return scheduler.checkExists(new JobKey("article-task-" + taskId, "article-tasks"));
    }

    private Boolean enabledInDb(long taskId) {
        return jdbcTemplate.queryForObject("SELECT ENABLED FROM SCHEDULE_TASK WHERE ID = ?",
                Boolean.class, taskId);
    }

    private Object nextRunInDb(long taskId) {
        return jdbcTemplate.queryForObject("SELECT NEXT_RUN_AT FROM SCHEDULE_TASK WHERE ID = ?",
                Object.class, taskId);
    }

    /**
     * 停止：库里置 false、Quartz 摘掉 Job、下次执行时间清空。
     *
     * <p>第三点单独断言，是因为它最容易漏：任务已经不会跑了，界面却还显示一个未来的
     * 「下次执行」，用户会一直等一个永远不会发生的运行。
     */
    @Test
    void stoppingRemovesTheQuartzJobAndClearsNextRun() throws Exception {
        String authorization = authorization();
        long taskId = createTask(authorization, "0 0 9 * * ?");
        assertThat(jobExists(taskId)).as("创建后应已注册调度").isTrue();
        assertThat(nextRunInDb(taskId)).as("启用中应有下次执行时间").isNotNull();

        mockMvc.perform(put("/api/tasks/" + taskId + "/enabled")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.nextRunAt").doesNotExist());

        assertThat(enabledInDb(taskId)).isFalse();
        assertThat(jobExists(taskId)).as("停止后 Quartz 里不应再有这个 Job").isFalse();
        assertThat(nextRunInDb(taskId)).as("不会执行了就不该留一个下次执行时间").isNull();
    }

    /** 重新启动：Job 回来、「下次执行」重新算出来。停用不该有副作用地毁掉任务。 */
    @Test
    void startingAgainReschedulesTheTask() throws Exception {
        String authorization = authorization();
        long taskId = createTask(authorization, "0 0 9 * * ?");

        mockMvc.perform(put("/api/tasks/" + taskId + "/enabled")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isOk());
        assertThat(jobExists(taskId)).isFalse();

        mockMvc.perform(put("/api/tasks/" + taskId + "/enabled")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        assertThat(enabledInDb(taskId)).isTrue();
        assertThat(jobExists(taskId)).as("重新启用后应重新注册调度").isTrue();
        assertThat(nextRunInDb(taskId)).as("重新启用后应重新算出下次执行时间").isNotNull();
    }

    /**
     * 停用只改状态，**不许**碰其它字段。
     *
     * <p>这正是「让前端提交整份表单」会踩的坑：读漏一个字段就静默清空。
     */
    @Test
    void stoppingDoesNotTouchOtherFields() throws Exception {
        String authorization = authorization();
        long taskId = createTask(authorization, "0 0 9 * * ?");

        mockMvc.perform(put("/api/tasks/" + taskId + "/enabled")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/" + taskId).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cronExpression").value("0 0 9 * * ?"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.aiPrompt").value("每天写一篇关于行业动态的文章。"))
                .andExpect(jsonPath("$.data.outputMode").value("LOCAL_DRAFT"));
    }

    /**
     * 启用一个「已经没有未来触发时间」的计划必须被拒。
     *
     * <p>否则用户点一下「启动」，看到绿灯亮起，而它永远不会跑——这比直接报错更难发现。
     */
    @Test
    void startingAnExhaustedScheduleIsRejected() throws Exception {
        String authorization = authorization();
        // 先把任务建成 2099 年某天，再直接改库改成「过去的时间」，绕过创建时的校验
        long taskId = createTask(authorization, "0 0 9 * * ?");
        mockMvc.perform(put("/api/tasks/" + taskId + "/enabled")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE SCHEDULE_TASK SET CRON_EXPRESSION = ? WHERE ID = ?",
                "0 0 9 1 1 ? 2020", taskId);

        mockMvc.perform(put("/api/tasks/" + taskId + "/enabled")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(enabledInDb(taskId)).as("被拒后状态必须保持不变").isFalse();
    }

    /** 缺字段要说清楚，而不是 500。 */
    @Test
    void missingEnabledFieldIsRejected() throws Exception {
        String authorization = authorization();
        long taskId = createTask(authorization, "0 0 9 * * ?");

        mockMvc.perform(put("/api/tasks/" + taskId + "/enabled")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** 任务不存在时 404 语义（BusinessException 默认），不能假装成功。 */
    @Test
    void unknownTaskIsRejected() throws Exception {
        String authorization = authorization();

        mockMvc.perform(put("/api/tasks/999999/enabled")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }
}
