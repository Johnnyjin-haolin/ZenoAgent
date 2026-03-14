/**
 * AI Agent API 封装
 * @date 2025-11-30
 */

import { http } from '@/utils/http';
import logger from '@/utils/logger';
import i18n from '@/locales';
import type {
  AgentRequest,
  AgentEvent,
  AgentEventCallbacks,
  ModelInfo,
  KnowledgeInfo,
  ConversationInfo,
  PageResult,
  HealthResponse,
  UserQuestion,
} from './agent.types';
import { ModelType } from '@/types/model.types';

/**
 * API 端点
 */
export enum AgentApi {
  /** 执行 Agent 任务（SSE 流式） */
  execute = '/aiagent/execute',
  /** 停止 Agent 执行 */
  stop = '/aiagent/stop',
  /** 提交用户对 Agent 提问的回答 */
  answer = '/aiagent/answer',
  /** 获取可用模型列表 */
  availableModels = '/aiagent/models/available',
  /** 健康检查 */
  health = '/aiagent/health',
  /** 会话列表 */
  conversations = '/aiagent/conversations',
  /** 会话详情/删除 */
  conversation = '/aiagent/conversation',
  /** 更新会话标题 */
  conversationTitle = '/aiagent/conversation/title',
  /** 会话消息列表 */
  conversationMessages = '/aiagent/conversation/{id}/messages',
  /** 工具执行确认 */
  toolConfirm = '/aiagent/tool/confirm',
}

/**
 * 获取可用模型列表
 * @param type 模型类型筛选（可选），使用 ModelType 枚举，不传则返回所有模型
 */
export async function getAvailableModels(type?: ModelType): Promise<ModelInfo[]> {
  try {
    const params = type ? { type: type as string } : undefined;
    const response = await http.get(
      { url: AgentApi.availableModels, params },
      { isTransformResponse: false }
    );
    return response.data || [];
  } catch (error) {
    logger.error('获取模型列表失败:', error);
    return [];
  }
}

/**
 * 获取 Embedding 模型列表（向量模型）
 */
export async function getEmbeddingModels(): Promise<ModelInfo[]> {
  return getAvailableModels(ModelType.EMBEDDING);
}

/**
 * 健康检查
 */
export async function checkHealth(): Promise<HealthResponse | null> {
  try {
    const response = await http.get(
      { url: AgentApi.health },
      { isTransformResponse: false }
    );
    if (response.success && response.data) {
      return response.data as HealthResponse;
    }
    return null;
  } catch (error) {
    logger.error('健康检查失败:', error);
    return null;
  }
}

/**
 * 获取知识库列表
 * 使用现有的知识库接口 /api/knowledge-bases
 */
export async function getKnowledgeList(): Promise<KnowledgeInfo[]> {
  try {
    const response = await http.get(
      { url: '/api/knowledge-bases' },
      { isTransformResponse: false }
    );
    // 后端返回格式：Result<List<KnowledgeBase>>，即 { success: true, data: [...] }
    if (response.success && response.data && Array.isArray(response.data)) {
      return response.data.map((item: any) => ({
        id: item.id,
        name: item.name,
        description: item.description,
        icon: '📚', // 默认图标
        documentCount: 0, // 默认文档数量（如果需要可以后续调用统计接口获取）
      }));
    }
    return [];
  } catch (error) {
    logger.error('获取知识库列表失败:', error);
    return [];
  }
}

/**
 * 执行 Agent 任务（SSE 流式返回）
 * @param request Agent 请求参数
 * @param callbacks 事件回调函数
 * @returns AbortController 用于取消请求
 */
