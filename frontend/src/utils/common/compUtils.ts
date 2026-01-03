/**
 * 组件工具函数
 */

/**
 * 获取文件访问URL
 */
export function getFileAccessHttpUrl(url?: string): string {
  if (!url) {
    return '';
  }
  
  // 如果是完整URL，直接返回
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }
  
  // 如果是相对路径，拼接基础URL
  const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  return `${baseURL}${url.startsWith('/') ? url : '/' + url}`;
}

