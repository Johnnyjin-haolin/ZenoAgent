<template>
  <div class="agent-tool-config">
    <div class="selector-label">
      <Icon icon="ant-design:tool-outlined" class="label-icon" />
      <span>{{ t('agent.toolConfig.label') }}</span>
      <a-tooltip :title="t('agent.toolConfig.tooltip')">
        <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
      </a-tooltip>
      <a-button
        type="link"
        size="small"
        @click="loadData"
        :loading="loading"
        class="refresh-btn"
        :title="t('agent.toolConfig.refresh')"
      >
        <Icon icon="ant-design:reload-outlined" />
      </a-button>
    </div>

    <!-- 工具选择触发区域 -->
    <div class="tool-selector-trigger" @click="showToolModal = true">
      <div v-if="selectedTools.length === 0" class="trigger-placeholder">
        <Icon icon="ant-design:plus-circle-outlined" />
        <span>{{ t('agent.toolConfig.selectPlaceholder') }}</span>
      </div>
      <div v-else class="trigger-tags">
        <a-tag
          v-for="(tool, index) in selectedTools.slice(0, maxVisibleTags)"
          :key="tool"
          closable
          @close.stop="removeTool(tool)"
          class="tech-tag"
        >
          {{ tool }}
        </a-tag>
        <a-tag v-if="selectedTools.length > maxVisibleTags" class="tech-tag-more">
          +{{ selectedTools.length - maxVisibleTags }}
        </a-tag>
        <a-button
          type="link"
          size="small"
          @click.stop="showToolModal = true"
          class="edit-btn"
        >
          {{ t('common.edit') }}
        </a-button>
      </div>
    </div>

    <div v-if="showHint" class="tool-hint">
      <Icon icon="ant-design:info-circle-outlined" />
      <span>{{ t('agent.toolConfig.hint') }}</span>
      <span v-if="serverGroups.length === 0 && !loading" style="color: #F87171; margin-left: 8px;">
        {{ t('agent.toolConfig.noServer') }}
      </span>
    </div>

    <!-- 工具选择Modal -->
    <a-modal
      v-model:open="showToolModal"
      :title="t('agent.toolConfig.modalTitle')"
      :width="900"
      :footer="null"
      class="tech-modal"
      @open="handleToolModalOpen"
    >
      <div class="tool-selector-modal">
        <!-- 搜索框 -->
        <div class="search-bar">
          <a-input
            v-model:value="searchKeyword"
            :placeholder="t('agent.toolConfig.searchPlaceholder')"
            allow-clear
            class="tech-input"
          >
            <template #prefix>
              <Icon icon="ant-design:search-outlined" />
            </template>
          </a-input>
        </div>

        <!-- 主内容区：左右分栏 -->
        <div class="selector-content">
          <!-- 左侧：服务器列表 -->
          <div class="server-list">
            <div class="list-header">
              <span>{{ t('agent.toolConfig.serverList') }}</span>
              <span class="count-badge">{{ filteredServers.length }}</span>
            </div>
            <div v-if="loading" class="loading-container">
              <a-spin />
            </div>
            <div v-else-if="filteredServers.length === 0" class="empty-container">
              <Icon icon="ant-design:info-circle-outlined" />
              <div>{{ t('agent.toolConfig.noServerFound') }}</div>
            </div>
            <div v-else class="server-items">
              <div
                v-for="server in filteredServers"
                :key="server.serverId || server.id"
                class="server-item"
                :class="{ active: activeServerId === (server.serverId || server.id) }"
                @click="selectServer(server.serverId || server.id)"
              >
                <div class="server-info">
                  <Icon :icon="getServerIcon(server)" class="server-icon" />
                  <div class="server-details">
                    <div class="server-name">{{ server.name || server.id }}</div>
                    <div class="server-meta">
                      <span class="status-dot" :class="{ active: server.enabled }"></span>
                      <span class="tool-count">{{ server.toolCount }} tools</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：工具列表 -->
          <div class="tool-list">
            <div class="list-header">
              <span>{{ t('agent.toolConfig.toolList') }}</span>
              <span class="count-badge">{{ filteredTools.length }}</span>
              <div class="header-actions">
                <a-button
                  type="link"
                  size="small"
                  @click="selectAllTools"
                  :disabled="filteredTools.length === 0"
                  class="action-btn"
                >
                  {{ t('common.selectAll') }}
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  @click="clearAllTools"
                  :disabled="filteredTools.length === 0"
                  class="action-btn"
                >
                  {{ t('common.clear') }}
                </a-button>
              </div>
            </div>
            <div v-if="!activeServerId" class="empty-container">
              <Icon icon="ant-design:arrow-left-outlined" />
              <div>{{ t('agent.toolConfig.selectServerFirst') }}</div>
            </div>
            <div v-else-if="filteredTools.length === 0" class="empty-container">
              <Icon icon="ant-design:tool-outlined" />
              <div>{{ t('agent.toolConfig.noToolsInServer') }}</div>
            </div>
            <div v-else class="tool-items">
              <div
                v-for="tool in filteredTools"
                :key="tool.name"
                class="tool-item"
                :class="{ selected: isToolSelected(tool.name) }"
              >
                <a-checkbox
                  :checked="isToolSelected(tool.name)"
                  @change="toggleTool(tool.name)"
                  class="tech-checkbox"
                >
                  <div class="tool-content">
                    <div class="tool-name">{{ tool.name }}</div>
                    <div v-if="tool.description" class="tool-desc-wrapper">
                      <div
                        class="tool-desc"
                        :class="{ expanded: isToolDescExpanded(tool.name) }"
                      >
                        {{ tool.description }}
                      </div>
                      <a-button
                        v-if="shouldShowExpandButton(tool.description)"
                        type="link"
                        size="small"
                        @click.stop="toggleToolDesc(tool.name)"
                        class="expand-btn"
                      >
                        {{ isToolDescExpanded(tool.name) ? 'Collapse' : 'Expand' }}
                        <Icon
                          :icon="isToolDescExpanded(tool.name) ? 'ant-design:up-outlined' : 'ant-design:down-outlined'"
                          style="margin-left: 2px;"
                        />
                      </a-button>
                    </div>
                  </div>
                </a-checkbox>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部：已选工具显示 -->
        <div class="selected-tools-bar">
          <div class="selected-header">
            <span>{{ t('agent.toolConfig.selectedTools') }} ({{ tempSelectedTools.length }})</span>
            <a-button
              type="link"
              size="small"
              @click="clearTempSelection"
              :disabled="tempSelectedTools.length === 0"
              class="clear-btn"
            >
              {{ t('common.clear') }}
            </a-button>
          </div>
          <div class="selected-tags">
            <a-tag
              v-for="tool in tempSelectedTools"
              :key="tool"
              closable
              @close="removeTempTool(tool)"
              class="tech-tag"
            >
              {{ tool }}
            </a-tag>
            <span v-if="tempSelectedTools.length === 0" class="empty-hint">{{ t('agent.toolConfig.noToolSelected') }}</span>
          </div>
        </div>

        <!-- 底部操作按钮 -->
        <div class="modal-footer">
          <a-button @click="handleCancel" class="tech-btn-default">{{ t('common.cancel') }}</a-button>
          <a-button type="primary" @click="handleConfirm" class="tech-btn-primary">{{ t('common.confirm') }}</a-button>
        </div>
      </div>
    </a-modal>

    <!-- MCP服务器查看Modal -->
    <a-modal
      v-model:open="showServerModal"
      :title="t('agent.toolConfig.serverConfigTitle')"
      :width="800"
      :footer="null"
      class="tech-modal"
      @open="handleServerModalOpen"
    >
      <div class="mcp-server-view">
        <div v-if="serverLoading" style="text-align: center; padding: 40px;">
          <a-spin size="large" />
          <div style="margin-top: 16px; color: #94a3b8;">{{ t('common.loading') }}</div>
        </div>
        <div v-else-if="serverGroups.length === 0" style="text-align: center; padding: 40px; color: #94a3b8;">
          <Icon icon="ant-design:info-circle-outlined" style="font-size: 48px; margin-bottom: 16px; opacity: 0.5;" />
          <div>{{ t('agent.toolConfig.noServer') }}</div>
        </div>
        <div v-else>
          <a-collapse v-model:activeKey="activeServerKeys" :bordered="false" class="tech-collapse">
            <a-collapse-panel
              v-for="server in serverGroups"
              :key="server.serverId || server.id"
              :header="getServerHeader(server)"
            >
              <div class="server-info">
                <div class="info-item">
                  <span class="info-label">ID:</span>
                  <span class="info-value">{{ server.serverId || server.id }}</span>
                </div>
                <div class="info-item" v-if="server.connectionType">
                  <span class="info-label">{{ t('common.connectionType') }}:</span>
                  <span class="info-value">{{ server.connectionType }}</span>
                </div>
                <div class="info-item" v-if="server.description">
                  <span class="info-label">{{ t('common.description') }}:</span>
                  <span class="info-value">{{ server.description }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">{{ t('common.status') }}:</span>
                  <a-tag :color="server.enabled ? 'green' : 'default'" size="small" class="status-tag">
                    {{ server.enabled ? t('common.enabled') : t('common.disabled') }}
                  </a-tag>
                </div>
              </div>
              <div class="tools-list">
                <div class="tools-header">
                  <span>{{ t('agent.toolConfig.toolListTitle') }} ({{ getServerTools(server.serverId || server.id).length }})</span>
                </div>
                <div v-if="getServerTools(server.serverId || server.id).length === 0" class="no-tools">
                  {{ t('agent.toolConfig.noToolsInServer') }}
                </div>
                <div v-else class="tools-items">
                  <div
                    v-for="tool in getServerTools(server.serverId || server.id)"
                    :key="tool.name"
                    class="tool-item"
                  >
                    <div class="tool-header">
                      <span class="tool-name">{{ tool.name }}</span>
                      <a-tag v-if="tool.enabled" color="green" size="small" class="status-tag">{{ t('common.enable') }}</a-tag>
                      <a-tag v-else color="default" size="small" class="status-tag">{{ t('common.disable') }}</a-tag>
                    </div>
                    <div v-if="tool.description" class="tool-desc-wrapper">
                      <div
                        class="tool-desc"
                        :class="{ expanded: isServerToolDescExpanded(tool.name) }"
                      >
                        {{ tool.description }}
                      </div>
                      <a-button
                        v-if="shouldShowExpandButton(tool.description)"
                        type="link"
                        size="small"
                        @click="toggleServerToolDesc(tool.name)"
                        class="expand-btn"
                      >
                        {{ isServerToolDescExpanded(tool.name) ? 'Collapse' : 'Expand' }}
                        <Icon
                          :icon="isServerToolDescExpanded(tool.name) ? 'ant-design:up-outlined' : 'ant-design:down-outlined'"
                          style="margin-left: 2px;"
                        />
                      </a-button>
                    </div>
                  </div>
                </div>
              </div>
            </a-collapse-panel>
          </a-collapse>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import { getMcpTools, getMcpGroups } from '../agent.api.adapted';
import { message } from 'ant-design-vue';
import type { McpGroupInfo, McpToolInfo } from '../agent.types';

const { t } = useI18n();

const props = withDefaults(
  defineProps<{
    modelValue?: string[];
    placeholder?: string;
    showHint?: boolean;
  }>(),
  {
    showHint: true,
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
  (e: 'change', value: string[]): void;
}>();

const selectedTools = ref<string[]>(props.modelValue || []);
const loading = ref(false);
const maxVisibleTags = 3;

// 数据相关
const serverGroups = ref<McpGroupInfo[]>([]);
const allTools = ref<McpToolInfo[]>([]);

// 工具选择Modal相关
const showToolModal = ref(false);
const activeServerId = ref<string>('');
const tempSelectedTools = ref<string[]>([]);
const searchKeyword = ref('');

// MCP服务器查看Modal相关
const showServerModal = ref(false);
const serverLoading = ref(false);
const activeServerKeys = ref<string[]>([]);

// 工具描述展开状态管理
const expandedToolDescs = ref<Set<string>>(new Set());
const expandedServerToolDescs = ref<Set<string>>(new Set());

// 描述文本最大显示行数（超过此行数显示展开按钮）
const MAX_DESC_LINES = 2;

// 加载数据
const loadData = async () => {
  loading.value = true;
  try {
    const [groups, tools] = await Promise.all([
      getMcpGroups(),
      getMcpTools()
    ]);
    serverGroups.value = groups;
    allTools.value = tools;
    
    // 默认选中第一个服务器
    if (groups.length > 0 && !activeServerId.value) {
      activeServerId.value = groups[0].serverId || groups[0].id;
    }
    
    if (groups.length === 0) {
      // message.warning(t('agent.toolConfig.noServer')); // Optional: suppress warning on load
    }
  } catch (error) {
    console.error('加载数据失败:', error);
    message.error(t('agent.toolConfig.loadingError'));
    serverGroups.value = [];
    allTools.value = [];
  } finally {
    loading.value = false;
  }
};

// 过滤后的服务器列表
const filteredServers = computed(() => {
  if (!searchKeyword.value) return serverGroups.value;
  const keyword = searchKeyword.value.toLowerCase();
  return serverGroups.value.filter(server => {
    const name = (server.name || server.id).toLowerCase();
    const desc = (server.description || '').toLowerCase();
    return name.includes(keyword) || desc.includes(keyword);
  });
});

// 过滤后的工具列表
const filteredTools = computed(() => {
  if (!activeServerId.value) return [];
  let tools = allTools.value.filter(tool => tool.serverId === activeServerId.value);
  
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase();
    tools = tools.filter(tool => {
      const name = tool.name.toLowerCase();
      const desc = (tool.description || '').toLowerCase();
      return name.includes(keyword) || desc.includes(keyword);
    });
  }
  
  return tools;
});

// 选择服务器
const selectServer = (serverId: string) => {
  activeServerId.value = serverId;
};

// 切换工具选择
const toggleTool = (toolName: string) => {
  const index = tempSelectedTools.value.indexOf(toolName);
  if (index > -1) {
    tempSelectedTools.value.splice(index, 1);
  } else {
    tempSelectedTools.value.push(toolName);
  }
};

// 判断工具是否已选中
const isToolSelected = (toolName: string) => {
  return tempSelectedTools.value.includes(toolName);
};

// 全选当前服务器工具
const selectAllTools = () => {
  const toolNames = filteredTools.value.map(tool => tool.name);
  toolNames.forEach(name => {
    if (!tempSelectedTools.value.includes(name)) {
      tempSelectedTools.value.push(name);
    }
  });
};

// 清空当前服务器工具选择
const clearAllTools = () => {
  const toolNames = filteredTools.value.map(tool => tool.name);
  tempSelectedTools.value = tempSelectedTools.value.filter(name => !toolNames.includes(name));
};

// 移除临时工具
const removeTempTool = (toolName: string) => {
  const index = tempSelectedTools.value.indexOf(toolName);
  if (index > -1) {
    tempSelectedTools.value.splice(index, 1);
  }
};

// 清空临时选择
const clearTempSelection = () => {
  tempSelectedTools.value = [];
};

// 移除工具
const removeTool = (toolName: string) => {
  const index = selectedTools.value.indexOf(toolName);
  if (index > -1) {
    selectedTools.value.splice(index, 1);
    handleChange(selectedTools.value);
  }
};

// 获取服务器图标
const getServerIcon = (server: McpGroupInfo): string => {
  const serverId = (server.serverId || server.id).toLowerCase();
  if (serverId.includes('device')) return 'ant-design:mobile-outlined';
  if (serverId.includes('analytics')) return 'ant-design:bar-chart-outlined';
  if (serverId.includes('file')) return 'ant-design:file-outlined';
  if (serverId.includes('aliyun')) return 'ant-design:cloud-outlined';
  return 'ant-design:api-outlined';
};

// 工具Modal打开时
const handleToolModalOpen = () => {
  // 初始化临时选择
  tempSelectedTools.value = [...selectedTools.value];
  // 如果还没有选中服务器，默认选中第一个
  if (!activeServerId.value && serverGroups.value.length > 0) {
    activeServerId.value = serverGroups.value[0].serverId || serverGroups.value[0].id;
  }
};

// 确认选择
const handleConfirm = () => {
  selectedTools.value = [...tempSelectedTools.value];
  handleChange(selectedTools.value);
  showToolModal.value = false;
  message.success(t('agent.toolConfig.selectedCount', { count: selectedTools.value.length }));
};

// 取消选择
const handleCancel = () => {
  showToolModal.value = false;
  tempSelectedTools.value = [...selectedTools.value];
};

// 处理选择变化
const handleChange = (value: string[]) => {
  emit('update:modelValue', value);
  emit('change', value);
};

// 获取服务器头部信息
const getServerHeader = (server: McpGroupInfo) => {
  return `${server.name || server.id} (${server.toolCount} 个工具)`;
};

// 获取指定服务器的工具列表
const getServerTools = (serverId?: string) => {
  if (!serverId) return [];
  return allTools.value.filter(tool => tool.serverId === serverId);
};

// 服务器Modal打开时
const handleServerModalOpen = () => {
  loadServerGroups();
};

// 加载服务器列表（用于查看对话框）
const loadServerGroups = async () => {
  serverLoading.value = true;
  try {
    const [groups, tools] = await Promise.all([
      getMcpGroups(),
      getMcpTools()
    ]);
    serverGroups.value = groups;
    allTools.value = tools;
    // 默认展开第一个服务器
    if (groups.length > 0 && activeServerKeys.value.length === 0) {
      activeServerKeys.value = [groups[0].serverId || groups[0].id];
    }
  } catch (error) {
    console.error('加载服务器列表失败:', error);
    message.error(t('agent.toolConfig.loadingError'));
    serverGroups.value = [];
    allTools.value = [];
  } finally {
    serverLoading.value = false;
  }
};

// 监听外部值变化
watch(
  () => props.modelValue,
  (newValue) => {
    selectedTools.value = newValue || [];
  }
);

// 判断是否应该显示展开按钮（描述超过2行）
// 使用字符数估算：假设每行约50个字符，2行约100个字符
// 实际显示会根据字体大小和容器宽度自动调整
const shouldShowExpandButton = (description: string): boolean => {
  if (!description) return false;
  // 如果描述超过80个字符，很可能超过2行，显示展开按钮
  // 这个阈值可以根据实际UI效果调整
  return description.length > 80;
};

// 工具描述展开/收起（工具选择Modal）
const toggleToolDesc = (toolName: string) => {
  if (expandedToolDescs.value.has(toolName)) {
    expandedToolDescs.value.delete(toolName);
  } else {
    expandedToolDescs.value.add(toolName);
  }
};

const isToolDescExpanded = (toolName: string): boolean => {
  return expandedToolDescs.value.has(toolName);
};

// 工具描述展开/收起（服务器查看Modal）
const toggleServerToolDesc = (toolName: string) => {
  if (expandedServerToolDescs.value.has(toolName)) {
    expandedServerToolDescs.value.delete(toolName);
  } else {
    expandedServerToolDescs.value.add(toolName);
  }
};

const isServerToolDescExpanded = (toolName: string): boolean => {
  return expandedServerToolDescs.value.has(toolName);
};

// 组件挂载时加载数据
onMounted(() => {
  loadData();
});
</script>

<style lang="less">
/* Global styles for Tech Modal in Tool Config */
.tech-modal {
  .ant-modal-content {
    background-color: rgba(15, 23, 42, 0.95) !important;
    backdrop-filter: blur(20px);
    border: 1px solid rgba(59, 130, 246, 0.2);
    box-shadow: 0 0 30px rgba(0, 0, 0, 0.5);
    border-radius: 12px;
  }
  
  .ant-modal-header {
    background: transparent;
    border-bottom: 1px solid rgba(59, 130, 246, 0.2);
    
    .ant-modal-title {
      color: #60A5FA;
      font-family: 'JetBrains Mono', monospace;
      font-weight: 600;
      letter-spacing: 1px;
    }
  }
  
  .ant-modal-close {
    color: rgba(148, 163, 184, 0.8);
    
    &:hover {
      color: #fff;
    }
  }
  
  .ant-modal-body {
    padding: 24px;
  }
}
</style>

<style scoped lang="less">
.agent-tool-config {
  .selector-label {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    font-size: 13px;
    font-family: 'JetBrains Mono', monospace;
    color: #e2e8f0;

    .label-icon {
      color: #60A5FA;
    }

    .help-icon {
      color: rgba(148, 163, 184, 0.6);
      font-size: 12px;
      cursor: help;
      transition: color 0.2s;

      &:hover {
        color: #60A5FA;
      }
    }
    
    .refresh-btn {
      color: rgba(148, 163, 184, 0.6);
      &:hover {
        color: #60A5FA;
      }
    }
  }

  .tool-selector-trigger {
    min-height: 36px;
    padding: 4px 11px;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.3s;
    background: rgba(0, 0, 0, 0.2);

    &:hover {
      border-color: #60A5FA;
      box-shadow: 0 0 8px rgba(59, 130, 246, 0.2);
    }

    .trigger-placeholder {
      display: flex;
      align-items: center;
      gap: 8px;
      color: rgba(148, 163, 184, 0.4);
      font-size: 13px;
      font-family: 'JetBrains Mono', monospace;
      height: 26px;
    }

    .trigger-tags {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
      
      .tech-tag {
        background: rgba(59, 130, 246, 0.15);
        border: 1px solid rgba(59, 130, 246, 0.3);
        color: #e2e8f0;
        font-family: 'JetBrains Mono', monospace;
        font-size: 12px;
        
        :deep(.anticon-close) {
          color: rgba(148, 163, 184, 0.8);
          &:hover {
            color: #fff;
          }
        }
      }
      
      .tech-tag-more {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid rgba(255, 255, 255, 0.1);
        color: #94a3b8;
      }
      
      .edit-btn {
        color: #60A5FA;
        font-family: 'JetBrains Mono', monospace;
        font-size: 12px;
      }
    }
  }

  .tool-hint {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin-top: 10px;
    padding: 8px 12px;
    background: rgba(59, 130, 246, 0.05);
    border: 1px solid rgba(59, 130, 246, 0.1);
    border-radius: 4px;
    font-size: 12px;
    color: #94a3b8;
    line-height: 1.5;

    .anticon {
      color: #60A5FA;
      margin-top: 2px;
      flex-shrink: 0;
    }
  }
}

// Modal Content Styles
.tool-selector-modal {
  .search-bar {
    margin-bottom: 16px;
    
    .tech-input {
      background: rgba(0, 0, 0, 0.2);
      border: 1px solid rgba(59, 130, 246, 0.2);
      border-radius: 4px;
      color: #fff;
      font-family: 'JetBrains Mono', monospace;
      
      :deep(.ant-input) {
        background: transparent;
        color: #fff;
        
        &::placeholder {
          color: rgba(148, 163, 184, 0.4);
        }
      }
      
      &:hover, &:focus, &:focus-within {
        border-color: #60A5FA;
        box-shadow: 0 0 8px rgba(59, 130, 246, 0.2);
      }
      
      .anticon {
        color: rgba(148, 163, 184, 0.6);
      }
    }
  }

  .selector-content {
    display: flex;
    gap: 16px;
    min-height: 400px;
    max-height: 500px;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 6px;
    overflow: hidden;
    background: rgba(0, 0, 0, 0.2);

    .server-list,
    .tool-list {
      display: flex;
      flex-direction: column;
      overflow: hidden;

      .list-header {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 12px 16px;
        background: rgba(59, 130, 246, 0.1);
        border-bottom: 1px solid rgba(59, 130, 246, 0.2);
        font-weight: 600;
        color: #e2e8f0;
        font-size: 13px;
        font-family: 'JetBrains Mono', monospace;

        .count-badge {
          padding: 2px 8px;
          background: rgba(59, 130, 246, 0.2);
          color: #60A5FA;
          border-radius: 12px;
          font-size: 11px;
          font-weight: normal;
        }
        
        .header-actions {
            margin-left: auto;
            display: flex;
            gap: 8px;
          }
          
          .action-btn {
            padding: 0;
            height: auto;
            color: #60A5FA;
            
            &:hover {
              color: #93C5FD;
            }
          }
      }

      .loading-container,
      .empty-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 60px 20px;
        color: #94a3b8;
        font-size: 13px;

        .anticon {
          font-size: 48px;
          margin-bottom: 12px;
          opacity: 0.5;
          color: #60A5FA;
        }
      }
    }

    .server-list {
      width: 280px;
      border-right: 1px solid rgba(59, 130, 246, 0.2);
      background: rgba(0, 0, 0, 0.1);

      .server-items {
        flex: 1;
        overflow-y: auto;
        padding: 8px;
        
        &::-webkit-scrollbar {
          width: 4px;
        }
        &::-webkit-scrollbar-track {
          background: rgba(255, 255, 255, 0.02);
        }
        &::-webkit-scrollbar-thumb {
          background: rgba(59, 130, 246, 0.2);
          border-radius: 2px;
        }

        .server-item {
          padding: 12px;
          margin-bottom: 8px;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.2s;
          background: rgba(255, 255, 255, 0.02);
          border: 1px solid transparent;

          &:hover {
            background: rgba(59, 130, 246, 0.05);
            border-color: rgba(59, 130, 246, 0.3);
          }

          &.active {
            background: rgba(59, 130, 246, 0.15);
            border-color: #60A5FA;
            box-shadow: 0 0 10px rgba(59, 130, 246, 0.1);
          }

          .server-info {
            display: flex;
            align-items: flex-start;
            gap: 12px;

            .server-icon {
              font-size: 18px;
              color: #60A5FA;
              margin-top: 2px;
              flex-shrink: 0;
            }

            .server-details {
              flex: 1;
              min-width: 0;

              .server-name {
                font-weight: 500;
                color: #e2e8f0;
                margin-bottom: 4px;
                font-size: 13px;
                font-family: 'JetBrains Mono', monospace;
              }

              .server-meta {
                display: flex;
                align-items: center;
                gap: 8px;
                font-size: 12px;
                
                .status-dot {
                  width: 6px;
                  height: 6px;
                  border-radius: 50%;
                  background: #475569;
                  
                  &.active {
                    background: #10B981;
                    box-shadow: 0 0 5px #10B981;
                  }
                }

                .tool-count {
                  color: #94a3b8;
                }
              }
            }
          }
        }
      }
    }

    .tool-list {
      flex: 1;
      background: transparent;

      .tool-items {
        flex: 1;
        overflow-y: auto;
        padding: 8px;
        
        &::-webkit-scrollbar {
          width: 4px;
        }
        &::-webkit-scrollbar-track {
          background: rgba(255, 255, 255, 0.02);
        }
        &::-webkit-scrollbar-thumb {
          background: rgba(59, 130, 246, 0.2);
          border-radius: 2px;
        }

        .tool-item {
          padding: 12px;
          margin-bottom: 8px;
          border-radius: 4px;
          border: 1px solid rgba(255, 255, 255, 0.05);
          background: rgba(255, 255, 255, 0.02);
          transition: all 0.2s;

          &:hover {
            border-color: rgba(59, 130, 246, 0.3);
            background: rgba(59, 130, 246, 0.05);
          }

          &.selected {
            border-color: #60A5FA;
            background: rgba(59, 130, 246, 0.1);
          }

          .tech-checkbox {
            width: 100%;
            
            :deep(.ant-checkbox) {
              top: 4px;
            }
            
            :deep(.ant-checkbox-inner) {
              background-color: transparent;
              border-color: rgba(148, 163, 184, 0.5);
            }
            
            :deep(.ant-checkbox-checked .ant-checkbox-inner) {
              background-color: #60A5FA;
              border-color: #60A5FA;
            }
            
            :deep(span) {
              color: #e2e8f0;
            }
          }

          .tool-content {
            flex: 1;

            .tool-name {
              font-weight: 500;
              color: #e2e8f0;
              margin-bottom: 4px;
              font-size: 13px;
              font-family: 'JetBrains Mono', monospace;
            }

            .tool-desc-wrapper {
              position: relative;

              .tool-desc {
                font-size: 12px;
                color: #94a3b8;
                line-height: 1.5;
                word-break: break-word;
                display: -webkit-box;
                -webkit-line-clamp: 2;
                -webkit-box-orient: vertical;
                overflow: hidden;
                text-overflow: ellipsis;
                transition: all 0.3s ease;

                &.expanded {
                  display: block;
                  -webkit-line-clamp: unset;
                }
              }

              .expand-btn {
                margin-top: 4px;
                padding: 0;
                height: auto;
                font-size: 11px;
                color: #60A5FA;
                line-height: 1.5;

                &:hover {
                  color: #93C5FD;
                }
              }
            }
          }
        }
      }
    }
  }

  .selected-tools-bar {
    margin-top: 16px;
    padding: 12px;
    background: rgba(0, 0, 0, 0.2);
    border-radius: 4px;
    border: 1px solid rgba(59, 130, 246, 0.2);

    .selected-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      font-weight: 600;
      color: #e2e8f0;
      font-size: 13px;
      font-family: 'JetBrains Mono', monospace;
      
      .clear-btn {
        color: rgba(148, 163, 184, 0.6);
        &:hover {
          color: #F87171;
        }
      }
    }

    .selected-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      min-height: 24px;
      
      .tech-tag {
        background: rgba(59, 130, 246, 0.15);
        border: 1px solid rgba(59, 130, 246, 0.3);
        color: #e2e8f0;
        font-family: 'JetBrains Mono', monospace;
        font-size: 12px;
        
        :deep(.anticon-close) {
          color: rgba(148, 163, 184, 0.8);
          &:hover {
            color: #fff;
          }
        }
      }

      .empty-hint {
        color: #64748b;
        font-size: 12px;
        font-style: italic;
      }
    }
  }

  .modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid rgba(59, 130, 246, 0.2);
    
    .tech-btn-default {
      background: transparent;
      border: 1px solid rgba(148, 163, 184, 0.3);
      color: #94a3b8;
      
      &:hover {
        border-color: #60A5FA;
        color: #60A5FA;
      }
    }
    
    .tech-btn-primary {
      background: rgba(59, 130, 246, 0.2);
      border: 1px solid rgba(59, 130, 246, 0.5);
      color: #60A5FA;
      
      &:hover {
        background: rgba(59, 130, 246, 0.3);
        border-color: #60A5FA;
        color: #fff;
      }
    }
  }
}

