package com.aiagent.vo;

import dev.langchain4j.data.message.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 上下文
 * 维护Agent执行过程中的状态和历史
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * Agent配置ID
     */
    private String agentId;
    
    /**
     * 对话历史（存储DTO格式，用于序列化）
     * 注意：存储到Redis时使用MessageDTO，读取后转换回ChatMessage
     */
    private List<MessageDTO> messageDTOs;
    
    /**
     * 工具调用历史
     */
    private List<Map<String, Object>> toolCallHistory;
    
    /**
     * RAG检索历史
     */
    private List<Map<String, Object>> ragRetrieveHistory;
    
    /**
     * 迭代次数
     */
    private Integer iterations;
    
    /**
     * 上下文变量
     */
    private Map<String, Object> variables;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 从 ChatMessage 列表设置消息（自动转换为DTO）
     */
    public void setMessages(List<ChatMessage> messages) {
        if (messages == null) {
            this.messageDTOs = new ArrayList<>();
        } else {
            this.messageDTOs = messages.stream()
                .map(MessageDTO::from)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 获取 ChatMessage 列表（从DTO转换）
     */
    public List<ChatMessage> getMessages() {
        if (messageDTOs == null) {
            return new ArrayList<>();
        }
        return messageDTOs.stream()
            .map(MessageDTO::toChatMessage)
            .filter(msg -> msg != null)
            .collect(Collectors.toList());
    }
    
    /**
     * 添加消息
     */
    public void addMessage(ChatMessage message) {
        if (messageDTOs == null) {
            messageDTOs = new ArrayList<>();
        }
        MessageDTO dto = MessageDTO.from(message);
        if (dto != null) {
            messageDTOs.add(dto);
        }
    }
}


