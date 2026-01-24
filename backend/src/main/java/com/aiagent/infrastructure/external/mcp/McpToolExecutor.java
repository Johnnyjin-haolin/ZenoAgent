package com.aiagent.infrastructure.external.mcp;

import com.aiagent.domain.enums.ConnectionTypeEnums;
import com.aiagent.api.dto.McpToolInfo;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP工具执行器
 * 用于直接调用MCP工具（非LangChain4j自动调用场景）
 * 
 * 注意：LangChain4j的McpToolProvider会自动处理工具执行
 * 这个执行器主要用于直接调用场景，例如API接口直接调用工具
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class McpToolExecutor {
    
    @Autowired
    private McpGroupManager mcpGroupManager;
    
    /**
     * 执行MCP工具（直接调用）
     * 使用LangChain4j MCP客户端执行工具
     * 
     * @param toolInfo 工具信息
     * @param params 工具参数
     * @return 执行结果
     */
    public Object execute(McpToolInfo toolInfo, Map<String, Object> params) {
        ConnectionTypeEnums connectionType = toolInfo.getConnectionType();
        String serverId = toolInfo.getServerId();
        String toolName = toolInfo.getName();
        
        // 如果connectionType为null，使用默认值
        if (connectionType == null) {
            log.warn("工具 {} 的connectionType为null，使用默认值STDIO", toolName);
            connectionType = ConnectionTypeEnums.STDIO;
        }
        
        log.info("执行MCP工具: name={}, type={}, serverId={}", toolName, connectionType, serverId);
        
        // 根据连接类型执行工具
        switch (connectionType) {
            case STREAMABLE_HTTP:
            case STDIO:
            case WEBSOCKET:
            case SSE:
                // 使用LangChain4j MCP客户端执行工具（支持所有MCP标准传输类型）
                return executeViaMcpClient(toolInfo, params, serverId, toolName, connectionType);

            default:
                throw new IllegalArgumentException(
                    String.format("不支持的工具连接类型: %s, 工具: %s", connectionType, toolName));
        }
    }
    
    /**
     * 通过MCP客户端执行工具
     * 
     * @param toolInfo 工具信息
     * @param params 工具参数
     * @param serverId 服务器ID
     * @param toolName 工具名称
     * @param connectionType 连接类型
     * @return 执行结果
     */
    private Object executeViaMcpClient(McpToolInfo toolInfo, Map<String, Object> params, 
                                       String serverId, String toolName, ConnectionTypeEnums connectionType) {
        try {
            McpClient client = mcpGroupManager.getMcpClient(serverId);
            if (client == null) {
                throw new IllegalStateException(
                    String.format("MCP客户端未找到: serverId=%s, connectionType=%s", serverId, connectionType));
            }
            
            // 构建调用请求
            // 注意：LangChain4j MCP客户端的API可能需要根据实际版本调整
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .id(toolInfo.getId())
                    .name(toolName)
                    .arguments(JSON.toJSONString(params))
                    .build();

            // 调用工具
            ToolExecutionResult result = client.executeTool(request);
            
            log.info("MCP工具执行成功: name={}, type={}, serverId={}", toolName, connectionType, serverId);
            
            // 返回结果内容
            // 注意：根据LangChain4j MCP API，结果可能是McpCallToolResult
            // 需要根据实际API调整
            if (result != null && result.result() != null) {
                return result.result();
            }
            return result;
            
        } catch (Exception e) {
            log.error("通过MCP客户端执行工具失败: name={}, serverId={}, type={}", 
                toolName, serverId, connectionType, e);
            throw new RuntimeException(
                String.format("工具执行失败: %s (serverId: %s, type: %s)", 
                    e.getMessage(), serverId, connectionType), e);
        }
    }
    
    /**
     * 执行MCP工具（通过工具名称）
     * 
     * @param toolName 工具名称（格式：serverId:toolName 或 toolName）
     * @param params 工具参数
     * @return 执行结果
     */
    public Object executeByName(String toolName, Map<String, Object> params) {
        // 从所有工具中查找
        McpToolInfo toolInfo = mcpGroupManager.getAllTools().stream()
            .filter(tool -> toolName.equals(tool.getName()) || toolName.equals(tool.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("工具未找到: " + toolName));
        
        return execute(toolInfo, params);
    }
}
