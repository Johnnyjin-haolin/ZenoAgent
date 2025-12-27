package com.aiagent.service;

import com.aiagent.constant.AgentConstants;
import com.aiagent.util.StringUtils;
import com.aiagent.vo.AgentContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 思考引擎
 * 负责分析当前情况，决定下一步动作
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ThinkingEngine {
    
    @Autowired
    private SimpleLLMChatHandler llmChatHandler;
    
    @Autowired
    private IntelligentToolSelector toolSelector;
    
    @Autowired
    private RAGEnhancer ragEnhancer;
    
    /**
     * 思考：分析目标、上下文和历史结果，决定下一步动作
     */
    public AgentAction think(String goal, AgentContext context, ActionResult lastResult) {
        log.info("开始思考，目标: {}", goal);
        
        // 构建思考提示词
        String thinkingPrompt = buildThinkingPrompt(goal, context, lastResult);
        
        // 调用LLM进行思考
        String thinkingResult = callLLMForThinking(thinkingPrompt, context);
        
        // 解析思考结果，生成动作
        AgentAction action = parseThinkingResult(thinkingResult, goal, context);
        
        log.info("思考完成，决定执行动作: {}", action != null ? action.getName() : "null");
        return action;
    }
    
    /**
     * 构建思考提示词
     */
    private String buildThinkingPrompt(String goal, AgentContext context, ActionResult lastResult) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个智能Agent，需要分析当前情况并决定下一步动作。\n\n");
        
        // 目标
        prompt.append("## 当前目标\n");
        prompt.append(goal).append("\n\n");
        
        // 上下文信息
        if (context != null) {
            prompt.append("## 上下文信息\n");
            if (context.getMessages() != null && !context.getMessages().isEmpty()) {
                prompt.append("对话历史:\n");
                List<ChatMessage> recentMessages = context.getMessages();
                int start = Math.max(0, recentMessages.size() - 5);
                for (int i = start; i < recentMessages.size(); i++) {
                    ChatMessage msg = recentMessages.get(i);
                    if (msg instanceof UserMessage) {
                        prompt.append("用户: ").append(((UserMessage) msg).singleText()).append("\n");
                    } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                        dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) msg;
                        prompt.append("助手: ").append(aiMsg.text()).append("\n");
                    }
                }
            }
            
            if (context.getToolCallHistory() != null && !context.getToolCallHistory().isEmpty()) {
                prompt.append("\n工具调用历史:\n");
                int historySize = context.getToolCallHistory().size();
                int start = Math.max(0, historySize - 3);
                for (int i = start; i < historySize; i++) {
                    Map<String, Object> call = context.getToolCallHistory().get(i);
                    prompt.append("- ").append(call.get("toolName")).append("\n");
                }
            }
        }
        
        // 上次执行结果
        if (lastResult != null) {
            prompt.append("\n## 上次执行结果\n");
            if (lastResult.isSuccess()) {
                prompt.append("成功: ").append(lastResult.getData()).append("\n");
            } else {
                prompt.append("失败: ").append(lastResult.getError()).append("\n");
            }
        }
        
        // 可用工具
        List<String> availableTools = toolSelector.getAvailableToolNames();
        if (!availableTools.isEmpty()) {
            prompt.append("\n## 可用工具\n");
            for (String tool : availableTools) {
                prompt.append("- ").append(tool).append("\n");
            }
        }
        
        // 思考指令
        prompt.append("\n## 你的任务\n");
        prompt.append("分析当前情况，决定下一步应该做什么。\n\n");
        prompt.append("可选动作类型：\n");
        prompt.append("1. TOOL_CALL - 调用工具（如果需要执行操作）\n");
        prompt.append("2. RAG_RETRIEVE - 检索知识库（如果需要查询信息）\n");
        prompt.append("3. LLM_GENERATE - 生成回复（如果可以直接回答）\n");
        prompt.append("4. COMPLETE - 完成任务（如果目标已达成）\n\n");
        prompt.append("请以JSON格式返回你的决定：\n");
        prompt.append("{\n");
        prompt.append("  \"actionType\": \"动作类型\",\n");
        prompt.append("  \"actionName\": \"动作名称\",\n");
        prompt.append("  \"reasoning\": \"为什么选择这个动作\",\n");
        prompt.append("  \"params\": {动作参数}\n");
        prompt.append("}\n\n");
        prompt.append("如果目标已达成，返回 {\"actionType\": \"COMPLETE\", \"reasoning\": \"完成原因\"}");
        
        return prompt.toString();
    }
    
    /**
     * 调用LLM进行思考
     */
    private String callLLMForThinking(String prompt, AgentContext context) {
        try {
            // TODO: 实现非流式LLM调用方法
            // 临时实现：返回一个默认动作
            log.warn("LLM思考调用未完全实现，使用默认逻辑");
            return generateDefaultThinking(prompt, context);
            
        } catch (Exception e) {
            log.error("LLM思考调用失败", e);
            return generateDefaultThinking(prompt, context);
        }
    }
    
    /**
     * 生成默认思考结果（临时实现）
     */
    private String generateDefaultThinking(String goal, AgentContext context) {
        // 简单逻辑：如果有工具可用，尝试工具调用；否则生成回复
        List<String> availableTools = toolSelector.getAvailableToolNames();
        
        if (!availableTools.isEmpty() && (goal.contains("执行") || goal.contains("调用"))) {
            return JSON.toJSONString(java.util.Map.of(
                "actionType", "TOOL_CALL",
                "actionName", availableTools.get(0),
                "reasoning", "检测到需要执行操作，选择调用工具",
                "params", new HashMap<>()
            ));
        } else {
            return JSON.toJSONString(java.util.Map.of(
                "actionType", "LLM_GENERATE",
                "actionName", "llm_generate",
                "reasoning", "可以直接生成回复",
                "params", java.util.Map.of("prompt", goal)
            ));
        }
    }
    
    /**
     * 解析思考结果，生成动作
     */
    private AgentAction parseThinkingResult(String thinkingResult, String goal, AgentContext context) {
        try {
            JSONObject json = JSON.parseObject(thinkingResult);
            String actionType = json.getString("actionType");
            String actionName = json.getString("actionName");
            String reasoning = json.getString("reasoning");
            Map<String, Object> params = json.getObject("params", Map.class);
            
            if (StringUtils.isEmpty(actionType)) {
                log.warn("思考结果中缺少actionType");
                return null;
            }
            
            AgentAction.ActionType type;
            try {
                type = AgentAction.ActionType.valueOf(actionType);
            } catch (IllegalArgumentException e) {
                log.warn("无效的动作类型: {}", actionType);
                return null;
            }
            
            return AgentAction.builder()
                .type(type)
                .name(actionName != null ? actionName : type.name().toLowerCase())
                .reasoning(reasoning)
                .params(params != null ? params : new java.util.HashMap<>())
                .build();
                
        } catch (Exception e) {
            log.error("解析思考结果失败", e);
            return null;
        }
    }
}

