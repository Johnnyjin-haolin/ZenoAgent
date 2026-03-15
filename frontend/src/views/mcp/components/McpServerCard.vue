<template>
  <div class="server-card" :class="{ disabled: !server.enabled }">
    <!-- 顶部 scope 标签 + 开关 -->
    <div class="card-top">
      <a-tag :color="server.scope === 0 ? 'blue' : 'purple'" class="scope-tag">
        {{ server.scope === 0 ? '🌐 GLOBAL' : '👤 PERSONAL' }}
      </a-tag>
      <a-switch
        :checked="server.enabled"
        size="small"
        class="enable-switch"
        @change="(v: boolean) => emit('toggle', v)"
      />
    </div>

    <!-- 服务器名称 + 描述 -->
    <div class="card-body">
      <div class="server-name">{{ server.name }}</div>
      <div v-if="server.description" class="server-desc">{{ server.description }}</div>
      <div class="server-url">
        <link-outlined class="url-icon" />
        <span class="url-text" :title="server.endpointUrl">{{ server.endpointUrl }}</span>
      </div>
      <div v-if="server.scope === 1 && server.capability" class="capability-tag">
        <tag-outlined />
        能力: {{ server.capability }}
      </div>
    </div>

    <!-- 统计信息行 -->
    <div class="card-stats">
      <div class="stat-item">
        <tool-outlined class="stat-icon" />
        <span class="stat-text">
          {{ server.toolCount != null ? server.toolCount + ' 个工具' : '工具未知' }}
        </span>
      </div>

      <!-- 认证状态：云端认证 + 本地密钥状态分开展示 -->
      <div v-if="hasCloudHeaders" class="stat-item" :class="globalAuthClass">
        <key-outlined class="stat-icon" />
        <span class="stat-text">{{ globalAuthLabel }}</span>
      </div>
      <div v-if="hasLocalHeaders" class="stat-item" :class="localSecretStatus">
        <key-outlined class="stat-icon" />
        <span class="stat-text">{{ localSecretLabel }}</span>
      </div>
      <div v-if="!hasAuthHeaders" class="stat-item no-auth">
        <key-outlined class="stat-icon" />
        <span class="stat-text">无需认证</span>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="card-actions">
      <!-- 配置本地密钥（存在 local Header 时显示，不限 scope） -->
      <a-button
        v-if="hasLocalHeaders"
        size="small"
        class="action-btn secret-btn"
        @click="emit('configure-secret')"
      >
        <template #icon><key-outlined /></template>
        配置密钥
      </a-button>

      <!-- 查看工具（GLOBAL + PERSONAL 均支持） -->
      <a-button
        size="small"
        class="action-btn"
        :class="{ 'tools-active': toolsExpanded }"
        :loading="toolsLoading"
        @click="toggleTools"
      >
        <template #icon>
          <down-outlined v-if="!toolsExpanded" />
          <up-outlined v-else />
        </template>
        {{ toolsExpanded ? '收起工具' : '查看工具' }}
      </a-button>

      <!-- GLOBAL 连通性测试 -->
      <a-button
        v-if="server.scope === 0"
        size="small"
        class="action-btn"
        @click="emit('test')"
      >
        <template #icon><thunderbolt-outlined /></template>
        测试
      </a-button>

      <!-- PERSONAL 本地连通性测试（浏览器直连） -->
      <a-button
        v-if="server.scope === 1"
        size="small"
        class="action-btn test-personal-btn"
        :loading="localTesting"
        @click="handleLocalTest"
      >
        <template #icon><thunderbolt-outlined /></template>
        本地测试
      </a-button>

      <!-- 编辑 -->
      <a-button size="small" class="action-btn" @click="emit('edit')">
        <template #icon><edit-outlined /></template>
        编辑
      </a-button>

      <!-- 删除 -->
      <a-button size="small" danger class="action-btn delete-btn" @click="emit('delete')">
        <template #icon><delete-outlined /></template>
      </a-button>
    </div>

    <!-- 工具展开区域 -->
    <div v-if="toolsExpanded" class="tools-panel">
      <!-- 加载中 -->
      <div v-if="toolsLoading" class="tools-loading">
        <a-spin size="small" />
        <span>正在获取工具列表...</span>
      </div>

      <!-- 无工具 -->
      <div v-else-if="toolList.length === 0" class="tools-empty">
        暂无工具（请检查服务器连通性）
      </div>

      <!-- 工具列表 -->
      <template v-else>
        <div class="tools-header">
          <unordered-list-outlined class="tools-header-icon" />
          <span class="tools-header-title">工具列表</span>
          <span class="tools-count">{{ toolList.length }} 个</span>
        </div>
        <div
          v-for="tool in toolList"
          :key="tool.name"
          class="tool-item"
          :class="{ expanded: expandedTools.has(tool.name) }"
          @click="toggleTool(tool.name)"
        >
          <div class="tool-row">
            <function-outlined class="tool-icon" />
            <span class="tool-name">{{ tool.name }}</span>
            <span v-if="tool.description" class="tool-desc-inline">{{ tool.description }}</span>
            <right-outlined
              class="tool-chevron"
              :class="{ rotated: expandedTools.has(tool.name) }"
            />
          </div>

          <!-- 参数 Schema 展开 -->
          <div v-if="expandedTools.has(tool.name) && hasParams(tool)" class="tool-params">
            <div class="params-title">参数</div>
            <div
              v-for="(param, paramName) in getParams(tool)"
              :key="paramName"
              class="param-row"
            >
              <span class="param-name">{{ paramName }}</span>
              <span class="param-type">{{ param.type || 'any' }}</span>
              <span v-if="isRequired(tool, paramName)" class="param-required">必填</span>
              <span v-if="param.description" class="param-desc">{{ param.description }}</span>
            </div>
          </div>
          <div v-else-if="expandedTools.has(tool.name) && !hasParams(tool)" class="tool-no-params">
            无需参数
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  LinkOutlined,
  TagOutlined,
  ToolOutlined,
  KeyOutlined,
  UnorderedListOutlined,
  ThunderboltOutlined,
  EditOutlined,
  DeleteOutlined,
  DownOutlined,
  UpOutlined,
  FunctionOutlined,
  RightOutlined,
} from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import { canExecutePersonalMcp, buildPersonalMcpHeaders } from '@/utils/mcpSecretStore';
import {
  testPersonalMcpServer,
  getMcpServerTools,
  prefetchPersonalMcpTools,
} from '../../agent/agent.api';
import type { McpServerInfo } from '../../agent/agent.types';

