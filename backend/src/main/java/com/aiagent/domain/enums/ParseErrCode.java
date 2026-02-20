package com.aiagent.domain.enums;


/**
 * Agent执行模式
 * 
 * @author aiagent
 */
public enum ParseErrCode {

    /**
     * action Type不合法
     */
    ACTION_TYPE_INVALID(1,"action Type不合法，请使用严格按照允许的ActionType,ActionType仅允许RAG_RETRIEVE、TOOL_CALL、DIRECT_RESPONSE、LLM_GENERATE，"),
    /**
     * JSON解析格式错误
     */
    JSON_PARSE(2,"输出不符合格式要求"),
    ACTIONS_MISSING(3,"缺少actions字段"),
    ACTIONS_EMPTY(4,"actions为空"),
    ACTION_ITEM_INVALID(5,"action项格式不合法"),
    ACTION_TYPE_MISSING(6,"actionType为空"),
    TOOL_CALL_PARAMS_MISSING(7,"toolCallParams缺失"),
    TOOL_NAME_MISSING(8,"toolName缺失"),
    RAG_PARAMS_MISSING(9,"ragRetrieveParams缺失"),
    RAG_QUERY_MISSING(10,"ragRetrieveParams.query缺失"),
    DIRECT_PARAMS_MISSING(11,"directResponseParams缺失"),
    DIRECT_CONTENT_MISSING(12,"directResponseParams.content缺失"),
    DIRECT_IS_COMPLETE_MISSING(12,"directResponseParams.isComplete缺失"),
    LLM_PARAMS_MISSING(13,"llmGenerateParams缺失"),
    LLM_PROMPT_MISSING(14,"llmGenerateParams.prompt缺失"),
    TOOL_NOT_FOUND(15, "Tool not found");

    private final Integer code;
    private final String description;

    ParseErrCode(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
