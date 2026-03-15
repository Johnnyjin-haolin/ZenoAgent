<template>
  <a-modal
    :open="open"
    :title="`工具列表 — ${server?.name || ''}`"
    width="700"
    class="mcp-tools-modal"
    :footer="null"
    @cancel="emit('update:open', false)"
  >
    <!-- 状态条 -->
    <div class="status-bar">
      <div class="status-indicator">
        <span
          class="status-dot"
          :class="{
            'dot-connected': connStatus === 'connected',
            'dot-failed': connStatus === 'failed',
            'dot-loading': connStatus === 'loading',
          }"
        />
        <span class="status-text">
          <template v-if="connStatus === 'loading'">正在连接...</template>
          <template v-else-if="connStatus === 'connected'">
            已连接 · {{ filteredTools.length < allTools.length ? `${filteredTools.length} / ${allTools.length}` : allTools.length }} 个工具
          </template>
          <template v-else-if="connStatus === 'failed'">连接失败</template>
          <template v-else>未检测</template>
        </span>
        <span v-if="errorMsg" class="error-hint">{{ errorMsg }}</span>
      </div>
      <a-button
        size="small"
        class="refresh-btn"
        :loading="connStatus === 'loading'"
        @click="loadTools"
      >
        <template #icon><reload-outlined /></template>
        刷新
      </a-button>
    </div>

    <!-- 搜索框 -->
    <a-input
      v-if="allTools.length > 0"
      v-model:value="searchText"
      placeholder="搜索工具名称或描述..."
      allow-clear
      class="search-input"
    >
      <template #prefix><search-outlined /></template>
    </a-input>

    <!-- 加载中 -->
    <div v-if="connStatus === 'loading'" class="modal-loading">
      <a-spin />
      <span>正在获取工具列表...</span>
    </div>

    <!-- 连接失败 -->
    <div v-else-if="connStatus === 'failed'" class="modal-empty">
      <a-empty :description="errorMsg || '连接失败，请检查服务器配置和网络'" />
    </div>

    <!-- 无工具 -->
    <div v-else-if="connStatus === 'connected' && allTools.length === 0" class="modal-empty">
      <a-empty description="该服务器未提供任何工具" />
    </div>

    <!-- 搜索无结果 -->
    <div v-else-if="connStatus === 'connected' && filteredTools.length === 0" class="modal-empty">
      <a-empty description="未找到匹配的工具" />
    </div>

    <!-- 工具列表 -->
    <div v-else-if="connStatus === 'connected'" class="tools-list">
      <div
        v-for="tool in filteredTools"
        :key="tool.name"
        class="tool-item"
        :class="{ expanded: expandedTools.has(tool.name) }"
        @click="toggleTool(tool.name)"
      >
        <div class="tool-header">
          <function-outlined class="tool-icon" />
          <span class="tool-name">{{ tool.name }}</span>
          <span v-if="tool.description" class="tool-desc-inline">{{ tool.description }}</span>
          <right-outlined
            class="tool-chevron"
            :class="{ rotated: expandedTools.has(tool.name) }"
          />
        </div>

        <!-- 参数展开 -->
        <div v-if="expandedTools.has(tool.name)" class="tool-params">
          <template v-if="hasParams(tool)">
            <div class="params-title">参数</div>
            <div
              v-for="(param, paramName) in getParams(tool)"
              :key="paramName"
              class="param-row"
            >
              <span class="param-name">{{ paramName }}</span>
              <span class="param-type">{{ param.type || 'any' }}</span>
              <span v-if="isRequired(tool, String(paramName))" class="param-required">必填</span>
              <span v-if="param.description" class="param-desc">{{ param.description }}</span>
            </div>
          </template>
          <div v-else class="no-params">无需参数</div>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import {
  FunctionOutlined,
  RightOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue';
import { getMcpServerTools, prefetchPersonalMcpTools } from '../../agent/agent.api';
import { buildPersonalMcpHeaders } from '@/utils/mcpSecretStore';
import type { McpServerInfo } from '../../agent/agent.types';

interface ToolEntry {
  name: string;
  description: string;
  inputSchema?: Record<string, unknown>;
}

interface Props {
  open: boolean;
  server?: McpServerInfo | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'connected', toolCount: number): void;
  (e: 'disconnected'): void;
}>();

type ConnStatus = 'idle' | 'loading' | 'connected' | 'failed';

const connStatus = ref<ConnStatus>('idle');
const errorMsg = ref('');
const allTools = ref<ToolEntry[]>([]);
const searchText = ref('');
const expandedTools = ref(new Set<string>());

const filteredTools = computed(() => {
  const kw = searchText.value.trim().toLowerCase();
  if (!kw) return allTools.value;
  return allTools.value.filter(
    (t) => t.name.toLowerCase().includes(kw) || t.description.toLowerCase().includes(kw)
  );
});

watch(
  () => [props.open, props.server?.id],
  ([open]) => {
    if (!open) {
      allTools.value = [];
      searchText.value = '';
      expandedTools.value = new Set();
      connStatus.value = 'idle';
      errorMsg.value = '';
      return;
    }
    loadTools();
  }
);

