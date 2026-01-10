package com.aiagent.service.rag;

import com.aiagent.config.AgentConfig;
import com.aiagent.config.EmbeddingStoreConfiguration;
import com.aiagent.model.Document;
import com.aiagent.service.llm.EmbeddingModelManager;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 向量化处理器
 * 负责文档向量化和存储
 * 
 * @author aiagent
 */
@Slf4j
@Component
public class EmbeddingProcessor {
    
    /**
     * 向量存储元数据: knowledgeId
     */
    public static final String METADATA_KNOWLEDGE_ID = "knowledgeId";
    
    /**
     * 向量存储元数据: docId
     */
    public static final String METADATA_DOC_ID = "docId";
    
    /**
     * 向量存储元数据: docName
     */
    public static final String METADATA_DOC_NAME = "docName";
    
    /**
     * 默认分段大小
     */
    private static final int DEFAULT_SEGMENT_SIZE = 1000;
    
    /**
     * 默认重叠大小
     */
    private static final int DEFAULT_OVERLAP_SIZE = 50;
    
    @Autowired
    private AgentConfig agentConfig;
    
    @Autowired
    private EmbeddingStoreConfiguration embeddingStoreConfiguration;
    
    @Autowired
    private EmbeddingModelManager embeddingModelManager;
    
    @Value("${aiagent.rag.document.segment-size:1000}")
    private int segmentSize;
    
    @Value("${aiagent.rag.document.overlap-size:50}")
    private int overlapSize;
    
    /**
     * 处理文档，进行向量化并存储
     * 
     * @param knowledgeBaseId 知识库ID
     * @param document 文档对象
     * @param content 文档内容
     * @param embeddingModelId 向量模型ID（可选，如果为空则使用默认模型）
     * @return 处理结果元数据
     */
    public Map<String, Object> processDocument(String knowledgeBaseId, Document document, String content, String embeddingModelId) {
        log.info("Processing document for vectorization: docId={}, knowledgeBaseId={}", 
                 document.getId(), knowledgeBaseId);
        
        try {
            // 1. 获取或创建Embedding模型
            EmbeddingModel embeddingModel = getOrCreateEmbeddingModel(embeddingModelId);
            
            // 2. 获取EmbeddingStore
            EmbeddingStore<TextSegment> embeddingStore = embeddingStoreConfiguration
                    .createDefaultEmbeddingStore(embeddingModel);
            
            // 3. 删除旧的向量数据（如果存在）
            deleteDocumentVectors(document.getId(), embeddingStore);
            
            // 4. 分段器
            DocumentSplitter splitter = DocumentSplitters.recursive(
                    segmentSize > 0 ? segmentSize : DEFAULT_SEGMENT_SIZE,
                    overlapSize > 0 ? overlapSize : DEFAULT_OVERLAP_SIZE,
                    new OpenAiTokenCountEstimator(embeddingModel.modelName())
            );
            
            // 5. 构建元数据
            Metadata metadata = Metadata.metadata(METADATA_DOC_ID, document.getId())
                    .put(METADATA_KNOWLEDGE_ID, knowledgeBaseId)
                    .put(METADATA_DOC_NAME, FilenameUtils.getName(document.getTitle()));
            
            // 6. 如果有标题，将标题添加到内容前面
            String finalContent = content;
            if (document.getTitle() != null && !document.getTitle().trim().isEmpty()) {
                finalContent = document.getTitle() + "\n\n" + content;
            }
            
            // 7. 创建LangChain4j Document
            dev.langchain4j.data.document.Document langChainDocument = 
                    dev.langchain4j.data.document.Document.from(finalContent, metadata);
            
            // 8. 分段并向量化存储
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            
            ingestor.ingest(langChainDocument);
            
            log.info("Document vectorized successfully: docId={}", document.getId());
            
            return metadata.toMap();
            
        } catch (Exception e) {
            log.error("Failed to process document: docId={}", document.getId(), e);
            throw new RuntimeException("Failed to process document: " + document.getId(), e);
        }
    }
    
    /**
     * 删除文档的所有向量数据
     * 
     * @param docId 文档ID
     * @param embeddingStore EmbeddingStore实例
     */
    public void deleteDocumentVectors(String docId, EmbeddingStore<TextSegment> embeddingStore) {
        try {
            embeddingStore.removeAll(metadataKey(METADATA_DOC_ID).isEqualTo(docId));
            log.debug("Deleted vectors for document: {}", docId);
        } catch (Exception e) {
            log.warn("Failed to delete vectors for document: {}", docId, e);
        }
    }
    
    /**
     * 删除知识库的所有向量数据
     * 
     * @param knowledgeBaseId 知识库ID
     * @param embeddingModelId 向量模型ID
     */
    public void deleteKnowledgeBaseVectors(String knowledgeBaseId, String embeddingModelId) {
        try {
            EmbeddingModel embeddingModel = getOrCreateEmbeddingModel(embeddingModelId);
            EmbeddingStore<TextSegment> embeddingStore = embeddingStoreConfiguration
                    .createDefaultEmbeddingStore(embeddingModel);
            
            embeddingStore.removeAll(metadataKey(METADATA_KNOWLEDGE_ID).isEqualTo(knowledgeBaseId));
            log.info("Deleted vectors for knowledge base: {}", knowledgeBaseId);
        } catch (Exception e) {
            log.error("Failed to delete vectors for knowledge base: {}", knowledgeBaseId, e);
        }
    }
    
    /**
     * 删除多个文档的向量数据
     * 
     * @param docIds 文档ID列表
     * @param embeddingModelId 向量模型ID
     */
    public void deleteDocumentVectors(java.util.List<String> docIds, String embeddingModelId) {
        try {
            EmbeddingModel embeddingModel = getOrCreateEmbeddingModel(embeddingModelId);
            EmbeddingStore<TextSegment> embeddingStore = embeddingStoreConfiguration
                    .createDefaultEmbeddingStore(embeddingModel);
            
            for (String docId : docIds) {
                deleteDocumentVectors(docId, embeddingStore);
            }
            
            log.info("Deleted vectors for {} documents", docIds.size());
        } catch (Exception e) {
            log.error("Failed to delete vectors for documents", e);
        }
    }
    
    /**
     * 获取或创建Embedding模型
     * 
     * @param embeddingModelId 模型ID（可选，如果为空则使用默认模型）
     * @return EmbeddingModel实例
     */
    private EmbeddingModel getOrCreateEmbeddingModel(String embeddingModelId) {
        // 使用 EmbeddingModelManager 统一管理 Embedding 模型
        if (embeddingModelId == null || embeddingModelId.isEmpty()) {
            return embeddingModelManager.getDefaultEmbeddingModel();
        }
        return embeddingModelManager.getOrCreateEmbeddingModel(embeddingModelId);
    }
    
    /**
     * 生成查询向量
     * 
     * @param queryText 查询文本
     * @param embeddingModelId 向量模型ID
     * @return Embedding向量
     */
    public Embedding embedQuery(String queryText, String embeddingModelId) {
        EmbeddingModel embeddingModel = getOrCreateEmbeddingModel(embeddingModelId);
        return embeddingModel.embed(queryText).content();
    }
}

