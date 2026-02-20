package com.aiagent.application;

import com.aiagent.common.constant.AgentConstants;
import com.aiagent.common.util.LocalCache;
import com.aiagent.api.dto.AgentEventData;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;

/**
 * SSE 事件发送与连接管理
 */
@Slf4j
@Service
public class AgentStreamingService {

    public SseEmitter createEmitter(String requestId) {
        SseEmitter emitter = new SseEmitter(AgentConstants.SSE_TIMEOUT_MILLIS);
        emitter.onError(throwable -> {
            log.error("SSE连接错误: {}", throwable.getMessage());
            removeEmitter(requestId);
            try {
                emitter.complete();
            } catch (Exception ignore) {}
        });
        return emitter;
    }

    public void cacheEmitter(String requestId, SseEmitter emitter) {
        LocalCache.put(AgentConstants.CACHE_PREFIX_AGENT_SSE, requestId, emitter);
    }

    public SseEmitter getEmitter(String requestId) {
        return LocalCache.get(AgentConstants.CACHE_PREFIX_AGENT_SSE, requestId);
    }

    public void removeEmitter(String requestId) {
        LocalCache.remove(AgentConstants.CACHE_PREFIX_AGENT_SSE, requestId);
    }

    public void sendEvent(SseEmitter emitter, AgentEventData eventData) {
        try {
            String eventName = eventData.getEvent();
            if (eventName == null || eventName.isBlank()) {
                eventName = AgentConstants.EVENT_AGENT_MESSAGE;
            }
            String eventStr = JSON.toJSONString(eventData);
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(Objects.requireNonNull(eventStr)));
            log.debug("发送Agent事件: {}", eventName);
        } catch (IllegalStateException e) {
            log.debug("SSE连接已关闭，忽略事件发送: {}", eventData.getEvent());
        } catch (IOException e) {
            log.error("发送SSE事件失败", e);
        }
    }

    public void closeEmitter(SseEmitter emitter, String requestId) {
        try {
            sendEvent(emitter, AgentEventData.builder()
                .requestId(requestId)
                .event(AgentConstants.EVENT_AGENT_COMPLETE)
                .message(AgentConstants.MESSAGE_TASK_COMPLETE)
                .build());
        } catch (Exception e) {
            log.error("发送完成事件失败", e);
        } finally {
            removeEmitter(requestId);
            try {
                emitter.complete();
            } catch (Exception ignore) {}
        }
    }
}
