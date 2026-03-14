<template>
  <div class="agent-edit-page">
    <!-- 顶部导航栏 -->
    <div class="edit-header">
      <a-button type="text" class="back-btn" @click="goBack">
        <template #icon><arrow-left-outlined /></template>
        返回列表
      </a-button>
      <div class="header-title">
        <robot-outlined class="header-icon" />
        <span>{{ isEdit ? '编辑 Agent' : '新建 Agent' }}</span>
      </div>
      <div class="header-actions">
        <a-button class="cancel-btn" @click="goBack">取消</a-button>
        <a-button type="primary" class="save-btn" :loading="saving" @click="handleSave">
          保存
        </a-button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="page-loading">
      <a-spin size="large" />
    </div>

    <!-- 表单内容 -->
    <div v-else class="edit-body">

      <!-- ── 基本信息 ────────────────────────────────────────────── -->
      <section class="config-section">
        <div class="section-title">
          <info-circle-outlined class="section-icon" />
          基本信息
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">名称 <span class="required">*</span></label>
            <a-input
              v-model:value="form.name"
              placeholder="输入 Agent 名称"
              :disabled="editingBuiltin"
              class="tech-input"
              :maxlength="50"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">描述</label>
            <a-textarea
              v-model:value="form.description"
              placeholder="描述该 Agent 的用途（可选）"
              :rows="2"
              :disabled="editingBuiltin"
              class="tech-input"
              :maxlength="200"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">系统提示词</label>
            <a-textarea
              v-model:value="form.systemPrompt"
              placeholder="输入系统提示词，定义 Agent 的行为和角色..."
              :rows="8"
              class="tech-input prompt-input"
            />
          </div>
        </div>
      </section>

      <!-- ── 工具配置 ────────────────────────────────────────────── -->
      <section class="config-section">
        <div class="section-title">
          <tool-outlined class="section-icon" />
          工具配置
        </div>

        <div class="form-row two-cols">
          <div class="form-item">
            <label class="form-label">MCP 工具分组</label>
            <a-select
              v-model:value="form.mcpGroups"
              mode="multiple"
              placeholder="选择启用的 MCP 工具分组（留空表示允许全部）"
              :options="mcpGroupOptions"
              allow-clear
              class="tech-select"
              dropdown-class-name="agent-edit-select-dropdown"
            />
          </div>
          <div class="form-item">
            <label class="form-label">系统内置工具</label>
            <a-select
              v-model:value="form.systemTools"
              mode="multiple"
              placeholder="选择启用的系统工具（留空表示允许全部）"
              :options="systemToolOptions"
              allow-clear
              class="tech-select"
              dropdown-class-name="agent-edit-select-dropdown"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">关联知识库</label>
            <a-select
              v-model:value="form.knowledgeIds"
              mode="multiple"
              placeholder="选择关联的知识库（对话时自动检索）"
              :options="knowledgeOptions"
              allow-clear
              class="tech-select"
              dropdown-class-name="agent-edit-select-dropdown"
            />
          </div>
        </div>
      </section>

      <!-- ── 上下文配置 ──────────────────────────────────────────── -->
      <section class="config-section">
        <div class="section-title">
          <setting-outlined class="section-icon" />
          上下文配置
          <span class="section-hint">控制对话历史加载量与工具调用轮数</span>
        </div>

        <div class="form-row two-cols">
          <div class="form-item">
            <label class="form-label">
              历史消息加载条数
              <a-tooltip title="每次对话时从数据库加载的最近历史消息条数，影响上下文记忆深度">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.historyMessageLoadLimit"
              :min="1"
              :max="200"
              placeholder="默认 20"
              class="tech-number"
            />
            <span class="field-hint">默认 20 条，范围 1–200</span>
          </div>
          <div class="form-item">
            <label class="form-label">
              最大工具调用轮数
              <a-tooltip title="单次对话中 LLM 最多可连续调用工具的轮数，防止无限循环">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.maxToolRounds"
              :min="1"
              :max="50"
              placeholder="默认 8"
              class="tech-number"
            />
            <span class="field-hint">默认 8 轮，范围 1–50</span>
          </div>
        </div>
      </section>

      <!-- ── RAG 检索配置 ─────────────────────────────────────────── -->
      <section class="config-section">
        <div class="section-title">
          <database-outlined class="section-icon" />
          RAG 检索配置
          <span class="section-hint">控制知识库检索行为，需配合「关联知识库」使用</span>
        </div>

        <div class="form-row two-cols">
          <div class="form-item">
            <label class="form-label">
              最大检索文档数
              <a-tooltip title="每次检索最多返回的文档块数量">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.ragMaxResults"
              :min="1"
              :max="20"
              placeholder="默认 3"
              class="tech-number"
            />
            <span class="field-hint">默认 3 条，范围 1–20</span>
          </div>
          <div class="form-item">
            <label class="form-label">
              最小相似度分数
              <a-tooltip title="低于此相似度的文档块将被过滤，范围 0–1，值越高越严格">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.ragMinScore"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
              placeholder="默认 0.5"
              class="tech-number"
            />
            <span class="field-hint">默认 0.5，范围 0–1</span>
          </div>
        </div>

        <div class="form-row two-cols">
          <div class="form-item">
            <label class="form-label">
              单文档最大字符数
              <a-tooltip title="单个文档块截断的最大字符数，留空表示不限制">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.ragMaxDocumentLength"
              :min="100"
              placeholder="留空表示不限制"
              class="tech-number"
            />
          </div>
          <div class="form-item">
            <label class="form-label">
              总内容最大字符数
              <a-tooltip title="所有检索文档块合计的最大字符数，留空表示不限制">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.ragMaxTotalContentLength"
              :min="100"
              placeholder="留空表示不限制"
              class="tech-number"
            />
          </div>
        </div>
      </section>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  RobotOutlined,
  ArrowLeftOutlined,
  InfoCircleOutlined,
  ToolOutlined,
  SettingOutlined,
  DatabaseOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons-vue';
