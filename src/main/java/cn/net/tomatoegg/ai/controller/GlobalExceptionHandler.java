package cn.net.tomatoegg.ai.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.common.ResponseUtils;
import cn.net.tomatoegg.ai.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Map<String, Object>> handleNotLogin(NotLoginException ex) {
        return ResponseEntity.status(ApiCode.UNAUTHORIZED.getHttpStatus())
                .body(ResponseUtils.fail(ApiCode.UNAUTHORIZED, "请先登录"));
    }

    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<Map<String, Object>> handleSaToken(SaTokenException ex) {
        return ResponseEntity.status(ApiCode.UNAUTHORIZED.getHttpStatus())
                .body(ResponseUtils.fail(ApiCode.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getApiCode().getHttpStatus())
                .body(ResponseUtils.fail(ex.getApiCode(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(ApiCode.BAD_REQUEST.getHttpStatus())
                .body(ResponseUtils.fail(ApiCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(ApiCode.BAD_REQUEST.getHttpStatus())
                .body(ResponseUtils.fail(ApiCode.BAD_REQUEST, "请求参数格式不正确"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ApiCode.BAD_REQUEST.getHttpStatus())
                .body(ResponseUtils.fail(ApiCode.BAD_REQUEST, "请求体格式不正确"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        ApiCode apiCode = switch (ex.getStatusCode().value()) {
            case 400 -> ApiCode.BAD_REQUEST;
            case 401 -> ApiCode.UNAUTHORIZED;
            case 403 -> ApiCode.FORBIDDEN;
            case 404 -> ApiCode.NOT_FOUND;
            default -> ApiCode.SERVER_ERROR;
        };
        String message = ex.getReason() == null ? apiCode.getMessage() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(ResponseUtils.fail(apiCode, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseUtils.fail(ApiCode.SERVER_ERROR, ex.getMessage() == null ? "服务器异常" : ex.getMessage()));
    }
}
