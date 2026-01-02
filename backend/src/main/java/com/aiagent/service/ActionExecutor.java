package com.aiagent.service;

import com.aiagent.service.action.LLMGenerateParams;
import com.aiagent.service.action.RAGRetrieveParams;
import com.aiagent.service.action.ToolCallParams;
import com.aiagent.service.tool.McpToolExecutor;
import com.aiagent.util.StringUtils;
import com.aiagent.vo.AgentContext;
import com.aiagent.vo.AgentKnowledgeResult;
import com.aiagent.vo.McpToolInfo;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 动作执行器
 * 负责执行Agent选定的动作
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ActionExecutor {
    
    @Autowired
    private RAGEnhancer ragEnhancer;
    
    @Autowired
    private SimpleLLMChatHandler llmChatHandler;
    
    @Autowired
    private MemorySystem memorySystem;
    
    @Autowired
    private McpToolExecutor mcpToolExecutor;
    
    @Autowired
    private IntelligentToolSelector toolSelector;
    
    /**
     * 执行动作
     */
    public ActionResult execute(AgentAction action, AgentContext context) {
        log.info("执行动作: {} - {}", action.getType(), action.getName());
        
        long startTime = System.currentTimeMillis();
        
        try {
            switch (action.getType()) {
                case TOOL_CALL:
                    return executeToolCall(action, context, startTime);
                case RAG_RETRIEVE:
                    return executeRAGRetrieve(action, context, startTime);
                case LLM_GENERATE:
                    return executeLLMGenerate(action, context, startTime);
                case COMPLETE:
                    return ActionResult.success("complete", "complete", 
                        "任务完成: " + action.getReasoning());
                default:
                    return ActionResult.failure(action.getType().name(), action.getName(),
                        "不支持的动作类型: " + action.getType(), "UNSUPPORTED_ACTION");
            }
        } catch (Exception e) {
            log.error("执行动作失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure(action.getType().name(), action.getName(),
                e.getMessage(), "EXCEPTION");
            result.setDuration(duration);
            return result;
        }
    }
    
    /**
     * 执行工具调用
     */
    private ActionResult executeToolCall(AgentAction action, AgentContext context, long startTime) {
        try {
            // 获取工具调用参数
            ToolCallParams toolCallParams = action.getToolCallParams();
            Map<String, Object> params;
            if (toolCallParams==null){
                throw new IllegalArgumentException("参数错误,toolCallParams为空");
            }
            
            // 使用特定类型参数
            params = Objects.requireNonNullElseGet(toolCallParams.getToolParams(), HashMap::new);
            // 如果toolCallParams中有toolName，优先使用
            String toolName = toolCallParams.getToolName();
            // 查找工具信息
            McpToolInfo toolInfo = toolSelector.getToolByName(toolName);
            if (toolInfo == null) {
                throw new IllegalArgumentException("工具未找到: " + toolName);
            }
            
            log.info("执行工具调用: name={}, params={}", toolName, toolCallParams);
            
            // 使用McpToolExecutor执行工具
            Object toolResult = mcpToolExecutor.execute(toolInfo, toolCallParams.getToolParams());

            // 将结果转换为字符串（用于记录和返回）
            String resultStr = parseToolResult(toolResult);
            
            // 记录工具调用历史
            memorySystem.recordToolCall(context, toolName, params, toolResult);
            
            long duration = System.currentTimeMillis() - startTime;
            ActionResult actionResult = ActionResult.success("tool_call", toolName, toolResult);
            actionResult.setDuration(duration);
            actionResult.setMetadata(Map.of(
                "toolName", toolName, 
                "params", params,
                "resultStr", resultStr
            ));
            
            log.info("工具调用成功: name={}, duration={}ms", toolName, duration);
            return actionResult;
            
        } catch (Exception e) {
            log.error("工具调用失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure("tool_call", action.getName(),
                e.getMessage(), "TOOL_CALL_ERROR");
            result.setDuration(duration);
            return result;
        }
    }
    
    /**
     * 解析工具执行结果为字符串
     */
    private String parseToolResult(Object toolResult) {
        if (toolResult == null) {
            return "工具执行完成，但未返回结果";
        }
        
        // 如果是字符串，直接返回
        if (toolResult instanceof String) {
            return (String) toolResult;
        }
        
        // 如果是Map或List，转换为JSON
        if (toolResult instanceof Map || toolResult instanceof List) {
            try {
                return JSON.toJSONString(toolResult);
            } catch (Exception e) {
                log.warn("工具结果JSON序列化失败", e);
                return toolResult.toString();
            }
        }
        
        // 其他类型，转换为字符串
        return toolResult.toString();
    }
    
    /**
     * 执行RAG检索
     */
    private ActionResult executeRAGRetrieve(AgentAction action, AgentContext context, long startTime) {
        try {
            // 获取RAG检索参数（优先使用特定类型参数）
            RAGRetrieveParams ragParams = action.getRagRetrieveParams();
            String query = "";
            List<String> knowledgeIds = List.of();
            
            if (ragParams != null) {
                // 使用特定类型参数
                query = ragParams.getQuery();
                knowledgeIds = Objects.requireNonNullElseGet(ragParams.getKnowledgeIds(), ArrayList::new);
            }
            
            // 如果query为空，使用reasoning作为查询
            if (StringUtils.isEmpty(query)) {
                query = action.getReasoning();
            }
            
            // 执行RAG检索
            AgentKnowledgeResult knowledgeResult = ragEnhancer.retrieve(query, knowledgeIds);
            
            // 记录RAG检索历史
            memorySystem.recordRAGRetrieve(context, query, knowledgeIds, 
                knowledgeResult != null ? knowledgeResult.getTotalCount() : 0);
            
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.success("rag_retrieve", "rag_retrieve", knowledgeResult);
            result.setDuration(duration);
            result.setMetadata(Map.of("query", query, "count",
                knowledgeResult != null ? knowledgeResult.getTotalCount() : 0));
            
            return result;
            
        } catch (Exception e) {
            log.error("RAG检索失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure("rag_retrieve", "rag_retrieve",
                e.getMessage(), "RAG_RETRIEVE_ERROR");
            result.setDuration(duration);
            return result;
        }
    }
    
    /**
     * 执行LLM生成
     * 注意：此方法返回TokenStream用于流式输出，实际内容需要在调用方处理
     */
    private ActionResult executeLLMGenerate(AgentAction action, AgentContext context, long startTime) {
        try {
            // 获取LLM生成参数（优先使用特定类型参数）
            LLMGenerateParams llmParams = action.getLlmGenerateParams();
            String prompt;
            String systemPrompt = null;
            
            if (llmParams == null) {
               throw  new IllegalArgumentException("llmParams 不能为空");
            }
            // 使用特定类型参数
            prompt = llmParams.getPrompt();
            systemPrompt = llmParams.getSystemPrompt();
            // 如果prompt为空，使用reasoning作为prompt
            if (StringUtils.isEmpty(prompt)) {
                prompt = action.getReasoning();
            }
            
            // 准备消息列表，包含完整的对话历史以保持上下文
            List<ChatMessage> messages = new ArrayList<>();
            
            // 1. 添加系统提示（如果有）
            if (StringUtils.isNotEmpty(systemPrompt)) {
                messages.add(new SystemMessage(systemPrompt));
            } else {
                // 默认系统提示
                messages.add(new SystemMessage("你是一个智能助手，能够帮助用户管理和查询阿里云资源。请用友好、专业的方式回答用户的问题。"));
            }
            
            // 2. 添加对话历史（保持上下文连贯性）
            if (context != null && context.getMessages() != null && !context.getMessages().isEmpty()) {
                List<ChatMessage> historyMessages = context.getMessages();
                
                // 保留最近的对话历史（避免token过多）
                // 保留最近10轮对话（20条消息）
                int maxHistoryMessages = 20;
                int startIdx = Math.max(0, historyMessages.size() - maxHistoryMessages);
                
                for (int i = startIdx; i < historyMessages.size(); i++) {
                    messages.add(historyMessages.get(i));
                }
                
                log.debug("包含对话历史: {} 条消息", historyMessages.size() - startIdx);
            }
            
            // 3. 添加当前的生成提示
            messages.add(new UserMessage(prompt));
            
            // 调用LLM
            String modelId = context != null ? context.getModelId() : null;
            if (StringUtils.isEmpty(modelId)) {
                modelId = "gpt-4o-mini";
            }
            
            String llmResponse;
            boolean isStreaming = false;
            
            if (context != null && context.getStreamingCallback() != null) {
                // 有回调，使用流式输出
                isStreaming = true;
                llmResponse = llmChatHandler.chatWithCallback(modelId, messages, context.getStreamingCallback());
            } else {
                // 如果没有回调，使用非流式调用
                log.debug("没有流式回调，使用非流式模式获取LLM结果");
                llmResponse = llmChatHandler.chatNonStreaming(modelId, messages);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.success("llm_generate", "llm_generate", llmResponse);
            result.setDuration(duration);
            result.setMetadata(Map.of(
                "prompt", prompt, 
                "modelId", modelId,
                "historyMessageCount", messages.size() - 2,
                "streaming", isStreaming,
                "textLength", llmResponse.length()
            ));
            
            return result;
            
        } catch (Exception e) {
            log.error("LLM生成失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure("llm_generate", "llm_generate",
                e.getMessage(), "LLM_GENERATE_ERROR");
            result.setDuration(duration);
            return result;
        }
    }
    
}

