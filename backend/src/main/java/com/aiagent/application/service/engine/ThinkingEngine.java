package com.aiagent.application.service.engine;

import com.aiagent.api.dto.ThinkingConfig;
import com.aiagent.application.model.AgentKnowledgeDocument;
import com.aiagent.application.model.AgentKnowledgeResult;
import com.aiagent.application.service.action.ActionInputDTO;
import com.aiagent.application.service.action.ActionResult;
import com.aiagent.application.service.action.ActionsResponseDTO;
import com.aiagent.application.service.action.AgentAction;
import com.aiagent.domain.enums.ActionType;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
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
    private static final String DECISION_FRAMEWORK_PROMPT = """
            ## 决策要求
            1. 先判断已有信息是否足够回答用户问题
            2. 如果你觉得解决该问题需要调用工具才需要 TOOL_CALL
            3. 需要知识库资料时选 RAG_RETRIEVE
            4. 如果你已经可以直接给出完整答案，必须使用 DIRECT_RESPONSE，把最终回复放在 content
            5. 只有在需要让模型二次生成或改写时才用 LLM_GENERATE（prompt 应是指令，不是答案）
            6. actionType只有TOOL_CALL、RAG_RETRIEVE、DIRECT_RESPONSE、LLM_GENERATE四种类型，严禁输出其他类型
            7. 每轮思考必须输出至少一个Action，如果你觉得需要用户输入或者已经可以回答问题，请使用DIRECT_RESPONSE，把提问/回复放在 content
            8. 避免重复调用同一工具
            
            """;
    
    /**
     * 输出格式提示词
     * 先输出思考过程，再输出JSON动作决策
     */
