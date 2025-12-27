package com.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent配置类
 * 从application.yml读取配置
 * 
 * @author aiagent
 */
@Data
@Component
@ConfigurationProperties(prefix = "aiagent")
public class AgentConfig {
    
    /**
     * 模型配置
     */
    private ModelConfig model = new ModelConfig();
    
    /**
     * 记忆配置
     */
    private MemoryConfig memory = new MemoryConfig();
    
    /**
     * RAG配置
     */
    private RAGConfig rag = new RAGConfig();
    
    /**
     * 工具配置
     */
    private ToolConfig tools = new ToolConfig();
    
    @Data
    public static class ModelConfig {
        private String defaultModelId = "gpt-4o-mini";
        private Map<String, String> taskModelMapping = new HashMap<>();
    }
    
    @Data
    public static class MemoryConfig {
        private int shortTermExpireHours = 24;
        private int contextExpireHours = 1;
        private int maxContextWindow = 20;
    }
    
    @Data
    public static class RAGConfig {
        private int defaultTopK = 5;
        private double defaultMinScore = 0.5;
    }
    
    @Data
    public static class ToolConfig {
        private boolean enabledByDefault = true;
    }
}

