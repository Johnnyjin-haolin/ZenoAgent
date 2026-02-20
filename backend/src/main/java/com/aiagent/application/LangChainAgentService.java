package com.aiagent.application;

import dev.langchain4j.service.TokenStream;

/**
 * LangChain4j Agent服务接口
 * LangChain4j会自动实现工具调用逻辑
 * 
 * 当注册了Tool后，LangChain4j会：
 * 1. 分析用户消息
 * 2. 识别是否需要调用工具
 * 3. 选择合适的工具
 * 4. 提取工具参数
 * 5. 调用Tool.execute()
 * 6. 将结果整合到回复中
 * 
 * @author aiagent
 */
public interface LangChainAgentService {
    /**
     * 聊天方法
     * 
     * @param userMessage 用户消息
     * @return TokenStream 流式响应
     */
    TokenStream chat(String userMessage);
}

