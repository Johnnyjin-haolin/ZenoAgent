package com.aiagent.application.service.engine;

import com.aiagent.api.dto.ThinkingConfig;
import com.aiagent.application.model.AgentKnowledgeDocument;
import com.aiagent.application.model.AgentKnowledgeResult;
import com.aiagent.application.service.action.ActionInputDTO;
import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.action.ActionsResponseDTO;
import com.aiagent.application.service.action.AgentAction;
import com.aiagent.domain.enums.ActionType;
import com.aiagent.domain.enums.ParseErrCode;
import com.aiagent.domain.model.KnowledgeBase;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.application.service.action.DirectResponseParams;
import com.aiagent.application.service.action.LLMGenerateParams;
import com.aiagent.application.service.action.RAGRetrieveParams;
import com.aiagent.application.service.action.ToolCallParams;
import com.aiagent.application.service.StreamingCallback;
import com.aiagent.shared.util.StringUtils;
import com.aiagent.application.model.AgentContext;
import com.aiagent.api.dto.AgentEventData;
import com.aiagent.api.dto.McpToolInfo;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 思考引擎 (基于提示词引导)
 * 负责分析当前情况，决定下一步Action
 * 
 * @author aiagent
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aiagent.thinking.engine", havingValue = "prompt_guided", matchIfMissing = true)
public class PromptGuidedThinkingEngine implements ThinkingEngine {
    
    @Autowired
    private SimpleLLMChatHandler llmChatHandler;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    @Autowired
    private IntelligentToolSelector toolSelector;

    @Resource
    private AgentConfig agentConfig;
    
    
    
    /**
     * 决策框架提示词
     */
    private static final String DECISION_FRAMEWORK_PROMPT = """
            ## 决策要求
            1. 先判断已有信息是否足够回答用户问题；
            2. 需要调用工具时使用 TOOL_CALL，需要知识库资料时使用 RAG_RETRIEVE；
            3. 需要向用户输出内容时使用 DIRECT_RESPONSE（回复内容放在content），并通过isComplete字段控制流程：
               - isComplete=true：**终止本轮**。用于：1. 任务彻底完成；2. **需要提问并等待用户回复**。此时意味着Agent暂停工作移交控制权给用户，actions数组中**仅允许包含这一个DIRECT_RESPONSE动作**；
               - isComplete=false：**过程通知**。用于：任务仍在进行中，仅向用户发送“正在查询”、“已调用工具”等进度提示，**不等待用户回复**，流程自动继续。可与TOOL_CALL/RAG_RETRIEVE/LLM_GENERATE并行；
            4. 需要模型二次生成/改写时使用 LLM_GENERATE（prompt为指令，非答案）；
            5. actionType仅允许：TOOL_CALL、RAG_RETRIEVE、DIRECT_RESPONSE、LLM_GENERATE，严禁其他值；
            6. 支持actions数组包含多个Action（并行执行），但一次最多5个，核心规则：
               - 仅当DIRECT_RESPONSE的isComplete=false时，可与其他非终止型Action（TOOL_CALL/RAG_RETRIEVE/LLM_GENERATE）并行；
               - 当DIRECT_RESPONSE的isComplete=true时，actions数组中**禁止包含任何其他Action**（仅保留该终止型回复）；
            7. 避免重复调用同一工具。
            
            """;
    
