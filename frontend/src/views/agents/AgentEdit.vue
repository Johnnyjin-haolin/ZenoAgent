<template>
  <div class="agent-edit-page">
    <!-- 顶部导航栏 -->
    <div class="edit-header">
      <a-button type="text" class="back-btn" @click="goBack">
        <template #icon><arrow-left-outlined /></template>
        {{ t('agentEdit.backToList') }}
      </a-button>
      <div class="header-title">
        <robot-outlined class="header-icon" />
        <span>{{ isEdit ? t('agentEdit.editTitle') : t('agentEdit.createTitle') }}</span>
      </div>
      <div class="header-actions">
        <a-button class="cancel-btn" @click="goBack">{{ t('agentEdit.cancel') }}</a-button>
        <a-button type="primary" class="save-btn" :loading="saving" @click="handleSave">
          {{ t('agentEdit.save') }}
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
          {{ t('agentEdit.basicInfo') }}
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">{{ t('agentEdit.nameLabel') }} <span class="required">*</span></label>
            <a-input
              v-model:value="form.name"
              :placeholder="t('agentEdit.namePlaceholder')"
              :disabled="editingBuiltin"
              class="tech-input"
              :maxlength="50"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">{{ t('agentEdit.descLabel') }}</label>
            <a-textarea
              v-model:value="form.description"
              :placeholder="t('agentEdit.descPlaceholder')"
              :rows="2"
              :disabled="editingBuiltin"
              class="tech-input"
              :maxlength="200"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-item">
            <label class="form-label">{{ t('agentEdit.systemPromptLabel') }}</label>
            <a-textarea
              v-model:value="form.systemPrompt"
              :placeholder="t('agentEdit.systemPromptPlaceholder')"
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
          {{ t('agentEdit.toolConfig') }}
        </div>

        <!-- GLOBAL MCP 服务器（服务端执行） -->
        <div class="form-row">
          <div class="form-item">
            <label class="form-label">
              🌐 GLOBAL MCP 服务器
              <a-tooltip title="由服务端直接调用，适合统一管理的工具。在 MCP 服务器管理页面中维护。">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-select
              v-model:value="form.serverMcpIds"
              mode="multiple"
              placeholder="选择要绑定的 GLOBAL MCP 服务器（可多选）"
              :options="globalMcpOptions"
              allow-clear
              class="tech-select"
              dropdown-class-name="agent-edit-select-dropdown"
              :loading="mcpLoading"
            />
            <span class="field-hint">
              已绑定 {{ form.serverMcpIds.length }} 个服务端 MCP 服务器
              <router-link to="/mcp" target="_blank" class="manage-link">管理 MCP 服务器 →</router-link>
            </span>
          </div>
        </div>

        <!-- PERSONAL MCP 能力标签（客户端执行） -->
        <div class="form-row">
          <div class="form-item">
            <label class="form-label">
              👤 PERSONAL MCP 能力需求
              <a-tooltip title="声明此 Agent 需要哪些个人 MCP 能力。运行时由用户浏览器本地调用，适合需要个人认证的工具（如 GitHub、Notion）。">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-select
              v-model:value="form.personalMcpCapabilities"
              mode="multiple"
              placeholder="选择所需的 PERSONAL MCP 能力标签（如 github、notion）"
              :options="personalMcpOptions"
              allow-clear
              class="tech-select"
              dropdown-class-name="agent-edit-select-dropdown"
              :loading="mcpLoading"
            />
            <span class="field-hint">
              当 Agent 执行时，具有对应能力标签的用户本地 PERSONAL MCP 将被自动匹配使用
            </span>
          </div>
        </div>

        <!-- 系统内置工具 -->
        <div class="form-row two-cols">
          <div class="form-item">
            <label class="form-label">{{ t('agentEdit.systemTools') }}</label>
            <a-select
              v-model:value="form.systemTools"
              mode="multiple"
              :placeholder="t('agentEdit.systemToolsPlaceholder')"
              :options="systemToolOptions"
              allow-clear
              class="tech-select"
              dropdown-class-name="agent-edit-select-dropdown"
            />
          </div>
          <div class="form-item">
            <label class="form-label">{{ t('agentEdit.knowledgeBases') }}</label>
            <a-select
              v-model:value="form.knowledgeIds"
              mode="multiple"
              :placeholder="t('agentEdit.knowledgeBasesPlaceholder')"
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
          {{ t('agentEdit.contextConfig') }}
          <span class="section-hint">{{ t('agentEdit.contextHint') }}</span>
        </div>

        <div class="form-row two-cols">
          <div class="form-item">
            <label class="form-label">
              {{ t('agentEdit.historyLimit') }}
              <a-tooltip :title="t('agentEdit.historyLimitTooltip')">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.historyMessageLoadLimit"
              :min="1"
              :max="200"
              :placeholder="t('agentEdit.historyLimitDefault')"
              class="tech-number"
            />
            <span class="field-hint">{{ t('agentEdit.historyLimitHint') }}</span>
          </div>
          <div class="form-item">
            <label class="form-label">
              {{ t('agentEdit.maxToolRounds') }}
              <a-tooltip :title="t('agentEdit.maxToolRoundsTooltip')">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.maxToolRounds"
              :min="1"
              :max="50"
              :placeholder="t('agentEdit.maxToolRoundsDefault')"
              class="tech-number"
            />
            <span class="field-hint">{{ t('agentEdit.maxToolRoundsHint') }}</span>
          </div>
        </div>
      </section>

      <!-- ── RAG 检索配置 ─────────────────────────────────────────── -->
      <section class="config-section">
        <div class="section-title">
          <database-outlined class="section-icon" />
          {{ t('agentEdit.ragConfig') }}
          <span class="section-hint">{{ t('agentEdit.ragHint') }}</span>
        </div>

        <div class="form-row two-cols">
          <div class="form-item">
            <label class="form-label">
              {{ t('agentEdit.ragMaxResults') }}
              <a-tooltip :title="t('agentEdit.ragMaxResultsTooltip')">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.ragMaxResults"
              :min="1"
              :max="20"
              :placeholder="t('agentEdit.ragMaxResultsDefault')"
              class="tech-number"
            />
            <span class="field-hint">{{ t('agentEdit.ragMaxResultsHint') }}</span>
          </div>
          <div class="form-item">
            <label class="form-label">
              {{ t('agentEdit.ragMinScore') }}
              <a-tooltip :title="t('agentEdit.ragMinScoreTooltip')">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.ragMinScore"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
              :placeholder="t('agentEdit.ragMinScoreDefault')"
              class="tech-number"
            />
            <span class="field-hint">{{ t('agentEdit.ragMinScoreHint') }}</span>
          </div>
        </div>

        <div class="form-row two-cols">
          <div class="form-item">
            <label class="form-label">
              {{ t('agentEdit.ragMaxDocLen') }}
              <a-tooltip :title="t('agentEdit.ragMaxDocLenTooltip')">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.ragMaxDocumentLength"
              :min="100"
              :placeholder="t('agentEdit.ragMaxDocLenPlaceholder')"
              class="tech-number"
            />
          </div>
          <div class="form-item">
            <label class="form-label">
              {{ t('agentEdit.ragMaxTotalLen') }}
              <a-tooltip :title="t('agentEdit.ragMaxTotalLenTooltip')">
                <question-circle-outlined class="help-icon" />
              </a-tooltip>
            </label>
            <a-input-number
              v-model:value="form.ragMaxTotalContentLength"
              :min="100"
              :placeholder="t('agentEdit.ragMaxDocLenPlaceholder')"
              class="tech-number"
            />
          </div>
        </div>
      </section>

      <!-- ── Skill 目录配置 ─────────────────────────────────────────── -->
      <section class="config-section">
        <div class="section-title">
          <ThunderboltOutlined class="section-icon" />
          {{ t('agentEdit.skillTree') }}
          <span class="section-hint">{{ t('agentEdit.skillTreeHint') }}</span>
        </div>
        <SkillTreeEditor v-model="form.skillTree" />
      </section>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { useI18n } from 'vue-i18n';
