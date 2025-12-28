package com.aiagent.vo;

import lombok.Data;

/**
 * 文本文档请求参数
 * 
 * @author aiagent
 */
@Data
public class TextDocumentRequest {
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 内容
     */
    private String content;
}

