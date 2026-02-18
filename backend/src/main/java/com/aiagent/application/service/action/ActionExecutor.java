package com.aiagent.application.service.action;

import com.aiagent.application.model.AgentKnowledgeDocument;
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
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                    return ActionResult.failure(action, "不支持的动作类型: " + action.getType());
            }
        } catch (Exception e) {
            log.error("执行动作失败", e);
            long duration = System.currentTimeMillis() - startTime;
            ActionResult result = ActionResult.failure(action, e.getMessage());
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
                    
                    ActionResult rejectResult = ActionResult.failure(action, rejectMessage);
                    rejectResult.setDuration(duration);
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
            
            // Structured event for single tool execution
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("toolName", toolInfo.getName());
            
            sendProgressEvent(context, AgentConstants.EVENT_STATUS_TOOL_EXECUTING_SINGLE,
                "正在执行工具: " + toolInfo.getName() + "...", data);
            
            // Execute tool
            long start = System.currentTimeMillis();
            ToolExecutionResult toolResult = mcpToolExecutor.execute(toolInfo, toolCallParams.getToolParams());

            sendProgressEvent(context, AgentConstants.EVENT_AGENT_TOOL_EXECUTING,
                    "调用工具: " + toolInfo.getName() + " 成功");
            
            // 注意：动作执行历史会在 executeParallel 方法结束后统一记录
            
            long duration = System.currentTimeMillis() - startTime;
            
            // 构造出参字符串
            String output = formatToolOutput(toolResult);
            
            // 创建结果对象
            ActionResult actionResult = ActionResult.success(action, output);
            actionResult.setDuration(duration);

            sendToolResultEvent(context, toolExecutionId, toolName, toolResult, null);
            
            log.info("工具调用成功: name={}, duration={}ms", toolName, duration);
            return actionResult;
            
        } catch (Exception e) {
            log.error("工具调用失败", e);
            long duration = System.currentTimeMillis() - startTime;
            
            ActionResult result = ActionResult.failure(action, e.getMessage());
            result.setDuration(duration);
            
            sendProgressEvent(context, AgentConstants.EVENT_AGENT_TOOL_EXECUTING, "调用工具失败");
            sendToolResultEvent(context, null, action.getName(), null, e.getMessage());
            return result;
        }
    }
    
    /**
     * 解析工具执行结果为字符串
     */
    private String parseToolResult(ToolExecutionResult toolResult) {
        if (toolResult == null) {
            return "工具执行完成，但未返回结果";
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
            
            // 执行RAG检索（传递 context，使用已加载的知识库信息）
            AgentKnowledgeResult knowledgeResult = ragEnhancer.retrieve(query, context.getKnowledgeBaseMap(),context.getRagConfig());
            long duration = System.currentTimeMillis() - startTime;
            // 构造出参字符串
            String output = formatRAGOutput(knowledgeResult);
            
            // 创建结果对象
            ActionResult result = ActionResult.success(action, output);
            result.setDuration(duration);
            return result;
        } catch (Exception e) {
            log.error("RAG检索失败", e);
            long duration = System.currentTimeMillis() - startTime;
            
            ActionResult result = ActionResult.failure(action, e.getMessage());
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
            log.debug("调用LLM生成，模型ID: {}, 提示词长度: {}", modelId, prompt != null ? prompt.length() : 0);

            if (context != null && context.getStreamingCallback() != null) {
                // 有回调，使用流式输出
                llmResponse = llmChatHandler.chatWithCallback(modelId, messages, context.getStreamingCallback());
            } else {
                // 如果没有回调，使用非流式调用
                log.debug("没有流式回调，使用非流式模式获取LLM结果");
                llmResponse = llmChatHandler.chatNonStreaming(modelId, messages);
            }

            long duration = System.currentTimeMillis() - startTime;
            
            // 构造出参字符串
            String output = formatLLMOutput(llmResponse);
            
            // 创建结果对象
            ActionResult result = ActionResult.success(action, output);
            result.setDuration(duration);
            
            return result;
            
        } catch (Exception e) {
            log.error("LLM生成失败", e);
            long duration = System.currentTimeMillis() - startTime;
            
            ActionResult result = ActionResult.failure(action, e.getMessage());
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
        // Structured event for batch execution
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("count", actions.size());
        
        sendProgressEvent(context, AgentConstants.EVENT_STATUS_TOOL_EXECUTING_BATCH,
            "正在并行执行 " + actions.size() + " 个操作...", data);

        Map<String,CompletableFuture<ActionResult>> actionResultMap=new HashMap<>();
        for (AgentAction action:actions){
            actionResultMap.put(action.getId(),CompletableFuture.supplyAsync(() -> {
                try {
                    log.debug("并行执行动作: {}", action.getName());
                    return execute(action, context);
                } catch (Exception e) {
                    log.error("并行执行动作失败: {}", action.getName(), e);
                    return ActionResult.failure(action, e.getMessage());
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
                results.add(ActionResult.failure(action, "执行异常: " + e.getMessage()));
            }
        }


        long duration = System.currentTimeMillis() - startTime;

        // 统计成功和失败数量
        long successCount = results.stream().filter(ActionResult::isSuccess).count();
        long failureCount = results.size() - successCount;

        log.info("并行执行完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
            results.size(), successCount, failureCount, duration);

        // 记录这轮迭代的动作执行结果
        recordIterationResults(results, context);

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
        
        // 构造出参字符串
        String output = formatDirectResponseOutput(content);
        
        // 创建结果对象
        ActionResult result = ActionResult.success(action, output);
        result.setDuration(duration);
        
        log.info("直接返回响应成功（流式）: duration={}ms", duration);
        return result;
}
    
    /**
     * 发送进度事件到前端
     */
    private void sendProgressEvent(AgentContext context, String event, String message) {
        sendProgressEvent(context, event, message, null);
    }

    /**
     * 发送进度事件到前端（带数据）
     */
    private void sendProgressEvent(AgentContext context, String event, String message, java.util.Map<String, Object> data) {
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
    
    /**
     * 记录动作执行历史到上下文
     * 将 ActionResult 转换为历史记录格式，便于思考引擎在下一轮推理时使用
     */
    /**
     * 记录一轮迭代的动作执行结果
     * 直接将这轮迭代的所有 ActionResult 作为一个整体添加到历史中
     */
    private void recordIterationResults(List<ActionResult> results, AgentContext context) {
        if (context == null || results == null || results.isEmpty()) {
            return;
        }
        
        // 确保历史列表存在
        if (context.getActionExecutionHistory() == null) {
            context.setActionExecutionHistory(new ArrayList<>());
        }
        
        // 直接将这轮迭代的所有结果添加为一个整体
        // 创建新列表以避免外部修改影响
        List<ActionResult> iterationResults = new ArrayList<>(results);
        context.getActionExecutionHistory().add(iterationResults);
        
        int iterationNumber = context.getActionExecutionHistory().size();
        log.debug("记录第 {} 轮迭代的 {} 个动作执行结果", iterationNumber, results.size());
    }
    
    // ========== 格式化工具方法 ==========
    
    /**
     * 截断字符串到指定长度
     */
    private String truncate(String str, Integer maxLength) {
        if (str == null) return "";
        if (maxLength==null){
            return str;
        }
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
    
    /**
     * 格式化工具输出结果
     * 根据结果类型智能格式化
     */
    private String formatToolOutput(ToolExecutionResult toolResult) {
        if (toolResult == null) {
            return "执行成功（无返回值）";
        }
        
        String resultStr = parseToolResult(toolResult); // 使用已有方法
        
        // 如果结果过长，截断到合理长度（500字符）
//        int maxLength = 500;
//        if (resultStr.length() > maxLength) {
//            return resultStr.substring(0, maxLength) + "...(已截断，完整结果" + resultStr.length() + "字符)";
//        }
//
        return resultStr;
    }
    
    /**
     * 格式化 RAG 输出结果
     * 包含检索到的文档数量和前3个文档的摘要
     */
    private String formatRAGOutput(AgentKnowledgeResult knowledgeResult) {
        if (knowledgeResult == null || knowledgeResult.getTotalCount() == 0) {
            return "未检索到相关知识";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("检索到").append(knowledgeResult.getTotalCount()).append("条知识：\n");

        // 展示文档
        for (int i = 0; i < knowledgeResult.getDocuments().size(); i++) {
            var doc = knowledgeResult.getDocuments().get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append("标题：").append(doc.getDocName() != null ? doc.getDocName() : "未知").append("\n");
            sb.append("    内容：").append(truncate(doc.getContent(), null)).append("\n");
        }

        
        return sb.toString();
    }
    
    /**
     * 格式化 LLM 输出结果
     * 截断到合理长度（300字符），保留完整信息供 AI 理解
     */
    private String formatLLMOutput(String llmResponse) {
        if (llmResponse == null || llmResponse.isEmpty()) {
            return "生成内容为空";
        }
        
        // LLM 生成的内容可能较长，截断到300字符用于提示词
//        int maxLength = 300;
//        if (llmResponse.length() > maxLength) {
//            return llmResponse.substring(0, maxLength) + "...(已截断，完整回复" + llmResponse.length() + "字符)";
//        }
        
        return llmResponse;
    }
    
    /**
     * 格式化直接响应输出结果
     */
    private String formatDirectResponseOutput(String content) {
        if (content == null || content.isEmpty()) {
            return "响应内容为空";
        }
        
//        int maxLength = 300;
//        if (content.length() > maxLength) {
//            return content.substring(0, maxLength) + "...(已截断，完整响应" + content.length() + "字符)";
//        }
        
        return content;
    }
    
}

