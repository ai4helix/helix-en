package com.helix.console.common;

import lombok.Getter;

/**
 * Business response codes.
 *
 * <p>Segment conventions:
 * <ul>
 *   <li>0           success</li>
 *   <li>1xxxx       common/parameter errors</li>
 *   <li>2xxxx       authentication & authorization</li>
 *   <li>3xxxx       engine related</li>
 *   <li>4xxxx       knowledge base related</li>
 *   <li>5xxxx       field/list DB related</li>
 * </ul>
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "Success"),
    FAIL(10000, "Operation failed"),
    PARAM_INVALID(10001, "Parameter validation failed"),
    NOT_FOUND(10002, "Data not found"),
    DATA_DUPLICATE(10003, "Data already exists"),
    DATA_IN_USE(10004, "Data is referenced and cannot be deleted"),
    SYSTEM_ERROR(10500, "System error"),

    UNAUTHORIZED(20001, "Not logged in or session expired"),
    FORBIDDEN(20003, "No permission to access"),

    ENGINE_NOT_FOUND(30001, "Engine not found"),
    VERSION_NOT_FOUND(30002, "Engine version not found"),
    VERSION_PUBLISHED(30003, "Published version cannot be edited, please create a draft first"),
    NODE_NOT_FOUND(30004, "Node not found"),
    NODE_TYPE_UNSUPPORTED(30005, "Unsupported node type"),
    FLOW_INVALID(30006, "Invalid decision flow structure"),
    EXECUTE_FAILED(30007, "Engine execution failed"),

    RULE_NOT_FOUND(40001, "Rule not found"),
    SCORECARD_NOT_FOUND(40002, "Scorecard not found"),

    FIELD_NOT_FOUND(50001, "Field not found"),
    LIST_DB_NOT_FOUND(50002, "List DB not found");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
