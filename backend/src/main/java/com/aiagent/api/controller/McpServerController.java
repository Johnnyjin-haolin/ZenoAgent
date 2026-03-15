package com.aiagent.api.controller;

import com.aiagent.api.dto.McpServerRequest;
import com.aiagent.api.dto.McpServerVO;
import com.aiagent.api.dto.McpToolInfo;
import com.aiagent.common.response.Result;
import com.aiagent.domain.mcp.McpServerService;
import com.aiagent.infrastructure.external.mcp.ClientToolCallManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器统一管理接口
 * <p>
 * GET    /api/mcp/servers           列表（可选 ?scope=0/1 过滤）
 * POST   /api/mcp/servers           创建
 * PUT    /api/mcp/servers/{id}      更新
 * DELETE /api/mcp/servers/{id}      删除
 * PUT    /api/mcp/servers/{id}/toggle 启用/禁用
 * POST   /api/mcp/servers/{id}/test  连通性测试
 * GET    /api/mcp/servers/{id}/tools 工具列表
 * POST   /api/mcp/client-tool-result 客户端工具执行结果回传（PERSONAL 专属）
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;
    private final ClientToolCallManager clientToolCallManager;

    @GetMapping("/servers")
    public Result<List<McpServerVO>> listServers(
            @RequestParam(value = "scope", required = false) Integer scope) {
        List<McpServerVO> list = scope != null
                ? mcpServerService.listByScope(scope)
                : mcpServerService.listAll();
        return Result.success(list);
    }

    @GetMapping("/servers/{id}")
    public Result<McpServerVO> getServer(@PathVariable String id) {
        McpServerVO vo = mcpServerService.getById(id);
        if (vo == null) {
            return Result.error("MCP server not found");
        }
        return Result.success(vo);
    }

    @PostMapping("/servers")
    public Result<McpServerVO> createServer(@RequestBody McpServerRequest req) {
        McpServerVO vo = mcpServerService.create(req);
        return Result.success(vo);
    }

    @PutMapping("/servers/{id}")
    public Result<McpServerVO> updateServer(
            @PathVariable String id,
            @RequestBody McpServerRequest req) {
        McpServerVO vo = mcpServerService.update(id, req);
        return Result.success(vo);
    }

    @DeleteMapping("/servers/{id}")
    public Result<Void> deleteServer(@PathVariable String id) {
        mcpServerService.delete(id);
        return Result.success(null);
    }

    @PutMapping("/servers/{id}/toggle")
    public Result<Void> toggleServer(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return Result.error("enabled field is required");
        }
        mcpServerService.toggleEnabled(id, enabled);
        return Result.success(null);
    }

    @PostMapping("/servers/{id}/test")
    public Result<String> testServer(@PathVariable String id) {
        String result = mcpServerService.testConnection(id);
        return Result.success(result);
    }

    @GetMapping("/servers/{id}/tools")
    public Result<List<McpToolInfo>> getServerTools(@PathVariable String id) {
        List<McpToolInfo> tools = mcpServerService.getTools(id);
        return Result.success(tools);
    }

    /**
     * 客户端工具执行结果回传（PERSONAL MCP 专属）
     * 客户端在浏览器执行完 MCP 工具后，通过此接口将结果提交给服务端继续推理
     */
    @PostMapping("/client-tool-result")
    public Result<Void> clientToolResult(@RequestBody ClientToolResultRequest req) {
        log.info("收到客户端工具执行结果: callId={}, hasError={}",
                req.getCallId(), req.getError() != null);
        if (req.getError() != null && !req.getError().isEmpty()) {
            clientToolCallManager.fail(req.getCallId(), req.getError());
        } else {
            clientToolCallManager.complete(req.getCallId(), req.getResult());
        }
        return Result.success(null);
    }

    /** 客户端工具结果回传请求体 */
    @lombok.Data
    public static class ClientToolResultRequest {
        /** 由服务端下发的调用 ID，用于关联等待中的 Future */
        private String callId;
        /** 工具执行结果（JSON 字符串） */
        private String result;
        /** 工具执行错误（非空时视为失败） */
        private String error;
    }
}
