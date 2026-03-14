package com.aiagent.application;

import com.aiagent.domain.context.AgentContextService;
import com.aiagent.domain.model.bo.MessageBO;
import com.aiagent.infrastructure.config.AgentConfig;
import com.aiagent.common.constant.AgentConstants;
import com.aiagent.domain.conversation.ConversationService;
import com.aiagent.domain.model.bo.AgentExecutionResult;
import com.aiagent.domain.memory.MemorySystem;
import com.aiagent.api.dto.AgentEventData;
import com.aiagent.api.dto.AgentRequest;
import com.aiagent.domain.model.bo.AgentContext;
import com.aiagent.common.util.UUIDGenerator;
import com.aiagent.application.SseAgentEventPublisher;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * AI Agent 服务实现
 *
 * <p>当前默认使用 {@link FunctionCallingEngine}（支持 Function Calling 的主流模型）。
 * 如需切换为 Prompt 引导的备选引擎，将 {@code @Qualifier} 值改为 {@code "promptReActEngine"}。
 *
 * @see FunctionCallingEngine
 * @see PromptReActEngine
 * @author aiagent
 */
@Slf4j
@Service
public class AgentServiceImpl implements IAgentService {
    private static final long STEP_EVENT_THRESHOLD_MS = 300;
    
    @Autowired
    @Qualifier("functionCallingEngine")
    private AgentEngine agentEngine;

    @Autowired
    private MemorySystem memorySystem;
    
    @Autowired
    private AgentStateMachine stateMachine;
    
    @Autowired
    private AgentConfig agentConfig;
    
    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentContextService agentContextService;

    @Autowired
    private AgentStreamingService streamingService;
    