interface ToolEntry {
  name: string;
  description: string;
  inputSchema?: Record<string, unknown>;
}

interface Props {
  server: McpServerInfo;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'edit'): void;
  (e: 'delete'): void;
  (e: 'toggle', enabled: boolean): void;
  (e: 'view-tools'): void;
  (e: 'test'): void;
  (e: 'configure-secret'): void;
}>();

const hasAuthHeaders = computed(() => {
  const h = props.server.authHeaders;
  return h != null && Object.keys(h).length > 0;
});

/** authHeaders 中是否存在云端存储的 Header（value 为 "***"） */
const hasCloudHeaders = computed(() => {
  const h = props.server.authHeaders;
  if (!h) return false;
  return Object.values(h).some((v) => v === '***');
});

/** authHeaders 中是否存在本地存储的 Header（value 为 ""） */
const hasLocalHeaders = computed(() => {
  const h = props.server.authHeaders;
  if (!h) return false;
  return Object.values(h).some((v) => v === '');
});

// ─── 云端认证状态 ──────────────────────────────────────────────────────────
const globalAuthClass = computed(() => {
  return hasCloudHeaders.value ? 'auth-ok' : 'auth-missing';
});

const globalAuthLabel = computed(() => {
  return hasCloudHeaders.value ? '云端密钥已配置' : '云端密钥未配置';
});

// ─── 本地密钥状态 ──────────────────────────────────────────────────────────
const localSecretStatus = computed(() => {
  if (!hasLocalHeaders.value) return 'no-auth';
  const localOnlyHeaders = Object.fromEntries(
    Object.entries(props.server.authHeaders || {}).filter(([, v]) => v === '')
  );
  return canExecutePersonalMcp(props.server.id, localOnlyHeaders) ? 'auth-ok' : 'auth-missing';
});

