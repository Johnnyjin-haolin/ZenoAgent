package com.aiagent.controller;

import com.aiagent.config.AgentConfig;
import com.aiagent.enums.ModelType;
import com.aiagent.service.IAgentService;
import com.aiagent.service.memory.MemorySystem;
import com.aiagent.service.tool.McpGroupManager;
import com.aiagent.storage.ConversationStorage;
import com.aiagent.vo.AgentRequest;
import com.aiagent.vo.ConversationInfo;
import com.aiagent.vo.ModelInfoVO;
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
    
    @Autowired
    private com.aiagent.service.ConversationService conversationService;
    
    @Autowired
    private com.aiagent.service.MessageService messageService;

    @Autowired
    private com.aiagent.service.tool.ToolConfirmationManager toolConfirmationManager;
    
    /**
     * 执行Agent任务
     */
    @PostMapping("/execute")
    public SseEmitter execute(@RequestBody AgentRequest request) {
        log.info("收到Agent执行请求: {}", request.getContent());
        return agentService.execute(request);
    }

    /**
     * 工具执行确认（手动模式）
     */
    @PostMapping("/tool/confirm")
    public ResponseEntity<?> confirmTool(@RequestBody com.aiagent.vo.ToolConfirmRequest request) {
        if (request == null || request.getToolExecutionId() == null || request.getApprove() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "参数不完整"));
        }
        boolean success = Boolean.TRUE.equals(request.getApprove())
            ? toolConfirmationManager.approve(request.getToolExecutionId())
            : toolConfirmationManager.reject(request.getToolExecutionId());

        if (success) {
            return ResponseEntity.ok().body(Map.of("success", true, "message", "操作成功"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "未找到待确认的工具执行"));
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
     * 从配置文件中读取所有配置的模型
     * 
     * @param type 模型类型筛选（可选）：CHAT（对话模型）或 EMBEDDING（向量模型），不传则返回所有模型
     */
    @GetMapping("/models/available")
    public ResponseEntity<?> getAvailableModels(
            @RequestParam(required = false) String type) {
        try {
            List<AgentConfig.LLMConfig.ModelDefinition> modelDefinitions = 
                agentConfig.getLlm().getModels();
            
            if (modelDefinitions == null || modelDefinitions.isEmpty()) {
                log.warn("未配置任何模型，返回空列表");
                return ResponseEntity.ok().body(Map.of("success", true, "result", Collections.emptyList()));
            }
            
            String defaultModelId = agentConfig.getModel().getDefaultModelId();
            
            // 解析类型筛选参数（使用枚举）
            ModelType filterType = null;
            if (type != null && !type.trim().isEmpty()) {
                filterType = ModelType.fromCode(type);
            }
            
            // 转换为前端需要的格式
            List<ModelInfoVO> models = new ArrayList<>();
            int sort = 1;
            
            for (AgentConfig.LLMConfig.ModelDefinition modelDef : modelDefinitions) {
                // 获取模型类型（如果未设置，默认CHAT）
                ModelType modelType = ModelType.fromCode(modelDef.getType());
                
                // 如果指定了类型筛选，只返回匹配的模型
                if (filterType != null && modelType != filterType) {
                    continue;
                }
                 
                // 判断是否为默认模型
                boolean isDefault = defaultModelId != null && defaultModelId.equals(modelDef.getId());
                
                // 如果没有设置name，使用id作为显示名称
                String displayName = modelDef.getName() != null && !modelDef.getName().isEmpty()
                    ? modelDef.getName()
                    : modelDef.getId();
                
                // 生成默认描述
                String description = generateDescription(modelDef);
                
                ModelInfoVO modelInfo = ModelInfoVO.builder()
                    .id(modelDef.getId())
                    .name(modelDef.getName())
                    .displayName(displayName)
                    .description(description)
                    .provider(modelDef.getProvider())
                    .type(modelType.getCode())
                    .sort(sort++)
                    .isDefault(isDefault)
                    .build();
                models.add(modelInfo);
            }
            
            // 按sort排序，默认模型排在前面
            models.sort((a, b) -> {
                if (Boolean.TRUE.equals(a.getIsDefault())) return -1;
                if (Boolean.TRUE.equals(b.getIsDefault())) return 1;
                int sortCompare = Integer.compare(
                    a.getSort() != null ? a.getSort() : Integer.MAX_VALUE,
                    b.getSort() != null ? b.getSort() : Integer.MAX_VALUE
                );
                return sortCompare != 0 ? sortCompare : a.getId().compareTo(b.getId());
            });
            
            log.debug("获取模型列表成功，类型筛选: {}, 返回数量: {}", type, models.size());
            return ResponseEntity.ok().body(Map.of("success", true, "result", models));
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            return ResponseEntity.ok().body(Map.of("success", false, "result", Collections.emptyList(), "message", "获取模型列表失败: " + e.getMessage()));
        }
    }

    
    /**
     * 生成模型描述
     */
    private String generateDescription(AgentConfig.LLMConfig.ModelDefinition modelDef) {
        String provider = modelDef.getProvider();
        if (provider == null) {
            return "AI模型";
        }
        switch (provider.toUpperCase()) {
            case "OPENAI":
                return "OpenAI官方模型";
            case "ZHIPU":
                return "智谱AI模型";
            case "DEEPSEEK":
                return "DeepSeek模型";
            case "QWEN":
                return "通义千问模型";
            case "GLM":
                return "GLM模型";
            default:
                return provider + "模型";
        }
    }
    
    /**
     * 获取会话列表（从MySQL读取）
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(
            @RequestParam(required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String status) {
        try {
            // 从MySQL读取分页数据
            com.aiagent.dto.Page<ConversationInfo> page = 
                conversationService.listConversations(pageNo, pageSize, status);
            
            Map<String, Object> result = new HashMap<>();
            result.put("records", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNo", page.getCurrent());
            result.put("pageSize", page.getSize());
            
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
     * 获取会话消息列表（从MySQL读取）
     */
    @GetMapping("/conversation/{id}/messages")
    public ResponseEntity<?> getConversationMessages(
            @PathVariable("id") String conversationId,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        try {
            // 从MySQL读取消息历史
            List<com.aiagent.dto.MessageDTO> messages = 
                messageService.getMessages(conversationId, limit);
            
            return ResponseEntity.ok().body(Map.of("success", true, "result", messages));
        } catch (Exception e) {
            log.error("获取会话消息失败: conversationId={}", conversationId, e);
            return ResponseEntity.ok().body(Map.of("success", true, "result", Collections.emptyList()));
        }
    }
    
    /**
     * 更新会话标题（同时更新Redis和MySQL）
     */
    @PutMapping("/conversation/title")
    public ResponseEntity<?> updateConversationTitle(
            @RequestParam String conversationId,
            @RequestParam String title) {
        try {
            // 同时更新Redis和MySQL
            conversationStorage.updateConversationTitle(conversationId, title);
            conversationService.updateTitle(conversationId, title);
                return ResponseEntity.ok().body(Map.of("success", true, "message", "更新成功"));
        } catch (Exception e) {
            log.error("更新会话标题失败", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除会话（同时删除Redis和MySQL）
     */
    @DeleteMapping("/conversation/{id}")
    public ResponseEntity<?> deleteConversation(@PathVariable("id") String conversationId) {
        try {
            // 同时删除Redis和MySQL（MySQL会级联删除消息）
            conversationStorage.deleteConversation(conversationId);
            conversationService.deleteConversation(conversationId);
            agentService.clearMemory(conversationId);
            
                return ResponseEntity.ok().body(Map.of("success", true, "message", "删除成功"));
        } catch (Exception e) {
            log.error("删除会话失败: conversationId={}", conversationId, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "删除失败: " + e.getMessage()));
        }
    }
    
    /**
     * 归档会话（同时更新Redis和MySQL）
     */
    @PostMapping("/conversations/archive")
    public ResponseEntity<?> archiveConversations(@RequestBody List<String> conversationIds) {
        try {
            int successCount = 0;
            for (String conversationId : conversationIds) {
                try {
                    // 同时更新Redis和MySQL
                    conversationStorage.updateConversationStatus(conversationId, "archived");
                    conversationService.updateStatus(conversationId, "archived");
                    successCount++;
                } catch (Exception e) {
                    log.warn("归档会话失败: conversationId={}", conversationId, e);
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
