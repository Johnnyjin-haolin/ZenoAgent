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
     * 工具调用历史数量（默认2次，最近的N次调用）
     * 控制在提示词中包含多少次最近的工具调用记录
     */
    @Builder.Default
    private Integer toolCallHistoryCount = 2;
    
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
    
    /**
     * 获取有效的工具调用历史数量（处理null情况）
     */
    public int getToolCallHistoryCountOrDefault() {
        return toolCallHistoryCount != null ? toolCallHistoryCount : 2;
    }
}