const localSecretLabel = computed(() => {
  if (!hasLocalHeaders.value) return '无需本地密钥';
  return localSecretStatus.value === 'auth-ok' ? '本地密钥已配置' : '本地密钥未配置';
});

// ─── PERSONAL 本地测试 ────────────────────────────────────────────────────

const localTesting = ref(false);

async function handleLocalTest() {
  if (localTesting.value) return;
  localTesting.value = true;
  try {
    const authHeaders = buildPersonalMcpHeaders(props.server.id, props.server.authHeaders);
    const result = await testPersonalMcpServer(props.server, authHeaders);
    if (result.startsWith('OK:')) {
      const count = result.slice(3);
      message.success(`连通成功，发现 ${count} 个工具`);
    } else {
      const reason = result.startsWith('FAIL:') ? result.slice(5) : result;
      message.error(`连通失败: ${reason}`);
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e);
    message.error(`测试异常: ${msg}`);
  } finally {
    localTesting.value = false;
  }
}

// ─── 工具展开 ─────────────────────────────────────────────────────────────

const toolsExpanded = ref(false);
const toolsLoading = ref(false);
const toolList = ref<ToolEntry[]>([]);
const expandedTools = ref(new Set<string>());

async function toggleTools() {
  if (toolsExpanded.value) {
    toolsExpanded.value = false;
    return;
  }
  toolsExpanded.value = true;
  if (toolList.value.length > 0) return;
  toolsLoading.value = true;
  try {
    if (props.server.scope === 0) {
      const mcpTools = await getMcpServerTools(props.server.id);
      toolList.value = mcpTools.map((t) => ({
        name: t.name,
        description: t.description,
        inputSchema: undefined,
      }));
    } else {
      const authHeaders = buildPersonalMcpHeaders(props.server.id, props.server.authHeaders);
      const schemas = await prefetchPersonalMcpTools(props.server, authHeaders);
      toolList.value = schemas.map((s) => ({
        name: s.toolName,
        description: s.description,
        inputSchema: s.inputSchema,
      }));
    }
  } catch {
    message.error('获取工具列表失败');
    toolsExpanded.value = false;
  } finally {
    toolsLoading.value = false;
  }
}

function toggleTool(name: string) {
  if (expandedTools.value.has(name)) {
    expandedTools.value.delete(name);
  } else {
    expandedTools.value.add(name);
  }
  expandedTools.value = new Set(expandedTools.value);
}

function hasParams(tool: ToolEntry): boolean {
  const props_ = (tool.inputSchema as Record<string, unknown> | undefined)?.properties;
  return props_ != null && Object.keys(props_).length > 0;
}

function getParams(tool: ToolEntry): Record<string, { type?: string; description?: string }> {
  return (
    ((tool.inputSchema as Record<string, unknown> | undefined)?.properties as Record<
      string,
      { type?: string; description?: string }
    >) || {}
  );
}

function isRequired(tool: ToolEntry, paramName: string): boolean {
  const req = (tool.inputSchema as Record<string, unknown> | undefined)?.required as
    | string[]
    | undefined;
  return Array.isArray(req) && req.includes(paramName);
}
</script>

<style scoped lang="less">
.server-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: all 0.2s;
  cursor: default;

  &:hover {
    border-color: rgba(59, 130, 246, 0.35);
    background: rgba(15, 23, 42, 0.75);
    box-shadow: 0 0 16px rgba(59, 130, 246, 0.1);
  }

  &.disabled {
    opacity: 0.55;
    filter: grayscale(0.4);
  }
}

// ─── 顶部 ──────────────────────────────────────────────────────────────────

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.scope-tag {
  font-size: 11px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  border-radius: 4px;
  line-height: 20px;
}

// ─── 主体 ──────────────────────────────────────────────────────────────────

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.server-name {
  font-size: 15px;
  font-weight: 600;
  color: #e2e8f0;
  font-family: 'Inter', sans-serif;
  line-height: 1.4;
}

.server-desc {
  font-size: 12px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.server-url {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
}

.url-icon {
  flex-shrink: 0;
  color: #3b82f6;
}

.url-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.capability-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #a78bfa;
  font-family: 'JetBrains Mono', monospace;
  background: rgba(167, 139, 250, 0.08);
  border: 1px solid rgba(167, 139, 250, 0.2);
  border-radius: 4px;
  padding: 2px 8px;
  width: fit-content;
}

