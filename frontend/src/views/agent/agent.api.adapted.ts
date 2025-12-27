/**
 * AI Agent API 封装（适配版本）
 * 已调整为使用独立的 HTTP 工具，适配新的项目结构
 * 
 * @author AI Agent Team
 * @date 2025-12-06
 */

import { http } from '@/utils/http';
import type {
  AgentRequest,
  AgentEvent,
  AgentEventCallbacks,
  ModelInfo,
  KnowledgeInfo,
  ConversationInfo,
  McpGroupInfo,
  McpToolInfo,
} from './agent.types';

/**
 * API 端点
 * 注意：这些路径需要与后端 AgentController 中的路径匹配
 */
export enum AgentApi {
  /** 执行 Agent 任务（SSE 流式） */
  execute = '/aiagent/execute',
  /** 获取可用模型列表（后端未实现，返回空数组） */
  availableModels = '/aiagent/models/available',
  /** 健康检查 */
  health = '/aiagent/health',
  /** 会话列表（后端未实现，返回空数组） */
  conversations = '/aiagent/conversations',
  /** 会话详情/删除（后端未实现） */
  conversation = '/aiagent/conversation',
  /** 更新会话标题（后端未实现） */
  conversationTitle = '/aiagent/conversation/title',
  /** 归档会话（后端未实现） */
  conversationArchive = '/aiagent/conversations/archive',
  /** 会话消息列表（后端未实现） */
  conversationMessages = '/aiagent/conversation/{id}/messages',
  /** 枚举查询（后端未实现） */
  enumMessageRoles = '/aiagent/enums/message-roles',
  enumMessageStatus = '/aiagent/enums/message-status',
  enumConversationStatus = '/aiagent/enums/conversation-status',
  /** MCP分组列表 */
  mcpGroups = '/aiagent/mcp/groups',
  /** MCP分组详情 */
  mcpGroup = '/aiagent/mcp/groups/{id}',
  /** MCP工具列表 */
  mcpTools = '/aiagent/mcp/tools',
}

/**
 * 获取可用模型列表
 * 注意：后端当前未实现此接口，返回默认模型列表
 */
export async function getAvailableModels(): Promise<ModelInfo[]> {
  try {
    // TODO: 后端实现此接口后，取消注释
    // const response = await http.get(
    //   { url: AgentApi.availableModels },
    //   { isTransformResponse: false }
    // );
    // return response.result || [];
    
    // 临时返回默认模型列表
    return [
      {
        id: 'gpt-4o-mini',
        displayName: 'GPT-4o Mini',
        description: '快速且经济的模型',
        icon: '🤖',
        sort: 1,
        isDefault: true,
      },
      {
        id: 'gpt-4o',
        displayName: 'GPT-4o',
        description: '最强大的模型',
        icon: '🚀',
        sort: 2,
        isDefault: false,
      },
    ];
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
    const response = await http.get({ url: AgentApi.health });
    return response.status === 'ok' || response.success === true;
  } catch (error) {
    console.error('健康检查失败:', error);
    return false;
  }
}

/**
 * 获取知识库列表
 * 注意：后端当前未实现此接口，返回空数组
 */
