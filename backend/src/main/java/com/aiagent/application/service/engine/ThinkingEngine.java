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
import dev.langchain4j.data.message.AiMessage;
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
import java.util.Map;
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
     * 先输出思考过程，再输出JSON动作决策
     */
    private static final String OUTPUT_FORMAT_PROMPT = """
            ## 输出协议与格式规范
            
            你必须严格遵守以下"三段式"输出协议：
            
            ### 第一阶段：深度思考 (Thinking)
            在做出决定前，必须先进行逻辑推演。
            格式要求：
            <thinking>
            1. 分析用户意图...
            2. 评估当前可用信息...
            3. 规划后续步骤...
            </thinking>
            
            ### 第二阶段：思考截断 (Checkpoint)
            思考结束后，必须输出唯一的结束标记：
            <THINKING_DONE>
            
            ### 第三阶段：动作执行 (Execution)
            在输出 `<THINKING_DONE>` 后，**立即**输出最终的动作指令 JSON，不要包含任何其他解释性文字。
            
            **⚠️ 严正警告 (CRITICAL WARNING)**：
            1. `<actions>` 标签内部 **只能** 包含一个标准的 JSON 对象。
            2. **严禁** 在 JSON 前后添加任何解释性文字（如 "好的，这是执行计划..."）。
            3. **严禁** 使用 Markdown 代码块标记（如 ```json ... ```）。
            4. 如果你想直接回复用户，请使用 `DIRECT_RESPONSE` 动作，将回复内容放在 `content` 字段中，而不是直接输出文本。
            
            格式要求：
            <actions>
            {
              "actions": [
                {
                  "actionType": "...",
                  "actionName": "...",
                  "reasoning": "...",
                  "...Params": { ... }
                }
              ]
            }
            </actions>
            
            ---
            
            ### 核心校验规则 (Critical Rules)
            
            1. **JSON 严格语法**
               - `<actions>` 内部必须是纯粹的 JSON 文本，**严禁**包含 markdown 代码块标记（如 ```json）。
               - 务必校验 `{}` 和 `[]` 的闭合性。
               - 所有字段名必须使用双引号。
            
            2. **参数结构规范 (Schema Definitions)**
               每个 `actionType` 对应唯一的参数对象，结构如下：
            
               **(A) TOOL_CALL (调用工具)**
               - 必须包含 `toolCallParams`:
                 ```json
                 "toolCallParams": {
                   "toolName": "工具名称(String, 必填)",
                   "toolParams": { "key": "value" } // 工具具体参数对象(Map, 必填)
                 }
                 ```
            
               **(B) RAG_RETRIEVE (知识库检索)**
               - 必须包含 `ragRetrieveParams`:
                 ```json
                 "ragRetrieveParams": {
                   "query": "检索关键词(String, 必填)",
                   "knowledgeIds": ["kb_id1"], // 知识库ID列表(List<String>, 可选)
                   "maxResults": 5, // 最大结果数(Integer, 可选)
                   "similarityThreshold": 0.7 // 相似度阈值(Double, 可选)
                 }
                 ```
            
               **(C) LLM_GENERATE (大模型生成)**
               - 必须包含 `llmGenerateParams`:
                 ```json
                 "llmGenerateParams": {
                   "prompt": "提示词(String, 必填)",
                   "systemPrompt": "系统设定(String, 可选)",
                   "temperature": 0.7 // 温度(Double, 可选)
                 }
                 ```
            
               **(D) DIRECT_RESPONSE (直接回复)**
               - 必须包含 `directResponseParams`:
                 ```json
                 "directResponseParams": {
                   "content": "回复内容(String, 必填)",
                   "streaming": true // 是否流式(Boolean, 默认为true)
                 }
                 ```
            
            3. **禁止废话**
               在 `</thinking>` 和 `<actions>` 之间，严禁输出任何自然语言过渡句。
            
            ---
            
            ### 标准范例 (Examples)
            
            #### 场景 1: 直接回复用户
            <thinking>
            用户在打招呼，无需调用工具。
            </thinking>
            <actions>
            {
              "actions": [
                {
                  "actionType": "DIRECT_RESPONSE",
                  "actionName": "greet_user",
                  "reasoning": "直接回复问候",
                  "directResponseParams": {
                    "content": "你好！有什么我可以帮你的吗？",
                    "streaming": true
                  }
                }
              ]
            }
            </actions>
            
            #### 场景 2: 调用工具查询
            <thinking>
            用户想查天气，我需要使用 get_weather 工具。
            </thinking>
            <actions>
            {
              "actions": [
                {
                  "actionType": "TOOL_CALL",
                  "actionName": "search_weather",
                  "reasoning": "用户询问天气，需要调用天气工具",
                  "toolCallParams": {
                    "toolName": "get_weather",
                    "toolParams": {
                      "city": "Hangzhou"
                    }
                  }
                }
              ]
            }
            </actions>
            """;
    /**
     * 思考：分析目标、上下文和历史结果，决定下一步Action（支持返回多个Action）
     */
    public List<AgentAction> think(String goal, AgentContext context, List<ActionResult> lastResults) {
        log.info("开始思考，目标: {}, 上次结果数量: {}", goal, lastResults != null ? lastResults.size() : 0);
        
        // 发送思考进度事件
        sendProgressEvent(context, AgentConstants.EVENT_STATUS_ANALYZING, "正在分析任务和用户意图...");
        
        // 构建思考提示词（分离系统提示词和用户提示词）
        PromptPair promptPair = buildThinkingPrompt(goal, context, lastResults);
        log.debug("系统提示词长度: {}, 用户提示词长度: {}", 
            promptPair.getSystemPrompt().length(), promptPair.getUserPrompt().length());
        log.info("系统提示词: {}\n, 用户提示词: {}",
                promptPair.getSystemPrompt(), promptPair.getUserPrompt());
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
        sendDecidedActionsEvent(context, actions);
        return actions;
    }
    
    /**
     * 构建思考提示词（分离系统提示词和用户提示词）
     */
    private PromptPair buildThinkingPrompt(String goal, AgentContext context, List<ActionResult> lastResults) {
        String systemPrompt = buildSystemPrompt();
        // 这里仅保留动态上下文（如当前目标、工具列表、执行结果等），不再包含历史对话
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
            try {
                String normalized = removeControlChars(text);
                String extracted = extractFirstJsonObject(normalized);
                String fallback = StringUtils.isNotEmpty(extracted) ? extracted : cleanJsonResponse(normalized);
                if (StringUtils.isNotEmpty(fallback)) {
                    log.debug("清理后的思考结果(兜底): {}", fallback);
                    return JSON.parseObject(fallback, ActionsResponseDTO.class);
                }
            } catch (Exception ignored) {
            }
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
            return candidate.substring(jsonStart, jsonEnd + 1);
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
        if (params == null && dto.getToolCallParams() != null && dto.getToolCallParams().getToolParams() != null) {
            Object contentObj = dto.getToolCallParams().getToolParams().get("content");
            if (contentObj instanceof String content && StringUtils.isNotEmpty(content)) {
                params = DirectResponseParams.builder()
                    .content(content)
                    .streaming(true)
                    .build();
            }
        }
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
    
}
