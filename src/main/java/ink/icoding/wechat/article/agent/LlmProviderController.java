package ink.icoding.wechat.article.agent;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.ApiResponse;
import ink.icoding.wechat.article.common.RateLimiter;
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
 * 模型供应商 API（{@code /api/llm-providers}，契约 §5.1）：权限全 ADMIN，与
 * {@link LlmProfileController} 同款。
 *
 * <p>为什么不做成「档案接口的一个子资源」（例如 {@code /api/llm-profiles/providers}）：
 * 供应商是**独立的一等实体**——它有自己的名字、启用位、以及「下面还挂着几个模型」这个删除护栏，
 * 挂到档案下面会让「档案不存在也要能建供应商」这条路径变得别扭，而前端本来就是两个区块。
 *
 * <p>此处不出现 {@code set-default} 之类的动作端点（对比档案的 {@code /set-default}）：
 * 「默认供应商」是设置页写透选出来的（{@link LlmProviderService#resolveDefaultProviderId}），
 * 不是管理员手工指定的标记，多一个标记位只会多一处需要保持同步的状态。
 */
@RestController
@RequestMapping("/api/llm-providers")
@PreAuthorize("hasRole('ADMIN')")
public class LlmProviderController {
    /** 模型发现限流：每用户每分钟 10 次（契约 §2.2，与 {@code /api/skills/preview} 同款用法）。 */
    private static final int DISCOVERY_MAX_PER_MINUTE = 10;

    private final LlmProviderService service;
    private final CurrentUserService currentUserService;
    private final RateLimiter rateLimiter;

    public LlmProviderController(LlmProviderService service, CurrentUserService currentUserService,
                                 RateLimiter rateLimiter) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public ApiResponse<List<LlmProviderService.ProviderView>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<LlmProviderService.ProviderView> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /**
     * 模型发现（契约 §2.2）：拉取该供应商当前能用的模型名列表。
     *
     * <p>为什么限流键按**用户**而不是按供应商：同名探测连点十次的多半是「按钮没反应又点了一下」，
     * 按供应商限流会让两个人同时点同一个供应商时互相干扰，而按用户限流才对应「一个人在刷」。
     *
     * <p>为什么 {@code enabled=false} 也允许探测：用户常先填端点与密钥、确认能连上，再启用；
     * 反过来会逼他先造出一个可能指向错误网关的「启用中」供应商。只读探测不改变任何状态。
     */
    @GetMapping("/{id}/models")
    public ApiResponse<List<String>> models(@PathVariable Long id) {
        rateLimiter.check("llm-model-discovery:" + currentUserService.required().id(),
                DISCOVERY_MAX_PER_MINUTE, java.time.Duration.ofMinutes(1));
        return ApiResponse.ok(service.discoverModels(id));
    }

    @PostMapping
    public ApiResponse<LlmProviderService.ProviderView> create(
            @Valid @RequestBody LlmProviderService.ProviderRequest request) {
        return ApiResponse.ok(service.create(request, currentUserService.required().id()));
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmProviderService.ProviderView> update(
            @PathVariable Long id, @Valid @RequestBody LlmProviderService.ProviderRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /** 删除：该供应商下仍有档案时返回 400（消息含剩余模型数），不做级联删除。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
