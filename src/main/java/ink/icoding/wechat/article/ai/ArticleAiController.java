package ink.icoding.wechat.article.ai;

import ink.icoding.wechat.article.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

@RestController
@RequestMapping("/api/articles/{articleId}/ai")
public class ArticleAiController {
    /** 对话限流：每用户每分钟最多 20 次（每次对话都是一次高成本 LLM 会话）。 */
    private static final int CHAT_MAX_PER_MINUTE = 20;

    private final ArticleAiService service;
    private final ink.icoding.wechat.article.common.RateLimiter rateLimiter;
    private final ink.icoding.wechat.article.auth.CurrentUserService currentUserService;

    public ArticleAiController(ArticleAiService service,
                               ink.icoding.wechat.article.common.RateLimiter rateLimiter,
                               ink.icoding.wechat.article.auth.CurrentUserService currentUserService) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/messages")
    public ApiResponse<List<AiMessage>> messages(@PathVariable Long articleId) {
        return ApiResponse.ok(service.messages(articleId));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public SseEmitter chat(@PathVariable Long articleId, @Valid @RequestBody ChatRequest request,
                           HttpServletResponse response) {
        rateLimiter.check("ai-chat:" + currentUserService.required().id(),
                CHAT_MAX_PER_MINUTE, java.time.Duration.ofMinutes(1));
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return service.chat(articleId, request.instruction(), request.assetIds());
    }

    @PostMapping("/sessions/{sessionId}/tools/{callId}/result")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<Void> toolResult(@PathVariable Long articleId, @PathVariable String sessionId,
                                        @PathVariable String callId,
                                        @RequestBody ArticleAiService.ToolResultRequest request) {
        service.completeTool(articleId, sessionId, callId, request);
        return ApiResponse.ok();
    }

    public record ChatRequest(@NotBlank String instruction, List<Long> assetIds) {}
}
