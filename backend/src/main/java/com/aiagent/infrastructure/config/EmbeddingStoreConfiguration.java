package com.aiagent.infrastructure.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量存储配置
 * 管理PgVector EmbeddingStore实例
 * 
 * @author aiagent
 */
@Slf4j
@Configuration
public class EmbeddingStoreConfiguration {
    
    /**
     * EmbeddingStore缓存
     * Key: modelId + connectionInfo
     */
    private static final ConcurrentHashMap<String, EmbeddingStore<TextSegment>> EMBED_STORE_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 维度缓存（避免重复调用 dimension() API）
     * Key: modelId + modelName + baseUrl
     */
    private static final ConcurrentHashMap<String, Integer> DIMENSION_CACHE = new ConcurrentHashMap<>();
    
    @Autowired
    private AgentConfig agentConfig;
    
    /**
     * 获取或创建EmbeddingStore实例
     * 根据模型维度自动创建不同表名
     * 
     * @param embeddingModel Embedding模型（用于获取维度）
     * @param modelId 模型ID（用于缓存key）
     * @return EmbeddingStore实例
     */
    public EmbeddingStore<TextSegment> getEmbeddingStore(EmbeddingModel embeddingModel, String modelId) {
        AgentConfig.RAGConfig.EmbeddingStoreConfig config = agentConfig.getRag().getEmbeddingStore();
        
        String connectionInfo = config.getHost() + ":" + config.getPort() + "/" + config.getDatabase();
        String key = (modelId != null ? modelId : "default") + ":" + connectionInfo;
        
        // 从缓存获取
        if (EMBED_STORE_CACHE.containsKey(key)) {
            return EMBED_STORE_CACHE.get(key);
        }
        
        // 尝试从缓存获取维度（避免重复调用 API）
        String dimensionCacheKey = buildDimensionCacheKey(embeddingModel, modelId);
        Integer cachedDimension = DIMENSION_CACHE.get(dimensionCacheKey);
        
        int dimension;
        if (cachedDimension != null) {
            log.debug("从缓存获取维度: dimension={}", cachedDimension);
            dimension = cachedDimension;
        } else {
            log.info("调用 embeddingModel.dimension() 获取向量维度...");
            try {
                dimension = embeddingModel.dimension();
                log.info("成功获取维度: dimension={}", dimension);
                
                // 缓存维度信息
                DIMENSION_CACHE.put(dimensionCacheKey, dimension);
            } catch (Exception e) {
                log.error("获取维度失败: {}", e.getMessage(), e);
                throw e;
            }
        }
        
        String tableName = config.getTable();
        
        // 如果维度不是默认的，加上维度后缀
        // 默认维度通常是1536（OpenAI text-embedding-3-small）或768等
        if (dimension != 1536) {
            tableName += "_" + dimension;
        }
        
        log.info("Creating PgVectorEmbeddingStore: table={}, dimension={}", tableName, dimension);
        
        EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
                .host(config.getHost())
                .port(config.getPort())
                .database(config.getDatabase())
                .user(config.getUser())
                .password(config.getPassword())
                .table(tableName)
                .dimension(dimension)
                .useIndex(config.isUseIndex())
                .indexListSize(config.getIndexListSize())
                .createTable(true)
                .dropTableFirst(false)
                .build();
        
        // 放入缓存
        EMBED_STORE_CACHE.put(key, embeddingStore);
        
        return embeddingStore;
    }
    
    /**
     * 创建默认的EmbeddingStore（使用默认模型）
     * 
     * @param embeddingModel 默认Embedding模型
     * @return EmbeddingStore实例
     */
    public EmbeddingStore<TextSegment> createDefaultEmbeddingStore(EmbeddingModel embeddingModel) {
        return getEmbeddingStore(embeddingModel, "default");
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        EMBED_STORE_CACHE.clear();
        DIMENSION_CACHE.clear();
        log.info("EmbeddingStore 和 Dimension 缓存已清除");
    }
    
    /**
     * 构建维度缓存的 key
     * 使用 modelId + modelName 作为唯一标识
     */
    private String buildDimensionCacheKey(EmbeddingModel embeddingModel, String modelId) {
        StringBuilder key = new StringBuilder();
        key.append(modelId != null ? modelId : "default");
        
        if (embeddingModel instanceof OpenAiEmbeddingModel) {
            OpenAiEmbeddingModel openAiModel = (OpenAiEmbeddingModel) embeddingModel;
            key.append(":").append(openAiModel.modelName());
        } else {
            key.append(":").append(embeddingModel.getClass().getName());
        }
        
        return key.toString();
    }
}

