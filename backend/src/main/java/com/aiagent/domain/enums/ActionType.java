package com.aiagent.domain.enums;

/**
 * 动作类型枚举
 */
public enum ActionType {
    /**
     * 调用工具
     */
    TOOL_CALL,

    /**
     * RAG检索
     */
    RAG_RETRIEVE,

    /**
     * LLM生成
     */
    LLM_GENERATE,

//        /**
//         * 请求用户输入
//         */
//        REQUEST_USER_INPUT,

    /**
     * 直接返回响应
     * 用于简单场景，直接返回预设的回复内容，无需调用LLM
     */
    DIRECT_RESPONSE
}