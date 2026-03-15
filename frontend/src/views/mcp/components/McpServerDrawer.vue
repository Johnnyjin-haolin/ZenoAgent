<template>
  <a-drawer
    :open="open"
    :title="isEdit ? '编辑 MCP 服务器' : '添加 MCP 服务器'"
    width="520"
    placement="right"
    class="mcp-drawer"
    :mask-closable="false"
    @close="emit('update:open', false)"
  >
    <a-form
      :model="form"
      layout="vertical"
      class="mcp-form"
    >
      <!-- 作用域 -->
      <a-form-item label="作用域" required>
        <a-radio-group v-model:value="form.scope" class="scope-radio">
          <a-radio-button :value="0">🌐 GLOBAL（服务端执行）</a-radio-button>
          <a-radio-button :value="1">👤 PERSONAL（客户端执行）</a-radio-button>
        </a-radio-group>
        <div class="form-hint">
          <template v-if="form.scope === 0">
            GLOBAL：由服务器直接调用 MCP，适合统一管理的工具（如内部 API、数据库工具）
          </template>
          <template v-else>
            PERSONAL：命令由服务端发送，在用户浏览器本地执行，适合需要个人认证的工具（如 GitHub、Notion）
          </template>
        </div>
      </a-form-item>

      <!-- 名称 -->
      <a-form-item label="服务器名称" required>
        <a-input
          v-model:value="form.name"
          placeholder="如：GitHub MCP"
          :maxlength="128"
          class="tech-input"
        />
      </a-form-item>

      <!-- 描述 -->
      <a-form-item label="描述">
        <a-textarea
          v-model:value="form.description"
          placeholder="简要说明此服务器的用途..."
          :rows="2"
          :maxlength="512"
          class="tech-input"
        />
      </a-form-item>

      <!-- 连接类型 -->
      <a-form-item label="连接类型" required>
        <a-select
          v-model:value="form.connectionType"
          class="tech-select"
          dropdown-class-name="mcp-form-dropdown"
        >
          <a-select-option value="streamable-http">Streamable HTTP（推荐）</a-select-option>
          <a-select-option value="sse">SSE（Server-Sent Events）</a-select-option>
        </a-select>
      </a-form-item>

      <!-- Endpoint URL -->
      <a-form-item label="Endpoint URL" required>
        <a-input
          v-model:value="form.endpointUrl"
          placeholder="https://your-mcp-server.com/mcp"
          :maxlength="512"
          class="tech-input"
        />
      </a-form-item>

      <!-- 认证请求头（动态键值对） -->
      <a-form-item>
        <template #label>
          <span>认证请求头</span>
          <span class="label-hint">（留空则无需认证）</span>
        </template>

        <!-- 已有的键值对行 -->
        <div
          v-for="(row, idx) in form.authHeaders"
          :key="idx"
          class="header-row"
        >
          <a-input
            v-model:value="row.key"
            placeholder="Header 名，如：Authorization"
            class="tech-input header-key-input"
          />
          <span class="header-colon">:</span>
          <!-- 本地存储：value 可编辑，提交时只存 localStorage 不上传 -->
          <!-- 云端存储：value 可编辑，提交时加密上传服务端 -->
          <a-input-password
            v-model:value="row.value"
            :placeholder="row.storageType === 'local'
              ? (isEdit ? '不填则保持原本地密钥' : '如：Bearer sk-xxx（仅存本地）')
              : (isEdit ? '不填则保持原值不变' : '如：Bearer sk-xxx')"
            class="tech-input header-val-input"
          />
          <!-- 存储方式切换 -->
          <a-radio-group
            v-model:value="row.storageType"
            size="small"
            class="storage-toggle"
            @change="() => { row.value = '' }"
          >
            <a-radio-button value="cloud" class="storage-btn">
              ☁️
            </a-radio-button>
            <a-radio-button value="local" class="storage-btn">
              💻
            </a-radio-button>
          </a-radio-group>
          <a-button
            type="text"
            danger
            class="header-del-btn"
            @click="removeAuthHeader(idx)"
          >
            ✕
          </a-button>
        </div>

        <!-- 添加按钮 -->
        <a-button
          type="dashed"
          size="small"
          class="add-header-btn"
          @click="addAuthHeader"
        >
          + 添加 Header
        </a-button>

        <div class="form-hint storage-hint">
          ☁️ <strong>云端</strong>：密钥加密存储在服务端，所有人共享此密钥&nbsp;&nbsp;
          💻 <strong>本地</strong>：密钥仅存浏览器 localStorage，每人自行配置，不上传
        </div>
      </a-form-item>

      <!-- 高级配置折叠 -->
      <a-collapse
        v-model:activeKey="advancedOpen"
        class="advanced-collapse"
        ghost
      >
        <a-collapse-panel key="advanced" header="高级配置">
          <div class="advanced-grid">
            <a-form-item label="超时（ms）">
              <a-input-number
                v-model:value="form.timeoutMs"
                :min="1000"
                :max="60000"
                :step="1000"
                class="tech-number"
                placeholder="10000"
              />
            </a-form-item>
            <a-form-item label="读取超时（ms）">
              <a-input-number
                v-model:value="form.readTimeoutMs"
                :min="1000"
                :max="300000"
                :step="1000"
                class="tech-number"
                placeholder="30000"
              />
            </a-form-item>
            <a-form-item label="重试次数">
              <a-input-number
                v-model:value="form.retryCount"
                :min="0"
                :max="10"
                class="tech-number"
                placeholder="3"
              />
            </a-form-item>
          </div>

          <a-form-item label="额外请求头（JSON 格式）">
            <a-textarea
              v-model:value="form.extraHeaders"
              placeholder='{"X-Custom-Header": "value"}'
              :rows="3"
              class="tech-input"
            />
          </a-form-item>
        </a-collapse-panel>
      </a-collapse>
    </a-form>

    <template #footer>
      <div class="drawer-footer">
        <a-button class="cancel-btn" @click="emit('update:open', false)">取消</a-button>
        <a-button
          type="primary"
          :loading="saving"
          class="save-btn"
          @click="handleSave"
        >
          {{ isEdit ? '保存修改' : '添加服务器' }}
        </a-button>
      </div>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { message } from 'ant-design-vue';