export async function executeAgent(
  request: AgentRequest,
  callbacks: AgentEventCallbacks
): Promise<AbortController> {
  const controller = new AbortController();
  const t = i18n.global.t;

  try {
    const readableStream = await http.post(
      {
        url: AgentApi.execute,
        params: request,
        adapter: 'fetch',
        responseType: 'stream',
        timeout: 5 * 60 * 1000, // 5 分钟超时
        signal: controller.signal,
      },
      {
        isTransformResponse: false,
      }
    );

    // 处理 SSE 流
    processSSEStream(readableStream, callbacks, controller);
  } catch (error: any) {
    logger.error('Agent 执行失败:', error);
    
    // 超时错误
    if (error.code === 'ETIMEDOUT' || error.name === 'AbortError') {
      callbacks.onError?.({
        requestId: '',
        error: 'TIMEOUT',
        message: t('agent.chat.sendFailed'),
      });
    } else {
      callbacks.onError?.({
        requestId: '',
        error: 'NETWORK_ERROR',
        message: error.message || t('agent.chat.sendFailed'),
      });
    }
  }

  return controller;
}

/**
 * 停止 Agent 执行
 */
export async function stopAgent(requestId: string): Promise<boolean> {
  try {
    const response = await http.post(
      { url: `${AgentApi.stop}/${requestId}` },
      { isTransformResponse: false }
    );
    return response.success === true;
  } catch (error) {
    logger.error('停止 Agent 失败:', error);
    return false;
  }
}

/**
 * 提交用户对 Agent 提问的回答
 * 在收到 agent:ask_user_question 事件后，用户填写回答后调用此函数
 * @param questionId  问题ID（从事件 data.questionId 获取）
 * @param answer      用户的回答内容
 */
export async function submitAnswer(questionId: string, answer: string): Promise<boolean> {
  try {
    const response = await http.post(
      { url: AgentApi.answer, params: { questionId, answer } },
      { isTransformResponse: false }
    );
    return response.success === true;
  } catch (error) {
    logger.error('提交用户回答失败:', error);
    return false;
  }
}

/**
 * 确认/拒绝工具执行（手动模式）
 */
export async function confirmToolExecution(
  toolExecutionId: string,
  approve: boolean,
  requestId?: string
): Promise<boolean> {
  try {
    const response = await http.post(
      {
        url: AgentApi.toolConfirm,
        params: { toolExecutionId, approve, requestId },
      },
      { isTransformResponse: false }
    );
    return response.success === true;
  } catch (error) {
    logger.error('工具确认失败:', error);
    return false;
  }
}

/**
 * 处理 SSE 数据流
 */
async function processSSEStream(
  readableStream: any,
  callbacks: AgentEventCallbacks,
  controller: AbortController
) {
  const reader = readableStream.getReader();
  const decoder = new TextDecoder('UTF-8');
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      
      if (done) {
        logger.debug('SSE 流结束');
        break;
      }

      // 解码数据块
      let chunk = decoder.decode(value, { stream: true });
      chunk = buffer + chunk;

      // 按 SSE 协议分割事件
      // SSE 格式: event: xxx\ndata: {...}\n\n
      const events = chunk.split('\n\n');
      
      // 保存最后一个不完整的事件到 buffer
      buffer = events[events.length - 1];

      // 处理完整的事件
      for (let i = 0; i < events.length - 1; i++) {
        const eventBlock = events[i];
        if (!eventBlock.trim()) continue;

        const lines = eventBlock.split('\n');
        let eventType = '';
        let eventData = '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.replace('event:', '').trim();
          } else if (line.startsWith('data:')) {
            eventData = line.replace('data:', '').trim();
          }
        }

        // 解析并分发事件
        if (eventType && eventData) {
          try {
            const parsedData: AgentEvent = JSON.parse(eventData);
            parsedData.event = eventType as any;
            logger.debug(`[Agent SSE] 收到事件: ${eventType}`, parsedData);
            dispatchEvent(parsedData, callbacks);
          } catch (error) {
            logger.error('解析事件数据失败:', error, eventData);
          }
        }
      }
    }
  } catch (error: any) {
    if (error.name === 'AbortError') {
      logger.debug('请求已取消');
    } else {
      logger.error('处理 SSE 流时出错:', error);
      callbacks.onError?.({
        requestId: '',
        error: 'STREAM_ERROR',
        message: '数据流处理错误',
      });
    }
  } finally {
    reader.releaseLock();
  }
}

