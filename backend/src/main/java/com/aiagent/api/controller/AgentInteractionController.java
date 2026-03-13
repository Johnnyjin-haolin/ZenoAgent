package com.aiagent.api.controller;

import com.aiagent.common.response.Result;
import com.aiagent.infrastructure.external.mcp.UserAnswerManager;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aiagent")
public class AgentInteractionController {

    @Autowired
    private UserAnswerManager userAnswerManager;

    @PostMapping("/answer")
    public Result<Boolean> answer(@RequestBody AnswerRequest request) {
        if (request.getQuestionId() == null || request.getQuestionId().isEmpty()) {
            return Result.error(com.aiagent.common.response.ErrorCode.VALIDATION_ERROR, "questionId 不能为空");
        }
        if (request.getAnswer() == null) {
            return Result.error(com.aiagent.common.response.ErrorCode.VALIDATION_ERROR, "answer 不能为空");
        }
        boolean success = userAnswerManager.receiveAnswer(request.getQuestionId(), request.getAnswer());
        if (success) {
            return Result.success("回答已接收", true);
        }
        return Result.error(com.aiagent.common.response.ErrorCode.INTERNAL_ERROR, "提交回答失败，问题可能已超时");
    }

    @Data
    public static class AnswerRequest {
        private String questionId;
        private String answer;
    }
}
