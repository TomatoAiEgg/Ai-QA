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
                .body(ResponseUtils.fail(ApiCode.UNAUTHORIZED, "Please login first"));
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
                .body(ResponseUtils.fail(ApiCode.BAD_REQUEST, "Invalid request parameter format"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(ApiCode.BAD_REQUEST.getHttpStatus())
                .body(ResponseUtils.fail(ApiCode.BAD_REQUEST, "Invalid request body format"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        int statusCode = ex.getStatusCode().value();
        ApiCode apiCode;
        if (statusCode == 400) {
            apiCode = ApiCode.BAD_REQUEST;
        } else if (statusCode == 401) {
            apiCode = ApiCode.UNAUTHORIZED;
        } else if (statusCode == 403) {
            apiCode = ApiCode.FORBIDDEN;
        } else if (statusCode == 404) {
            apiCode = ApiCode.NOT_FOUND;
        } else {
            apiCode = ApiCode.SERVER_ERROR;
        }
        String message = ex.getReason() == null ? apiCode.getMessage() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(ResponseUtils.fail(apiCode, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        String message = ex.getMessage() == null ? "Internal server error" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseUtils.fail(ApiCode.SERVER_ERROR, message));
    }
}
