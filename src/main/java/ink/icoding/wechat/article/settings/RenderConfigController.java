package ink.icoding.wechat.article.settings;

import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 排版渲染服务设置（render_config 单行表，ADMIN 维护）。
 * GET 返回令牌掩码；POST /test 真实 GET 一次语法指令验证连通性并顺带刷新缓存。
 */
@RestController
@RequestMapping("/api/settings/render")
@PreAuthorize("hasRole('ADMIN')")
public class RenderConfigController {
    private final RenderConfigService service;
    private final ink.icoding.wechat.article.skill.MarkFlowRenderService renderService;

    public RenderConfigController(RenderConfigService service,
                                  ink.icoding.wechat.article.skill.MarkFlowRenderService renderService) {
        this.service = service;
        this.renderService = renderService;
    }

    @GetMapping
    public ApiResponse<RenderConfigService.ConfigView> get() {
        return ApiResponse.ok(service.get());
    }

    @PutMapping
    public ApiResponse<RenderConfigService.ConfigView> update(
            @Valid @RequestBody RenderConfigService.UpdateRequest request) {
        return ApiResponse.ok(service.update(request));
    }

    @PostMapping("/test")
    public ApiResponse<ink.icoding.wechat.article.skill.MarkFlowRenderService.TestResult> test() {
        return ApiResponse.ok(renderService.testConnection());
    }
}