import {
  RobotOutlined,
  ArrowLeftOutlined,
  InfoCircleOutlined,
  ToolOutlined,
  SettingOutlined,
  DatabaseOutlined,
  QuestionCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue';
import {
  getAgentDefinition,
  createAgentDefinition,
  updateAgentDefinition,
  getMcpServers,
  getAvailableSystemToolsForAgent,
  getKnowledgeList,
} from '../agent/agent.api';
import type { AgentDefinitionRequest, SkillTreeNode } from '../agent/agent.types';
import SkillTreeEditor from './components/SkillTreeEditor.vue';

// ─── 路由 ────────────────────────────────────────────────────────────────────

const route = useRoute();
const router = useRouter();
const { t } = useI18n();

const isEdit = computed(() => !!route.params.id);
const agentId = computed(() => route.params.id as string | undefined);

// ─── 状态 ────────────────────────────────────────────────────────────────────

const loading = ref(false);
const saving = ref(false);
const mcpLoading = ref(false);
const editingBuiltin = ref(false);

const globalMcpOptions = ref<{ label: string; value: string }[]>([]);
const personalMcpOptions = ref<{ label: string; value: string }[]>([]);
const systemToolOptions = ref<{ label: string; value: string }[]>([]);
const knowledgeOptions = ref<{ label: string; value: string }[]>([]);

const form = ref({
  name: '',
  description: '',
  systemPrompt: '',
  serverMcpIds: [] as string[],
  personalMcpCapabilities: [] as string[],
  systemTools: [] as string[],
  knowledgeIds: [] as string[],
  historyMessageLoadLimit: null as number | null,
  maxToolRounds: null as number | null,
  ragMaxResults: null as number | null,
  ragMinScore: null as number | null,
  ragMaxDocumentLength: null as number | null,
  ragMaxTotalContentLength: null as number | null,
  skillTree: [] as SkillTreeNode[],
});

// ─── 初始化 ──────────────────────────────────────────────────────────────────

async function loadMcpOptions() {
  mcpLoading.value = true;
  try {
    const allServers = await getMcpServers();
    globalMcpOptions.value = allServers
      .filter((s) => s.scope === 0)
      .map((s) => ({
        label: s.enabled ? s.name : `${s.name}（已禁用）`,
        value: s.id,
      }));

    // 对于 PERSONAL：用能力标签作为选项（去重）
    const capSet = new Set<string>();
    allServers
      .filter((s) => s.scope === 1 && s.capability)
      .forEach((s) => capSet.add(s.capability!));
    personalMcpOptions.value = [...capSet].map((cap) => ({
      label: cap,
      value: cap,
    }));
  } finally {
    mcpLoading.value = false;
  }
}

async function loadOptions() {
  const [tools, kbList] = await Promise.all([
    getAvailableSystemToolsForAgent(),
    getKnowledgeList(),
  ]);
  systemToolOptions.value = tools.map((tool) => ({
    label: `${tool.name}${tool.description ? ' — ' + tool.description : ''}`,
    value: tool.name,
  }));
  knowledgeOptions.value = kbList.map((kb) => ({ label: kb.name, value: kb.id }));
}

async function loadAgent() {
  if (!agentId.value) return;
  const agent = await getAgentDefinition(agentId.value);
  if (!agent) {
    message.error(t('agentEdit.agentNotFound'));
    router.push({ name: 'AgentList' });
    return;
  }
  editingBuiltin.value = agent.builtin;
  form.value = {
    name: agent.name,
    description: agent.description || '',
    systemPrompt: agent.systemPrompt || '',
    serverMcpIds: agent.tools?.serverMcpIds || [],
    personalMcpCapabilities: agent.tools?.personalMcpCapabilities || [],
    systemTools: agent.tools?.systemTools || [],
    knowledgeIds: agent.tools?.knowledgeIds || [],
    historyMessageLoadLimit: agent.contextConfig?.historyMessageLoadLimit ?? null,
    maxToolRounds: agent.contextConfig?.maxToolRounds ?? null,
    ragMaxResults: agent.ragConfig?.maxResults ?? null,
    ragMinScore: agent.ragConfig?.minScore ?? null,
    ragMaxDocumentLength: agent.ragConfig?.maxDocumentLength ?? null,
    ragMaxTotalContentLength: agent.ragConfig?.maxTotalContentLength ?? null,
    skillTree: agent.skillTree || [],
  };
}

onMounted(async () => {
  loading.value = true;
  try {
    await Promise.all([loadOptions(), loadMcpOptions(), loadAgent()]);
  } catch {
    message.error(t('agentEdit.loadFailed'));
  } finally {
    loading.value = false;
  }
});

// ─── 保存 ────────────────────────────────────────────────────────────────────

async function handleSave() {
  if (!form.value.name.trim()) {
    message.warning(t('agentEdit.nameRequired'));
    return;
  }

  const request: AgentDefinitionRequest = {
    name: form.value.name.trim(),
    description: form.value.description.trim() || undefined,
    systemPrompt: form.value.systemPrompt || undefined,
    tools: {
      serverMcpIds: form.value.serverMcpIds,
      personalMcpCapabilities: form.value.personalMcpCapabilities,
      systemTools: form.value.systemTools,
      knowledgeIds: form.value.knowledgeIds,
    },
    skillTree: form.value.skillTree.length > 0 ? form.value.skillTree : undefined,
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
      message.success(isEdit.value ? t('agentEdit.saveSuccess') : t('agentEdit.createSuccess'));
      router.push({ name: 'AgentList' });
    } else {
      message.error(isEdit.value ? t('agentEdit.saveFailed') : t('agentEdit.createFailed'));
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

.manage-link {
  color: #60a5fa;
  margin-left: 8px;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

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
