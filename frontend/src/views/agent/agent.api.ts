/**
 * AI Agent API 封装
 * @author JeecG Team
 * @date 2025-11-30
 */

import { defHttp } from '@/utils/http';
import type {
  AgentRequest,
  AgentEvent,
  AgentEventCallbacks,
  ModelInfo,
  KnowledgeInfo,
  ConversationInfo,
} from './agent.types';

/**
 * API 端点
 */
export enum AgentApi {
  /** 执行 Agent 任务（SSE 流式） */
  execute = '/aiagent/execute',
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
  /** 归档会话 */
  conversationArchive = '/aiagent/conversations/archive',
  /** 会话消息列表 */
  conversationMessages = '/aiagent/conversation/{id}/messages',
  /** 枚举查询 */
  enumMessageRoles = '/aiagent/enums/message-roles',
  enumMessageStatus = '/aiagent/enums/message-status',
  enumConversationStatus = '/aiagent/enums/conversation-status',
}

/**
 * 获取可用模型列表
 */
export async function getAvailableModels(): Promise<ModelInfo[]> {
  try {
    const response = await defHttp.get(
      { url: AgentApi.availableModels },
      { isTransformResponse: false }
    );
    return response.result || [];
  } catch (error) {
    console.error('获取模型列表失败:', error);
    return [];
  }
}

/**
 * 健康检查
 */
export async function checkHealth(): Promise<boolean> {
  try {
    const response = await defHttp.get({ url: AgentApi.health });
    return response.success === true;
  } catch (error) {
    console.error('健康检查失败:', error);
    return false;
  }
}

/**
 * 获取知识库列表
 * 注意：这里复用现有的知识库接口
 */
