package com.aiagent.application.service.action;

import com.aiagent.domain.enums.ActionType;
import com.aiagent.shared.constant.AgentConstants;
import com.aiagent.domain.enums.AgentMode;
import com.aiagent.application.service.StreamingCallback;
import com.aiagent.application.service.engine.IntelligentToolSelector;
import com.aiagent.application.service.engine.SimpleLLMChatHandler;
import com.aiagent.application.service.memory.MemorySystem;
import com.aiagent.application.service.rag.RAGEnhancer;
import com.aiagent.infrastructure.external.mcp.McpToolExecutor;
import com.aiagent.infrastructure.external.mcp.ToolConfirmationDecision;
import com.aiagent.infrastructure.external.mcp.ToolConfirmationManager;
import com.aiagent.shared.util.StringUtils;
import com.aiagent.shared.util.UUIDGenerator;
import com.aiagent.application.model.AgentContext;
import com.aiagent.api.dto.AgentEventData;
import com.aiagent.application.model.AgentKnowledgeResult;
import com.aiagent.api.dto.McpToolInfo;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 动作执行器
 * 负责执行Agent选定的动作
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class ActionExecutor {
    
    @Autowired
    private RAGEnhancer ragEnhancer;
    
    @Autowired
    private SimpleLLMChatHandler llmChatHandler;
    
    @Autowired
    private MemorySystem memorySystem;
    
    @Autowired
    private McpToolExecutor mcpToolExecutor;
    
    @Autowired
    private IntelligentToolSelector toolSelector;

    @Autowired
    private ToolConfirmationManager toolConfirmationManager;
    
    /**
     * 并行执行线程池（限制最多5个并发）
     */
    private static final ExecutorService PARALLEL_EXECUTOR = 
        Executors.newFixedThreadPool(5, r -> {
            Thread t = new Thread(r, "action-parallel-executor");
            t.setDaemon(true);
            return t;
        });
    
    /**
     * 最大并行执行数量
     */
    private static final int MAX_PARALLEL_ACTIONS = 5;

    /**
     * 工具执行确认超时（毫秒）
     */
    private static final long TOOL_CONFIRM_TIMEOUT_MS = 60_000L;
    
    /**
     * 执行动作
     */
    public ActionResult execute(AgentAction action, AgentContext context) {
        log.info("执行动作: {} - {}", action.getType(), action.getName());
        
        long startTime = System.currentTimeMillis();
        
        try {
            switch (action.getType()) {
                case TOOL_CALL:
                    return executeToolCall(action, context, startTime);
                case RAG_RETRIEVE:
                    return executeRAGRetrieve(action, context, startTime);
                case LLM_GENERATE:
                    return executeLLMGenerate(action, context, startTime);
                case DIRECT_RESPONSE:
                    return executeDirectResponse(action, context, startTime);
                default:
                    return ActionResult.failure(action.getType(), action.getName(),
                        "不支持的动作类型: " + action.getType(), "UNSUPPORTED_ACTION");
            }
        } catch (Exception e) {
            log.error("执行动作失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure(action.getType(), action.getName(),
                e.getMessage(), "EXCEPTION");
            result.setDuration(duration);
            return result;
        }
    }
    
    /**
     * 执行工具调用
     */
    private ActionResult executeToolCall(AgentAction action, AgentContext context, long startTime) {
        try {
            // 获取工具调用参数
            ToolCallParams toolCallParams = action.getToolCallParams();
            Map<String, Object> params;
            if (toolCallParams==null){
                throw new IllegalArgumentException("参数错误,toolCallParams为空");
            }
            
            // 使用特定类型参数
            params = Objects.requireNonNullElseGet(toolCallParams.getToolParams(), HashMap::new);
            // 如果toolCallParams中有toolName，优先使用
            String toolName = toolCallParams.getToolName();
            // 查找工具信息
            McpToolInfo toolInfo = toolSelector.getToolByName(toolName);
            if (toolInfo == null) {
                throw new IllegalArgumentException("工具未找到: " + toolName);
            }

            String toolExecutionId = UUIDGenerator.generate();
            boolean requiresConfirmation = context != null && context.getMode() == AgentMode.MANUAL;

            // 手动模式下等待用户确认
            if (requiresConfirmation) {
                toolConfirmationManager.register(toolExecutionId);
            }

            // 发送工具调用事件（包含确认信息）
            sendToolCallEvent(context, toolExecutionId, toolName, params, requiresConfirmation);

            if (requiresConfirmation) {
                ToolConfirmationDecision decision = toolConfirmationManager.waitForDecision(
                    toolExecutionId, TOOL_CONFIRM_TIMEOUT_MS);
                if (decision != ToolConfirmationDecision.APPROVED) {
                    String rejectMessage = decision == ToolConfirmationDecision.TIMEOUT
                        ? "用户拒绝执行（确认超时）"
                        : "用户拒绝执行";
                    long duration = System.currentTimeMillis() - startTime;
                    ActionResult rejectResult = ActionResult.failure(ActionType.TOOL_CALL, toolName,
                        rejectMessage, "USER_REJECTED");
                    rejectResult.setDuration(duration);
                    rejectResult.setMetadata(Map.of(
                        "toolExecutionId", toolExecutionId,
                        "toolName", toolName,
                        "params", params,
                        "rejectReason", rejectMessage
                    ));
                    sendToolResultEvent(context, toolExecutionId, toolName, null, rejectMessage);
                    return rejectResult;
                }
            }
            
            // 检查工具是否在启用列表中（如果指定了enabledTools）
            if (context != null && context.getEnabledTools() != null && !context.getEnabledTools().isEmpty()) {
                if (!context.getEnabledTools().contains(toolName)) {
                    String errorMsg = String.format("工具未启用: %s。当前启用的工具列表: %s", 
                        toolName, context.getEnabledTools());
                    log.warn(errorMsg);
                    throw new IllegalArgumentException(errorMsg);
                }
                log.debug("工具 {} 已通过启用检查", toolName);
            } else {
                log.debug("未指定启用工具列表，允许所有工具");
            }
            
            log.info("执行工具调用: name={}, params={}", toolName, toolCallParams);
            
            // 发送工具执行进度事件
            sendProgressEvent(context, AgentConstants.EVENT_AGENT_TOOL_EXECUTING,
                "正在执行工具: " + toolInfo.getName() + "...");
            
            // 使用McpToolExecutor执行工具
            Object toolResult = mcpToolExecutor.execute(toolInfo, toolCallParams.getToolParams());

            // 将结果转换为字符串（用于记录和返回）
            String resultStr = parseToolResult(toolResult);
            sendProgressEvent(context, AgentConstants.EVENT_AGENT_TOOL_EXECUTING,
                    "调用工具: " + toolInfo.getName() + " 成功");
            
            // 记录工具调用历史
            memorySystem.recordToolCall(context, toolName, params, toolResult);
            
            long duration = System.currentTimeMillis() - startTime;
            ActionResult actionResult = ActionResult.success(ActionType.TOOL_CALL, toolName, toolResult);
            actionResult.setDuration(duration);
            actionResult.setMetadata(Map.of(
                "toolExecutionId", toolExecutionId,
                "toolName", toolName, 
                "params", params,
                "resultStr", resultStr
            ));

            sendToolResultEvent(context, toolExecutionId, toolName, toolResult, null);
            
            log.info("工具调用成功: name={}, duration={}ms", toolName, duration);
            return actionResult;
            
        } catch (Exception e) {
            log.error("工具调用失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure(ActionType.TOOL_CALL, action.getName(),
                e.getMessage(), "TOOL_CALL_ERROR");
            sendProgressEvent(context, AgentConstants.EVENT_AGENT_TOOL_EXECUTING,
                    "调用工具失败");
            result.setDuration(duration);
            result.setMetadata(Map.of(
                "toolName", action.getName()
            ));
            sendToolResultEvent(context, null, action.getName(), null, e.getMessage());
            return result;
        }
    }
    
    /**
     * 解析工具执行结果为字符串
     */
    private String parseToolResult(Object toolResult) {
        if (toolResult == null) {
            return "工具执行完成，但未返回结果";
        }
        
        // 如果是字符串，直接返回
        if (toolResult instanceof String) {
            return (String) toolResult;
        }
        
        // 如果是Map或List，转换为JSON
        if (toolResult instanceof Map || toolResult instanceof List) {
            try {
                return JSON.toJSONString(toolResult);
            } catch (Exception e) {
                log.warn("工具结果JSON序列化失败", e);
                return toolResult.toString();
            }
        }
        
        // 其他类型，转换为字符串
        return toolResult.toString();
    }
    
    /**
     * 执行RAG检索
     */
    private ActionResult executeRAGRetrieve(AgentAction action, AgentContext context, long startTime) {
        try {
            // 获取RAG检索参数（优先使用特定类型参数）
            RAGRetrieveParams ragParams = action.getRagRetrieveParams();
            String query = "";
            List<String> knowledgeIds = List.of();
            
            if (ragParams != null) {
                // 使用特定类型参数
                query = ragParams.getQuery();
                knowledgeIds = Objects.requireNonNullElseGet(ragParams.getKnowledgeIds(), ArrayList::new);
            }
            
            // 如果query为空，使用reasoning作为查询
            if (StringUtils.isEmpty(query)) {
                query = action.getReasoning();
            }
            
            // 发送RAG检索进度事件
            String ragMessage = "查询相关知识";
            if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
                ragMessage += " (知识库数量: " + knowledgeIds.size() + ")";
            }
            ragMessage += "...";
            sendProgressEvent(context, AgentConstants.EVENT_AGENT_RAG_QUERYING, ragMessage);
            
            // 执行RAG检索
            AgentKnowledgeResult knowledgeResult = ragEnhancer.retrieve(query, knowledgeIds);
            
            // 记录RAG检索历史
            memorySystem.recordRAGRetrieve(context, query, knowledgeIds, 
                knowledgeResult != null ? knowledgeResult.getTotalCount() : 0);
            
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.success(ActionType.RAG_RETRIEVE, "rag_retrieve", knowledgeResult);
            result.setDuration(duration);
            result.setMetadata(Map.of("query", query, "count",
                knowledgeResult != null ? knowledgeResult.getTotalCount() : 0));
            
            return result;
            
        } catch (Exception e) {
            log.error("RAG检索失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure(ActionType.RAG_RETRIEVE, "rag_retrieve",
                e.getMessage(), "RAG_RETRIEVE_ERROR");
            result.setDuration(duration);
            return result;
        }
    }
    
    /**
     * 执行LLM生成
     * 注意：此方法返回TokenStream用于流式输出，实际内容需要在调用方处理
     */
    private ActionResult executeLLMGenerate(AgentAction action, AgentContext context, long startTime) {
        try {
            // 获取LLM生成参数（优先使用特定类型参数）
            LLMGenerateParams llmParams = action.getLlmGenerateParams();
            String prompt;
            String systemPrompt = null;
            
            if (llmParams == null) {
               throw  new IllegalArgumentException("llmParams 不能为空");
            }
            // 使用特定类型参数
            prompt = llmParams.getPrompt();
            systemPrompt = llmParams.getSystemPrompt();
            // 如果prompt为空，使用reasoning作为prompt
            if (StringUtils.isEmpty(prompt)) {
                prompt = action.getReasoning();
            }
            
            // 准备消息列表，包含完整的对话历史以保持上下文
            List<ChatMessage> messages = new ArrayList<>();
            
            // 1. 添加系统提示（如果有）
            if (StringUtils.isNotEmpty(systemPrompt)) {
                messages.add(new SystemMessage(systemPrompt));
            } else {
                // 默认系统提示
                messages.add(new SystemMessage("你是一个智能助手，能够帮助用户管理和查询阿里云资源。请用友好、专业的方式回答用户的问题。"));
            }
            
            // 2. 添加对话历史（保持上下文连贯性）
            if (context != null && context.getMessages() != null && !context.getMessages().isEmpty()) {
                List<ChatMessage> historyMessages = context.getMessages();
                
                // 保留最近的对话历史（避免token过多）
                // 保留最近10轮对话（20条消息）
                int maxHistoryMessages = 20;
                int startIdx = Math.max(0, historyMessages.size() - maxHistoryMessages);
                
                for (int i = startIdx; i < historyMessages.size(); i++) {
                    messages.add(historyMessages.get(i));
                }
                
                log.debug("包含对话历史: {} 条消息", historyMessages.size() - startIdx);
            }
            
            // 3. 添加当前的生成提示
            messages.add(new UserMessage(prompt));
            
            // 发送生成进度事件
            sendProgressEvent(context, AgentConstants.EVENT_AGENT_GENERATING, "正在生成回复...");
            
            // 调用LLM
            String modelId = context != null ? context.getModelId() : null;
            if (StringUtils.isEmpty(modelId)) {
                modelId = "gpt-4o-mini";
            }
            
            String llmResponse;
            boolean isStreaming = false;
            log.info("调用LLM生成，模型ID: {}, 提示词: {}", modelId, prompt);

            if (context != null && context.getStreamingCallback() != null) {
                // 有回调，使用流式输出
                isStreaming = true;
                llmResponse = llmChatHandler.chatWithCallback(modelId, messages, context.getStreamingCallback());
            } else {
                // 如果没有回调，使用非流式调用
                log.debug("没有流式回调，使用非流式模式获取LLM结果");
                llmResponse = llmChatHandler.chatNonStreaming(modelId, messages);
            }

            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.success(ActionType.LLM_GENERATE, "llm_generate", llmResponse);
            result.setDuration(duration);
            result.setMetadata(Map.of(
                "prompt", prompt, 
                "modelId", modelId,
                "historyMessageCount", messages.size() - 2,
                "streaming", isStreaming,
                "textLength", llmResponse.length()
            ));
            
            return result;
            
        } catch (Exception e) {
            log.error("LLM生成失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure(ActionType.LLM_GENERATE, "llm_generate",
                e.getMessage(), "LLM_GENERATE_ERROR");
            result.setDuration(duration);
            return result;
        }
    }
    
    /**
     * 并行执行多个动作
     * 所有动作会同时执行，即使某个失败也会继续执行其他动作
     * 返回所有执行结果的列表，供后续观察和反思阶段使用
     */
    public List<ActionResult> executeParallel(List<AgentAction> actions, AgentContext context) {
        log.info("并行执行 {} 个动作", actions.size());
        
        // 限制最多5个
        if (actions.size() > MAX_PARALLEL_ACTIONS) {
            log.warn("动作数量超过限制（{}），只执行前{}个", actions.size(), MAX_PARALLEL_ACTIONS);
            actions = actions.subList(0, MAX_PARALLEL_ACTIONS);
        }
        
        long startTime = System.currentTimeMillis();
        
        // 发送并行执行开始事件
        sendProgressEvent(context, AgentConstants.EVENT_AGENT_TOOL_EXECUTING, 
            "正在并行执行 " + actions.size() + " 个操作...");

        Map<String,CompletableFuture<ActionResult>> actionResultMap=new HashMap<>();
        for (AgentAction action:actions){
            actionResultMap.put(action.getId(),CompletableFuture.supplyAsync(() -> {
                try {
                    log.debug("并行执行动作: {}", action.getName());
                    return execute(action, context);
                } catch (Exception e) {
                    log.error("并行执行动作失败: {}", action.getName(), e);
                    return ActionResult.failure(
                            action.getType(),
                            action.getName(),
                            e.getMessage(),
                            "EXCEPTION"
                    );
                }
            }, PARALLEL_EXECUTOR));
        }
        List<ActionResult> results=new ArrayList<>();
        for (AgentAction action:actions){
            CompletableFuture<ActionResult> future = actionResultMap.get(action.getId());
            try {
                future.join();
                results.add(future.get());
            } catch (Exception e) {
                log.error("等待动作执行完成时出错", e);
                results.add(ActionResult.failure(action.getType(), action.getName(),
                        "执行异常: " + e.getMessage(), "EXCEPTION"));
            }
        }


        long duration = System.currentTimeMillis() - startTime;

        // 统计成功和失败数量
        long successCount = results.stream().filter(ActionResult::isSuccess).count();
        long failureCount = results.size() - successCount;

        log.info("并行执行完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
            results.size(), successCount, failureCount, duration);

        return results;

    }
    
    /**
     * 执行直接返回响应
     * 用于简单场景，直接返回预设的回复内容，无需调用LLM
     */
    private ActionResult executeDirectResponse(AgentAction action, AgentContext context, long startTime) {
        DirectResponseParams params = action.getDirectResponseParams();
        if (params == null || StringUtils.isEmpty(params.getContent())) {
            throw new IllegalArgumentException("DirectResponseParams.content 不能为空");
        }

        String content = params.getContent();

        // 发送进度事件
        sendProgressEvent(context, AgentConstants.EVENT_AGENT_GENERATING, "正在生成回复...");

        StreamingCallback callback = context.getStreamingCallback();
        callback.onStart();

        // 模拟流式输出：按词或字符发送
        // 使用较小的延迟，模拟自然的打字速度（约每20ms发送一次）
        // 按空格和换行分割，保留分隔符
        String[] words = content.split("(?<=[\\s\\n])");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            callback.onToken(word);
            try {
                // 控制速度：每个词之间延迟15-25ms（随机变化，更自然）
                int delay = 15 + (int)(Math.random() * 10);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("流式输出被中断");
                break;
            }
        }

        callback.onComplete(content);

        // 标记为流式输出
        long duration = System.currentTimeMillis() - startTime;
        ActionResult result = ActionResult.success(ActionType.DIRECT_RESPONSE, "direct_response", content);
        result.setDuration(duration);
        result.setMetadata(Map.of("streaming", true));
        log.info("直接返回响应成功（流式）: duration={}ms", duration);
        return result;
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

    private void sendToolCallEvent(AgentContext context, String toolExecutionId, String toolName,
                                   Map<String, Object> params, boolean requiresConfirmation) {
        if (context != null && context.getEventPublisher() != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("toolExecutionId", toolExecutionId);
            data.put("toolName", toolName);
            data.put("params", params);
            data.put("requiresConfirmation", requiresConfirmation);
            data.put("mode", context.getMode() != null ? context.getMode().name() : AgentMode.AUTO.name());
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(AgentConstants.EVENT_AGENT_TOOL_CALL)
                    .data(data)
                    .build()
            );
        }
    }

    private void sendToolResultEvent(AgentContext context, String toolExecutionId, String toolName,
                                     Object result, String error) {
        if (context != null && context.getEventPublisher() != null) {
            Map<String, Object> data = new HashMap<>();
            if (toolExecutionId != null) {
                data.put("toolExecutionId", toolExecutionId);
            }
            data.put("toolName", toolName);
            if (result != null) {
                data.put("result", result);
            }
            if (error != null) {
                data.put("error", error);
            }
            context.getEventPublisher().accept(
                AgentEventData.builder()
                    .event(AgentConstants.EVENT_AGENT_TOOL_RESULT)
                    .data(data)
                    .build()
            );
        }
    }
    
}

