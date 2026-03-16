package com.aiagent.application;

import com.aiagent.api.dto.AgentEventData;
import com.aiagent.common.constant.AgentConstants;

/**
 * Agent 推理引擎的事件发布接口
 *
 * <p>引擎只依赖此接口，不感知底层传输协议（SSE / WebSocket / 日志等）。
 * 通过实现此接口可以将 Agent 事件路由到任意前端协议，无需改动引擎代码。
 *
 * <p>当前实现：
 * <ul>
 *   <li>{@link SseAgentEventPublisher} - 将事件序列化为 SSE 推送给浏览器</li>
 * </ul>
 */
public interface AgentEventPublisher {

    /**
     * 工具调用开始（自动模式）
     *
     * @param toolName 工具名称
     * @param params   工具参数（已解析为 Map 或原始字符串）
     */
    void onToolCall(String toolName, Object params);

    /**
     * 工具调用开始（支持手动确认模式）
     *
     * @param toolName             工具名称
     * @param params               工具参数
     * @param requiresConfirmation 是否需要用户确认（MANUAL 模式下为 true）
     * @param toolExecutionId      工具执行 ID，需要确认时由前端回传
     */
    void onToolCall(String toolName, Object params, boolean requiresConfirmation, String toolExecutionId);

    /**
     * 工具调用完成
     *
     * @param toolName   工具名称
     * @param result     工具返回内容
     * @param durationMs 执行耗时（毫秒）
     * @param error      错误信息，正常完成时为 null
     */
    void onToolResult(String toolName, Object result, long durationMs, String error);

    /**
     * 最终回复的流式 token（直接追加到对话内容）
     *
     * @param token 文本片段
     */
    void onToken(String token);

    /**
     * 中间推理过程的流式 token（工具调用轮的思考内容，展示在思考步骤区）
     *
     * @param token 文本片段
     */
    void onThinkingToken(String token);

    /**
     * 向浏览器下发 PERSONAL MCP 工具调用请求（客户端执行）
     * <p>
     * 浏览器收到此事件后，使用 localStorage 中存储的密钥调用本地 MCP，
     * 执行完成后通过 POST /api/mcp/client-tool-result 将结果回传。
     *
     * @param callId   唯一调用 ID（与 ClientToolCallManager 中的 Future 关联）
     * @param toolName MCP 工具名称
     * @param serverId 对应的 PERSONAL MCP 服务器 ID
     * @param params   工具参数（已解析为 Map 或原始字符串）
     */
    default void onPersonalToolCall(String callId, String toolName, String serverId, Object params) {
        // 默认空实现，SseAgentEventPublisher 覆盖此方法
    }

    /**
     * 所有流式 token 发送完毕（最终回复已完整输出）
     */
    void onStreamComplete();

    /**
     * 整个 Agent 任务完成
     */
    void onComplete();

    /**
     * 发生不可恢复的错误
     *
     * @param error 错误描述
     */
    void onError(String error);

    // ── 兼容旧调用方式（过渡期使用，后续移除）────────────────────────────────

    /**
     * 旧式事件发布兼容入口（{@code publisher.accept(eventData)} → {@code publisher.publish(eventData)}）
     * <p>将 {@link AgentEventData} 按 event 类型路由到对应语义方法。
     * 遗留代码（PromptReActEngine、ActionExecutor 等）可继续使用此方法，无需立即改造。
     */
    default void accept(AgentEventData eventData) {
        if (eventData == null) return;
        String event = eventData.getEvent();
        if (event == null) return;

        switch (event) {
            case AgentConstants.EVENT_AGENT_MESSAGE:
                onToken(eventData.getContent() != null ? eventData.getContent() : "");
                break;
            case AgentConstants.EVENT_AGENT_THINKING_DELTA:
                onThinkingToken(eventData.getContent() != null ? eventData.getContent() : "");
                break;
            case AgentConstants.EVENT_AGENT_STREAM_COMPLETE:
                onStreamComplete();
                break;
            case AgentConstants.EVENT_AGENT_COMPLETE:
                onComplete();
                break;
            case AgentConstants.EVENT_AGENT_ERROR:
                onError(eventData.getMessage() != null ? eventData.getMessage() : "unknown error");
                break;
            case AgentConstants.EVENT_AGENT_TOOL_CALL: {
                String toolName = eventData.getData() instanceof java.util.Map
                    ? (String) ((java.util.Map<?, ?>) eventData.getData()).get("toolName")
                    : eventData.getMessage();
                onToolCall(toolName != null ? toolName : "unknown", eventData.getData());
                break;
            }
            case AgentConstants.EVENT_AGENT_TOOL_RESULT: {
                String toolName = eventData.getData() instanceof java.util.Map
                    ? (String) ((java.util.Map<?, ?>) eventData.getData()).get("toolName")
                    : eventData.getMessage();
                Object result = eventData.getData() instanceof java.util.Map
                    ? ((java.util.Map<?, ?>) eventData.getData()).get("result")
                    : null;
                Object dur = eventData.getData() instanceof java.util.Map
                    ? ((java.util.Map<?, ?>) eventData.getData()).get("duration")
                    : null;
                long durationMs = dur instanceof Number ? ((Number) dur).longValue() : 0L;
                onToolResult(toolName != null ? toolName : "unknown", result, durationMs, null);
                break;
            }
            default:
                // 其他事件直接透传：通过 SseAgentEventPublisher 的 send 方法处理
                // 此处无法直接路由，忽略即可（或子类 override 此方法做扩展）
                break;
        }
    }
}
