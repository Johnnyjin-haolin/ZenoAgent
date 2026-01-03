package com.aiagent.service;

import com.aiagent.constant.AgentConstants;
import com.aiagent.service.action.LLMGenerateParams;
import com.aiagent.service.action.RAGRetrieveParams;
import com.aiagent.service.action.ToolCallParams;
import com.aiagent.util.StringUtils;
import com.aiagent.vo.AgentContext;
import com.aiagent.vo.AgentEventData;
import com.aiagent.vo.McpToolInfo;
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
     * 决策框架提示词
     */
    private static final String DECISION_FRAMEWORK_PROMPT = "## 决策框架\n\n" +
            "请按照以下步骤进行**结构化思考**：\n\n" +
            "### 步骤1：理解当前状态\n" +
            "- 用户的当前需求是什么？\n" +
            "- 这是新的需求，还是之前任务的延续？\n" +
            "- 对话历史中有哪些关键信息？\n\n" +
            "### 步骤2：评估已有信息\n" +
            "- 我已经知道什么？（检查对话历史、工具执行结果）\n" +
            "- 还需要什么信息才能回答？\n" +
            "- 上次工具调用的结果是否已经足够回答用户的问题？\n\n" +
            "### 步骤3：选择动作\n" +
            "根据评估结果，选择最合适的动作类型：\n\n" +
            "**何时选择 LLM_GENERATE？**\n" +
            "✅ 用户询问系统能力、功能介绍等元信息（如\"你能做什么\"、\"你是谁\"）\n" +
            "✅ 打招呼、闲聊等社交性对话（如\"你好\"、\"谢谢\"）\n" +
            "✅ 已有足够信息可以直接回答用户问题\n" +
            "✅ 需要解释、总结、分析已有数据\n\n" +
            "**何时选择 TOOL_CALL？**\n" +
            "✅ 需要查询外部系统的实时数据\n" +
            "✅ 需要执行具体操作（创建、删除、修改等）\n" +
            "✅ 用户明确要求执行某个任务\n" +
            "❌ 不要：如果上次刚调用过同一工具且已有有效结果\n\n" +
            "**何时选择 RAG_RETRIEVE？**\n" +
            "✅ 需要查询知识库中的文档、资料\n" +
            "✅ 用户询问特定领域知识或历史记录\n\n" +
            "**何时选择 COMPLETE？**\n" +
            "✅ 用户的需求已经完全满足\n" +
            "✅ 已经给出了完整的回答\n\n" +
            "### 步骤4：自我检查\n" +
            "- 这个决策是否合理？\n" +
            "- 是否会导致重复调用？\n" +
            "- 是否真的需要外部信息？\n\n" +
            "## 关键约束\n\n" +
            "🚫 **禁止行为**:\n" +
            "1. 不要重复调用刚执行过的工具（除非有新的参数或明确需要）\n" +
            "2. 不要为了\"看起来智能\"而调用工具\n" +
            "3. 不要在已有答案时继续查询\n\n" +
            "✅ **推荐行为**:\n" +
            "1. 优先使用已有信息回答\n" +
            "2. 只在确实需要时才调用工具\n" +
            "3. 对简单问题直接回答\n\n" +
            "## 示例参考\n\n" +
            "【示例1：系统能力询问】\n" +
            "用户输入: \"你好呀，你有什么功能\"\n" +
            "分析: 这是询问系统能力的元信息查询，不需要调用任何工具\n" +
            "决策: LLM_GENERATE\n" +
            "原因: 直接介绍系统功能即可\n\n" +
            "【示例2：明确的任务需求】\n" +
            "用户输入: \"帮我搜索华东区域的ECS实例\"\n" +
            "分析: 这是明确的查询需求，需要调用资源搜索工具\n" +
            "决策: TOOL_CALL\n" +
            "原因: 需要查询外部系统的实时数据\n\n" +
            "【示例3：已有信息场景】\n" +
            "用户输入: \"有哪些资源？\"\n" +
            "上次工具调用: SearchResources，已返回资源列表\n" +
            "分析: 上次调用已经获取了资源列表，无需重复调用\n" +
            "决策: LLM_GENERATE\n" +
            "原因: 直接总结并展示已有的资源列表\n\n" +
            "【示例4：需要更多细节】\n" +
            "用户输入: \"第一个资源的详细配置是什么？\"\n" +
            "上次结果: 只有资源列表摘要，没有详细配置\n" +
            "分析: 需要查询资源详情\n" +
            "决策: TOOL_CALL\n" +
            "原因: 需要新的数据（详细配置）\n\n";
    
    /**
     * 输出格式提示词
     */
    private static final String OUTPUT_FORMAT_PROMPT = "## 输出格式\n\n" +
            "请严格按照以下JSON格式返回你的决定：\n\n" +
            "**TOOL_CALL格式**:\n" +
            "```json\n" +
            "{\n" +
            "  \"actionType\": \"TOOL_CALL\",\n" +
            "  \"actionName\": \"工具名称\",\n" +
            "  \"reasoning\": \"为什么选择这个动作\",\n" +
            "  \"toolCallParams\": {\n" +
            "    \"toolName\": \"工具名称\",\n" +
            "    \"toolParams\": {\"参数名\": \"参数值\"}\n" +
            "  }\n" +
            "}\n" +
            "```\n\n" +
            "**RAG_RETRIEVE格式**:\n" +
            "```json\n" +
            "{\n" +
            "  \"actionType\": \"RAG_RETRIEVE\",\n" +
            "  \"actionName\": \"rag_retrieve\",\n" +
            "  \"reasoning\": \"为什么需要检索知识库\",\n" +
            "  \"ragRetrieveParams\": {\n" +
            "    \"query\": \"检索查询文本\",\n" +
            "    \"knowledgeIds\": [],\n" +
            "    \"maxResults\": 10\n" +
            "  }\n" +
            "}\n" +
            "```\n\n" +
            "**LLM_GENERATE格式**:\n" +
            "```json\n" +
            "{\n" +
            "  \"actionType\": \"LLM_GENERATE\",\n" +
            "  \"actionName\": \"llm_generate\",\n" +
            "  \"reasoning\": \"为什么可以直接生成回复\",\n" +
            "  \"llmGenerateParams\": {\n" +
            "    \"prompt\": \"用户说'XXX'，请友好地回复并...\"\n" +
            "  }\n" +
            "}\n" +
            "```\n\n" +
            "**COMPLETE格式**:\n" +
            "```json\n" +
            "{\n" +
            "  \"actionType\": \"COMPLETE\",\n" +
            "  \"actionName\": \"complete\",\n" +
            "  \"reasoning\": \"任务已完成的原因\"\n" +
            "}\n" +
            "```\n\n" +
            "⚠️ **重要**: 只返回JSON对象，不要包含其他文字说明或Markdown代码块标记！\n";
    
    /**
     * 思考：分析目标、上下文和历史结果，决定下一步动作
     */
    public AgentAction think(String goal, AgentContext context, ActionResult lastResult) {
        log.info("开始思考，目标: {}", goal);
        
        // 发送思考进度事件
        sendProgressEvent(context, AgentConstants.EVENT_AGENT_THINKING, "正在分析任务和用户意图...");
        
        // 构建思考提示词
        String thinkingPrompt = buildThinkingPrompt(goal, context, lastResult);
        
        // 调用LLM进行思考
        String thinkingResult = callLLMForThinking(thinkingPrompt, context);
        
        // 解析思考结果，生成动作
        AgentAction action = parseThinkingResult(thinkingResult, goal, context);
        
        // 循环检测：如果检测到异常循环，强制使用LLM_GENERATE
        if (action != null && detectLoopAnomaly(context, action, lastResult)) {
            log.warn("检测到循环调用异常，强制切换为LLM_GENERATE");
            String prompt = "用户问: " + goal + "\n\n";
            if (lastResult != null && lastResult.isSuccess()) {
                prompt += "我已经获取到以下信息: " + lastResult.getData() + "\n\n";
            }
            prompt += "请根据已有信息，直接回答用户的问题。如果信息不足，也要友好地告知用户。";
            
            action = AgentAction.llmGenerate(
                com.aiagent.service.action.LLMGenerateParams.builder()
                    .prompt(prompt)
                    .build(),
                "检测到重复调用，使用已有信息直接回答"
            );
        }
        
        log.info("思考完成，决定执行动作: {}", action != null ? action.getName() : "null");
        return action;
    }
    
    /**
     * 构建思考提示词（使用决策框架）
     */
    private String buildThinkingPrompt(String goal, AgentContext context, ActionResult lastResult) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个智能Agent的思考模块，遵循ReAct（Reasoning + Acting）框架。\n\n");
        
        // ========== 第一部分：当前状态 ==========
        prompt.append("## 当前状态\n\n");
        prompt.append("**用户需求**: ").append(goal).append("\n\n");
        
        // 对话历史
        if (context != null && context.getMessages() != null && !context.getMessages().isEmpty()) {
            prompt.append("**对话历史**（最近5轮）:\n");
            List<ChatMessage> recentMessages = context.getMessages();
            int start = Math.max(0, recentMessages.size() - 5);
            for (int i = start; i < recentMessages.size(); i++) {
                ChatMessage msg = recentMessages.get(i);
                if (msg instanceof UserMessage) {
                    prompt.append("- 用户: ").append(((UserMessage) msg).singleText()).append("\n");
                } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                    dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) msg;
                    prompt.append("- 助手: ").append(aiMsg.text()).append("\n");
                }
            }
            prompt.append("\n");
        }
        
        // 工具调用历史
        if (context != null && context.getToolCallHistory() != null && !context.getToolCallHistory().isEmpty()) {
            prompt.append("**工具调用历史**（最近3次）:\n");
            int historySize = context.getToolCallHistory().size();
            int start = Math.max(0, historySize - 3);
            for (int i = start; i < historySize; i++) {
                Map<String, Object> call = context.getToolCallHistory().get(i);
                prompt.append("- ").append(call.get("toolName"));
                if (call.containsKey("params")) {
                    prompt.append(" (参数: ").append(call.get("params")).append(")");
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        // 上次执行结果
        if (lastResult != null) {
            prompt.append("**上次执行结果**:\n");
            if (lastResult.isSuccess()) {
                String resultData = lastResult.getData() != null ? lastResult.getData().toString() : "";
                // 限制结果长度，避免提示词过长
                if (resultData.length() > 1000) {
                    resultData = resultData.substring(0, 1000) + "... (结果过长，已截断)";
                }
                prompt.append("✅ 成功: ").append(resultData).append("\n\n");
            } else {
                prompt.append("❌ 失败: ").append(lastResult.getError()).append("\n\n");
            }
        }
        
        // ========== 第二部分：决策框架 ==========
        prompt.append(DECISION_FRAMEWORK_PROMPT);
        
        // ========== 第五部分：可用工具 ==========
        List<McpToolInfo> availableTools = toolSelector.selectTools(goal,
            context != null ? context.getEnabledMcpGroups() : null);
        if (!availableTools.isEmpty()) {
            prompt.append("## 可用工具\n\n");
            for (McpToolInfo tool : availableTools) {
                prompt.append("**").append(tool.getName()).append("**\n");
                if (StringUtils.isNotEmpty(tool.getDescription())) {
                    // 限制描述长度
                    String desc = tool.getDescription();
                    if (desc.length() > 500) {
                        desc = desc.substring(0, 500) + "...";
                    }
                    prompt.append("- 描述: ").append(desc).append("\n");
                }
                if (tool.getParameters() != null && !tool.getParameters().isEmpty()) {
                    prompt.append("- 参数: ").append(com.alibaba.fastjson2.JSON.toJSONString(tool.getParameters())).append("\n");
                }
                prompt.append("\n");
            }
        }
        
        // ========== 第六部分：输出格式 ==========
        prompt.append(OUTPUT_FORMAT_PROMPT);
        
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
     * 使用规则引擎快速判断简单场景
     */
    private String generateDefaultThinking(String goal, AgentContext context) {
        log.info("LLM思考失败，使用降级逻辑");
        
        String lowerGoal = goal.toLowerCase();
        
        // 1. 优先判断：元信息查询（询问系统功能、能力等）
        if (isMetaQuery(lowerGoal)) {
            log.info("识别为元信息查询，返回LLM_GENERATE");
            return createLLMGenerateAction(
                "用户询问系统功能或能力，应该直接介绍，不需要调用工具",
                "用户问: " + goal + "\n请友好地介绍你作为智能Agent助手的功能和能力。"
            );
        }
        
        // 2. 判断：问候和社交性对话
        if (isGreeting(lowerGoal)) {
            log.info("识别为问候，返回LLM_GENERATE");
            return createLLMGenerateAction(
                "用户在打招呼或进行社交性对话，友好回应即可",
                "用户说: " + goal + "\n请友好地回应用户的问候。"
            );
        }
        
        // 3. 判断：是否有明确的操作意图
        List<com.aiagent.vo.McpToolInfo> availableTools = toolSelector.selectTools(goal, 
            context != null ? context.getEnabledMcpGroups() : null);
        
        if (!availableTools.isEmpty() && hasActionIntent(lowerGoal)) {
            log.info("识别为操作意图，返回TOOL_CALL");
            com.aiagent.vo.McpToolInfo firstTool = availableTools.get(0);
            return createToolCallAction(
                firstTool.getName(),
                "检测到明确的操作需求，调用相应工具"
            );
        }
        
        // 4. 默认：生成回复
        log.info("无法明确分类，默认返回LLM_GENERATE");
        return createLLMGenerateAction(
            "可以直接回答的问题",
            "用户问: " + goal + "\n请根据你的知识直接回答用户的问题。"
        );
    }
    
    /**
     * 判断是否为元信息查询（询问系统功能、能力等）
     */
    private boolean isMetaQuery(String lowerGoal) {
        String[] metaKeywords = {
            "什么功能", "能做什么", "可以做什么", "有什么用", "怎么用",
            "你是谁", "你叫什么", "介绍一下", "是什么", "干什么的",
            "what can you do", "who are you", "what are you", "introduce yourself"
        };
        
        for (String keyword : metaKeywords) {
            if (lowerGoal.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断是否为问候或社交性对话
     */
    private boolean isGreeting(String lowerGoal) {
        String[] greetingKeywords = {
            "你好", "您好", "hi", "hello", "hey", "早上好", "晚上好",
            "谢谢", "感谢", "thank", "再见", "拜拜", "bye"
        };
        
        for (String keyword : greetingKeywords) {
            if (lowerGoal.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断是否有明确的操作意图
     */
    private boolean hasActionIntent(String lowerGoal) {
        String[] actionKeywords = {
            "帮我", "帮忙", "请", "搜索", "查询", "查找", "找",
            "创建", "新建", "添加", "删除", "移除", "修改", "更新",
            "执行", "运行", "启动", "停止", "列出", "显示",
            "search", "query", "find", "create", "delete", "update", "list"
        };
        
        for (String keyword : actionKeywords) {
            if (lowerGoal.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 创建LLM_GENERATE动作的JSON字符串
     */
    private String createLLMGenerateAction(String reasoning, String prompt) {
        Map<String, Object> result = new HashMap<>();
        result.put("actionType", "LLM_GENERATE");
        result.put("actionName", "llm_generate");
        result.put("reasoning", reasoning);
        
        Map<String, Object> llmParams = new HashMap<>();
        llmParams.put("prompt", prompt);
        result.put("llmGenerateParams", llmParams);
        
        return JSON.toJSONString(result);
    }
    
    /**
     * 创建TOOL_CALL动作的JSON字符串
     */
    private String createToolCallAction(String toolName, String reasoning) {
        Map<String, Object> result = new HashMap<>();
        result.put("actionType", "TOOL_CALL");
        result.put("actionName", toolName);
        result.put("reasoning", reasoning);
        
        Map<String, Object> toolCallParams = new HashMap<>();
        toolCallParams.put("toolName", toolName);
        toolCallParams.put("toolParams", new HashMap<>());
        result.put("toolCallParams", toolCallParams);
        
        return JSON.toJSONString(result);
    }
    
    /**
     * 解析思考结果，生成动作
     */
    private AgentAction parseThinkingResult(String thinkingResult, String goal, AgentContext context) {
        try {
            // 清理返回文本，移除可能的Markdown代码块包装和其他文本
            String cleanedResult = cleanJsonResponse(thinkingResult);
            log.debug("清理后的思考结果: {}", cleanedResult);
            
            JSONObject json = JSON.parseObject(cleanedResult);
            String actionType = json.getString("actionType");
            String actionName = json.getString("actionName");
            String reasoning = json.getString("reasoning");
            
            if (StringUtils.isEmpty(actionType)) {
                log.warn("思考结果中缺少actionType，原始结果: {}", thinkingResult);
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
            log.error("解析思考结果失败，原始结果: {}", thinkingResult, e);
            // 尝试提取JSON（可能被包装在markdown代码块中）
            try {
                String extractedJson = extractJsonFromText(thinkingResult);
                if (extractedJson != null && !extractedJson.equals(thinkingResult)) {
                    log.info("尝试从文本中提取JSON并重新解析");
                    return parseThinkingResult(extractedJson, goal, context);
                }
            } catch (Exception e2) {
                log.error("提取JSON也失败", e2);
            }
            return null;
        }
    }
    
    /**
     * 清理JSON响应文本
     * 移除Markdown代码块标记、前后空白等
     */
    private String cleanJsonResponse(String response) {
        if (StringUtils.isEmpty(response)) {
            return response;
        }
        
        String cleaned = response.trim();
        
        // 移除Markdown代码块标记（```json ... ``` 或 ``` ... ```）
        if (cleaned.startsWith("```")) {
            int startIdx = cleaned.indexOf('\n');
            if (startIdx > 0) {
                cleaned = cleaned.substring(startIdx + 1);
            }
            int endIdx = cleaned.lastIndexOf("```");
            if (endIdx > 0) {
                cleaned = cleaned.substring(0, endIdx);
            }
        }
        
        // 移除前后空白
        cleaned = cleaned.trim();
        
        // 如果文本中包含JSON对象（以{开头，以}结尾），提取它
        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            cleaned = cleaned.substring(jsonStart, jsonEnd + 1);
        }
        
        return cleaned;
    }
    
    /**
     * 从文本中提取JSON对象
     */
    private String extractJsonFromText(String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        
        // 尝试提取第一个完整的JSON对象
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        
        int braceCount = 0;
        int end = start;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    end = i;
                    break;
                }
            }
        }
        
        if (braceCount == 0 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return null;
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
    
    /**
     * 检测循环异常
     * 如果连续调用同一工具且参数相同或结果类似，认为是异常循环
     */
    private boolean detectLoopAnomaly(AgentContext context, AgentAction proposedAction, ActionResult lastResult) {
        if (context == null || context.getToolCallHistory() == null || context.getToolCallHistory().isEmpty()) {
            return false;
        }
        
        // 只检测TOOL_CALL类型
        if (proposedAction.getType() != AgentAction.ActionType.TOOL_CALL) {
            return false;
        }
        
        List<Map<String, Object>> history = context.getToolCallHistory();
        
        // 至少需要有一次历史调用
        if (history.isEmpty()) {
            return false;
        }
        
        String proposedToolName = proposedAction.getName();
        String lastToolName = (String) history.get(history.size() - 1).get("toolName");
        
        // 检查：是否连续调用同一个工具
        if (proposedToolName.equals(lastToolName)) {
            log.warn("检测到重复调用同一工具: {}", proposedToolName);
            
            // 进一步检查：如果历史中连续2次都是同一工具，则认为是循环
            if (history.size() >= 2) {
                String secondLastToolName = (String) history.get(history.size() - 2).get("toolName");
                if (proposedToolName.equals(secondLastToolName)) {
                    log.error("检测到连续3次调用同一工具 {}, 判定为异常循环", proposedToolName);
                    return true;
                }
            }
            
            // 如果上次调用成功且有结果，也认为不应该重复调用
            if (lastResult != null && lastResult.isSuccess() && lastResult.getData() != null) {
                log.warn("上次工具调用已成功返回结果，不应重复调用");
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 发送进度事件到前端
     */
    private void sendProgressEvent(AgentContext context, String event, String message) {
        if (context != null && context.getEventPublisher() != null) {
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(event)
                    .message(message)
                    .build()
            );
        }
    }
}

