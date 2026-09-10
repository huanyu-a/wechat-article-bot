package ink.icoding.wechat.article.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception exception) {
        String message = exception instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().findFirst()
                    .map(error -> error.getField() + " " + error.getDefaultMessage()).orElse("请求参数不正确")
                : exception.getMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    /** 方法级 @PreAuthorize 拒绝时返回 403，而不是被下面的兜底处理器吞成 500。 */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException exception) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("没有权限执行该操作"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request error", exception);
        return ResponseEntity.internalServerError().body(ApiResponse.error("服务暂时不可用，请稍后重试"));
    }
}
