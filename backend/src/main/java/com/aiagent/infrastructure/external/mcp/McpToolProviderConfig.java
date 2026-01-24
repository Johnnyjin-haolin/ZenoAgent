package com.aiagent.infrastructure.external.mcp;

import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.PostConstruct;

/**
 * MCP工具提供者配置
 * 创建基础的工具提供者（包含所有启用的MCP客户端）
 * 
 * 注意：动态过滤的工具提供者由 McpToolProviderFactory 创建
 * 
 * @author aiagent
 */
@Slf4j
@Configuration
@DependsOn({"mcpGroupManager", "mcpClientFactory", "mcpToolProviderFactory"})
public class McpToolProviderConfig {
    
    @Autowired
    private McpToolProviderFactory mcpToolProviderFactory;
    
    /**
     * 创建基础工具提供者（包含所有启用的MCP客户端）
     * 用于不需要过滤的场景
     * 
     * 注意：如果MCP未启用或没有可用客户端，返回null
     */
    @Bean
    public ToolProvider mcpToolProvider() {
        log.info("创建基础MCP工具提供者Bean...");
        ToolProvider provider = mcpToolProviderFactory.createBaseToolProvider();
        if (provider == null) {
            log.warn("MCP工具提供者为null，可能MCP未启用或没有可用客户端");
        }
        return provider;
    }
    
    /**
     * 初始化后验证工具提供者
     */
    @PostConstruct
    public void validateToolProvider() {
        log.info("MCP工具提供者配置完成");
    }
}

