package com.aiagent.application.service.engine;

import com.aiagent.api.dto.AgentEventData;
import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.api.dto.ThinkingConfig;
import com.aiagent.application.model.AgentContext;
import com.aiagent.application.model.AgentKnowledgeDocument;
import com.aiagent.application.model.AgentKnowledgeResult;
import com.aiagent.application.service.StreamingCallback;
import com.aiagent.application.service.action.*;
import com.aiagent.domain.enums.ActionType;
import com.aiagent.domain.enums.ParseErrCode;
import com.aiagent.domain.model.KnowledgeBase;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.shared.util.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 思考引擎 (基于OpenAI原生Reasoning能力)
 * 适配支持 reasoning_content 的模型 (如 DeepSeek R1, OpenAI o1/o3)
 * 
 * @author aiagent
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aiagent.thinking.engine", havingValue = "openai_reasoning", matchIfMissing = true)
public class OpenAIReasoningThinkingEngine implements ThinkingEngine {

    @Autowired
    private SimpleLLMChatHandler llmChatHandler;

    @Autowired
    private IntelligentToolSelector toolSelector;

    @Resource
    private AgentConfig agentConfig;

    /**
     * 决策框架提示词 (简化版，移除手动思考引导)
     */
    private static final String DECISION_FRAMEWORK_PROMPT = """
            ## 决策要求
            1. 分析目标和上下文，决定下一步动作。
            2. 仅在必要时调用工具 (TOOL_CALL)。
            3. 需要知识库资料时选 RAG_RETRIEVE。
            4. 如果可以回答用户问题，使用 DIRECT_RESPONSE。
            5. actionType 仅限: TOOL_CALL, RAG_RETRIEVE, DIRECT_RESPONSE, LLM_GENERATE。
            6. 每轮至少输出一个 Action。
            ### 各actionType参数规范（必填+可选）
            #### (A) TOOL_CALL（调用工具）
            当actionType=TOOL_CALL时，必须包含toolCallParams字段，结构如下：
            "toolCallParams": {
              "toolName": "工具名称(String, 必填)", // 如ResourceCenter-20221201-SearchResources
              "toolParams": "{}"// 工具具体参数对象，以tool的params JsonObjectSchema要求为准
            }
            #### (B) RAG_RETRIEVE（知识库检索）
            当actionType=RAG_RETRIEVE时，必须包含ragRetrieveParams字段，结构如下：
            "ragRetrieveParams": {
              "query": "检索关键词(String, 必填)",
              "knowledgeIds": ["kb_id1"], // 知识库ID列表(List<String>, 可选)
              "maxResults": 5, // 最大结果数(Integer, 可选，默认5)
              "similarityThreshold": 0.7 // 相似度阈值(Double, 可选，默认0.7)
            }
            
            #### (C) LLM_GENERATE（大模型生成）
            当actionType=LLM_GENERATE时，必须包含llmGenerateParams字段，结构如下：
            "llmGenerateParams": {
              "prompt": "提示词(String, 必填)", // 给子模型的生成指令
              "systemPrompt": "系统设定(String, 可选)", // 子模型的系统角色
              "temperature": 0.7 // 温度(Double, 可选，默认0.7)
            }
            
            #### (D) DIRECT_RESPONSE（直接回复）
            当actionType=DIRECT_RESPONSE时，必须包含directResponseParams字段，结构如下：
            "directResponseParams": {
              "content": "回复内容(String, 必填)", // 给用户的最终回复
            }
            """;

