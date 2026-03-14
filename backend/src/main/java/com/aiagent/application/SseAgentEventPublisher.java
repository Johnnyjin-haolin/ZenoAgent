package com.aiagent.application;

import com.aiagent.api.dto.AgentEventData;
import com.aiagent.common.constant.AgentConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 基于 SSE 的 AgentEventPublisher 实现
 *
 * <p>将 {@link AgentEventPublisher} 的语义调用翻译为 {@link AgentEventData} SSE 事件，
 * 通过 {@link AgentStreamingService} 推送到浏览器。
 *
 * <p>此类由 {@link AgentServiceImpl} 在每次请求时实例化，持有当次请求的
 * {@code requestId}、{@code conversationId} 和 {@code SseEmitter}，完全无状态共享。
 */
@Slf4j
public class SseAgentEventPublisher implements AgentEventPublisher {

    private final String requestId;
    private final String conversationId;
    private final SseEmitter emitter;
    private final AgentStreamingService streamingService;

    public SseAgentEventPublisher(String requestId,
                                  String conversationId,
                                  SseEmitter emitter,
                                  AgentStreamingService streamingService) {
        this.requestId = requestId;
        this.conversationId = conversationId;
        this.emitter = emitter;
        this.streamingService = streamingService;
    }

    @Override
    public void onToolCall(String toolName, Object params) {
        onToolCall(toolName, params, false, null);
    }

    @Override
    public void onToolCall(String toolName, Object params, boolean requiresConfirmation, String toolExecutionId) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("toolName", toolName);
        data.put("params", params);
        data.put("requiresConfirmation", requiresConfirmation);
        if (toolExecutionId != null) {
            data.put("toolExecutionId", toolExecutionId);
        }
        String msg = requiresConfirmation
            ? "等待确认工具: " + toolName
            : "调用工具: " + toolName;
        send(AgentConstants.EVENT_AGENT_TOOL_CALL, msg, null, data);
    }

    @Override
    public void onToolResult(String toolName, Object result, long durationMs, String error) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("toolName", toolName);
        data.put("result", result);
        data.put("duration", durationMs);
        if (error != null) {
            data.put("error", error);
        }
        send(AgentConstants.EVENT_AGENT_TOOL_RESULT, "工具执行完成: " + toolName, null, data);
    }

    @Override
    public void onToken(String token) {
        // 最终回复 token，对应前端 agent:message 事件，直接追加到 content
        streamingService.sendEvent(emitter, AgentEventData.builder()
            .requestId(requestId)
            .event(AgentConstants.EVENT_AGENT_MESSAGE)
            .content(token)
            .conversationId(conversationId)
            .build());
    }

    @Override
    public void onThinkingToken(String token) {
        // 中间推理 token，展示在思考步骤折叠区
        streamingService.sendEvent(emitter, AgentEventData.builder()
            .requestId(requestId)
            .event(AgentConstants.EVENT_AGENT_THINKING_DELTA)
            .content(token)
            .conversationId(conversationId)
            .build());
    }

    @Override
    public void onStreamComplete() {
        send(AgentConstants.EVENT_AGENT_STREAM_COMPLETE, AgentConstants.MESSAGE_STREAM_COMPLETE, null, null);
    }

    @Override
    public void onComplete() {
        send(AgentConstants.EVENT_AGENT_COMPLETE, AgentConstants.MESSAGE_TASK_COMPLETE, null, null);
    }

    @Override
    public void onError(String error) {
        send(AgentConstants.EVENT_AGENT_ERROR, error, null, null);
    }

    // ── 内部工具方法 ──────────────────────────────────────────────────────────

    private void send(String event, String message, String content, Object data) {
        try {
            AgentEventData.AgentEventDataBuilder builder = AgentEventData.builder()
                .requestId(requestId)
                .event(event)
                .message(message)
                .conversationId(conversationId);
            if (content != null) {
                builder.content(content);
            }
            if (data != null) {
                builder.data(data);
            }
            streamingService.sendEvent(emitter, builder.build());
        } catch (Exception e) {
            log.debug("发送 SSE 事件失败: event={}", event, e);
        }
    }
}
