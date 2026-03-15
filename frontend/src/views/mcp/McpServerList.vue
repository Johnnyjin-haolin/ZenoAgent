<template>
  <div class="mcp-page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <api-outlined class="header-icon" />
        <div>
          <h2 class="page-title">MCP 服务器管理</h2>
          <p class="page-subtitle">管理 GLOBAL（服务端执行）和 PERSONAL（客户端执行）MCP 服务器</p>
        </div>
      </div>
      <a-button type="primary" class="add-btn" @click="handleAdd">
        <template #icon><plus-outlined /></template>
        添加服务器
      </a-button>
    </div>

    <!-- 过滤栏 -->
    <div class="filter-bar">
      <a-segmented
        v-model:value="scopeFilter"
        :options="scopeOptions"
        class="scope-filter"
      />
      <a-input-search
        v-model:value="searchText"
        placeholder="搜索服务器名称..."
        allow-clear
        class="search-input"
        @search="handleSearch"
        @change="handleSearch"
      />
    </div>

    <!-- 服务器列表 -->
    <div v-if="loading" class="loading-wrap">
      <a-spin size="large" />
    </div>
    <div v-else-if="filteredServers.length === 0" class="empty-wrap">
      <a-empty description="暂无 MCP 服务器，点击右上角添加" />
    </div>
    <div v-else class="server-grid">
      <McpServerCard
        v-for="server in filteredServers"
        :key="server.id"
        :server="server"
        @edit="handleEdit(server)"
        @delete="handleDelete(server)"
        @toggle="handleToggle(server, $event)"
        @view-tools="handleViewTools(server)"
        @test="handleTest(server)"
        @configure-secret="handleConfigureSecret(server)"
      />
    </div>

    <!-- 新增/编辑 抽屉 -->
    <McpServerDrawer
      v-model:open="drawerOpen"
      :server="editingServer"
      @saved="handleSaved"
    />

    <!-- 工具查看 Modal -->
    <McpToolsModal
      v-model:open="toolsModalOpen"
      :server="viewingServer"
    />

    <!-- 密钥配置 Modal（PERSONAL 专用） -->
    <McpSecretModal
      v-model:open="secretModalOpen"
      :server="secretServer"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { message, Modal } from 'ant-design-vue';
import { ApiOutlined, PlusOutlined } from '@ant-design/icons-vue';
import { getMcpServers, deleteMcpServer, toggleMcpServer, testMcpServer } from '../agent/agent.api';
import type { McpServerInfo } from '../agent/agent.types';
import McpServerCard from './components/McpServerCard.vue';
import McpServerDrawer from './components/McpServerDrawer.vue';
import McpToolsModal from './components/McpToolsModal.vue';
import McpSecretModal from './components/McpSecretModal.vue';

// ─── 状态 ──────────────────────────────────────────────────────────────────

const loading = ref(false);
const servers = ref<McpServerInfo[]>([]);
const scopeFilter = ref<'ALL' | 'GLOBAL' | 'PERSONAL'>('ALL');
const searchText = ref('');

const drawerOpen = ref(false);
const editingServer = ref<McpServerInfo | null>(null);

const toolsModalOpen = ref(false);
const viewingServer = ref<McpServerInfo | null>(null);

const secretModalOpen = ref(false);
const secretServer = ref<McpServerInfo | null>(null);

// ─── 过滤器配置 ────────────────────────────────────────────────────────────

const scopeOptions = [
  { label: '全部', value: 'ALL' },
  { label: '🌐 GLOBAL（服务端）', value: 'GLOBAL' },
  { label: '👤 PERSONAL（客户端）', value: 'PERSONAL' },
];

const filteredServers = computed(() => {
  let list = servers.value;
  if (scopeFilter.value === 'GLOBAL') {
    list = list.filter((s) => s.scope === 0);
  } else if (scopeFilter.value === 'PERSONAL') {
    list = list.filter((s) => s.scope === 1);
  }
  if (searchText.value.trim()) {
    const kw = searchText.value.trim().toLowerCase();
    list = list.filter(
      (s) =>
        s.name.toLowerCase().includes(kw) ||
        s.description?.toLowerCase().includes(kw) ||
        s.endpointUrl.toLowerCase().includes(kw)
    );
  }
  return list;
});