// MCP Server View Styles
.mcp-server-view {
  .server-info {
    margin-bottom: 16px;
    padding: 16px;
    background: rgba(255, 255, 255, 0.02);
    border-radius: 4px;
    border: 1px solid rgba(255, 255, 255, 0.05);

    .info-item {
      margin-bottom: 8px;
      display: flex;
      align-items: center;

      &:last-child {
        margin-bottom: 0;
      }

      .info-label {
        font-weight: 500;
        color: #94a3b8;
        margin-right: 8px;
        min-width: 100px;
        font-family: 'JetBrains Mono', monospace;
        font-size: 13px;
      }

      .info-value {
        color: #e2e8f0;
        flex: 1;
        font-family: 'JetBrains Mono', monospace;
        font-size: 13px;
      }
      
      .status-tag {
        font-family: 'JetBrains Mono', monospace;
      }
    }
  }

  .tech-collapse {
    background: transparent;
    
    :deep(.ant-collapse-item) {
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    }
    
    :deep(.ant-collapse-header) {
      color: #e2e8f0 !important;
      font-family: 'JetBrains Mono', monospace;
    }
    
    :deep(.ant-collapse-content) {
      background: transparent;
      border-top: 1px solid rgba(255, 255, 255, 0.05);
      color: #94a3b8;
    }
  }

  .tools-list {
    margin-top: 16px;

    .tools-header {
      font-weight: 600;
      margin-bottom: 12px;
      color: #e2e8f0;
      font-size: 13px;
      font-family: 'JetBrains Mono', monospace;
    }

    .no-tools {
      text-align: center;
      padding: 20px;
      color: #64748b;
      font-size: 13px;
      font-style: italic;
    }

    .tools-items {
      .tool-item {
        padding: 12px;
        margin-bottom: 8px;
        border: 1px solid rgba(255, 255, 255, 0.05);
        border-radius: 4px;
        background: rgba(255, 255, 255, 0.02);
        transition: all 0.2s;

        &:hover {
          border-color: rgba(59, 130, 246, 0.3);
          background: rgba(59, 130, 246, 0.05);
        }

        .tool-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 4px;

          .tool-name {
            font-weight: 500;
            color: #e2e8f0;
            font-size: 13px;
            font-family: 'JetBrains Mono', monospace;
          }
          
          .status-tag {
            font-family: 'JetBrains Mono', monospace;
          }
        }

        .tool-desc-wrapper {
          margin-top: 4px;

          .tool-desc {
            font-size: 12px;
            color: #94a3b8;
            line-height: 1.5;
            word-break: break-word;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
            text-overflow: ellipsis;
            transition: all 0.3s ease;

            &.expanded {
              display: block;
              -webkit-line-clamp: unset;
            }
          }

          .expand-btn {
            margin-top: 4px;
            padding: 0;
            height: auto;
            font-size: 11px;
            color: #60A5FA;
            line-height: 1.5;

            &:hover {
              color: #93C5FD;
            }
          }
        }
      }
    }
  }
}
</style>