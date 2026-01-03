package com.aiagent.entity;

import lombok.Data;

import java.util.Date;

/**
 * 会话实体类
 * 
 * @author aiagent
 */
@Data
public class ConversationEntity {
    
    /**
     * 会话ID（UUID）
     */
    private String id;
    
    /**
     * 会话标题
     */
    private String title;
    
    /**
     * 用户ID（预留）
     */
    private String userId;
    
    /**
     * 使用的模型ID
     */
    private String modelId;
    
    /**
     * 模型名称
     */
    private String modelName;
    
    /**
     * 状态：active/archived/deleted
     */
    private String status;
    
    /**
     * 消息数量
     */
    private Integer messageCount;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 更新时间
     */
    private Date updateTime;
}

