package com.aiagent.infrastructure.external.mcp;

import com.aiagent.infrastructure.config.McpServerConfig;
import com.aiagent.domain.enums.ConnectionTypeEnums;
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
 * MCP工具提供者工厂
 * 根据启用的分组动态创建过滤后的工具提供者
 * 
 * 根据LangChain4j文档最佳实践：
 * - 支持工具名称过滤（filterToolNames）
 * - 支持自定义过滤器（filter）
 * - 支持多个客户端
 * - 处理工具名称冲突
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class McpToolProviderFactory {
    
    @Autowired
    private McpServerConfig mcpConfig;
    
    @Autowired
    private McpClientFactory mcpClientFactory;
    
    @Autowired
    private McpGroupManager mcpGroupManager;
    
    /**
     * 创建基础工具提供者（包含所有启用的MCP客户端）
     */
    public ToolProvider createBaseToolProvider() {
        if (!mcpConfig.isEnabled()) {
            log.info("MCP功能已禁用，返回null工具提供者");
            return null;
        }
        
        log.info("创建基础MCP工具提供者...");
        
        // 获取所有启用的MCP客户端
        List<McpClient> mcpClients = getAllEnabledClients();
        
        if (mcpClients.isEmpty()) {
            log.warn("未找到可用的MCP客户端，返回null工具提供者");
            return null;
        }
        
        // 使用LangChain4j的McpToolProvider创建工具提供者
        ToolProvider toolProvider = McpToolProvider.builder()
            .mcpClients(mcpClients)
            .failIfOneServerFails(false) // 如果某个服务器失败，不影响其他服务器
            .build();
        
        log.info("基础MCP工具提供者创建成功，包含 {} 个MCP客户端", mcpClients.size());
        
        return toolProvider;
    }
    
    /**
     * 创建过滤后的工具提供者（根据启用的分组）
     * 
     * @param enabledGroups 启用的分组列表（为空或null则返回所有工具）
     * @return 过滤后的工具提供者
     */
    public ToolProvider createFilteredToolProvider(List<String> enabledGroups) {
        if (!mcpConfig.isEnabled()) {
            return null;
        }
        
        // 获取所有启用的MCP客户端
        List<McpClient> mcpClients = getAllEnabledClients();
        
        if (mcpClients.isEmpty()) {
            return null;
        }
        
        // 如果没有指定分组，返回所有工具
        if (enabledGroups == null || enabledGroups.isEmpty()) {
            return createBaseToolProvider();
        }
        
        log.info("创建过滤后的MCP工具提供者，启用分组: {}", enabledGroups);
        
        // 获取指定分组的工具名称列表
        List<String> toolNames = mcpGroupManager.getToolsByGroups(enabledGroups).stream()
            .map(com.aiagent.api.dto.McpToolInfo::getName)
            .collect(Collectors.toList());
        
        if (toolNames.isEmpty()) {
            log.warn("指定分组未找到工具，启用分组: {}", enabledGroups);
            return null;
        }
        
        log.info("过滤工具，保留 {} 个工具", toolNames.size());
        
        // 使用工具名称过滤
        ToolProvider toolProvider = McpToolProvider.builder()
            .mcpClients(mcpClients)
            .filterToolNames(toolNames.toArray(new String[0]))  // 按工具名称过滤
            .failIfOneServerFails(false)
            .build();
        
        log.info("过滤后的MCP工具提供者创建成功，包含 {} 个工具", toolNames.size());
        
        return toolProvider;
    }
    
    /**
     * 创建自定义过滤的工具提供者
     * 
     * @param filter 自定义过滤器（BiPredicate<McpClient, ToolSpecification>）
     * @return 过滤后的工具提供者
     */
    public ToolProvider createCustomFilteredToolProvider(
            java.util.function.BiPredicate<McpClient, dev.langchain4j.agent.tool.ToolSpecification> filter) {
        if (!mcpConfig.isEnabled()) {
            return null;
        }
        
        List<McpClient> mcpClients = getAllEnabledClients();
        
        if (mcpClients.isEmpty()) {
            return null;
        }
        
        return McpToolProvider.builder()
            .mcpClients(mcpClients)
            .filter(filter)  // 自定义过滤器
            .failIfOneServerFails(false)
            .build();
    }
    
    /**
     * 获取所有启用的MCP客户端
     */
    private List<McpClient> getAllEnabledClients() {
        List<McpClient> mcpClients = new ArrayList<>();
        
        for (McpServerConfig.McpServerDefinition server : mcpConfig.getServers()) {
            if (!server.isEnabled()) {
                continue;
            }
            
            ConnectionTypeEnums connectionType = server.getConnection().getType();
            // 只处理支持的传输类型
            if (connectionType != null && isSupportedTransportType(connectionType)) {
                try {
                    // 获取或创建MCP客户端
                    McpClient client = mcpClientFactory.getOrCreateClient(server);
                    if (client != null) {
                        mcpClients.add(client);
                        log.debug("添加MCP客户端到工具提供者: serverId={}", server.getId());
                    }
                } catch (Exception e) {
                    log.error("获取或创建MCP客户端失败: serverId={}", server.getId(), e);
                }
            }
        }
        
        return mcpClients;
    }
    
    /**
     * 检查是否为支持的传输类型
     * 
     * @param connectionType 连接类型枚举
     * @return 是否支持
     */
    private boolean isSupportedTransportType(ConnectionTypeEnums connectionType) {
        if (connectionType == null) {
            return false;
        }
        
        // 支持所有MCP标准传输类型
        return connectionType == ConnectionTypeEnums.STREAMABLE_HTTP ||
               connectionType == ConnectionTypeEnums.STDIO ||
               connectionType == ConnectionTypeEnums.WEBSOCKET ||
               connectionType == ConnectionTypeEnums.SSE;
               // DOCKER暂时不支持
    }
}

