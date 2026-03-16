package com.aiagent.api.dto;

import lombok.Data;

/**
 * PERSONAL MCP 工具 Schema
 * <p>
 * 由前端在发送消息前 prefetch 各 PERSONAL MCP 服务器的工具列表，
 * 将真实工具 schema 随 {@link AgentRequest} 上传到后端。
 * 后端直接用这些 schema 构造 ToolSpecification 交给 LLM，
 * 不再注入占位假工具。
 */
@Data
public class PersonalMcpToolSchema {

    /** 对应的 PERSONAL MCP 服务器 ID（SSE 下发时用于前端路由） */
    private String serverId;

    /** 真实工具名（如 search_bailian、list_files） */
    private String toolName;

    /** 工具描述（直接来自 MCP tools/list 响应） */
    private String description;

    /**
     * JSON Schema 参数定义（兼容 OpenAI function calling 格式）
     * 来自 MCP tools/list 响应中的 inputSchema 字段
     */
    private Object inputSchema;
}