    /**
     * 输出格式提示词
     * 先输出思考过程，再输出JSON动作决策
     */
    private static final String JSON_OUTPUT_FORMAT_PROMPT = """
            ## 输出格式强制约束（必须100%遵守，任何违规都会导致输出无效）
            1. 输出内容必须是**纯合法JSON对象**，无任何非JSON文本（如<thinking>标签、注释、说明文字等）；
            2. JSON对象包含2个必填顶级字段：
               - thinking：字符串类型，填写你的逻辑推演过程（需清晰说明“为什么选择该动作类型”“参数如何确定”等）；
               - actions：数组类型，仅包含1个动作指令对象（单轮仅输出1个动作）；
            3. actions数组中的每个动作对象必须包含以下基础字段：
               - actionType：字符串类型，仅允许取值【TOOL_CALL/RAG_RETRIEVE/LLM_GENERATE/DIRECT_RESPONSE】，严禁使用其他值；
               - actionName：字符串类型，填写动作名称（如search_weather/retrieve_knowledge/generate_content/reply_user）；
               - reasoning：字符串类型，填写选择该动作的简短理由（区别于thinking的详细推演）；
            4. 不同actionType需额外包含对应必填参数字段（缺失会判定为无效）：
            
            ### 各actionType参数规范（必填+可选）
            #### (A) TOOL_CALL（调用工具）
            当actionType=TOOL_CALL时，必须包含toolCallParams字段，结构如下：
            "toolCallParams": {
              "toolName": "工具名称(String, 必填)", // 如ResourceCenter-20221201-SearchResources
              "toolParams": "工具具体参数(JSON字符串, 必填)" // 工具具体参数对象(Map, 必填)
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
            
            ### (D) DIRECT_RESPONSE（直接回复）
               "directResponseParams": {
                 "content": "回复内容(String, 必填)", // 给用户的回复文本
                 "isComplete": "是否终止流程(Boolean,必填)", // true=终止，false=继续，如果你觉得任务已经解决或者需要用户新的输入才能解决，此时填true
               }
            ## JSON Schema（供你校验输出，必须完全匹配）
            {
              "type": "object",
              "properties": {
                "thinking": {
                  "type": "string",
                  "description": "详细的逻辑推演过程"
                },
                "actions": {
                  "type": "array",
                  "minItems": 1,
                  "maxItems": 1,
                  "items": {
                    "type": "object",
                    "properties": {
                      "actionType": {
                        "type": "string",
                        "enum": ["TOOL_CALL", "RAG_RETRIEVE", "LLM_GENERATE", "DIRECT_RESPONSE"]
                      },
                      "actionName": {
                        "type": "string"
                      },
                      "reasoning": {
                        "type": "string"
                      },
                      "toolCallParams": {
                        "type": "object",
                        "properties": {
                          "toolName": { "type": "string" },
                          "toolParams": { "type": "string" }
                        },
                        "required": ["toolName", "toolParams"]
                      },
                      "ragRetrieveParams": {
                        "type": "object",
                        "properties": {
                          "query": { "type": "string" },
                          "knowledgeIds": { "type": "array", "items": { "type": "string" } },
                          "maxResults": { "type": "integer" },
                          "similarityThreshold": { "type": "number" }
                        },
                        "required": ["query"]
                      },
                      "llmGenerateParams": {
                        "type": "object",
                        "properties": {
                          "prompt": { "type": "string" },
                          "systemPrompt": { "type": "string" },
                          "temperature": { "type": "number" }
                        },
                        "required": ["prompt"]
                      },
                      "directResponseParams": {
                        "type": "object",
                        "properties": {
                          "content": { "type": "string" },
                          "isComplete": { "type": "boolean" }
                        },
                        "required": ["content"]
                      }
                    },
                    "required": ["actionType", "actionName", "reasoning"],
                    "additionalProperties": false,
                    "allOf": [
                      {
                        "if": { "properties": { "actionType": { "const": "TOOL_CALL" } }, "required": ["actionType"] },
                        "then": { "required": ["toolCallParams"] }
                      },
                      {
                        "if": { "properties": { "actionType": { "const": "RAG_RETRIEVE" } }, "required": ["actionType"] },
                        "then": { "required": ["ragRetrieveParams"] }
                      },
                      {
                        "if": { "properties": { "actionType": { "const": "LLM_GENERATE" } }, "required": ["actionType"] },
                        "then": { "required": ["llmGenerateParams"] }
                      },
                      {
                        "if": { "properties": { "actionType": { "const": "DIRECT_RESPONSE" } }, "required": ["actionType"] },
                        "then": { "required": ["directResponseParams"] }
                      }
                    ]
                  }
                }
              },
              "required": ["thinking", "actions"],
              "additionalProperties": false
            }
            
            ## 输出示例（供你参考，需模仿结构但替换为实际内容）
            ### 示例1：DIRECT_RESPONSE
            {
              "thinking": "用户询问阿里云服务器资源，已通过ListResourceTypes工具获取完整的资源类型列表，无需调用其他工具或检索知识库，可直接整理内容回复用户。需要确保回复内容涵盖计算、网络、存储类服务器相关资源，并清晰分类。",
              "actions": [
                {
                  "actionType": "DIRECT_RESPONSE",
                  "actionName": "reply_aliyun_server_resources",
                  "reasoning": "已获取资源类型列表，直接整理回复",
                  "directResponseParams": {
                    "content": "阿里云服务器相关资源主要分为三类：1. 计算类：ACS::ECS::Instance（ECS实例）、ACS::ECI::ContainerGroup（弹性容器实例）；2. 网络类：ACS::SLB::LoadBalancer（负载均衡）、ACS::VPC::VPC（虚拟私有云）；3. 存储类：ACS::RDS::DBInstance（关系型数据库）、ACS::OSS::Bucket（对象存储）。",
                    "isComplete": true
                  }
                }
              ]
            }
            
            ### 示例2：TOOL_CALL
            {
              "thinking": "用户需要查询阿里云ECS资源数量，首先需要调用ResourceCenter-20221201-GetResourceCounts工具，过滤条件设置为ResourceType=ECS，MatchType=Equals，确保参数格式符合工具要求。",
              "actions": [
                {
                  "actionType": "TOOL_CALL",
                  "actionName": "get_ecs_resource_counts",
                  "reasoning": "需要调用工具查询ECS资源数量",
                  "toolCallParams": {
                    "toolName": "ResourceCenter-20221201-GetResourceCounts",
                    "toolParams": "{"Filter": [{"Key”:”ResourceType”,”Value”:”ECS”,”MatchType”:”Equals”}],”MaxResults\": 20}"
                  }
                }
              ]
            }
            
            ### 示例3：DIRECT_RESPONSE（需要用户补充信息）
            {
              "thinking": "用户想要查询天气，但未提供具体城市。当前无法调用天气工具，必须先向用户询问城市名称。这是一个需要用户输入的阻断性步骤。",
              "actions": [
                {
                  "actionType": "DIRECT_RESPONSE",
                  "actionName": "ask_city_location",
                  "reasoning": "缺少城市信息，需询问用户",
                  "directResponseParams": {
                    "content": "请问您想查询哪个城市的天气？",
                    "isComplete": true
                  }
                }
              ]
            }
            
            ## 最终要求
            1. 输出前必须对照上述Schema和参数规范自检，确保无字段缺失、类型错误、枚举值违规；
            2. 禁止添加任何额外字段（additionalProperties=false）；
            3. 确保JSON语法合法（无多余逗号、引号闭合、字段名双引号等）；
            4. thinking字段需详细、逻辑完整，reasoning字段需简洁明了。
            """;

