/**
 * 知识库管理API
 */

import { http } from '@/utils/http';
import type {
  KnowledgeBase,
  KnowledgeBaseStats,
  Document,
  CreateKnowledgeBaseRequest,
  UpdateKnowledgeBaseRequest,
  CreateTextDocumentRequest,
  ApiResponse,
} from '@/types/knowledge-base.types';

/**
 * API端点
 */
const API_BASE = '/api';

/**
 * 知识库API端点
 */
export const KnowledgeBaseApi = {
  /** 知识库列表 */
  list: `${API_BASE}/knowledge-bases`,
  /** 知识库详情 */
  detail: (id: string) => `${API_BASE}/knowledge-bases/${id}`,
  /** 创建知识库 */
  create: `${API_BASE}/knowledge-bases`,
  /** 更新知识库 */
  update: (id: string) => `${API_BASE}/knowledge-bases/${id}`,
  /** 删除知识库 */
  delete: (id: string) => `${API_BASE}/knowledge-bases/${id}`,
  /** 统计信息 */
  stats: (id: string) => `${API_BASE}/knowledge-bases/${id}/stats`,
};

/**
 * 文档API端点
 */
export const DocumentApi = {
  /** 上传文档 */
  upload: `${API_BASE}/documents/upload`,
  /** ZIP导入 */
  importZip: `${API_BASE}/documents/import-zip`,
  /** 创建文本文档 */
  createText: `${API_BASE}/documents/text`,
  /** 文档列表 */
  list: (knowledgeBaseId: string) => `${API_BASE}/documents/knowledge-base/${knowledgeBaseId}`,
  /** 重建文档 */
  rebuild: (docId: string) => `${API_BASE}/documents/${docId}/rebuild`,
  /** 删除文档 */
  delete: (docId: string) => `${API_BASE}/documents/${docId}`,
};

/**
 * 获取知识库列表
 */
export async function getKnowledgeBaseList(): Promise<KnowledgeBase[]> {
  try {
    const response = await http.get<ApiResponse<KnowledgeBase[]>>({
      url: KnowledgeBaseApi.list,
    });
    return response?.data || [];
  } catch (error) {
    console.error('获取知识库列表失败:', error);
    throw error;
  }
}

/**
 * 获取知识库详情
 */
export async function getKnowledgeBase(id: string): Promise<KnowledgeBase> {
  try {
    const response = await http.get<ApiResponse<KnowledgeBase>>({
      url: KnowledgeBaseApi.detail(id),
    });
    if (!response?.data) {
      throw new Error('知识库不存在');
    }
    return response.data;
  } catch (error) {
    console.error('获取知识库详情失败:', error);
    throw error;
  }
}

/**
 * 创建知识库
 */
export async function createKnowledgeBase(
  request: CreateKnowledgeBaseRequest
): Promise<KnowledgeBase> {
  try {
    const response = await http.post<ApiResponse<KnowledgeBase>>({
      url: KnowledgeBaseApi.create,
      data: request,
    });
    if (!response?.data) {
      throw new Error(response?.message || '创建失败');
    }
    return response.data;
  } catch (error) {
    console.error('创建知识库失败:', error);
    throw error;
  }
}

/**
 * 更新知识库
 */
export async function updateKnowledgeBase(
  id: string,
  request: UpdateKnowledgeBaseRequest
): Promise<KnowledgeBase> {
  try {
    const response = await http.put<ApiResponse<KnowledgeBase>>({
      url: KnowledgeBaseApi.update(id),
      data: request,
    });
    if (!response?.data) {
      throw new Error(response?.message || '更新失败');
    }
    return response.data;
  } catch (error) {
    console.error('更新知识库失败:', error);
    throw error;
  }
}

/**
 * 删除知识库
 */
export async function deleteKnowledgeBase(id: string): Promise<void> {
  try {
    await http.delete<ApiResponse<void>>({
      url: KnowledgeBaseApi.delete(id),
    });
  } catch (error) {
    console.error('删除知识库失败:', error);
    throw error;
  }
}

/**
 * 获取知识库统计信息
 */
