/**
 * HTTP 请求工具
 * 替代原项目中的 defHttp，提供独立的 HTTP 请求封装
 */

import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import logger from './logger';

// API 基础 URL（从环境变量读取，默认为后端地址）
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

/**
 * 创建 Axios 实例
 */
const httpInstance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * 请求拦截器
 */
httpInstance.interceptors.request.use(
  (config) => {
    // 如果是 FormData，删除默认的 Content-Type，让 axios 自动设置（包含 boundary）
    if (config.data instanceof FormData) {
      // 删除默认的 Content-Type，让 axios 自动设置 multipart/form-data 和 boundary
      if (config.headers) {
        delete (config.headers as any)['Content-Type'];
      }
    }
    // 可以在这里添加 token 等认证信息
    // const token = localStorage.getItem('token');
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器
 */
httpInstance.interceptors.response.use(
  (response: AxiosResponse) => {
    // 统一处理响应数据
    return response;
  },
  (error) => {
    // 统一错误处理
    logger.error('HTTP请求错误:', error);
    return Promise.reject(error);
  }
);

/**
 * HTTP 工具类
 * 提供类似 defHttp 的接口，方便迁移
 */
export const http = {
  /**
   * GET 请求
   */
  get<T = any>(
    config: { url: string; params?: any },
    options?: { isTransformResponse?: boolean }
  ): Promise<T> {
    return httpInstance.get(config.url, { params: config.params }).then((response) => {
      if (options?.isTransformResponse === false) {
        return response.data as T;
      }
      // 默认转换响应格式（兼容原项目格式）
      if (response.data && typeof response.data === 'object' && 'result' in response.data) {
        return response.data as T;
      }
      return response.data as T;
    });
  },

  /**
   * POST 请求
   */
  post<T = any>(
    config: {
      url: string;
      params?: any;
      data?: any;
      adapter?: string;
      responseType?: string;
      timeout?: number;
      signal?: AbortSignal;
      headers?: Record<string, string>;
    },
    options?: { isTransformResponse?: boolean }
  ): Promise<T> {
    const { adapter, responseType, signal, headers, ...restConfig } = config;
    
    // 处理 SSE 流式响应
    if (adapter === 'fetch' || responseType === 'stream') {
      return handleStreamRequest(config) as Promise<T>;
    }

    // 判断是否是 FormData
    const isFormData = config.data instanceof FormData;
    
    // 如果是 FormData，删除手动设置的 Content-Type，让 axios 自动设置（包含 boundary）
    let requestHeaders = headers;
    if (isFormData && headers) {
      const { 'Content-Type': _, ...otherHeaders } = headers;
      requestHeaders = otherHeaders;
    }

    return httpInstance
      .post(config.url, config.data || config.params, {
        params: config.params,
        headers: requestHeaders,
        signal,
        timeout: config.timeout,
      })
      .then((response) => {
        if (options?.isTransformResponse === false) {
          return response.data as T;
        }
        return response.data as T;
      });
  },

  /**
   * PUT 请求
   */
  put<T = any>(
    config: { url: string; params?: any; data?: any; joinParamsToUrl?: boolean },
    options?: { isTransformResponse?: boolean }
  ): Promise<T> {
    let url = config.url;
    
    // 如果 joinParamsToUrl 为 true，将参数拼接到 URL
    if (config.joinParamsToUrl && config.params) {
      const params = new URLSearchParams(config.params).toString();
      url = `${url}?${params}`;
    }

    return httpInstance.put(url, config.data || config.params).then((response) => {
      if (options?.isTransformResponse === false) {
        return response.data as T;
      }
      return response.data as T;
    });
  },

  /**
   * DELETE 请求
   */
  delete<T = any>(
    config: { url: string; params?: any },
    options?: { isTransformResponse?: boolean }
  ): Promise<T> {
    return httpInstance.delete(config.url, { params: config.params }).then((response) => {
      if (options?.isTransformResponse === false) {
        return response.data as T;
      }
      return response.data as T;
    });
  },
};

/**
 * 处理流式请求（SSE）
 */
function handleStreamRequest(config: {
  url: string;
  params?: any;
  data?: any;
  timeout?: number;
  signal?: AbortSignal;
}): Promise<ReadableStream> {
  const url = `${API_BASE_URL}${config.url}`;
  
  return fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(config.data || config.params),
    signal: config.signal,
  }).then((response) => {
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    if (!response.body) {
      throw new Error('Response body is null');
    }
    return response.body;
  });
}

/**
 * 将在未来版本中移除
 */
export const defHttp = http;

export default http;


