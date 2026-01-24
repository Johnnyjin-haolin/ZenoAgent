package com.aiagent.infrastructure.external.mcp;

import com.aiagent.infrastructure.config.McpServerConfig;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP传输工厂
 * 根据配置创建正确的MCP传输实例
 * 
 * 支持类型：
 * - streamable-http: StreamableHttpMcpTransport（推荐）
 * - stdio: StdioMcpTransport
 * - websocket: WebSocketMcpTransport（未来支持）
 * - docker: DockerMcpTransport（未来支持）
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class McpTransportFactory {
    
    /**
     * 创建MCP传输
     * 
     * @param server 服务器配置
     * @return MCP传输实例
     */
    public McpTransport createTransport(McpServerConfig.McpServerDefinition server) {
        String type = server.getConnection().getType().getValue();
        String url = server.getConnection().getUrl();
        
        log.info("创建MCP传输: serverId={}, type={}", server.getId(), type);
        
        if ("streamable-http".equalsIgnoreCase(type) || 
            "http".equalsIgnoreCase(type) || 
            "remote".equalsIgnoreCase(type)) {
            // Streamable HTTP传输（推荐）
            return createStreamableHttpTransport(server, url);
            
        } else if ("stdio".equalsIgnoreCase(type) || 
                   "local".equalsIgnoreCase(type)) {
            // Stdio传输
            return createStdioTransport(server, url);
            
        } else if ("websocket".equalsIgnoreCase(type) || 
                   "ws".equalsIgnoreCase(type)) {
            // WebSocket传输（未来支持）
            log.warn("WebSocket传输暂未实现，serverId={}", server.getId());
            throw new UnsupportedOperationException("WebSocket传输暂未实现: serverId=" + server.getId());
            
        } else if ("docker".equalsIgnoreCase(type)) {
            // Docker传输（未来支持）
            log.warn("Docker传输暂未实现，serverId={}", server.getId());
            throw new UnsupportedOperationException("Docker传输暂未实现: serverId=" + server.getId());
            
        } else {
            throw new IllegalArgumentException("不支持的传输类型: " + type + ", serverId=" + server.getId());
        }
    }
    
    /**
     * 创建Streamable HTTP传输
     */
    private McpTransport createStreamableHttpTransport(McpServerConfig.McpServerDefinition server, String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Streamable HTTP传输需要配置URL: serverId=" + server.getId());
        }
        // 设置headers（包括Authorization等）
        // 使用反射尝试设置headers，因为LangChain4j MCP的API可能不同版本有差异
        Map<String, String> headers = server.getConnection().getHeaders();

        StreamableHttpMcpTransport.Builder builder = StreamableHttpMcpTransport.builder()
            .url(url)
            .logRequests(true).customHeaders(headers)
            .logResponses(true);
        
        return builder.build();
    }
    
    /**
     * 创建Stdio传输
     */
    private McpTransport createStdioTransport(McpServerConfig.McpServerDefinition server, String url) {
        // 从配置中获取命令
        List<String> command = parseCommand(url, server);
        
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Stdio传输需要配置命令: serverId=" + server.getId());
        }
        
        return StdioMcpTransport.builder()
            .command(command)
            .logEvents(true)
            .build();
    }
    
    /**
     * 解析命令（从URL或配置中）
     * 
     * 注意：这是一个简化实现，实际应该从配置中读取命令列表
     */
    private List<String> parseCommand(String url, McpServerConfig.McpServerDefinition server) {
        // 如果URL是命令字符串，按空格分割
        if (url != null && !url.isEmpty()) {
            // 简单处理：按空格分割
            // 实际应该支持更复杂的配置，比如JSON数组
            String[] parts = url.split("\\s+");
            List<String> command = new ArrayList<>();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    command.add(part);
                }
            }
            if (!command.isEmpty()) {
                return command;
            }
        }
        
        // 默认：返回null，表示需要配置
        return null;
    }
}

