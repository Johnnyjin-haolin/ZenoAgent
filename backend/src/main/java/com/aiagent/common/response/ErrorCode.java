package com.aiagent.common.response;

import lombok.Getter;

/**
 * 错误码定义
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, "成功"),
    GENERAL_ERROR(1000, "通用错误"),
    VALIDATION_ERROR(1001, "参数校验失败"),
    NOT_FOUND(1004, "资源不存在"),
    INTERNAL_ERROR(1500, "内部错误");

    private final Integer code;
    private final String description;

    ErrorCode(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
}