//    private static final String OUTPUT_FORMAT_PROMPT = """
//            ## 输出协议与格式规范
//
//            你必须严格遵守以下"三段式"输出协议：
//
//            ### 第一阶段：深度思考 (Thinking)
//            在做出决定前，必须先进行逻辑推演。
//            格式要求：
//            <thinking>
//            1. 分析用户意图...
//            2. 评估当前可用信息...
//            3. 规划后续步骤...
//            </thinking>
//
//            ### 第二阶段：思考截断 (Checkpoint)
//            思考结束后，必须输出唯一的结束标记：
//            <THINKING_DONE>
//
//            ### 第三阶段：动作执行 (Execution)
//            在输出 `<THINKING_DONE>` 后，**立即**输出最终的动作指令 JSON，不要包含任何其他解释性文字。
//
//            **⚠️ 严正警告 (CRITICAL WARNING)**：
//            1. `<actions>` 标签内部 **只能** 包含一个标准的 JSON 对象。
//            2. **严禁** 在 JSON 前后添加任何解释性文字（如 "好的，这是执行计划..."）。
//            3. **严禁** 使用 Markdown 代码块标记（如 ```json ... ```）。
//            4. 如果你想直接回复用户，请使用 `DIRECT_RESPONSE` 动作，将回复内容放在 `content` 字段中，而不是直接输出文本。
//
//            格式要求：
//            <actions>
//            {
//              "actions": [
//                {
//                  "actionType": "...",
//                  "actionName": "...",
//                  "reasoning": "...",
//                  "...Params": { ... }
//                }
//              ]
//            }
//            </actions>
//
//            ---
//
//            ### 核心校验规则 (Critical Rules)
//
//            1. **JSON 严格语法**
//               - `<actions>` 内部必须是纯粹的 JSON 文本，**严禁**包含 markdown 代码块标记（如 ```json）。
//               - 务必校验 `{}` 和 `[]` 的闭合性。
//               - 所有字段名必须使用双引号。
//
//            2. **参数结构规范 (Schema Definitions)**
//               每个 `actionType` 对应唯一的参数对象，结构如下：
//
//               **(A) TOOL_CALL (调用工具)**
//               - 必须包含 `toolCallParams`:
//                 ```json
//                 "toolCallParams": {
//                   "toolName": "工具名称(String, 必填)",
//                   "toolParams": { "key": "value" } // 工具具体参数对象(Map, 必填)
//                 }
//                 ```
//
//               **(B) RAG_RETRIEVE (知识库检索)**
//               - 必须包含 `ragRetrieveParams`:
//                 ```json
//                 "ragRetrieveParams": {
//                   "query": "检索关键词(String, 必填)",
//                   "knowledgeIds": ["kb_id1"], // 知识库ID列表(List<String>, 可选)
//                   "maxResults": 5, // 最大结果数(Integer, 可选)
//                   "similarityThreshold": 0.7 // 相似度阈值(Double, 可选)
//                 }
//                 ```
//
//               **(C) LLM_GENERATE (大模型生成)**
//               - 必须包含 `llmGenerateParams`:
//                 ```json
//                 "llmGenerateParams": {
//                   "prompt": "提示词(String, 必填)",
//                   "systemPrompt": "系统设定(String, 可选)",
//                   "temperature": 0.7 // 温度(Double, 可选)
//                 }
//                 ```
//
//               **(D) DIRECT_RESPONSE (直接回复)**
//               - 必须包含 `directResponseParams`:
//                 ```json
//                 "directResponseParams": {
//                   "content": "回复内容(String, 必填)",
//                   "streaming": true // 是否流式(Boolean, 默认为true)
//                 }
//                 ```
//
//            3. **禁止废话**
//               在 `</thinking>` 和 `<actions>` 之间，严禁输出任何自然语言过渡句。
//            4.actionType 仅允许取值：TOOL_CALL、RAG_RETRIEVE、LLM_GENERATE、DIRECT_RESPONSE，**严禁使用任何其他自定义类型（如answer、reply等）**，否则视为无效输出。
//            5.每一轮思考至少要有一个Action，如果你觉得可以结束了，请用DIRECT_RESPONSE
//            6.输出动作指令前，必须自检：
//                 - actionType 是否为 TOOL_CALL/RAG_RETRIEVE/LLM_GENERATE/DIRECT_RESPONSE 之一；
//                 - 若为 DIRECT_RESPONSE，是否将回复内容放入 directResponseParams.content；
//                 - 若自检不通过，立即修正后再输出。
//
//            ---
//
//            ### 标准范例 (Examples)
//
//            #### 场景 1: 直接回复用户
//            <thinking>
//            用户在打招呼，无需调用工具。
//            </thinking>
//            <actions>
//            {
//              "actions": [
//                {
//                  "actionType": "DIRECT_RESPONSE",
//                  "actionName": "greet_user",
//                  "reasoning": "直接回复问候",
//                  "directResponseParams": {
//                    "content": "你好！有什么我可以帮你的吗？",
//                    "streaming": true
//                  }
//                }
//              ]
//            }
//            </actions>
//
//            #### 场景 2: 调用工具查询
//            <thinking>
//            用户想查天气，我需要使用 get_weather 工具。
//            </thinking>
//            <actions>
//            {
//              "actions": [
//                {
//                  "actionType": "TOOL_CALL",
//                  "actionName": "search_weather",
//                  "reasoning": "用户询问天气，需要调用天气工具",
//                  "toolCallParams": {
//                    "toolName": "get_weather",
//                    "toolParams": {
//                      "city": "Hangzhou"
//                    }
//                  }
//                }
//              ]
//            }
//            </actions>
//            """;


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
              "toolParams": { "key": "value" } // 工具具体参数对象(Map, 必填)
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
              "streaming": true // 是否流式(Boolean, 可选，默认true)
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
                          "toolParams": { "type": "object" }
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
                          "streaming": { "type": "boolean" }
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
                    "streaming": true
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
                    "toolParams": {
                      "Filter": [{"Key":"ResourceType","Value":"ECS","MatchType":"Equals"}],
                      "MaxResults": 20
                    }
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
            messages.add(new SystemMessage(promptPair.getSystemPrompt()));
            messages.add(new UserMessage(promptPair.getUserPrompt()));

            String modelId =StringUtils.isEmpty(context.getModelId()) ? agentConfig.getLlm().getDefaultModel(): context.getModelId();

            String fullText = llmChatHandler.chatWithResponseFormat(modelId,
                    messages,  buildStructuredResponseFormat(), createStructuredStreamingCallback(context));
            log.info("ai回复：{}", fullText);
            try {
                finalActions = parseThinkingResult(fullText, goal, context);
            }catch (LLMParseException e){
                retryHint=e.getMessage();
                log.warn("输出不合规，进行重试，输出:{}",fullText);
                Map<String, Object> data = new java.util.HashMap<>();
                    data.put("attempt", attempt + 1);
                    data.put("violations", "LLM parse failed");
                    data.put("modelId", modelId);
                sendStatusEvent(context, AgentConstants.EVENT_STATUS_RETRYING, "输出不合规，进行重试", data);
                continue;
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
            finalActions.stream().map(AgentAction::getName).collect(java.util.stream.Collectors.joining(", ")));
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
    
    private String callLLMForThinking(PromptPair promptPair, AgentContext context) {
            List<ChatMessage> messages = new ArrayList<>();
            
            // 1. 系统提示词
            messages.add(new SystemMessage(promptPair.getSystemPrompt()));
            
            // 2. 插入原生历史对话 (Native Messages)
            if (context.getMessages() != null && !context.getMessages().isEmpty()) {
                // 获取配置的历史消息加载数量限制
                ThinkingConfig config = context.getThinkingConfig();
                int historyLimit = (config != null) ? config.getHistoryMessageLoadLimitOrDefault() : 10;
                
                List<ChatMessage> history = context.getMessages();
                int start = Math.max(0, history.size() - historyLimit);
                
                // 将最近的历史消息加入到 messages 列表中
                // 注意：这里直接复用 ChatMessage 对象，保留了 User/AI 的角色信息
                for (int i = start; i < history.size(); i++) {
                    messages.add(history.get(i));
                }
                log.debug("已加载 {} 条历史对话消息", history.size() - start);
            }
            
            // 3. 当前任务上下文（包含工具、RAG结果、执行历史等）
            // 将这些作为最新的 UserMessage 发送
            messages.add(new UserMessage(promptPair.getUserPrompt()));
            
            String modelId = context != null ? context.getModelId() : null;
            if (StringUtils.isEmpty(modelId)) {
                modelId = agentConfig.getLlm().getDefaultModel();
            }
            long startNs = System.nanoTime();
            log.debug("思考LLM请求开始，modelId={}, systemPromptChars={}, userPromptChars={}", 
                modelId, 
                promptPair.getSystemPrompt().length(),
                promptPair.getUserPrompt().length());
            
            StreamingCallback callback = createThinkingStreamingCallback(context);
            String response = llmChatHandler.chatWithCallback(modelId, messages, callback);
            log.info("思考LLM请求完成，耗时 {} ms", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
            log.debug("LLM思考完整响应: {}", response);
            String actionsJson = extractActionsJson(response);
            log.debug("LLM思考提取的动作JSON: {}", actionsJson);
            return actionsJson;
    }

    private String callLLMForThinkingRaw(PromptPair promptPair, AgentContext context) {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage(promptPair.getSystemPrompt()));
            if (context.getMessages() != null && !context.getMessages().isEmpty()) {
                ThinkingConfig config = context.getThinkingConfig();
                int historyLimit = (config != null) ? config.getHistoryMessageLoadLimitOrDefault() : 10;
                List<ChatMessage> history = context.getMessages();
                int start = Math.max(0, history.size() - historyLimit);
                for (int i = start; i < history.size(); i++) {
                    messages.add(history.get(i));
                }
            }
            messages.add(new UserMessage(promptPair.getUserPrompt()));
            String modelId = context != null ? context.getModelId() : null;
            if (StringUtils.isEmpty(modelId)) {
                modelId = agentConfig.getLlm().getDefaultModel();
            }
            long startNs = System.nanoTime();
            StreamingCallback callback = createThinkingStreamingCallback(context);
            String response = llmChatHandler.chatWithCallback(modelId, messages, callback);
            log.info("思考LLM请求完成，耗时 {} ms", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
            log.debug("LLM思考完整响应: {}", response);
            return response;
    }

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

    private List<String> validateThinkingOutput(String fullResponse, String actionsJson) {
        List<String> violations = new ArrayList<>();
        if (StringUtils.isEmpty(fullResponse)) {
            violations.add("缺少整体响应");
            return violations;
        }
        String text = removeControlChars(fullResponse);
        int tStart = text.indexOf("<thinking>");
        int tEnd = text.indexOf("</thinking>");
        int doneIdx = text.indexOf("<THINKING_DONE>");
        if (tStart < 0) {
            violations.add("缺少<thinking>");
        }
        if (tEnd < 0) {
            violations.add("缺少</thinking>");
        }
        if (doneIdx < 0) {
            violations.add("缺少<THINKING_DONE>");
        } else if (tEnd >= 0 && doneIdx < tEnd) {
            violations.add("标记顺序错误");
        }
        int aStart = text.indexOf("<actions>");
        int aEnd = text.indexOf("</actions>");
        if (aStart < 0 || aEnd < 0) {
            violations.add("缺少<actions>JSON段");
        }
        if (text.contains("```") || (actionsJson != null && actionsJson.contains("```"))) {
            violations.add("JSON含Markdown代码块");
        }
        if (StringUtils.isEmpty(actionsJson)) {
            violations.add("无法提取动作JSON");
        } else {
            try {
                ActionsResponseDTO dto = tryParseActionsResponse(actionsJson);
                if (dto == null || !dto.hasActions()) {
                    violations.add("JSON解析失败或无actions");
                }
            } catch (Exception e) {
                violations.add("JSON解析异常");
            }
        }
        return violations;
    }

    private String buildRetryHint(List<String> violations) {
        //todo 这里格式校验
        StringBuilder sb = new StringBuilder();
        sb.append("请严格修复以下格式问题：\n");
        if (violations != null && !violations.isEmpty()) {
            for (String v : violations) {
                sb.append("- ").append(v).append("\n");
            }
        } else {
            sb.append("- 输出不符合三段式协议\n");
        }
        sb.append("\n必须严格遵守：先<thinking>…</thinking>，再<THINKING_DONE>，最后<actions>{JSON}</actions>；<actions>内部只能是纯JSON，禁止解释性文本和```代码块。");
        return sb.toString();
    }
    
    /**
     * 尝试解析为 ActionsResponseDTO
     * 这里考虑一些兼容性的操作，最大程度容忍ai的格式输出错误
     */
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
    
    /**
     * 构建 AgentAction 列表
     */
    private List<AgentAction> buildAgentActions(List<ActionInputDTO> actionDTOs, AgentContext context) {
        return actionDTOs.stream()
            .map(dto -> buildAgentAction(dto, context))
            .filter(Objects::nonNull)
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
                log.warn("不支持的Action类型: {}", type);
                return null;
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
        // 激进清理：只保留可见字符、空格、换行、回车、制表符
        // 替换掉所有其他控制字符（包括 0x1A SUB）
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
            
            // 处理转义字符
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            
            // 处理字符串引号
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            // 如果在字符串内，忽略结构字符
            if (inString) {
                continue;
            }
            
            // 处理 JSON 结构
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                    // 找到完整的 JSON 对象
                    if (depth == 0 && start >= 0) {
                        return input.substring(start, i + 1);
                    }
                }
            }
        }
        
        // 如果循环结束但 depth > 0，说明 JSON 被截断
        // 尝试自动补全
        if (depth > 0 && start >= 0) {
            log.warn("检测到 JSON 截断，尝试自动补全。depth: {}, content: {}", depth, input.substring(start));
            StringBuilder sb = new StringBuilder(input.substring(start));
            // 如果还在字符串内，先补齐引号
            if (inString) {
                sb.append('"');
            }
            // 补齐缺失的大括号
            for (int k = 0; k < depth; k++) {
                sb.append('}');
            }
            String fixed = sb.toString();
            log.info("自动补全后的 JSON: {}", fixed);
            return fixed;
        }
        
        return "";
    }

    private StreamingCallback createThinkingStreamingCallback(AgentContext context) {
        return new StreamingCallback() {
            private final ThinkingStreamState state = new ThinkingStreamState();

            @Override
            public void onToken(String token) {
                if (StringUtils.isEmpty(token)) {
                    return;
                }
                state.fullText.append(token);
                String delta = handleThinkingStream(state, context, false);
                if (StringUtils.isNotEmpty(delta)) {
                    sendThinkingDeltaEvent(context, delta);
                }
            }

            @Override
            public void onComplete(String fullText) {
                if (!StringUtils.isEmpty(fullText) && state.fullText.isEmpty()) {
                    state.fullText.append(fullText);
                }
                String delta = handleThinkingStream(state, context, true);
                if (StringUtils.isNotEmpty(delta)) {
                    sendThinkingDeltaEvent(context, delta);
                }
                log.info(state.fullText.toString());
            }

            @Override
            public void onError(Throwable error) {
                sendProgressEvent(context, AgentConstants.EVENT_AGENT_ERROR, "思考阶段发生错误: " + error.getMessage());
            }
        };
    }

    private String handleThinkingStream(ThinkingStreamState state, AgentContext context, boolean isComplete) {
        String text = state.fullText.toString();
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        int thinkingStartIdx = text.indexOf("<thinking>");
        int thinkingEndIdx = text.indexOf("</thinking>");
        int tagLength = "<thinking>".length();
        if (thinkingStartIdx != -1 && !state.thinkingStarted) {
            state.thinkingStarted = true;
            state.thinkingContentSentIndex = thinkingStartIdx + tagLength;
            sendProgressEvent(context, AgentConstants.EVENT_STATUS_THINKING_PROCESS, "正在进行深入思考...");
        }
        String delta = "";
        if (state.thinkingStarted) {
            int logicalEnd = thinkingEndIdx != -1 ? thinkingEndIdx : text.length();

            // Lookahead buffer: if we haven't found the end tag yet and stream is not complete,
            // check if the end of the current content looks like a partial closing tag.
            if (thinkingEndIdx == -1 && !isComplete) {
                String currentContent = text.substring(state.thinkingContentSentIndex, logicalEnd);
                int bufferLen = calculatePartialTagMatchLength(currentContent);
                if (bufferLen > 0) {
                    logicalEnd -= bufferLen;
                }
            }

            if (state.thinkingContentSentIndex < logicalEnd) {
                String newContent = text.substring(state.thinkingContentSentIndex, logicalEnd);
                if (StringUtils.isNotEmpty(newContent)) {
                    delta = sanitizeThinkingDelta(newContent);
                }
                state.thinkingContentSentIndex = logicalEnd;
            }
            if (thinkingEndIdx != -1 && !state.thinkingClosed) {
                state.thinkingClosed = true;
            }
        }
        
        // 检测思考完成标记
        int doneIdx = text.indexOf("<THINKING_DONE>");
        if (doneIdx != -1 && !state.thinkingDoneMarked) {
            state.thinkingDoneMarked = true;
            sendProgressEvent(context, AgentConstants.EVENT_STATUS_PLANNING, "思考完成，正在生成动作计划...");
        }
        
        return delta;
    }

    private int calculatePartialTagMatchLength(String content) {
        if (StringUtils.isEmpty(content)) {
            return 0;
        }
        String tag = "</thinking>";
        // Check from longest possible match down to length 1
        // We only care if the content *ends* with a prefix of the tag
        for (int i = tag.length() - 1; i >= 1; i--) {
            String prefix = tag.substring(0, i);
            if (content.endsWith(prefix)) {
                return i;
            }
        }
        return 0;
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

    private String sanitizeThinkingDelta(String delta) {
        if (StringUtils.isEmpty(delta)) {
            return "";
        }
        String cleaned = delta;
        cleaned = cleaned.replace("<thinking>", "");
        cleaned = cleaned.replace("</thinking>", "");
        cleaned = cleaned.replace("</thinking", "");
        cleaned = cleaned.replace("<thinking", "");
        return cleaned.trim().isEmpty() ? "" : cleaned;
    }

    private void sendDecidedActionsEvent(AgentContext context, List<AgentAction> actions) {
        if (context == null || context.getEventPublisher() == null || actions == null || actions.isEmpty()) {
            return;
        }
        String names = actions.stream().map(AgentAction::getName).collect(Collectors.joining(", "));
        
        // Use structured event for i18n
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("actionNames", names);
        
        context.getEventPublisher().accept(
            AgentEventData.builder()
                .event(AgentConstants.EVENT_STATUS_THINKING_PROCESS)
                .message("正在进行深入思考...") // Fallback message
                .data(data)
                .build()
        );
    }

    private String extractActionsJson(String response) {
        if (StringUtils.isEmpty(response)) {
            return response;
        }
        String text = removeControlChars(response);
        
        // 1. 尝试提取 <actions> 标签内的内容
        int startTagIdx = text.indexOf("<actions>");
        int endTagIdx = text.indexOf("</actions>");
        
        String candidate = text;
        if (startTagIdx >= 0) {
            int contentStart = startTagIdx + "<actions>".length();
            if (endTagIdx > contentStart) {
                candidate = text.substring(contentStart, endTagIdx);
            } else {
                candidate = text.substring(contentStart);
            }
        }
        
        // 2. 在候选文本中寻找第一个 '{' 和最后一个 '}'
        // 这能有效过滤掉标签内的自然语言杂质（如：<actions>\n好的，这是JSON...\n{...}\n</actions>）
        int jsonStart = candidate.indexOf('{');
        int jsonEnd = candidate.lastIndexOf('}');
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            String jsonStr = candidate.substring(jsonStart, jsonEnd + 1);
            // 3. 移除 JSON 中的注释 (支持 // 和 <!-- -->)
            // 移除 // 注释 (注意不要误删 URL 中的 //)
            // 简单处理：仅移除行尾的 // 注释，且不在引号内
            // 更安全的做法是移除 <!-- --> 风格注释，因为错误日志中出现了这种
            jsonStr = jsonStr.replaceAll("<!--[\\s\\S]*?-->", "");
            
            // 尝试移除行尾 // 注释 (风险较高，暂不启用，除非必要)
            // jsonStr = jsonStr.replaceAll("(?m)//.*$", "");
            
            return jsonStr;
        }
        
        return candidate.trim();
    }

    private static class ThinkingStreamState {
        StringBuilder fullText = new StringBuilder();
        boolean thinkingStarted;
        boolean thinkingClosed;
        boolean thinkingDoneMarked;
        int thinkingContentSentIndex;
    }

    private static class StructuredStreamState {
        StringBuilder fullText = new StringBuilder();
        int lastThinkingSentLength;
    }

    private ResponseFormat buildStructuredResponseFormat() {
        JsonObjectSchema toolCallParams = JsonObjectSchema.builder()
            .addStringProperty("toolName")
            .addProperty("toolParams", JsonObjectSchema.builder().build())
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

    /**
     * 构建工具调用Action
     */
    private AgentAction buildToolCallAction(ActionInputDTO dto) {
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
        
        
        // 如果 toolCallParams 中没有 toolName，设置它
        if (StringUtils.isEmpty(params.getToolName())) {
            params.setToolName(toolName);
        }
        
        return AgentAction.toolCall(toolName, params);
    }
    
    /**
     * 构建RAG检索Action
     */
    private AgentAction buildRAGRetrieveAction(ActionInputDTO dto, AgentContext context) {
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
        
        return AgentAction.ragRetrieve(params);
    }
    
    /**
     * 构建直接返回响应Action
     */
    private AgentAction buildDirectResponseAction(ActionInputDTO dto) {
        DirectResponseParams params = dto.getDirectResponseParams();
        DirectResponseParams.builder()
                .content(dto.getDirectResponseParams().getContent())
                .build();
        return AgentAction.directResponse(params);
    }
    
    /**
     * 构建LLM生成Action
     */
    private AgentAction buildLLMGenerateAction(ActionInputDTO dto) {
        LLMGenerateParams params = dto.getLlmGenerateParams();
        if (params == null) {
            log.warn("LLM_GENERATEAction缺少llmGenerateParams");
            return null;
        }
        
        if (StringUtils.isEmpty(params.getPrompt())) {
            log.warn("LLM_GENERATEAction缺少prompt");
            return null;
        }
        
        return AgentAction.llmGenerate(params);
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

    /**
     * 添加 RAG 知识库信息到提示词
     */
    private void appendRAGInfo(StringBuilder prompt, AgentContext context) {
        // 1. 可用知识库列表
        appendAvailableKnowledgeBases(prompt, context);
        
        // 2. 已检索的知识库信息
        appendRetrievedKnowledgeInfo(prompt, context);
    }
    
    /**
     * 添加可用知识库列表到提示词
     */
    private void appendAvailableKnowledgeBases(StringBuilder prompt, AgentContext context) {
        List<String> knowledgeIds = context.getKnowledgeIds();
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return;
        }
        
        prompt.append("## 可用知识库\n\n");
        prompt.append("以下知识库可用于 RAG_RETRIEVE 检索：\n\n");
        
        // 从 context 中获取已加载的知识库信息
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
                // 如果 context 中没有，显示知识库ID（这种情况不应该发生，但作为兜底）
                prompt.append(index).append(". 知识库 (ID: ").append(knowledgeId).append(")\n\n");
            }
            index++;
        }
    }
    
    /**
     * 添加已检索的知识库信息到提示词
     */
    private void appendRetrievedKnowledgeInfo(StringBuilder prompt, AgentContext context) {
        AgentKnowledgeResult knowledgeResult = context.getInitialRagResult();
        if (knowledgeResult == null || knowledgeResult.isEmpty()) {
            return;
        }
        
        prompt.append("## 已检索的知识库信息\n\n");
        prompt.append("以下是从知识库中已检索到的相关信息：\n\n");
        
        // 查询词
        if (StringUtils.isNotEmpty(knowledgeResult.getQuery())) {
            prompt.append("- 查询词: \"").append(knowledgeResult.getQuery()).append("\"\n");
        }
        
        // 检索结果统计
        int totalCount = knowledgeResult.getTotalCount() != null ? knowledgeResult.getTotalCount() : 0;
        prompt.append("- 检索结果: 找到 ").append(totalCount).append(" 条相关文档\n");
        
        // 平均相关度
        if (knowledgeResult.getAvgScore() != null) {
            double avgScorePercent = knowledgeResult.getAvgScore() * 100;
            prompt.append("- 平均相关度: ").append(String.format("%.1f%%", avgScorePercent)).append("\n");
        }
        
        prompt.append("\n");
        
        // 相关文档列表
        List<AgentKnowledgeDocument> documents = knowledgeResult.getDocuments();
        if (documents != null && !documents.isEmpty()) {
            prompt.append("- 相关文档:\n\n");
            
            // 从 context 中获取知识库信息（已批量加载）
            Map<String, KnowledgeBase> knowledgeBaseMap = context.getKnowledgeBaseMap();
            if (knowledgeBaseMap == null) {
                knowledgeBaseMap = new HashMap<>();
            }
            
            // 构建知识库ID到名称的映射（用于显示知识库名称）
            Map<String, String> knowledgeBaseNameMap = new HashMap<>();
            for (AgentKnowledgeDocument doc : documents) {
                if (doc.getKnowledgeId() != null && !knowledgeBaseNameMap.containsKey(doc.getKnowledgeId())) {
                    KnowledgeBase kb = knowledgeBaseMap.get(doc.getKnowledgeId());
                    if (kb != null) {
                        knowledgeBaseNameMap.put(doc.getKnowledgeId(), kb.getName());
                    }
                }
            }
            
            // 列出每个文档
            for (int i = 0; i < documents.size(); i++) {
                AgentKnowledgeDocument doc = documents.get(i);
                prompt.append("  ").append(i + 1).append(". ");
                
                // 文档名
                if (StringUtils.isNotEmpty(doc.getDocName())) {
                    prompt.append(doc.getDocName());
                } else {
                    prompt.append("文档");
                }
                
                // 相关度
                if (doc.getScore() != null) {
                    double scorePercent = doc.getScore() * 100;
                    prompt.append(" - 相关度: ").append(String.format("%.1f%%", scorePercent));
                }
                
                prompt.append("\n");
                
                // 来源知识库
                if (doc.getKnowledgeId() != null) {
                    String kbName = knowledgeBaseNameMap.get(doc.getKnowledgeId());
                    if (kbName != null) {
                        prompt.append("     来源知识库: ").append(kbName);
                    } else {
                        prompt.append("     来源知识库: (ID: ").append(doc.getKnowledgeId()).append(")");
                    }
                    prompt.append("\n");
                }
                
                // 文档内容（不截断）
                if (StringUtils.isNotEmpty(doc.getContent())) {
                    prompt.append("     ").append(doc.getContent()).append("\n");
                }
                
                prompt.append("\n");
            }
        }
        
        prompt.append("\n");
    }

    /**
     * 格式化工具定义（包含参数说明）
     */
    private String formatToolDefinition(McpToolInfo tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(tool.getName());

        if (StringUtils.isNotEmpty(tool.getDescription())) {
            sb.append(": ").append(tool.getDescription());
        }
        sb.append("\n");
        sb.append("params:").append(tool.getParameters().toString());
        sb.append("\nmetadata:").append(tool.getMetadata().toString());

        return sb.toString();
    }
}
