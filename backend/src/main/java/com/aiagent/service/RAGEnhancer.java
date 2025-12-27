package com.aiagent.service;

import com.aiagent.util.StringUtils;
import com.aiagent.vo.AgentKnowledgeDocument;
import com.aiagent.vo.AgentKnowledgeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG增强器
 * 为Agent提供知识库检索和增强能力
 * 
 * 注意：此实现为简化版本，需要根据实际情况集成RAG模块
 * 如需完整RAG功能，需要集成原项目的EmbeddingHandler
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class RAGEnhancer {
    
    /**
     * 默认检索数量
     */
    private static final int DEFAULT_TOP_K = 5;
    
    /**
     * 默认相似度阈值
     */
    private static final double DEFAULT_MIN_SCORE = 0.5;
    
    /**
     * 检索相关知识
     * 
     * 注意：这是一个占位实现，需要集成实际的RAG检索功能
     * 可以：
     * 1. 集成原项目的EmbeddingHandler
     * 2. 或者实现一个简化的向量检索
     * 
     * @param query 查询文本
     * @param knowledgeIds 知识库ID列表
     * @return Agent领域模型的检索结果
     */
    public AgentKnowledgeResult retrieve(String query, List<String> knowledgeIds) {
        log.info("开始RAG检索，查询: {}, 知识库数量: {}", query, 
                 knowledgeIds != null ? knowledgeIds.size() : 0);
        
        if (StringUtils.isEmpty(query)) {
            log.warn("查询文本为空");
            return null;
        }
        
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            log.warn("未指定知识库，返回空结果");
            return AgentKnowledgeResult.builder()
                .query(query)
                .documents(new ArrayList<>())
                .totalCount(0)
                .summary("")
                .build();
        }
        
        // TODO: 集成实际的RAG检索功能
        // 当前返回空结果，实际使用时需要：
        // 1. 集成EmbeddingHandler进行向量检索
        // 2. 或者连接PgVector数据库进行检索
        log.warn("RAG检索功能尚未实现，返回空结果。需要集成RAG模块。");
        
        return AgentKnowledgeResult.builder()
            .query(query)
            .documents(new ArrayList<>())
            .totalCount(0)
            .summary("")
            .avgScore(0.0)
            .maxScore(0.0)
            .minScore(0.0)
            .build();
    }
    
    /**
     * 构建增强提示词
     * 
     * @param userQuery 用户问题
     * @param knowledgeResult 检索到的知识
     * @return 增强后的提示词
     */
    public String buildEnhancedPrompt(String userQuery, AgentKnowledgeResult knowledgeResult) {
        if (knowledgeResult == null || knowledgeResult.isEmpty()) {
            return userQuery;
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下知识库内容回答用户问题：\n\n");
        
        // 添加检索到的知识
        prompt.append("相关知识：\n");
        List<AgentKnowledgeDocument> docs = knowledgeResult.getDocuments();
        
        for (int i = 0; i < docs.size(); i++) {
            AgentKnowledgeDocument doc = docs.get(i);
            prompt.append(i + 1).append(". ");
            
            if (StringUtils.isNotEmpty(doc.getDocName())) {
                prompt.append("[").append(doc.getDocName()).append("] ");
            }
            
            // 添加内容和相关度
            prompt.append(doc.getContent());
            prompt.append(" (相关度: ").append(doc.getScorePercentage()).append(")");
            prompt.append("\n\n");
        }
        
        prompt.append("---\n\n");
        prompt.append("用户问题：").append(userQuery).append("\n\n");
        prompt.append("请基于以上知识库内容，用专业、准确的语言回答用户问题。");
        prompt.append("如果知识库中没有相关信息，请明确说明。");
        
        return prompt.toString();
    }
    
    /**
     * 提取知识摘要
     */
    public String extractSummary(AgentKnowledgeResult knowledgeResult) {
        if (knowledgeResult == null || knowledgeResult.isEmpty()) {
            return "";
        }
        
        if (StringUtils.isNotEmpty(knowledgeResult.getSummary())) {
            return knowledgeResult.getSummary();
        }
        
        StringBuilder summary = new StringBuilder();
        for (AgentKnowledgeDocument doc : knowledgeResult.getDocuments()) {
            if (StringUtils.isNotEmpty(doc.getContent())) {
                summary.append(doc.getContent()).append("\n");
            }
        }
        
        return summary.toString();
    }
}


