package com.aiagent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 健康检查响应
 * 
 * @author aiagent
 */
@Data
@AllArgsConstructor
public class HealthResponse {
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 消息
     */
    private String message;
}
