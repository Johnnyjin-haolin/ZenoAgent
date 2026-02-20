package com.aiagent.application;

import com.aiagent.api.dto.AgentRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent服务接口
 * 定义Agent的核心服务方法
 * 
 * @author aiagent
 */
public interface IAgentService {
    
    /**
     * 执行Agent任务
     */
    SseEmitter execute(AgentRequest request);
    
    /**
     * 停止Agent执行
     */
    boolean stop(String requestId);
    
    /**
     * 清除会话记忆
     */
    boolean clearMemory(String conversationId);
}