import {
  getAgentDefinition,
  createAgentDefinition,
  updateAgentDefinition,
  getAvailableMcpGroupsForAgent,
  getAvailableSystemToolsForAgent,
  getKnowledgeList,
} from '../agent/agent.api';
import type { AgentDefinitionRequest } from '../agent/agent.types';

// ─── 路由 ────────────────────────────────────────────────────────────────────

const route = useRoute();
const router = useRouter();

const isEdit = computed(() => !!route.params.id);
const agentId = computed(() => route.params.id as string | undefined);

// ─── 状态 ────────────────────────────────────────────────────────────────────

const loading = ref(false);
const saving = ref(false);
const editingBuiltin = ref(false);

const mcpGroupOptions = ref<{ label: string; value: string }[]>([]);
const systemToolOptions = ref<{ label: string; value: string }[]>([]);
const knowledgeOptions = ref<{ label: string; value: string }[]>([]);

const form = ref({
  name: '',
  description: '',
  systemPrompt: '',
  mcpGroups: [] as string[],
  systemTools: [] as string[],
  knowledgeIds: [] as string[],
  historyMessageLoadLimit: null as number | null,
  maxToolRounds: null as number | null,
  ragMaxResults: null as number | null,
  ragMinScore: null as number | null,
  ragMaxDocumentLength: null as number | null,
  ragMaxTotalContentLength: null as number | null,
});

// ─── 初始化 ──────────────────────────────────────────────────────────────────

async function loadOptions() {
  const [groups, tools, kbList] = await Promise.all([
    getAvailableMcpGroupsForAgent(),
    getAvailableSystemToolsForAgent(),
    getKnowledgeList(),
  ]);
  mcpGroupOptions.value = groups.map((g) => ({ label: g.name || g.id, value: g.id }));
  systemToolOptions.value = tools.map((t) => ({
    label: `${t.name}${t.description ? ' — ' + t.description : ''}`,
    value: t.name,
  }));
  knowledgeOptions.value = kbList.map((kb) => ({ label: kb.name, value: kb.id }));
}

async function loadAgent() {
  if (!agentId.value) return;
  const agent = await getAgentDefinition(agentId.value);
  if (!agent) {
    message.error('Agent 不存在');
    router.push({ name: 'AgentList' });
    return;
  }
  editingBuiltin.value = agent.builtin;
  form.value = {
    name: agent.name,
    description: agent.description || '',
    systemPrompt: agent.systemPrompt || '',
    mcpGroups: agent.tools?.mcpGroups || [],
    systemTools: agent.tools?.systemTools || [],
    knowledgeIds: agent.tools?.knowledgeIds || [],
    historyMessageLoadLimit: agent.contextConfig?.historyMessageLoadLimit ?? null,
    maxToolRounds: agent.contextConfig?.maxToolRounds ?? null,
    ragMaxResults: agent.ragConfig?.maxResults ?? null,
    ragMinScore: agent.ragConfig?.minScore ?? null,
    ragMaxDocumentLength: agent.ragConfig?.maxDocumentLength ?? null,
    ragMaxTotalContentLength: agent.ragConfig?.maxTotalContentLength ?? null,
  };
}

