package com.aiagent.application.service.rag;

import com.aiagent.domain.model.Document;
import com.aiagent.domain.model.KnowledgeBase;
import com.aiagent.infrastructure.repository.DocumentRepository;
import com.aiagent.infrastructure.repository.KnowledgeBaseRepository;
import com.aiagent.shared.util.UUIDGenerator;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 文档管理服务
 * 
 * @author aiagent
 */
@Slf4j
@Service
public class DocumentService {
    
    /**
     * 支持的文件类型
     */
    private static final List<String> SUPPORTED_FILE_TYPES = List.of(
            "txt", "md", "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt"
    );
    
    /**
     * ZIP解压限制
     */
    private static final long MAX_FILE_SIZE = 150 * 1024 * 1024; // 150MB
    private static final long MAX_TOTAL_SIZE = 1024 * 1024 * 1024; // 1GB
    private static final int MAX_ENTRY_COUNT = 10000;
    
    /**
     * 向量化线程池
     */
    private static final ExecutorService VECTORIZATION_EXECUTOR = Executors.newFixedThreadPool(10);
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private DocumentParser documentParser;
    
    @Autowired
    private EmbeddingProcessor embeddingProcessor;
    
    @Value("${aiagent.rag.upload.path:./uploads}")
    private String uploadPath;
    
    @Value("${aiagent.rag.upload.max-file-size:157286400}")
    private long maxFileSize;
    
    /**
     * 上传单文件并向量化
     * 
     * @param knowledgeBaseId 知识库ID
     * @param file 上传的文件
     * @return 文档对象
     */
    public Document uploadDocument(String knowledgeBaseId, MultipartFile file) throws IOException {
        // 验证知识库存在
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found: " + knowledgeBaseId));
        
        // 验证文件类型
        String fileName = file.getOriginalFilename();
        if (fileName == null || !isSupportedFile(fileName)) {
            throw new IllegalArgumentException("Unsupported file type: " + fileName);
        }
        
        // 验证文件大小
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds limit: " + maxFileSize);
        }
        
        // 直接从流解析文件内容（不保存原文件）
        String fileId = UUIDGenerator.generate();
        String content;
        try (InputStream inputStream = file.getInputStream()) {
            content = documentParser.parse(inputStream, fileName);
        }
        
        // 创建文档记录
        Document document = Document.builder()
                .id(fileId)
                .knowledgeBaseId(knowledgeBaseId)
                .title(FilenameUtils.getBaseName(fileName))
                .type(Document.Type.FILE)
                .content(content)  // 直接存储解析后的内容
                .status(Document.Status.DRAFT)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        
        // 设置元数据（不包含文件路径）
        JSONObject metadata = new JSONObject();
        metadata.put("originalFileName", fileName);
        metadata.put("fileSize", file.getSize());
        document.setMetadata(metadata.toJSONString());
        
        // 保存文档
        documentRepository.save(document);
        
        // 异步向量化
        asyncVectorizeDocument(knowledgeBase, document);
        
