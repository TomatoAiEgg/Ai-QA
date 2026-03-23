package cn.net.tomatoegg.ai.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ResponseUtils {

    private ResponseUtils() {
    }

    public static <T> Map<String, Object> success(T data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", ApiCode.SUCCESS.getCode());
        body.put("message", ApiCode.SUCCESS.getMessage());
        body.put("data", data);
        return body;
    }

    public static Map<String, Object> success() {
        return success(null);
    }

    public static Map<String, Object> fail(ApiCode apiCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", apiCode.getCode());
        body.put("message", message);
        body.put("data", null);
        return body;
    }
}