/**
 * 分发事件到对应的回调函数
 */
function dispatchEvent(event: AgentEvent, callbacks: AgentEventCallbacks) {
  const { event: eventType } = event;
  const t = i18n.global.t;

  logger.debug(`[Agent] 分发事件: ${eventType}`);

  switch (eventType) {
    case 'agent:start':
      logger.debug('[Agent] 任务开始');
      callbacks.onStart?.(event);
      break;

    case 'agent:iteration_start':
      logger.debug('[Agent] 迭代开始:', event.data?.iterationNumber);
      callbacks.onIterationStart?.(event);
      break;

    case 'agent:thinking':
      logger.debug('[Agent] AI 思考中:', event.message);
      callbacks.onThinking?.(event);
      break;
    case 'agent:thinking_delta':
      logger.debug('[Agent] AI 思考片段:', event.content?.substring(0, 20));
      callbacks.onThinkingDelta?.(event);
      break;

    case 'agent:planning':
      logger.debug('[Agent] 正在规划:', event.message);
      callbacks.onThinking?.({
        ...event,
        message: event.message || t('agent.status.planning'),
        statusText: t('agent.status.planning')
      });
      break;

    case 'agent:tool_executing':
      // tool_executing 已合并到 tool_call 事件，此处保留兼容
      logger.debug('[Agent] 正在执行工具:', event.message);
      break;

    case 'agent:rag_querying':
      logger.debug('[Agent] 正在查询知识库:', event.message);
      callbacks.onThinking?.({
        ...event,
        message: event.message || t('agent.status.rag_querying'),
        statusText: t('agent.status.rag_querying')
      });
      break;

    case 'agent:generating':
      logger.debug('[Agent] 正在生成回复:', event.message);
      callbacks.onThinking?.({
        ...event,
        message: event.message || t('agent.chat.generating'),
        statusText: t('agent.chat.generating')
      });
      break;

    case 'agent:observing':
      logger.debug('[Agent] 正在观察结果:', event.message);
      callbacks.onThinking?.({
        ...event,
        message: event.message || t('agent.status.processing'),
        statusText: t('agent.status.processing')
      });
      break;

    case 'agent:reflecting':
      logger.debug('[Agent] 正在反思:', event.message);
      callbacks.onThinking?.({
        ...event,
        message: event.message || t('agent.chat.thinkingProcess'),
        statusText: t('agent.chat.thinkingProcess')
      });
      break;

    case 'agent:model_selected':
      logger.debug('[Agent] 模型已选择:', event.data);
      callbacks.onModelSelected?.(event);
      break;

    case 'agent:rag_retrieve':
      logger.debug('[Agent] RAG 检索:', event.message);
      callbacks.onRagRetrieve?.(event);
      break;

    case 'agent:ask_user_question': {
      logger.debug('[Agent] 向用户提问:', event.data);
      if (callbacks.onAskUserQuestion && event.data) {
        const question: UserQuestion = {
          questionId: event.data.questionId,
          question: event.data.question,
          questionType: event.data.questionType,
          options: event.data.options,
          previewContent: event.data.previewContent,
        };
        callbacks.onAskUserQuestion(question);
      }
      break;
    }

    case 'agent:tool_call':
      logger.debug('[Agent] 工具调用:', event.data);
      callbacks.onToolCall?.(event);
      break;

    case 'agent:tool_result':
      logger.debug('[Agent] 工具结果:', event.data);
      callbacks.onToolResult?.(event);
      break;

    case 'agent:message':
      logger.debug('[Agent] 流式内容:', event.content?.substring(0, 20));
      callbacks.onMessage?.(event);
      break;

    case 'agent:stream_complete':
      logger.debug('[Agent] 流式输出完成');
      callbacks.onStreamComplete?.(event);
      break;

    case 'agent:iteration_end':
      logger.debug('[Agent] 迭代结束:', event.data?.iterationNumber);
      callbacks.onIterationEnd?.(event);
      break;

    case 'agent:status:analyzing':
    case 'agent:status:thinking_process':
    case 'agent:status:planning':
    case 'agent:status:rag_querying':
    case 'agent:status:tool_executing_single':
    case 'agent:status:tool_executing_batch':
      logger.debug('[Agent] 状态更新:', event.event, event.data);
      callbacks.onStatusUpdate?.(event);
      break;
    case 'agent:status:retrying':
      logger.debug('[Agent] 重试状态:', event.event, event.data);
      callbacks.onStatusUpdate?.(event);
      break;

    case 'agent:complete':
      logger.debug('[Agent] 任务完成');
      callbacks.onComplete?.(event);
      break;

    case 'agent:error':
      logger.error('[Agent] 发生错误:', event.message);
      callbacks.onError?.(event);
      break;

    default:
      logger.warn('[Agent] 未知的事件类型:', eventType);
  }
}

