package com.aiagent.entity;

import lombok.Data;

import java.util.Date;

/**
 * 消息实体类
 * 
 * @author aiagent
 */
@Data
public class MessageEntity {
    
    /**
     * 消息ID（自增）
     */
    private Long id;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 消息唯一标识（UUID）
     */
    private String messageId;
    
    /**
     * 角色：user/assistant/system
     */
    private String role;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 使用的模型ID
     */
    private String modelId;
    
    /**
     * Token数量
     */
    private Integer tokens;
    
    /**
     * 耗时（毫秒）
     */
    private Integer duration;
    
    /**
     * 元数据（JSON字符串）
     */
    private String metadata;
    
    /**
     * 创建时间
     */
    private Date createTime;
}

