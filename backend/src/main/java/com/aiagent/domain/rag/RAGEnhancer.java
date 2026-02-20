package com.aiagent.domain.rag;

import com.aiagent.api.dto.RAGConfig;
import com.aiagent.infrastructure.config.EmbeddingStoreConfiguration;
import com.aiagent.domain.model.bo.KnowledgeBase;
import com.aiagent.infrastructure.repository.KnowledgeBaseRepository;
import com.aiagent.infrastructure.external.llm.EmbeddingModelManager;
import com.aiagent.common.util.StringUtils;
import com.aiagent.domain.model.bo.AgentKnowledgeDocument;
import com.aiagent.domain.model.bo.AgentKnowledgeResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * RAG增强器
 * 为Agent提供知识库检索和增强能力
 * 
 * 使用langchain4j的EmbeddingModel和EmbeddingStore实现RAG检索
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class RAGEnhancer {
    
    @Autowired
    private EmbeddingStoreConfiguration embeddingStoreConfiguration;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private EmbeddingModelManager embeddingModelManager;


    
    
    /**
     * 检索相关知识
     * 
     * 根据知识库ID列表查询知识库信息，然后执行RAG检索
     * 
     * @param query 查询文本
     * @param knowledgeIds 知识库ID列表
     * @return Agent领域模型的检索结果
     */
    public AgentKnowledgeResult retrieve(String query, List<String> knowledgeIds,RAGConfig ragConfig) {
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
        
        // 批量查询知识库信息
        Map<String, KnowledgeBase> knowledgeBaseMap = knowledgeBaseRepository.findByIds(knowledgeIds);
        log.debug("查询到知识库信息: count={}", knowledgeBaseMap.size());
        
        // 调用核心检索方法
        return retrieve(query, knowledgeBaseMap,ragConfig);
    }
    

    /**
     * 检索相关知识（使用已查询的知识库信息）
     * 
     * 这是RAG检索的核心方法，接收已查询好的知识库信息，执行实际的检索逻辑
     * @param ragConfig RAG配置
     * @param query 查询文本
     * @param knowledgeBaseMap 知识库映射（knowledgeId -> KnowledgeBase），已查询好的知识库信息
     * @return Agent领域模型的检索结果
     */
    public AgentKnowledgeResult retrieve(String query, Map<String, KnowledgeBase> knowledgeBaseMap,RAGConfig ragConfig) {
        log.info("执行RAG检索，查询: {}, 知识库数量: {}", query, 
                 knowledgeBaseMap != null ? knowledgeBaseMap.size() : 0);
        
        if (StringUtils.isEmpty(query)) {
            log.warn("查询文本为空");
            return null;
        }
        
        if (knowledgeBaseMap == null || knowledgeBaseMap.isEmpty()) {
            log.warn("知识库信息为空，返回空结果");
            return createEmptyResult(query);
        }
        
        // 提取知识库ID列表
        List<String> knowledgeIds = new ArrayList<>(knowledgeBaseMap.keySet());
        
        // 使用langchain4j实现RAG检索
        try {
            // 1. 获取或创建Embedding模型
            EmbeddingModel model = embeddingModelManager.getDefaultEmbeddingModel();
            if (model == null) {
                log.warn("Embedding模型不可用，返回空结果");
                return createEmptyResult(query);
            }
            
            // 2. 生成查询向量
            Embedding queryEmbedding = model.embed(query).content();
            
            // 3. 使用配置参数
            int maxResults = ragConfig.getMaxResultsOrDefault();
            double minScore = ragConfig.getMinScoreOrDefault();
            
            log.info("RAG配置: maxResults={}, minScore={}, maxDocLength={}, maxTotalLength={}", 
                maxResults, minScore, 
                ragConfig.hasDocumentLengthLimit() ? ragConfig.getMaxDocumentLength() : "无限制",
                ragConfig.hasTotalContentLengthLimit() ? ragConfig.getMaxTotalContentLength() : "无限制");
            
            // 4. 合并所有知识库的检索结果
            List<EmbeddingMatch<TextSegment>> allMatches = new ArrayList<>();
            
            for (String knowledgeId : knowledgeIds) {
                try {
                    KnowledgeBase knowledgeBase = knowledgeBaseMap.get(knowledgeId);
                    if (knowledgeBase == null) {
                        log.warn("Knowledge base not found in map: {}", knowledgeId);
                        continue;
                    }
                    
                    // 获取该知识库的EmbeddingStore
                    EmbeddingStore<TextSegment> store = embeddingStoreConfiguration
                            .createDefaultEmbeddingStore(model);
                    
                    // 构建搜索请求，按知识库ID过滤
                    EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(maxResults)
                            .minScore(minScore)
                            .filter(metadataKey(EmbeddingProcessor.METADATA_KNOWLEDGE_ID).isEqualTo(knowledgeId))
                            .build();
                    
                    EmbeddingSearchResult<TextSegment> searchResult = store.search(searchRequest);
                    List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
                    
                    if (matches != null && !matches.isEmpty()) {
                        allMatches.addAll(matches);
                    }
                } catch (Exception e) {
                    log.error("Failed to search in knowledge base: {}", knowledgeId, e);
                }
            }
            
            // 5. 按分数排序并取前N个
            List<EmbeddingMatch<TextSegment>> matches = allMatches.stream()
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(maxResults)
                    .collect(Collectors.toList());
            
            // 6. 转换为AgentKnowledgeResult
            AgentKnowledgeResult result = convertToKnowledgeResult(query, matches, knowledgeIds);
            
            // 7. 应用文档长度限制（如果配置了）
            if (ragConfig.hasDocumentLengthLimit() || ragConfig.hasTotalContentLengthLimit()) {
                result = applyLengthLimits(result, ragConfig);
            } else {
                log.info("未配置文档长度限制，保留完整内容");
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("RAG检索失败", e);
            return createEmptyResult(query);
        }
    }

    
    /**
     * 构建增强提示词
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
     * 创建空结果
     */
    private AgentKnowledgeResult createEmptyResult(String query) {
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
     * 将EmbeddingMatch列表转换为AgentKnowledgeResult
     */
    private AgentKnowledgeResult convertToKnowledgeResult(
            String query, 
            List<EmbeddingMatch<TextSegment>> matches,
            List<String> knowledgeIds) {
        
        if (matches == null || matches.isEmpty()) {
            return createEmptyResult(query);
        }
        
        // 过滤：如果指定了knowledgeIds，只返回匹配的文档
        List<EmbeddingMatch<TextSegment>> filteredMatches = matches;
        if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
            filteredMatches = matches.stream()
                .filter(match -> {
                    TextSegment segment = match.embedded();
                    if (segment == null) {
                        return false;
                    }
                    // 从metadata中获取knowledgeId
                    String docKnowledgeId = getMetadataValue(segment, "knowledgeId");
                    return docKnowledgeId != null && knowledgeIds.contains(docKnowledgeId);
                })
                .collect(Collectors.toList());
        }
        
        // 转换为AgentKnowledgeDocument列表
        List<AgentKnowledgeDocument> documents = new ArrayList<>();
        double totalScore = 0.0;
        double maxScore = 0.0;
        double minScore = 1.0;
        
        for (EmbeddingMatch<TextSegment> match : filteredMatches) {
            TextSegment segment = match.embedded();
            if (segment == null) {
                continue;
            }
            
            double score = match.score();
            totalScore += score;
            maxScore = Math.max(maxScore, score);
            minScore = Math.min(minScore, score);
            
            // 获取metadata
            Map<String, Object> metadataMap = extractMetadata(segment);
            
            AgentKnowledgeDocument doc = AgentKnowledgeDocument.builder()
                .content(segment.text())
                .score(score)
                .docName(getMetadataValue(segment, "docName"))
                .docId(getMetadataValue(segment, "docId"))
                .knowledgeId(getMetadataValue(segment, "knowledgeId"))
                .metadata(metadataMap)
                .build();
            
            documents.add(doc);
        }
        
        // 计算平均分数
        double avgScore = documents.isEmpty() ? 0.0 : totalScore / documents.size();
        
        // 生成摘要
        String summary = documents.stream()
            .map(AgentKnowledgeDocument::getContent)
            .filter(content -> content != null && !content.isEmpty())
            .limit(3)
            .collect(Collectors.joining("\n\n"));
        
        return AgentKnowledgeResult.builder()
            .query(query)
            .documents(documents)
            .totalCount(documents.size())
            .summary(summary)
            .avgScore(avgScore)
            .maxScore(maxScore)
            .minScore(minScore > 1.0 ? 0.0 : minScore)
            .build();
    }
    
    /**
     * 从TextSegment中提取metadata
     */
    private Map<String, Object> extractMetadata(TextSegment segment) {
        Map<String, Object> metadataMap = new HashMap<>();
        if (segment == null) {
            return metadataMap;
        }
        
        try {
            dev.langchain4j.data.document.Metadata metadata = segment.metadata();
            if (metadata != null) {
                // 手动提取常见字段
                String[] commonKeys = {"docName", "docId", "knowledgeId", "source", "chunkIndex"};
                for (String key : commonKeys) {
                    Object value = getMetadataValue(segment, key);
                    if (value != null) {
                        metadataMap.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取metadata失败", e);
        }
        
        return metadataMap;
    }
    
    /**
     * 从TextSegment的metadata中获取值
     */
    private String getMetadataValue(TextSegment segment, String key) {
        if (segment == null) {
            return null;
        }
        
        try {
            dev.langchain4j.data.document.Metadata metadata = segment.metadata();
            if (metadata == null) {
                return null;
            }
            
            // 尝试使用getString方法
            try {
                return metadata.getString(key);
            } catch (Exception e1) {
                // 如果getString不存在，尝试通过反射获取值
                try {
                    java.lang.reflect.Method getMethod = metadata.getClass().getMethod("get", String.class);
                    Object value = getMethod.invoke(metadata, key);
                    return value != null ? value.toString() : null;
                } catch (Exception e2) {
                    log.debug("获取metadata值失败: key={}", key, e2);
                    return null;
                }
            }
        } catch (Exception e) {
            log.debug("获取metadata值失败: key={}", key, e);
            return null;
        }
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
    
    /**
     * 应用文档长度限制
     * 支持null表示无限制
     * 
     * @param result 原始检索结果
     * @param ragConfig RAG配置
     * @return 应用长度限制后的结果
     */
    private AgentKnowledgeResult applyLengthLimits(
            AgentKnowledgeResult result, 
            com.aiagent.api.dto.RAGConfig ragConfig) {
        
        if (result == null || result.getDocuments() == null || result.getDocuments().isEmpty()) {
            return result;
        }
        
        List<AgentKnowledgeDocument> documents = result.getDocuments();
        List<AgentKnowledgeDocument> processedDocs = new ArrayList<>();
        int totalLength = 0;
        
        // 获取限制参数
        Integer maxDocLength = ragConfig.getMaxDocumentLength();
        Integer maxTotalLength = ragConfig.getMaxTotalContentLength();
        boolean hasDocLimit = maxDocLength != null && maxDocLength > 0;
        boolean hasTotalLimit = maxTotalLength != null && maxTotalLength > 0;
        
        log.debug("应用长度限制: 单文档限制={}, 总长度限制={}", 
            hasDocLimit ? maxDocLength : "无限制", 
            hasTotalLimit ? maxTotalLength : "无限制");
        
        for (AgentKnowledgeDocument doc : documents) {
            String content = doc.getContent();
            if (content == null) {
                processedDocs.add(doc);
                continue;
            }
            
            int originalLength = content.length();
            
            // 1. 应用单文档长度限制
            if (hasDocLimit && maxDocLength != null && content.length() > maxDocLength) {
                content = content.substring(0, maxDocLength) + "...";
                log.debug("文档被截断: {} -> {} 字符", originalLength, maxDocLength);
            }
            
            // 2. 应用总长度限制
            if (hasTotalLimit && maxTotalLength != null) {
                if (totalLength >= maxTotalLength) {
                    log.debug("已达总长度限制 {}，跳过剩余文档", maxTotalLength);
                    break;
                }
                
                if (totalLength + content.length() > maxTotalLength) {
                    int remaining = maxTotalLength - totalLength;
                    if (remaining > 100) { // 至少保留100字符
                        content = content.substring(0, remaining) + "...";
                        log.debug("文档因总长度限制被截断: {} 字符", remaining);
                    } else {
                        log.debug("剩余空间不足100字符，跳过此文档");
                        break;
                    }
                }
            }
            
            // 创建新的文档对象（避免修改原对象）
            AgentKnowledgeDocument processedDoc = AgentKnowledgeDocument.builder()
                .content(content)
                .score(doc.getScore())
                .docName(doc.getDocName())
                .docId(doc.getDocId())
                .knowledgeId(doc.getKnowledgeId())
                .metadata(doc.getMetadata())
                .build();
            
            processedDocs.add(processedDoc);
            totalLength += content.length();
        }
        
        log.info("长度限制应用完成: 原文档数={}, 处理后文档数={}, 总字符数={}", 
            documents.size(), processedDocs.size(), totalLength);
        
        // 重新计算统计信息
        double totalScore = 0.0;
        double maxScore = 0.0;
        double minScore = 1.0;
        
        for (AgentKnowledgeDocument doc : processedDocs) {
            if (doc.getScore() != null) {
                double score = doc.getScore();
                totalScore += score;
                maxScore = Math.max(maxScore, score);
                minScore = Math.min(minScore, score);
            }
        }
        
        double avgScore = processedDocs.isEmpty() ? 0.0 : totalScore / processedDocs.size();
        
        // 重新构建结果
        return AgentKnowledgeResult.builder()
            .query(result.getQuery())
            .documents(processedDocs)
            .totalCount(processedDocs.size())
            .summary(result.getSummary())
            .avgScore(avgScore)
            .maxScore(maxScore)
            .minScore(minScore > 1.0 ? 0.0 : minScore)
            .build();
    }
}
