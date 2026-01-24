package com.aiagent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型信息VO
 * 用于前端展示模型列表
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoVO {
    
    /**
     * 模型ID（唯一标识）
     */
    private String id;
    
    /**
     * 模型名称（显示名称）
     */
    private String name;
    
    /**
     * 展示名称（前端显示用，如果不提供则使用name）
     */
    private String displayName;
    
    /**
     * 模型描述
     */
    private String description;
    
    /**
     * 模型提供商（OPENAI, ZHIPU, DEEPSEEK等）
     */
    private String provider;
    
    /**
     * 排序（数字越小越靠前）
     */
    private Integer sort;
    
    /**
     * 是否为默认模型
     */
    private Boolean isDefault;
    
    /**
     * 模型类型（CHAT: 对话模型, EMBEDDING: 向量模型）
     */
    private String type;
}