    @Override
    public List<AgentAction> think(String goal, AgentContext context, List<ActionResult> lastResults) {
        log.info("开始原生推理思考，目标: {}", goal);

        // 发送思考进度事件
        sendProgressEvent(context, AgentConstants.EVENT_STATUS_ANALYZING, "正在通过推理模型分析...");

        List<AgentAction> finalActions = new ArrayList<>();
        int maxRetries = 3;
        String retryHint = "";

        //重试机制
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            PromptPair promptPair = buildThinkingPrompt(goal, context, lastResults, retryHint);
            log.info("系统提示词：{}\n用户提示词:{}",promptPair.getSystemPrompt(),promptPair.getUserPrompt());
            List<ChatMessage> messages = buildMessages(promptPair, context);
            String modelId = getModelId(context);

            log.info("调用推理模型: {}", modelId);
            
            // 使用支持 onThinking 的回调
            StreamingCallback callback = createReasoningStreamingCallback(context);
            
            String fullText = llmChatHandler.chatWithResponseFormat(
                    modelId,
                    messages,
                    buildStructuredResponseFormat(),
                    callback
            );
            
            log.info("AI响应: {}", fullText);

            try {
                finalActions = parseThinkingResult(fullText, context);
                break; // 解析成功，退出重试
            } catch (Exception e) {
                log.warn("解析失败，尝试重试: {}", e.getMessage());
                retryHint = "JSON解析失败，请确保输出符合Schema要求。错误: " + e.getMessage();
                sendStatusEvent(context, AgentConstants.EVENT_STATUS_RETRYING, "输出格式错误，正在重试...", null);
            }
        }

        if (finalActions.isEmpty()) {
            log.warn("未产生有效动作");
            return new ArrayList<>();
        }

