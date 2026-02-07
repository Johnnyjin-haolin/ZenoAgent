/**
 * AI Agent API 封装（适配版本）
 * 已调整为使用独立的 HTTP 工具，适配新的项目结构
 * 
 * @author AI Agent Team
 * @date 2025-12-06
 */

import { http } from '@/utils/http';
import logger from '@/utils/logger';
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
  /** MCP分组列表 */
  mcpGroups = '/aiagent/mcp/groups',
  /** MCP分组详情 */
  mcpGroup = '/aiagent/mcp/groups/{id}',
  /** MCP工具列表 */
  mcpTools = '/aiagent/mcp/tools',
}
/**
 * 获取MCP分组列表
 */
export async function getMcpGroups(): Promise<McpGroupInfo[]> {
  try {
    const response = await http.get({ url: AgentApi.mcpGroups });
    return response.data || [];
  } catch (error) {
    logger.error('获取MCP分组列表失败:', error);
    return [];
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
    return response.data || [];
  } catch (error) {
    logger.error('获取MCP工具列表失败:', error);
    return [];
  }
}