export async function getKnowledgeList(): Promise<KnowledgeInfo[]> {
  try {
    // TODO: 后端实现RAG功能后，实现此接口
    // const response = await http.get(
    //   { url: '/airag/knowledge/list' },
    //   { isTransformResponse: false }
    // );
    // if (response.success && response.result?.records) {
    //   return response.result.records.map((item: any) => ({
    //     id: item.id,
    //     name: item.name,
    //     description: item.description,
    //     icon: item.icon,
    //     documentCount: item.documentCount,
    //   }));
    // }
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
    const readableStream = await http.post(
      {
        url: AgentApi.execute,
        data: request,
        adapter: 'fetch',
        responseType: 'stream',
        timeout: 5 * 60 * 1000, // 5 分钟超时
        signal: controller.signal,
      },
      {
        isTransformResponse: false,
      }
    ) as unknown as ReadableStream<Uint8Array>;

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
  readableStream: ReadableStream<Uint8Array>,
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
 * 注意：后端当前未实现此接口，返回空数组
 */
export async function getConversations(
  pageNo = 1,
  pageSize = 50,
  status?: string
): Promise<ConversationInfo[]> {
  try {
    // TODO: 后端实现会话管理后，实现此接口
    // const response = await http.get(
    //   { 
    //     url: AgentApi.conversations,
    //     params: { pageNo, pageSize, status }
    //   },
    //   { isTransformResponse: false }
    // );
    // 
    // if (response.success && response.result?.records) {
    //   return response.result.records.map((item: any) => ({
    //     id: item.id,
    //     title: item.title,
    //     isEdit: false,
    //     disabled: false,
    //     messageCount: item.messageCount,
    //     status: item.status,
    //     modelId: item.modelId,
    //     modelName: item.modelName,
    //     createTime: item.createTime,
    //     updateTime: item.updateTime,
    //   }));
    // }
    return [];
  } catch (error) {
    console.error('获取会话列表失败:', error);
    return [];
  }
}

/**
 * 获取会话消息
 * 注意：后端当前未实现此接口，返回空数组
 */
export async function getConversationMessages(
  conversationId: string,
  limit = 50
): Promise<any[]> {
  try {
    // TODO: 后端实现会话消息查询后，实现此接口
    // const url = AgentApi.conversationMessages.replace('{id}', conversationId);
    // const response = await http.get(
    //   { 
    //     url,
    //     params: { limit }
    //   },
    //   { isTransformResponse: false }
    // );
    // 
    // if (response.success) {
    //   return response.result || [];
    // }
    return [];
  } catch (error) {
    console.error('获取会话消息失败:', error);
    return [];
  }
}

/**
 * 更新会话标题
 * 注意：后端当前未实现此接口
 */
export async function updateConversationTitle(
  conversationId: string,
  title: string
): Promise<boolean> {
  try {
    // TODO: 后端实现会话管理后，实现此接口
    // const response = await http.put(
    //   { 
    //     url: AgentApi.conversationTitle,
    //     params: { conversationId, title }
    //   },
    //   { joinParamsToUrl: true }
    // );
    // return response.success === true;
    return false;
  } catch (error) {
    console.error('更新会话标题失败:', error);
    return false;
  }
}

/**
 * 删除会话
 * 注意：后端当前未实现此接口
 */
export async function deleteConversation(conversationId: string): Promise<boolean> {
  try {
    // TODO: 后端实现会话管理后，实现此接口
    // const response = await http.delete(
    //   { 
    //     url: `${AgentApi.conversation}/${conversationId}`
    //   }
    // );
    // return response.success === true;
    return false;
  } catch (error) {
    console.error('删除会话失败:', error);
    return false;
  }
}

/**
 * 归档会话
 * 注意：后端当前未实现此接口
 */
export async function archiveConversations(conversationIds: string[]): Promise<boolean> {
  try {
    // TODO: 后端实现会话管理后，实现此接口
    // const response = await http.post(
    //   { 
    //     url: AgentApi.conversationArchive,
    //     params: conversationIds
    //   }
    // );
    // return response.success === true;
    return false;
  } catch (error) {
    console.error('归档会话失败:', error);
    return false;
  }
}

/**
 * 获取消息角色枚举列表
 * 注意：后端当前未实现此接口，返回默认枚举
 */
export async function getMessageRoles(): Promise<Array<{ code: string; name: string }>> {
  try {
    // TODO: 后端实现枚举接口后，实现此接口
    // const response = await http.get(
    //   { url: AgentApi.enumMessageRoles },
    //   { isTransformResponse: false }
    // );
    // return response.result || [];
    
    // 临时返回默认枚举
    return [
      { code: 'user', name: '用户' },
      { code: 'assistant', name: '助手' },
      { code: 'system', name: '系统' },
    ];
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
    // TODO: 后端实现枚举接口后，实现此接口
    return [
      { code: 'success', name: '成功' },
      { code: 'error', name: '错误' },
      { code: 'processing', name: '处理中' },
    ];
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
    // TODO: 后端实现枚举接口后，实现此接口
    return [
      { code: 'active', name: '活跃' },
      { code: 'archived', name: '已归档' },
    ];
  } catch (error) {
    console.error('获取对话状态枚举失败:', error);
    return [];
  }
}

/**
 * 获取MCP分组列表
 */
export async function getMcpGroups(): Promise<McpGroupInfo[]> {
  try {
    const response = await http.get({ url: AgentApi.mcpGroups });
    return response.result || [];
  } catch (error) {
    console.error('获取MCP分组列表失败:', error);
    return [];
  }
}

/**
 * 获取MCP分组详情
 */
export async function getMcpGroup(groupId: string): Promise<McpGroupInfo | null> {
  try {
    const url = AgentApi.mcpGroup.replace('{id}', groupId);
    const response = await http.get({ url });
    if (response.success && response.result) {
      return response.result;
    }
    return null;
  } catch (error) {
    console.error('获取MCP分组详情失败:', error);
    return null;
  }
}

/**
 * 获取MCP工具列表
 * @param groups 分组列表（可选，为空则返回所有启用的工具）
 */
export async function getMcpTools(groups?: string[]): Promise<McpToolInfo[]> {
  try {
    const params: any = {};
    if (groups && groups.length > 0) {
      // 如果传递了分组列表，需要将数组转换为查询参数
      // 注意：这里可能需要根据实际的http工具调整参数传递方式
      params.groups = groups;
    }
    const response = await http.get({ 
      url: AgentApi.mcpTools,
      params: groups && groups.length > 0 ? { groups: groups.join(',') } : undefined,
    });
    return response.result || [];
  } catch (error) {
    console.error('获取MCP工具列表失败:', error);
    return [];
  }
}


