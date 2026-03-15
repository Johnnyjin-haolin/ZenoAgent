package com.aiagent.api.dto;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.Date;

/**
 * 消息响应对象（API 层）
 * 
 * @author aiagent
 */
@Data
public class MessageResponse {
    
    /**
     * 消息唯一标识
     */
    private String id;
    
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
     * 执行此消息的 Agent ID（assistant 消息时有值）
     */
    private String agentId;
    
    /**
     * Token数量
     */
    private Integer tokens;
    
    /**
     * 耗时（毫秒）
     */
    private Integer duration;
    
    /**
     * 元数据（工具调用、RAG结果等）
     */
    private JSONObject metadata;
    
    /**
     * 创建时间
     */
    private Date createTime;
}

