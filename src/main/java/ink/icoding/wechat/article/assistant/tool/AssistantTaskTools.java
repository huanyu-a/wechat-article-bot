package ink.icoding.wechat.article.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.schedule.ScheduleTask;
import ink.icoding.wechat.article.schedule.ScheduleTaskService;
import ink.icoding.wechat.article.schedule.TaskRun;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局助手的定时任务工具（docs/dev/global-assistant-plan.md §4.3）。
 *
 * <p><b>为什么每一条写工具都是「先读全量、再整体回写」</b>：{@code ScheduleTaskService.apply(...)}
 * 把请求**整行覆盖**到实体（每个字段无条件 set），而 {@code TaskRequest} 的 name / cronExpression /
 * aiPrompt 都是 {@code @NotBlank}。于是「只把 enabled 改成 false」这种请求若只带一个字段，
 * 其余字段会变成 null——轻则被 {@code @NotBlank} 拒绝，重则把 Cron 与提示词**静默清空**
 * （服务层还会顺手按新值重排 Quartz 计划）。所以每个写工具的第一步都是读当前全量，
 * 再用入参覆盖其中几项。
 *
 * <p><b>为什么不提供删除</b>：删除任务会连带丢掉历史运行记录与 Quartz 计划，属不可逆操作，
 * 必须由人在界面上确认（plan §1.2）。「先别跑了」这个真实诉求由
 * {@code set_schedule_task_enabled} 覆盖——停用优于删除。
 *
 * <p><b>{@code userId} 为什么由装配点传入</b>：{@code create} 要写 {@code created_by}，而它读的是
 * {@code SecurityContext}；工具跑在智能体的工作线程里，那里没有 HTTP 请求的登录上下文。
 * 详见 {@link AssistantArticleTools#callAsUser}（同一份处理的唯一实现处）。
 */
public final class AssistantTaskTools {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 运行日志回传上限：日志可达数万字符，整段塞进上下文会挤掉后续轮次。 */
    private static final int LOG_LIMIT = 2000;

    /** 单次最多回传的运行记录条数（服务层自身按 100 条封顶）。 */
    private static final int MAX_RUNS = 50;

    private AssistantTaskTools() {
    }

    /** 七个任务工具：两个只读（含运行记录）+ 五个写（含删除）。 */
    public static List<Tool> all(ScheduleTaskService taskService, Long userId) {
        return List.of(new ListScheduleTasksTool(taskService), new ListTaskRunsTool(taskService),
                new CreateScheduleTaskTool(taskService, userId), new UpdateScheduleTaskTool(taskService),
                new SetScheduleTaskEnabledTool(taskService), new RunScheduleTaskNowTool(taskService),
                new DeleteScheduleTaskTool(taskService));
    }

    @ToolInfo(name = "delete_schedule_task",
            description = "删除定时任务（连同它的调度计划）。运行记录与已产出的文章不受影响。"
                    + "只是暂时不想让它跑，用 set_schedule_task_enabled 停用即可，不必删除。"
                    + "删除不可撤销，执行前必须向用户确认任务名称。")
    public static class DeleteScheduleTaskTool implements Tool<DeleteScheduleTaskParam> {
        private final ScheduleTaskService taskService;

        public DeleteScheduleTaskTool(ScheduleTaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public String execute(DeleteScheduleTaskParam param) {
            if (param.getTaskId() == null) {
                throw new IllegalArgumentException("请给出任务 id（可先用 list_schedule_tasks 查找）");
            }
            ScheduleTask task = taskService.required(param.getTaskId());
            taskService.delete(param.getTaskId());
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("deleted", true);
            row.put("taskId", task.getId());
            row.put("name", task.getName());
            return json(row);
        }
    }

    @Data
    public static class DeleteScheduleTaskParam extends ToolParam {
        @Param(description = "要删除的定时任务 id")
        private Long taskId;
    }

    @ToolInfo(name = "list_schedule_tasks",
            description = "列出全部定时任务（含停用的）。返回 id、名称、Cron、时区、输出方式、执行模式、启用状态、"
                    + "下次触发时间与公众号名；要改某个任务先用它拿到 id。")
    public static class ListScheduleTasksTool implements Tool<ListScheduleTasksParam> {
        private final ScheduleTaskService taskService;

        public ListScheduleTasksTool(ScheduleTaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public String execute(ListScheduleTasksParam param) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ScheduleTask task : taskService.list()) {
                if (param.getAccountId() != null && !param.getAccountId().equals(task.getAccountId())) continue;
                if (param.getEnabled() != null && !param.getEnabled().equals(task.getEnabled())) continue;
                items.add(taskSummary(task));
            }
            return json(AssistantToolBudget.page(items, param.getLimit(), "定时任务"));
        }
    }

    @Data
    public static class ListScheduleTasksParam extends ToolParam {
        @Param(required = false, description = "按公众号 id 过滤，留空列出全部")
        private Long accountId;
        @Param(required = false, description = "true 只看已启用、false 只看已停用，留空全部")
        private Boolean enabled;
        @Param(required = false, description = "返回条数，1 到 " + AssistantToolBudget.MAX_LIMIT
                + "（默认 " + AssistantToolBudget.DEFAULT_LIMIT + "）")
        private Integer limit;
    }

    @ToolInfo(name = "list_task_runs",
            description = "列出某个定时任务最近的运行记录（新的在前）。返回 id、执行模式、状态、开始/结束时间、"
                    + "工具调用数与执行日志摘要（超过 " + LOG_LIMIT + " 字符会截断并注明）。")
    public static class ListTaskRunsTool implements Tool<ListTaskRunsParam> {
        private final ScheduleTaskService taskService;

        public ListTaskRunsTool(ScheduleTaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public String execute(ListTaskRunsParam param) {
            if (param.getTaskId() == null) {
                throw new IllegalArgumentException("请给出任务 id（可先用 list_schedule_tasks 查找）");
            }
            int limit = Math.min(MAX_RUNS, Math.max(1, value(param.getLimit(), 10)));
            List<Map<String, Object>> items = new ArrayList<>();
            for (TaskRun run : taskService.runs(param.getTaskId())) {
                if (items.size() >= limit) break;
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", run.getId());
                view.put("mode", run.getMode());
                view.put("triggerType", run.getTriggerType());
                view.put("status", run.getStatus());
                view.put("startedAt", text(run.getStartedAt()));
                view.put("finishedAt", text(run.getFinishedAt()));
                view.put("toolCallCount", run.getToolCallCount());
                view.put("generatedCount", run.getGeneratedCount());
                view.put("articleId", run.getArticleId());
                view.put("message", run.getMessage());
                view.put("executionLog", truncate(run.getExecutionLog()));
                items.add(view);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", param.getTaskId());
            result.put("count", items.size());
            result.put("limit", limit);
            result.put("items", items);
            return json(result);
        }
    }

    @Data
    public static class ListTaskRunsParam extends ToolParam {
        @Param(description = "任务 id")
        private Long taskId;
        @Param(required = false, description = "返回最近多少条运行记录，1 到 " + MAX_RUNS + "（默认 10）")
        private Integer limit;
    }

    @ToolInfo(name = "create_schedule_task",
            description = "新建定时任务并立即纳入调度。创建前必须与用户确认名称、Cron、时区与输出方式；"
                    + "Cron 必须能算出未来触发时间（如 0 0 8 * * ? 表示每天 8 点）。"
                    + "outputMode 取 LOCAL_DRAFT / WECHAT_DRAFT / AUTO_PUBLISH，executionMode 取 SINGLE / PIPELINE / COORDINATOR。"
                    + "本工具只建任务，不代替用户决定发布哪一篇。")
    public static class CreateScheduleTaskTool implements Tool<CreateScheduleTaskParam> {
        private final ScheduleTaskService taskService;
        private final Long userId;

        public CreateScheduleTaskTool(ScheduleTaskService taskService, Long userId) {
            this.taskService = taskService;
            this.userId = userId;
        }

        @Override
        public String execute(CreateScheduleTaskParam param) {
            String name = blankToNull(param.getName());
            String cron = blankToNull(param.getCronExpression());
            String aiPrompt = blankToNull(param.getAiPrompt());
            if (name == null) throw new IllegalArgumentException("请给出任务名称");
            if (cron == null) throw new IllegalArgumentException("请给出 Cron 表达式（如 0 0 8 * * ?）");
            if (aiPrompt == null) throw new IllegalArgumentException("请给出 AI 提示词：任务每次运行都会按它创作");
            ScheduleTaskService.TaskRequest request = new ScheduleTaskService.TaskRequest(
                    name, param.getAccountId(), param.getCoverAssetId(), cron,
                    blankToNull(param.getTimezone()), aiPrompt,
                    blankToNull(param.getOutputMode()), param.getSkillIds(), param.getEnabled(),
                    blankToNull(param.getExecutionMode()), null, param.getMaxRevisionRounds());
            ScheduleTask created = AssistantArticleTools.callAsUser(userId, () -> taskService.create(request));
            return json(taskSummary(taskService.required(created.getId())));
        }
    }

    @Data
    public static class CreateScheduleTaskParam extends ToolParam {
        @Param(description = "任务名称")
        private String name;
        @Param(description = "Cron 表达式（Quartz 六/七段语法，必须能算出未来触发时间）")
        private String cronExpression;
        @Param(description = "AI 提示词：描述每次运行要创作什么")
        private String aiPrompt;
        @Param(required = false, description = "时区，默认 Asia/Shanghai")
        private String timezone;
        @Param(required = false, description = "目标公众号 id；outputMode 不是 LOCAL_DRAFT 时必填")
        private Long accountId;
        @Param(required = false, description = "输出方式：LOCAL_DRAFT（默认）/ WECHAT_DRAFT / AUTO_PUBLISH")
        private String outputMode;
        @Param(required = false, description = "执行模式：SINGLE（默认）/ PIPELINE / COORDINATOR")
        private String executionMode;
        @Param(required = false, description = "任务级创作技能 id 列表")
        private List<Long> skillIds;
        @Param(required = false, description = "封面素材 assetId")
        private Long coverAssetId;
        @Param(required = false, description = "审核不通过的返工上限，0 到 5（服务层默认 2）")
        private Integer maxRevisionRounds;
        @Param(required = false, description = "是否启用，默认 true")
        private Boolean enabled;
    }

    @ToolInfo(name = "update_schedule_task",
            description = "修改定时任务。未提供的字段一律沿用当前值（本工具会先读取当前任务再整体回写）。"
                    + "必须先用 list_schedule_tasks 确认改的是哪个任务；改 Cron 后服务层会校验未来触发时间。")
    public static class UpdateScheduleTaskTool implements Tool<UpdateScheduleTaskParam> {
        private final ScheduleTaskService taskService;

        public UpdateScheduleTaskTool(ScheduleTaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public String execute(UpdateScheduleTaskParam param) {
            if (param.getTaskId() == null) {
                throw new IllegalArgumentException("请给出任务 id（可先用 list_schedule_tasks 查找）");
            }
            ScheduleTaskService.TaskView current = ScheduleTaskService.toView(taskService.required(param.getTaskId()));
            // 关键：apply() 覆盖每个字段，只带部分字段的请求会把 Cron/提示词清空，因此这里先补齐当前值
            ScheduleTaskService.TaskRequest request = new ScheduleTaskService.TaskRequest(
                    pick(blankToNull(param.getName()), current.name()),
                    param.getAccountId() == null ? current.accountId() : param.getAccountId(),
                    param.getCoverAssetId() == null ? current.coverAssetId() : param.getCoverAssetId(),
                    pick(blankToNull(param.getCronExpression()), current.cronExpression()),
                    pick(blankToNull(param.getTimezone()), current.timezone()),
                    pick(blankToNull(param.getAiPrompt()), current.aiPrompt()),
                    pick(blankToNull(param.getOutputMode()), current.outputMode()),
                    param.getSkillIds() == null ? current.skillIds() : param.getSkillIds(),
                    current.enabled(),
                    pick(blankToNull(param.getExecutionMode()), current.executionMode()),
                    current.stageAgents(),
                    param.getMaxRevisionRounds() == null ? current.maxRevisionRounds() : param.getMaxRevisionRounds());
            taskService.update(current.id(), request);
            return json(taskSummary(taskService.required(current.id())));
        }
    }

    @Data
    public static class UpdateScheduleTaskParam extends ToolParam {
        @Param(description = "任务 id")
        private Long taskId;
        @Param(required = false, description = "新名称；不传保持不变")
        private String name;
        @Param(required = false, description = "新 Cron 表达式；不传保持不变")
        private String cronExpression;
        @Param(required = false, description = "新 AI 提示词；不传保持不变")
        private String aiPrompt;
        @Param(required = false, description = "新时区；不传保持不变")
        private String timezone;
        @Param(required = false, description = "新输出方式；不传保持不变")
        private String outputMode;
        @Param(required = false, description = "新执行模式；不传保持不变")
        private String executionMode;
        @Param(required = false, description = "新技能 id 列表；不传保持不变")
        private List<Long> skillIds;
        @Param(required = false, description = "新公众号 id；不传保持不变")
        private Long accountId;
        @Param(required = false, description = "新封面素材 assetId；不传保持不变")
        private Long coverAssetId;
        @Param(required = false, description = "返工上限 0 到 5；不传保持不变")
        private Integer maxRevisionRounds;
    }

    /**
     * 启用/停用。
     *
     * <p>看起来只是改一个布尔，但同样走整行回写——{@code apply()} 不区分「这次改了哪一位」，
     * 只带 {@code enabled} 的请求会把 Cron 与提示词抹成 null。因此这里复用同一条「读全量再回写」的路子，
     * 而不是贪省事构造一个只含 enabled 的 {@code TaskRequest}。
     */
    @ToolInfo(name = "set_schedule_task_enabled",
            description = "启用或停用定时任务（停用即从调度中摘除计划，但保留任务定义与历史运行记录）。"
                    + "只改启用状态，其他字段不受影响；重新启用时会校验该计划是否还有未来触发时间。")
    public static class SetScheduleTaskEnabledTool implements Tool<SetScheduleTaskEnabledParam> {
        private final ScheduleTaskService taskService;

        public SetScheduleTaskEnabledTool(ScheduleTaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public String execute(SetScheduleTaskEnabledParam param) {
            if (param.getTaskId() == null) {
                throw new IllegalArgumentException("请给出任务 id（可先用 list_schedule_tasks 查找）");
            }
            if (param.getEnabled() == null) {
                throw new IllegalArgumentException("请给出 enabled：true 启用，false 停用");
            }
            // 直接调 setEnabled：它只改一个字段。原来这里是「读全量 → 拼 TaskRequest → 整体回写」，
            // 与 UI 的开关共用同一个入口，两边不会再走不同的写路径。
            taskService.setEnabled(param.getTaskId(), param.getEnabled());
            return json(taskSummary(taskService.required(param.getTaskId())));
        }
    }

    @Data
    public static class SetScheduleTaskEnabledParam extends ToolParam {
        @Param(description = "任务 id")
        private Long taskId;
        @Param(description = "true 启用、false 停用")
        private Boolean enabled;
    }

    /**
     * 立即跑一次。
     *
     * <p>服务层是**异步启动**（{@code TaskExecutionService.start} 把执行交给线程池后立刻返回运行行），
     * 所以这里回的是「已受理的运行 id 与状态」，不是创作结果。描述必须讲清这一点，
     * 否则模型会向用户宣称「已经生成好了」。
     */
    @ToolInfo(name = "run_schedule_task_now",
            description = "立即触发一次定时任务（异步执行，立刻返回运行记录 id 与状态 RUNNING，不代表已生成完）。"
                    + "本结果只表示已受理；要确认结果请在稍后用 list_task_runs 查这条运行。")
    public static class RunScheduleTaskNowTool implements Tool<RunScheduleTaskNowParam> {
        private final ScheduleTaskService taskService;

        public RunScheduleTaskNowTool(ScheduleTaskService taskService) {
            this.taskService = taskService;
        }

        @Override
        public String execute(RunScheduleTaskNowParam param) {
            if (param.getTaskId() == null) {
                throw new IllegalArgumentException("请给出任务 id（可先用 list_schedule_tasks 查找）");
            }
            TaskRun run = taskService.run(param.getTaskId());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runId", run.getId());
            result.put("taskId", run.getTaskId());
            result.put("status", run.getStatus());
            result.put("mode", run.getMode());
            result.put("triggerType", run.getTriggerType());
            result.put("startedAt", text(run.getStartedAt()));
            result.put("note", "任务已在后台开始执行，本结果只表示已受理；稍后用 list_task_runs 查看结果");
            return json(result);
        }
    }

    @Data
    public static class RunScheduleTaskNowParam extends ToolParam {
        @Param(description = "任务 id")
        private Long taskId;
    }

    private static Map<String, Object> taskSummary(ScheduleTask task) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", task.getId());
        view.put("name", task.getName());
        view.put("accountId", task.getAccountId());
        view.put("accountName", task.getAccountName());
        view.put("coverAssetId", task.getCoverAssetId());
        view.put("cronExpression", task.getCronExpression());
        view.put("timezone", task.getTimezone());
        view.put("aiPrompt", task.getAiPrompt());
        view.put("outputMode", task.getOutputMode());
        view.put("executionMode", task.getExecutionMode());
        view.put("skillIds", WechatAccountService.parseSkillIds(task.getSkillIds()));
        view.put("enabled", task.getEnabled());
        view.put("maxRevisionRounds", task.getMaxRevisionRounds());
        view.put("lastRunAt", text(task.getLastRunAt()));
        view.put("nextRunAt", text(task.getNextRunAt()));
        view.put("updatedAt", text(task.getUpdatedAt()));
        return view;
    }

    /** {@code provided} 非空则用它，否则回落到 {@code current}（「未提供即不改」的统一表达）。 */
    private static String pick(String provided, String current) {
        return provided == null ? current : provided;
    }

    private static String truncate(String log) {
        if (log == null) return null;
        return log.length() <= LOG_LIMIT ? log
                : log.substring(0, LOG_LIMIT) + "…（日志已截断，原始长度 " + log.length() + " 字符）";
    }

    /**
     * 时间序列化成 ISO 字符串。
     *
     * <p>为什么不直接交给共享 MAPPER：本文件用裸 {@code new ObjectMapper()}（与 {@code ArticleMediaTools}
     * 同款），没注册 JSR-310 模块，序列化 {@code LocalDateTime} 会抛 {@code InvalidDefinitionException}
     * ——一个只读工具会因此变成工具失败，模型会以为读取出了问题。
     */
    private static String text(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String json(Object value) {
        try {
            // 统一兜底：即便某个工具忘了用 AssistantToolBudget.page，输出也不会撑爆上下文
            return AssistantToolBudget.cap(MAPPER.writeValueAsString(value));
        } catch (Exception error) {
            throw new IllegalStateException("助手任务工具结果序列化失败", error);
        }
    }
}