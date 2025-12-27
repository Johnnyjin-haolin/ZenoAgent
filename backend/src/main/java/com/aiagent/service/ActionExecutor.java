package com.aiagent.service;

import com.aiagent.util.StringUtils;
import com.aiagent.vo.AgentContext;
import com.aiagent.vo.AgentKnowledgeResult;
import com.aiagent.vo.McpToolInfo;
import dev.langchain4j.data.message.ChatMessage;
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
            String toolName = action.getName();
            Map<String, Object> paramsObj = action.getParams();
            
            // 转换参数为Map
            Map<String, Object> params;
            params = Objects.requireNonNullElseGet(paramsObj, HashMap::new);
            
            // 查找工具信息
            McpToolInfo toolInfo = toolSelector.getToolByName(toolName);
            if (toolInfo == null) {
                throw new IllegalArgumentException("工具未找到: " + toolName);
            }
            
            log.info("执行工具调用: name={}, params={}", toolName, params);
            
            // 使用McpToolExecutor执行工具
            Object toolResult = mcpToolExecutor.execute(toolInfo, params);
            
            // 将结果转换为字符串（用于记录和返回）
            String resultStr = parseToolResult(toolResult);
            
            // 记录工具调用历史
            memorySystem.recordToolCall(context, toolName, params, toolResult);
            
            long duration = System.currentTimeMillis() - startTime;
            ActionResult actionResult = ActionResult.success("tool_call", toolName, toolResult);
            actionResult.setDuration(duration);
            actionResult.setMetadata(java.util.Map.of(
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
        if (toolResult instanceof java.util.Map || toolResult instanceof java.util.List) {
            try {
                return com.alibaba.fastjson2.JSON.toJSONString(toolResult);
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
            String query = (String) action.getParams().getOrDefault("query", "");
            if (StringUtils.isEmpty(query)) {
                query = action.getReasoning();
            }
            
            // 获取知识库ID列表
            List<String> knowledgeIds = (List<String>) action.getParams().getOrDefault("knowledgeIds",
                new ArrayList<>());
            
            // 执行RAG检索
            AgentKnowledgeResult knowledgeResult = ragEnhancer.retrieve(query, knowledgeIds);
            
            // 记录RAG检索历史
            memorySystem.recordRAGRetrieve(context, query, knowledgeIds, 
                knowledgeResult != null ? knowledgeResult.getTotalCount() : 0);
            
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.success("rag_retrieve", "rag_retrieve", knowledgeResult);
            result.setDuration(duration);
            result.setMetadata(java.util.Map.of("query", query, "count", 
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
     */
    private ActionResult executeLLMGenerate(AgentAction action, AgentContext context, long startTime) {
        try {
            String prompt = (String) action.getParams().getOrDefault("prompt", "");
            if (StringUtils.isEmpty(prompt)) {
                prompt = action.getReasoning();
            }
            
            // 准备消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new UserMessage(prompt));
            
            // 调用LLM（简化实现：这里需要非流式调用）
            // TODO: 实现非流式LLM调用
            log.warn("LLM生成使用简化实现");
            
            // 临时返回成功结果
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.success("llm_generate", "llm_generate", 
                "LLM生成功能待完善");
            result.setDuration(duration);
            
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

