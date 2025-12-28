package com.aiagent.service.rag;

import com.aiagent.model.Document;
import com.aiagent.model.KnowledgeBase;
import com.aiagent.repository.DocumentRepository;
import com.aiagent.repository.KnowledgeBaseRepository;
import com.aiagent.util.UUIDGenerator;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
        
        // 保存文件
        String fileId = UUIDGenerator.generate();
        String relativePath = saveFile(knowledgeBaseId, fileId, file);
        
        // 创建文档记录
        Document document = Document.builder()
                .id(fileId)
                .knowledgeBaseId(knowledgeBaseId)
                .title(FilenameUtils.getBaseName(fileName))
                .type(Document.Type.FILE)
                .status(Document.Status.DRAFT)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 设置元数据
        JSONObject metadata = new JSONObject();
        metadata.put("filePath", relativePath);
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
        
        // 保存ZIP文件
        String zipId = UUIDGenerator.generate();
        String zipRelativePath = saveFile(knowledgeBaseId, zipId, zipFile);
        String zipFullPath = new File(uploadPath, zipRelativePath).getAbsolutePath();
        
        // 解压目录
        String extractDir = uploadPath + File.separator + knowledgeBaseId + File.separator + zipId + File.separator + "extracted";
        Path extractPath = Paths.get(extractDir);
        Files.createDirectories(extractPath);
        
        // 解压文件
        List<Document> documents = new ArrayList<>();
        unzipFile(zipFullPath, extractDir, extractedFile -> {
            String extractedFileName = extractedFile.getName();
            if (!isSupportedFile(extractedFileName)) {
                log.warn("Skipping unsupported file: {}", extractedFileName);
                return;
            }
            
            // 创建文档记录
            String docId = UUIDGenerator.generate();
            String relativePath = extractDir.replace(uploadPath + File.separator, "") + 
                                 File.separator + extractedFileName;
            
            Document document = Document.builder()
                    .id(docId)
                    .knowledgeBaseId(knowledgeBaseId)
                    .title(FilenameUtils.getBaseName(extractedFileName))
                    .type(Document.Type.FILE)
                    .status(Document.Status.DRAFT)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            
            // 设置元数据
            JSONObject metadata = new JSONObject();
            metadata.put("filePath", relativePath.replace(File.separator, "/"));
            metadata.put("originalFileName", extractedFileName);
            document.setMetadata(metadata.toJSONString());
            
            documents.add(document);
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
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
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
        document.setUpdateTime(LocalDateTime.now());
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
        
        // 删除文件（如果是文件类型）
        if (Document.Type.FILE.equals(document.getType()) && document.getMetadata() != null) {
            try {
                JSONObject metadata = JSONObject.parseObject(document.getMetadata());
                String filePath = metadata.getString("filePath");
                if (filePath != null) {
                    File file = new File(uploadPath, filePath);
                    if (file.exists()) {
                        file.delete();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to delete file for document: {}", docId, e);
            }
        }
        
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
     * 异步向量化文档
     */
    private void asyncVectorizeDocument(KnowledgeBase knowledgeBase, Document document) {
        CompletableFuture.runAsync(() -> {
            try {
                // 更新状态为构建中
                document.setStatus(Document.Status.BUILDING);
                document.setUpdateTime(LocalDateTime.now());
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
                document.setUpdateTime(LocalDateTime.now());
                documentRepository.save(document);
                
                log.info("Document vectorized successfully: {}", document.getId());
                
            } catch (Exception e) {
                log.error("Failed to vectorize document: {}", document.getId(), e);
                
                // 更新状态为失败
                document.setStatus(Document.Status.FAILED);
                document.setFailedReason(e.getMessage());
                document.setUpdateTime(LocalDateTime.now());
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
        
        // 如果是文件类型，解析文件
        if (Document.Type.FILE.equals(document.getType()) && document.getMetadata() != null) {
            JSONObject metadata = JSONObject.parseObject(document.getMetadata());
            String filePath = metadata.getString("filePath");
            if (filePath != null) {
                File file = new File(uploadPath, filePath);
                if (file.exists()) {
                    return documentParser.parse(file);
                }
            }
        }
        
        throw new IOException("Cannot extract content from document: " + document.getId());
    }
    
    /**
     * 保存文件到本地
     */
    private String saveFile(String knowledgeBaseId, String fileId, MultipartFile file) throws IOException {
        String dir = uploadPath + File.separator + knowledgeBaseId + File.separator + fileId;
        Files.createDirectories(Paths.get(dir));
        
        String fileName = file.getOriginalFilename();
        String savedPath = dir + File.separator + fileName;
        File savedFile = new File(savedPath);
        
        file.transferTo(savedFile);
        
        // 返回相对路径
        return (knowledgeBaseId + File.separator + fileId + File.separator + fileName)
                .replace(File.separator, "/");
    }
    
    /**
     * 解压ZIP文件
     */
    private void unzipFile(String zipFilePath, String destDir, Consumer<File> afterExtract) throws IOException {
        Path zipPath = Paths.get(zipFilePath);
        Path targetDir = Paths.get(destDir);
        
        long totalUnzippedSize = 0;
        int entryCount = 0;
        
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                entryCount++;
                
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw new IOException("Too many entries in ZIP file (possible ZIP bomb)");
                }
                
                Path newPath = safeResolve(targetDir, entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    
                    try (InputStream is = zipFile.getInputStream(entry);
                         OutputStream os = Files.newOutputStream(newPath)) {
                        
                        long bytesCopied = copyLimited(is, os, MAX_FILE_SIZE);
                        totalUnzippedSize += bytesCopied;
                        
                        if (totalUnzippedSize > MAX_TOTAL_SIZE) {
                            throw new IOException("Total extracted size exceeds limit (possible ZIP bomb)");
                        }
                    }
                    
                    if (afterExtract != null) {
                        afterExtract.accept(newPath.toFile());
                    }
                }
            }
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

