package com.aiagent.service.llm;

import com.aiagent.config.AgentConfig;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
     * 
     * @return EmbeddingModel实例
     */
    public EmbeddingModel getDefaultEmbeddingModel() {
        String defaultModelId = agentConfig.getRag().getDefaultEmbeddingModelId();
        if (defaultModelId == null || defaultModelId.isEmpty()) {
            throw new RuntimeException("未配置默认 Embedding 模型ID");
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
                // 检查是否为 Embedding 模型
                String type = m.getType();
                return type != null && "EMBEDDING".equalsIgnoreCase(type);
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
        
        // 所有提供商都使用OpenAI兼容接口
        switch (provider) {
            case "OPENAI":
            default:
                // 所有OpenAI兼容接口的提供商都使用OpenAiEmbeddingModel
                log.info("创建 Embedding 模型: provider={}, modelId={}, modelName={}, baseUrl={}", 
                    provider, modelDef.getId(), modelName, baseUrl);
                return OpenAiEmbeddingModel.builder()
                    .apiKey(modelDef.getApiKey())
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .build();
        }
    }
    
    /**
     * 清除模型缓存
     */
    public void clearModelCache() {
        embeddingModelCache.clear();
        log.info("Embedding 模型缓存已清除");
    }
}
