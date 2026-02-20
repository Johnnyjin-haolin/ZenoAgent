package com.aiagent.domain.action;

import com.aiagent.common.enums.ActionType;
import com.aiagent.common.util.UUIDGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 伪id，生成时时间戳
     */
    private String id;

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
     * 预期结果
     */
    private String expectedResult;

    /**
     * 创建工具调用动作（使用特定参数类型）
     */
    public static AgentAction toolCall(String toolName, ToolCallParams params) {
        return AgentAction.builder().id(UUIDGenerator.generate())
            .type(ActionType.TOOL_CALL)
            .name(toolName)
            .toolCallParams(params)
            .description("调用工具: " + toolName)
            .build();
    }

    
    /**
     * 创建RAG检索动作（使用特定参数类型）
     */
    public static AgentAction ragRetrieve(RAGRetrieveParams params) {
        return AgentAction.builder()
            .id(UUIDGenerator.generate())
            .type(ActionType.RAG_RETRIEVE)
            .name("rag_retrieve")
            .ragRetrieveParams(params)
            .description("检索知识库: " + (params != null ? params.getQuery() : ""))
            .build();
    }

    /**
     * 创建LLM生成动作（使用特定参数类型）
     */
    public static AgentAction llmGenerate(LLMGenerateParams params) {
        return AgentAction.builder()
            .id(UUIDGenerator.generate())
            .type(ActionType.LLM_GENERATE)
            .name("llm_generate")
            .llmGenerateParams(params)
            .description("生成回复")
            .build();
    }

    /**
     * 创建直接返回响应动作
     */
    public static AgentAction directResponse(DirectResponseParams params) {
        String contentPreview = params != null && params.getContent() != null
            ? (params.getContent().length() > 50
                ? params.getContent().substring(0, 50) + "..."
                : params.getContent())
            : "";
        return AgentAction.builder().id(UUIDGenerator.generate())
            .type(ActionType.DIRECT_RESPONSE)
            .name("direct_response")
            .directResponseParams(params)
            .description("直接返回回复: " + contentPreview)
            .build();
    }
}


