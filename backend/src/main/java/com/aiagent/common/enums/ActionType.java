package com.aiagent.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 动作类型枚举
 */
@Getter
public enum ActionType {

    /**
     * 调用工具
     */
    TOOL_CALL("TOOL_CALL"),

    /**
     * RAG检索
     */
    RAG_RETRIEVE("RAG_RETRIEVE"),

    /**
     * LLM生成
     */
    LLM_GENERATE("LLM_GENERATE"),

//        /**
//         * 请求用户输入
//         */
//        REQUEST_USER_INPUT,

    /**
     * 直接返回响应
     * 用于简单场景，直接返回预设的回复内容，无需调用LLM
     */
    DIRECT_RESPONSE("DIRECT_RESPONSE"),;

    private final String code;

    ActionType(String code) {
        this.code = code;
    }

    public static ActionType getByCode(String code){
        for(ActionType actionType : ActionType.values()){
            if (actionType.code.equals(code)){
                return actionType;
            }
        }
        return null;
    }
    public static List<String> getActionTypeEnums(){
        return Arrays.stream(ActionType.values()).map(ActionType::getCode).collect(Collectors.toList());
    }
}