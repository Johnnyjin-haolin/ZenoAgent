package com.aiagent.application.service.engine;

import com.aiagent.domain.enums.AgentState;
import dev.langchain4j.data.message.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct循环执行结果
 * 包含所有发给用户的对话消息，用于持久化存储
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActExecutionResult {
    
    /**
     * 执行是否成功
     */
    private boolean success;
    
    /**
     * 错误信息（如果失败）
     */
    private String error;
    
    /**
     * 错误类型（如果失败）
     */
    private String errorType;
    
    /**
     * 所有对话消息列表（按时间顺序）
     * 包含用户消息和AI回复消息
     */
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();
    
    /**
     * 执行统计信息
     */
    private int iterations;  // 迭代次数
    
    private long totalDurationMs;  // 总耗时（毫秒）
    
    private AgentState finalState;  // 最终状态
    
    /**
     * 元数据（工具调用记录、RAG检索记录等）
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * 创建成功结果
     */
    public static ReActExecutionResult success(List<ChatMessage> messages, int iterations, 
                                               long totalDurationMs, AgentState finalState) {
        return ReActExecutionResult.builder()
            .success(true)
            .messages(messages != null ? new ArrayList<>(messages) : new ArrayList<>())
            .iterations(iterations)
            .totalDurationMs(totalDurationMs)
            .finalState(finalState)
            .metadata(new HashMap<>())
            .build();
    }
    
    /**
     * 创建失败结果（不包含消息）
     */
    public static ReActExecutionResult failure(String error, String errorType, 
                                                int iterations, long totalDurationMs, 
                                                AgentState finalState) {
        return failure(error, errorType, new ArrayList<>(), iterations, totalDurationMs, finalState);
    }
    
    /**
     * 创建失败结果（包含已有消息）
     */
    public static ReActExecutionResult failure(String error, String errorType,
                                                List<ChatMessage> messages,
                                                int iterations, long totalDurationMs, 
                                                AgentState finalState) {
        return ReActExecutionResult.builder()
            .success(false)
            .error(error)
            .errorType(errorType)
            .messages(messages != null ? new ArrayList<>(messages) : new ArrayList<>())
            .iterations(iterations)
            .totalDurationMs(totalDurationMs)
            .finalState(finalState)
            .metadata(new HashMap<>())
            .build();
    }
}

