package com.aiagent.api.controller;

import com.aiagent.domain.model.KnowledgeBase;
import com.aiagent.application.service.rag.KnowledgeBaseService;
import com.aiagent.api.dto.KnowledgeBaseRequest;
import com.aiagent.shared.response.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理Controller
 * 
 * @author aiagent
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    /**
     * 创建知识库
     */
    @PostMapping
    public Result<KnowledgeBase> createKnowledgeBase(@RequestBody KnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.createKnowledgeBase(
                request.getName(),
                request.getDescription(),
                request.getEmbeddingModelId()
        );
        return Result.success("知识库创建成功", knowledgeBase);
    }
    
    /**
     * 更新知识库
     */
    @PutMapping("/{id}")
    public Result<KnowledgeBase> updateKnowledgeBase(
            @PathVariable String id,
            @RequestBody KnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.updateKnowledgeBase(
                id,
                request.getName(),
                request.getDescription()
        );
        return Result.success("知识库更新成功", knowledgeBase);
    }
    
    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable String id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return Result.success("知识库删除成功", null);
    }
    
    /**
     * 查询知识库详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBase> getKnowledgeBase(@PathVariable String id) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBase(id);
        return Result.success(knowledgeBase);
    }
    
    /**
     * 查询所有知识库
     */
    @GetMapping
    public Result<List<KnowledgeBase>> listKnowledgeBases() {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseService.listKnowledgeBases();
        return Result.success(knowledgeBases);
    }
    
    /**
     * 获取知识库统计信息
     */
    @GetMapping("/{id}/stats")
    public Result<KnowledgeBaseService.KnowledgeBaseStats> getStats(@PathVariable String id) {
        KnowledgeBaseService.KnowledgeBaseStats stats = knowledgeBaseService.getStats(id);
        return Result.success(stats);
    }
}

