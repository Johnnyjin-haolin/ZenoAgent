package com.aiagent.api.dto;

import com.aiagent.common.enums.ConnectionTypeEnums;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.Data;

import java.util.Map;

/**
 * MCP工具信息VO
 * 用于工具列表展示和前端展示
 * 
 * 注意：LangChain4j的McpToolProvider会自动处理工具注册和执行
 * 这里保留基本信息用于展示和分组管理，包括参数定义供大模型使用
 * 
 * @author aiagent
 */
@Data
public class McpToolInfo {
    
    /**
     * 工具ID（唯一标识，格式：serverId:toolName）
     */
    private String id;
    
    /**
     * 工具名称（唯一标识）
     */
    private String name;
    
    /**
     * 工具描述
     */
    private String description;

    @JsonIgnore
    private JsonObjectSchema parameters;

    /**
     * 工具参数的 JSON Schema（标准格式，供前端展示参数信息）
     * 格式：{"type":"object","properties":{"param1":{"type":"string","description":"..."},...},"required":["param1"]}
     */
    private Map<String, Object> inputSchema;

    private Map<String, Object> metadata;
    
    /**
     * 是否是 PERSONAL MCP 工具（客户端执行）
     * true = 由浏览器在本地执行，服务端通过 SSE 下发调用请求
     */
    private boolean personal;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 所属服务器ID
     */
    private String serverId;
    
    /**
     * 连接类型（使用ConnectionType枚举）
     */
    private ConnectionTypeEnums connectionType;
}
