/**
 * 知识库相关类型定义
 */

/**
 * 知识库信息
 */
export interface KnowledgeBase {
  /** 知识库ID */
  id: string;
  /** 知识库名称 */
  name: string;
  /** 描述 */
  description?: string;
  /** 向量模型ID */
  embeddingModelId: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 知识库统计信息
 */
export interface KnowledgeBaseStats {
  /** 知识库ID */
  knowledgeBaseId: string;
  /** 知识库名称 */
  knowledgeBaseName: string;
  /** 总文档数 */
  totalDocuments: number;
  /** 已完成文档数 */
  completedDocuments: number;
  /** 失败文档数 */
  failedDocuments: number;
  /** 处理中文档数 */
  buildingDocuments: number;
}

/**
 * 文档信息
 */
export interface Document {
  /** 文档ID */
  id: string;
  /** 知识库ID */
  knowledgeBaseId: string;
  /** 文档标题 */
  title: string;
  /** 文档类型：FILE（文件）、TEXT（文本）、WEB（网页） */
  type: 'FILE' | 'TEXT' | 'WEB';
  /** 文档内容（可选） */
  content?: string;
  /** 元数据（JSON格式） */
  metadata?: string;
  /** 状态：DRAFT（草稿）、BUILDING（处理中）、COMPLETE（完成）、FAILED（失败） */
  status: 'DRAFT' | 'BUILDING' | 'COMPLETE' | 'FAILED';
  /** 失败原因（当status为FAILED时） */
  failedReason?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 创建知识库请求
 */
export interface CreateKnowledgeBaseRequest {
  /** 名称 */
  name: string;
  /** 描述 */
  description?: string;
  /** 向量模型ID */
  embeddingModelId: string;
}

/**
 * 更新知识库请求
 */
export interface UpdateKnowledgeBaseRequest {
  /** 名称 */
  name?: string;
  /** 描述 */
  description?: string;
}

/**
 * 创建文本文档请求
 */
export interface CreateTextDocumentRequest {
  /** 标题 */
  title: string;
  /** 内容 */
  content: string;
}

/**
 * API响应包装
 */
export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  message?: string;
  errorCode?: string | null;
}

