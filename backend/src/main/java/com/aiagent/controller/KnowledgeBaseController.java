package com.aiagent.controller;

import com.aiagent.model.KnowledgeBase;
import com.aiagent.service.rag.KnowledgeBaseService;
import com.aiagent.vo.KnowledgeBaseRequest;
import com.aiagent.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理Controller
 * 
 * @author aiagent
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    /**
     * 创建知识库
     */
    @PostMapping
    public ResponseEntity<Result<KnowledgeBase>> createKnowledgeBase(@RequestBody KnowledgeBaseRequest request) {
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseService.createKnowledgeBase(
                    request.getName(),
                    request.getDescription(),
                    request.getEmbeddingModelId()
            );
            return ResponseEntity.ok(Result.success("知识库创建成功", knowledgeBase));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create knowledge base", e);
            return ResponseEntity.internalServerError().body(Result.error("创建知识库失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新知识库
     */
    @PutMapping("/{id}")
    public ResponseEntity<Result<KnowledgeBase>> updateKnowledgeBase(
            @PathVariable String id,
            @RequestBody KnowledgeBaseRequest request) {
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseService.updateKnowledgeBase(
                    id,
                    request.getName(),
                    request.getDescription()
            );
            return ResponseEntity.ok(Result.success("知识库更新成功", knowledgeBase));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update knowledge base: {}", id, e);
            return ResponseEntity.internalServerError().body(Result.error("更新知识库失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deleteKnowledgeBase(@PathVariable String id) {
        try {
            knowledgeBaseService.deleteKnowledgeBase(id);
            return ResponseEntity.ok(Result.success("知识库删除成功", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete knowledge base: {}", id, e);
            return ResponseEntity.internalServerError().body(Result.error("删除知识库失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询知识库详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Result<KnowledgeBase>> getKnowledgeBase(@PathVariable String id) {
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBase(id);
            return ResponseEntity.ok(Result.success(knowledgeBase));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get knowledge base: {}", id, e);
            return ResponseEntity.internalServerError().body(Result.error("查询知识库失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询所有知识库
     */
    @GetMapping
    public ResponseEntity<Result<List<KnowledgeBase>>> listKnowledgeBases() {
        try {
            List<KnowledgeBase> knowledgeBases = knowledgeBaseService.listKnowledgeBases();
            return ResponseEntity.ok(Result.success(knowledgeBases));
        } catch (Exception e) {
            log.error("Failed to list knowledge bases", e);
            return ResponseEntity.internalServerError().body(Result.error("查询知识库列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取知识库统计信息
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<Result<KnowledgeBaseService.KnowledgeBaseStats>> getStats(@PathVariable String id) {
        try {
            KnowledgeBaseService.KnowledgeBaseStats stats = knowledgeBaseService.getStats(id);
            return ResponseEntity.ok(Result.success(stats));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to get knowledge base stats: {}", id, e);
            return ResponseEntity.internalServerError().body(Result.error("查询统计信息失败: " + e.getMessage()));
        }
    }
}

