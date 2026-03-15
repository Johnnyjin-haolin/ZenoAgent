package com.aiagent.infrastructure.external.mcp;

import com.aiagent.infrastructure.config.McpServerConfig;
import com.aiagent.common.enums.ConnectionTypeEnums;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 工具提供者工厂
 * <p>
 * 根据 serverMcpIds 动态创建过滤后的工具提供者（GLOBAL 类型）
 */
@Slf4j
@Component
public class McpToolProviderFactory {

    @Autowired
    private McpServerConfig mcpConfig;

    @Autowired
    private McpClientFactory mcpClientFactory;

    @Autowired
    private McpManager mcpManager;

    /**
     * 创建包含所有启用 GLOBAL MCP 客户端的工具提供者
     */
    public ToolProvider createBaseToolProvider() {
        if (!mcpConfig.isEnabled()) {
            return null;
        }
        List<McpClient> clients = getAllEnabledClients();
        if (clients.isEmpty()) {
            return null;
        }
        return McpToolProvider.builder()
                .mcpClients(clients)
                .failIfOneServerFails(false)
                .build();
    }

    /**
     * 按指定服务器 ID 列表创建过滤后的工具提供者
     *
     * @param serverIds 服务器 ID 列表（为空时返回所有工具）
     */
    public ToolProvider createFilteredToolProvider(List<String> serverIds) {
        if (!mcpConfig.isEnabled()) {
            return null;
        }
        if (serverIds == null || serverIds.isEmpty()) {
            return createBaseToolProvider();
        }

        // 仅创建指定服务器的客户端
        List<McpClient> clients = serverIds.stream()
                .map(id -> {
                    McpServerConfig.McpServerDefinition def = mcpConfig.findById(id);
                    if (def == null || !def.isEnabled()) return null;
                    ConnectionTypeEnums connType = def.getConnection().getType();
                    if (!isSupportedTransport(connType)) return null;
                    try {
                        return mcpClientFactory.getOrCreateClient(def);
                    } catch (Exception e) {
                        log.error("创建 MCP 客户端失败: serverId={}", id, e);
                        return null;
                    }
                })
                .filter(c -> c != null)
                .collect(Collectors.toList());

        if (clients.isEmpty()) {
            return null;
        }

        return McpToolProvider.builder()
                .mcpClients(clients)
                .failIfOneServerFails(false)
                .build();
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────────

    private List<McpClient> getAllEnabledClients() {
        List<McpClient> clients = new ArrayList<>();
        for (McpServerConfig.McpServerDefinition server : mcpConfig.getServers()) {
            if (!server.isEnabled()) continue;
            ConnectionTypeEnums connType = server.getConnection().getType();
            if (!isSupportedTransport(connType)) continue;
            try {
                McpClient client = mcpClientFactory.getOrCreateClient(server);
                if (client != null) {
                    clients.add(client);
                }
            } catch (Exception e) {
                log.error("获取 MCP 客户端失败: serverId={}", server.getId(), e);
            }
        }
        return clients;
    }

    private boolean isSupportedTransport(ConnectionTypeEnums connType) {
        if (connType == null) return false;
        return connType == ConnectionTypeEnums.STREAMABLE_HTTP
                || connType == ConnectionTypeEnums.STDIO
                || connType == ConnectionTypeEnums.WEBSOCKET
                || connType == ConnectionTypeEnums.SSE;
    }
}
