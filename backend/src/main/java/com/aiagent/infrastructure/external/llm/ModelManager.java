package com.aiagent.infrastructure.external.llm;

import com.aiagent.infrastructure.config.AgentConfig;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.net.http.HttpClient;

/**
 * 模型管理器
 * 支持多模型配置和故障转移
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ModelManager {
    
    @Autowired
    private AgentConfig agentConfig;
    
    /**
     * 流式模型缓存（modelId -> StreamingChatModel）
     */
    private final Map<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();
    
    /**
     * 非流式模型缓存（modelId -> ChatModel）
     */
    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    
    /**
     * 根据任务类型获取模型ID列表（按优先级排序）
     * 
     * @param taskType 任务类型（SIMPLE_CHAT, RAG_QUERY等）
     * @return 模型ID列表
     */
    public List<String> getModelIdsForTask(String taskType) {
        Map<String, List<String>> taskModelMapping = agentConfig.getModel().getTaskModelMapping();
        
        if (taskModelMapping != null && taskModelMapping.containsKey(taskType)) {
            List<String> modelIds = taskModelMapping.get(taskType);
            if (modelIds != null && !modelIds.isEmpty()) {
                return new ArrayList<>(modelIds);
            }
        }
        
        // 如果没有配置，返回默认模型
        List<String> defaultModels = new ArrayList<>();
        defaultModels.add(agentConfig.getModel().getDefaultModelId());
        return defaultModels;
    }
    
    /**
     * 获取或创建流式模型实例
     * 
     * @param modelId 模型ID
     * @return StreamingChatModel实例
     */
    public StreamingChatModel getOrCreateStreamingModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            modelId = agentConfig.getModel().getDefaultModelId();
        }
        
        // 检查缓存
        if (streamingModelCache.containsKey(modelId)) {
            return streamingModelCache.get(modelId);
        }
        
        // 创建新模型实例
        AgentConfig.LLMConfig.ModelDefinition modelDef = getModelDefinition(modelId);
        if (modelDef == null) {
            throw new RuntimeException("未找到模型配置: " + modelId);
        }
        
        if (modelDef.getApiKey() == null || modelDef.getApiKey().isEmpty()) {
            throw new RuntimeException("模型 " + modelId + " 未配置API Key");
        }
        
        StreamingChatModel model = createStreamingModel(modelDef);
        
        // 缓存模型实例
        streamingModelCache.put(modelId, model);
        
        log.info("创建并缓存流式模型实例: modelId={}, name={}", modelId, modelDef.getName());
        return model;
    }
    
    /**
     * 获取或创建非流式模型实例
     * 
     * @param modelId 模型ID
     * @return ChatModel实例
     */
    public ChatModel getOrCreateChatModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            modelId = agentConfig.getModel().getDefaultModelId();
        }
        
        // 检查缓存
        if (chatModelCache.containsKey(modelId)) {
            return chatModelCache.get(modelId);
        }
        
        // 创建新模型实例
        AgentConfig.LLMConfig.ModelDefinition modelDef = getModelDefinition(modelId);
        if (modelDef == null) {
            throw new RuntimeException("未找到模型配置: " + modelId);
        }
        
        if (modelDef.getApiKey() == null || modelDef.getApiKey().isEmpty()) {
            throw new RuntimeException("模型 " + modelId + " 未配置API Key");
        }
        
        ChatModel model = createChatModel(modelDef);
        
        // 缓存模型实例
        chatModelCache.put(modelId, model);
        
        log.info("创建并缓存非流式模型实例: modelId={}, name={}", modelId, modelDef.getName());
        return model;
    }
    
    /**
     * 按顺序尝试模型，直到成功（故障转移）
     * 
     * @param modelIds 模型ID列表（按优先级排序）
     * @param isStreaming 是否使用流式模型
     * @return 成功的模型实例
     * @throws RuntimeException 如果所有模型都失败
     */
    public Object tryModelsWithFallback(List<String> modelIds, boolean isStreaming) {
        List<String> triedModels = new ArrayList<>();
        Exception lastException = null;
        
        for (String modelId : modelIds) {
            try {
                log.debug("尝试使用模型: {}", modelId);
                Object model = isStreaming ? 
                    (Object) getOrCreateStreamingModel(modelId) : 
                    (Object) getOrCreateChatModel(modelId);
                log.info("成功使用模型: {}", modelId);
                return model;
            } catch (Exception e) {
                triedModels.add(modelId);
                lastException = e;
                log.warn("模型 {} 失败，尝试下一个: {}", modelId, e.getMessage());
            }
        }
        
        // 所有模型都失败
        throw new RuntimeException(
            String.format("所有模型都失败。尝试的模型: %s。最后错误: %s", 
                String.join(", ", triedModels), 
                lastException != null ? lastException.getMessage() : "未知错误"),
            lastException
        );
    }
    
    /**
     * 获取模型定义
     */
    private AgentConfig.LLMConfig.ModelDefinition getModelDefinition(String modelId) {
        List<AgentConfig.LLMConfig.ModelDefinition> models = agentConfig.getLlm().getModels();
        if (models == null) {
            return null;
        }
        
        return models.stream()
            .filter(m -> modelId.equals(m.getId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 创建流式模型
     * 
     * 支持的提供商：
     * - OPENAI: OpenAI官方API
     * - ZHIPU: 智谱AI（使用OpenAI兼容接口）
     * - DEEPSEEK: DeepSeek（使用OpenAI兼容接口）
     * - QWEN: 通义千问（使用OpenAI兼容接口）
     * - GLM: GLM模型（使用OpenAI兼容接口）
     * - 其他OpenAI兼容接口的提供商
     */
    private StreamingChatModel createStreamingModel(AgentConfig.LLMConfig.ModelDefinition modelDef) {
        String provider = modelDef.getProvider() != null ? modelDef.getProvider().toUpperCase() : "OPENAI";
        
        // 获取baseUrl，如果没有配置则使用默认值
        String baseUrl = modelDef.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            // 根据不同提供商设置默认baseUrl
            switch (provider) {
                case "OPENAI":
                    baseUrl = "https://api.openai.com/v1";
                    break;
                case "ZHIPU":
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4/";
                    break;
                case "DEEPSEEK":
                    baseUrl = "https://api.deepseek.com/v1";
                    break;
                case "QWEN":
                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
                    break;
                case "GLM":
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4/";
                    break;
                default:
                    baseUrl = "https://api.openai.com/v1";
            }
        }
        
        // 所有提供商都使用OpenAI兼容接口（如果支持OpenAI兼容接口）
        // 如果不支持，需要在case中添加特殊处理
        switch (provider) {
            case "OPENAI":
            case "ZHIPU":      // 智谱AI
            case "DEEPSEEK":   // DeepSeek
            case "QWEN":       // 通义千问
            case "GLM":        // GLM模型
            default:
                // 所有OpenAI兼容接口的提供商都使用OpenAiStreamingChatModel
                log.info("创建流式模型: provider={}, modelId={}, baseUrl={}", 
                    provider, modelDef.getId(), baseUrl);
                int timeoutSeconds = resolveTimeoutSeconds(modelDef);
                return OpenAiStreamingChatModel.builder()
                    .httpClientBuilder(new JdkHttpClientBuilder()
                        .httpClientBuilder(HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)))
                    .apiKey(modelDef.getApiKey())
                    .baseUrl(baseUrl)
                    .modelName(modelDef.getId())
                    .temperature(0.7)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        }
    }
    
    /**
     * 创建非流式模型
     * 
     * 支持的提供商：
     * - OPENAI: OpenAI官方API
     * - ZHIPU: 智谱AI（使用OpenAI兼容接口）
     * - DEEPSEEK: DeepSeek（使用OpenAI兼容接口）
     * - QWEN: 通义千问（使用OpenAI兼容接口）
     * - GLM: GLM模型（使用OpenAI兼容接口）
     * - 其他OpenAI兼容接口的提供商
     */
    private ChatModel createChatModel(AgentConfig.LLMConfig.ModelDefinition modelDef) {
        String provider = modelDef.getProvider() != null ? modelDef.getProvider().toUpperCase() : "OPENAI";
        
        // 获取baseUrl，如果没有配置则使用默认值
        String baseUrl = modelDef.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            // 根据不同提供商设置默认baseUrl
            switch (provider) {
                case "OPENAI":
                    baseUrl = "https://api.openai.com/v1";
                    break;
                case "ZHIPU":
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4/";
                    break;
                case "DEEPSEEK":
                    baseUrl = "https://api.deepseek.com/v1";
                    break;
                case "QWEN":
                    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
                    break;
                case "GLM":
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4/";
                    break;
                default:
                    baseUrl = "https://api.openai.com/v1";
            }
        }
        
        // 所有提供商都使用OpenAI兼容接口（如果支持OpenAI兼容接口）
        // 如果不支持，需要在case中添加特殊处理
        switch (provider) {
            case "OPENAI":
            case "ZHIPU":      // 智谱AI
            case "DEEPSEEK":   // DeepSeek
            case "QWEN":       // 通义千问
            case "GLM":        // GLM模型
            default:
                // 所有OpenAI兼容接口的提供商都使用OpenAiChatModel
                log.info("创建非流式模型: provider={}, modelId={}, baseUrl={}", 
                    provider, modelDef.getId(), baseUrl);
                int timeoutSeconds = resolveTimeoutSeconds(modelDef);
                return OpenAiChatModel.builder()
                    .httpClientBuilder(new JdkHttpClientBuilder()
                        .httpClientBuilder(HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)))
                    .apiKey(modelDef.getApiKey())
                    .baseUrl(baseUrl)
                    .modelName(modelDef.getId())
                    .temperature(0.7)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
        }
    }

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
        streamingModelCache.clear();
        chatModelCache.clear();
        log.info("模型缓存已清除");
    }
}