        return document;
    }
    
    /**
     * 从ZIP批量导入文档
     * 
     * @param knowledgeBaseId 知识库ID
     * @param zipFile ZIP文件
     * @return 导入的文档列表
     */
    public List<Document> importFromZip(String knowledgeBaseId, MultipartFile zipFile) throws IOException {
        // 验证知识库存在
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found: " + knowledgeBaseId));
        
        // 验证是ZIP文件
        String fileName = zipFile.getOriginalFilename();
        if (fileName == null || !FilenameUtils.getExtension(fileName).equalsIgnoreCase("zip")) {
            throw new IllegalArgumentException("File must be a ZIP archive");
        }
        
        // 从内存中解压ZIP文件（不保存到磁盘）
        List<Document> documents = new ArrayList<>();
        unzipFromStream(zipFile.getInputStream(), entryWithStream -> {
            String entryName = entryWithStream.getName();
            String extractedFileName = new File(entryName).getName();
            
            // 跳过目录和非支持的文件
            if (entryWithStream.isDirectory() || !isSupportedFile(extractedFileName)) {
                log.warn("Skipping unsupported file or directory: {}", entryName);
                return;
            }
            
            try {
                // 直接从ZIP流解析文件内容（不保存到磁盘）
                // 注意：ZipArchiveInputStream 的当前条目流已经是 entryWithStream.getInputStream()
                String content = documentParser.parse(entryWithStream.getInputStream(), extractedFileName);
                
                // 创建文档记录
                String docId = UUIDGenerator.generate();
                Document document = Document.builder()
                        .id(docId)
                        .knowledgeBaseId(knowledgeBaseId)
                        .title(FilenameUtils.getBaseName(extractedFileName))
                        .type(Document.Type.FILE)
                        .content(content)  // 直接存储解析后的内容
                        .status(Document.Status.DRAFT)
                        .createTime(new Date())
                        .updateTime(new Date())
                        .build();
                
                // 设置元数据（不包含文件路径）
                JSONObject metadata = new JSONObject();
                metadata.put("originalFileName", extractedFileName);
                metadata.put("entryPath", entryName);  // ZIP中的路径
                document.setMetadata(metadata.toJSONString());
                
                documents.add(document);
            } catch (Exception e) {
                log.error("Failed to parse file from ZIP: {}", entryName, e);
                // 继续处理其他文件
            }
        });
        
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("No supported files found in ZIP archive");
        }
        
        // 批量保存文档
        documentRepository.saveAll(documents);
        
        // 异步向量化所有文档
        for (Document document : documents) {
            asyncVectorizeDocument(knowledgeBase, document);
        }
        
        return documents;
    }
    
    /**
     * 保存文本文档
     * 
     * @param knowledgeBaseId 知识库ID
     * @param title 标题
     * @param content 内容
     * @return 文档对象
     */
    public Document saveTextDocument(String knowledgeBaseId, String title, String content) {
        // 验证知识库存在
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found: " + knowledgeBaseId));
        
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }
        
        // 创建文档
        Document document = Document.builder()
                .id(UUIDGenerator.generate())
                .knowledgeBaseId(knowledgeBaseId)
                .title(title)
                .type(Document.Type.TEXT)
                .content(content)
                .status(Document.Status.DRAFT)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        
        // 保存文档
        documentRepository.save(document);
        
        // 异步向量化
        asyncVectorizeDocument(knowledgeBase, document);
        
        return document;
    }
    
    /**
     * 重建文档向量
     * 
     * @param docId 文档ID
     */
    public void rebuildDocument(String docId) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + docId));
        
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(document.getKnowledgeBaseId())
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found: " + document.getKnowledgeBaseId()));
        
        // 更新状态
        document.setStatus(Document.Status.BUILDING);
        document.setUpdateTime(new Date());
        documentRepository.save(document);
        
        // 异步向量化
        asyncVectorizeDocument(knowledgeBase, document);
    }
    
    /**
     * 删除文档
     * 
     * @param docId 文档ID
     */
    public void deleteDocument(String docId) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + docId));
        
        // 删除向量数据
        embeddingProcessor.deleteDocumentVectors(docId, null);
        
        // 不再需要删除文件，因为文件不再保存到磁盘
        // 文件内容已存储在数据库的 content 字段中，删除文档记录时自动删除
        
        // 删除文档记录
        documentRepository.deleteById(docId);
    }
    
    /**
     * 查询知识库的所有文档
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 文档列表
     */
    public List<Document> listDocuments(String knowledgeBaseId) {
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }
    
    /**
     * 分页查询文档列表（支持搜索、筛选、排序）
     * 
     * @param knowledgeBaseId 知识库ID
     * @param pageNo 页码（从1开始）
     * @param pageSize 每页大小
     * @param keyword 关键词（文档名称搜索）
     * @param status 状态筛选
     * @param type 类型筛选
     * @param orderBy 排序字段（如：create_time）
     * @param orderDirection 排序方向（ASC/DESC）
     * @return 分页结果
     */
    public com.aiagent.api.dto.Page<Document> listDocumentsPage(
            String knowledgeBaseId,
            int pageNo,
            int pageSize,
            String keyword,
            String status,
            String type,
            String orderBy,
            String orderDirection) {
        int offset = (pageNo - 1) * pageSize;
        
        // 处理排序字段，防止SQL注入
        String safeOrderBy = validateOrderBy(orderBy);
        String safeOrderDirection = "DESC".equalsIgnoreCase(orderDirection) ? "DESC" : "ASC";
        
        List<Document> documents = documentRepository.findPage(
                knowledgeBaseId, keyword, status, type, safeOrderBy, safeOrderDirection, offset, pageSize);
        int total = documentRepository.countByConditions(knowledgeBaseId, keyword, status, type);
        
        com.aiagent.api.dto.Page<Document> page = new com.aiagent.api.dto.Page<>();
        page.setRecords(documents);
        page.setTotal(total);
        page.setCurrent(pageNo);
        page.setSize(pageSize);
        
        log.debug("查询文档列表: knowledgeBaseId={}, pageNo={}, pageSize={}, total={}", 
                knowledgeBaseId, pageNo, pageSize, total);
        return page;
    }
    
    /**
     * 验证排序字段，防止SQL注入
     */
    private String validateOrderBy(String orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return null;
        }
        // 只允许字母、数字和下划线
        if (orderBy.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            return orderBy;
        }
        return null;
    }
    
    /**
     * 异步向量化文档
     */
    private void asyncVectorizeDocument(KnowledgeBase knowledgeBase, Document document) {
        CompletableFuture.runAsync(() -> {
            try {
                // 更新状态为构建中
                document.setStatus(Document.Status.BUILDING);
                document.setUpdateTime(new Date());
                documentRepository.save(document);
                
                // 解析文档内容
                String content = extractDocumentContent(document);
                
                // 向量化并存储
                embeddingProcessor.processDocument(
                        knowledgeBase.getId(),
                        document,
                        content,
                        knowledgeBase.getEmbeddingModelId()
                );
                
                // 更新状态为完成
                document.setStatus(Document.Status.COMPLETE);
                document.setUpdateTime(new Date());
                documentRepository.save(document);
                
                log.info("Document vectorized successfully: {}", document.getId());
                
            } catch (Exception e) {
                log.error("Failed to vectorize document: {}", document.getId(), e);
                
                // 更新状态为失败
                document.setStatus(Document.Status.FAILED);
                document.setUpdateTime(new Date());
                documentRepository.save(document);
            }
        }, VECTORIZATION_EXECUTOR);
    }
    
    /**
     * 提取文档内容
     */
    private String extractDocumentContent(Document document) throws IOException {
        // 如果是文本类型，直接返回内容
        if (Document.Type.TEXT.equals(document.getType())) {
            return document.getContent() != null ? document.getContent() : "";
        }
        
        // 如果是文件类型，直接返回已解析的内容（内容已存储在数据库中）
        if (Document.Type.FILE.equals(document.getType())) {
            if (document.getContent() != null && !document.getContent().isEmpty()) {
                return document.getContent();
            }
            // 如果内容为空，说明解析失败或数据异常
            throw new IOException("Document content is empty: " + document.getId());
        }
        
        throw new IOException("Cannot extract content from document: " + document.getId());
    }

    /**
     * 从输入流中解压ZIP文件（不保存到磁盘）
     * 
     * @param inputStream ZIP文件的输入流
     * @param entryProcessor 处理每个ZIP条目的回调函数
     */
    private void unzipFromStream(InputStream inputStream, Consumer<ZipArchiveEntryWithStream> entryProcessor) throws IOException {
        int entryCount = 0;
        long totalSize = 0;
        
        try (ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(inputStream)) {
            ZipArchiveEntry entry;
            while ((entry = zipInputStream.getNextZipEntry()) != null) {
                entryCount++;
                
                // 检查条目数量限制
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new IOException("Too many entries in ZIP file (possible ZIP bomb)");
                }
                
                // 跳过目录
                if (entry.isDirectory()) {
                    continue;
                }
                
                // 检查单个文件大小限制
                long entrySize = entry.getSize();
                if (entrySize > MAX_FILE_SIZE) {
                    log.warn("Skipping large file in ZIP: {} (size: {})", entry.getName(), entrySize);
                    continue;
                }
                
                // 检查总大小限制
                totalSize += entrySize;
                if (totalSize > MAX_TOTAL_SIZE) {
                    throw new IOException("Total extracted size exceeds limit (possible ZIP bomb)");
                }
                
                // 创建包装对象，包含条目和流
                ZipArchiveEntryWithStream entryWithStream = new ZipArchiveEntryWithStream(entry, zipInputStream);
                entryProcessor.accept(entryWithStream);
            }
        }
    }
    
    /**
     * ZIP条目包装类，包含条目信息和输入流
     */
    private static class ZipArchiveEntryWithStream {
        private final ZipArchiveEntry entry;
        private final ZipArchiveInputStream zipInputStream;
        
        public ZipArchiveEntryWithStream(ZipArchiveEntry entry, ZipArchiveInputStream zipInputStream) {
            this.entry = entry;
            this.zipInputStream = zipInputStream;
        }
        
        public String getName() {
            return entry.getName();
        }
        
        public boolean isDirectory() {
            return entry.isDirectory();
        }
        
        public InputStream getInputStream() {
            // 返回 ZipArchiveInputStream，它已经定位到当前条目的数据流
            return zipInputStream;
        }
    }
    
    /**
     * 安全解析路径，防止路径遍历攻击
     */
    private Path safeResolve(Path targetDir, String entryName) throws IOException {
        Path resolvedPath = targetDir.resolve(entryName).normalize();
        if (!resolvedPath.startsWith(targetDir)) {
            throw new IOException("ZIP path traversal attack detected: " + entryName);
        }
        return resolvedPath;
    }
    
    /**
     * 复制输入流到输出流，限制大小
     */
    private long copyLimited(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long totalCopied = 0;
        int bytesRead;
        
        while ((bytesRead = in.read(buffer)) != -1) {
            totalCopied += bytesRead;
            if (totalCopied > maxBytes) {
                throw new IOException("File size exceeds limit (possible ZIP bomb)");
            }
            out.write(buffer, 0, bytesRead);
        }
        
        return totalCopied;
    }
    
    /**
     * 检查文件类型是否支持
     */
    private boolean isSupportedFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String ext = FilenameUtils.getExtension(fileName).toLowerCase();
        return SUPPORTED_FILE_TYPES.contains(ext);
    }
}

