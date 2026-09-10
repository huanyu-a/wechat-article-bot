package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能体定义 API（/api/agents，skills-agent-plan 5.6）：读全员、写 ADMIN/OPERATOR。
 */
@RestController
@RequestMapping("/api/agents")
public class AgentDefinitionController {
    private final AgentDefinitionService service;
    private final ToolRegistry toolRegistry;
    private final CurrentUserService currentUserService;

    public AgentDefinitionController(AgentDefinitionService service, ToolRegistry toolRegistry,
                                     CurrentUserService currentUserService) {
        this.service = service;
        this.toolRegistry = toolRegistry;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<List<AgentDefinition>> list(@RequestParam(required = false) String stage,
                                                   @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(service.list(stage, enabled));
    }

    /** 工具组清单（前端勾选用），路径须在 /{id} 之前声明。 */
    @GetMapping("/tool-groups")
    public ApiResponse<List<ToolRegistry.Group>> toolGroups() {
        return ApiResponse.ok(toolRegistry.groups());
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentDefinition> get(@PathVariable Long id) {
        return ApiResponse.ok(service.required(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<AgentDefinition> create(@Valid @RequestBody AgentDefinitionService.AgentRequest request) {
        return ApiResponse.ok(service.create(request, currentUser()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<AgentDefinition> update(@PathVariable Long id,
                                               @Valid @RequestBody AgentDefinitionService.AgentRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<AgentDefinition> duplicate(@PathVariable Long id) {
        return ApiResponse.ok(service.duplicate(id, currentUser()));
    }

    private Long currentUser() {
        return currentUserService.required().id();
    }
}
