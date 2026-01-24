package com.aiagent.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.james.mime4j.dom.datetime.DateTime;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 文档实体
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    
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
     * 文档类型：FILE（文件）、TEXT（文本）、WEB（网页）
     */
    private String type;
    
    /**
     * 文档内容（直接文本内容，可选）
     */
    private String content;
    
    /**
     * 元数据（JSON格式，存储文件路径等信息）
     * 示例：{"filePath":"/path/to/file.pdf","sourcePath":"/path/to/source"}
     */
    private String metadata;
    
    /**
     * 状态：DRAFT（草稿）、BUILDING（构建中）、COMPLETE（完成）、FAILED（失败）
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
    
    /**
     * 文档类型常量
     */
    public static class Type {
        public static final String FILE = "FILE";
        public static final String TEXT = "TEXT";
        public static final String WEB = "WEB";
    }
    
    /**
     * 文档状态常量
     */
    public static class Status {
        public static final String DRAFT = "DRAFT";
        public static final String BUILDING = "BUILDING";
        public static final String COMPLETE = "COMPLETE";
        public static final String FAILED = "FAILED";
    }
}

