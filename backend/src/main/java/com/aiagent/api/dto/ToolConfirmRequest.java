package com.aiagent.api.dto;

import lombok.Data;

/**
 * 工具执行确认请求
 *
 * @author aiagent
 */
@Data
public class ToolConfirmRequest {

    /**
     * 请求ID（用于日志追踪，可选）
     */
    private String requestId;

    /**
     * 工具执行ID
     */
    private String toolExecutionId;

    /**
     * 是否批准执行
     */
    private Boolean approve;
}