/**
 * 获取会话列表（分页）
 */
export async function getConversations(pageNo = 1, pageSize = 50, status?: string): Promise<PageResult<ConversationInfo>> {
  try {
    const response = await http.get(
      { 
        url: AgentApi.conversations,
        params: { pageNo, pageSize, status }
      },
      { isTransformResponse: false }
    );
    
    if (response.success && response.data) {
      const pageResult = response.data as PageResult<any>;
      // 转换 records 中的每个项，添加前端需要的字段
      const records = pageResult.records.map((item: any) => ({
        id: item.id,
        title: item.title,
        isEdit: false,
        disabled: false,
        messageCount: item.messageCount,
        status: item.status,
        modelId: item.modelId,
        modelName: item.modelName,
        createTime: item.createTime,
        updateTime: item.updateTime,
      }));
      
      return {
        records,
        total: pageResult.total,
        pageNo: pageResult.pageNo,
        pageSize: pageResult.pageSize,
        pages: pageResult.pages,
      };
    }
    
    // 返回空的分页结果
    return {
      records: [],
      total: 0,
      pageNo: pageNo,
      pageSize: pageSize,
      pages: 0,
    };
  } catch (error) {
    logger.error('获取会话列表失败:', error);
    return {
      records: [],
      total: 0,
      pageNo: pageNo,
      pageSize: pageSize,
      pages: 0,
    };
  }
}

/**
 * 获取会话消息
 */
export async function getConversationMessages(conversationId: string, limit = 50): Promise<any[]> {
  try {
    const url = AgentApi.conversationMessages.replace('{id}', conversationId);
    const response = await http.get(
      { 
        url,
        params: { limit }
      },
      { isTransformResponse: false }
    );
    
    if (response.success) {
      return response.data || [];
    }
    return [];
  } catch (error) {
    logger.error('获取会话消息失败:', error);
    return [];
  }
}

/**
 * 更新会话标题
 */
export async function updateConversationTitle(conversationId: string, title: string): Promise<boolean> {
  try {
    const response = await http.put(
      { 
        url: AgentApi.conversationTitle,
        params: { conversationId, title }
      },
      { joinParamsToUrl: true }
    );
    return response.success === true;
  } catch (error) {
    logger.error('更新会话标题失败:', error);
    return false;
  }
}

/**
 * 删除会话
 */
export async function deleteConversation(conversationId: string): Promise<boolean> {
  try {
    const response = await http.delete(
      { 
        url: `${AgentApi.conversation}/${conversationId}`
      }
    );
    return response.success === true;
  } catch (error) {
    logger.error('删除会话失败:', error);
    return false;
  }
}
