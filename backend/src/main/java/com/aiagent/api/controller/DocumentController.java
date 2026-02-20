package com.aiagent.api.controller;

import com.aiagent.api.dto.Page;
import com.aiagent.api.dto.PageResult;
import com.aiagent.domain.model.bo.Document;
import com.aiagent.domain.rag.DocumentService;
import com.aiagent.common.response.Result;
import com.aiagent.api.dto.TextDocumentRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文档管理Controller
 * 
 * @author aiagent
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    @Autowired
    private DocumentService documentService;
    
    /**
     * 上传单文件
     */
    @PostMapping("/upload")
    public Result<Document> uploadDocument(
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam("file") MultipartFile file) throws IOException {
        Document document = documentService.uploadDocument(knowledgeBaseId, file);
        return Result.success("文件上传成功，正在处理中", document);
    }
    
    /**
     * ZIP批量导入
     */
    @PostMapping("/import-zip")
    public Result<List<Document>> importFromZip(
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestParam("file") MultipartFile zipFile) throws IOException {
        List<Document> documents = documentService.importFromZip(knowledgeBaseId, zipFile);
        return Result.success("ZIP导入成功，正在处理中", documents);
    }
    
    /**
     * 创建文本文档
     */
    @PostMapping("/text")
    public Result<Document> createTextDocument(
            @RequestParam("knowledgeBaseId") String knowledgeBaseId,
            @RequestBody TextDocumentRequest request) {
        Document document = documentService.saveTextDocument(
                knowledgeBaseId,
                request.getTitle(),
                request.getContent()
        );
        return Result.success("文本文档创建成功，正在处理中", document);
    }
    
    /**
     * 查询知识库的文档列表（支持分页、搜索、筛选、排序）
     */
    @GetMapping("/knowledge-base/{knowledgeBaseId}")
    public Result<PageResult<Document>> listDocuments(
            @PathVariable String knowledgeBaseId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false, defaultValue = "DESC") String orderDirection) {
        Page<Document> page = documentService.listDocumentsPage(
                knowledgeBaseId, pageNo, pageSize, keyword, status, type, orderBy, orderDirection);

        PageResult<Document> result = PageResult.from(page);
        return Result.success(result);
    }
    
    /**
     * 重建文档向量
     */
    @PutMapping("/{docId}/rebuild")
    public Result<Void> rebuildDocument(@PathVariable String docId) {
        documentService.rebuildDocument(docId);
        return Result.success("文档重建已启动", null);
    }
    
    /**
     * 删除文档
     */
    @DeleteMapping("/{docId}")
    public Result<Void> deleteDocument(@PathVariable String docId) {
        documentService.deleteDocument(docId);
        return Result.success("文档删除成功", null);
    }
}

