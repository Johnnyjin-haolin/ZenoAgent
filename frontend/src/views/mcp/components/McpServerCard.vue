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
      <!-- 连接状态点 -->
      <div class="stat-item">
        <span class="conn-dot" :class="connDotClass" />
        <span class="stat-text">{{ connLabel }}</span>
      </div>

      <!-- 工具数量 -->
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
      <!-- 配置本地密钥 -->
      <a-button
        v-if="hasLocalHeaders"
        size="small"
        class="action-btn secret-btn"
        @click="emit('configure-secret')"
      >
        <template #icon><key-outlined /></template>
        配置密钥
      </a-button>

      <!-- 查看工具（GLOBAL + PERSONAL 均显示） -->
      <a-button
        size="small"
        class="action-btn"
        @click="emit('view-tools')"
      >
        <template #icon><unordered-list-outlined /></template>
        查看工具
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

      <!-- PERSONAL 本地连通性测试 -->
      <a-button
        v-if="server.scope === 1"
        size="small"
        class="action-btn"
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
} from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import { canExecutePersonalMcp, buildPersonalMcpHeaders } from '@/utils/mcpSecretStore';
import { testPersonalMcpServer } from '../../agent/agent.api';
import type { McpServerInfo } from '../../agent/agent.types';

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
  (e: 'local-test-result', connected: boolean, toolCount: number | null): void;
}>();

// ─── 认证 Header 状态 ──────────────────────────────────────────────────────

const hasAuthHeaders = computed(() => {
  const h = props.server.authHeaders;
  return h != null && Object.keys(h).length > 0;
});

const hasCloudHeaders = computed(() => {
  const h = props.server.authHeaders;
  if (!h) return false;
  return Object.values(h).some((v) => v === '***');
});

const hasLocalHeaders = computed(() => {
  const h = props.server.authHeaders;
  if (!h) return false;
  return Object.values(h).some((v) => v === '');
});

const globalAuthClass = computed(() => (hasCloudHeaders.value ? 'auth-ok' : 'auth-missing'));
const globalAuthLabel = computed(() =>
  hasCloudHeaders.value ? '云端密钥已配置' : '云端密钥未配置'
);

const localSecretStatus = computed(() => {
  if (!hasLocalHeaders.value) return 'no-auth';
  const localOnly = Object.fromEntries(
    Object.entries(props.server.authHeaders || {}).filter(([, v]) => v === '')
  );
  return canExecutePersonalMcp(props.server.id, localOnly) ? 'auth-ok' : 'auth-missing';
});

const localSecretLabel = computed(() => {
  if (!hasLocalHeaders.value) return '无需本地密钥';
  return localSecretStatus.value === 'auth-ok' ? '本地密钥已配置' : '本地密钥未配置';
});

// ─── 连接状态点 ────────────────────────────────────────────────────────────

const connDotClass = computed(() => {
  if (props.server._connected === true) return 'dot-connected';
  if (props.server._connected === false) return 'dot-failed';
  return 'dot-unknown';
});

const connLabel = computed(() => {
  if (props.server._connected === true) return '已连接';
  if (props.server._connected === false) return '连接失败';
  return '未检测';
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
      const count = parseInt(result.slice(3), 10);
      message.success(`连通成功，发现 ${count} 个工具`);
      emit('local-test-result', true, isNaN(count) ? null : count);
    } else {
      const reason = result.startsWith('FAIL:') ? result.slice(5) : result;
      message.error(`连通失败: ${reason}`);
      emit('local-test-result', false, null);
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e);
    message.error(`测试异常: ${msg}`);
    emit('local-test-result', false, null);
  } finally {
    localTesting.value = false;
  }
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
  align-items: center;
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

// ─── 连接状态点 ────────────────────────────────────────────────────────────

.conn-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;

  &.dot-connected {
    background: #34d399;
    box-shadow: 0 0 5px rgba(52, 211, 153, 0.6);
  }

  &.dot-failed {
    background: #f87171;
    box-shadow: 0 0 5px rgba(248, 113, 113, 0.6);
  }

  &.dot-unknown {
    background: #475569;
  }
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
</style>
