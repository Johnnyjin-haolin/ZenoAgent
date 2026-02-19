package com.aiagent.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent执行模式
 * 
 * @author aiagent
 */
@Getter
@AllArgsConstructor
public enum ParseErrCode {

    /**
     * action Type不合法
     */
    ACTION_TYPE_INVALID(1,"action Type不合法，请使用严格按照允许的ActionType"),

    /**
     * JSON解析格式错误
     */
    JSON_PARSE(2,"输出不符合格式要求");

    private final Integer code;
    private final String description;

}


