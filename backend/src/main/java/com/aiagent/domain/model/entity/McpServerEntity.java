package com.aiagent.domain.model.entity;

import lombok.Data;

import java.util.Date;

/**
 * MCP 服务器配置实体 —— 对应 mcp_server 表
 * <p>
 * scope: 0=GLOBAL(服务端执行)  1=PERSONAL(客户端执行)
 * PERSONAL 类型的 auth_value 为 NULL，密钥仅存浏览器 localStorage
 */
@Data
public class McpServerEntity {

    private String id;

    /** 显示名称 */
    private String name;

    /** 描述 */
    private String description;

    /**
     * 作用域：0=GLOBAL, 1=PERSONAL
     */
    private Integer scope;

    /**
     * PERSONAL 类型归属用户，暂不做权限校验（全部用户可见）
     */
    private String ownerUserId;

    /** 连接类型：streamable-http / sse / stdio / websocket */
    private String connectionType;

    /** MCP 服务端点 URL */
    private String endpointUrl;

    /**
     * 认证请求头（JSON 格式，键值对）
     * <p>
     * GLOBAL 类型示例：{"Authorization":"Bearer sk-xxx","X-Tenant-Id":"t001"}
     * PERSONAL 类型示例：{"Authorization":"","X-Api-Key":""}（仅存 Header 名，值为空；运行时由浏览器 localStorage 补充）
     * NULL 或空 JSON 表示无需认证，直接允许执行
     */
    private String authHeader;

    /** 额外请求头（JSON 格式，GLOBAL 类型加密存储） */
    private String extraHeaders;

    private Integer timeoutMs;

    private Integer readTimeoutMs;

    private Integer retryCount;

    /** 是否启用 */
    private Integer enabled;

    private String createdBy;

    private Date createdAt;

    private Date updatedAt;
}
