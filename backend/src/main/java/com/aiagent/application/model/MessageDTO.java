package com.aiagent.application.model;

import dev.langchain4j.data.message.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息 DTO
 * 用于序列化存储 LangChain4j 的 ChatMessage
 * 
 * @author aiagent
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO implements Serializable {
    
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
     * 从 ChatMessage 转换为 DTO
     */
    public static MessageDTO from(ChatMessage message) {
        if (message == null) {
            return null;
        }
        
        MessageDTO dto = new MessageDTO();
        
        if (message instanceof UserMessage) {
            UserMessage userMsg = (UserMessage) message;
            dto.setType("USER");
            dto.setText(userMsg.singleText());
            dto.setName(userMsg.name());
            
        } else if (message instanceof AiMessage) {
            AiMessage aiMsg = (AiMessage) message;
            dto.setType("AI");
            dto.setText(aiMsg.text());
            
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
            // 未知类型，尝试获取文本
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
                return AiMessage.from(text);
                
            case "SYSTEM":
                return SystemMessage.from(text);
                
            case "TOOL_EXECUTION":
                if (toolExecutionId != null && toolName != null) {
                    return ToolExecutionResultMessage.from(toolExecutionId, toolName, text);
                } else {
                    // 降级为 AI 消息
                    return AiMessage.from(text);
                }
                
            default:
                // 未知类型降级为 AI 消息
                return AiMessage.from(text);
        }
    }
}


