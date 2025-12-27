package com.aiagent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent SSE 事件数据
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEventData {
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 事件类型
     */
    private String event;
    
    /**
     * 消息内容（状态提示等）
     */
    private String message;
    
    /**
     * 流式内容（用于 agent:message 事件）
     */
    private String content;
    
    /**
     * 数据载荷
     */
    private Object data;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 主题ID
     */
    private String topicId;
}


