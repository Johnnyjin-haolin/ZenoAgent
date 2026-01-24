package com.aiagent.application.model;

import com.aiagent.shared.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 知识文档
 * 类型安全的知识库检索结果文档
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledgeDocument {
    
    /**
     * 文档内容
     */
    private String content;
    
    /**
     * 相似度分数 (0.0 ~ 1.0)
     */
    private Double score;
    
    /**
     * 文档名称
     */
    private String docName;
    
    /**
     * 文档ID
     */
    private String docId;
    
    /**
     * 知识库ID
     */
    private String knowledgeId;
    
    /**
     * 元数据（扩展字段）
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * 从Map构建文档对象
     */
    public static AgentKnowledgeDocument fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        
        AgentKnowledgeDocumentBuilder builder = AgentKnowledgeDocument.builder()
            .content((String) map.get("content"))
            .docName((String) map.get("docName"))
            .docId((String) map.get("docId"))
            .knowledgeId((String) map.get("knowledgeId"))
            .metadata(new HashMap<>(map));
        
        // 处理score
        Object scoreObj = map.get("score");
        if (scoreObj instanceof Double) {
            builder.score((Double) scoreObj);
        } else if (scoreObj instanceof Number) {
            builder.score(((Number) scoreObj).doubleValue());
        } else if (scoreObj != null) {
            try {
                builder.score(Double.parseDouble(scoreObj.toString()));
            } catch (NumberFormatException e) {
                builder.score(0.0);
            }
        } else {
            builder.score(0.0);
        }
        
        return builder.build();
    }
    
    /**
     * 判断是否为高相关度文档
     */
    public boolean isHighRelevance(double threshold) {
        return score != null && score >= threshold;
    }
    
    /**
     * 获取文档摘要（内容前100个字符）
     */
    public String getSummary() {
        if (StringUtils.isEmpty(content)) {
            return "";
        }
        
        int length = Math.min(100, content.length());
        String summary = content.substring(0, length);
        
        if (content.length() > 100) {
            summary += "...";
        }
        
        return summary;
    }
    
    /**
     * 获取格式化的分数百分比
     */
    public String getScorePercentage() {
        if (score == null) {
            return "0.0%";
        }
        return String.format("%.1f%%", score * 100);
    }
    
    /**
     * 获取元数据字段值
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * 设置元数据字段
     */
    public void putMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
}


