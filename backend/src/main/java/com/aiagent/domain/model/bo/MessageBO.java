package com.aiagent.domain.model.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息 DTO
 * 用于序列化存储 LangChain4j 的 ChatMessage
 * 
 * @author aiagent
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageBO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息类型
     * USER, AI, SYSTEM, TOOL_EXECUTION
     */
    private String type;
    
    /**
     * 消息内容（文本）
     */
    private String text;
    
    /**
     * 消息名称（可选，用于 UserMessage 的 name）
     */
    private String name;
    
    /**
     * 工具执行ID（可选，用于 ToolExecutionResultMessage）
     */
    private String toolExecutionId;
    
    /**
     * 工具名称（可选，用于 ToolExecutionResultMessage）
     */
    private String toolName;

    /**
     * 工具调用请求列表（可选，用于携带 tool_calls 的 AiMessage）
     */
    private List<ToolExecutionRequestDTO> toolExecutionRequests;

    /**
     * ToolExecutionRequest 的可序列化 DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolExecutionRequestDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String name;
        private String arguments;

        public static ToolExecutionRequestDTO from(ToolExecutionRequest req) {
            return new ToolExecutionRequestDTO(req.id(), req.name(), req.arguments());
        }

        public ToolExecutionRequest toToolExecutionRequest() {
            return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
        }
    }
    
    /**
     * 从 ChatMessage 转换为 DTO
     */
    public static MessageBO from(ChatMessage message) {
        if (message == null) {
            return null;
        }
        
        MessageBO dto = new MessageBO();
        
        if (message instanceof UserMessage) {
            UserMessage userMsg = (UserMessage) message;
            dto.setType("USER");
            dto.setText(userMsg.singleText());
            dto.setName(userMsg.name());
            
        } else if (message instanceof AiMessage) {
            AiMessage aiMsg = (AiMessage) message;
            dto.setType("AI");
            dto.setText(aiMsg.text());
            if (aiMsg.hasToolExecutionRequests()) {
                dto.setToolExecutionRequests(
                    aiMsg.toolExecutionRequests().stream()
                        .map(ToolExecutionRequestDTO::from)
                        .collect(Collectors.toList())
                );
            }
            
        } else if (message instanceof SystemMessage) {
            SystemMessage sysMsg = (SystemMessage) message;
            dto.setType("SYSTEM");
            dto.setText(sysMsg.text());
            
        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) message;
            dto.setType("TOOL_EXECUTION");
            dto.setText(toolMsg.text());
            dto.setToolExecutionId(toolMsg.id());
            dto.setToolName(toolMsg.toolName());
            
        } else {
            dto.setType("UNKNOWN");
        }
        
        return dto;
    }
    
    /**
     * 转换回 ChatMessage
     */
    public ChatMessage toChatMessage() {
        if (type == null) {
            return null;
        }
        
        switch (type) {
            case "USER":
                if (name != null && !name.isEmpty()) {
                    return UserMessage.from(name, text);
                } else {
                    return UserMessage.from(text);
                }
                
            case "AI":
                if (toolExecutionRequests != null && !toolExecutionRequests.isEmpty()) {
                    List<ToolExecutionRequest> requests = toolExecutionRequests.stream()
                        .map(ToolExecutionRequestDTO::toToolExecutionRequest)
                        .collect(Collectors.toList());
                    // text 可以为 null（纯 tool_calls 无正文时），AiMessage 支持 text=null + toolExecutionRequests
                    return AiMessage.from(requests);
                }
                // 纯文本回复，text 不应为 null；防御性处理
                return AiMessage.from(text != null ? text : "");
                
            case "SYSTEM":
                return SystemMessage.from(text);
                
            case "TOOL_EXECUTION":
                if (toolExecutionId != null && toolName != null) {
                    return ToolExecutionResultMessage.from(toolExecutionId, toolName, text);
                } else {
                    return AiMessage.from(text != null ? text : "");
                }
                
            default:
                return AiMessage.from(text != null ? text : "");
        }
    }
}
