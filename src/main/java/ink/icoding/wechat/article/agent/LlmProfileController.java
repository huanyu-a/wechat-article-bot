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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型档案 API（/api/llm-profiles，skills-agent-plan 5.6）：权限全 ADMIN。
 */
@RestController
@RequestMapping("/api/llm-profiles")
@PreAuthorize("hasRole('ADMIN')")
public class LlmProfileController {
    private final LlmProfileService service;
    private final CurrentUserService currentUserService;

    public LlmProfileController(LlmProfileService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<List<LlmProfileService.ProfileView>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<LlmProfileService.ProfileView> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<LlmProfileService.ProfileView> create(
            @Valid @RequestBody LlmProfileService.ProfileRequest request) {
        return ApiResponse.ok(service.create(request, currentUserService.required().id()));
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmProfileService.ProfileView> update(
            @PathVariable Long id, @Valid @RequestBody LlmProfileService.ProfileRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/set-default")
    public ApiResponse<LlmProfileService.ProfileView> setDefault(@PathVariable Long id) {
        return ApiResponse.ok(service.setDefault(id));
    }

    /**
     * 设为兜底档案（故障切换链的最后一段）。
     *
     * <p>与 set-default 分开两个端点而不是合成一个「设置标记」：两者语义不同且可以同时成立——
     * 默认档案是「没绑定就用它」，兜底档案是「主力全挂才用它」，合成一个会让前端不得不用
     * 一个字段表达两种状态。
     */
    @PostMapping("/{id}/set-fallback")
    public ApiResponse<LlmProfileService.ProfileView> setFallback(@PathVariable Long id) {
        return ApiResponse.ok(service.setFallback(id));
    }
}
