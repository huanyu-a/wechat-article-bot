package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.common.ApiResponse;
import ink.icoding.wechat.article.common.BusinessException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public class ScheduleTaskController {
    private final ScheduleTaskService service;
    private final ScheduledExecutionRouter router;

    public ScheduleTaskController(ScheduleTaskService service, ScheduledExecutionRouter router) {
        this.service = service;
        this.router = router;
    }

    @GetMapping
    public ApiResponse<List<ScheduleTaskService.TaskView>> list() {
        return ApiResponse.ok(service.list().stream().map(ScheduleTaskService::toView).toList());
    }

    /** 执行模式清单（前端单选卡片用），路径须在 /{id} 之前声明。 */
    @GetMapping("/execution-modes")
    public ApiResponse<List<java.util.Map<String, String>>> executionModes() { return ApiResponse.ok(router.modes()); }

    @GetMapping("/{id}")
    public ApiResponse<ScheduleTaskService.TaskView> get(@PathVariable Long id) {
        return ApiResponse.ok(ScheduleTaskService.toView(service.required(id)));
    }

    @PostMapping
    public ApiResponse<ScheduleTaskService.TaskView> create(@Valid @RequestBody ScheduleTaskService.TaskRequest request) {
        return ApiResponse.ok(ScheduleTaskService.toView(service.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduleTaskService.TaskView> update(@PathVariable Long id,
                                                           @Valid @RequestBody ScheduleTaskService.TaskRequest request) {
        return ApiResponse.ok(ScheduleTaskService.toView(service.update(id, request)));
    }

    /**
     * 启动 / 停止调度。
     *
     * <p>用 PUT 而不是 POST：它是幂等的状态设置（连点两次「停止」结果一样），
     * 与 {@code /run}（每次调用都产生一条新运行记录，天然非幂等）区分开。
     */
    @PutMapping("/{id}/enabled")
    public ApiResponse<ScheduleTaskService.TaskView> setEnabled(@PathVariable Long id,
                                                               @RequestBody EnabledRequest request) {
        if (request == null || request.enabled() == null) {
            throw new BusinessException("请给出 enabled：true 启用，false 停止");
        }
        return ApiResponse.ok(ScheduleTaskService.toView(
                service.setEnabled(id, request.enabled())));
    }

    /** 只带一个字段的请求体：启停不需要（也不应该需要）整份任务表单。 */
    public record EnabledRequest(Boolean enabled) {}

    @PostMapping("/{id}/run")
    public ApiResponse<TaskRun> run(@PathVariable Long id) { return ApiResponse.ok(service.run(id)); }

    @GetMapping("/{id}/runs")
    public ApiResponse<List<TaskRun>> runs(@PathVariable Long id) { return ApiResponse.ok(service.runs(id)); }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
