package com.aiagent.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent动作定义
 * 表示Agent要执行的一个动作
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAction {
    
    /**
     * 动作类型
     */
    private ActionType type;
    
    /**
     * 动作名称
     */
    private String name;
    
    /**
     * 动作描述
     */
    private String description;
    
    /**
     * 动作参数
     */
    private Map<String, Object> params;
    
    /**
     * 推理过程（为什么选择这个动作）
     */
    private String reasoning;
    
    /**
     * 预期结果
     */
    private String expectedResult;
    
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
        
        /**
         * 完成任务
         */
        COMPLETE,
        
        /**
         * 请求用户输入
         */
        REQUEST_USER_INPUT,
        
        /**
         * 等待
         */
        WAIT
    }
    
    /**
     * 创建工具调用动作
     */
    public static AgentAction toolCall(String toolName, Map<String, Object> params, String reasoning) {
        return AgentAction.builder()
            .type(ActionType.TOOL_CALL)
            .name(toolName)
            .params(params)
            .reasoning(reasoning)
            .description("调用工具: " + toolName)
            .build();
    }
    
    /**
     * 创建RAG检索动作
     */
    public static AgentAction ragRetrieve(String query, Map<String, Object> params, String reasoning) {
        return AgentAction.builder()
            .type(ActionType.RAG_RETRIEVE)
            .name("rag_retrieve")
            .params(params)
            .reasoning(reasoning)
            .description("检索知识库: " + query)
            .build();
    }
    
    /**
     * 创建LLM生成动作
     */
    public static AgentAction llmGenerate(String prompt, String reasoning) {
        return AgentAction.builder()
            .type(ActionType.LLM_GENERATE)
            .name("llm_generate")
            .params(Map.of("prompt", prompt))
            .reasoning(reasoning)
            .description("生成回复")
            .build();
    }
    
    /**
     * 创建完成动作
     */
    public static AgentAction complete(String reasoning) {
        return AgentAction.builder()
            .type(ActionType.COMPLETE)
            .name("complete")
            .reasoning(reasoning)
            .description("任务完成")
            .build();
    }
}

