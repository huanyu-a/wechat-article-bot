package ink.icoding.wechat.article.assistant;

import ink.icoding.wechat.article.common.ApiResponse;
import ink.icoding.wechat.article.common.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;

/**
 * 全局助手（docs/dev/global-assistant-plan.md §4.1）。
 *
 * <p>为什么是独立一域而不是挂到 {@code /api/articles/{id}/ai} 之下：那条链路的一切（会话序列化、
 * 工具执行上下文、历史消息）都以 articleId 为外键。全局助手要处理「还没有文章」的请求
 * （新建草稿、配模型、改定时任务），强行借用一个不存在的 articleId 只会让两条链路互相污染。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    /**
     * 对话限流：每用户每分钟 12 次。
     *
     * <p>比编辑器链路的 20 次更严：编辑器的每次对话通常只改一段文字，而全局助手的一次对话可能
     * 触及任务创建、模型档案变更这类**影响后续自动运行**的操作，值得让用户慢一点、
     * 看清楚上一轮做了什么。
     */
    private static final int CHAT_MAX_PER_MINUTE = 12;
    /** 历史消息单次最多取多少条。 */
    private static final int MESSAGES_MAX_LIMIT = 200;

    private final AssistantService service;
    private final RateLimiter rateLimiter;
    private final ink.icoding.wechat.article.auth.CurrentUserService currentUserService;

    public AssistantController(AssistantService service, RateLimiter rateLimiter,
                                ink.icoding.wechat.article.auth.CurrentUserService currentUserService) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.currentUserService = currentUserService;
    }

    /**
     * 本人的助手对话历史（新的在后）。
     *
     * <p>不加 {@code @PreAuthorize}：只读自己的历史，身份由 {@code currentUserService.required()}
     * 强制要求（未登录即 401），没有越权面。
     */
    @GetMapping("/messages")
    public ApiResponse<List<AssistantMessage>> messages(
            @RequestParam(defaultValue = "50") int limit) {
        int capped = Math.max(1, Math.min(limit, MESSAGES_MAX_LIMIT));
        return ApiResponse.ok(service.messages(capped));
    }

    /** 开新对话：清空本人的会话上下文，历史消息保留。 */
    @PostMapping("/reset")
    public ApiResponse<Void> reset() {
        service.reset();
        return ApiResponse.ok();
    }

    /**
     * 可选的模型档案（面板的下拉框）。
     *
     * <p>与 {@code /chat} 同一组角色：能发起对话的人才需要选模型。返回体里没有密钥、掩码或 baseUrl
     * （见 {@link AssistantService.ModelOption}），所以不必收窄到 ADMIN。
     */
    @GetMapping("/models")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<AssistantService.ModelChoice> models() {
        return ApiResponse.ok(service.models());
    }

    /** 保存「我的助手用哪个模型」；{@code profileId} 传 null 表示清除选择、回到自动挑链。 */
    @PutMapping("/model")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<AssistantService.ModelChoice> selectModel(@RequestBody ModelRequest request) {
        return ApiResponse.ok(service.selectModel(request == null ? null : request.profileId()));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public SseEmitter chat(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
        // 限流键含用户 id：全局助手与编辑器助手各算各的额度，互不挤占
        rateLimiter.check("assistant-chat:" + currentUserService.required().id(),
                CHAT_MAX_PER_MINUTE, Duration.ofMinutes(1));
        // 与编辑器链路同样的反缓冲头：少了它们，Nginx/浏览器会把 SSE 攒到缓冲区满才吐
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return service.chat(request.instruction(), request.context(), request.profileId());
    }

    /**
     * 请求体：指令 + 前端当前页面上下文 + 本次想用的模型档案（后两者都可省略）。
     *
     * <p>{@code profileId} 与 {@code PUT /model} 保存的偏好是两回事：这里是「这一条消息用哪个」，
     * 偏好是「默认用哪个」。两者都传时以请求为准——用户在下拉框里临时换一个试一下，
     * 不该被迫改掉默认设置。
     */
    public record ChatRequest(@NotBlank String instruction,
                              AssistantService.AssistantContext context,
                              Long profileId) {
        public ChatRequest {
            if (context == null) context = AssistantService.AssistantContext.empty();
        }
    }

    /** 模型选择请求体；{@code profileId} 为空表示清除选择。 */
    public record ModelRequest(Long profileId) {
    }
}