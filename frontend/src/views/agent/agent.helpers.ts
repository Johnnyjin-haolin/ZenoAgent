/**
 * Agent 工具配置相关辅助函数
 */

import type { McpServerSelection, AgentToolsConfig } from './agent.types';

/** 系统工具组的虚拟 serverId（与 AgentToolConfig.vue 保持一致） */
export const SYSTEM_TOOLS_SERVER_ID = '__system_tools__';

/** 工具元数据：工具名 → serverId */
export type ToolsMetaMap = Record<string, string>;

/**
 * 将 selectedTools（工具名数组）+ toolsMetaMap 转换为后端所需参数格式。
 * 返回 { mcpServers, systemTools }，供 sendMessage / handleSave 使用。
 */
export function buildAgentToolsConfig(
  selectedTools: string[],
  toolsMetaMap: ToolsMetaMap
): { mcpServers: McpServerSelection[] | undefined; systemTools: string[] | undefined } {
  if (selectedTools.length === 0) {
    return { mcpServers: undefined, systemTools: undefined };
  }

  const systemToolNames: string[] = [];
  const mcpToolsByServer: Record<string, string[]> = {};

  for (const toolName of selectedTools) {
    const serverId = toolsMetaMap[toolName];
    if (!serverId) continue;
    if (serverId === SYSTEM_TOOLS_SERVER_ID) {
      systemToolNames.push(toolName);
    } else {
      if (!mcpToolsByServer[serverId]) mcpToolsByServer[serverId] = [];
      mcpToolsByServer[serverId].push(toolName);
    }
  }

  const mcpServers: McpServerSelection[] = Object.entries(mcpToolsByServer).map(
    ([serverId, toolNames]) => ({ serverId, toolNames })
  );

  return {
    mcpServers: mcpServers.length > 0 ? mcpServers : undefined,
    systemTools: systemToolNames.length > 0 ? systemToolNames : undefined,
  };
}

/**
 * 将后端存储的 AgentToolsConfig（mcpServers + systemTools）
 * 反向还原为 selectedTools 数组和 toolsMetaMap。
 *
 * 注意：此函数只能还原工具名列表和元数据映射，
 * 不能还原工具的 description 等详情（需要额外 API 调用）。
 */
export function restoreToolsFromConfig(toolsConfig: AgentToolsConfig | undefined): {
  selectedTools: string[];
  toolsMetaMap: ToolsMetaMap;
} {
  if (!toolsConfig) {
    return { selectedTools: [], toolsMetaMap: {} };
  }

  const selectedTools: string[] = [];
  const toolsMetaMap: ToolsMetaMap = {};

  // 还原系统工具
  for (const toolName of toolsConfig.systemTools ?? []) {
    selectedTools.push(toolName);
    toolsMetaMap[toolName] = SYSTEM_TOOLS_SERVER_ID;
  }

  // 还原 MCP 工具
  for (const serverSel of toolsConfig.mcpServers ?? []) {
    if (!serverSel.toolNames || serverSel.toolNames.length === 0) {
      // toolNames 为 null/空 表示选了整个服务器，但没有具体工具名可还原
      // 此情况在编辑页加载时需要 AgentToolConfig 内部处理
      continue;
    }
    for (const toolName of serverSel.toolNames) {
      selectedTools.push(toolName);
      toolsMetaMap[toolName] = serverSel.serverId;
    }
  }

  return { selectedTools, toolsMetaMap };
}
