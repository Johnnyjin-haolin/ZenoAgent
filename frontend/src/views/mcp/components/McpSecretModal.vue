<template>
  <a-modal
    :open="open"
    :title="`配置本地密钥 — ${server?.name || ''}`"
    width="520"
    class="mcp-secret-modal"
    ok-text="保存密钥"
    cancel-text="取消"
    @ok="handleSave"
    @cancel="handleCancel"
  >
    <div class="secret-modal-body">
      <!-- 说明 -->
      <div class="secret-info">
        <info-circle-outlined class="info-icon" />
        <div class="info-text">
          <strong>本地密钥</strong>说明：
          密钥仅存储在您的浏览器 localStorage 中，不会上传到服务端。
          当 Agent 需要调用此 MCP 工具时，浏览器将自动使用该密钥进行认证。
          云端密钥（☁️）已由管理员配置，此处无需填写。
        </div>
      </div>

      <!-- 无需认证 -->
      <template v-if="headerKeys.length === 0">
        <div class="no-auth-hint">
          <a-tag color="green">✓ 此服务器无需认证</a-tag>
          <span class="meta-label">无认证 Header 配置，可直接执行</span>
        </div>
      </template>

      <!-- 各 Header 密钥配置行 -->
      <template v-else>
        <div
          v-for="headerName in headerKeys"
          :key="headerName"
          class="header-secret-row"
        >
          <div class="header-secret-label">
            <a-tag color="blue" class="header-tag">{{ headerName }}</a-tag>
            <a-tag :color="hasSecretFor(headerName) ? 'green' : 'orange'" class="status-tag">
              {{ hasSecretFor(headerName) ? '✓ 已配置' : '⚠ 未配置' }}
            </a-tag>
          </div>
          <a-input-password
            v-model:value="secretInputs[headerName]"
            :placeholder="hasSecretFor(headerName) ? '留空则保持原密钥' : '请输入密钥值（如：Bearer token）'"
            class="tech-input"
            allow-clear
          />
          <div v-if="hasSecretFor(headerName)" class="clear-btn-row">
            <a-button danger size="small" @click="handleClearOne(headerName)">
              <template #icon><delete-outlined /></template>
              清除此 Header 密钥
            </a-button>
          </div>
        </div>
      </template>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { InfoCircleOutlined, DeleteOutlined } from '@ant-design/icons-vue';
import { getMcpSecret, setMcpSecret, removeMcpSecret } from '@/utils/mcpSecretStore';
import type { McpServerInfo } from '../../agent/agent.types';

interface Props {
  open: boolean;
  server?: McpServerInfo | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
}>();

/**
 * 需要本地配置密钥的 Header 名列表
 * 只包含 value 为 "" 的 Header（本地存储类型）
 * value 为 "***" 的是云端密钥，不在此配置
 */
const headerKeys = computed(() => {
  if (!props.server?.authHeaders) return [];
  return Object.entries(props.server.authHeaders)
    .filter(([, v]) => v === '')
    .map(([k]) => k);
});

/** 每个 Header 对应的输入框值 */
const secretInputs = ref<Record<string, string>>({});

watch(
  () => props.open,
  (open) => {
    if (open) {
      const inputs: Record<string, string> = {};
      for (const h of headerKeys.value) {
        inputs[h] = '';
      }
      secretInputs.value = inputs;
    }
  }
);

function hasSecretFor(headerName: string): boolean {
  if (!props.server?.id) return false;
  const secret = getMcpSecret(props.server.id, headerName);
  return secret !== null && secret.trim() !== '';
}

function handleSave() {
  if (!props.server?.id) return;

  let saved = 0;
  let skipped = 0;

  for (const headerName of headerKeys.value) {
    const val = (secretInputs.value[headerName] || '').trim();
    if (val) {
      setMcpSecret(props.server.id, headerName, val);
      saved++;
    } else if (hasSecretFor(headerName)) {
      skipped++;
    }
  }

  if (saved > 0) {
    message.success(`已保存 ${saved} 个密钥到本地${skipped > 0 ? `（${skipped} 个保持原值）` : ''}`);
  } else if (skipped > 0) {
    message.info('密钥未变更');
  } else if (headerKeys.value.length > 0) {
    message.warning('请至少输入一个密钥');
    return;
  }

  emit('update:open', false);
}

function handleCancel() {
  emit('update:open', false);
}

function handleClearOne(headerName: string) {
  if (!props.server?.id) return;
  Modal.confirm({
    title: '确认清除',
    content: `确定要清除 ${headerName} 的本地密钥吗？`,
    okText: '清除',
    okType: 'danger',
    cancelText: '取消',
    onOk() {
      removeMcpSecret(props.server!.id, headerName);
      message.success(`${headerName} 本地密钥已清除`);
    },
  });
}
</script>

<style scoped lang="less">
.secret-modal-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.secret-info {
  display: flex;
  gap: 10px;
  background: rgba(59, 130, 246, 0.07);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 8px;
  padding: 12px 14px;
}

.info-icon {
  color: #60a5fa;
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 2px;
}

.info-text {
  font-size: 12px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', monospace;
  line-height: 1.7;

  strong {
    color: #e2e8f0;
  }
}

.no-auth-hint {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-label {
  font-size: 12px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
}

// ─── 每行 Header 密钥配置 ─────────────────────────────────────────────────

.header-secret-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  background: rgba(0, 0, 0, 0.15);
  border: 1px solid rgba(59, 130, 246, 0.1);
  border-radius: 8px;
}

.header-secret-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.header-tag {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

.status-tag {
  font-size: 11px;
}

.clear-btn-row {
  display: flex;
  justify-content: flex-end;
}
</style>

<style lang="less">
.mcp-secret-modal {
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

  .ant-modal-footer {
    border-top-color: rgba(59, 130, 246, 0.12) !important;
    background: rgba(15, 23, 42, 0.95);
  }

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

    input {
      background: transparent !important;
      color: #e2e8f0 !important;
    }

    .ant-input-password-icon {
      color: #475569;

      &:hover {
        color: #60a5fa;
      }
    }
  }
}
</style>
