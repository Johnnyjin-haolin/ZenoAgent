/**
 * MCP 密钥本地存储工具
 *
 * 用途：管理 PERSONAL MCP 服务器的 API 密钥，
 * 密钥仅存储在浏览器 localStorage，不上传到服务端。
 *
 * 存储 Key 格式：mcp_secret_<serverId>_<headerName>
 * 例如：mcp_secret_abc123_Authorization
 *
 * 安全说明：
 * - localStorage 中的数据不会跨设备同步
 * - PERSONAL MCP 的 endpointUrl / authHeaders（键名）存云端，密钥值存本地
 * - 无需认证的 PERSONAL MCP（authHeaders 为空）无需配置密钥，直接允许执行
 */

const STORAGE_KEY_PREFIX = 'mcp_secret_';

function storageKey(serverId: string, headerName: string): string {
  return `${STORAGE_KEY_PREFIX}${serverId}_${headerName}`;
}

/**
 * 存储指定服务器某个 Header 的密钥值
 */
export function setMcpSecret(serverId: string, headerName: string, secret: string): void {
  if (!serverId || !headerName || !secret) return;
  localStorage.setItem(storageKey(serverId, headerName), secret);
}

/**
 * 获取指定服务器某个 Header 的密钥值
 * @returns 密钥字符串，未配置则返回 null
 */
export function getMcpSecret(serverId: string, headerName: string): string | null {
  if (!serverId || !headerName) return null;
  return localStorage.getItem(storageKey(serverId, headerName));
}

/**
 * 删除指定服务器某个 Header 的密钥
 */
export function removeMcpSecret(serverId: string, headerName: string): void {
  if (!serverId || !headerName) return;
  localStorage.removeItem(storageKey(serverId, headerName));
}

/**
 * 删除指定服务器所有 Header 的密钥
 */
export function removeMcpServerSecrets(serverId: string): void {
  const keysToRemove: string[] = [];
  const prefix = `${STORAGE_KEY_PREFIX}${serverId}_`;
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key?.startsWith(prefix)) {
      keysToRemove.push(key);
    }
  }
  keysToRemove.forEach((k) => localStorage.removeItem(k));
}

/**
 * 检查指定服务器是否所有需要认证的 Header 都已配置密钥
 *
 * @param serverId     服务器 ID
 * @param authHeaders  认证 Header 键值对（来自 McpServerInfo.authHeaders）
 *                     Key 为 Header 名，Value 为 ""（PERSONAL 类型）
 *                     为 null/undefined/空对象 表示无需认证
 * @returns true = 可以直接执行；false = 需要用户补充密钥
 */
export function canExecutePersonalMcp(
  serverId: string,
  authHeaders?: Record<string, string> | null
): boolean {
  if (!authHeaders || Object.keys(authHeaders).length === 0) return true;
  // 所有 Header 都有对应密钥才允许
  return Object.keys(authHeaders).every((headerName) => {
    const secret = getMcpSecret(serverId, headerName);
    return secret !== null && secret.trim() !== '';
  });
}

/**
 * 构建请求头 Map（用于调用 PERSONAL MCP 时携带认证信息）
 *
 * @param serverId    服务器 ID
 * @param authHeaders 认证 Header 键值对（来自 McpServerInfo.authHeaders）
 * @returns 请求头 Map，若无需认证或部分未配置则只返回已配置的项
 */
export function buildPersonalMcpHeaders(
  serverId: string,
  authHeaders?: Record<string, string> | null
): Record<string, string> {
  if (!authHeaders || Object.keys(authHeaders).length === 0) return {};
  const result: Record<string, string> = {};
  for (const headerName of Object.keys(authHeaders)) {
    const secret = getMcpSecret(serverId, headerName);
    if (secret && secret.trim()) {
      // Authorization 系列：如果用户已经写了 "Bearer xxx" 就直接用，否则补前缀
      result[headerName] = secret.startsWith('Bearer ') ? secret : `Bearer ${secret}`;
    }
  }
  return result;
}

/**
 * 获取指定服务器所有未配置密钥的 Header 名列表
 * 用于判断缺少哪些密钥，提示用户补充
 */
export function getMissingSecretHeaders(
  serverId: string,
  authHeaders?: Record<string, string> | null
): string[] {
  if (!authHeaders || Object.keys(authHeaders).length === 0) return [];
  return Object.keys(authHeaders).filter((headerName) => {
    const secret = getMcpSecret(serverId, headerName);
    return secret === null || secret.trim() === '';
  });
}

/**
 * 获取所有已配置密钥的 serverId + headerName 列表
 */
export function listConfiguredSecrets(): Array<{ serverId: string; headerName: string }> {
  const results: Array<{ serverId: string; headerName: string }> = [];
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key?.startsWith(STORAGE_KEY_PREFIX)) {
      const rest = key.slice(STORAGE_KEY_PREFIX.length);
      const underscoreIdx = rest.indexOf('_');
      if (underscoreIdx > 0) {
        results.push({
          serverId: rest.slice(0, underscoreIdx),
          headerName: rest.slice(underscoreIdx + 1),
        });
      }
    }
  }
  return results;
}

/**
 * 清除所有 PERSONAL MCP 密钥（慎用）
 */
export function clearAllMcpSecrets(): void {
  const keysToRemove: string[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key?.startsWith(STORAGE_KEY_PREFIX)) {
      keysToRemove.push(key);
    }
  }
  keysToRemove.forEach((k) => localStorage.removeItem(k));
}