export async function getKnowledgeBaseStats(id: string): Promise<KnowledgeBaseStats> {
  try {
    const response = await http.get<ApiResponse<KnowledgeBaseStats>>({
      url: KnowledgeBaseApi.stats(id),
    });
    if (!response?.data) {
      throw new Error('获取统计信息失败');
    }
    return response.data;
  } catch (error) {
    console.error('获取统计信息失败:', error);
    throw error;
  }
}

/**
 * 文档列表分页响应
 */
export interface DocumentPageResponse {
  records: Document[];
  total: number;
  pageNo: number;
  pageSize: number;
  pages: number;
}

/**
 * 获取文档列表（分页）
 */
export async function getDocumentList(
  knowledgeBaseId: string,
  params?: {
    pageNo?: number;
    pageSize?: number;
    keyword?: string;
    status?: string;
    type?: string;
    orderBy?: string;
    orderDirection?: 'ASC' | 'DESC';
  }
): Promise<DocumentPageResponse> {
  try {
    const response = await http.get<ApiResponse<DocumentPageResponse>>({
      url: DocumentApi.list(knowledgeBaseId),
      params: {
        pageNo: params?.pageNo || 1,
        pageSize: params?.pageSize || 10,
        keyword: params?.keyword,
        status: params?.status,
        type: params?.type,
        orderBy: params?.orderBy,
        orderDirection: params?.orderDirection || 'DESC',
      },
    });
    return response?.data || { records: [], total: 0, pageNo: 1, pageSize: 10, pages: 0 };
  } catch (error) {
    console.error('获取文档列表失败:', error);
    throw error;
  }
}

/**
 * 上传文档
 */
export async function uploadDocument(
  knowledgeBaseId: string,
  file: File
): Promise<Document> {
  try {
    const formData = new FormData();
    formData.append('knowledgeBaseId', knowledgeBaseId);
    formData.append('file', file);

    const response = await http.post<ApiResponse<Document>>({
      url: DocumentApi.upload,
      data: formData,
      // 不设置 Content-Type，让 axios 自动处理 FormData（包含 boundary）
    });
    if (!response?.data) {
      throw new Error(response?.message || '上传失败');
    }
    return response.data;
  } catch (error) {
    console.error('上传文档失败:', error);
    throw error;
  }
}

/**
 * ZIP批量导入
 */
export async function importDocumentsFromZip(
  knowledgeBaseId: string,
  zipFile: File
): Promise<Document[]> {
  try {
    const formData = new FormData();
    formData.append('knowledgeBaseId', knowledgeBaseId);
    formData.append('file', zipFile);

    const response = await http.post<ApiResponse<Document[]>>({
      url: DocumentApi.importZip,
      data: formData,
      // 不设置 Content-Type，让 axios 自动处理 FormData（包含 boundary）
    });
    return response?.data || [];
  } catch (error) {
    console.error('ZIP导入失败:', error);
    throw error;
  }
}

/**
 * 创建文本文档
 */
export async function createTextDocument(
  knowledgeBaseId: string,
  request: CreateTextDocumentRequest
): Promise<Document> {
  try {
    const response = await http.post<ApiResponse<Document>>({
      url: DocumentApi.createText,
      params: { knowledgeBaseId },
      data: request,
    });
    if (!response?.data) {
      throw new Error(response?.message || '创建失败');
    }
    return response.data;
  } catch (error) {
    console.error('创建文本文档失败:', error);
    throw error;
  }
}

/**
 * 重建文档向量
 */
export async function rebuildDocument(docId: string): Promise<void> {
  try {
    await http.put<ApiResponse<void>>({
      url: DocumentApi.rebuild(docId),
    });
  } catch (error) {
    console.error('重建文档失败:', error);
    throw error;
  }
}

/**
 * 删除文档
 */
export async function deleteDocument(docId: string): Promise<void> {
  try {
    await http.delete<ApiResponse<void>>({
      url: DocumentApi.delete(docId),
    });
  } catch (error) {
    console.error('删除文档失败:', error);
    throw error;
  }
}

