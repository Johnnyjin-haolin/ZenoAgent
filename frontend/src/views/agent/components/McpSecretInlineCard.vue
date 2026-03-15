<template>
  <div class="mcp-secret-card">
    <!-- 头部 -->
    <div class="card-header">
      <key-outlined class="card-icon" />
      <div class="card-title">PERSONAL MCP 需要认证</div>
    </div>

    <!-- 说明 -->
    <div class="card-desc">
      Agent 正在尝试调用工具
      <code class="tool-name">{{ request.toolName }}</code>，
      但 MCP 服务器「<strong>{{ request.serverName }}</strong>」需要以下认证 Header：
      <span v-for="h in request.missingHeaders" :key="h">
        <code>{{ h }}</code>
      </span>
    </div>

    <!-- 密钥输入（若缺多个 Header，统一输入同一值；极少见场景，用户可后续在设置页逐个配置） -->
    <div class="card-input-row">
      <a-input-password
        v-model:value="secretInput"
        :placeholder="inputPlaceholder"
        class="secret-input"
        allow-clear
        @press-enter="handleSaveAndExecute"
      />
    </div>

    <!-- 保存选项 + 操作按钮 -->
    <div class="card-footer">
      <a-checkbox v-model:checked="saveToLocal" class="save-checkbox">
        保存到本地（下次自动使用）
      </a-checkbox>
      <div class="card-actions">
        <a-button size="small" class="skip-btn" @click="emit('skip')">
          跳过此工具
        </a-button>
        <a-button
          type="primary"
          size="small"
          class="exec-btn"
          :disabled="!secretInput.trim()"
          @click="handleSaveAndExecute"
        >
          <template #icon><thunderbolt-outlined /></template>
          确认执行
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { KeyOutlined, ThunderboltOutlined } from '@ant-design/icons-vue';
import { setMcpSecret } from '@/utils/mcpSecretStore';
import type { PendingSecretRequest } from '../hooks/useAgentChat';

interface Props {
  request: PendingSecretRequest;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'execute', secret: string): void;
  (e: 'skip'): void;
}>();

const secretInput = ref('');
const saveToLocal = ref(false);

const inputPlaceholder = computed(() => {
  const headers = props.request.missingHeaders;
  if (headers.length === 1) {
    return `请输入 ${headers[0]} 的值（如：Bearer ghp_xxx）`;
  }
  return `请输入认证密钥（如：Bearer token），将用于所有缺失 Header`;
});

function handleSaveAndExecute() {
  const secret = secretInput.value.trim();
  if (!secret) return;

  if (saveToLocal.value) {
    // 将密钥分别保存到每个缺失 Header
    for (const headerName of props.request.missingHeaders) {
      setMcpSecret(props.request.serverId, headerName, secret);
    }
  }

  emit('execute', secret);
  secretInput.value = '';
}
</script>

<style scoped lang="less">
.mcp-secret-card {
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(167, 139, 250, 0.3);
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 8px 0;
  box-shadow: 0 4px 16px rgba(167, 139, 250, 0.08);
}

// ─── 头部 ──────────────────────────────────────────────────────────────────

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-icon {
  font-size: 16px;
  color: #a78bfa;
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  color: #c4b5fd;
  font-family: 'Inter', sans-serif;
}

// ─── 说明 ──────────────────────────────────────────────────────────────────

.card-desc {
  font-size: 12px;
  color: #94a3b8;
  font-family: 'JetBrains Mono', monospace;
  line-height: 1.7;

  strong {
    color: #e2e8f0;
  }

  code {
    background: rgba(167, 139, 250, 0.1);
    border: 1px solid rgba(167, 139, 250, 0.2);
    border-radius: 3px;
    padding: 1px 5px;
    color: #a78bfa;
    font-size: 11px;
    margin: 0 2px;
  }

  .tool-name {
    color: #60a5fa;
    background: rgba(59, 130, 246, 0.1);
    border-color: rgba(59, 130, 246, 0.2);
  }
}

// ─── 输入 ──────────────────────────────────────────────────────────────────

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.save-checkbox {
  font-size: 12px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.skip-btn {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.1);
  color: #64748b;
  font-size: 12px;
  height: 28px;

  &:hover {
    color: #94a3b8;
    border-color: rgba(255, 255, 255, 0.2);
  }
}

.exec-btn {
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  border: none;
  font-size: 12px;
  height: 28px;
  box-shadow: 0 0 8px rgba(167, 139, 250, 0.25);

  &:hover {
    background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  }
}
</style>

<style lang="less">
.mcp-secret-card {
  .secret-input {
    .ant-input {
      background: rgba(0, 0, 0, 0.3) !important;
      border-color: rgba(167, 139, 250, 0.3) !important;
      color: #e2e8f0 !important;
      font-size: 12px;
      font-family: 'JetBrains Mono', monospace;
    }

    .ant-input:focus,
    .ant-input-affix-wrapper-focused {
      border-color: #a78bfa !important;
      box-shadow: 0 0 0 2px rgba(167, 139, 250, 0.1) !important;
    }

    .ant-input-password-icon {
      color: #475569;

      &:hover {
        color: #a78bfa;
      }
    }
  }

  .save-checkbox {
    .ant-checkbox-checked .ant-checkbox-inner {
      background: #7c3aed;
      border-color: #7c3aed;
    }

    .ant-checkbox-inner {
      background: rgba(0, 0, 0, 0.3);
      border-color: rgba(167, 139, 250, 0.3);
    }
  }
}
</style>
