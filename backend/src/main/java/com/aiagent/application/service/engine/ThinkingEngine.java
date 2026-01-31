package com.aiagent.application.service.engine;

import com.aiagent.application.service.action.ActionInputDTO;
import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.action.ActionsResponseDTO;
import com.aiagent.application.service.action.AgentAction;
import com.aiagent.domain.enums.ActionType;
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
import java.util.stream.Collectors;

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
            "2. 如果你觉得解决该问题需要调用工具才需要 TOOL_CALL\n" +
            "3. 需要知识库资料时选 RAG_RETRIEVE\n" +
            "4. 如果你已经可以直接给出完整答案，必须使用 DIRECT_RESPONSE，把最终回复放在 content\n" +
            "5. 只有在需要让模型二次生成或改写时才用 LLM_GENERATE（prompt 应是指令，不是答案）\n" +
            "6. 避免重复调用同一工具\n\n";
    
    /**
     * 输出格式提示词
     * 统一使用 actions 数组格式，即使只有一个动作也要放在数组中
     */
    private static final String OUTPUT_FORMAT_PROMPT = "## 输出格式\n\n" +
            "只返回JSON对象，不要包含其他文字。\n" +
            "必须使用 actions 数组格式，即使只有一个动作也要放在数组中。\n" +
            "actionType 只能是 TOOL_CALL / RAG_RETRIEVE / LLM_GENERATE / DIRECT_RESPONSE / COMPLETE。\n\n" +
            "格式示例（单个动作）：\n" +
            "{\"actions\":[{\"actionType\":\"TOOL_CALL\",\"actionName\":\"工具名\",\"reasoning\":\"原因\",\"toolCallParams\":{\"toolName\":\"工具名\",\"toolParams\":{}}}]}\n" +
            "{\"actions\":[{\"actionType\":\"RAG_RETRIEVE\",\"actionName\":\"rag_retrieve\",\"reasoning\":\"原因\",\"ragRetrieveParams\":{\"query\":\"检索词\",\"knowledgeIds\":[],\"maxResults\":10}}]}\n" +
            "{\"actions\":[{\"actionType\":\"LLM_GENERATE\",\"actionName\":\"llm_generate\",\"reasoning\":\"原因\",\"llmGenerateParams\":{\"prompt\":\"请根据上下文生成回复\"}}]}\n" +
            "{\"actions\":[{\"actionType\":\"DIRECT_RESPONSE\",\"actionName\":\"direct_response\",\"reasoning\":\"原因\",\"directResponseParams\":{\"content\":\"...\",\"streaming\":true}}]}\n\n" +
            "格式示例（多个动作）：\n" +
            "{\"actions\":[{\"actionType\":\"TOOL_CALL\",\"actionName\":\"工具名1\",\"reasoning\":\"原因1\",\"toolCallParams\":{\"toolName\":\"工具名1\",\"toolParams\":{}}},{\"actionType\":\"RAG_RETRIEVE\",\"actionName\":\"rag_retrieve\",\"reasoning\":\"原因2\",\"ragRetrieveParams\":{\"query\":\"检索词\",\"knowledgeIds\":[],\"maxResults\":10}}]}\n";
    
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
        
