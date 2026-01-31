package com.aiagent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 思考引擎配置参数
 * 前端可通过配置页传入，用于控制提示词构建的行为
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThinkingConfig {
    
    /**
     * 对话历史轮数（默认3轮，最近的N轮对话）
     * 控制在提示词中包含多少轮历史对话
     */
    @Builder.Default
    private Integer conversationHistoryRounds = 3;
    
    /**
     * 单条消息最大长度（默认200字符，超过截断）
     * 用于截断过长的历史消息，避免提示词过长
     */
    @Builder.Default
    private Integer maxMessageLength = 200;
    
    /**
     * 动作执行历史轮数（默认2轮，显示最近N轮迭代的执行历史）
     * 控制在提示词中包含多少轮 ReACT 迭代的动作执行记录
     * 每轮迭代可能包含多个动作（TOOL_CALL、RAG_RETRIEVE、LLM_GENERATE等）
     */
    private Integer actionExecutionHistoryCount = null;
    
    /**
     * 获取有效的对话历史轮数（处理null情况）
     */
    public int getConversationHistoryRoundsOrDefault() {
        return conversationHistoryRounds != null ? conversationHistoryRounds : 3;
    }
    
    /**
     * 获取有效的单条消息最大长度（处理null情况）
     */
    public int getMaxMessageLengthOrDefault() {
        return maxMessageLength != null ? maxMessageLength : 200;
    }

}

