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
 * 目前大多数模型都不原生支持，如qwen3,但是大多模型都支持 原生支持 Reasoning 能力（会思考）但原生不输出 reasoning 字段reasoning 字段 = Ollama 封装出来的
 * 需要注意用ollama部署的模型，吐出字段为Reasoning，但是langchain4j默认解析的字段为reasoning_content,所以即使支持也会因为字段不一致而失败
 * 
 * @author aiagent
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aiagent.thinking.engine", havingValue = "openai_reasoning")
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
            1. 先判断已有信息是否足够回答用户问题
            2. 如果你觉得解决该问题需要调用工具才需要 TOOL_CALL
            3. 需要知识库资料时选 RAG_RETRIEVE
            4. 如果你已经可以直接给出完整答案，必须使用 DIRECT_RESPONSE，把最终回复放在 content
            5. 只有在需要让模型二次生成或改写时才用 LLM_GENERATE（prompt 应是指令，不是答案）
            6. 每轮思考必须输出至少一个Action，如果你觉得需要用户输入或者已经可以回答问题，请使用DIRECT_RESPONSE，把提问/回复放在 content
            7. 避免重复调用同一工具
            
            ## 格式要求
            1. 仅输出一个 JSON 对象，禁止输出任何额外文字、代码块、markdown。
            2. JSON 顶层必须包含字段 actions，类型为数组，至少 1 个元素。
            3. 每个 action 必须包含且仅包含字段：actionType、actionName，以及对应的参数对象。
            4. actionType 必须且只能取以下枚举值之一（全大写，严格匹配）：
               TOOL_CALL | RAG_RETRIEVE | DIRECT_RESPONSE | LLM_GENERATE
            5. actionType 字段名必须为 "actionType"，不得使用 "type" 或其他字段名。
            ## 输出格式（严格遵循）
            {
              "actions": [
                {
                  "actionType": "TOOL_CALL|RAG_RETRIEVE|DIRECT_RESPONSE|LLM_GENERATE",
                  "actionName": "string",
                  "toolCallParams": { ... } | "ragRetrieveParams": { ... } | "directResponseParams": { ... } | "llmGenerateParams": { ... }
                }
              ]
            }
            
            ## 各 actionType 参数要求
            #### (A) TOOL_CALL（调用工具）
            当 actionType=TOOL_CALL 时，必须包含 toolCallParams 字段，结构如下：
            "toolCallParams": {
              "toolName": "工具名称(String, 必填)",
              "toolParams": "{}"
            }
            #### (B) RAG_RETRIEVE（知识库检索）
            当 actionType=RAG_RETRIEVE 时，必须包含 ragRetrieveParams 字段，结构如下：
            "ragRetrieveParams": {
              "query": "检索关键词(String, 必填)",
              "knowledgeIds": ["kb_id1"],
              "maxResults": 5,
              "similarityThreshold": 0.7
            }
            
            #### (C) LLM_GENERATE（大模型生成）
            当 actionType=LLM_GENERATE 时，必须包含 llmGenerateParams 字段，结构如下：
            "llmGenerateParams": {
              "prompt": "提示词(String, 必填)",
              "systemPrompt": "系统设定(String, 可选)",
              "temperature": 0.7
            }
            
            #### (D) DIRECT_RESPONSE（直接回复）
            当 actionType=DIRECT_RESPONSE 时，必须包含 directResponseParams 字段，结构如下：
            "directResponseParams": {
              "content": "回复内容(String, 必填)"
            }
            
            ## 失败示例（严格禁止）
            - 使用 "type" 代替 "actionType"
            - actionType 使用小写或其他值
            - 输出任何 JSON 之外的文本
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
                break;
            } catch (LLMParseException e) {
                log.warn("解析失败，尝试重试: {}", e.getMessage());
                retryHint = e.getMessage();
                sendStatusEvent(context, AgentConstants.EVENT_STATUS_RETRYING, "输出格式错误，正在重试...", null);
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
        systemPrompt.append("你是一个智能Agent的思考与决策模块，需基于用户需求输出包含思考过程和动作指令的结构化JSON内容。。\n");
        systemPrompt.append(DECISION_FRAMEWORK_PROMPT);
        
        if (StringUtils.isNotEmpty(retryHint)) {
            systemPrompt.append("\n## 修正要求\n").append(retryHint);
        }

        StringBuilder userPrompt = new StringBuilder();

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

        userPrompt.append("## 当前目标\n").append(goal).append("\n\n");

        if (StringUtils.isNotEmpty(retryHint)) {
            userPrompt.append("\n## 修正要求\n").append(retryHint);
        }

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
     * 简化的 Schema，仅包含 actions
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
            .addBooleanProperty("isComplete")
            .required("content").required("isComplete")
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
        JSONObject root;
        try {
            root = JSON.parseObject(cleaned);
        } catch (Exception e) {
            throw new LLMParseException(ParseErrCode.JSON_PARSE, "path=$, raw=" + truncate(cleaned, 300));
        }
        if (root == null || !root.containsKey("actions")) {
            throw new LLMParseException(ParseErrCode.ACTIONS_MISSING, "path=$.actions, raw=" + truncate(cleaned, 300));
        }

        JSONArray actions = root.getJSONArray("actions");
        if (actions == null) {
            throw new LLMParseException(ParseErrCode.ACTIONS_MISSING, "path=$.actions, raw=" + truncate(root, 300));
        }
        if (actions.isEmpty()) {
            throw new LLMParseException(ParseErrCode.ACTIONS_EMPTY, "path=$.actions");
        }
        return buildAgentActions(actions, context);
    }
    
    // --- 复用 PromptGuidedThinkingEngine 的部分辅助逻辑 (简化版) ---
    
    private List<AgentAction> buildAgentActions(JSONArray actionArray, AgentContext context) {
        List<AgentAction> results = new ArrayList<>();
        for (int i = 0; i < actionArray.size(); i++) {
            Object item = actionArray.get(i);
            if (!(item instanceof JSONObject)) {
                throw new LLMParseException(ParseErrCode.ACTION_ITEM_INVALID, "path=$.actions[" + i + "], raw=" + truncate(item, 300));
            }
            JSONObject raw = (JSONObject) item;
            ActionInputDTO dto = raw.toJavaObject(ActionInputDTO.class);
            results.add(buildAgentAction(dto, context, i, raw));
        }
        return results;
    }

    private AgentAction buildAgentAction(ActionInputDTO dto, AgentContext context, int index, JSONObject raw) {
        String basePath = "$.actions[" + index + "]";
        if (dto == null) {
            throw new LLMParseException(ParseErrCode.ACTION_ITEM_INVALID, "path=" + basePath + ", raw=" + truncate(raw, 300));
        }
        if (StringUtils.isEmpty(dto.getActionType())) {
            throw new LLMParseException(ParseErrCode.ACTION_TYPE_MISSING, "path=" + basePath + ".actionType, raw=" + truncate(raw, 300));
        }
        ActionType type = ActionType.getByCode(dto.getActionType());
        if (type == null) {
            throw new LLMParseException(ParseErrCode.ACTION_TYPE_INVALID, "path=" + basePath + ".actionType, raw=" + truncate(raw, 300));
        }
        String name = StringUtils.isNotEmpty(dto.getActionName()) ? dto.getActionName() : type.name().toLowerCase();

        switch (type) {
            case TOOL_CALL:
                if (dto.getToolCallParams() == null) {
                    throw new LLMParseException(ParseErrCode.TOOL_CALL_PARAMS_MISSING, "path=" + basePath + ".toolCallParams, raw=" + truncate(raw, 300));
                }
                if (StringUtils.isEmpty(dto.getToolCallParams().getToolName())) {
                    throw new LLMParseException(ParseErrCode.TOOL_NAME_MISSING, "path=" + basePath + ".toolCallParams.toolName, raw=" + truncate(raw, 300));
                }
                return AgentAction.toolCall(name, dto.getToolCallParams());
            case RAG_RETRIEVE:
                if (dto.getRagRetrieveParams() == null) {
                    throw new LLMParseException(ParseErrCode.RAG_PARAMS_MISSING, "path=" + basePath + ".ragRetrieveParams, raw=" + truncate(raw, 300));
                }
                if (StringUtils.isEmpty(dto.getRagRetrieveParams().getQuery())) {
                    throw new LLMParseException(ParseErrCode.RAG_QUERY_MISSING, "path=" + basePath + ".ragRetrieveParams.query, raw=" + truncate(raw, 300));
                }
                if (dto.getRagRetrieveParams().getKnowledgeIds() == null) {
                    dto.getRagRetrieveParams().setKnowledgeIds(context.getKnowledgeIds());
                }
                return AgentAction.ragRetrieve(dto.getRagRetrieveParams());
            case DIRECT_RESPONSE:
                if (dto.getDirectResponseParams() == null) {
                    throw new LLMParseException(ParseErrCode.DIRECT_PARAMS_MISSING, "path=" + basePath + ".directResponseParams, raw=" + truncate(raw, 300));
                }
                if (StringUtils.isEmpty(dto.getDirectResponseParams().getContent())) {
                    throw new LLMParseException(ParseErrCode.DIRECT_CONTENT_MISSING, "path=" + basePath + ".directResponseParams.content, raw=" + truncate(raw, 300));
                }
                if (StringUtils.isEmpty(dto.getDirectResponseParams().getIsComplete())) {
                    log.warn("DIRECT_RESPONSEAction缺少ssComplete");
                    throw new LLMParseException(ParseErrCode.DIRECT_IS_COMPLETE_MISSING);
                }
                return AgentAction.directResponse(dto.getDirectResponseParams());
            case LLM_GENERATE:
                if (dto.getLlmGenerateParams() == null) {
                    throw new LLMParseException(ParseErrCode.LLM_PARAMS_MISSING, "path=" + basePath + ".llmGenerateParams, raw=" + truncate(raw, 300));
                }
                if (StringUtils.isEmpty(dto.getLlmGenerateParams().getPrompt())) {
                    throw new LLMParseException(ParseErrCode.LLM_PROMPT_MISSING, "path=" + basePath + ".llmGenerateParams.prompt, raw=" + truncate(raw, 300));
                }
                return AgentAction.llmGenerate(dto.getLlmGenerateParams());
            default:
                throw new LLMParseException(ParseErrCode.ACTION_TYPE_INVALID, "path=" + basePath + ".actionType, raw=" + truncate(raw, 300));
        }
    }

    private String truncate(Object raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String text = raw instanceof String ? (String) raw : JSON.toJSONString(raw);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
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
