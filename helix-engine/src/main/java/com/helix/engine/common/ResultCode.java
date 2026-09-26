package com.helix.engine.common;

/**
 * Business response codes. Segment scheme consistent with helix-console:
 *
 * <ul>
 *   <li>0           Success</li>
 *   <li>1xxxx       General/parameter errors</li>
 *   <li>2xxxx       Authentication and authorization</li>
 *   <li>3xxxx       Engine execution</li>
 * </ul>
 */
public enum ResultCode {

    SUCCESS(0, "Success"),
    FAIL(10000, "Operation failed"),
    PARAM_INVALID(10001, "Parameter validation failed"),
    NOT_FOUND(10002, "Data not found"),
    SYSTEM_ERROR(10500, "System error"),

    UNAUTHORIZED(20001, "Unauthorized"),
    FORBIDDEN(20003, "No permission"),

    ENGINE_NOT_FOUND(30001, "Engine not found or not deployed"),
    NODE_NOT_FOUND(30002, "Node not found"),
    NODE_TYPE_UNSUPPORTED(30003, "Unsupported node type"),
    EXECUTE_FAILED(30004, "Engine execution failed"),
    DATA_CENTER_ERROR(30005, "Data Center fetch failed"),
    FLOW_INVALID(30006, "Invalid decision flow structure");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
