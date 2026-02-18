package com.aiagent.shared.constant;

/**
 * Agent 常量定义
 * 
 * @author aiagent
 */
public class AgentConstants {
    
    /**
     * 默认Agent ID
     */
    public static final String DEFAULT_AGENT_ID = "default-agent";
    
    /**
     * 默认最大迭代次数
     */
    public static final int DEFAULT_MAX_ITERATIONS = 10;
    
    /**
     * 默认最大工具调用次数
     */
    public static final int DEFAULT_MAX_TOOL_CALLS = 5;
    
    /**
     * 默认上下文窗口大小
     */
    public static final int DEFAULT_CONTEXT_WINDOW = 10;
    
    /**
     * 任务分类提示词
     */
    public static final String TASK_CLASSIFY_PROMPT = 
        "你是一个任务分类助手。根据用户输入，判断任务类型并返回对应的标识。\n\n" +
        "任务类型说明：\n" +
        "1. SIMPLE_CHAT - 简单对话：闲聊、问候、简单问答等，不需要工具或知识库\n" +
        "2. RAG_QUERY - 知识查询：需要从知识库检索信息回答的问题\n" +
        "3. TOOL_CALL - 工具调用：需要调用外部工具或执行设备操作\n" +
        "4. COMPLEX_WORKFLOW - 复杂任务：需要多步骤、多工具协同完成的任务\n\n" +
        "用户输入：{{user_input}}\n\n" +
        "可用工具列表：\n{{available_tools}}\n\n" +
        "可用知识库：\n{{available_knowledge}}\n\n" +
        "请直接返回任务类型标识（SIMPLE_CHAT/RAG_QUERY/TOOL_CALL/COMPLEX_WORKFLOW），不要其他解释。";
    
    /**
     * 工具选择提示词
     */
    public static final String TOOL_SELECT_PROMPT = 
        "你是一个工具选择助手。根据用户需求，从可用工具列表中选择最合适的工具。\n\n" +
        "用户需求：{{user_input}}\n\n" +
        "可用工具列表：\n{{tool_list}}\n\n" +
        "请返回JSON格式的工具调用列表：\n" +
        "[{\"toolName\": \"工具名称\", \"reason\": \"选择原因\", \"params\": {参数}}]\n\n" +
        "如果不需要工具，返回空数组 []";
    
    /**
     * SSE事件类型
     */
    public static final String EVENT_AGENT_START = "agent:start";
    public static final String EVENT_AGENT_THINKING = "agent:thinking";
    public static final String EVENT_AGENT_THINKING_DELTA = "agent:thinking_delta";
    public static final String EVENT_AGENT_PLANNING = "agent:planning";
    public static final String EVENT_AGENT_MODEL_SELECTED = "agent:model_selected";
    public static final String EVENT_AGENT_TOOL_CALL = "agent:tool_call";
    public static final String EVENT_AGENT_TOOL_RESULT = "agent:tool_result";
    public static final String EVENT_AGENT_TOOL_EXECUTING = "agent:tool_executing";
    public static final String EVENT_AGENT_RAG_RETRIEVE = "agent:rag_retrieve";
    public static final String EVENT_AGENT_RAG_QUERYING = "agent:rag_querying";
    public static final String EVENT_AGENT_GENERATING = "agent:generating";
    public static final String EVENT_AGENT_OBSERVING = "agent:observing";
    public static final String EVENT_AGENT_REFLECTING = "agent:reflecting";
    public static final String EVENT_AGENT_MESSAGE = "agent:message";
    public static final String EVENT_AGENT_STREAM_COMPLETE = "agent:stream_complete";
    public static final String EVENT_AGENT_COMPLETE = "agent:complete";
    public static final String EVENT_AGENT_ERROR = "agent:error";
    public static final String EVENT_ITERATION_START = "agent:iteration_start";
    public static final String EVENT_ITERATION_END = "agent:iteration_end";

    // Detailed status events for i18n
    public static final String EVENT_STATUS_THINKING_PROCESS = "agent:status:thinking_process";
    public static final String EVENT_STATUS_TOOL_EXECUTING_SINGLE = "agent:status:tool_executing_single";
    public static final String EVENT_STATUS_TOOL_EXECUTING_BATCH = "agent:status:tool_executing_batch";
    
    /**
     * Redis缓存前缀
     */
    public static final String CACHE_PREFIX_AGENT_CONTEXT = "aiagent:context:";
    public static final String CACHE_PREFIX_AGENT_SSE = "aiagent:sse:";

    /**
     * SSE配置
     */
    public static final long SSE_TIMEOUT_MILLIS = -1L;
    public static final int STREAMING_WAIT_TIMEOUT_SECONDS = 30;

    /**
     * 会话默认值
     */
    public static final String DEFAULT_CONVERSATION_TITLE = "新对话";
    public static final String DEFAULT_CONVERSATION_STATUS = "active";
    public static final String ARCHIVED_CONVERSATION_STATUS = "archived";

    /**
     * 常用提示消息
     */
    public static final String MESSAGE_AGENT_START = "zeno agent 启动中";
    public static final String MESSAGE_STREAM_COMPLETE = "流式输出完成";
    public static final String MESSAGE_TASK_COMPLETE = "任务完成";
    public static final String MESSAGE_RAG_QUERYING = "正在检索知识库...";
}