    @Autowired
    private StopRequestManager stopRequestManager;

    
    @Override
    //todo 前端UI界面参照Google的炫酷画面
    public SseEmitter execute(AgentRequest request) {
        log.info("开始执行Agent任务（ReAct架构）: {}", request.getContent());
        
        // 标准化会话ID：前端临时ID不直接入库
        request.setConversationId(agentContextService.normalizeConversationId(request.getConversationId()));
        
        // 1. 创建SSE连接
        String requestId = UUIDGenerator.generate();
        SseEmitter emitter = streamingService.createEmitter(requestId);
        
        // 3. 发送初始事件
        streamingService.sendEvent(emitter, AgentEventData.builder()
            .requestId(requestId)
            .event(AgentConstants.EVENT_AGENT_START)
            .message(AgentConstants.MESSAGE_AGENT_START)
            .conversationId(request.getConversationId())
            .build());
        
        // 4. 缓存emitter
        streamingService.cacheEmitter(requestId, emitter);
        
        // 5. 异步执行任务
        CompletableFuture.runAsync(() -> {
            try {
                executeWithEngine(request, requestId, emitter);
            } catch (Exception e) {
                log.error("Agent任务执行失败", e);
                streamingService.sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_ERROR)
                    .message("执行失败: " + e.getMessage())
                    .conversationId(request.getConversationId())
                    .build());
                streamingService.closeEmitter(emitter, requestId);
            }
        });
        
        return emitter;
    }
    
    /**
     * 使用 AgentEngine 执行任务
     */
    private void executeWithEngine(AgentRequest request, String requestId, SseEmitter emitter) {
        long totalStartNs = System.nanoTime();
        long stepStartNs = System.nanoTime();
        
        // 1. 加载或创建上下文
        final AgentContext context = agentContextService.loadOrCreateContext(request);
        String conversationId = context.getConversationId();
        
        try {
            stepStartNs = logStep("load_context", stepStartNs, requestId, conversationId, null, emitter);
                
            // 1.1 确保会话在MySQL中存在（如果不存在则创建）
            agentContextService.ensureConversationExists(conversationId, request);
            stepStartNs = logStep("ensure_conversation", stepStartNs, requestId, conversationId, null, emitter);

            // 2. 保存用户消息到记忆和MySQL（统一通过MemorySystem处理）
            UserMessage userMessage = new UserMessage(request.getContent());
            memorySystem.saveShortTermMemory(conversationId, userMessage, null, null, null, null);
            context.addMessage(userMessage);
            stepStartNs = logStep("save_user_message", stepStartNs, requestId, conversationId, null, emitter);

            // 3. 设置上下文变量（使用具体属性）
            // 智能选择模型：如果未指定modelId，使用配置的默认模型
            String modelId = request.getModelId();
            if (modelId == null || modelId.trim().isEmpty()) {
                modelId = agentConfig.getModel().getDefaultModelId();
                log.info("未指定模型，使用默认模型: {}", modelId);

                // 发送模型选择事件
                streamingService.sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_THINKING)
                    .message("使用默认模型: " + modelId)
                    .conversationId(context.getConversationId())
                    .build());
            } else {
                log.info("使用指定模型: {}", modelId);
            }
            context.setModelId(modelId);
            context.setEnabledMcpGroups(request.getEnabledMcpGroups());
            context.setKnowledgeIds(request.getKnowledgeIds());
            // 设置启用的工具名称列表（为空则允许所有工具）
            context.setEnabledTools(request.getEnabledTools());
            context.setRequestId(requestId);
            stepStartNs = logStep("init_context_vars", stepStartNs, requestId, conversationId,
                "modelId=" + modelId, emitter);

            // 设置事件发布器（SseAgentEventPublisher 将语义调用翻译为 SSE 事件）
            SseAgentEventPublisher publisher = new SseAgentEventPublisher(
                requestId, context.getConversationId(), emitter, streamingService);
            context.setEventPublisher(publisher);

            // 3.1 设置流式输出回调（使用CountDownLatch确保流式完成后再关闭SSE）
            // onToken / onComplete 委托给 publisher 路由，引擎内部只调 publisher 接口
            CountDownLatch streamingCompleteLatch = new CountDownLatch(1);
            context.setStreamingCallback(new StreamingCallback() {
                @Override
                public void onToken(String token) {
                    // FunctionCallingEngine 内部已通过 publisher.onToken / publisher.onThinkingToken 路由
                    // 此处仅作兜底（PromptReActEngine 等可能直接触发此回调）
                    publisher.onToken(token);
                }

                @Override
                public void onComplete(String fullText) {
                    log.debug("LLM生成完成，文本长度: {}", fullText.length());
                    publisher.onStreamComplete();
                    // 释放锁，允许主线程继续执行并关闭SSE
                    streamingCompleteLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    log.error("LLM流式输出错误", error);
                    publisher.onError("生成失败: " + error.getMessage());
                    // 发生错误也要释放锁
                    streamingCompleteLatch.countDown();
                }

                @Override
                public void onStart() {
                    log.debug("LLM开始生成");
                }
            });
            stepStartNs = logStep("init_streaming_callback", stepStartNs, requestId, conversationId, null, emitter);

            // 3.2 如果有知识库，执行预检索（仅在第一次请求时）
            if (context.getKnowledgeIds() != null && !context.getKnowledgeIds().isEmpty()) {
                long ragStartNs = System.nanoTime();
                agentContextService.performInitialRagRetrieval(
                    request,
                    context,
                    requestId,
                    eventData -> streamingService.sendEvent(emitter, eventData)
                );
                stepStartNs = logStep("rag_pre_retrieval", ragStartNs, requestId, conversationId,
                    "knowledgeCount=" + context.getKnowledgeIds().size(), emitter);
            }

            // 4. 注册状态变更监听器
            stateMachine.initialize(context, newState -> {
                streamingService.sendEvent(emitter, AgentEventData.builder()
                    .requestId(requestId)
                    .event(AgentConstants.EVENT_AGENT_THINKING)
                    .message("状态: " + newState.getDescription())
                    .conversationId(context.getConversationId())
                    .build());
            });
            stepStartNs = logStep("init_state_machine", stepStartNs, requestId, conversationId, null, emitter);

            // 5. 执行推理循环
            streamingService.sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_THINKING)
                .message("开始推理循环...")
                .conversationId(context.getConversationId())
                .build());

            long engineStartNs = System.nanoTime();
            AgentExecutionResult executionResult = agentEngine.execute(context);
            stepStartNs = logStep("engine_execute", engineStartNs, requestId, conversationId,
                "modelId=" + modelId + ", iterations=" + executionResult.getIterations(), emitter);

            // 6. 批量保存本轮新增的 AI/Tool 消息（UserMessage 已在步骤2保存，此处跳过）
            // executionResult.getMessages() 是完整消息列表，新增消息 = 总数 - 加载到历史消息数 - 1(本轮user)
            // 但最简单可靠的方式：保存 context 中类型非 USER 且非 SYSTEM 的新增消息
            saveNewAssistantMessages(context, context.getModelId(), conversationId);
            stepStartNs = logStep("save_assistant_messages", stepStartNs, requestId, conversationId, null, emitter);

            // 更新对话消息数量
            try {
                conversationService.incrementMessageCount(context.getConversationId());
            } catch (Exception e) {
                log.warn("更新消息数量失败: conversationId={}", context.getConversationId(), e);
            }
            stepStartNs = logStep("increment_message_count", stepStartNs, requestId, conversationId, null, emitter);


            // 7. 保存上下文
            memorySystem.saveContext(context);
            stepStartNs = logStep("save_context", stepStartNs, requestId, conversationId, null, emitter);

            // 8. 关闭SSE（此时所有流式事件都已发送完成）
            streamingService.closeEmitter(emitter, requestId);
            logStep("close_emitter", System.nanoTime(), requestId, conversationId, null, emitter);
            logStep("total", totalStartNs, requestId, conversationId, null, emitter);
            
        } finally {
            // 清除 Redis 中的停止标志（防止遗留）
            stopRequestManager.clearStopFlag(requestId);
        }
    }
    
    @Override
    public boolean stop(String requestId) {
        log.info("收到停止请求: requestId={}", requestId);
        
        // 1. 在 Redis 中设置停止标志（支持多实例）
        boolean success = stopRequestManager.setStopFlag(requestId);
        
        if (!success) {
            log.warn("设置停止标志失败: requestId={}", requestId);
            return false;
        }
        
        // 2. 尝试关闭本机的 SSE emitter（如果存在）
        SseEmitter emitter = streamingService.getEmitter(requestId);
        if (emitter != null) {
            streamingService.sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_COMPLETE)
                .message("用户已停止")
                .build());
            streamingService.closeEmitter(emitter, requestId);
            log.info("已关闭本机 SSE 连接: requestId={}", requestId);
        } else {
            log.info("未找到本机 SSE 连接（任务可能在其他实例执行）: requestId={}", requestId);
        }
        
        return true;
    }
    
    @Override
    public boolean clearMemory(String conversationId) {
        memorySystem.clearMemory(conversationId);
        return true;
    }

    private long logStep(String step, long startNs, String requestId, String conversationId,
                         String extra, SseEmitter emitter) {
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;
        String extraInfo = (extra == null || extra.isEmpty()) ? "-" : extra;
        log.info("agent_step|requestId={} conversationId={} step={} durationMs={} extra={}",
            requestId, conversationId, step, durationMs, extraInfo);

        if (durationMs >= STEP_EVENT_THRESHOLD_MS && emitter != null) {
            streamingService.sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_THINKING)
                .message("步骤耗时: " + step + " " + durationMs + "ms")
                .conversationId(conversationId)
                .build());
        }
        return System.nanoTime();
    }

    /**
     * 批量保存本轮新增的 AI/Tool 消息到 MySQL
     * 策略：从 messageDTOs 中找出新增的非 USER 消息（通过简单时序判断）
     */
    private void saveNewAssistantMessages(AgentContext context, String modelId, String conversationId) {
        List<MessageBO> allDTOs = context.getMessageBOS();
        if (allDTOs == null || allDTOs.isEmpty()) {
            return;
        }

        // 简单策略：遍历最后几条消息，保存所有 AI / TOOL_EXECUTION 类型
        // （因为执行循环中多次 addMessage，这些消息一定是新增的）
        int savedCount = 0;
        for (int i = allDTOs.size() - 1; i >= 0 && savedCount < 20; i--) {
            MessageBO dto = allDTOs.get(i);
            String type = dto.getType();

            // 只保存 AI 和 TOOL_EXECUTION，跳过 USER/SYSTEM
            if ("AI".equals(type) || "TOOL_EXECUTION".equals(type)) {
                try {
                    ChatMessage message = dto.toChatMessage();
                    if (message != null) {
                        memorySystem.saveShortTermMemory(conversationId, message, modelId, null, null, null);
                        savedCount++;
                    }
                } catch (Exception e) {
                    log.warn("保存消息失败，跳过: type={}, error={}", type, e.getMessage());
                }
            } else if ("USER".equals(type)) {
                // 遇到 USER 消息说明前面的都是历史消息，停止遍历
                break;
            }
        }

        if (savedCount > 0) {
            log.info("批量保存本轮新增消息完成: conversationId={}, count={}", conversationId, savedCount);
        }
    }
}