// ─── 统计行 ────────────────────────────────────────────────────────────────

.card-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;

  &.auth-ok {
    color: #34d399;

    .stat-icon {
      color: #34d399;
    }
  }

  &.auth-missing {
    color: #fbbf24;

    .stat-icon {
      color: #fbbf24;
    }
  }

  &.no-auth {
    color: #475569;
  }
}

.stat-icon {
  font-size: 12px;
}

// ─── 操作按钮 ──────────────────────────────────────────────────────────────

.card-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  border-top: 1px solid rgba(59, 130, 246, 0.08);
  padding-top: 10px;
}

.action-btn {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(59, 130, 246, 0.18);
  color: #94a3b8;
  font-size: 12px;
  height: 28px;
  border-radius: 5px;

  &:hover {
    border-color: rgba(59, 130, 246, 0.45);
    color: #e2e8f0;
    background: rgba(59, 130, 246, 0.08);
  }

  &.tools-active {
    color: #60a5fa;
    border-color: rgba(59, 130, 246, 0.4);
    background: rgba(59, 130, 246, 0.08);
  }

  &.secret-btn {
    color: #a78bfa;
    border-color: rgba(167, 139, 250, 0.3);

    &:hover {
      background: rgba(167, 139, 250, 0.1);
    }
  }

  &.delete-btn {
    margin-left: auto;
  }
}

// ─── 工具展开面板 ──────────────────────────────────────────────────────────

.tools-panel {
  border-top: 1px solid rgba(59, 130, 246, 0.12);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tools-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  color: #64748b;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}

.tools-empty {
  padding: 12px 0;
  color: #475569;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
  text-align: center;
}

.tools-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.tools-header-icon {
  color: #3b82f6;
  font-size: 12px;
}

.tools-header-title {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.tools-count {
  font-size: 11px;
  color: #3b82f6;
  font-family: 'JetBrains Mono', monospace;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 10px;
  padding: 0 6px;
  line-height: 18px;
}

.tool-item {
  background: rgba(10, 15, 30, 0.5);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 7px;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: rgba(59, 130, 246, 0.28);
    background: rgba(15, 25, 50, 0.7);
  }

  &.expanded {
    border-color: rgba(59, 130, 246, 0.3);
    background: rgba(15, 25, 50, 0.8);
  }
}

.tool-row {
  display: flex;
  align-items: center;
  gap: 7px;
}

.tool-icon {
  color: #60a5fa;
  font-size: 11px;
  flex-shrink: 0;
}

.tool-name {
  font-size: 12px;
  font-weight: 600;
  color: #93c5fd;
  font-family: 'JetBrains Mono', monospace;
  flex-shrink: 0;
}

.tool-desc-inline {
  font-size: 11px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-chevron {
  font-size: 10px;
  color: #475569;
  flex-shrink: 0;
  transition: transform 0.2s;
  margin-left: auto;

  &.rotated {
    transform: rotate(90deg);
  }
}

// ─── 参数 Schema ──────────────────────────────────────────────────────────

.tool-params {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed rgba(59, 130, 246, 0.12);
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.tool-no-params {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed rgba(59, 130, 246, 0.12);
  font-size: 11px;
  color: #334155;
  font-family: 'JetBrains Mono', monospace;
}

.params-title {
  font-size: 10px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 3px;
}

.param-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
}

.param-name {
  font-size: 11px;
  font-weight: 600;
  color: #7dd3fc;
  font-family: 'JetBrains Mono', monospace;
}

.param-type {
  font-size: 10px;
  color: #a78bfa;
  font-family: 'JetBrains Mono', monospace;
  background: rgba(167, 139, 250, 0.08);
  border: 1px solid rgba(167, 139, 250, 0.18);
  border-radius: 3px;
  padding: 0 5px;
  line-height: 16px;
}

.param-required {
  font-size: 10px;
  color: #f87171;
  font-family: 'JetBrains Mono', monospace;
  background: rgba(248, 113, 113, 0.08);
  border: 1px solid rgba(248, 113, 113, 0.2);
  border-radius: 3px;
  padding: 0 5px;
  line-height: 16px;
}

.param-desc {
  font-size: 11px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
}
</style>
