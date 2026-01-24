package com.aiagent.controller;

import com.aiagent.enums.ErrorCode;
import com.aiagent.service.tool.ToolConfirmationManager;
import com.aiagent.vo.Result;
import com.aiagent.vo.ToolConfirmRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Agent 工具相关接口
 */
@RestController
@RequestMapping("/aiagent")
public class AgentToolController {

    @Autowired
    private ToolConfirmationManager toolConfirmationManager;

    /**
     * 工具执行确认（手动模式）
     */
    @PostMapping("/tool/confirm")
    public ResponseEntity<Result<Boolean>> confirmTool(@RequestBody ToolConfirmRequest request) {
        if (request == null || request.getToolExecutionId() == null || request.getApprove() == null) {
            throw new IllegalArgumentException("参数不完整");
        }
        boolean success = Boolean.TRUE.equals(request.getApprove())
            ? toolConfirmationManager.approve(request.getToolExecutionId())
            : toolConfirmationManager.reject(request.getToolExecutionId());

        if (success) {
            return ResponseEntity.ok(Result.success("操作成功", true));
        }
        return ResponseEntity.ok(Result.error(ErrorCode.NOT_FOUND, "未找到待确认的工具执行"));
    }
}

