package com.aiagent.controller;

import com.aiagent.config.AgentConfig;
import com.aiagent.service.IAgentService;
import com.aiagent.service.MemorySystem;
import com.aiagent.service.tool.McpGroupManager;
import com.aiagent.storage.ConversationStorage;
import com.aiagent.vo.AgentRequest;
import com.aiagent.vo.ConversationInfo;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Agent 控制器
 * 
 * @author aiagent
 */
@Slf4j
@RestController
@RequestMapping("/aiagent")
public class AgentController {
    
    @Autowired
    private IAgentService agentService;
    
    @Autowired
    private ConversationStorage conversationStorage;
    
    @Autowired
    private MemorySystem memorySystem;
    
    @Autowired
    private AgentConfig agentConfig;
    
    @Autowired
    private McpGroupManager mcpGroupManager;
    
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
    public ResponseEntity<?> stop(@PathVariable String requestId) {
        boolean success = agentService.stop(requestId);
        if (success) {
            return ResponseEntity.ok().body(Map.of("success", true, "message", "Agent已停止"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "未找到对应的Agent任务"));
    }
    
    /**
     * 清除会话记忆
     */
    @DeleteMapping("/memory/{conversationId}")
    public ResponseEntity<?> clearMemory(@PathVariable String conversationId) {
        boolean success = agentService.clearMemory(conversationId);
        if (success) {
            return ResponseEntity.ok().body(Map.of("success", true, "message", "记忆已清除"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "清除失败"));
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok().body(Map.of("status", "ok", "message", "AI Agent服务正常运行"));
    }
    
    /**
     * 获取可用模型列表
     */
    @GetMapping("/models/available")
    public ResponseEntity<?> getAvailableModels() {
        try {
            // 从配置读取默认模型列表
            List<Map<String, Object>> models = new ArrayList<>();
            
            String defaultModelId = agentConfig.getModel().getDefaultModelId();
            models.add(Map.of(
                "id", defaultModelId,
                "displayName", "GPT-4o Mini",
                "description", "快速且经济的模型",
                "icon", "🤖",
                "sort", 1,
                "isDefault", true
            ));
            
            models.add(Map.of(
                "id", "gpt-4o",
                "displayName", "GPT-4o",
                "description", "最强大的模型",
                "icon", "🚀",
                "sort", 2,
                "isDefault", false
            ));
            
            return ResponseEntity.ok().body(Map.of("success", true, "result", models));
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            return ResponseEntity.ok().body(Map.of("success", true, "result", Collections.emptyList()));
        }
    }
    
    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(
            @RequestParam(required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String status) {
        try {
            List<Map<String, Object>> allConversations = conversationStorage.listConversations(status);
            
            // 转换为ConversationInfo格式
            List<ConversationInfo> conversations = allConversations.stream()
                .map(this::mapToConversationInfo)
                .collect(Collectors.toList());
            
            // 分页处理
            int start = (pageNo - 1) * pageSize;
            int end = Math.min(start + pageSize, conversations.size());
            List<ConversationInfo> pageList = start < conversations.size() 
                ? conversations.subList(start, end) 
                : Collections.emptyList();
            
            Map<String, Object> result = new HashMap<>();
            result.put("records", pageList);
            result.put("total", conversations.size());
            result.put("pageNo", pageNo);
            result.put("pageSize", pageSize);
            
            return ResponseEntity.ok().body(Map.of("success", true, "result", result));
        } catch (Exception e) {
            log.error("获取会话列表失败", e);
            return ResponseEntity.ok().body(Map.of(
                "success", true, 
                "result", Map.of("records", Collections.emptyList(), "total", 0)
            ));
        }
    }
    
    /**
     * 获取会话消息列表
     */
    @GetMapping("/conversation/{id}/messages")
    public ResponseEntity<?> getConversationMessages(
            @PathVariable("id") String conversationId,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        try {
            List<ChatMessage> messages = memorySystem.getShortTermMemory(conversationId, limit);
            
            // 转换为前端需要的格式
            List<Map<String, Object>> messageList = messages.stream()
                .map(this::mapChatMessage)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok().body(Map.of("success", true, "result", messageList));
        } catch (Exception e) {
            log.error("获取会话消息失败: conversationId={}", conversationId, e);
            return ResponseEntity.ok().body(Map.of("success", true, "result", Collections.emptyList()));
        }
    }
    
    /**
     * 更新会话标题
     */
    @PutMapping("/conversation/title")
    public ResponseEntity<?> updateConversationTitle(
            @RequestParam String conversationId,
            @RequestParam String title) {
        try {
            boolean success = conversationStorage.updateConversationTitle(conversationId, title);
            if (success) {
                return ResponseEntity.ok().body(Map.of("success", true, "message", "更新成功"));
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "更新失败"));
        } catch (Exception e) {
            log.error("更新会话标题失败", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除会话
     */
    @DeleteMapping("/conversation/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable("id") String conversationId) {
        try {
            // 删除对话和记忆
            boolean deleted = conversationStorage.deleteConversation(conversationId);
            agentService.clearMemory(conversationId);
            
            if (deleted) {
                return ResponseEntity.ok().body(Map.of("success", true, "message", "删除成功"));
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "删除失败"));
        } catch (Exception e) {
            log.error("删除会话失败: conversationId={}", conversationId, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "删除失败: " + e.getMessage()));
        }
    }
    
    /**
     * 归档会话
     */
    @PostMapping("/conversations/archive")
    public ResponseEntity<?> archiveConversations(@RequestBody List<String> conversationIds) {
        try {
            int successCount = 0;
            for (String conversationId : conversationIds) {
                if (conversationStorage.updateConversationStatus(conversationId, "archived")) {
                    successCount++;
                }
            }
            
            return ResponseEntity.ok().body(Map.of(
                "success", true, 
                "message", String.format("成功归档 %d/%d 个会话", successCount, conversationIds.size())
            ));
        } catch (Exception e) {
            log.error("归档会话失败", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "归档失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取消息角色枚举
     */
    @GetMapping("/enums/message-roles")
    public ResponseEntity<?> getMessageRoles() {
        List<Map<String, String>> roles = Arrays.asList(
            Map.of("code", "user", "name", "用户"),
            Map.of("code", "assistant", "name", "助手"),
            Map.of("code", "system", "name", "系统")
        );
        return ResponseEntity.ok().body(Map.of("success", true, "result", roles));
    }
    
    /**
     * 获取消息状态枚举
     */
    @GetMapping("/enums/message-status")
    public ResponseEntity<?> getMessageStatus() {
        List<Map<String, String>> statuses = Arrays.asList(
            Map.of("code", "success", "name", "成功"),
            Map.of("code", "error", "name", "错误"),
            Map.of("code", "processing", "name", "处理中")
        );
        return ResponseEntity.ok().body(Map.of("success", true, "result", statuses));
    }
    
    /**
     * 获取会话状态枚举
     */
    @GetMapping("/enums/conversation-status")
    public ResponseEntity<?> getConversationStatus() {
        List<Map<String, String>> statuses = Arrays.asList(
            Map.of("code", "active", "name", "活跃"),
            Map.of("code", "archived", "name", "已归档")
        );
        return ResponseEntity.ok().body(Map.of("success", true, "result", statuses));
    }
    
    /**
     * 获取MCP分组列表
     */
    @GetMapping("/mcp/groups")
    public ResponseEntity<?> getMcpGroups() {
        try {
            List<com.aiagent.vo.McpGroupInfo> groups = mcpGroupManager.getEnabledGroups();
            return ResponseEntity.ok().body(Map.of("success", true, "result", groups));
        } catch (Exception e) {
            log.error("获取MCP分组列表失败", e);
            return ResponseEntity.ok().body(Map.of("success", true, "result", Collections.emptyList()));
        }
    }
    
    /**
     * 获取MCP分组详情
     */
    @GetMapping("/mcp/groups/{groupId}")
    public ResponseEntity<?> getMcpGroup(@PathVariable String groupId) {
        try {
            com.aiagent.vo.McpGroupInfo group = mcpGroupManager.getGroupById(groupId);
            if (group != null) {
                return ResponseEntity.ok().body(Map.of("success", true, "result", group));
            }
            return ResponseEntity.ok().body(Map.of("success", false, "message", "分组不存在"));
        } catch (Exception e) {
            log.error("获取MCP分组详情失败: groupId={}", groupId, e);
            return ResponseEntity.ok().body(Map.of("success", false, "message", "获取失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取MCP工具列表
     * 支持按分组筛选
     */
    @GetMapping("/mcp/tools")
    public ResponseEntity<?> getMcpTools(@RequestParam(required = false) List<String> groups) {
        try {
            List<com.aiagent.vo.McpToolInfo> tools = mcpGroupManager.getToolsByGroups(groups);
            return ResponseEntity.ok().body(Map.of("success", true, "result", tools));
        } catch (Exception e) {
            log.error("获取MCP工具列表失败: groups={}", groups, e);
            return ResponseEntity.ok().body(Map.of("success", true, "result", Collections.emptyList()));
        }
    }
    
    /**
     * 将Map转换为ConversationInfo
     */
    private ConversationInfo mapToConversationInfo(Map<String, Object> map) {
        ConversationInfo.ConversationInfoBuilder builder = ConversationInfo.builder();
        
        if (map.get("id") != null) {
            builder.id(map.get("id").toString());
        }
        if (map.get("title") != null) {
            builder.title(map.get("title").toString());
        }
        if (map.get("status") != null) {
            builder.status(map.get("status").toString());
        } else {
            builder.status("active");
        }
        if (map.get("messageCount") != null) {
            builder.messageCount(Integer.parseInt(map.get("messageCount").toString()));
        } else {
            builder.messageCount(0);
        }
        if (map.get("modelId") != null) {
            builder.modelId(map.get("modelId").toString());
        }
        if (map.get("createTime") != null) {
            builder.createTime((Date) map.get("createTime"));
        }
        if (map.get("updateTime") != null) {
            builder.updateTime((Date) map.get("updateTime"));
        }
        
        return builder.build();
    }
    
    /**
     * 将ChatMessage转换为Map
     */
    private Map<String, Object> mapChatMessage(ChatMessage message) {
        Map<String, Object> map = new HashMap<>();
        
        if (message instanceof dev.langchain4j.data.message.UserMessage) {
            map.put("role", "user");
            map.put("content", ((UserMessage) message).contents());
        } else if (message instanceof dev.langchain4j.data.message.AiMessage) {
            map.put("role", "assistant");
            map.put("content", ((AiMessage) message).text());
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage) {
            map.put("role", "system");
            map.put("content", ((SystemMessage) message).text());
        }
        
        return map;
    }
}
