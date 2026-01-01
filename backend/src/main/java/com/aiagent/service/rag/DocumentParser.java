package com.aiagent.service.rag;

import dev.langchain4j.data.document.BlankDocumentException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 文档解析器
 * 支持多种文档格式的解析
 * 
 * @author aiagent
 */
@Slf4j
@Service
public class DocumentParser {
    
    private static final Tika tika = new Tika();
    
    /**
     * 解析文件，返回文本内容
     * 
     * @param file 文件对象
     * @return 解析后的文本内容
     */
    public String parse(File file) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + file);
        }

        try {
            String fileName = file.getName().toLowerCase();
            InputStream inputStream = Files.newInputStream(file.toPath());
            
            try {
                if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".pdf")) {
                    return parseByTika(inputStream);
                } else if (fileName.endsWith(".docx") || fileName.endsWith(".doc") ||
                           fileName.endsWith(".xlsx") || fileName.endsWith(".xls") ||
                           fileName.endsWith(".pptx") || fileName.endsWith(".ppt")) {
                    return parseByTika(inputStream);
                } else {
                    log.warn("Unsupported file type, trying Tika auto-detect: {}", FilenameUtils.getExtension(fileName));
                    return parseByTika(inputStream);
                }
            } finally {
                inputStream.close();
            }
        } catch (Exception e) {
            log.error("Failed to parse file: {}", file.getName(), e);
            throw new RuntimeException("Failed to parse file: " + file.getName(), e);
        }
    }
    
    /**
     * 解析输入流，返回文本内容
     * 
     * @param inputStream 输入流
     * @param fileName 文件名（用于识别格式）
     * @return 解析后的文本内容
     */
    public String parse(InputStream inputStream, String fileName) {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        
        try {
            return parseByTika(inputStream);
        } catch (Exception e) {
            log.error("Failed to parse input stream: {}", fileName, e);
            throw new RuntimeException("Failed to parse input stream: " + fileName, e);
        }
    }
    
    /**
     * 使用Tika解析文档
     * 
     * @param inputStream 输入流
     * @return 解析后的文本内容
     */
    private String parseByTika(InputStream inputStream) {
        try {
            Parser parser = new AutoDetectParser();
            ContentHandler contentHandler = new BodyContentHandler(-1); // -1表示不限制大小
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            
            parser.parse(inputStream, contentHandler, metadata, parseContext);
            String text = contentHandler.toString();
            
            if (text == null || text.trim().isEmpty()) {
                throw new BlankDocumentException();
            }
            
            return text;
        } catch (BlankDocumentException e) {
            throw e;
        } catch (ZeroByteFileException e) {
            throw new BlankDocumentException();
        } catch (Exception e) {
            log.error("Tika parsing failed", e);
            throw new RuntimeException("Failed to parse document with Tika", e);
        }
    }
    
    /**
     * 检查是否支持的文件类型
     * 
     * @param fileName 文件名
     * @return 是否支持
     */
    public static boolean isSupportedFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        
        String ext = FilenameUtils.getExtension(fileName).toLowerCase();
        return ext.equals("txt") || ext.equals("md") || ext.equals("pdf") ||
               ext.equals("docx") || ext.equals("doc") ||
               ext.equals("xlsx") || ext.equals("xls") ||
               ext.equals("pptx") || ext.equals("ppt");
    }
}

