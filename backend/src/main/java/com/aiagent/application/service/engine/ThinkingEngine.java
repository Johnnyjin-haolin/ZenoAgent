package com.aiagent.application.service.engine;

import com.aiagent.application.service.action.ActionInputDTO;
import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.action.ActionsResponseDTO;
import com.aiagent.application.service.action.AgentAction;
import com.aiagent.domain.enums.ActionType;
import com.aiagent.infrastructure.config.AgentConfig;
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

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 思考引擎
 * 负责分析当前情况，决定下一步Action
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

    @Resource
    private AgentConfig agentConfig;
    
    
    
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
     * 统一使用 actions 数组格式，即使只有一个Action也要放在数组中
     */
    private static final String OUTPUT_FORMAT_PROMPT = "## 输出格式\n\n" +
            "只返回JSON对象，不要包含其他文字。\n" +
            "必须使用 actions 数组格式，即使只有一个Action也要放在数组中。\n" +
            "actionType 只能是 TOOL_CALL / RAG_RETRIEVE / LLM_GENERATE / DIRECT_RESPONSE / COMPLETE。\n\n" +
            "格式示例（单个Action）：\n" +
            "{\"actions\":[{\"actionType\":\"TOOL_CALL\",\"actionName\":\"工具名\",\"reasoning\":\"原因\",\"toolCallParams\":{\"toolName\":\"工具名\",\"toolParams\":{}}}]}\n" +
            "{\"actions\":[{\"actionType\":\"RAG_RETRIEVE\",\"actionName\":\"rag_retrieve\",\"reasoning\":\"原因\",\"ragRetrieveParams\":{\"query\":\"检索词\",\"knowledgeIds\":[],\"maxResults\":10}}]}\n" +
            "{\"actions\":[{\"actionType\":\"LLM_GENERATE\",\"actionName\":\"llm_generate\",\"reasoning\":\"原因\",\"llmGenerateParams\":{\"prompt\":\"请根据上下文生成回复\"}}]}\n" +
            "{\"actions\":[{\"actionType\":\"DIRECT_RESPONSE\",\"actionName\":\"direct_response\",\"reasoning\":\"原因\",\"directResponseParams\":{\"content\":\"...\",\"streaming\":true}}]}\n\n" +
            "格式示例（多个Action）：\n" +
            "{\"actions\":[{\"actionType\":\"TOOL_CALL\",\"actionName\":\"工具名1\",\"reasoning\":\"原因1\",\"toolCallParams\":{\"toolName\":\"工具名1\",\"toolParams\":{}}},{\"actionType\":\"RAG_RETRIEVE\",\"actionName\":\"rag_retrieve\",\"reasoning\":\"原因2\",\"ragRetrieveParams\":{\"query\":\"检索词\",\"knowledgeIds\":[],\"maxResults\":10}}]}\n";
    
    /**
     * 思考：分析目标、上下文和历史结果，决定下一步Action（支持返回多个Action）
     */
    public List<AgentAction> think(String goal, AgentContext context, List<ActionResult> lastResults) {
        log.info("开始思考，目标: {}, 上次结果数量: {}", goal, lastResults != null ? lastResults.size() : 0);
        
        // 发送思考进度事件
        sendProgressEvent(context, AgentConstants.EVENT_AGENT_THINKING, "正在分析任务和用户意图...");
        
        // 构建思考提示词（分离系统提示词和用户提示词）
        PromptPair promptPair = buildThinkingPrompt(goal, context, lastResults);
        log.info("系统提示词长度: {}, 用户提示词长度: {}", 
            promptPair.getSystemPrompt().length(), promptPair.getUserPrompt().length());
        // 调用LLM进行思考
        String thinkingResult = callLLMForThinking(promptPair, context);
        log.info("思考结果: {}", thinkingResult);
        // 解析思考结果，生成Action列表
        List<AgentAction> actions = parseThinkingResult(thinkingResult, goal, context);
        
        // 如果解析失败或为空，返回空列表
        if (actions.isEmpty()) {
            log.warn("思考阶段未产生Action");
            return new ArrayList<>();
        }
        
        // 限制最多5个Action
        if (actions.size() > 5) {
            log.warn("Action数量超过限制（{}），只保留前5个", actions.size());
            actions = actions.subList(0, 5);
        }
        log.info("思考完成，决定执行 {} 个Action: {}", actions.size(),
            actions.stream().map(AgentAction::getName).collect(java.util.stream.Collectors.joining(", ")));
        return actions;
    }
    
    /**
     * 构建思考提示词（分离系统提示词和用户提示词）
     */
    private PromptPair buildThinkingPrompt(String goal, AgentContext context, List<ActionResult> lastResults) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(goal, context);
        return new PromptPair(systemPrompt, userPrompt);
    }
    
    /**
     * 构建系统提示词（静态规则和约束）
     */
    private String buildSystemPrompt() {

        return "你是一个智能Agent的思考模块，你需要理解用户对话背后想要做的事情，并决定下一步Action。\n\n" +

                // 决策要求
                DECISION_FRAMEWORK_PROMPT +

                // 输出格式
                OUTPUT_FORMAT_PROMPT;
    }
    
    /**
     * 构建用户提示词（动态上下文信息）
     * 使用配置参数控制历史长度和截断
     */
    private String buildUserPrompt(String goal, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // 获取配置
        com.aiagent.api.dto.ThinkingConfig config = context.getThinkingConfig();
        if (config == null) {
            config = com.aiagent.api.dto.ThinkingConfig.builder().build();
        }
        

        // ========== 可用工具 ==========
        List<McpToolInfo> availableTools = toolSelector.selectTools(goal,
                context.getEnabledMcpGroups(),
                context.getEnabledTools());
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

        // ========== 对话历史 ==========
        if (context.getMessages() != null && !context.getMessages().isEmpty()) {
            int rounds = config.getConversationHistoryRoundsOrDefault();
            int maxLength = config.getMaxMessageLengthOrDefault();

            List<ChatMessage> recentMessages = context.getMessages();
            // 每轮2条消息（用户+AI）
            int start = Math.max(0, recentMessages.size() - rounds * 2);

            if (start < recentMessages.size()) {
                prompt.append("## 对话历史（最近").append(rounds).append("轮）\n\n");
                for (int i = start; i < recentMessages.size(); i++) {
                    ChatMessage msg = recentMessages.get(i);
                    if (msg instanceof UserMessage) {
                        String text = ((UserMessage) msg).singleText();
                        if (text.length() > maxLength) {
                            text = text.substring(0, maxLength) + "...";
                        }
                        prompt.append("- 用户: ").append(text).append("\n");
                    } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                        dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) msg;
                        String text = aiMsg.text();
                        if (text.length() > maxLength) {
                            text = text.substring(0, maxLength) + "...";
                        }
                        prompt.append("- 助手: ").append(text).append("\n");
                    }
                }
                prompt.append("\n");
            }
        }

        // ========== Action执行历史（按迭代轮次展示）==========
        if (context.getActionExecutionHistory() != null && !context.getActionExecutionHistory().isEmpty()) {
            int totalIterations = context.getActionExecutionHistory().size();

            Integer showIterations = config.getActionExecutionHistoryCount();
            int start = showIterations==null?0:totalIterations-showIterations;

            if (start < totalIterations) {
                prompt.append("## 最近Action执行历史（最近").append(showIterations).append("轮迭代）\n\n");

                for (int i = start; i < totalIterations; i++) {
                    List<ActionResult> iterationResults = context.getActionExecutionHistory().get(i);

                    // 迭代标题
                    prompt.append("### 第 ").append(i + 1).append(" 轮迭代");
                    if (iterationResults.size() == 1) {
                        prompt.append("（1 个Action）\n\n");
                    } else {
                        prompt.append("（").append(iterationResults.size()).append(" 个Action）\n\n");
                    }

                    // 展示该轮的每个Action - 直接使用 ActionResult.toString()
                    for (int j = 0; j < iterationResults.size(); j++) {
                        ActionResult result = iterationResults.get(j);

                        // 直接使用 toString() 方法获取 AI 可读的格式化字符串
                        prompt.append("**Action ").append(j + 1).append("**:\n");
                        prompt.append(result.toString());
                        prompt.append("\n");
                    }
                }
            }
        }
        // ========== 当前目标 ==========
        prompt.append("## 用户对话：\n\n");
        prompt.append(goal).append("\n\n");
        return prompt.toString();
    }
    
    /**
     * 调用LLM进行思考
     * 使用系统提示词和用户提示词分离的方式
     */
    private String callLLMForThinking(PromptPair promptPair, AgentContext context) {
            // 准备消息列表
            List<ChatMessage> messages = new ArrayList<>();
            // 使用构建好的系统提示词
            messages.add(new SystemMessage(promptPair.getSystemPrompt()));
            // 使用构建好的用户提示词
            messages.add(new UserMessage(promptPair.getUserPrompt()));
            
            // 获取模型ID（从上下文或使用默认值）
            String modelId = context != null ? context.getModelId() : null;
            if (StringUtils.isEmpty(modelId)) {
                modelId = agentConfig.getLlm().getDefaultModel();
            }
            long startNs = System.nanoTime();
            log.info("思考LLM请求开始，modelId={}, systemPromptChars={}, userPromptChars={}", 
                modelId, 
                promptPair.getSystemPrompt(),
                promptPair.getUserPrompt());
            
            // 调用非流式LLM获取完整响应
            String response = llmChatHandler.chatNonStreaming(modelId, messages);
            
            log.info("思考LLM请求完成，耗时 {} ms", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
            log.debug("LLM思考响应: {}", response);
            return response;
    }

    /**
     * 解析思考结果，生成Action列表
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
            log.warn("Action缺少actionType");
            return null;
        }
        
        ActionType type;
        try {
            type = ActionType.valueOf(dto.getActionType());
        } catch (IllegalArgumentException e) {
            log.warn("无效的Action类型: {}", dto.getActionType());
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
                log.warn("不支持的Action类型: {}", type);
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
     * 构建工具调用Action
     */
    private AgentAction buildToolCallAction(ActionInputDTO dto, String reasoning) {
        ToolCallParams params = dto.getToolCallParams();
        if (params == null) {
            log.warn("TOOL_CALLAction缺少toolCallParams");
            return null;
        }
        
        // 获取工具名称（优先使用toolCallParams中的，否则使用actionName）
        String toolName = params.getToolName();
        if (StringUtils.isEmpty(toolName)) {
            toolName = dto.getActionName();
        }
        if (StringUtils.isEmpty(toolName)) {
            log.warn("TOOL_CALLAction缺少工具名称");
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
     * 构建RAG检索Action
     */
    private AgentAction buildRAGRetrieveAction(ActionInputDTO dto, String reasoning, AgentContext context) {
        RAGRetrieveParams params = dto.getRagRetrieveParams();
        if (params == null) {
            log.warn("RAG_RETRIEVEAction缺少ragRetrieveParams");
            return null;
        }
        
        if (StringUtils.isEmpty(params.getQuery())) {
            log.warn("RAG_RETRIEVEAction缺少query");
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
     * 构建直接返回响应Action
     */
    private AgentAction buildDirectResponseAction(ActionInputDTO dto, String reasoning) {
        DirectResponseParams params = dto.getDirectResponseParams();
        if (params == null) {
            log.warn("DIRECT_RESPONSEAction缺少directResponseParams");
            return null;
        }
        
        if (StringUtils.isEmpty(params.getContent())) {
            log.warn("DIRECT_RESPONSEAction缺少content");
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
     * 构建LLM生成Action
     */
    private AgentAction buildLLMGenerateAction(ActionInputDTO dto, String reasoning) {
        LLMGenerateParams params = dto.getLlmGenerateParams();
        if (params == null) {
            log.warn("LLM_GENERATEAction缺少llmGenerateParams");
            return null;
        }
        
        if (StringUtils.isEmpty(params.getPrompt())) {
            log.warn("LLM_GENERATEAction缺少prompt");
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

