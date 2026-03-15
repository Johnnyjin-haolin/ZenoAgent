package com.aiagent.infrastructure.external.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 客户端工具调用管理器（PERSONAL MCP 专用）
 * <p>
 * 流程：
 * 1. FunctionCallingEngine 发现 PERSONAL 类型工具时，通过 {@link #newCall} 创建一个 Future；
 * 2. 通过 SSE 向浏览器下发 {@code PERSONAL_TOOL_CALL} 事件（携带 callId + 参数）；
 * 3. 浏览器调用本地 MCP，执行完成后 POST /api/mcp/client-tool-result；
 * 4. Controller 调用 {@link #complete} 或 {@link #fail} 唤醒 Future；
 * 5. FunctionCallingEngine 拿到结果继续推理。
 */
@Slf4j
@Component
public class ClientToolCallManager {

    /** 等待超时时间（秒），超时后向 LLM 报告工具执行超时错误 */
    private static final long TIMEOUT_SECONDS = 60L;

    /** callId → CompletableFuture<String>（结果 JSON 字符串） */
    private final Map<String, CompletableFuture<String>> pendingCalls = new ConcurrentHashMap<>();

    /**
     * 注册一个新的客户端工具调用，返回 callId 和 Future。
     *
     * @return callId（唯一标识此次调用）
     */
    public String newCall(CompletableFuture<String> future) {
        String callId = UUID.randomUUID().toString();
        pendingCalls.put(callId, future);
        log.debug("注册客户端工具调用: callId={}", callId);
        return callId;
    }

    /**
     * 阻塞等待客户端结果（由 FunctionCallingEngine 调用）
     *
     * @param callId 调用 ID
     * @return 工具执行结果 JSON
     * @throws RuntimeException 超时或执行失败
     */
    public String waitForResult(String callId) {
        CompletableFuture<String> future = pendingCalls.get(callId);
        if (future == null) {
            throw new IllegalStateException("未找到客户端工具调用: callId=" + callId);
        }
        try {
            String result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("客户端工具执行成功: callId={}", callId);
            return result;
        } catch (TimeoutException e) {
            log.warn("客户端工具执行超时: callId={}", callId);
            throw new RuntimeException("客户端工具执行超时（" + TIMEOUT_SECONDS + "s）");
        } catch (Exception e) {
            throw new RuntimeException("客户端工具执行失败: " + e.getMessage(), e);
        } finally {
            pendingCalls.remove(callId);
        }
    }

    /**
     * 客户端工具执行成功，唤醒等待的 Future
     *
     * @param callId 调用 ID
     * @param result 工具结果 JSON
     */
    public void complete(String callId, String result) {
        CompletableFuture<String> future = pendingCalls.get(callId);
        if (future != null) {
            future.complete(result);
            log.debug("客户端工具回传成功: callId={}", callId);
        } else {
            log.warn("未找到等待中的调用，可能已超时: callId={}", callId);
        }
    }

    /**
     * 客户端工具执行失败，唤醒等待的 Future 并传播错误
     *
     * @param callId 调用 ID
     * @param error  错误信息
     */
    public void fail(String callId, String error) {
        CompletableFuture<String> future = pendingCalls.get(callId);
        if (future != null) {
            future.completeExceptionally(new RuntimeException(error));
            log.warn("客户端工具执行失败: callId={}, error={}", callId, error);
        } else {
            log.warn("未找到等待中的调用，可能已超时: callId={}", callId);
        }
    }

    /**
     * 获取当前待处理调用数（监控用）
     */
    public int getPendingCount() {
        return pendingCalls.size();
    }
}