onMounted(async () => {
  loading.value = true;
  try {
    await Promise.all([loadOptions(), loadAgent()]);
  } catch {
    message.error('加载数据失败');
  } finally {
    loading.value = false;
  }
});

// ─── 保存 ────────────────────────────────────────────────────────────────────

async function handleSave() {
  if (!form.value.name.trim()) {
    message.warning('请输入 Agent 名称');
    return;
  }

  const request: AgentDefinitionRequest = {
    name: form.value.name.trim(),
    description: form.value.description.trim() || undefined,
    systemPrompt: form.value.systemPrompt || undefined,
    tools: {
      mcpGroups: form.value.mcpGroups,
      systemTools: form.value.systemTools,
      knowledgeIds: form.value.knowledgeIds,
    },
  };

  if (form.value.historyMessageLoadLimit != null || form.value.maxToolRounds != null) {
    request.contextConfig = {};
    if (form.value.historyMessageLoadLimit != null) {
      request.contextConfig.historyMessageLoadLimit = form.value.historyMessageLoadLimit;
    }
    if (form.value.maxToolRounds != null) {
      request.contextConfig.maxToolRounds = form.value.maxToolRounds;
    }
  }

  if (
    form.value.ragMaxResults != null ||
    form.value.ragMinScore != null ||
    form.value.ragMaxDocumentLength != null ||
    form.value.ragMaxTotalContentLength != null
  ) {
    request.ragConfig = {};
    if (form.value.ragMaxResults != null) request.ragConfig.maxResults = form.value.ragMaxResults;
    if (form.value.ragMinScore != null) request.ragConfig.minScore = form.value.ragMinScore;
    if (form.value.ragMaxDocumentLength != null) request.ragConfig.maxDocumentLength = form.value.ragMaxDocumentLength;
    if (form.value.ragMaxTotalContentLength != null) request.ragConfig.maxTotalContentLength = form.value.ragMaxTotalContentLength;
  }

  saving.value = true;
  try {
    let result;
    if (isEdit.value && agentId.value) {
      result = await updateAgentDefinition(agentId.value, request);
    } else {
      result = await createAgentDefinition(request);
    }
    if (result) {
      message.success(isEdit.value ? '保存成功' : '创建成功');
      router.push({ name: 'AgentList' });
    } else {
      message.error(isEdit.value ? '保存失败' : '创建失败');
    }
  } finally {
    saving.value = false;
  }
}

// ─── 返回 ────────────────────────────────────────────────────────────────────

function goBack() {
  router.push({ name: 'AgentList' });
}
</script>

<style scoped lang="less">
.agent-edit-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: transparent;
  overflow: hidden;
}

// ─── 顶部导航栏 ───────────────────────────────────────────────────────────────

.edit-header {
  display: flex;
  align-items: center;
  padding: 14px 24px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.15);
  background: rgba(15, 23, 42, 0.8);
  backdrop-filter: blur(8px);
  flex-shrink: 0;
  gap: 16px;
}

.back-btn {
  color: #64748b;
  padding: 0 8px;
  font-size: 13px;
  font-family: 'JetBrains Mono', monospace;

  &:hover {
    color: #60a5fa;
    background: rgba(59, 130, 246, 0.08);
  }
}

.header-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #e2e8f0;
  font-family: 'Inter', sans-serif;
}

.header-icon {
  color: #60a5fa;
  font-size: 18px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.cancel-btn {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  height: 32px;
  padding: 0 16px;
  font-size: 13px;
  border-radius: 5px;

  &:hover {
    border-color: rgba(59, 130, 246, 0.4);
    color: #e2e8f0;
  }
}

.save-btn {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  height: 32px;
  padding: 0 20px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 5px;
  box-shadow: 0 0 10px rgba(59, 130, 246, 0.3);

  &:hover {
    background: linear-gradient(135deg, #60a5fa, #3b82f6);
  }
}

// ─── 加载中 ───────────────────────────────────────────────────────────────────

.page-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

// ─── 主体内容 ─────────────────────────────────────────────────────────────────

.edit-body {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  gap: 24px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
  }
}

// ─── 配置区块 ─────────────────────────────────────────────────────────────────

.config-section {
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(59, 130, 246, 0.12);
  border-radius: 10px;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #cbd5e1;
  font-family: 'Inter', sans-serif;
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.08);
}

.section-icon {
  color: #60a5fa;
  font-size: 15px;
}

.section-hint {
  font-size: 12px;
  color: #475569;
  font-weight: 400;
  font-family: 'JetBrains Mono', monospace;
  margin-left: 4px;
}

// ─── 表单 ─────────────────────────────────────────────────────────────────────