import { createMcpServer, updateMcpServer } from '../../agent/agent.api';
import { setMcpSecret, getMcpSecret } from '@/utils/mcpSecretStore';
import type { McpServerInfo, McpServerRequest } from '../../agent/agent.types';

interface Props {
  open: boolean;
  server?: McpServerInfo | null;
}

/** 键值对行（用于表单内部状态） */
interface HeaderRow {
  key: string;
  value: string;
  /** 存储方式：cloud=加密存云端，local=只存浏览器 localStorage */
  storageType: 'cloud' | 'local';
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'saved'): void;
}>();

const saving = ref(false);
const advancedOpen = ref<string[]>([]);

interface FormState {
  name: string;
  description: string;
  scope: 0 | 1;
  connectionType: string;
  endpointUrl: string;
  authHeaders: HeaderRow[];
  extraHeaders: string;
  timeoutMs: number;
  readTimeoutMs: number;
  retryCount: number;
  enabled: boolean;
}

const defaultForm = (): FormState => ({
  name: '',
  description: '',
  scope: 0,
  connectionType: 'streamable-http',
  endpointUrl: '',
  authHeaders: [],
  extraHeaders: '',
  timeoutMs: 10000,
  readTimeoutMs: 30000,
  retryCount: 3,
  enabled: true,
});

const form = ref<FormState>(defaultForm());

const isEdit = computed(() => !!props.server?.id);

/**
 * 将后端返回的 authHeaders Record 转为表单行数组
 * value 为 "" 表示本地存储；value 为 "***" 或非空表示云端存储
 */
function recordToRows(record?: Record<string, string>): HeaderRow[] {
  if (!record) return [];
  return Object.entries(record).map(([key, value]) => ({
    key,
    value,
    storageType: value === '' ? 'local' : 'cloud',
  }));
}

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    if (props.server) {
      form.value = {
        name: props.server.name,
        description: props.server.description || '',
        scope: props.server.scope,
        connectionType: props.server.connectionType,
        endpointUrl: props.server.endpointUrl,
        // 编辑时：cloud 类型 value="***" 清空让用户重新输入；local 类型从 localStorage 回填
        authHeaders: recordToRows(props.server.authHeaders).map((row) => ({
          key: row.key,
          value: row.storageType === 'local'
            ? (getMcpSecret(props.server!.id, row.key) ?? '')
            : (row.value === '***' ? '' : row.value),
          storageType: row.storageType,
        })),
        extraHeaders: '',
        timeoutMs: props.server.timeoutMs || 10000,
        readTimeoutMs: 30000,
        retryCount: 3,
        enabled: props.server.enabled,
      };
    } else {
      form.value = defaultForm();
    }
    advancedOpen.value = [];
  }
);

function addAuthHeader() {
  // GLOBAL 默认云端；PERSONAL 默认本地
  form.value.authHeaders.push({ key: '', value: '', storageType: form.value.scope === 0 ? 'cloud' : 'local' });
}

function removeAuthHeader(idx: number) {
  form.value.authHeaders.splice(idx, 1);
}