// ─── 加载数据 ──────────────────────────────────────────────────────────────

async function loadServers() {
  loading.value = true;
  try {
    servers.value = await getMcpServers();
  } catch {
    message.error('加载 MCP 服务器列表失败');
  } finally {
    loading.value = false;
  }
}

onMounted(loadServers);

// ─── 事件处理 ──────────────────────────────────────────────────────────────

function handleSearch() {
  // 搜索通过 computed 响应式自动处理
}

function handleAdd() {
  editingServer.value = null;
  drawerOpen.value = true;
}

function handleEdit(server: McpServerInfo) {
  editingServer.value = server;
  drawerOpen.value = true;
}

function handleDelete(server: McpServerInfo) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除 MCP 服务器「${server.name}」吗？删除后无法恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      const ok = await deleteMcpServer(server.id);
      if (ok) {
        message.success('删除成功');
        await loadServers();
      } else {
        message.error('删除失败');
      }
    },
  });
}

async function handleToggle(server: McpServerInfo, enabled: boolean) {
  const ok = await toggleMcpServer(server.id, enabled);
  if (ok) {
    server.enabled = enabled;
    message.success(enabled ? '已启用' : '已禁用');
  } else {
    message.error('操作失败');
  }
}

async function handleTest(server: McpServerInfo) {
  message.loading({ content: `正在测试连通性...`, key: 'test', duration: 0 });
  const result = await testMcpServer(server.id);
  if (result === 'OK' || result.startsWith('OK:')) {
    const toolCount = result.startsWith('OK:') ? parseInt(result.slice(3), 10) : null;
    const hint = toolCount != null ? `，已发现 ${toolCount} 个工具` : '';
    message.success({ content: `连通性测试通过 ✓${hint}`, key: 'test' });
  } else {
    message.error({ content: `连通性测试失败: ${result}`, key: 'test', duration: 5 });
  }
}

function handleViewTools(server: McpServerInfo) {
  viewingServer.value = server;
  toolsModalOpen.value = true;
}

function handleConfigureSecret(server: McpServerInfo) {
  secretServer.value = server;
  secretModalOpen.value = true;
}

async function handleSaved() {
  drawerOpen.value = false;
  await loadServers();
}
</script>

<style scoped lang="less">
.mcp-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 24px 28px;
  gap: 20px;
  overflow: hidden;
}

// ─── 页头 ──────────────────────────────────────────────────────────────────

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-icon {
  font-size: 28px;
  color: #60a5fa;
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #e2e8f0;
  font-family: 'Inter', sans-serif;
  line-height: 1.3;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
}

.add-btn {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  height: 36px;
  padding: 0 20px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 7px;
  box-shadow: 0 0 10px rgba(59, 130, 246, 0.3);
  flex-shrink: 0;

  &:hover {
    background: linear-gradient(135deg, #60a5fa, #3b82f6);
  }
}

// ─── 过滤栏 ────────────────────────────────────────────────────────────────

.filter-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.search-input {
  width: 280px;
}

// ─── 服务器网格 ────────────────────────────────────────────────────────────

.server-grid {
  flex: 1;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
  align-content: start;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
  }
}

// ─── 空状态 / 加载 ─────────────────────────────────────────────────────────

.loading-wrap,
.empty-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>

<style lang="less">
// scope-filter 样式
.mcp-page {
  .scope-filter {
    .ant-segmented-item {
      color: #64748b;
      font-size: 12px;

      &:hover {
        color: #e2e8f0;
      }

      &.ant-segmented-item-selected {
        color: #60a5fa;
        font-weight: 500;
      }
    }
  }

  // search input dark 主题
  .search-input {
    .ant-input {
      background: rgba(0, 0, 0, 0.2) !important;
      border-color: rgba(59, 130, 246, 0.2) !important;
      color: #e2e8f0 !important;
      font-size: 13px;
      font-family: 'JetBrains Mono', monospace;
    }

    .ant-input:focus,
    .ant-input-affix-wrapper-focused {
      border-color: #60a5fa !important;
    }
  }
}
</style>
