package com.aiagent.infrastructure.external.mcp;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 用户回答等待管理器
 * 工具 AskUserQuestionTool 调用后，通过此类阻塞等待前端用户的回答
 * 设计参照 ToolConfirmationManager，使用 Redis BlockingQueue 实现跨实例通信
 *
 * @author aiagent
 */
@Slf4j
@Component
public class UserAnswerManager {

    private static final String KEY_PREFIX = "aiagent:user:answer:";
    private static final long DEFAULT_EXPIRE_MS = 10 * 60_000L;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 注册待回答的问题
     */
    public void register(String questionId) {
        RBlockingQueue<String> queue = getQueue(questionId);
        queue.expire(Duration.ofMillis(DEFAULT_EXPIRE_MS));
        log.debug("注册用户提问: questionId={}", questionId);
    }

    /**
     * 阻塞等待用户回答，超时返回 null
     */
    public String waitForAnswer(String questionId, long timeoutMs) {
        RBlockingQueue<String> queue = getQueue(questionId);
        queue.expire(Duration.ofMillis(Math.max(timeoutMs * 2, DEFAULT_EXPIRE_MS)));
        try {
            String answer = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (answer == null) {
                log.warn("等待用户回答超时: questionId={}", questionId);
            }
            return answer;
        } catch (Exception e) {
            log.warn("等待用户回答异常: questionId={}", questionId, e);
            return null;
        } finally {
            try {
                queue.delete();
            } catch (Exception e) {
                log.debug("清理回答队列失败: questionId={}", questionId, e);
            }
        }
    }

    /**
     * 接收用户回答（由 API 接口调用，解除阻塞）
     */
    public boolean receiveAnswer(String questionId, String answer) {
        RBlockingQueue<String> queue = getQueue(questionId);
        boolean offered = queue.offer(answer);
        if (!offered) {
            log.warn("提交用户回答失败: questionId={}", questionId);
        }
        log.info("用户回答已接收: questionId={}, answer={}", questionId, answer);
        return offered;
    }

    private RBlockingQueue<String> getQueue(String questionId) {
        return redissonClient.getBlockingQueue(KEY_PREFIX + questionId);
    }
}
