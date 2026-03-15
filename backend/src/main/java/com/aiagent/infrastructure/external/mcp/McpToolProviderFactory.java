package com.aiagent.infrastructure.external.mcp;

import com.aiagent.domain.agent.AgentDefinition;
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
 * 根据 {@link AgentDefinition.McpServerSelection} 列表动态创建工具提供者（GLOBAL 类型）。
 * 支持服务器级选择（toolNames=null 时全量）和工具名细粒度白名单过滤。
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
     * 创建包含所有启用 GLOBAL MCP 客户端的工具提供者（无过滤）
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
     * 按 {@link AgentDefinition.McpServerSelection} 列表创建工具提供者。
     * <p>
     * 规则：
     * <ul>
     *   <li>selections 为空/null → 返回所有 GLOBAL MCP 工具（等同 createBaseToolProvider）</li>
     *   <li>selections 非空 → 仅初始化列表中的 MCP 客户端</li>
     * </ul>
     * <p>
     * 注意：LangChain4j 的 {@code McpToolProvider} 不原生支持工具名白名单过滤，
     * 工具名过滤由上层 {@link com.aiagent.domain.tool.ToolRegistry} 在组装 ToolSpecification
     * 时通过 {@link McpManager#getToolsBySelections} 完成，此处只负责连接对应的 MCP 客户端。
     *
     * @param selections MCP 服务器工具选择列表
     */
    public ToolProvider createFilteredToolProvider(List<AgentDefinition.McpServerSelection> selections) {
        if (!mcpConfig.isEnabled()) {
            return null;
        }
        if (selections == null || selections.isEmpty()) {
            return createBaseToolProvider();
        }

        List<McpClient> clients = selections.stream()
                .map(sel -> {
                    McpServerConfig.McpServerDefinition def = mcpConfig.findById(sel.getServerId());
                    if (def == null || !def.isEnabled()) return null;
                    if (!isSupportedTransport(def.getConnection().getType())) return null;
                    try {
                        return mcpClientFactory.getOrCreateClient(def);
                    } catch (Exception e) {
                        log.error("创建 MCP 客户端失败: serverId={}", sel.getServerId(), e);
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
            if (!isSupportedTransport(server.getConnection().getType())) continue;
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
