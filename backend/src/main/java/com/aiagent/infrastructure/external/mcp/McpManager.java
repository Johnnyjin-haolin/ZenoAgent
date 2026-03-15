package com.aiagent.infrastructure.external.mcp;

import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.infrastructure.config.McpServerConfig;
import com.aiagent.common.enums.ConnectionTypeEnums;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP 服务器 & 工具管理器
 * <p>
 * 职责：
 * - 维护 serverId → McpServerVO 和 serverId → Tools 的内存缓存
 * - 提供工具列表查询接口（供 AgentToolConfig 前端 API 使用）
 * - 管理 McpClient 实例的创建（委托 McpClientFactory）
 */
@Slf4j
@Component
public class McpManager {

    @Autowired
    private McpServerConfig mcpConfig;

    @Autowired
    private McpClientFactory mcpClientFactory;

    /**
     * serverId → McpServerConfig.McpServerDefinition
     */
    private final Map<String, McpServerConfig.McpServerDefinition> serverCache = new ConcurrentHashMap<>();

    /**
     * serverId → List<McpToolInfo>（工具列表缓存）
     */
    private final Map<String, List<McpToolInfo>> toolsByServer = new ConcurrentHashMap<>();

    /**
     * serverId → McpClient（仅 GLOBAL 类型）
     */
    private final Map<String, McpClient> serverClients = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!mcpConfig.isEnabled()) {
            log.info("MCP 功能已禁用，跳过初始化");
            return;
        }
        scan();
        mcpConfig.addChangeListener(this::reload);
    }

    public void reload() {
        log.info("重新加载 MCP 服务器配置...");
        serverCache.clear();
        toolsByServer.clear();
        serverClients.clear();
        scan();
        log.info("MCP 重新加载完成，共 {} 个服务器", serverCache.size());
    }

    private void scan() {
        for (McpServerConfig.McpServerDefinition server : mcpConfig.getServers()) {
            if (!server.isEnabled()) continue;

            serverCache.put(server.getId(), server);

            ConnectionTypeEnums connType = server.getConnection().getType();
            if (!isSupportedTransport(connType)) {
                log.warn("不支持的连接类型: {}, serverId={}", connType, server.getId());
                continue;
            }

            try {
                McpClient client = mcpClientFactory.getOrCreateClient(server);
                serverClients.put(server.getId(), client);

                List<ToolSpecification> specs = client.listTools();
                List<McpToolInfo> tools = specs.stream()
                        .map(spec -> toToolInfo(spec, server))
                        .collect(Collectors.toList());
                toolsByServer.put(server.getId(), tools);

                log.info("服务器 {} 加载了 {} 个工具", server.getId(), tools.size());
            } catch (Exception e) {
                log.error("加载服务器工具列表失败: serverId={}", server.getId(), e);
                toolsByServer.put(server.getId(), Collections.emptyList());
            }
        }
    }

    // ── 查询接口 ──────────────────────────────────────────────────────────────

    /**
     * 获取所有已启用服务器的定义列表
     */
    public List<McpServerConfig.McpServerDefinition> getAllServers() {
        return new ArrayList<>(serverCache.values());
    }

    /**
     * 获取指定服务器的工具列表
     */
    public List<McpToolInfo> getToolsByServerId(String serverId) {
        return toolsByServer.getOrDefault(serverId, Collections.emptyList());
    }

    /**
     * 获取指定服务器 ID 列表的所有工具（供 FunctionCallingEngine 使用）
     */
    public List<McpToolInfo> getToolsByServerIds(List<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return getAllTools();
        }
        return serverIds.stream()
                .flatMap(id -> toolsByServer.getOrDefault(id, Collections.emptyList()).stream())
                .filter(McpToolInfo::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有工具
     */
    public List<McpToolInfo> getAllTools() {
        return toolsByServer.values().stream()
                .flatMap(List::stream)
                .filter(McpToolInfo::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * 按工具名称查找
     */
    public McpToolInfo getToolByName(String toolName) {
        return toolsByServer.values().stream()
                .flatMap(List::stream)
                .filter(t -> toolName.equals(t.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取 MCP 客户端
     */
    public McpClient getMcpClient(String serverId) {
        return serverClients.get(serverId);
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private boolean isSupportedTransport(ConnectionTypeEnums connType) {
        if (connType == null) return false;
        return connType == ConnectionTypeEnums.STREAMABLE_HTTP
                || connType == ConnectionTypeEnums.STDIO
                || connType == ConnectionTypeEnums.WEBSOCKET
                || connType == ConnectionTypeEnums.SSE;
    }

    private McpToolInfo toToolInfo(ToolSpecification spec,
                                   McpServerConfig.McpServerDefinition server) {
        McpToolInfo tool = new McpToolInfo();
        tool.setId(server.getId() + ":" + spec.name());
        tool.setName(spec.name());
        tool.setDescription(spec.description());
        tool.setServerId(server.getId());
        tool.setEnabled(true);
        tool.setConnectionType(server.getConnection().getType());
        tool.setParameters(spec.parameters());
        tool.setMetadata(spec.metadata());
        return tool;
    }
}
