package com.aiagent.api.controller;

import com.aiagent.api.dto.MessageDTO;
import com.aiagent.api.dto.Page;
import com.aiagent.api.dto.PageResult;
import com.aiagent.application.service.conversation.ConversationService;
import com.aiagent.application.service.agent.IAgentService;
import com.aiagent.application.service.message.MessageService;
import com.aiagent.infrastructure.storage.ConversationStorage;
import com.aiagent.api.dto.ConversationInfo;
import com.aiagent.shared.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public Result<PageResult<ConversationInfo>> getConversations(
            @RequestParam(required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String status) {
        Page<ConversationInfo> page =
            conversationService.listConversations(pageNo, pageSize, status);

        PageResult<ConversationInfo> result = PageResult.from(page);
        return Result.success(result);
    }

    /**
     * 获取会话消息列表（从MySQL读取）
     */
    @GetMapping("/conversation/{id}/messages")
    public Result<List<MessageDTO>> getConversationMessages(
            @PathVariable("id") String conversationId,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        List<MessageDTO> messages =
            messageService.getMessages(conversationId, limit);

        return Result.success(messages);
    }

    /**
     * 更新会话标题（同时更新Redis和MySQL）
     */
    @PutMapping("/conversation/title")
    public Result<Boolean> updateConversationTitle(
            @RequestParam String conversationId,
            @RequestParam String title) {
        conversationStorage.updateConversationTitle(conversationId, title);
        conversationService.updateTitle(conversationId, title);
        return Result.success("更新成功", true);
    }

    /**
     * 删除会话（同时删除Redis和MySQL）
     */
    @DeleteMapping("/conversation/{id}")
    public Result<Boolean> deleteConversation(@PathVariable("id") String conversationId) {
        conversationStorage.deleteConversation(conversationId);
        conversationService.deleteConversation(conversationId);
        agentService.clearMemory(conversationId);

        return Result.success("删除成功", true);
    }

    /**
     * 归档会话（同时更新Redis和MySQL）
     */
    @PostMapping("/conversations/archive")
    public Result<String> archiveConversations(@RequestBody List<String> conversationIds) {
        int successCount = 0;
        for (String conversationId : conversationIds) {
            conversationStorage.updateConversationStatus(conversationId, com.aiagent.shared.constant.AgentConstants.ARCHIVED_CONVERSATION_STATUS);
            conversationService.updateStatus(conversationId, com.aiagent.shared.constant.AgentConstants.ARCHIVED_CONVERSATION_STATUS);
            successCount++;
        }

        return Result.success(
            String.format("成功归档 %d/%d 个会话", successCount, conversationIds.size()),
            null
        );
    }
}