//        // 循环检测：如果检测到异常循环，强制使用LLM_GENERATE
//        if (actions.size() == 1 && lastResults != null && !lastResults.isEmpty()) {
//            AgentAction action = actions.get(0);
//            ActionResult lastResult = lastResults.get(lastResults.size() - 1);
//            if (detectLoopAnomaly(context, action, lastResult)) {
//                log.warn("检测到循环调用异常，强制切换为LLM_GENERATE");
//                String prompt = "用户问: " + goal + "\n\n";
//                if (lastResult != null && lastResult.isSuccess()) {
//                    prompt += "我已经获取到以下信息: " + lastResult.getData() + "\n\n";
//                }
//                prompt += "请根据已有信息，直接回答用户的问题。如果信息不足，也要友好地告知用户。";
//
//                actions = java.util.Collections.singletonList(
//                    AgentAction.llmGenerate(
//                        com.aiagent.application.service.action.LLMGenerateParams.builder()
//                            .prompt(prompt)
//                            .build(),
//                        "检测到重复调用，使用已有信息直接回答"
//                    )
//                );
//            }
//        }
        
        log.info("思考完成，决定执行 {} 个动作: {}", actions.size(), 
            actions.stream().map(AgentAction::getName).collect(java.util.stream.Collectors.joining(", ")));
        return actions;
    }
    
    /**
     * 构建思考提示词（使用决策框架）
     * todo 这里应该区分系统提示词和用户提示词，系统提示词中是规则，用户提示词包含的是对话历史本轮对话等动态信息
     */
    private String buildThinkingPrompt(String goal, AgentContext context, List<ActionResult> lastResults) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个智能Agent的思考模块，需要决定下一步动作。\n\n");
        
        // ========== 第一部分：当前状态 ==========
        prompt.append("## 当前状态\n\n");
        prompt.append("**用户需求**: ").append(goal).append("\n\n");

        //todo 对话历史需要区分react的轮数，每一轮将将对应action的信息和执行结果记录下来，拼接成提示词
        // 对话历史（最近3轮，截断）
        //todo 对话轮数、历史长度截断需要可配置
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
        // todo：工具调用历史存储需要在mysql中，这里需要拼接工具调用的出参和入参
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
            // 准备消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一个智能Agent的思考模块，需要分析情况并做出决策。请严格按照JSON格式返回结果。"));
            //todo 这里应该放到userMessage中吗
            // todo 我需要对代码整体做一遍走查，看下有哪些不合理的地方
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
    }

    /**
     * 解析思考结果，生成动作列表
     * 统一使用 actions 数组格式，直接使用类反序列化
     */
    private List<AgentAction> parseThinkingResult(String thinkingResult, String goal, AgentContext context) {
        // 1. 尝试解析为 ActionsResponseDTO
        ActionsResponseDTO response = tryParseActionsResponse(thinkingResult);
        if (response != null && response.hasActions()) {
            List<AgentAction> actions = buildAgentActions(response.getActions(), context);
            if (!actions.isEmpty()) {
                return actions;
            }
        }
        return new ArrayList<>();
    }
    
    /**
     * 尝试解析为 ActionsResponseDTO
     */
    private ActionsResponseDTO tryParseActionsResponse(String text) {
        try {
            String cleaned = cleanJsonResponse(text);
            log.debug("清理后的思考结果: {}", cleaned);
            return JSON.parseObject(cleaned, ActionsResponseDTO.class);
        } catch (Exception e) {
            log.error("解析JSON失败，原始结果: {}", text, e);
            return null;
        }
    }
    
    /**
     * 构建 AgentAction 列表
     */
    private List<AgentAction> buildAgentActions(List<ActionInputDTO> actionDTOs, AgentContext context) {
        return actionDTOs.stream()
            .map(dto -> buildAgentAction(dto, context))
            .filter(action -> action != null)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据 DTO 构建 AgentAction
     */
    private AgentAction buildAgentAction(ActionInputDTO dto, AgentContext context) {
        if (StringUtils.isEmpty(dto.getActionType())) {
            log.warn("动作缺少actionType");
            return null;
        }
        
        ActionType type;
        try {
            type = ActionType.valueOf(dto.getActionType());
        } catch (IllegalArgumentException e) {
            log.warn("无效的动作类型: {}", dto.getActionType());
            return null;
        }
        
        String reasoning = dto.getReasoning();
        String actionName = dto.getActionName();
        
        AgentAction action = null;
        switch (type) {
            case TOOL_CALL:
                action = buildToolCallAction(dto, reasoning);
                break;
            case RAG_RETRIEVE:
                action = buildRAGRetrieveAction(dto, reasoning, context);
                break;
            case LLM_GENERATE:
                action = buildLLMGenerateAction(dto, reasoning);
                break;
            case DIRECT_RESPONSE:
                action = buildDirectResponseAction(dto, reasoning);
                break;
            default:
                log.warn("不支持的动作类型: {}", type);
                return null;
        }
        
        // 设置 actionName（如果为空）
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
     * 构建工具调用动作
     */
    private AgentAction buildToolCallAction(ActionInputDTO dto, String reasoning) {
        ToolCallParams params = dto.getToolCallParams();
        if (params == null) {
            log.warn("TOOL_CALL动作缺少toolCallParams");
            return null;
        }
        
        // 获取工具名称（优先使用toolCallParams中的，否则使用actionName）
        String toolName = params.getToolName();
        if (StringUtils.isEmpty(toolName)) {
            toolName = dto.getActionName();
        }
        if (StringUtils.isEmpty(toolName)) {
            log.warn("TOOL_CALL动作缺少工具名称");
            return null;
        }
        
        // 确保 toolParams 不为 null
        if (params.getToolParams() == null) {
            params.setToolParams(new HashMap<>());
        }
        
        // 如果 toolCallParams 中没有 toolName，设置它
        if (StringUtils.isEmpty(params.getToolName())) {
            params.setToolName(toolName);
        }
        
        return AgentAction.toolCall(toolName, params, reasoning);
    }
    
    /**
     * 构建RAG检索动作
     */
    private AgentAction buildRAGRetrieveAction(ActionInputDTO dto, String reasoning, AgentContext context) {
        RAGRetrieveParams params = dto.getRagRetrieveParams();
        if (params == null) {
            log.warn("RAG_RETRIEVE动作缺少ragRetrieveParams");
            return null;
        }
        
        if (StringUtils.isEmpty(params.getQuery())) {
            log.warn("RAG_RETRIEVE动作缺少query");
            return null;
        }
        
        // 如果knowledgeIds为空，从上下文获取
        if (params.getKnowledgeIds() == null || params.getKnowledgeIds().isEmpty()) {
            if (context != null && context.getKnowledgeIds() != null) {
                params.setKnowledgeIds(context.getKnowledgeIds());
                log.debug("从上下文获取knowledgeIds: {}", params.getKnowledgeIds());
            } else {
                params.setKnowledgeIds(new ArrayList<>());
            }
        }
        
        return AgentAction.ragRetrieve(params, reasoning);
    }
    
    /**
     * 构建直接返回响应动作
     */
    private AgentAction buildDirectResponseAction(ActionInputDTO dto, String reasoning) {
        DirectResponseParams params = dto.getDirectResponseParams();
        if (params == null) {
            log.warn("DIRECT_RESPONSE动作缺少directResponseParams");
            return null;
        }
        
        if (StringUtils.isEmpty(params.getContent())) {
            log.warn("DIRECT_RESPONSE动作缺少content");
            return null;
        }
        
        // 如果 streaming 未设置（为 false），使用默认值 true
        // 注意：FastJSON2 反序列化时，如果 JSON 中没有 streaming 字段，boolean 类型默认为 false
        // 但根据业务逻辑，应该默认为 true
        if (!params.isStreaming() && params.getContent() != null) {
            // 重新构建，确保使用默认值 true
            params = DirectResponseParams.builder()
                .content(params.getContent())
                .systemPrompt(params.getSystemPrompt())
                .streaming(true)  // 默认值
                .build();
        }
        
        return AgentAction.directResponse(params, reasoning);
    }
    
    /**
     * 构建LLM生成动作
     */
    private AgentAction buildLLMGenerateAction(ActionInputDTO dto, String reasoning) {
        LLMGenerateParams params = dto.getLlmGenerateParams();
        if (params == null) {
            log.warn("LLM_GENERATE动作缺少llmGenerateParams");
            return null;
        }
        
        if (StringUtils.isEmpty(params.getPrompt())) {
            log.warn("LLM_GENERATE动作缺少prompt");
            return null;
        }
        
        return AgentAction.llmGenerate(params, reasoning);
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

