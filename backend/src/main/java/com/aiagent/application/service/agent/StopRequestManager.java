package com.aiagent.application.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 停止请求管理器
 * 用于多实例部署场景下的停止信号传递
 * 
 * 参考 ToolConfirmationManager 实现，使用 Redis 存储停止标志
 * 支持跨实例通信：停止请求和任务执行可能在不同的机器上
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class StopRequestManager {

    private static final String KEY_PREFIX = "aiagent:stop:request:";
    private static final long DEFAULT_EXPIRE_MS = 5 * 60_000L; // 5分钟

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 设置停止标志
     * 
     * @param requestId 请求ID
     * @return 是否设置成功
     */
    public boolean setStopFlag(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            log.warn("设置停止标志失败: requestId 为空");
            return false;
        }
        
        try {
            String key = KEY_PREFIX + requestId;
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set("STOP");
            bucket.expire(Duration.ofMillis(DEFAULT_EXPIRE_MS));
            
            log.info("设置停止标志成功: requestId={}", requestId);
            return true;
        } catch (Exception e) {
            log.error("设置停止标志异常: requestId={}", requestId, e);
            return false;
        }
    }

    /**
     * 检查是否有停止请求
     * 
     * @param requestId 请求ID
     * @return true-需要停止，false-继续执行
     */
    public boolean isStopRequested(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return false;
        }
        
        try {
            String key = KEY_PREFIX + requestId;
            RBucket<String> bucket = redissonClient.getBucket(key);
            boolean exists = bucket.isExists();
            
            if (exists) {
                log.debug("检测到停止标志: requestId={}", requestId);
            }
            
            return exists;
        } catch (Exception e) {
            log.error("检查停止标志异常: requestId={}", requestId, e);
            return false;
        }
    }

    /**
     * 清除停止标志
     * 
     * @param requestId 请求ID
     */
    public void clearStopFlag(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return;
        }
        
        try {
            String key = KEY_PREFIX + requestId;
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.delete();
            log.debug("清除停止标志: requestId={}", requestId);
        } catch (Exception e) {
            log.warn("清除停止标志失败: requestId={}", requestId, e);
        }
    }
}

