package cn.suhoan.starlight.controller;

import cn.suhoan.starlight.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理
 *
 * @author suhoan
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleStatus(ResponseStatusException exception) {
        log.error("请求处理失败: status={}, reason={}", exception.getStatusCode(), exception.getReason());
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(exception.getReason() == null ? "请求失败" : exception.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception exception) {
        log.error("请求处理失败", exception);
        String simpleName = exception.getClass().getSimpleName();
        if (simpleName.contains("NotLogin")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("请先登录"));
        }
        if (simpleName.contains("NotPermission")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("没有操作权限"));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(exception.getMessage() == null ? "服务器开小差了" : exception.getMessage()));
    }
}