        log.info("决定执行 {} 个动作", finalActions.size());
        sendDecidedActionsEvent(context, finalActions);
        return finalActions;
    }

    private PromptPair buildThinkingPrompt(String goal, AgentContext context, List<ActionResult> lastResults, String retryHint) {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是一个拥有深度推理能力的智能Agent。\n");
        systemPrompt.append(DECISION_FRAMEWORK_PROMPT);
        
        if (StringUtils.isNotEmpty(retryHint)) {
            systemPrompt.append("\n## 修正要求\n").append(retryHint);
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("## 当前目标\n").append(goal).append("\n\n");
        
        // 工具列表
        List<McpToolInfo> tools = toolSelector.selectTools(goal, context.getEnabledMcpGroups(), context.getEnabledTools());
        if (!tools.isEmpty()) {
            userPrompt.append("## 可用工具\n");
            for (McpToolInfo tool : tools) {
                userPrompt.append(formatToolDefinition(tool)).append("\n");
            }
            userPrompt.append("\n");
        }

        // RAG 信息
        appendRAGInfo(userPrompt, context);

        // 历史记录
        appendHistory(userPrompt, context);

        return new PromptPair(systemPrompt.toString(), userPrompt.toString());
    }

    private List<ChatMessage> buildMessages(PromptPair promptPair, AgentContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 历史消息
        ThinkingConfig cfg = context.getThinkingConfig();
        int historyLimit = (cfg != null) ? cfg.getHistoryMessageLoadLimitOrDefault() : 10;
        if (context.getMessages() != null) {
            List<ChatMessage> history = context.getMessages();
            int start = Math.max(0, history.size() - historyLimit);
            for (int i = start; i < history.size(); i++) {
                messages.add(history.get(i));
            }
        }
        
        messages.add(new SystemMessage(promptPair.getSystemPrompt()));
        messages.add(new UserMessage(promptPair.getUserPrompt()));
        
        return messages;
    }

    private StreamingCallback createReasoningStreamingCallback(AgentContext context) {
        return new StreamingCallback() {
            private boolean thinkingStarted = false;

            @Override
            public void onThinking(String thinkingToken) {
                if (StringUtils.isEmpty(thinkingToken)) return;
                
                if (!thinkingStarted) {
                    thinkingStarted = true;
                    sendProgressEvent(context, AgentConstants.EVENT_STATUS_THINKING_PROCESS, "正在进行深度推理...");
                }
                
                // 直接发送推理内容
                sendThinkingDeltaEvent(context, thinkingToken);
            }

            @Override
            public void onToken(String token) {
                // 普通内容生成，暂不处理，或者可以作为 Log 输出
            }

            @Override
            public void onComplete(String fullText) {
                sendProgressEvent(context, AgentConstants.EVENT_STATUS_PLANNING, "推理完成，生成执行计划...");
            }

            @Override
            public void onError(Throwable error) {
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_ERROR, "推理过程出错: " + error.getMessage());
            }
        };
    }

    /**
     * 简化的 Schema，仅包含 actions，不需要 thinking 字段
     */
    private ResponseFormat buildStructuredResponseFormat() {
        JsonObjectSchema toolCallParams = JsonObjectSchema.builder()
            .addStringProperty("toolName")
            .addProperty("toolParams", JsonObjectSchema.builder().build())
            .required("toolName", "toolParams")
            .build();
            
        JsonObjectSchema ragRetrieveParams = JsonObjectSchema.builder()
            .addStringProperty("query")
            .addProperty("knowledgeIds", JsonArraySchema.builder().items(JsonStringSchema.builder().build()).build())
            .build();
            
        JsonObjectSchema llmGenerateParams = JsonObjectSchema.builder()
            .addStringProperty("prompt")
            .addStringProperty("systemPrompt")
            .addNumberProperty("temperature")
            .required("prompt")
            .build();
            
        JsonObjectSchema directResponseParams = JsonObjectSchema.builder()
            .addStringProperty("content")
            .addBooleanProperty("streaming")
            .required("content")
            .build();
            
        JsonObjectSchema actionItem = JsonObjectSchema.builder()
            .addStringProperty("actionType")
            .addStringProperty("actionName")
            .addStringProperty("reasoning")
            .addProperty("toolCallParams", toolCallParams)
            .addProperty("ragRetrieveParams", ragRetrieveParams)
            .addProperty("llmGenerateParams", llmGenerateParams)
            .addProperty("directResponseParams", directResponseParams)
            .required("actionType", "actionName")
            .build();
            
        JsonArraySchema actionsArray = JsonArraySchema.builder()
            .items(actionItem)
            .build();
            
        JsonObjectSchema root = JsonObjectSchema.builder()
            .addProperty("actions", actionsArray)
            .required("actions")
            .build();
            
        JsonSchema schema = JsonSchema.builder()
            .name("AgentDecision")
            .rootElement(root)
            .build();
            
        return ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(schema)
            .build();
    }

    private List<AgentAction> parseThinkingResult(String json, AgentContext context) {
        String cleaned = cleanJsonResponse(json);
        JSONObject root = JSON.parseObject(cleaned);
        if (root == null || !root.containsKey("actions")) {
            throw new RuntimeException("缺少 actions 字段");
        }
        
        JSONArray actions = root.getJSONArray("actions");
        return buildAgentActions(actions.toJavaList(ActionInputDTO.class), context);
    }
    
    // --- 复用 PromptGuidedThinkingEngine 的部分辅助逻辑 (简化版) ---
    
    private List<AgentAction> buildAgentActions(List<ActionInputDTO> dtos, AgentContext context) {
        return dtos.stream()
            .map(dto -> buildAgentAction(dto, context))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private AgentAction buildAgentAction(ActionInputDTO dto, AgentContext context) {
        if (StringUtils.isEmpty(dto.getActionType())) return null;
        try {
            ActionType type = ActionType.getByCode(dto.getActionType());
            if (type == null) {
                throw new LLMParseException(ParseErrCode.ACTION_TYPE_INVALID);
            }
            String name = StringUtils.isNotEmpty(dto.getActionName()) ? dto.getActionName() : type.name().toLowerCase();
            
            switch (type) {
                case TOOL_CALL:
                    if (dto.getToolCallParams() == null) {
                        throw new LLMParseException(ParseErrCode.ACTION_TYPE_INVALID);
                    }
                    if (StringUtils.isEmpty(dto.getToolCallParams().getToolName())) {
                        dto.getToolCallParams().setToolName(name);
                    }
                    return AgentAction.toolCall(name, dto.getToolCallParams());
                case RAG_RETRIEVE:
                    if (dto.getRagRetrieveParams() == null) return null;
                    if (dto.getRagRetrieveParams().getKnowledgeIds() == null) dto.getRagRetrieveParams().setKnowledgeIds(context.getKnowledgeIds());
                    return AgentAction.ragRetrieve(dto.getRagRetrieveParams());
                case DIRECT_RESPONSE:
                    if (dto.getDirectResponseParams() == null) return null;
                    return AgentAction.directResponse(dto.getDirectResponseParams());
                case LLM_GENERATE:
                    if (dto.getLlmGenerateParams() == null) return null;
                    return AgentAction.llmGenerate(dto.getLlmGenerateParams());
                default:
                    return null;
            }
        } catch (Exception e) {
            log.error("构建Action失败", e);
            return null;
        }
    }

    private String cleanJsonResponse(String response) {
        if (StringUtils.isEmpty(response)) return "{}";
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int idx = cleaned.indexOf("\n");
            if (idx > 0) cleaned = cleaned.substring(idx + 1);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private String getModelId(AgentContext context) {
        return StringUtils.isNotEmpty(context.getModelId()) ? context.getModelId() : agentConfig.getLlm().getDefaultModel();
    }

    private void sendProgressEvent(AgentContext context, String event, String message) {
        if (context != null && context.getEventPublisher() != null) {
            context.getEventPublisher().accept(AgentEventData.builder().event(event).message(message).build());
        }
    }

    private void sendStatusEvent(AgentContext context, String event, String message, Map<String, Object> data) {
        if (context != null && context.getEventPublisher() != null) {
            context.getEventPublisher().accept(AgentEventData.builder().event(event).message(message).data(data).build());
        }
    }

    private void sendThinkingDeltaEvent(AgentContext context, String content) {
        if (context != null && context.getEventPublisher() != null) {
            context.getEventPublisher().accept(AgentEventData.builder()
                .event(AgentConstants.EVENT_AGENT_THINKING_DELTA)
                .content(content)
                .build());
        }
    }

    private void sendDecidedActionsEvent(AgentContext context, List<AgentAction> actions) {
        if (context == null || context.getEventPublisher() == null) return;
        String names = actions.stream().map(AgentAction::getName).collect(Collectors.joining(", "));
        Map<String, Object> data = new HashMap<>();
        data.put("actionNames", names);
        context.getEventPublisher().accept(AgentEventData.builder()
            .event(AgentConstants.EVENT_STATUS_THINKING_PROCESS)
            .message("思考完成")
            .data(data)
            .build());
    }

    private String formatToolDefinition(McpToolInfo tool) {
        return "- " + tool.getName() + ": " + tool.getDescription() + "\nparams:" + tool.getParameters().toString();
    }

    private void appendRAGInfo(StringBuilder prompt, AgentContext context) {
        AgentKnowledgeResult result = context.getInitialRagResult();
        if (result != null && !result.isEmpty()) {
            prompt.append("## 知识库检索结果\n");
            for (AgentKnowledgeDocument doc : result.getDocuments()) {
                prompt.append("- ").append(doc.getContent()).append("\n");
            }
            prompt.append("\n");
        }
    }

    private void appendHistory(StringBuilder prompt, AgentContext context) {
        if (context.getActionExecutionHistory() != null && !context.getActionExecutionHistory().isEmpty()) {
            prompt.append("## 执行历史\n");
            int i = 1;
            for (List<ActionResult> results : context.getActionExecutionHistory()) {
                prompt.append("第 ").append(i++).append(" 轮:\n");
                for (ActionResult res : results) {
                    prompt.append(res.toString()).append("\n");
                }
            }
            prompt.append("\n");
        }
    }
}
