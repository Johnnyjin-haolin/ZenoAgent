package com.aiagent.api.controller;

import com.aiagent.shared.response.ErrorCode;
import com.aiagent.application.service.agent.IAgentService;
import com.aiagent.api.dto.AgentRequest;
import com.aiagent.api.dto.HealthResponse;
import com.aiagent.shared.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 执行相关接口
 */
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
        return agentService.execute(request);
    }

    /**
     * 停止Agent执行
     */
    @PostMapping("/stop/{requestId}")
    public Result<Boolean> stop(@PathVariable String requestId) {
        boolean success = agentService.stop(requestId);
        if (success) {
            return Result.success("Agent已停止", true);
        }
        return Result.error(ErrorCode.NOT_FOUND, "未找到对应的Agent任务");
    }

    /**
     * 清除会话记忆
     */
    @DeleteMapping("/memory/{conversationId}")
    public Result<Boolean> clearMemory(@PathVariable String conversationId) {
        boolean success = agentService.clearMemory(conversationId);
        if (success) {
            return Result.success("记忆已清除", true);
        }
        return Result.error(ErrorCode.INTERNAL_ERROR, "清除失败");
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<HealthResponse> health() {
        return Result.success(new HealthResponse("ok", "AI Agent服务正常运行"));
    }
}

