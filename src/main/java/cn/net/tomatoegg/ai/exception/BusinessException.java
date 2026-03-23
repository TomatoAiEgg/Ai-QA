package cn.net.tomatoegg.ai.exception;

import cn.net.tomatoegg.ai.common.ApiCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ApiCode apiCode;

    public BusinessException(ApiCode apiCode) {
        super(apiCode.getMessage());
        this.apiCode = apiCode;
    }

    public BusinessException(ApiCode apiCode, String message) {
        super(message);
        this.apiCode = apiCode;
    }

    public BusinessException(ApiCode apiCode, Throwable cause) {
        super(apiCode.getMessage(), cause);
        this.apiCode = apiCode;
    }

    public BusinessException(ApiCode apiCode, String message, Throwable cause) {
        super(message, cause);
        this.apiCode = apiCode;
    }
}