async function handleSave() {
  if (!form.value.name.trim()) {
    message.warning('请填写服务器名称');
    return;
  }
  if (!form.value.endpointUrl.trim()) {
    message.warning('请填写 Endpoint URL');
    return;
  }

  // 过滤掉 key 为空的行，构建 authHeaders Map
  // storageType=cloud：value 正常上传；storageType=local：value 清空（密钥存 localStorage）
  const authHeadersMap: Record<string, string> = {};
  for (const row of form.value.authHeaders) {
    const k = row.key.trim();
    if (k) {
      authHeadersMap[k] = row.storageType === 'cloud' ? row.value.trim() : '';
    }
  }

  const req: McpServerRequest = {
    name: form.value.name.trim(),
    description: form.value.description?.trim() || undefined,
    scope: form.value.scope,
    connectionType: form.value.connectionType,
    endpointUrl: form.value.endpointUrl.trim(),
    authHeaders: Object.keys(authHeadersMap).length > 0 ? authHeadersMap : undefined,
    extraHeaders: form.value.extraHeaders?.trim() || undefined,
    timeoutMs: form.value.timeoutMs,
    readTimeoutMs: form.value.readTimeoutMs,
    retryCount: form.value.retryCount,
    enabled: true,
  };

  saving.value = true;
  try {
    let ok: McpServerInfo | null;
    if (isEdit.value && props.server?.id) {
      ok = await updateMcpServer(props.server.id, req);
    } else {
      ok = await createMcpServer(req);
    }
    if (ok) {
      // local 类型且填了值的 Header：保存到 localStorage（不上传服务端）
      for (const row of form.value.authHeaders) {
        const k = row.key.trim();
        const v = row.value.trim();
        if (k && v && row.storageType === 'local') {
          setMcpSecret(ok.id, k, v);
        }
      }
      message.success(isEdit.value ? '保存成功' : '添加成功');
      emit('saved');
    } else {
      message.error(isEdit.value ? '保存失败' : '添加失败');
    }
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="less">
.mcp-form {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.form-hint {
  font-size: 11px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
  margin-top: 4px;
  line-height: 1.6;
}

.label-hint {
  font-size: 11px;
  color: #475569;
  font-weight: 400;
  margin-left: 4px;
}

.scope-radio {
  width: 100%;
  display: flex;
  gap: 0;
}

// ─── 认证 Header 键值对编辑器 ────────────────────────────────────────────────

.header-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.header-key-input {
  flex: 0 0 180px;
}

.header-colon {
  color: #475569;
  font-size: 14px;
  flex-shrink: 0;
}

.header-val-input {
  flex: 1;
  min-width: 0;
}

.storage-toggle {
  flex-shrink: 0;
  display: flex;
}

.storage-btn {
  padding: 0 6px !important;
  font-size: 13px;
  line-height: 30px;
  height: 32px !important;
}

.header-del-btn {
  flex-shrink: 0;
  padding: 0 6px;
  height: 32px;
  color: #475569;

  &:hover {
    color: #f87171;
  }
}

.add-header-btn {
  width: 100%;
  border-color: rgba(59, 130, 246, 0.2) !important;
  color: #475569 !important;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
  background: rgba(0, 0, 0, 0.1) !important;
  margin-top: 2px;

  &:hover {
    border-color: rgba(59, 130, 246, 0.5) !important;
    color: #60a5fa !important;
  }
}

// ─── 高级配置 ────────────────────────────────────────────────────────────────

.advanced-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.cancel-btn {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  height: 34px;

  &:hover {
    border-color: rgba(59, 130, 246, 0.4);
    color: #e2e8f0;
  }
}

.save-btn {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  height: 34px;
  box-shadow: 0 0 10px rgba(59, 130, 246, 0.25);

  &:hover {
    background: linear-gradient(135deg, #60a5fa, #3b82f6);
  }
}

.tech-number {
  width: 100%;
}
</style>

<style lang="less">
.mcp-drawer {
  .ant-drawer-header {
    background: rgba(15, 23, 42, 0.95) !important;
    border-bottom-color: rgba(59, 130, 246, 0.15) !important;

    .ant-drawer-title {
      color: #e2e8f0 !important;
      font-size: 15px;
      font-weight: 600;
    }

    .ant-drawer-close {
      color: #64748b !important;

      &:hover {
        color: #e2e8f0 !important;
      }
    }
  }

  .ant-drawer-body {
    background: rgba(10, 15, 30, 0.98) !important;
    padding: 20px 24px;
  }

  .ant-drawer-footer {
    background: rgba(15, 23, 42, 0.95) !important;
    border-top-color: rgba(59, 130, 246, 0.12) !important;
    padding: 14px 24px;
  }

  // Form label
  .ant-form-item-label > label {
    color: #94a3b8 !important;
    font-size: 12px;
    font-family: 'JetBrains Mono', monospace;
    font-weight: 500;
  }

  // Inputs
  .tech-input {
    background: rgba(0, 0, 0, 0.25) !important;
    border-color: rgba(59, 130, 246, 0.2) !important;
    color: #e2e8f0 !important;
    font-size: 13px;
    font-family: 'JetBrains Mono', monospace;
    border-radius: 6px;

    &:hover,
    &:focus,
    &:focus-within {
      border-color: #60a5fa !important;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.08) !important;
    }

    textarea,
    input {
      background: transparent !important;
      color: #e2e8f0 !important;
      font-family: 'JetBrains Mono', monospace;

      &::placeholder {
        color: #475569 !important;
        opacity: 1;
      }
    }

    // 兼容 a-input-password 的 affix-wrapper 结构
    &.ant-input-affix-wrapper {
      input {
        background: transparent !important;
        color: #e2e8f0 !important;

        &::placeholder {
          color: #475569 !important;
          opacity: 1;
        }
      }
    }

    .ant-input-password-icon,
    .ant-input-suffix .anticon {
      color: #475569;

      &:hover {
        color: #60a5fa;
      }
    }

    &.ant-input-disabled,
    &[disabled],
    &.ant-input-affix-wrapper-disabled {
      background: rgba(0, 0, 0, 0.1) !important;
      border-color: rgba(59, 130, 246, 0.08) !important;
      color: #475569 !important;
      cursor: not-allowed;

      input {
        color: #475569 !important;
        cursor: not-allowed;
      }
    }
  }

  // Input number
  .tech-number {
    background: rgba(0, 0, 0, 0.25) !important;
    border-color: rgba(59, 130, 246, 0.2) !important;
    border-radius: 6px !important;

    &:hover {
      border-color: #60a5fa !important;
    }

    .ant-input-number-input {
      background: transparent !important;
      color: #e2e8f0 !important;
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
    }

    .ant-input-number-handler-wrap {
      background: rgba(15, 23, 42, 0.8);
      border-left-color: rgba(59, 130, 246, 0.12);
    }
  }

  // Select
  .tech-select {
    width: 100%;

    .ant-select-selector {
      background: rgba(0, 0, 0, 0.25) !important;
      border-color: rgba(59, 130, 246, 0.2) !important;
      color: #e2e8f0 !important;
      font-size: 13px;
      border-radius: 6px !important;
    }

    &.ant-select-focused .ant-select-selector {
      border-color: #60a5fa !important;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.08) !important;
    }
  }

  // Scope radio buttons
  .scope-radio {
    .ant-radio-button-wrapper {
      background: rgba(0, 0, 0, 0.2);
      border-color: rgba(59, 130, 246, 0.2);
      color: #64748b;
      font-size: 12px;

      &:hover {
        color: #e2e8f0;
        border-color: rgba(59, 130, 246, 0.5);
      }

      &.ant-radio-button-wrapper-checked {
        background: rgba(59, 130, 246, 0.15);
        border-color: #3b82f6;
        color: #60a5fa;
        font-weight: 500;
      }
    }
  }

  // 存储方式切换（☁️ / 💻）
  .storage-toggle {
    .ant-radio-button-wrapper {
      background: rgba(0, 0, 0, 0.2);
      border-color: rgba(59, 130, 246, 0.15);
      color: #64748b;
      font-size: 13px;
      height: 32px;
      line-height: 30px;
      padding: 0 7px;

      &:hover {
        border-color: rgba(59, 130, 246, 0.5);
        color: #e2e8f0;
      }

      &.ant-radio-button-wrapper-checked {
        background: rgba(59, 130, 246, 0.18);
        border-color: #3b82f6;
        color: #60a5fa;

        &::before {
          background-color: rgba(59, 130, 246, 0.3);
        }
      }
    }
  }

  // 存储说明提示
  .storage-hint {
    strong {
      color: #94a3b8;
    }
  }

  // Collapse
  .advanced-collapse {
    margin-top: 4px;

    .ant-collapse-header {
      color: #64748b !important;
      font-size: 12px;
      font-family: 'JetBrains Mono', monospace;
      padding: 8px 0 !important;
    }

    .ant-collapse-content {
      background: transparent !important;
      border: none;
    }

    .ant-collapse-content-box {
      padding: 0 !important;
    }
  }
}

// Select dropdown
.mcp-form-dropdown {
  background: #0f172a !important;
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;

  .ant-select-item {
    color: #94a3b8;
    font-size: 13px;
    font-family: 'JetBrains Mono', monospace;

    &:hover,
    &.ant-select-item-option-active {
      background: rgba(59, 130, 246, 0.1) !important;
      color: #e2e8f0;
    }

    &.ant-select-item-option-selected {
      background: rgba(59, 130, 246, 0.18) !important;
      color: #60a5fa;
      font-weight: 500;
    }
  }
}
</style>
