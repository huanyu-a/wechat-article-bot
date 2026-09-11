package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.common.ApiResponse;
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

    @PostMapping("/{id}/run")
    public ApiResponse<TaskRun> run(@PathVariable Long id) { return ApiResponse.ok(service.run(id)); }

    @GetMapping("/{id}/runs")
    public ApiResponse<List<TaskRun>> runs(@PathVariable Long id) { return ApiResponse.ok(service.runs(id)); }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
