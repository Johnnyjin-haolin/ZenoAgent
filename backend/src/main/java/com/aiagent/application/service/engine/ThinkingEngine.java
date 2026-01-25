package com.aiagent.application.service.engine;

import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.action.AgentAction;
import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.application.service.action.DirectResponseParams;
import com.aiagent.application.service.action.LLMGenerateParams;
import com.aiagent.application.service.action.RAGRetrieveParams;
import com.aiagent.application.service.action.ToolCallParams;
import com.aiagent.shared.util.StringUtils;
import com.aiagent.application.model.AgentContext;
import com.aiagent.api.dto.AgentEventData;
import com.aiagent.api.dto.McpToolInfo;
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
    
    
    
    /**
     * 决策框架提示词
     */
    private static final String DECISION_FRAMEWORK_PROMPT = "## 决策要求\n\n" +
            "1. 先判断已有信息是否足够回答用户问题\n" +
            "2. 仅在需要实时/外部数据时才选 TOOL_CALL\n" +
            "3. 需要知识库资料时选 RAG_RETRIEVE\n" +
            "4. 内容可直接回复时选 DIRECT_RESPONSE 或 LLM_GENERATE\n" +
            "5. 避免重复调用同一工具\n\n";
    
    /**
     * 输出格式提示词
     */
    private static final String OUTPUT_FORMAT_PROMPT = "## 输出格式\n\n" +
            "只返回JSON对象，不要包含其他文字。\n" +
            "actionType 只能是 TOOL_CALL / RAG_RETRIEVE / LLM_GENERATE / DIRECT_RESPONSE / COMPLETE。\n\n" +
            "单个动作示例：\n" +
            "{\"actionType\":\"TOOL_CALL\",\"actionName\":\"工具名\",\"reasoning\":\"原因\",\"toolCallParams\":{\"toolName\":\"工具名\",\"toolParams\":{}}}\n" +
            "{\"actionType\":\"RAG_RETRIEVE\",\"actionName\":\"rag_retrieve\",\"reasoning\":\"原因\",\"ragRetrieveParams\":{\"query\":\"检索词\",\"knowledgeIds\":[],\"maxResults\":10}}\n" +
            "{\"actionType\":\"LLM_GENERATE\",\"actionName\":\"llm_generate\",\"reasoning\":\"原因\",\"llmGenerateParams\":{\"prompt\":\"...\"}}\n" +
            "{\"actionType\":\"DIRECT_RESPONSE\",\"actionName\":\"direct_response\",\"reasoning\":\"原因\",\"directResponseParams\":{\"content\":\"...\",\"streaming\":true}}\n" +
            "{\"actionType\":\"COMPLETE\",\"actionName\":\"complete\",\"reasoning\":\"原因\"}\n\n" +
            "多个动作示例：\n" +
            "{\"actions\":[{\"actionType\":\"TOOL_CALL\",\"actionName\":\"工具名\",\"reasoning\":\"原因\",\"toolCallParams\":{\"toolName\":\"工具名\",\"toolParams\":{}}}]}\n";
    
    /**
     * 思考：分析目标、上下文和历史结果，决定下一步动作（支持返回多个动作）
     */
    public List<AgentAction> think(String goal, AgentContext context, List<ActionResult> lastResults) {
        log.info("开始思考，目标: {}, 上次结果数量: {}", goal, lastResults != null ? lastResults.size() : 0);
        
        // 发送思考进度事件
        sendProgressEvent(context, AgentConstants.EVENT_AGENT_THINKING, "正在分析任务和用户意图...");
        
        // 构建思考提示词
        String thinkingPrompt = buildThinkingPrompt(goal, context, lastResults);
        log.info("思考提示词长度: {}", thinkingPrompt.length());
        // 调用LLM进行思考
        String thinkingResult = callLLMForThinking(thinkingPrompt, context);
        log.info("思考结果: {}", thinkingResult);
        // 解析思考结果，生成动作列表
        List<AgentAction> actions = parseThinkingResult(thinkingResult, goal, context);
        
        // 如果解析失败或为空，返回空列表
        if (actions == null || actions.isEmpty()) {
            log.warn("思考阶段未产生动作");
            return new ArrayList<>();
        }
        
        // 限制最多5个动作
        if (actions.size() > 5) {
            log.warn("动作数量超过限制（{}），只保留前5个", actions.size());
            actions = actions.subList(0, 5);
        }
        
        // 循环检测：如果检测到异常循环，强制使用LLM_GENERATE
        if (actions.size() == 1 && lastResults != null && !lastResults.isEmpty()) {
            AgentAction action = actions.get(0);
            ActionResult lastResult = lastResults.get(lastResults.size() - 1);
            if (detectLoopAnomaly(context, action, lastResult)) {
                log.warn("检测到循环调用异常，强制切换为LLM_GENERATE");
                String prompt = "用户问: " + goal + "\n\n";
                if (lastResult != null && lastResult.isSuccess()) {
                    prompt += "我已经获取到以下信息: " + lastResult.getData() + "\n\n";
                }
                prompt += "请根据已有信息，直接回答用户的问题。如果信息不足，也要友好地告知用户。";
                
                actions = java.util.Collections.singletonList(
                    AgentAction.llmGenerate(
                        com.aiagent.application.service.action.LLMGenerateParams.builder()
                            .prompt(prompt)
                            .build(),
                        "检测到重复调用，使用已有信息直接回答"
                    )
                );
            }
        }
        
        log.info("思考完成，决定执行 {} 个动作: {}", actions.size(), 
            actions.stream().map(AgentAction::getName).collect(java.util.stream.Collectors.joining(", ")));
        return actions;
    }
    
    /**
     * 构建思考提示词（使用决策框架）
     */
    private String buildThinkingPrompt(String goal, AgentContext context, List<ActionResult> lastResults) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个智能Agent的思考模块，需要决定下一步动作。\n\n");
        
        // ========== 第一部分：当前状态 ==========
        prompt.append("## 当前状态\n\n");
        prompt.append("**用户需求**: ").append(goal).append("\n\n");
        
        // 对话历史（最近3轮，截断）
        if (context != null && context.getMessages() != null && !context.getMessages().isEmpty()) {
            prompt.append("**对话历史**（最近3轮）:\n");
            List<ChatMessage> recentMessages = context.getMessages();
            int start = Math.max(0, recentMessages.size() - 3);
            for (int i = start; i < recentMessages.size(); i++) {
                ChatMessage msg = recentMessages.get(i);
                if (msg instanceof UserMessage) {
                    String text = ((UserMessage) msg).singleText();
                    if (text.length() > 200) {
                        text = text.substring(0, 200) + "...";
                    }
                    prompt.append("- 用户: ").append(text).append("\n");
                } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                    dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) msg;
                    String text = aiMsg.text();
                    if (text.length() > 200) {
                        text = text.substring(0, 200) + "...";
                    }
                    prompt.append("- 助手: ").append(text).append("\n");
                }
            }
            prompt.append("\n");
        }
        
        // 工具调用历史（最近2次）
        if (context != null && context.getToolCallHistory() != null && !context.getToolCallHistory().isEmpty()) {
            prompt.append("**工具调用历史**（最近2次）:\n");
            int historySize = context.getToolCallHistory().size();
            int start = Math.max(0, historySize - 2);
            for (int i = start; i < historySize; i++) {
                Map<String, Object> call = context.getToolCallHistory().get(i);
                prompt.append("- ").append(call.get("toolName"));
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        // 上次执行结果（简要）
        if (lastResults != null && !lastResults.isEmpty()) {
            prompt.append("**上次执行结果**（共 ").append(lastResults.size()).append(" 个动作）:\n");
            for (int i = 0; i < lastResults.size(); i++) {
                ActionResult result = lastResults.get(i);
                prompt.append("动作 ").append(i + 1).append(" (").append(result.getActionName()).append("): ");
                if (result.isSuccess()) {
                    String resultData = result.getData() != null ? result.getData().toString() : "";
                    if (resultData.length() > 300) {
                        resultData = resultData.substring(0, 300) + "...";
                    }
                    prompt.append("成功: ").append(resultData);
                } else {
                    prompt.append("失败: ").append(result.getError());
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        // ========== 第二部分：决策要求 ==========
        prompt.append(DECISION_FRAMEWORK_PROMPT);
        
        // ========== 第三部分：可用工具 ==========
        List<McpToolInfo> availableTools = toolSelector.selectTools(goal,
            context != null ? context.getEnabledMcpGroups() : null,
            context != null ? context.getEnabledTools() : null);
        if (!availableTools.isEmpty()) {
            prompt.append("## 可用工具\n\n");
            for (McpToolInfo tool : availableTools) {
                prompt.append("- ").append(tool.getName());
                if (StringUtils.isNotEmpty(tool.getDescription())) {
                    String desc = tool.getDescription();
                    if (desc.length() > 120) {
                        desc = desc.substring(0, 120) + "...";
                    }
                    prompt.append(" (").append(desc).append(")");
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        // ========== 第四部分：输出格式 ==========
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
            long startNs = System.nanoTime();
            log.info("思考LLM请求开始，modelId={}, promptChars={}", modelId, prompt != null ? prompt.length() : 0);
            
            // 调用非流式LLM获取完整响应
            String response = llmChatHandler.chatNonStreaming(modelId, messages);
            
            log.info("思考LLM请求完成，耗时 {} ms", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
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
        
        // 1. 优先判断：检查是否为简单场景，可以直接返回（使用checkQuickResponse）
        AgentAction quickResponse = checkQuickResponse(goal, context);
        if (quickResponse != null) {
            log.info("降级逻辑识别为简单场景，返回DIRECT_RESPONSE");
            return createDirectResponseAction(quickResponse);
        }
        
        String lowerGoal = goal.toLowerCase();
        
        // 2. 判断：元信息查询（询问系统功能、能力等）
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
        List<com.aiagent.api.dto.McpToolInfo> availableTools = toolSelector.selectTools(goal, 
            context != null ? context.getEnabledMcpGroups() : null,
            context != null ? context.getEnabledTools() : null);
        
        if (!availableTools.isEmpty() && hasActionIntent(lowerGoal)) {
            log.info("识别为操作意图，返回TOOL_CALL");
            com.aiagent.api.dto.McpToolInfo firstTool = availableTools.get(0);
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
     * 检查是否为简单场景，可以直接返回响应
     * 
     * @param goal 用户目标
     * @param context Agent上下文
     * @return 如果是简单场景，返回对应的AgentAction；否则返回null
     */
    private AgentAction checkQuickResponse(String goal, AgentContext context) {
        if (StringUtils.isEmpty(goal)) {
            return null;
        }
        
        String lowerGoal = goal.toLowerCase().trim();
        
        // 1. 系统能力询问（MCP工具列表）
        AgentAction action = checkSystemCapabilityQuery(lowerGoal, goal, context);
        if (action != null) {
            return action;
        }
        
        // 2. 可以继续添加其他简单场景的判断...
        // 例如：问候、帮助信息等
        
        return null;
    }
    
    /**
     * 检查是否为系统能力询问（MCP工具列表）
     */
    private AgentAction checkSystemCapabilityQuery(String lowerGoal, String originalGoal, AgentContext context) {
        // 匹配模式：询问系统能力、MCP工具、可用工具等
        // 关键词列表（不区分顺序，只要包含即可）
        String[] capabilityKeywords = {
            "你能", "你会", "你有什么", "功能", "能力", "工具", "mcp",
            "capability", "tool", "能调用", "可用"
        };
        
        // 简单匹配：包含关键词
        boolean isCapabilityQuery = false;
        int matchCount = 0;
        for (String keyword : capabilityKeywords) {
            if (lowerGoal.contains(keyword)) {
                matchCount++;
            }
        }
        
        // 如果包含2个或以上关键词，很可能是能力询问
        if (matchCount >= 2) {
            isCapabilityQuery = true;
        }
        
        // 更精确的匹配：检查是否包含疑问词和能力词组合
        if (!isCapabilityQuery) {
            boolean hasQuestionWord = lowerGoal.contains("什么") || lowerGoal.contains("哪些") || 
                                    lowerGoal.contains("how") || lowerGoal.contains("what") ||
                                    lowerGoal.contains("？") || lowerGoal.contains("?");
            boolean hasCapabilityWord = lowerGoal.contains("功能") || lowerGoal.contains("能力") || 
                                      lowerGoal.contains("工具") || lowerGoal.contains("mcp") ||
                                      lowerGoal.contains("capability") || lowerGoal.contains("tool") ||
                                      lowerGoal.contains("能调用") || lowerGoal.contains("可用");
            isCapabilityQuery = hasQuestionWord && hasCapabilityWord;
        }
        
        if (!isCapabilityQuery) {
            return null;
        }
        
        log.info("识别为系统能力询问: {}", originalGoal);
        
        // 获取可用工具列表
        List<McpToolInfo> tools = toolSelector.selectTools(originalGoal, 
            context != null ? context.getEnabledMcpGroups() : null,
            context != null ? context.getEnabledTools() : null);
        
        // 按服务器分组
        java.util.Map<String, List<McpToolInfo>> toolsByServer = new java.util.HashMap<>();
        for (McpToolInfo tool : tools) {
            String serverId = tool.getServerId() != null ? tool.getServerId() : "unknown";
            toolsByServer.computeIfAbsent(serverId, k -> new java.util.ArrayList<>()).add(tool);
        }
        
        // 构建工具介绍内容
        StringBuilder content = new StringBuilder();
        content.append("我可以调用以下MCP能力：\n\n");
        
        if (tools.isEmpty()) {
            content.append("当前未配置MCP工具。");
        } else {
            int index = 1;
            for (java.util.Map.Entry<String, List<McpToolInfo>> entry : toolsByServer.entrySet()) {
                String serverId = entry.getKey();
                List<McpToolInfo> serverTools = entry.getValue();
                
                // 服务器名称（如果有分组信息，可以使用分组名称）
                content.append("**").append(serverId).append("**:\n");
                for (McpToolInfo tool : serverTools) {
                    content.append(index++).append(". **").append(tool.getName()).append("**");
                    if (StringUtils.isNotEmpty(tool.getDescription())) {
                        content.append(" - ").append(tool.getDescription());
                    }
                    content.append("\n");
                }
                content.append("\n");
            }
        }
        
        String reasoning = "用户询问系统能力，属于元信息查询。已从系统配置中获取所有可用的MCP工具列表，无需调用外部工具，可以直接向用户介绍这些能力。";
        
        return AgentAction.directResponse(
            DirectResponseParams.builder()
                .content(content.toString())
                .systemPrompt("请友好、清晰地介绍这些MCP能力。")
                .streaming(true)
                .build(),
            reasoning
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
     * 创建DIRECT_RESPONSE动作的JSON字符串
     */
    private String createDirectResponseAction(AgentAction action) {
        if (action == null || action.getDirectResponseParams() == null) {
            log.warn("无效的DIRECT_RESPONSE动作");
            return null;
        }
        
        DirectResponseParams params = action.getDirectResponseParams();
        Map<String, Object> result = new HashMap<>();
        result.put("actionType", "DIRECT_RESPONSE");
        result.put("actionName", "direct_response");
        result.put("reasoning", action.getReasoning());
        
        Map<String, Object> directResponseParams = new HashMap<>();
        directResponseParams.put("content", params.getContent());
        if (StringUtils.isNotEmpty(params.getSystemPrompt())) {
            directResponseParams.put("systemPrompt", params.getSystemPrompt());
        }
        directResponseParams.put("streaming", params.isStreaming());
        result.put("directResponseParams", directResponseParams);
        
        return JSON.toJSONString(result);
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
     * 解析思考结果，生成动作列表（支持单个或多个动作）
     */
    private List<AgentAction> parseThinkingResult(String thinkingResult, String goal, AgentContext context) {
        try {
            // 清理返回文本，移除可能的Markdown代码块包装和其他文本
            String cleanedResult = cleanJsonResponse(thinkingResult);
            log.debug("清理后的思考结果: {}", cleanedResult);
            
            JSONObject json = JSON.parseObject(cleanedResult);
            
            // 检查是否有actions数组（多个动作）
            if (json.containsKey("actions")) {
                List<Object> actionsList = json.getList("actions", Object.class);
                if (actionsList != null && !actionsList.isEmpty()) {
                    List<AgentAction> actions = new ArrayList<>();
                    for (Object actionObj : actionsList) {
                        if (actionObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> actionMap = (Map<String, Object>) actionObj;
                            JSONObject actionJson = new JSONObject(actionMap);
                            AgentAction action = parseSingleAction(actionJson, context);
                            if (action != null) {
                                actions.add(action);
                            }
                        } else if (actionObj instanceof JSONObject) {
                            AgentAction action = parseSingleAction((JSONObject) actionObj, context);
                            if (action != null) {
                                actions.add(action);
                            }
                        }
                    }
                    return actions;
                }
            }
            
            // 兼容旧格式：单个动作（直接是action对象）
            AgentAction singleAction = parseSingleAction(json, context);
            if (singleAction != null) {
                return java.util.Collections.singletonList(singleAction);
            }
            
            return new ArrayList<>();
                
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
            return new ArrayList<>();
        }
    }
    
    /**
     * 解析单个动作
     */
    private AgentAction parseSingleAction(JSONObject json, AgentContext context) {
        String actionType = json.getString("actionType");
        String actionName = json.getString("actionName");
        String reasoning = json.getString("reasoning");
        
        if (StringUtils.isEmpty(actionType)) {
            log.warn("动作中缺少actionType");
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
            case DIRECT_RESPONSE:
                action = parseDirectResponseAction(json, actionName, reasoning, context);
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
     * 解析直接返回响应动作
     */
    private AgentAction parseDirectResponseAction(JSONObject json, String actionName, String reasoning, AgentContext context) {
        JSONObject directResponseParamsJson = json.getJSONObject("directResponseParams");
        if (directResponseParamsJson == null) {
            log.warn("DIRECT_RESPONSE动作缺少directResponseParams");
            return null;
        }
        
        String content = directResponseParamsJson.getString("content");
        if (StringUtils.isEmpty(content)) {
            log.warn("DIRECT_RESPONSE动作缺少content");
            return null;
        }
        
        String systemPrompt = directResponseParamsJson.getString("systemPrompt");
        boolean streaming = directResponseParamsJson.getBooleanValue("streaming");
        // 如果未指定streaming，默认为true
        if (!directResponseParamsJson.containsKey("streaming")) {
            streaming = true;
        }
        
        DirectResponseParams directResponseParams = DirectResponseParams.builder()
            .content(content)
            .systemPrompt(systemPrompt)
            .streaming(streaming)
            .build();
        
        return AgentAction.directResponse(directResponseParams, reasoning);
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

