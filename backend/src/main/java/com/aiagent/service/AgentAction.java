package com.aiagent.service;

import com.aiagent.service.action.DirectResponseParams;
import com.aiagent.service.action.LLMGenerateParams;
import com.aiagent.service.action.RAGRetrieveParams;
import com.aiagent.service.action.ToolCallParams;
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
     * 工具调用参数（当type为TOOL_CALL时使用）
     */
    private ToolCallParams toolCallParams;
    
    /**
     * RAG检索参数（当type为RAG_RETRIEVE时使用）
     */
    private RAGRetrieveParams ragRetrieveParams;
    
    /**
     * LLM生成参数（当type为LLM_GENERATE时使用）
     */
    private LLMGenerateParams llmGenerateParams;
    
    /**
     * 直接返回响应参数（当type为DIRECT_RESPONSE时使用）
     */
    private DirectResponseParams directResponseParams;
    
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
        WAIT,
        
        /**
         * 直接返回响应
         * 用于简单场景，直接返回预设的回复内容，无需调用LLM
         */
        DIRECT_RESPONSE
    }
    
    /**
     * 创建工具调用动作（使用特定参数类型）
     */
    public static AgentAction toolCall(String toolName, ToolCallParams params, String reasoning) {
        return AgentAction.builder()
            .type(ActionType.TOOL_CALL)
            .name(toolName)
            .toolCallParams(params)
            .reasoning(reasoning)
            .description("调用工具: " + toolName)
            .build();
    }
    
    /**
     * 创建工具调用动作（兼容旧版本，使用Map）
     * @deprecated 建议使用 toolCall(String, ToolCallParams, String)
     */
    @Deprecated
    public static AgentAction toolCall(String toolName, Map<String, Object> params, String reasoning) {
        ToolCallParams toolCallParams = ToolCallParams.builder()
            .toolName(toolName)
            .toolParams(params)
            .build();
        return toolCall(toolName, toolCallParams, reasoning);
    }
    
    /**
     * 创建RAG检索动作（使用特定参数类型）
     */
    public static AgentAction ragRetrieve(RAGRetrieveParams params, String reasoning) {
        return AgentAction.builder()
            .type(ActionType.RAG_RETRIEVE)
            .name("rag_retrieve")
            .ragRetrieveParams(params)
            .reasoning(reasoning)
            .description("检索知识库: " + (params != null ? params.getQuery() : ""))
            .build();
    }
    
    /**
     * 创建RAG检索动作（兼容旧版本，使用Map）
     * @deprecated 建议使用 ragRetrieve(RAGRetrieveParams, String)
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static AgentAction ragRetrieve(String query, Map<String, Object> params, String reasoning) {
        RAGRetrieveParams ragParams = RAGRetrieveParams.builder()
            .query(query)
            .knowledgeIds((java.util.List<String>) params.getOrDefault("knowledgeIds", new java.util.ArrayList<>()))
            .build();
        return ragRetrieve(ragParams, reasoning);
    }
    
    /**
     * 创建LLM生成动作（使用特定参数类型）
     */
    public static AgentAction llmGenerate(LLMGenerateParams params, String reasoning) {
        return AgentAction.builder()
            .type(ActionType.LLM_GENERATE)
            .name("llm_generate")
            .llmGenerateParams(params)
            .reasoning(reasoning)
            .description("生成回复")
            .build();
    }
    
    /**
     * 创建LLM生成动作（兼容旧版本，使用String prompt）
     * @deprecated 建议使用 llmGenerate(LLMGenerateParams, String)
     */
    @Deprecated
    public static AgentAction llmGenerate(String prompt, String reasoning) {
        LLMGenerateParams params = LLMGenerateParams.builder()
            .prompt(prompt)
            .build();
        return llmGenerate(params, reasoning);
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
    
    /**
     * 创建直接返回响应动作
     */
    public static AgentAction directResponse(DirectResponseParams params, String reasoning) {
        String contentPreview = params != null && params.getContent() != null
            ? (params.getContent().length() > 50 
                ? params.getContent().substring(0, 50) + "..." 
                : params.getContent())
            : "";
        return AgentAction.builder()
            .type(ActionType.DIRECT_RESPONSE)
            .name("direct_response")
            .directResponseParams(params)
            .reasoning(reasoning)
            .description("直接返回回复: " + contentPreview)
            .build();
    }
}


