<template>
  <div class="agent-tool-config">
    <div class="selector-label">
      <Icon icon="ant-design:tool-outlined" />
      <span>可用工具</span>
      <a-tooltip title="从配置的MCP服务器获取工具列表">
        <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
      </a-tooltip>
      <a-button
        type="link"
        size="small"
        @click="loadData"
        :loading="loading"
        style="margin-left: 8px; padding: 0; height: auto;"
        title="刷新工具列表"
      >
        <Icon icon="ant-design:reload-outlined" />
      </a-button>
    </div>

    <!-- 工具选择触发区域 -->
    <div class="tool-selector-trigger" @click="showToolModal = true">
      <div v-if="selectedTools.length === 0" class="trigger-placeholder">
        <Icon icon="ant-design:plus-circle-outlined" />
        <span>点击选择工具</span>
      </div>
      <div v-else class="trigger-tags">
        <a-tag
          v-for="(tool, index) in selectedTools.slice(0, maxVisibleTags)"
          :key="tool"
          closable
          @close.stop="removeTool(tool)"
          color="blue"
        >
          {{ tool }}
        </a-tag>
        <a-tag v-if="selectedTools.length > maxVisibleTags" color="default">
          +{{ selectedTools.length - maxVisibleTags }}
        </a-tag>
        <a-button
          type="link"
          size="small"
          @click.stop="showToolModal = true"
          style="padding: 0 4px; height: auto;"
        >
          编辑
        </a-button>
          </div>
        </div>

    <div v-if="showHint" class="tool-hint">
      <Icon icon="ant-design:info-circle-outlined" />
      <span>提示：留空表示允许所有工具，支持通配符（如 device-*）</span>
      <span v-if="serverGroups.length === 0 && !loading" style="color: #ff4d4f; margin-left: 8px;">
        （未找到MCP服务器，请检查配置）
      </span>
    </div>

    <!-- 工具选择Modal -->
    <a-modal
      v-model:open="showToolModal"
      title="选择工具"
      :width="900"
      :footer="null"
      @open="handleToolModalOpen"
    >
      <div class="tool-selector-modal">
        <!-- 搜索框 -->
        <div class="search-bar">
          <a-input
            v-model:value="searchKeyword"
            placeholder="搜索服务器或工具..."
            allow-clear
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
              <span>MCP服务器</span>
              <span class="count-badge">{{ filteredServers.length }}</span>
            </div>
            <div v-if="loading" class="loading-container">
              <a-spin />
            </div>
            <div v-else-if="filteredServers.length === 0" class="empty-container">
              <Icon icon="ant-design:info-circle-outlined" />
              <div>未找到服务器</div>
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
                      <a-tag :color="server.enabled ? 'green' : 'default'" size="small">
                        {{ server.enabled ? '已启用' : '已禁用' }}
                      </a-tag>
                      <span class="tool-count">{{ server.toolCount }} 个工具</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：工具列表 -->
          <div class="tool-list">
            <div class="list-header">
              <span>工具方法</span>
              <span class="count-badge">{{ filteredTools.length }}</span>
              <a-button
                type="link"
                size="small"
                @click="selectAllTools"
                :disabled="filteredTools.length === 0"
                style="margin-left: auto; padding: 0 4px;"
              >
                全选
              </a-button>
              <a-button
                type="link"
                size="small"
                @click="clearAllTools"
                :disabled="filteredTools.length === 0"
                style="padding: 0 4px;"
              >
                清空
              </a-button>
            </div>
            <div v-if="!activeServerId" class="empty-container">
              <Icon icon="ant-design:arrow-left-outlined" />
              <div>请先选择左侧的MCP服务器</div>
            </div>
            <div v-else-if="filteredTools.length === 0" class="empty-container">
              <Icon icon="ant-design:tool-outlined" />
              <div>该服务器暂无工具</div>
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
                        {{ isToolDescExpanded(tool.name) ? '收起' : '展开' }}
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
            <span>已选工具 ({{ tempSelectedTools.length }})</span>
            <a-button
              type="link"
              size="small"
              @click="clearTempSelection"
              :disabled="tempSelectedTools.length === 0"
            >
              清空
            </a-button>
          </div>
          <div class="selected-tags">
            <a-tag
              v-for="tool in tempSelectedTools"
              :key="tool"
              closable
              @close="removeTempTool(tool)"
              color="blue"
            >
              {{ tool }}
            </a-tag>
            <span v-if="tempSelectedTools.length === 0" class="empty-hint">未选择任何工具</span>
          </div>
        </div>

        <!-- 底部操作按钮 -->
        <div class="modal-footer">
          <a-button @click="handleCancel">取消</a-button>
          <a-button type="primary" @click="handleConfirm">确认</a-button>
        </div>
      </div>
    </a-modal>

    <!-- MCP服务器查看Modal（保留原有功能） -->
    <a-modal
      v-model:open="showServerModal"
      title="MCP服务器配置"
      :width="800"
      :footer="null"
      @open="handleServerModalOpen"
    >
      <div class="mcp-server-view">
        <div v-if="serverLoading" style="text-align: center; padding: 40px;">
          <a-spin size="large" />
          <div style="margin-top: 16px; color: #8c8c8c;">加载中...</div>
        </div>
        <div v-else-if="serverGroups.length === 0" style="text-align: center; padding: 40px; color: #8c8c8c;">
          <Icon icon="ant-design:info-circle-outlined" style="font-size: 48px; margin-bottom: 16px; opacity: 0.5;" />
          <div>未配置MCP服务器</div>
          <div style="margin-top: 8px; font-size: 12px;">请在后端配置文件 mcp.json 中配置MCP服务器</div>
        </div>
        <div v-else>
          <a-collapse v-model:activeKey="activeServerKeys" :bordered="false">
            <a-collapse-panel
              v-for="server in serverGroups"
              :key="server.serverId || server.id"
              :header="getServerHeader(server)"
            >
              <div class="server-info">
                <div class="info-item">
                  <span class="info-label">服务器ID:</span>
                  <span class="info-value">{{ server.serverId || server.id }}</span>
                </div>
                <div class="info-item" v-if="server.connectionType">
                  <span class="info-label">连接类型:</span>
                  <span class="info-value">{{ server.connectionType }}</span>
                </div>
                <div class="info-item" v-if="server.description">
                  <span class="info-label">描述:</span>
                  <span class="info-value">{{ server.description }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">状态:</span>
                  <a-tag :color="server.enabled ? 'green' : 'default'" size="small">
                    {{ server.enabled ? '已启用' : '已禁用' }}
                  </a-tag>
                </div>
              </div>
              <div class="tools-list">
                <div class="tools-header">
                  <span>工具列表 ({{ getServerTools(server.serverId || server.id).length }})</span>
                </div>
                <div v-if="getServerTools(server.serverId || server.id).length === 0" class="no-tools">
                  该服务器暂无工具
                </div>
                <div v-else class="tools-items">
                  <div
                    v-for="tool in getServerTools(server.serverId || server.id)"
                    :key="tool.name"
                    class="tool-item"
                  >
                    <div class="tool-header">
                      <span class="tool-name">{{ tool.name }}</span>
                      <a-tag v-if="tool.enabled" color="green" size="small">启用</a-tag>
                      <a-tag v-else color="default" size="small">禁用</a-tag>
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
                        {{ isServerToolDescExpanded(tool.name) ? '收起' : '展开' }}
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
import { Icon } from '@/components/Icon';
import { getMcpTools, getMcpGroups } from '../agent.api.adapted';
import { message } from 'ant-design-vue';
import type { McpGroupInfo, McpToolInfo } from '../agent.types';

const props = withDefaults(
  defineProps<{
    modelValue?: string[];
    placeholder?: string;
    showHint?: boolean;
  }>(),
  {
    placeholder: '选择或输入工具名称',
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
      message.warning('未找到MCP服务器，请检查配置');
    }
  } catch (error) {
    console.error('加载数据失败:', error);
    message.error('加载数据失败，请检查后端服务');
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
  message.success(`已选择 ${selectedTools.value.length} 个工具`);
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
    message.error('加载服务器列表失败');
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

<style scoped lang="less">
.agent-tool-config {
  .selector-label {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 500;
    color: #262626;

    .help-icon {
      color: #8c8c8c;
      font-size: 14px;
      cursor: help;
    }
  }

  .tool-selector-trigger {
    min-height: 32px;
    padding: 4px 11px;
    border: 1px solid #d9d9d9;
      border-radius: 6px;
    cursor: pointer;
    transition: all 0.3s;
    background: #fff;

    &:hover {
      border-color: #40a9ff;
    }

    .trigger-placeholder {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #bfbfbf;
      font-size: 14px;
    }

    .trigger-tags {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }
  }

  .tool-hint {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin-top: 8px;
    padding: 8px 12px;
    background: #f0f2f5;
    border-radius: 6px;
    font-size: 12px;
    color: #595959;
    line-height: 1.5;

    .anticon {
      color: #1890ff;
      margin-top: 2px;
      flex-shrink: 0;
    }
  }
}

// 工具选择Modal样式
.tool-selector-modal {
  .search-bar {
    margin-bottom: 16px;
  }

  .selector-content {
    display: flex;
    gap: 16px;
    min-height: 400px;
    max-height: 500px;
    border: 1px solid #e8e8e8;
    border-radius: 6px;
    overflow: hidden;

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
        background: #fafafa;
        border-bottom: 1px solid #e8e8e8;
        font-weight: 500;
        color: #262626;
        font-size: 14px;

        .count-badge {
          padding: 2px 8px;
          background: #e6f7ff;
          color: #1890ff;
          border-radius: 12px;
          font-size: 12px;
          font-weight: normal;
        }
      }

      .loading-container,
      .empty-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 60px 20px;
        color: #8c8c8c;
        font-size: 14px;

        .anticon {
          font-size: 48px;
          margin-bottom: 12px;
          opacity: 0.5;
        }
      }
    }

    .server-list {
      width: 300px;
      border-right: 1px solid #e8e8e8;
      background: #fafafa;

      .server-items {
        flex: 1;
        overflow-y: auto;
        padding: 8px;

        .server-item {
          padding: 12px;
          margin-bottom: 8px;
          border-radius: 6px;
          cursor: pointer;
          transition: all 0.2s;
          background: #fff;
          border: 1px solid transparent;

          &:hover {
            background: #e6f7ff;
            border-color: #91d5ff;
          }

          &.active {
            background: #bae7ff;
            border-color: #40a9ff;
            box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
          }

          .server-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;

            .server-icon {
    font-size: 20px;
              color: #1890ff;
              margin-top: 2px;
    flex-shrink: 0;
  }

            .server-details {
    flex: 1;
    min-width: 0;

              .server-name {
                font-weight: 500;
                color: #262626;
                margin-bottom: 6px;
                font-size: 14px;
              }

              .server-meta {
                display: flex;
                align-items: center;
                gap: 8px;
                font-size: 12px;

                .tool-count {
                  color: #8c8c8c;
                }
              }
            }
          }
        }
      }
    }

    .tool-list {
      flex: 1;
      background: #fff;

      .tool-items {
        flex: 1;
        overflow-y: auto;
        padding: 8px;

        .tool-item {
          padding: 12px;
          margin-bottom: 8px;
          border-radius: 6px;
          border: 1px solid #e8e8e8;
          transition: all 0.2s;

          &:hover {
            border-color: #91d5ff;
            background: #f0f7ff;
          }

          &.selected {
            border-color: #40a9ff;
            background: #e6f7ff;
          }

          :deep(.ant-checkbox-wrapper) {
            width: 100%;

            .ant-checkbox {
              margin-right: 12px;
            }
          }

          .tool-content {
            flex: 1;

    .tool-name {
              font-weight: 500;
              color: #262626;
              margin-bottom: 4px;
              font-size: 14px;
            }

            .tool-desc-wrapper {
              position: relative;

              .tool-desc {
                font-size: 12px;
                color: #8c8c8c;
                line-height: 1.5;
                word-break: break-word;
                // 默认显示2行，超出省略
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
                font-size: 12px;
                color: #1890ff;
                line-height: 1.5;

                &:hover {
                  color: #40a9ff;
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
    background: #f5f5f5;
    border-radius: 6px;
    border: 1px solid #e8e8e8;

    .selected-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      font-weight: 500;
      color: #262626;
      font-size: 14px;
    }

    .selected-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      min-height: 24px;

      .empty-hint {
        color: #8c8c8c;
        font-size: 12px;
      }
    }
  }

  .modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid #e8e8e8;
  }
}

// MCP服务器查看Modal样式
.mcp-server-view {
  .server-info {
    margin-bottom: 16px;
    padding: 12px;
    background: #f5f5f5;
    border-radius: 4px;

    .info-item {
      margin-bottom: 8px;
      display: flex;
      align-items: center;

      &:last-child {
        margin-bottom: 0;
      }

      .info-label {
        font-weight: 500;
        color: #666;
        margin-right: 8px;
        min-width: 80px;
      }

      .info-value {
        color: #262626;
        flex: 1;
      }
    }
  }

  .tools-list {
    margin-top: 16px;

    .tools-header {
      font-weight: 500;
      margin-bottom: 12px;
      color: #262626;
      font-size: 14px;
    }

    .no-tools {
      text-align: center;
      padding: 20px;
      color: #999;
      font-size: 13px;
    }

    .tools-items {
      .tool-item {
        padding: 12px;
        margin-bottom: 8px;
        border: 1px solid #e8e8e8;
        border-radius: 4px;
        background: #fafafa;
        transition: all 0.2s;

        &:hover {
          border-color: #1890ff;
          background: #f0f7ff;
        }

        &:last-child {
          margin-bottom: 0;
        }

        .tool-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 4px;

          .tool-name {
            font-weight: 500;
            color: #262626;
            font-size: 14px;
          }
        }

        .tool-desc-wrapper {
          margin-top: 4px;

    .tool-desc {
      font-size: 12px;
      color: #8c8c8c;
            line-height: 1.5;
            word-break: break-word;
            // 默认显示2行，超出省略
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
            font-size: 12px;
            color: #1890ff;
            line-height: 1.5;

            &:hover {
              color: #40a9ff;
            }
          }
        }
      }
    }
  }
}
</style>
