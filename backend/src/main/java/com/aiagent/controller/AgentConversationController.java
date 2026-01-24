package com.aiagent.controller;

import com.aiagent.service.ConversationService;
import com.aiagent.service.IAgentService;
import com.aiagent.service.MessageService;
import com.aiagent.storage.ConversationStorage;
import com.aiagent.vo.ConversationInfo;
import com.aiagent.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话相关接口
 */
@RestController
@RequestMapping("/aiagent")
public class AgentConversationController {

    @Autowired
    private ConversationStorage conversationStorage;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private IAgentService agentService;

    /**
     * 获取会话列表（从MySQL读取）
     */
    @GetMapping("/conversations")
    public ResponseEntity<Result<Map<String, Object>>> getConversations(
            @RequestParam(required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String status) {
        com.aiagent.dto.Page<ConversationInfo> page =
            conversationService.listConversations(pageNo, pageSize, status);

        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pageNo", page.getCurrent());
        result.put("pageSize", page.getSize());

        return ResponseEntity.ok(Result.success(result));
    }

    /**
     * 获取会话消息列表（从MySQL读取）
     */
    @GetMapping("/conversation/{id}/messages")
    public ResponseEntity<Result<List<com.aiagent.dto.MessageDTO>>> getConversationMessages(
            @PathVariable("id") String conversationId,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        List<com.aiagent.dto.MessageDTO> messages =
            messageService.getMessages(conversationId, limit);

        return ResponseEntity.ok(Result.success(messages));
    }

    /**
     * 更新会话标题（同时更新Redis和MySQL）
     */
    @PutMapping("/conversation/title")
    public ResponseEntity<Result<Boolean>> updateConversationTitle(
            @RequestParam String conversationId,
            @RequestParam String title) {
        conversationStorage.updateConversationTitle(conversationId, title);
        conversationService.updateTitle(conversationId, title);
        return ResponseEntity.ok(Result.success("更新成功", true));
    }

    /**
     * 删除会话（同时删除Redis和MySQL）
     */
    @DeleteMapping("/conversation/{id}")
    public ResponseEntity<Result<Boolean>> deleteConversation(@PathVariable("id") String conversationId) {
        conversationStorage.deleteConversation(conversationId);
        conversationService.deleteConversation(conversationId);
        agentService.clearMemory(conversationId);

        return ResponseEntity.ok(Result.success("删除成功", true));
    }

    /**
     * 归档会话（同时更新Redis和MySQL）
     */
    @PostMapping("/conversations/archive")
    public ResponseEntity<Result<String>> archiveConversations(@RequestBody List<String> conversationIds) {
        int successCount = 0;
        for (String conversationId : conversationIds) {
            conversationStorage.updateConversationStatus(conversationId, com.aiagent.constant.AgentConstants.ARCHIVED_CONVERSATION_STATUS);
            conversationService.updateStatus(conversationId, com.aiagent.constant.AgentConstants.ARCHIVED_CONVERSATION_STATUS);
            successCount++;
        }

        return ResponseEntity.ok(Result.success(
            String.format("成功归档 %d/%d 个会话", successCount, conversationIds.size()),
            null
        ));
    }
}

