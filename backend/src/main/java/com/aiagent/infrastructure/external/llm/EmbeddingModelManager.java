package com.aiagent.infrastructure.external.llm;

import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.domain.enums.ModelType;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Embedding 模型管理器
 * 支持多模型配置和统一管理
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class EmbeddingModelManager {
    
    @Autowired
    private AgentConfig agentConfig;
    
    /**
     * Embedding模型缓存（modelId -> EmbeddingModel）
     */
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    
    /**
     * 获取或创建 Embedding 模型实例
     * 
     * @param modelId 模型ID（如果为空，使用默认模型）
     * @return EmbeddingModel实例
     */
    public EmbeddingModel getOrCreateEmbeddingModel(String modelId) {
        // 如果未指定模型ID，使用默认模型
        if (modelId == null || modelId.isEmpty()) {
            modelId = agentConfig.getRag().getDefaultEmbeddingModelId();
            if (modelId == null || modelId.isEmpty()) {
                throw new RuntimeException("未配置默认 Embedding 模型ID，请在 application.yml 中配置 aiagent.rag.default-embedding-model-id");
            }
        }
        
        // 检查缓存
        if (embeddingModelCache.containsKey(modelId)) {
            return embeddingModelCache.get(modelId);
        }
        
        // 创建新模型实例
        AgentConfig.LLMConfig.ModelDefinition modelDef = getEmbeddingModelDefinition(modelId);
        if (modelDef == null) {
            throw new RuntimeException("未找到 Embedding 模型配置: " + modelId + "，请确保该模型在 aiagent.llm.models 中配置且 type 为 EMBEDDING");
        }
        
        if (modelDef.getApiKey() == null || modelDef.getApiKey().isEmpty()) {
            throw new RuntimeException("模型 " + modelId + " 未配置API Key");
        }
        
        EmbeddingModel model = createEmbeddingModel(modelDef);
        
        // 缓存模型实例
        embeddingModelCache.put(modelId, model);
        
        log.info("创建并缓存 Embedding 模型实例: modelId={}, name={}, modelName={}", 
                modelId, modelDef.getName(), modelDef.getModelName());
        return model;
    }
    
    /**
     * 获取默认 Embedding 模型
     * 优先从 task-model-mapping 的 RAG_QUERY 场景获取，如果没有配置则使用 rag.default-embedding-model-id
     * 
     * @return EmbeddingModel实例
     */
    public EmbeddingModel getDefaultEmbeddingModel() {
        String defaultModelId = null;
        
        // 优先从 task-model-mapping 的 RAG_QUERY 场景获取
        Map<String, List<String>> taskModelMapping = agentConfig.getModel().getTaskModelMapping();
        if (taskModelMapping != null && taskModelMapping.containsKey("RAG_QUERY")) {
            List<String> modelIds = taskModelMapping.get("RAG_QUERY");
            if (modelIds != null && !modelIds.isEmpty()) {
                // 取第一个模型（最高优先级）
                defaultModelId = modelIds.get(0);
                log.debug("从 task-model-mapping.RAG_QUERY 获取默认 Embedding 模型: {}", defaultModelId);
            }
        }
        
        // 如果没有配置，使用 rag.default-embedding-model-id
        if (defaultModelId == null || defaultModelId.isEmpty()) {
            defaultModelId = agentConfig.getRag().getDefaultEmbeddingModelId();
            log.debug("从 rag.default-embedding-model-id 获取默认 Embedding 模型: {}", defaultModelId);
        }
        
        if (defaultModelId == null || defaultModelId.isEmpty()) {
            throw new RuntimeException("未配置默认 Embedding 模型ID，请在 task-model-mapping.RAG_QUERY 或 rag.default-embedding-model-id 中配置");
        }
        
        return getOrCreateEmbeddingModel(defaultModelId);
    }
    
    /**
     * 获取 Embedding 模型定义（只返回 type 为 EMBEDDING 的模型）
     */
    private AgentConfig.LLMConfig.ModelDefinition getEmbeddingModelDefinition(String modelId) {
        List<AgentConfig.LLMConfig.ModelDefinition> models = agentConfig.getLlm().getModels();
        if (models == null) {
            return null;
        }
        
        return models.stream()
            .filter(m -> modelId.equals(m.getId()))
            .filter(m -> {
                // 检查是否为 Embedding 模型（使用枚举）
                ModelType modelType = ModelType.fromCode(m.getType());
                return modelType == ModelType.EMBEDDING;
            })
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 创建 Embedding 模型
     * 
     * 支持的提供商：
     * - OPENAI: OpenAI官方API
     * - 其他OpenAI兼容接口的提供商
     */
    private EmbeddingModel createEmbeddingModel(AgentConfig.LLMConfig.ModelDefinition modelDef) {
        String provider = modelDef.getProvider() != null ? modelDef.getProvider().toUpperCase() : "OPENAI";
        
        // 获取baseUrl，如果没有配置则使用默认值
        String baseUrl = modelDef.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            switch (provider) {
                case "OPENAI":
                    baseUrl = "https://api.openai.com/v1";
                    break;
                default:
                    baseUrl = "https://api.openai.com/v1";
            }
        }
        
        // 获取实际模型名称（对于Embedding模型，这是必需的）
        String modelName = modelDef.getModelName();
        if (modelName == null || modelName.isEmpty()) {
            // 如果没有指定 modelName，使用 id（向后兼容）
            modelName = modelDef.getId();
            log.warn("模型 {} 未指定 modelName，使用 id 作为模型名称: {}", modelDef.getId(), modelName);
        }
        
        // 解析超时时间
        int timeoutSeconds = resolveTimeoutSeconds(modelDef);
        
        // 计算连接超时（通常比请求超时短一些）
        int connectTimeoutSeconds = Math.min(30, timeoutSeconds / 2);
        if (connectTimeoutSeconds < 10) {
            connectTimeoutSeconds = 10; // 最少10秒连接超时
        }
        
        // 所有提供商都使用OpenAI兼容接口
        switch (provider) {
            case "OPENAI":
            default:
                // 所有OpenAI兼容接口的提供商都使用OpenAiEmbeddingModel
                // 显式配置 HTTP 客户端，确保超时设置生效
                return OpenAiEmbeddingModel.builder()
                    .httpClientBuilder(new JdkHttpClientBuilder()
                        .httpClientBuilder(HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                            .version(HttpClient.Version.HTTP_1_1)))
                    .apiKey(modelDef.getApiKey())
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        }
    }
    
    /**
     * 解析超时时间（秒）
     * 优先级：模型配置 > 全局配置 > 默认值（120秒）
     * 
     * @param modelDef 模型定义
     * @return 超时时间（秒）
     */
    private int resolveTimeoutSeconds(AgentConfig.LLMConfig.ModelDefinition modelDef) {
        if (modelDef.getTimeoutSeconds() != null && modelDef.getTimeoutSeconds() > 0) {
            return modelDef.getTimeoutSeconds();
        }
        int globalTimeout = agentConfig.getLlm().getTimeoutSeconds();
        return globalTimeout > 0 ? globalTimeout : 120;
    }
    
    /**
     * 清除模型缓存
     */
    public void clearModelCache() {
        embeddingModelCache.clear();
        log.info("Embedding 模型缓存已清除");
    }
}
