package com.helix.engine.common;

/**
 * Business exception. Uniformly converted to {@link Result} by {@link GlobalExceptionHandler}.
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public static BizException of(ResultCode code) {
        return new BizException(code);
    }

    public static BizException of(ResultCode code, String message) {
        return new BizException(code, message);
    }

    public int getCode() {
        return code;
    }
}
