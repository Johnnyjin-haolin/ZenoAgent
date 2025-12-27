package com.aiagent.service;

import com.aiagent.util.StringUtils;
import com.aiagent.vo.AgentKnowledgeDocument;
import com.aiagent.vo.AgentKnowledgeResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    
    /**
     * 默认检索数量
     */
    private static final int DEFAULT_TOP_K = 5;
    
    /**
     * 默认相似度阈值
     */
    private static final double DEFAULT_MIN_SCORE = 0.5;
    
    @Value("${aiagent.llm.api-key:}")
    private String defaultApiKey;
    
    @Value("${aiagent.llm.base-url:https://api.openai.com/v1}")
    private String defaultBaseUrl;
    
    @Value("${aiagent.rag.default-top-k:5}")
    private int defaultTopK;
    
    @Value("${aiagent.rag.default-min-score:0.5}")
    private double defaultMinScore;
    
    /**
     * Embedding模型（用于生成文本向量）
     * 如果未注入，将使用OpenAI的Embedding模型
     */
    private EmbeddingModel embeddingModel;
    
    /**
     * Embedding存储（用于向量检索）
     * 如果未注入，将使用内存存储（仅用于演示）
     */
    private EmbeddingStore<TextSegment> embeddingStore;
    
    /**
     * 初始化Embedding模型
     */
    private EmbeddingModel getOrCreateEmbeddingModel() {
        if (embeddingModel != null) {
            return embeddingModel;
        }
        
        // 使用OpenAI Embedding模型
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = defaultApiKey;
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("未配置OPENAI_API_KEY，无法使用Embedding功能");
            return null;
        }
        
        embeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey(apiKey)
            .baseUrl(defaultBaseUrl)
            .modelName("text-embedding-3-small")
            .build();
        
        log.info("初始化Embedding模型: text-embedding-3-small");
        return embeddingModel;
    }
    
    /**
     * 注入自定义的Embedding模型
     */
    @Autowired(required = false)
    public void setEmbeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        log.info("注入自定义Embedding模型: {}", embeddingModel != null ? embeddingModel.getClass().getSimpleName() : "null");
    }
    
    /**
     * 注入自定义的Embedding存储
     */
    @Autowired(required = false)
    public void setEmbeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
        log.info("注入自定义Embedding存储: {}", embeddingStore != null ? embeddingStore.getClass().getSimpleName() : "null");
    }
    
    /**
     * 检索相关知识
     * 
     * 使用langchain4j的EmbeddingModel和EmbeddingStore实现RAG检索
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
        
        // 使用langchain4j实现RAG检索
        try {
            // 1. 获取或创建Embedding模型
            EmbeddingModel model = getOrCreateEmbeddingModel();
            if (model == null) {
                log.warn("Embedding模型不可用，返回空结果");
                return createEmptyResult(query);
            }
            
            // 2. 获取Embedding存储
            EmbeddingStore<TextSegment> store = getOrCreateEmbeddingStore();
            if (store == null) {
                log.warn("Embedding存储不可用，返回空结果");
                return createEmptyResult(query);
            }
            
            // 3. 生成查询向量
            Embedding queryEmbedding = model.embed(query).content();
            
            // 4. 从存储中检索相似文档
            int maxResults = defaultTopK > 0 ? defaultTopK : DEFAULT_TOP_K;
            double minScore = defaultMinScore > 0 ? defaultMinScore : DEFAULT_MIN_SCORE;
            
            // 使用EmbeddingStore的search方法检索相似文档
            // langchain4j的EmbeddingStore接口使用search方法，接受EmbeddingSearchRequest参数
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
            
            EmbeddingSearchResult<TextSegment> searchResult = store.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
            
            // 5. 转换为AgentKnowledgeResult
            return convertToKnowledgeResult(query, matches, knowledgeIds);
            
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
     * 获取或创建Embedding存储
     * 如果未注入，使用内存存储（仅用于演示，不支持持久化）
     */
    private EmbeddingStore<TextSegment> getOrCreateEmbeddingStore() {
        if (embeddingStore != null) {
            return embeddingStore;
        }
        
        // 使用内存存储（仅用于演示）
        // 实际生产环境应该使用持久化的存储（如PgVector）
        log.warn("未注入Embedding存储，使用内存存储（仅用于演示，不支持持久化）");
        embeddingStore = new InMemoryEmbeddingStore<>();
        
        return embeddingStore;
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
            
            // Metadata接口可能没有直接的get方法
            // 尝试通过反射获取值（作为最后手段）
            try {
                java.lang.reflect.Method getMethod = metadata.getClass().getMethod("get", String.class);
                Object value = getMethod.invoke(metadata, key);
                return value != null ? value.toString() : null;
            } catch (Exception e) {
                log.debug("获取metadata值失败: key={}", key, e);
                return null;
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
}
