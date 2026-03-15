package com.aiagent.domain.model.bo;

import com.aiagent.common.enums.AgentState;
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
 * Agent 推理引擎统一执行结果
 * 供所有 AgentEngine 实现返回，替代原来各引擎各自的结果类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionResult {

    /** 执行是否成功 */
    private boolean success;

    /** 错误信息（失败时） */
    private String error;

    /** 错误类型（失败时） */
    private String errorType;

    /** 本次执行产生的完整对话消息列表 */
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    /** 推理迭代/轮次数 */
    private int iterations;

    /** 总耗时（毫秒） */
    private long totalDurationMs;

    /** 最终状态 */
    private AgentState finalState;

    /** 扩展元数据（工具调用记录、RAG检索记录等） */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public static AgentExecutionResult success(List<ChatMessage> messages, int iterations,
                                               long totalDurationMs, AgentState finalState) {
        return AgentExecutionResult.builder()
                .success(true)
                .messages(messages != null ? new ArrayList<>(messages) : new ArrayList<>())
                .iterations(iterations)
                .totalDurationMs(totalDurationMs)
                .finalState(finalState)
                .metadata(new HashMap<>())
                .build();
    }

    public static AgentExecutionResult failure(String error, String errorType,
                                               int iterations, long totalDurationMs,
                                               AgentState finalState) {
        return failure(error, errorType, new ArrayList<>(), iterations, totalDurationMs, finalState);
    }

    public static AgentExecutionResult failure(String error, String errorType,
                                               List<ChatMessage> messages,
                                               int iterations, long totalDurationMs,
                                               AgentState finalState) {
        return AgentExecutionResult.builder()
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
