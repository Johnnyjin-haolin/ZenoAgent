package com.aiagent.controller;

import com.aiagent.dto.Page;
import com.aiagent.model.Document;
import com.aiagent.service.rag.DocumentService;
import com.aiagent.vo.Result;
import com.aiagent.vo.TextDocumentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档管理Controller
 * 
 * @author aiagent
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    @Autowired
    private DocumentService documentService;
    
    /**
     * 上传单文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Result<Document>> uploadDocument(
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam("file") MultipartFile file) {
        try {
            Document document = documentService.uploadDocument(knowledgeBaseId, file);
            return ResponseEntity.ok(Result.success("文件上传成功，正在处理中", document));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.internalServerError().body(Result.error("文件上传失败: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.internalServerError().body(Result.error("文件上传失败: " + e.getMessage()));
        }
    }
    
    /**
     * ZIP批量导入
     */
    @PostMapping("/import-zip")
    public ResponseEntity<Result<List<Document>>> importFromZip(
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam("file") MultipartFile zipFile) {
        try {
            List<Document> documents = documentService.importFromZip(knowledgeBaseId, zipFile);
            return ResponseEntity.ok(Result.success("ZIP导入成功，正在处理中", documents));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to import ZIP", e);
            return ResponseEntity.internalServerError().body(Result.error("ZIP导入失败: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to import ZIP", e);
            return ResponseEntity.internalServerError().body(Result.error("ZIP导入失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建文本文档
     */
    @PostMapping("/text")
    public ResponseEntity<Result<Document>> createTextDocument(
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestBody TextDocumentRequest request) {
        try {
            Document document = documentService.saveTextDocument(
                    knowledgeBaseId,
                    request.getTitle(),
                    request.getContent()
            );
            return ResponseEntity.ok(Result.success("文本文档创建成功，正在处理中", document));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create text document", e);
            return ResponseEntity.internalServerError().body(Result.error("创建文本文档失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询知识库的文档列表（支持分页、搜索、筛选、排序）
     */
    @GetMapping("/knowledge-base/{knowledgeBaseId}")
    public ResponseEntity<Result<Map<String, Object>>> listDocuments(
            @PathVariable String knowledgeBaseId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false, defaultValue = "DESC") String orderDirection) {
        try {
            Page<Document> page = documentService.listDocumentsPage(
                    knowledgeBaseId, pageNo, pageSize, keyword, status, type, orderBy, orderDirection);
            
            Map<String, Object> result = new HashMap<>();
            result.put("records", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNo", page.getCurrent());
            result.put("pageSize", page.getSize());
            result.put("pages", page.getPages());
            
            return ResponseEntity.ok(Result.success(result));
        } catch (Exception e) {
            log.error("Failed to list documents for knowledge base: {}", knowledgeBaseId, e);
            return ResponseEntity.internalServerError().body(Result.error("查询文档列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 重建文档向量
     */
    @PutMapping("/{docId}/rebuild")
    public ResponseEntity<Result<Void>> rebuildDocument(@PathVariable String docId) {
        try {
            documentService.rebuildDocument(docId);
            return ResponseEntity.ok(Result.success("文档重建已启动", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to rebuild document: {}", docId, e);
            return ResponseEntity.internalServerError().body(Result.error("重建文档失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除文档
     */
    @DeleteMapping("/{docId}")
    public ResponseEntity<Result<Void>> deleteDocument(@PathVariable String docId) {
        try {
            documentService.deleteDocument(docId);
            return ResponseEntity.ok(Result.success("文档删除成功", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete document: {}", docId, e);
            return ResponseEntity.internalServerError().body(Result.error("删除文档失败: " + e.getMessage()));
        }
    }
}

