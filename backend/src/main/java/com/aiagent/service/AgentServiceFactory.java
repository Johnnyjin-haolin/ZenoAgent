package com.aiagent.service;

import com.aiagent.config.AgentConfig;
import com.aiagent.service.llm.ModelManager;
import com.aiagent.service.tool.McpToolProviderFactory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

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
    
    @Autowired
    private ModelManager modelManager;
    
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
     * 获取或创建模型实例（根据任务类型，支持故障转移）
     */
    private StreamingChatModel getOrCreateModel(String modelId) {
        // 如果指定了modelId，直接使用
        if (modelId != null && !modelId.isEmpty()) {
            return modelManager.getOrCreateStreamingModel(modelId);
        }
        
        // 否则使用默认模型
        return modelManager.getOrCreateStreamingModel(
            agentConfig.getModel().getDefaultModelId()
        );
        }
        
    /**
     * 根据任务类型获取模型（支持故障转移）
     * 
     * @param taskType 任务类型
     * @return 模型实例
     */
    public StreamingChatModel getModelForTask(String taskType) {
        List<String> modelIds = modelManager.getModelIdsForTask(taskType);
        return (StreamingChatModel) modelManager.tryModelsWithFallback(modelIds, true);
    }
    
    /**
     * 清除模型缓存
     */
    public void clearModelCache() {
        modelManager.clearModelCache();
        log.info("模型缓存已清除");
    }
}

