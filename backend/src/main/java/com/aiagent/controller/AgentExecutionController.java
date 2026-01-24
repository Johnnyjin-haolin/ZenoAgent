package com.aiagent.controller;

import com.aiagent.enums.ErrorCode;
import com.aiagent.service.IAgentService;
import com.aiagent.vo.AgentRequest;
import com.aiagent.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Agent 执行相关接口
 */
@Slf4j
@RestController
@RequestMapping("/aiagent")
public class AgentExecutionController {

    @Autowired
    private IAgentService agentService;

    /**
     * 执行Agent任务
     */
    @PostMapping("/execute")
    public SseEmitter execute(@RequestBody AgentRequest request) {
        log.info("收到Agent执行请求: {}", request.getContent());
        return agentService.execute(request);
    }

    /**
     * 停止Agent执行
     */
    @PostMapping("/stop/{requestId}")
    public ResponseEntity<Result<Boolean>> stop(@PathVariable String requestId) {
        boolean success = agentService.stop(requestId);
        if (success) {
            return ResponseEntity.ok(Result.success("Agent已停止", true));
        }
        return ResponseEntity.ok(Result.error(ErrorCode.NOT_FOUND, "未找到对应的Agent任务"));
    }

    /**
     * 清除会话记忆
     */
    @DeleteMapping("/memory/{conversationId}")
    public ResponseEntity<Result<Boolean>> clearMemory(@PathVariable String conversationId) {
        boolean success = agentService.clearMemory(conversationId);
        if (success) {
            return ResponseEntity.ok(Result.success("记忆已清除", true));
        }
        return ResponseEntity.ok(Result.error(ErrorCode.INTERNAL_ERROR, "清除失败"));
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Result<Map<String, String>>> health() {
        return ResponseEntity.ok(Result.success(Map.of("status", "ok", "message", "AI Agent服务正常运行")));
    }
}