export async function getKnowledgeList(): Promise<KnowledgeInfo[]> {
  try {
    const response = await defHttp.get(
      { url: '/airag/knowledge/list' },
      { isTransformResponse: false }
    );
    if (response.success && response.result?.records) {
      return response.result.records.map((item: any) => ({
        id: item.id,
        name: item.name,
        description: item.description,
        icon: item.icon,
        documentCount: item.documentCount,
      }));
    }
    return [];
  } catch (error) {
    console.error('获取知识库列表失败:', error);
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

  try {
    const readableStream = await defHttp.post(
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
    console.error('Agent 执行失败:', error);
    
    // 超时错误
    if (error.code === 'ETIMEDOUT' || error.name === 'AbortError') {
      callbacks.onError?.({
        requestId: '',
        error: 'TIMEOUT',
        message: '请求超时，请稍后重试',
      });
    } else {
      callbacks.onError?.({
        requestId: '',
        error: 'NETWORK_ERROR',
        message: error.message || '网络错误，请稍后重试',
      });
    }
  }

  return controller;
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
        console.log('SSE 流结束');
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
            console.log(`[Agent SSE] 收到事件: ${eventType}`, parsedData);
            dispatchEvent(parsedData, callbacks);
          } catch (error) {
            console.error('解析事件数据失败:', error, eventData);
          }
        }
      }
    }
  } catch (error: any) {
    if (error.name === 'AbortError') {
      console.log('请求已取消');
    } else {
      console.error('处理 SSE 流时出错:', error);
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

  console.log(`[Agent] 分发事件: ${eventType}`);

  switch (eventType) {
    case 'agent:start':
      console.log('[Agent] 任务开始');
      callbacks.onStart?.(event);
      break;

    case 'agent:thinking':
      console.log('[Agent] AI 思考中:', event.message);
      callbacks.onThinking?.(event);
      break;

    case 'agent:model_selected':
      console.log('[Agent] 模型已选择:', event.data);
      callbacks.onModelSelected?.(event);
      break;

    case 'agent:rag_retrieve':
      console.log('[Agent] RAG 检索:', event.message);
      callbacks.onRagRetrieve?.(event);
      break;

    case 'agent:tool_call':
      console.log('[Agent] 工具调用:', event.data);
      callbacks.onToolCall?.(event);
      break;

    case 'agent:tool_result':
      console.log('[Agent] 工具结果:', event.data);
      callbacks.onToolResult?.(event);
      break;

    case 'agent:message':
      console.log('[Agent] 流式内容:', event.content?.substring(0, 20));
      callbacks.onMessage?.(event);
      break;

    case 'agent:complete':
      console.log('[Agent] 任务完成');
      callbacks.onComplete?.(event);
      break;

    case 'agent:error':
      console.error('[Agent] 发生错误:', event.message);
      callbacks.onError?.(event);
      break;

    default:
      console.warn('[Agent] 未知的事件类型:', eventType);
  }
}

/**
 * 获取会话列表（分页）
 */
export async function getConversations(pageNo = 1, pageSize = 50, status?: string): Promise<ConversationInfo[]> {
  try {
    const response = await defHttp.get(
      { 
        url: AgentApi.conversations,
        params: { pageNo, pageSize, status }
      },
      { isTransformResponse: false }
    );
    
    if (response.success && response.result?.records) {
      return response.result.records.map((item: any) => ({
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
    }
    return [];
  } catch (error) {
    console.error('获取会话列表失败:', error);
    return [];
  }
}

/**
 * 获取会话消息
 */
export async function getConversationMessages(conversationId: string, limit = 50): Promise<any[]> {
  try {
    const url = AgentApi.conversationMessages.replace('{id}', conversationId);
    const response = await defHttp.get(
      { 
        url,
        params: { limit }
      },
      { isTransformResponse: false }
    );
    
    if (response.success) {
      return response.result || [];
    }
    return [];
  } catch (error) {
    console.error('获取会话消息失败:', error);
    return [];
  }
}

/**
 * 更新会话标题
 */
export async function updateConversationTitle(conversationId: string, title: string): Promise<boolean> {
  try {
    const response = await defHttp.put(
      { 
        url: AgentApi.conversationTitle,
        params: { conversationId, title }
      },
      { joinParamsToUrl: true }
    );
    return response.success === true;
  } catch (error) {
    console.error('更新会话标题失败:', error);
    return false;
  }
}

/**
 * 删除会话
 */
export async function deleteConversation(conversationId: string): Promise<boolean> {
  try {
    const response = await defHttp.delete(
      { 
        url: `${AgentApi.conversation}/${conversationId}`
      }
    );
    return response.success === true;
  } catch (error) {
    console.error('删除会话失败:', error);
    return false;
  }
}

/**
 * 归档会话
 */
export async function archiveConversations(conversationIds: string[]): Promise<boolean> {
  try {
    const response = await defHttp.post(
      { 
        url: AgentApi.conversationArchive,
        params: conversationIds
      }
    );
    return response.success === true;
  } catch (error) {
    console.error('归档会话失败:', error);
    return false;
  }
}

/**
 * 获取消息角色枚举列表
 */
export async function getMessageRoles(): Promise<Array<{ code: string; name: string }>> {
  try {
    const response = await defHttp.get(
      { url: AgentApi.enumMessageRoles },
      { isTransformResponse: false }
    );
    return response.result || [];
  } catch (error) {
    console.error('获取消息角色枚举失败:', error);
    return [];
  }
}

/**
 * 获取消息状态枚举列表
 */
export async function getMessageStatus(): Promise<Array<{ code: string; name: string }>> {
  try {
    const response = await defHttp.get(
      { url: AgentApi.enumMessageStatus },
      { isTransformResponse: false }
    );
    return response.result || [];
  } catch (error) {
    console.error('获取消息状态枚举失败:', error);
    return [];
  }
}

/**
 * 获取对话状态枚举列表
 */
export async function getConversationStatus(): Promise<Array<{ code: string; name: string }>> {
  try {
    const response = await defHttp.get(
      { url: AgentApi.enumConversationStatus },
      { isTransformResponse: false }
    );
    return response.result || [];
  } catch (error) {
    console.error('获取对话状态枚举失败:', error);
    return [];
  }
}

