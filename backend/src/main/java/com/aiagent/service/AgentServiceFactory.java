package com.aiagent.service;

import com.aiagent.config.AgentConfig;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent服务工厂
 * 负责创建和管理 LangChain4j AI Services
 * 
 * 优化点：
 * - 复用 StreamingChatLanguageModel 实例
 * - 根据启用的分组动态创建过滤后的 ToolProvider
 * - 缓存服务实例（可选）
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class AgentServiceFactory {
    
    @Autowired
    private AgentConfig agentConfig;
    
    @Autowired
    private McpToolProviderFactory mcpToolProviderFactory;
    
    @Value("${aiagent.llm.api-key:}")
    private String defaultApiKey;
    
    @Value("${aiagent.llm.base-url:https://api.openai.com/v1}")
    private String defaultBaseUrl;
    
    /**
     * 模型实例缓存（modelName -> StreamingChatLanguageModel）
     */
    private final Map<String, StreamingChatModel> modelCache = new ConcurrentHashMap<>();
    
    /**
     * 创建Agent服务接口
     */
    public interface AgentService {
        TokenStream chat(String userMessage);
    }
    
    /**
     * 创建Agent服务（带工具支持）
     * 
     * @param modelId 模型ID
     * @param enabledGroups 启用的MCP分组列表（为空则使用所有工具）
     * @return Agent服务实例
     */
    public AgentService createAgentService(String modelId, List<String> enabledGroups) {
        log.info("创建Agent服务: modelId={}, enabledGroups={}", modelId, enabledGroups);
        
        // 1. 获取或创建模型实例
        StreamingChatModel chatModel = getOrCreateModel(modelId);
        
        // 2. 创建过滤后的工具提供者
        ToolProvider toolProvider = mcpToolProviderFactory.createFilteredToolProvider(enabledGroups);
        
        // 3. 创建Agent服务
        AiServices<AgentService> builder =
            AiServices.builder(AgentService.class)
                .streamingChatModel(chatModel);
        
        // 如果有工具提供者，则注册
        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }
        
        AgentService agentService = builder.build();
        
        log.info("Agent服务创建成功: modelId={}", modelId);
        return agentService;
    }
    
    /**
     * 创建Agent服务（无工具支持）
     */
    public AgentService createAgentServiceWithoutTools(String modelId) {
        log.info("创建Agent服务（无工具）: modelId={}", modelId);

        StreamingChatModel chatModel = getOrCreateModel(modelId);

        return AiServices.builder(AgentService.class)
            .streamingChatModel(chatModel)
            .build();
    }
    
    /**
     * 获取或创建模型实例
     */
    private StreamingChatModel getOrCreateModel(String modelId) {
        // 确定模型名称
        String modelName = agentConfig.getModel().getDefaultModelId();
        if (modelId != null && !modelId.isEmpty() && !modelId.equals(modelName)) {
            modelName = modelId;
        }
        
        // 检查缓存
        if (modelCache.containsKey(modelName)) {
            return modelCache.get(modelName);
        }
        
        // 创建新模型实例
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = defaultApiKey;
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("未配置OPENAI_API_KEY");
        }

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(defaultBaseUrl)
            .modelName(modelName)
            .temperature(0.7)
            .build();
        
        // 缓存模型实例
        modelCache.put(modelName, model);
        
        log.info("创建并缓存模型实例: modelName={}", modelName);
        return model;
    }
    
    /**
     * 清除模型缓存
     */
    public void clearModelCache() {
        modelCache.clear();
        log.info("模型缓存已清除");
    }
}