async function loadTools() {
  if (!props.server) return;
  connStatus.value = 'loading';
  errorMsg.value = '';
  allTools.value = [];
  expandedTools.value = new Set();
  try {
    if (props.server.scope === 0) {
      const mcpTools = await getMcpServerTools(props.server.id);
      allTools.value = mcpTools.map((t) => ({
        name: t.name,
        description: t.description,
        inputSchema: t.inputSchema,
      }));
    } else {
      const authHeaders = buildPersonalMcpHeaders(props.server.id, props.server.authHeaders);
      const schemas = await prefetchPersonalMcpTools(props.server, authHeaders);
      allTools.value = schemas.map((s) => ({
        name: s.toolName,
        description: s.description,
        inputSchema: s.inputSchema,
      }));
    }
    connStatus.value = 'connected';
    emit('connected', allTools.value.length);
  } catch (err: unknown) {
    connStatus.value = 'failed';
    errorMsg.value = err instanceof Error ? err.message : String(err);
    emit('disconnected');
  }
}

function toggleTool(name: string) {
  const next = new Set(expandedTools.value);
  if (next.has(name)) {
    next.delete(name);
  } else {
    next.add(name);
  }
  expandedTools.value = next;
}

function hasParams(tool: ToolEntry): boolean {
  const p = (tool.inputSchema as Record<string, unknown> | undefined)?.properties;
  return p != null && Object.keys(p).length > 0;
}

function getParams(tool: ToolEntry): Record<string, { type?: string; description?: string }> {
  return (
    ((tool.inputSchema as Record<string, unknown> | undefined)?.properties as Record<
      string,
      { type?: string; description?: string }
    >) ?? {}
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
// ─── 状态条 ────────────────────────────────────────────────────────────────

.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: rgba(10, 18, 40, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.12);
  border-radius: 8px;
  margin-bottom: 14px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #475569;

  &.dot-connected {
    background: #34d399;
    box-shadow: 0 0 6px rgba(52, 211, 153, 0.5);
  }

  &.dot-failed {
    background: #f87171;
    box-shadow: 0 0 6px rgba(248, 113, 113, 0.5);
  }

  &.dot-loading {
    background: #fbbf24;
    animation: pulse 1.2s ease-in-out infinite;
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.status-text {
  font-size: 12px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', monospace;
}

.error-hint {
  font-size: 11px;
  color: #f87171;
  font-family: 'JetBrains Mono', monospace;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.refresh-btn {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(59, 130, 246, 0.2);
  color: #64748b;
  font-size: 12px;
  height: 26px;
  border-radius: 5px;

  &:hover {
    border-color: rgba(59, 130, 246, 0.4);
    color: #60a5fa;
    background: rgba(59, 130, 246, 0.06);
  }
}

// ─── 搜索框 ────────────────────────────────────────────────────────────────

.search-input {
  margin-bottom: 12px;
  background: rgba(10, 18, 40, 0.5) !important;
  border-color: rgba(59, 130, 246, 0.18) !important;
  border-radius: 7px;
  color: #e2e8f0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;

  :deep(.ant-input) {
    background: transparent !important;
    color: #e2e8f0 !important;
    font-family: 'JetBrains Mono', monospace;

    &::placeholder {
      color: #475569 !important;
    }
  }

  :deep(.ant-input-prefix) {
    color: #475569;
    margin-right: 8px;
  }
}

// ─── 加载 / 空态 ──────────────────────────────────────────────────────────

.modal-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}

.modal-empty {
  padding: 32px 0;
  display: flex;
  justify-content: center;
}

// ─── 工具列表 ──────────────────────────────────────────────────────────────

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 58vh;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 5px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
  }
}

.tool-item {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 8px;
  padding: 10px 14px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    border-color: rgba(59, 130, 246, 0.28);
    background: rgba(15, 25, 50, 0.8);
  }

  &.expanded {
    border-color: rgba(59, 130, 246, 0.3);
    background: rgba(10, 20, 45, 0.9);
  }
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-icon {
  color: #60a5fa;
  font-size: 12px;
  flex-shrink: 0;
}

.tool-name {
  font-size: 13px;
  font-weight: 600;
  color: #93c5fd;
  font-family: 'JetBrains Mono', monospace;
  flex-shrink: 0;
  min-width: 120px;
}

.tool-desc-inline {
  font-size: 12px;
  color: #64748b;
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
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(59, 130, 246, 0.12);
}

.no-params {
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
  margin-bottom: 6px;
}

.param-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
  padding: 3px 0;

  &:not(:last-child) {
    border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  }
}

.param-name {
  font-size: 12px;
  font-weight: 600;
  color: #7dd3fc;
  font-family: 'JetBrains Mono', monospace;
  min-width: 100px;
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

<style lang="less">
.mcp-tools-modal {
  .ant-modal-content {
    background: rgba(10, 15, 30, 0.98) !important;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 12px;
  }

  .ant-modal-header {
    background: transparent !important;
    border-bottom-color: rgba(59, 130, 246, 0.12) !important;
  }

  .ant-modal-title {
    color: #e2e8f0 !important;
    font-size: 15px;
    font-weight: 600;
  }

  .ant-modal-close-x {
    color: #64748b !important;

    &:hover {
      color: #e2e8f0 !important;
    }
  }

  .ant-modal-body {
    padding: 20px;
  }
}
</style>
