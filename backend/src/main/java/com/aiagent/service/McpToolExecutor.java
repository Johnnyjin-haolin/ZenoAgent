package com.aiagent.service;

import com.aiagent.vo.McpToolInfo;
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
        String connectionType = toolInfo.getConnectionType();
        String serverId = toolInfo.getServerId();
        String toolName = toolInfo.getName();
        
        log.info("执行MCP工具: name={}, type={}, serverId={}", toolName, connectionType, serverId);
        
        if ("http".equals(connectionType) || "stdio".equals(connectionType) || 
            "remote".equals(connectionType)) {
            // 使用LangChain4j MCP客户端执行工具
            try {
                McpClient client = mcpGroupManager.getMcpClient(serverId);
                if (client == null) {
                    throw new IllegalStateException("MCP客户端未找到: serverId=" + serverId);
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
                
                log.info("MCP工具执行成功: name={}", toolName);
                
                // 返回结果内容
                // 注意：根据LangChain4j MCP API，结果可能是McpCallToolResult
                // 需要根据实际API调整
                if (result != null && result.result() != null) {
                    return result.result();
                }
                return result;
                
            } catch (Exception e) {
                log.error("通过MCP客户端执行工具失败: name={}, serverId={}", toolName, serverId, e);
                throw new RuntimeException("工具执行失败: " + e.getMessage(), e);
            }
            
        } else if ("local".equals(connectionType)) {
            // 本地工具执行（未来扩展）
            throw new UnsupportedOperationException("本地工具执行器未实现，工具: " + toolName);
        } else {
            throw new IllegalArgumentException("不支持的工具类型: " + connectionType + ", 工具: " + toolName);
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
