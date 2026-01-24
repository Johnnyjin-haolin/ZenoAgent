package com.aiagent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 会话信息VO
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationInfo {
    
    private String id;
    private String title;
    private String userId;
    private String status;
    private Integer messageCount;
    private String modelId;
    private String modelName;
    private Date createTime;
    private Date updateTime;
}


