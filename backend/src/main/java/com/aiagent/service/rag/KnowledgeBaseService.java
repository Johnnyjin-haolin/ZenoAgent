package com.aiagent.service.rag;

import com.aiagent.model.Document;
import com.aiagent.model.KnowledgeBase;
import com.aiagent.repository.DocumentRepository;
import com.aiagent.repository.KnowledgeBaseRepository;
import com.aiagent.service.rag.EmbeddingProcessor;
import com.aiagent.util.UUIDGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库管理服务
 * 
 * @author aiagent
 */
@Slf4j
@Service
public class KnowledgeBaseService {
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private EmbeddingProcessor embeddingProcessor;
    
    /**
     * 创建知识库
     * 
     * @param name 名称
     * @param description 描述
     * @param embeddingModelId 向量模型ID
     * @return 知识库对象
     */
    public KnowledgeBase createKnowledgeBase(String name, String description, String embeddingModelId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Knowledge base name cannot be empty");
        }
        
        if (embeddingModelId == null || embeddingModelId.trim().isEmpty()) {
            throw new IllegalArgumentException("Embedding model ID cannot be empty");
        }
        
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .id(UUIDGenerator.generate())
                .name(name)
                .description(description)
                .embeddingModelId(embeddingModelId)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        knowledgeBaseRepository.save(knowledgeBase);
        log.info("Created knowledge base: {}", knowledgeBase.getId());
        
        return knowledgeBase;
    }
    
    /**
     * 更新知识库
     * 
     * @param id 知识库ID
     * @param name 名称
     * @param description 描述
     * @return 更新后的知识库对象
     */
    public KnowledgeBase updateKnowledgeBase(String id, String name, String description) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found: " + id));
        
        if (name != null && !name.trim().isEmpty()) {
            knowledgeBase.setName(name);
        }
        
        if (description != null) {
            knowledgeBase.setDescription(description);
        }
        
        knowledgeBase.setUpdateTime(LocalDateTime.now());
        knowledgeBaseRepository.save(knowledgeBase);
        
        log.info("Updated knowledge base: {}", id);
        return knowledgeBase;
    }
    
    /**
     * 删除知识库
     * 
     * @param id 知识库ID
     */
    public void deleteKnowledgeBase(String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found: " + id));
        
        // 删除所有文档的向量数据
        List<Document> documents = documentRepository.findByKnowledgeBaseId(id);
        if (!documents.isEmpty()) {
            List<String> docIds = documents.stream()
                    .map(Document::getId)
                    .collect(Collectors.toList());
            embeddingProcessor.deleteDocumentVectors(docIds, knowledgeBase.getEmbeddingModelId());
        }
        
        // 删除知识库的向量数据
        embeddingProcessor.deleteKnowledgeBaseVectors(id, knowledgeBase.getEmbeddingModelId());
        
        // 删除所有文档记录
        documentRepository.deleteByKnowledgeBaseId(id);
        
        // 删除知识库记录
        knowledgeBaseRepository.deleteById(id);
        
        log.info("Deleted knowledge base: {}", id);
    }
    
    /**
     * 查询知识库详情
     * 
     * @param id 知识库ID
     * @return 知识库对象
     */
    public KnowledgeBase getKnowledgeBase(String id) {
        return knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found: " + id));
    }
    
    /**
     * 查询所有知识库
     * 
     * @return 知识库列表
     */
    public List<KnowledgeBase> listKnowledgeBases() {
        return knowledgeBaseRepository.findAll();
    }
    
    /**
     * 获取知识库统计信息
     * 
     * @param id 知识库ID
     * @return 统计信息对象
     */
    public KnowledgeBaseStats getStats(String id) {
        KnowledgeBase knowledgeBase = getKnowledgeBase(id);
        List<Document> documents = documentRepository.findByKnowledgeBaseId(id);
        
        long totalDocuments = documents.size();
        long completedDocuments = documents.stream()
                .filter(doc -> Document.Status.COMPLETE.equals(doc.getStatus()))
                .count();
        long failedDocuments = documents.stream()
                .filter(doc -> Document.Status.FAILED.equals(doc.getStatus()))
                .count();
        long buildingDocuments = documents.stream()
                .filter(doc -> Document.Status.BUILDING.equals(doc.getStatus()))
                .count();
        
        return KnowledgeBaseStats.builder()
                .knowledgeBaseId(id)
                .knowledgeBaseName(knowledgeBase.getName())
                .totalDocuments(totalDocuments)
                .completedDocuments(completedDocuments)
                .failedDocuments(failedDocuments)
                .buildingDocuments(buildingDocuments)
                .build();
    }
    
    /**
     * 知识库统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class KnowledgeBaseStats {
        private String knowledgeBaseId;
        private String knowledgeBaseName;
        private long totalDocuments;
        private long completedDocuments;
        private long failedDocuments;
        private long buildingDocuments;
    }
}

