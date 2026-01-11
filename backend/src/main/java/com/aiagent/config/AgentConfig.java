package com.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
     * LLM配置
     */
    private LLMConfig llm = new LLMConfig();
    
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
    public static class LLMConfig {
        /**
         * 模型列表配置
         */
        private List<ModelDefinition> models = new ArrayList<>();
        
        /**
         * 默认模型ID（从models列表中选择）
         */
        private String defaultModel = "gpt-4o-mini";
        
        @Data
        public static class ModelDefinition {
            /**
             * 模型ID（唯一标识）
             */
            private String id;
            
            /**
             * 模型名称（显示用）
             */
            private String name;
            
            /**
             * 模型提供商（OPENAI, GLM等）
             */
            private String provider = "OPENAI";
            
            /**
             * 模型类型（CHAT: 对话模型, EMBEDDING: 向量模型）
             * 默认为 CHAT
             */
            private String type = "CHAT";
            
            /**
             * 实际模型名称（用于API调用）
             * 对于Embedding模型，这是实际的模型名称（如 text-embedding-3-small）
             * 对于Chat模型，如果未指定，则使用 id
             */
            private String modelName;
            
            /**
             * API Key
             */
            private String apiKey;
            
            /**
             * Base URL
             */
            private String baseUrl = "https://api.openai.com/v1";
        }
    }
    
    @Data
    public static class ModelConfig {
        /**
         * 默认模型ID
         */
        private String defaultModelId = "gpt-4o-mini";
        
        /**
         * 任务模型映射（值为模型ID列表，按顺序尝试）
         * Key: 任务类型（SIMPLE_CHAT, RAG_QUERY等）
         * Value: 模型ID列表
         */
        private Map<String, List<String>> taskModelMapping = new HashMap<>();
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
        
        /**
         * 默认 Embedding 模型ID（从 llm.models 中选择）
         */
        private String defaultEmbeddingModelId = "text-embedding-3-small";
        
        /**
         * 向量存储配置
         */
        private EmbeddingStoreConfig embeddingStore = new EmbeddingStoreConfig();
        
        @Data
        public static class EmbeddingStoreConfig {
            private String host = "localhost";
            private int port = 5432;
            private String database = "aiagent";
            private String user = "postgres";
            private String password = "postgres";
            private String table = "embeddings";
            private boolean useIndex = true;
            private int indexListSize = 100;
        }
    }
    
    @Data
    public static class ToolConfig {
        private boolean enabledByDefault = true;
    }
}

