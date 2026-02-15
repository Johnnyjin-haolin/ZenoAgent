import { apiBaseUrl } from '@/config/env';

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
  
  // 相对路径时拼接基础 URL（见 @/config/env）
  return `${apiBaseUrl}${url.startsWith('/') ? url : '/' + url}`;
}

