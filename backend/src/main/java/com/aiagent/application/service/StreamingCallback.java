package com.aiagent.application.service;

/**
 * 流式输出回调接口
 * 用于实时传递LLM生成的token到前端
 * 
 * @author aiagent
 */
public interface StreamingCallback {
    
    /**
     * 当生成新的token时调用
     * 
     * @param token 生成的token片段
     */
    void onToken(String token);
    
    /**
     * 当产生思考/推理内容时调用 (适配 reasoning_content)
     * 
     * @param thinkingToken 思考内容的token片段
     */
    default void onThinking(String thinkingToken) {
        // 默认空实现
    }
    
    /**
     * 当生成完成时调用
     * 
     * @param fullText 完整的生成文本
     */
    void onComplete(String fullText);
    
    /**
     * 当发生错误时调用
     * 
     * @param error 错误信息
     */
    void onError(Throwable error);
    
    /**
     * 当开始生成时调用（可选）
     */
    default void onStart() {
        // 默认空实现
    }
}

