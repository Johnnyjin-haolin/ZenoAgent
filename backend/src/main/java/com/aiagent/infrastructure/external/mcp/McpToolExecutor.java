package com.aiagent.infrastructure.external.mcp;

import com.aiagent.common.enums.ConnectionTypeEnums;
import com.aiagent.api.dto.McpToolInfo;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    public ToolExecutionResult execute(McpToolInfo toolInfo, String params) {
        ConnectionTypeEnums connectionType = toolInfo.getConnectionType();
        String serverId = toolInfo.getServerId();
        String toolName = toolInfo.getName();

        McpClient client = mcpGroupManager.getMcpClient(serverId);
        if (client == null) {
            throw new IllegalStateException(
                    String.format("MCP客户端未找到: serverId=%s, connectionType=%s", serverId, toolInfo.getConnectionType()));
        }

        log.info("执行MCP工具: name={}, type={}, serverId={}", toolName, connectionType, serverId);

        // 构建调用请求
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id(toolInfo.getId())
                .name(toolName)
                .arguments(params)
                .build();
        // 调用工具
        return client.executeTool(request);

    }

}
