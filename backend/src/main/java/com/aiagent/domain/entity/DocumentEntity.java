package com.aiagent.domain.entity;

import lombok.Data;
import org.apache.james.mime4j.dom.datetime.DateTime;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 文档实体类
 * 
 * @author aiagent
 */
@Data
public class DocumentEntity {
    
    /**
     * 主键ID
     */
    private String id;
    
    /**
     * 知识库ID
     */
    private String knowledgeBaseId;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 文档类型：FILE/TEXT/WEB
     */
    private String type;
    
    /**
     * 文档内容（文本类型直接存储）
     */
    private String content;
    
    /**
     * 元数据（JSON格式）
     */
    private String metadata;
    
    /**
     * 状态：DRAFT/BUILDING/COMPLETE/FAILED
     */
    private String status;
    
    /**
     * 创建时间
     */
    private Date createTime;
    
    /**
     * 更新时间
     */
    private Date updateTime;
}

