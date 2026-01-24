package com.aiagent.infrastructure.external.mcp;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行确认管理器
 * 用于手动模式下等待用户确认/拒绝
 *
 * @author aiagent
 */
@Slf4j
@Component
public class ToolConfirmationManager {

    private static final String KEY_PREFIX = "aiagent:tool:confirm:";
    private static final long DEFAULT_EXPIRE_MS = 5 * 60_000L;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 注册待确认的工具调用
     */
    public void register(String toolExecutionId) {
        RBlockingQueue<String> queue = getQueue(toolExecutionId);
        queue.expire(Duration.ofMillis(DEFAULT_EXPIRE_MS));
    }

    /**
     * 等待用户确认结果
     */
    public ToolConfirmationDecision waitForDecision(String toolExecutionId, long timeoutMs) {
        RBlockingQueue<String> queue = getQueue(toolExecutionId);
        queue.expire(Duration.ofMillis(Math.max(timeoutMs * 2, DEFAULT_EXPIRE_MS)));
        try {
            String decision = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (decision == null) {
                log.warn("等待用户确认超时: toolExecutionId={}", toolExecutionId);
                return ToolConfirmationDecision.TIMEOUT;
            }
            return "APPROVED".equalsIgnoreCase(decision)
                ? ToolConfirmationDecision.APPROVED
                : ToolConfirmationDecision.REJECTED;
        } catch (Exception e) {
            log.warn("等待用户确认异常: toolExecutionId={}", toolExecutionId, e);
            return ToolConfirmationDecision.REJECTED;
        } finally {
            try {
                queue.delete();
            } catch (Exception e) {
                log.debug("清理确认队列失败: toolExecutionId={}", toolExecutionId, e);
            }
        }
    }

    /**
     * 用户确认执行
     */
    public boolean approve(String toolExecutionId) {
        return complete(toolExecutionId, "APPROVED");
    }

    /**
     * 用户拒绝执行
     */
    public boolean reject(String toolExecutionId) {
        return complete(toolExecutionId, "REJECTED");
    }

    private boolean complete(String toolExecutionId, String decision) {
        RBlockingQueue<String> queue = getQueue(toolExecutionId);
        boolean offered = queue.offer(decision);
        if (!offered) {
            log.warn("确认结果提交失败: toolExecutionId={}, decision={}", toolExecutionId, decision);
        }
        return offered;
    }

    private RBlockingQueue<String> getQueue(String toolExecutionId) {
        String key = KEY_PREFIX + toolExecutionId;
        return redissonClient.getBlockingQueue(key);
    }
}