    /**
     * 思考：分析目标、上下文和历史结果，决定下一步Action（支持返回多个Action）
     */
    @Override
    public List<AgentAction> think(String goal, AgentContext context, List<ActionResult> lastResults) {
        log.info("开始思考，目标: {}, 上次结果数量: {}", goal, lastResults != null ? lastResults.size() : 0);
        
        // 发送思考进度事件
        sendProgressEvent(context, AgentConstants.EVENT_STATUS_ANALYZING, "正在分析任务和用户意图...");
        String retryHint = "";
        List<AgentAction> finalActions = new ArrayList<>();
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            //构建提示词
            PromptPair promptPair = buildThinkingPrompt(goal,context,lastResults,retryHint);
            log.info("系统提示词:{}\n,用户提示词:{}",promptPair.getSystemPrompt(), promptPair.getUserPrompt());

            // 构造历史对话
            List<ChatMessage> messages = new ArrayList<>();
            ThinkingConfig cfg = context.getThinkingConfig();
            int historyLimit = (cfg != null) ? cfg.getHistoryMessageLoadLimitOrDefault() : 10;
            if (context.getMessages() != null && !context.getMessages().isEmpty()) {
                List<ChatMessage> history = context.getMessages();
                int start = Math.max(0, history.size() - historyLimit);
                for (int i = start; i < history.size(); i++) {
                    messages.add(history.get(i));
                }
            }
            log.info("历史对话:{}",messages);
            messages.add(new SystemMessage(promptPair.getSystemPrompt()));
            messages.add(new UserMessage(promptPair.getUserPrompt()));

            String modelId =StringUtils.isEmpty(context.getModelId()) ? agentConfig.getLlm().getDefaultModel(): context.getModelId();

            String fullText = llmChatHandler.chatWithResponseFormat(modelId,
                    messages,  buildStructuredResponseFormat(), createStructuredStreamingCallback(context));
            log.info("ai回复：{}", fullText);
            try {
                finalActions = parseThinkingResult(fullText, goal, context);
                break;
            }catch (LLMParseException e){
                retryHint=e.getMessage();
                log.warn("输出不合规，进行重试，输出:{}",fullText);
                Map<String, Object> data = new java.util.HashMap<>();
                    data.put("attempt", attempt + 1);
                    data.put("violations", "LLM parse failed");
                    data.put("modelId", modelId);
                sendStatusEvent(context, AgentConstants.EVENT_STATUS_RETRYING, "输出不合规，进行重试", data);
            }
        }
        if (finalActions.isEmpty()) {
            log.warn("思考阶段未产生Action");
            return new ArrayList<>();
        }
        if (finalActions.size() > 5) {
            log.warn("Action数量超过限制（{}），只保留前5个", finalActions.size());
            finalActions = finalActions.subList(0, 5);
        }
        log.info("思考完成，决定执行 {} 个Action: {}", finalActions.size(),
                finalActions.stream().map(AgentAction::getName).collect(Collectors.joining(", ")));
        sendDecidedActionsEvent(context, finalActions);
        return finalActions;
    }
    
    /**
     * 构建思考提示词（分离系统提示词和用户提示词）
     */
    private PromptPair buildThinkingPrompt(String goal, AgentContext context, List<ActionResult> lastResults) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(goal, context);
        return new PromptPair(systemPrompt, userPrompt);
    }

    private PromptPair buildThinkingPrompt(String goal, AgentContext context, List<ActionResult> lastResults, String retryHint) {
        String systemPrompt = buildSystemPrompt(retryHint);
        String userPrompt = buildUserPrompt(goal, context, retryHint);
        return new PromptPair(systemPrompt, userPrompt);
    }

    /**
     * 构建系统提示词（静态规则和约束）
     */
    private String buildSystemPrompt() {
        return "你是一个智能Agent的思考与决策模块，需基于用户需求输出包含思考过程和动作指令的结构化JSON内容。\n"+
               DECISION_FRAMEWORK_PROMPT+
                JSON_OUTPUT_FORMAT_PROMPT;
    }

    private String buildSystemPrompt(String retryHint) {
        String base = buildSystemPrompt();
        if (StringUtils.isNotEmpty(retryHint)) {
            StringBuilder sb = new StringBuilder(base);
            sb.append("\n\n");
            sb.append(retryHint);
            return sb.toString();
        }
        return base;
    }

    /**
     * 构建用户提示词（动态上下文信息）
     * 使用配置参数控制历史长度和截断
     */
    private String buildUserPrompt(String goal, AgentContext context) {
        StringBuilder prompt = new StringBuilder();
        
        // 获取配置
        ThinkingConfig config = context.getThinkingConfig();
        if (config == null) {
            config = ThinkingConfig.builder().build();
        }
        

        // ========== 可用工具 ==========
        List<McpToolInfo> availableTools = toolSelector.selectTools(goal,
                context.getEnabledMcpGroups(),
                context.getEnabledTools());
        if (!availableTools.isEmpty()) {
            prompt.append("## 可用工具\n\n");
            for (McpToolInfo tool : availableTools) {
                prompt.append(formatToolDefinition(tool));
                prompt.append("\n");
            }
            prompt.append("\n");
        }

        // ========== RAG 知识库信息 ==========
        appendRAGInfo(prompt, context);

        // ========== Action执行历史（按迭代轮次展示）==========
        if (context.getActionExecutionHistory() != null && !context.getActionExecutionHistory().isEmpty()) {
            int totalIterations = context.getActionExecutionHistory().size();

            Integer showIterations = config.getActionExecutionHistoryCount();
            int start = showIterations==null?0:totalIterations-showIterations;
            start=Math.max(start,0);

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
        prompt.append("## 当前目标：\n\n");
        prompt.append(goal).append("\n\n");
        return prompt.toString();
    }

    private String buildUserPrompt(String goal, AgentContext context, String retryHint) {
        String base = buildUserPrompt(goal, context);
        if (StringUtils.isNotEmpty(retryHint)) {
            StringBuilder sb = new StringBuilder(base);
            sb.append("## 格式纠正提醒\n\n");
            sb.append(retryHint);
            sb.append("\n");
            return sb.toString();
        }
        return base;
    }
    
    // ... (其他辅助方法保持不变，但为了节省token，我在这里省略一些未改变的私有方法，
    // 实际写入时必须包含所有方法。为了保证正确性，我将完整写入所有方法。)
    // 由于字数限制，我将继续写入剩余方法。
    
    // 注意：上面的 write 操作可能因为内容太长而被截断。
    // 我需要分块写入或者确保一次写完。鉴于这是一个新文件，我必须一次写完。
    // 为了保险，我将只包含必要的方法，并确保所有逻辑完整。

    /**
     * 解析思考结果，生成Action列表
     * 统一使用 actions 数组格式，直接使用类反序列化
     */
    private List<AgentAction> parseThinkingResult(String thinkingResult, String goal, AgentContext context) {
        // 1. 尝试解析为 ActionsResponseDTO
        ActionsResponseDTO response = tryParseActionsResponse(thinkingResult);
        if (response.hasActions()) {
            List<AgentAction> actions = buildAgentActions(response.getActions(), context);
            if (!actions.isEmpty()) {
                return actions;
            }
        }
        return new ArrayList<>();
    }

    // ... tryParseActionsResponse, cleanJsonResponse, removeControlChars, extractFirstJsonObject, buildAgentActions, buildAgentAction 等方法
    // 这些方法是 PromptGuidedThinkingEngine 特有的解析逻辑，必须保留。
    
    // (由于篇幅原因，我这里直接使用原始代码中的辅助方法)
    
    private ActionsResponseDTO tryParseActionsResponse(String text) {
        try {
            String cleaned = cleanJsonResponse(text);
            log.debug("清理后的思考结果: {}", cleaned);
            
            // 1. 先尝试解析为 JSONObject，进行兼容性处理
            JSONObject jsonObject = JSON.parseObject(cleaned);
            if (jsonObject != null && jsonObject.containsKey("actions")) {
                JSONArray actions = jsonObject.getJSONArray("actions");
                if (actions != null) {
                    for (Object item : actions) {
                        if (item instanceof JSONObject) {
                            JSONObject action = (JSONObject) item;

                            // 兼容 action -> actionType
                            if (action.containsKey("action") && !action.containsKey("actionType")) {
                                action.put("actionType", action.getString("action"));
                            }

                            // 兼容 params -> xxxParams
                            if (action.containsKey("params")) {
                                String type = action.getString("actionType");
                                if (StringUtils.isNotEmpty(type)) {
                                    String targetParamKey = null;
                                    // 统一转大写比较
                                    switch (type.toUpperCase()) {
                                        case "DIRECT_RESPONSE":
                                            targetParamKey = "directResponseParams";
                                            break;
                                        case "TOOL_CALL":
                                            targetParamKey = "toolCallParams";
                                            break;
                                        case "RAG_RETRIEVE":
                                            targetParamKey = "ragRetrieveParams";
                                            break;
                                        case "LLM_GENERATE":
                                            targetParamKey = "llmGenerateParams";
                                            break;
                                    }

                                    if (targetParamKey != null && !action.containsKey(targetParamKey)) {
                                        action.put(targetParamKey, action.get("params"));
                                    }
                                }
                            }
                        }
                    }
                }
                // 重新转换为 DTO
                return jsonObject.to(ActionsResponseDTO.class);
            }
            
            return JSON.parseObject(cleaned, ActionsResponseDTO.class);
        } catch (Exception e) {
            try {
                String normalized = removeControlChars(text);
                String extracted = extractFirstJsonObject(normalized);
                String fallback = StringUtils.isNotEmpty(extracted) ? extracted : cleanJsonResponse(normalized);
                if (StringUtils.isNotEmpty(fallback)) {
                    log.info("清理后的思考结果(兜底): {}", fallback);
                    return JSON.parseObject(fallback, ActionsResponseDTO.class);
                }
            } catch (Exception ignored) {

            }
            String errMsg = String.format("上次thinking内容：%s,解析格式错误，请严格按照以下格式解析:%s", text, JSON_OUTPUT_FORMAT_PROMPT);
            throw new LLMParseException(1,errMsg);
        }
    }
    
    private List<AgentAction> buildAgentActions(List<ActionInputDTO> actionDTOs, AgentContext context) {
        return actionDTOs.stream()
            .map(dto -> buildAgentAction(dto, context))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    private AgentAction buildAgentAction(ActionInputDTO dto, AgentContext context) {
        if (StringUtils.isEmpty(dto.getActionType())) {
            log.warn("Action缺少actionType");
            throw new LLMParseException(ParseErrCode.ACTION_TYPE_INVALID);
        }
        
        ActionType type;
        try {
            type = ActionType.valueOf(dto.getActionType());
        } catch (IllegalArgumentException e) {
            log.warn("无效的Action类型: {}", dto.getActionType());
            throw new LLMParseException(ParseErrCode.ACTION_TYPE_INVALID);
        }
        
        String actionName = dto.getActionName();
        
        AgentAction action = null;
        switch (type) {
            case TOOL_CALL:
                action = buildToolCallAction(dto);
                break;
            case RAG_RETRIEVE:
                action = buildRAGRetrieveAction(dto, context);
                break;
            case LLM_GENERATE:
                action = buildLLMGenerateAction(dto);
                break;
            case DIRECT_RESPONSE:
                action = buildDirectResponseAction(dto);
                break;
            default:
                throw new LLMParseException(ParseErrCode.ACTION_TYPE_INVALID);
        }
        
        // 设置 actionName（如果为空）
        if (action != null && StringUtils.isEmpty(action.getName())) {
            action.setName(actionName != null ? actionName : type.name().toLowerCase());
        }
        
        return action;
    }
    
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

        cleaned = removeControlChars(cleaned);

        String extracted = extractFirstJsonObject(cleaned);
        if (StringUtils.isNotEmpty(extracted)) {
            return extracted;
        }
        
        // 如果文本中包含JSON对象（以{开头，以}结尾），提取它
        int jsonStart = cleaned.indexOf('{');
        int jsonEnd = cleaned.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            cleaned = cleaned.substring(jsonStart, jsonEnd + 1);
        }

        return cleaned;
    }

    private String removeControlChars(String input) {
        if (StringUtils.isEmpty(input)) {
            return "";
        }
        return input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    private String extractFirstJsonObject(String input) {
        if (StringUtils.isEmpty(input)) {
            return "";
        }
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (inString) {
                continue;
            }
            
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        return input.substring(start, i + 1);
                    }
                }
            }
        }
        
        if (depth > 0 && start >= 0) {
            log.warn("检测到 JSON 截断，尝试自动补全。depth: {}, content: {}", depth, input.substring(start));
            StringBuilder sb = new StringBuilder(input.substring(start));
            if (inString) {
                sb.append('"');
            }
            for (int k = 0; k < depth; k++) {
                sb.append('}');
            }
            return sb.toString();
        }
        
        return "";
    }

    private static class StructuredStreamState {
        StringBuilder fullText = new StringBuilder();
        int lastThinkingSentLength;
    }

    private ResponseFormat buildStructuredResponseFormat() {
        // 1. 先定义 actionType 允许的枚举值
        JsonObjectSchema toolCallParams = JsonObjectSchema.builder()
            .addStringProperty("toolName")
            .addStringProperty("toolParams")
            .required("toolName", "toolParams")
            .build();
        JsonObjectSchema ragRetrieveParams = JsonObjectSchema.builder()
            .addStringProperty("query")
            .addProperty("knowledgeIds", JsonArraySchema.builder().items(JsonStringSchema.builder().build()).build())
            .addIntegerProperty("maxResults")
            .addNumberProperty("similarityThreshold")
            .required("query")
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
            .required("content")
            .build();
        JsonObjectSchema actionItem = JsonObjectSchema.builder()
                .addEnumProperty("actionType", ActionType.getActionTypeEnums())
            .addStringProperty("actionName")
            .addStringProperty("reasoning")
            .addProperty("toolCallParams", toolCallParams)
            .addProperty("ragRetrieveParams", ragRetrieveParams)
            .addProperty("llmGenerateParams", llmGenerateParams)
            .addProperty("directResponseParams", directResponseParams)
            .required("actionType", "actionName", "reasoning")
            .build();
        JsonArraySchema actionsArray = JsonArraySchema.builder()
            .items(actionItem)
            .build();
        JsonObjectSchema root = JsonObjectSchema.builder()
            .addStringProperty("thinking")
            .addProperty("actions", actionsArray)
            .required("thinking", "actions")
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

    private StreamingCallback createStructuredStreamingCallback(AgentContext context) {
        return new StreamingCallback() {
            private final StructuredStreamState state = new StructuredStreamState();
            @Override
            public void onToken(String token) {
                if (StringUtils.isEmpty(token)) {
                    return;
                }
                state.fullText.append(token);
                String delta = handleStructuredStream(state, context, false);
                if (StringUtils.isNotEmpty(delta)) {
                    sendThinkingDeltaEvent(context, delta);
                }
            }
            @Override
            public void onComplete(String fullText) {
                if (!StringUtils.isEmpty(fullText) && state.fullText.isEmpty()) {
                    state.fullText.append(fullText);
                }
                String delta = handleStructuredStream(state, context, true);
                if (StringUtils.isNotEmpty(delta)) {
                    sendThinkingDeltaEvent(context, delta);
                }
            }
            @Override
            public void onError(Throwable error) {
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_ERROR, "思考阶段发生错误: " + error.getMessage());
            }
        };
    }

    private String handleStructuredStream(StructuredStreamState state, AgentContext context, boolean isComplete) {
        String text = state.fullText.toString();
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        int keyIdx = text.indexOf("\"thinking\"");
        if (keyIdx != -1) {
            int colonIdx = text.indexOf(":", keyIdx);
            if (colonIdx != -1) {
                int startQuote = text.indexOf("\"", colonIdx + 1);
                if (startQuote != -1) {
                    int i = startQuote + 1;
                    boolean esc = false;
                    int closing = -1;
                    for (; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (esc) {
                            esc = false;
                        } else if (c == '\\') {
                            esc = true;
                        } else if (c == '"') {
                            closing = i;
                            break;
                        }
                    }
                    int logicalEnd = closing != -1 ? closing : text.length();
                    if (startQuote + 1 <= logicalEnd) {
                        String current = text.substring(startQuote + 1, logicalEnd);
                        int prevLen = Math.min(state.lastThinkingSentLength, current.length());
                        String delta = current.substring(prevLen);
                        if (StringUtils.isNotEmpty(delta)) {
                            state.lastThinkingSentLength = current.length();
                            sendProgressEvent(context, AgentConstants.EVENT_STATUS_THINKING_PROCESS, "正在进行深入思考...");
                            return delta;
                        }
                    }
                }
            }
        }
        if (text.contains("\"actions\"")) {
            sendProgressEvent(context, AgentConstants.EVENT_STATUS_PLANNING, "思考完成，正在生成动作计划...");
        }
        return "";
    }

    private AgentAction buildToolCallAction(ActionInputDTO dto) {
        ToolCallParams params = dto.getToolCallParams();
        if (params == null) {
            log.warn("TOOL_CALLAction缺少toolCallParams");
            throw new LLMParseException(ParseErrCode.TOOL_CALL_PARAMS_MISSING);
        }
        
        String toolName = params.getToolName();
        if (StringUtils.isEmpty(toolName)) {
            toolName = dto.getActionName();
        }
        if (StringUtils.isEmpty(toolName)) {
            log.warn("TOOL_CALLAction缺少工具名称");
            throw new LLMParseException(ParseErrCode.TOOL_NAME_MISSING);
        }
        
        if (StringUtils.isEmpty(params.getToolName())) {
            params.setToolName(toolName);
        }

        // Check if tool exists
        McpToolInfo toolInfo = toolSelector.getToolByName(toolName);
        if (toolInfo == null) {
            log.warn("Thinking engine generated invalid tool: {}", toolName);
            throw new LLMParseException(ParseErrCode.TOOL_NOT_FOUND.getCode(), "Tool not found: " + toolName + ". Please check available tools.");
        }
        
        return AgentAction.toolCall(toolName, params);
    }
    
    private AgentAction buildRAGRetrieveAction(ActionInputDTO dto, AgentContext context) {
        RAGRetrieveParams params = dto.getRagRetrieveParams();
        if (params == null) {
            log.warn("RAG_RETRIEVEAction缺少ragRetrieveParams");
            throw new LLMParseException(ParseErrCode.RAG_PARAMS_MISSING);
        }
        
        if (StringUtils.isEmpty(params.getQuery())) {
            log.warn("RAG_RETRIEVEAction缺少query");
            throw new LLMParseException(ParseErrCode.RAG_QUERY_MISSING);
        }
        
        if (params.getKnowledgeIds() == null || params.getKnowledgeIds().isEmpty()) {
            if (context != null && context.getKnowledgeIds() != null) {
                params.setKnowledgeIds(context.getKnowledgeIds());
                log.debug("从上下文获取knowledgeIds: {}", params.getKnowledgeIds());
            } else {
                params.setKnowledgeIds(new ArrayList<>());
            }
        }
        
        return AgentAction.ragRetrieve(params);
    }
    
    private AgentAction buildDirectResponseAction(ActionInputDTO dto) {
        DirectResponseParams params = dto.getDirectResponseParams();
        if (params == null) {
            log.warn("DIRECT_RESPONSEAction缺少directResponseParams");
            throw new LLMParseException(ParseErrCode.DIRECT_PARAMS_MISSING);
        }

        if (StringUtils.isEmpty(params.getContent())) {
            log.warn("DIRECT_RESPONSEAction缺少content");
            throw new LLMParseException(ParseErrCode.DIRECT_CONTENT_MISSING);
        }
        if (StringUtils.isEmpty(params.getIsComplete())) {
            log.warn("DIRECT_RESPONSEAction缺少ssComplete");
            throw new LLMParseException(ParseErrCode.DIRECT_IS_COMPLETE_MISSING);
        }

        return AgentAction.directResponse(params);
    }
    
    private AgentAction buildLLMGenerateAction(ActionInputDTO dto) {
        LLMGenerateParams params = dto.getLlmGenerateParams();
        if (params == null) {
            log.warn("LLM_GENERATEAction缺少llmGenerateParams");
            throw new LLMParseException(ParseErrCode.LLM_PARAMS_MISSING);
        }
        
        if (StringUtils.isEmpty(params.getPrompt())) {
            log.warn("LLM_GENERATEAction缺少prompt");
            throw new LLMParseException(ParseErrCode.LLM_PROMPT_MISSING);
        }
        
        return AgentAction.llmGenerate(params);
    }

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
    
    private void sendStatusEvent(AgentContext context, String event, String message, java.util.Map<String, Object> data) {
        if (context != null && context.getEventPublisher() != null) {
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(event)
                    .message(message)
                    .data(data)
                    .build()
            );
        }
    }

    private void appendRAGInfo(StringBuilder prompt, AgentContext context) {
        appendAvailableKnowledgeBases(prompt, context);
        appendRetrievedKnowledgeInfo(prompt, context);
    }
    
    private void appendAvailableKnowledgeBases(StringBuilder prompt, AgentContext context) {
        List<String> knowledgeIds = context.getKnowledgeIds();
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return;
        }
        
        prompt.append("## 可用知识库\n\n");
        prompt.append("以下知识库可用于 RAG_RETRIEVE 检索：\n\n");
        
        Map<String, KnowledgeBase> knowledgeBaseMap = context.getKnowledgeBaseMap();
        if (knowledgeBaseMap == null) {
            knowledgeBaseMap = new HashMap<>();
        }
        
        int index = 1;
        for (String knowledgeId : knowledgeIds) {
            KnowledgeBase knowledgeBase = knowledgeBaseMap.get(knowledgeId);
            if (knowledgeBase != null) {
                prompt.append(index).append(". ").append(knowledgeBase.getName());
                prompt.append(" (ID: ").append(knowledgeId).append(")");
                
                if (StringUtils.isNotEmpty(knowledgeBase.getDescription())) {
                    prompt.append("\n   描述: ").append(knowledgeBase.getDescription());
                }
                prompt.append("\n\n");
            } else {
                prompt.append(index).append(". 知识库 (ID: ").append(knowledgeId).append(")\n\n");
            }
            index++;
        }
    }
    
    private void appendRetrievedKnowledgeInfo(StringBuilder prompt, AgentContext context) {
        AgentKnowledgeResult knowledgeResult = context.getInitialRagResult();
        if (knowledgeResult == null || knowledgeResult.isEmpty()) {
            return;
        }
        
        prompt.append("## 已检索的知识库信息\n\n");
        prompt.append("以下是从知识库中已检索到的相关信息：\n\n");
        
        if (StringUtils.isNotEmpty(knowledgeResult.getQuery())) {
            prompt.append("- 查询词: \"").append(knowledgeResult.getQuery()).append("\"\n");
        }
        
        int totalCount = knowledgeResult.getTotalCount() != null ? knowledgeResult.getTotalCount() : 0;
        prompt.append("- 检索结果: 找到 ").append(totalCount).append(" 条相关文档\n");
        
        if (knowledgeResult.getAvgScore() != null) {
            double avgScorePercent = knowledgeResult.getAvgScore() * 100;
            prompt.append("- 平均相关度: ").append(String.format("%.1f%%", avgScorePercent)).append("\n");
        }
        
        prompt.append("\n");
        
        List<AgentKnowledgeDocument> documents = knowledgeResult.getDocuments();
        if (documents != null && !documents.isEmpty()) {
            prompt.append("- 相关文档:\n\n");
            
            Map<String, KnowledgeBase> knowledgeBaseMap = context.getKnowledgeBaseMap();
            if (knowledgeBaseMap == null) {
                knowledgeBaseMap = new HashMap<>();
            }
            
            Map<String, String> knowledgeBaseNameMap = new HashMap<>();
            for (AgentKnowledgeDocument doc : documents) {
                if (doc.getKnowledgeId() != null && !knowledgeBaseNameMap.containsKey(doc.getKnowledgeId())) {
                    KnowledgeBase kb = knowledgeBaseMap.get(doc.getKnowledgeId());
                    if (kb != null) {
                        knowledgeBaseNameMap.put(doc.getKnowledgeId(), kb.getName());
                    }
                }
            }
            
            for (int i = 0; i < documents.size(); i++) {
                AgentKnowledgeDocument doc = documents.get(i);
                prompt.append("  ").append(i + 1).append(". ");
                
                if (StringUtils.isNotEmpty(doc.getDocName())) {
                    prompt.append(doc.getDocName());
                } else {
                    prompt.append("文档");
                }
                
                if (doc.getScore() != null) {
                    double scorePercent = doc.getScore() * 100;
                    prompt.append(" - 相关度: ").append(String.format("%.1f%%", scorePercent));
                }
                
                prompt.append("\n");
                
                if (doc.getKnowledgeId() != null) {
                    String kbName = knowledgeBaseNameMap.get(doc.getKnowledgeId());
                    if (kbName != null) {
                        prompt.append("     来源知识库: ").append(kbName);
                    } else {
                        prompt.append("     来源知识库: (ID: ").append(doc.getKnowledgeId()).append(")");
                    }
                    prompt.append("\n");
                }
                
                if (StringUtils.isNotEmpty(doc.getContent())) {
                    prompt.append("     ").append(doc.getContent()).append("\n");
                }
                
                prompt.append("\n");
            }
        }
        
        prompt.append("\n");
    }

    private String formatToolDefinition(McpToolInfo tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(tool.getName());

        if (StringUtils.isNotEmpty(tool.getDescription())) {
            sb.append(": ").append(tool.getDescription());
        }
        sb.append("\n");
        try {
            sb.append("params:").append(OBJECT_MAPPER.writeValueAsString(tool.getParameters()));
            if (tool.getMetadata() != null && !tool.getMetadata().isEmpty()) {
                sb.append("\nmetadata:").append(OBJECT_MAPPER.writeValueAsString(tool.getMetadata()));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize tool definition for {}", tool.getName(), e);
            sb.append("params:").append(tool.getParameters());
            if (tool.getMetadata() != null) {
                sb.append("\nmetadata:").append(tool.getMetadata());
            }
        }

        return sb.toString();
    }
    
    private void sendThinkingDeltaEvent(AgentContext context, String content) {
        if (context != null && context.getEventPublisher() != null) {
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(AgentConstants.EVENT_AGENT_THINKING_DELTA)
                    .content(content)
                    .build()
            );
        }
    }
    
    private void sendDecidedActionsEvent(AgentContext context, List<AgentAction> actions) {
        if (context == null || context.getEventPublisher() == null || actions == null || actions.isEmpty()) {
            return;
        }
        String names = actions.stream().map(AgentAction::getName).collect(Collectors.joining(", "));
        
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("actionNames", names);
        
        context.getEventPublisher().accept(
            AgentEventData.builder()
                .event(AgentConstants.EVENT_STATUS_THINKING_PROCESS)
                .message("正在进行深入思考...") 
                .data(data)
                .build()
        );
    }
}
