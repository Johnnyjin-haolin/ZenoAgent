package com.aiagent.shared.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果
 * 
 * @author aiagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 数据
     */
    private T data;
    
    /**
     * 错误代码
     */
    private Integer errorCode;
    
    /**
     * 成功响应
     */
    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
                .success(true)
                .message("操作成功")
                .data(data)
                .errorCode(ErrorCode.SUCCESS.getCode())
                .build();
    }
    
    /**
     * 成功响应（带消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return Result.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .errorCode(ErrorCode.SUCCESS.getCode())
                .build();
    }
    
    /**
     * 失败响应
     */
    public static <T> Result<T> error(String message) {
        return Result.<T>builder()
                .success(false)
                .message(message)
                .errorCode(ErrorCode.GENERAL_ERROR.getCode())
                .build();
    }
    
    /**
     * 失败响应（带错误代码）
     */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return Result.<T>builder()
                .success(false)
                .errorCode(errorCode.getCode())
                .message(message)
                .build();
    }
}

