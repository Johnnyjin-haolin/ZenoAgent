/**
 * Skill 管理 API
 */

import { http } from '@/utils/http';

// ─────────────────────────────────────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────────────────────────────────────

export interface AgentSkill {
  id: string;
  name: string;
  summary: string;
  content: string;
  tags: string[];
  status?: string;
  createTime?: string;
  updateTime?: string;
}

export interface AgentSkillRequest {
  name: string;
  summary: string;
  content: string;
  tags?: string[];
}

export interface SkillTreeNode {
  id: string;
  label: string;
  enabled: boolean;
  skillId?: string;
  children: SkillTreeNode[];
}

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

// ─────────────────────────────────────────────────────────────────────────────
// API 端点
// ─────────────────────────────────────────────────────────────────────────────

const BASE = '/aiagent/skills';

// ─────────────────────────────────────────────────────────────────────────────
// API 函数
// ─────────────────────────────────────────────────────────────────────────────

export async function getSkillList(): Promise<AgentSkill[]> {
  const response = await http.get<ApiResponse<AgentSkill[]>>({ url: BASE });
  return response?.data || [];
}

export async function getSkill(id: string): Promise<AgentSkill> {
  const response = await http.get<ApiResponse<AgentSkill>>({ url: `${BASE}/${id}` });
  if (!response?.data) throw new Error('Skill 不存在');
  return response.data;
}

export async function createSkill(request: AgentSkillRequest): Promise<AgentSkill> {
  const response = await http.post<ApiResponse<AgentSkill>>({ url: BASE, data: request });
  if (!response?.data) throw new Error(response?.message || '创建失败');
  return response.data;
}

export async function updateSkill(id: string, request: AgentSkillRequest): Promise<AgentSkill> {
  const response = await http.put<ApiResponse<AgentSkill>>({ url: `${BASE}/${id}`, data: request });
  if (!response?.data) throw new Error(response?.message || '更新失败');
  return response.data;
}

export async function deleteSkill(id: string): Promise<void> {
  await http.delete<ApiResponse<void>>({ url: `${BASE}/${id}` });
}