.form-row {
  display: flex;
  gap: 16px;

  &.two-cols > .form-item {
    flex: 1;
    min-width: 0;
  }
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.form-label {
  font-size: 12px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.required {
  color: #f87171;
}

.help-icon {
  color: #475569;
  font-size: 11px;
  cursor: pointer;

  &:hover {
    color: #60a5fa;
  }
}

.field-hint {
  font-size: 11px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
}

// input-number 样式统一在全局 <style lang="less"> 块中处理（.agent-edit-page .tech-number）
.tech-number {
  width: 100%;
}
</style>

<style lang="less">
// ── 组件内部：input / textarea / input-number / select 触发器 ────────────────
.agent-edit-page {
  .tech-input {
    background: rgba(0, 0, 0, 0.2) !important;
    border-color: rgba(59, 130, 246, 0.2) !important;
    color: #e2e8f0 !important;
    font-size: 13px;
    font-family: 'JetBrains Mono', monospace;
    border-radius: 6px;

    &:hover,
    &:focus,
    &:focus-within {
      border-color: #60a5fa !important;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    textarea {
      background: transparent;
      color: #e2e8f0;
    }
  }

  .prompt-input {
    textarea {
      line-height: 1.7;
    }
  }

  // input-number 背景 & 文字颜色
  .tech-number {
    background: rgba(0, 0, 0, 0.2) !important;
    border-color: rgba(59, 130, 246, 0.2) !important;
    border-radius: 6px !important;

    &:hover {
      border-color: #60a5fa !important;
    }

    &.ant-input-number-focused,
    &:focus-within {
      border-color: #60a5fa !important;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
    }

    .ant-input-number-input-wrap,
    .ant-input-number-input {
      background: transparent !important;
      color: #e2e8f0 !important;
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
    }

    // 上下箭头区域
    .ant-input-number-handler-wrap {
      background: rgba(15, 23, 42, 0.8);
      border-left-color: rgba(59, 130, 246, 0.15);

      .ant-input-number-handler {
        border-color: rgba(59, 130, 246, 0.1);

        &:hover {
          background: rgba(59, 130, 246, 0.1);
          .ant-input-number-handler-up-inner,
          .ant-input-number-handler-down-inner {
            color: #60a5fa;
          }
        }

        .ant-input-number-handler-up-inner,
        .ant-input-number-handler-down-inner {
          color: #475569;
        }
      }
    }
  }

  .tech-select {
    width: 100%;

    // 触发器（选择框本身）
    .ant-select-selector {
      background: rgba(0, 0, 0, 0.2) !important;
      border-color: rgba(59, 130, 246, 0.2) !important;
      color: #e2e8f0 !important;
      min-height: 36px;
      border-radius: 6px !important;

      &:hover {
        border-color: #60a5fa !important;
      }
    }

    &.ant-select-focused .ant-select-selector {
      border-color: #60a5fa !important;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
    }

    // 已选 tag
    .ant-select-selection-item {
      background: rgba(59, 130, 246, 0.15) !important;
      border-color: rgba(59, 130, 246, 0.3) !important;
      color: #60a5fa !important;
      font-size: 12px;
      border-radius: 3px;

      .ant-select-selection-item-remove {
        color: rgba(96, 165, 250, 0.6);
        &:hover { color: #60a5fa; }
      }
    }

    .ant-select-selection-placeholder {
      color: rgba(148, 163, 184, 0.35);
      font-size: 12px;
    }

    // 清除按钮 / 下拉箭头
    .ant-select-clear,
    .ant-select-arrow {
      color: #475569;
      background: transparent;
      &:hover { color: #60a5fa; }
    }
  }
}

// ── 全局：Select 下拉弹层（teleport 到 body，无法用组件选择器覆盖）────────────
.agent-edit-select-dropdown {
  background: #0f172a !important;
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
  padding: 4px;

  // 搜索框（多选模式）
  .ant-select-search__field {
    background: transparent !important;
    color: #e2e8f0;
  }

  .ant-select-item {
    color: #94a3b8;
    border-radius: 4px;
    font-size: 13px;
    font-family: 'JetBrains Mono', monospace;
    padding: 7px 10px;

    &:hover,
    &.ant-select-item-option-active {
      background: rgba(59, 130, 246, 0.1) !important;
      color: #e2e8f0;
    }

    &.ant-select-item-option-selected {
      background: rgba(59, 130, 246, 0.18) !important;
      color: #60a5fa !important;
      font-weight: 500;

      .ant-select-item-option-state {
        color: #60a5fa;
      }
    }

    &.ant-select-item-option-disabled {
      color: #334155;
    }
  }

  // 空状态
  .ant-empty-description {
    color: #475569;
    font-size: 12px;
  }
}
</style>
