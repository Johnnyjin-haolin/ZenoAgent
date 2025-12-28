package com.aiagent.service;

import com.aiagent.service.action.LLMGenerateParams;
import com.aiagent.service.action.RAGRetrieveParams;
import com.aiagent.service.action.ToolCallParams;
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
        
        // 可用工具（包含参数定义，便于大模型理解如何调用）
        List<com.aiagent.vo.McpToolInfo> availableTools = toolSelector.selectTools(goal, 
            context != null ? context.getEnabledMcpGroups() : null);
        if (!availableTools.isEmpty()) {
            prompt.append("\n## 可用工具\n");
            for (com.aiagent.vo.McpToolInfo tool : availableTools) {
                prompt.append("### ").append(tool.getName()).append("\n");
                if (StringUtils.isNotEmpty(tool.getDescription())) {
                    prompt.append("描述: ").append(tool.getDescription()).append("\n");
                }
                // 添加参数定义（JSON Schema格式）
                if (tool.getParameters() != null && !tool.getParameters().isEmpty()) {
                    prompt.append("参数定义: ").append(com.alibaba.fastjson2.JSON.toJSONString(tool.getParameters())).append("\n");
                } else {
                    prompt.append("参数: 无参数\n");
                }
                prompt.append("\n");
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
        prompt.append("请严格按照以下JSON格式返回你的决定：\n\n");
        
        prompt.append("### TOOL_CALL 格式：\n");
        prompt.append("{\n");
        prompt.append("  \"actionType\": \"TOOL_CALL\",\n");
        prompt.append("  \"actionName\": \"工具名称\",\n");
        prompt.append("  \"reasoning\": \"为什么选择这个动作\",\n");
        prompt.append("  \"toolCallParams\": {\n");
        prompt.append("    \"toolName\": \"工具名称（可选，如果与actionName相同可省略）\",\n");
        prompt.append("    \"toolParams\": {\n");
        prompt.append("      \"参数名1\": \"参数值1\",\n");
        prompt.append("      \"参数名2\": \"参数值2\"\n");
        prompt.append("    }\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        
        prompt.append("### RAG_RETRIEVE 格式：\n");
        prompt.append("{\n");
        prompt.append("  \"actionType\": \"RAG_RETRIEVE\",\n");
        prompt.append("  \"actionName\": \"rag_retrieve\",\n");
        prompt.append("  \"reasoning\": \"为什么需要检索知识库\",\n");
        prompt.append("  \"ragRetrieveParams\": {\n");
        prompt.append("    \"query\": \"检索查询文本\",\n");
        prompt.append("    \"knowledgeIds\": [\"知识库ID1\", \"知识库ID2\"]（可选，如果不提供将从上下文获取）,\n");
        prompt.append("    \"maxResults\": 10（可选）,\n");
        prompt.append("    \"similarityThreshold\": 0.8（可选）\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        
        prompt.append("### LLM_GENERATE 格式：\n");
        prompt.append("{\n");
        prompt.append("  \"actionType\": \"LLM_GENERATE\",\n");
        prompt.append("  \"actionName\": \"llm_generate\",\n");
        prompt.append("  \"reasoning\": \"为什么可以直接生成回复\",\n");
        prompt.append("  \"llmGenerateParams\": {\n");
        prompt.append("    \"prompt\": \"生成提示词\",\n");
        prompt.append("    \"systemPrompt\": \"系统提示词（可选）\",\n");
        prompt.append("    \"temperature\": 0.7（可选）,\n");
        prompt.append("    \"maxTokens\": 1000（可选）\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        
        prompt.append("### COMPLETE 格式：\n");
        prompt.append("{\n");
        prompt.append("  \"actionType\": \"COMPLETE\",\n");
        prompt.append("  \"actionName\": \"complete\",\n");
        prompt.append("  \"reasoning\": \"任务已完成的原因\"\n");
        prompt.append("}\n\n");
        
        prompt.append("注意：请确保返回的JSON格式正确，只包含对应动作类型所需的参数字段。");
        
        return prompt.toString();
    }
    
    /**
     * 调用LLM进行思考
     */
    private String callLLMForThinking(String prompt, AgentContext context) {
        try {
            // 准备消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一个智能Agent的思考模块，需要分析情况并做出决策。请严格按照JSON格式返回结果。"));
            messages.add(new UserMessage(prompt));
            
            // 获取模型ID（从上下文或使用默认值）
            String modelId = context != null ? context.getModelId() : null;
            if (StringUtils.isEmpty(modelId)) {
                modelId = "gpt-4o-mini";
            }
            
            // 调用非流式LLM获取完整响应
            String response = llmChatHandler.chatNonStreaming(modelId, messages);
            
            log.debug("LLM思考响应: {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("LLM思考调用失败，使用默认逻辑", e);
            // 失败时降级为默认逻辑
            return generateDefaultThinking(prompt, context);
        }
    }
    
    /**
     * 生成默认思考结果（降级方案）
     */
    private String generateDefaultThinking(String goal, AgentContext context) {
        // 简单逻辑：如果有工具可用，尝试工具调用；否则生成回复
        List<com.aiagent.vo.McpToolInfo> availableTools = toolSelector.selectTools(goal, 
            context != null ? context.getEnabledMcpGroups() : null);
        
        if (!availableTools.isEmpty() && (goal.contains("执行") || goal.contains("调用"))) {
            com.aiagent.vo.McpToolInfo firstTool = availableTools.get(0);
            String toolName = firstTool.getName();
            Map<String, Object> defaultResult = new HashMap<>();
            defaultResult.put("actionType", "TOOL_CALL");
            defaultResult.put("actionName", toolName);
            defaultResult.put("reasoning", "检测到需要执行操作，选择调用工具");
            Map<String, Object> toolCallParams = new HashMap<>();
            toolCallParams.put("toolName", toolName);
            toolCallParams.put("toolParams", new HashMap<>());
            defaultResult.put("toolCallParams", toolCallParams);
            return JSON.toJSONString(defaultResult);
        } else {
            Map<String, Object> defaultResult = new HashMap<>();
            defaultResult.put("actionType", "LLM_GENERATE");
            defaultResult.put("actionName", "llm_generate");
            defaultResult.put("reasoning", "可以直接生成回复");
            Map<String, Object> llmParams = new HashMap<>();
            llmParams.put("prompt", goal);
            defaultResult.put("llmGenerateParams", llmParams);
            return JSON.toJSONString(defaultResult);
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
            
            // 根据动作类型解析对应的参数
            AgentAction action = null;
            switch (type) {
                case TOOL_CALL:
                    action = parseToolCallAction(json, actionName, reasoning, context);
                    break;
                case RAG_RETRIEVE:
                    action = parseRAGRetrieveAction(json, actionName, reasoning, context);
                    break;
                case LLM_GENERATE:
                    action = parseLLMGenerateAction(json, actionName, reasoning, context);
                    break;
                case COMPLETE:
                    action = AgentAction.complete(reasoning != null ? reasoning : "任务已完成");
                    break;
                default:
                    log.warn("不支持的动作类型: {}", type);
                    return null;
            }
            
            if (action != null && StringUtils.isEmpty(action.getName())) {
                action.setName(actionName != null ? actionName : type.name().toLowerCase());
            }
            
            return action;
                
        } catch (Exception e) {
            log.error("解析思考结果失败: {}", thinkingResult, e);
            return null;
        }
    }
    
    /**
     * 解析工具调用动作
     */
    private AgentAction parseToolCallAction(JSONObject json, String actionName, String reasoning, AgentContext context) {
        JSONObject toolCallParamsJson = json.getJSONObject("toolCallParams");
        if (toolCallParamsJson == null) {
            log.warn("TOOL_CALL动作缺少toolCallParams");
            return null;
        }
        
        // 获取工具名称（优先使用toolCallParams中的，否则使用actionName）
        String toolName = toolCallParamsJson.getString("toolName");
        if (StringUtils.isEmpty(toolName)) {
            toolName = actionName;
        }
        if (StringUtils.isEmpty(toolName)) {
            log.warn("TOOL_CALL动作缺少工具名称");
            return null;
        }
        
        // 获取工具参数
        @SuppressWarnings("unchecked")
        Map<String, Object> toolParams = (Map<String, Object>) toolCallParamsJson.getObject("toolParams", Map.class);
        if (toolParams == null) {
            toolParams = new HashMap<>();
        }
        
        ToolCallParams toolCallParams = ToolCallParams.builder()
            .toolName(toolName)
            .toolParams(toolParams)
            .build();
        
        return AgentAction.toolCall(toolName, toolCallParams, reasoning);
    }
    
    /**
     * 解析RAG检索动作
     */
    private AgentAction parseRAGRetrieveAction(JSONObject json, String actionName, String reasoning, AgentContext context) {
        JSONObject ragParamsJson = json.getJSONObject("ragRetrieveParams");
        if (ragParamsJson == null) {
            log.warn("RAG_RETRIEVE动作缺少ragRetrieveParams");
            return null;
        }
        
        String query = ragParamsJson.getString("query");
        if (StringUtils.isEmpty(query)) {
            log.warn("RAG_RETRIEVE动作缺少query");
            return null;
        }
        
        // 获取knowledgeIds，如果未提供则从上下文获取
        List<String> knowledgeIds = new ArrayList<>();
        if (ragParamsJson.containsKey("knowledgeIds")) {
            knowledgeIds = ragParamsJson.getList("knowledgeIds", String.class);
        }
        
        // 如果knowledgeIds为空，从上下文获取
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            if (context != null && context.getKnowledgeIds() != null) {
                knowledgeIds = context.getKnowledgeIds();
                log.debug("从上下文获取knowledgeIds: {}", knowledgeIds);
            }
        }
        
        RAGRetrieveParams ragParams = RAGRetrieveParams.builder()
            .query(query)
            .knowledgeIds(knowledgeIds != null ? knowledgeIds : new ArrayList<>())
            .maxResults(ragParamsJson.getInteger("maxResults"))
            .similarityThreshold(ragParamsJson.getDouble("similarityThreshold"))
            .build();
        
        return AgentAction.ragRetrieve(ragParams, reasoning);
    }
    
    /**
     * 解析LLM生成动作
     */
    private AgentAction parseLLMGenerateAction(JSONObject json, String actionName, String reasoning, AgentContext context) {
        JSONObject llmParamsJson = json.getJSONObject("llmGenerateParams");
        if (llmParamsJson == null) {
            log.warn("LLM_GENERATE动作缺少llmGenerateParams");
            return null;
        }
        
        String prompt = llmParamsJson.getString("prompt");
        if (StringUtils.isEmpty(prompt)) {
            log.warn("LLM_GENERATE动作缺少prompt");
            return null;
        }
        
        LLMGenerateParams llmParams = LLMGenerateParams.builder()
            .prompt(prompt)
            .systemPrompt(llmParamsJson.getString("systemPrompt"))
            .temperature(llmParamsJson.getDouble("temperature"))
            .maxTokens(llmParamsJson.getInteger("maxTokens"))
            .build();
        
        return AgentAction.llmGenerate(llmParams, reasoning);
    }
}

