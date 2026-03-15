<template>
  <div class="agent-tool-config">

    <!-- ── 弹窗模式：触发区域 + 提示 ── -->
    <template v-if="!inlineMode">
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
      <div class="tool-selector-trigger" @click="openToolModal">
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
            @click.stop="openToolModal"
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
    </template>

    <!-- ── 弹窗模式：Modal ── -->
    <a-modal
      v-if="!inlineMode"
      v-model:open="showToolModal"
      :title="t('agent.toolConfig.modalTitle')"
      :width="900"
      :footer="null"
      class="tech-modal"
    >
      <div class="tool-selector-modal">
        <!-- Tab 切换 -->
        <a-tabs v-model:activeKey="activeTab" class="tool-tabs">

          <!-- ── 系统工具 Tab ─────────────────────────────── -->
          <a-tab-pane key="system" :tab="t('agent.toolConfig.systemToolsTab')">
            <div class="system-tools-pane">
              <!-- 搜索框 -->
              <div class="search-bar">
                <a-input
                  v-model:value="systemSearchKeyword"
                  :placeholder="t('agent.toolConfig.searchPlaceholder')"
                  allow-clear
                  class="tech-input"
                >
                  <template #prefix>
                    <Icon icon="ant-design:search-outlined" />
                  </template>
                </a-input>
              </div>
              <!-- 列表头 -->
              <div class="list-header">
                <span>{{ t('agent.toolConfig.toolList') }}</span>
                <span class="count-badge">{{ filteredSystemTools.length }}</span>
                <div class="header-actions">
                  <a-button
                    type="link"
                    size="small"
                    @click="selectAllSystemTools"
                    :disabled="filteredSystemTools.length === 0"
                    class="action-btn"
                  >
                    {{ t('common.selectAll') }}
                  </a-button>
                  <a-button
                    type="link"
                    size="small"
                    @click="clearAllSystemTools"
                    :disabled="filteredSystemTools.length === 0"
                    class="action-btn"
                  >
                    {{ t('common.clear') }}
                  </a-button>
                </div>
              </div>
              <!-- 系统工具列表 -->
              <div v-if="loading" class="loading-container">
                <a-spin />
              </div>
              <div v-else-if="filteredSystemTools.length === 0" class="empty-container">
                <Icon icon="ant-design:tool-outlined" />
                <div>{{ t('agent.toolConfig.noToolsInServer') }}</div>
              </div>
              <div v-else class="system-tool-items">
                <div
                  v-for="tool in filteredSystemTools"
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
                        <div class="tool-desc" :class="{ expanded: isToolDescExpanded(tool.name) }">
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
          </a-tab-pane>

          <!-- ── MCP 工具 Tab ────────────────────────────── -->
          <a-tab-pane key="mcp" :tab="t('agent.toolConfig.mcpToolsTab')">
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
                    :key="server.id"
                    class="server-item"
                    :class="{ active: activeServerId === server.id }"
                    @click="selectServer(server.id)"
                  >
                    <div class="server-info">
                      <Icon :icon="getServerIcon(server)" class="server-icon" />
                      <div class="server-details">
                        <div class="server-name">
                          {{ server.name }}
                          <a-tag v-if="server.scope === 1" color="blue" style="margin-left:4px;font-size:10px;padding:0 4px;">PERSONAL</a-tag>
                        </div>
                        <div class="server-meta">
                          <span class="status-dot" :class="{ active: server.enabled }"></span>
                          <span class="tool-count">{{ server.toolCount ?? 0 }} tools</span>
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
                <div v-else-if="loadingServerIds[activeServerId]" class="loading-container">
                  <a-spin />
                  <div style="margin-top:8px;font-size:12px;color:#475569;">{{ t('common.loading') }}</div>
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
          </a-tab-pane>

        </a-tabs>

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

        <!-- 底部操作按钮（弹窗模式） -->
        <div class="modal-footer">
          <a-button @click="handleCancel" class="tech-btn-default">{{ t('common.cancel') }}</a-button>
          <a-button type="primary" @click="handleConfirm" class="tech-btn-primary">{{ t('common.confirm') }}</a-button>
        </div>
      </div>
    </a-modal>

    <!-- ── 内嵌模式：直接渲染工具选择器内容 ── -->
    <div v-if="inlineMode" class="tool-selector-modal inline-mode">
      <!-- 加载刷新按钮 -->
      <div class="inline-toolbar">
        <a-button
          type="link"
          size="small"
          @click="loadData"
          :loading="loading"
          class="refresh-btn"
        >
          <Icon icon="ant-design:reload-outlined" />
          {{ t('agent.toolConfig.refresh') }}
        </a-button>
      </div>

      <!-- Tab 切换 -->
      <a-tabs v-model:activeKey="activeTab" class="tool-tabs">

        <!-- ── 系统工具 Tab ─────────────────────────────── -->
        <a-tab-pane key="system" :tab="t('agent.toolConfig.systemToolsTab')">
          <div class="system-tools-pane">
            <div class="search-bar">
              <a-input
                v-model:value="systemSearchKeyword"
                :placeholder="t('agent.toolConfig.searchPlaceholder')"
                allow-clear
                class="tech-input"
              >
                <template #prefix>
                  <Icon icon="ant-design:search-outlined" />
                </template>
              </a-input>
            </div>
            <div class="list-header">
              <span>{{ t('agent.toolConfig.toolList') }}</span>
              <span class="count-badge">{{ filteredSystemTools.length }}</span>
              <div class="header-actions">
                <a-button
                  type="link"
                  size="small"
                  @click="selectAllSystemToolsInline"
                  :disabled="filteredSystemTools.length === 0"
                  class="action-btn"
                >
                  {{ t('common.selectAll') }}
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  @click="clearAllSystemToolsInline"
                  :disabled="filteredSystemTools.length === 0"
                  class="action-btn"
                >
                  {{ t('common.clear') }}
                </a-button>
              </div>
            </div>
            <div v-if="loading" class="loading-container">
              <a-spin />
            </div>
            <div v-else-if="filteredSystemTools.length === 0" class="empty-container">
              <Icon icon="ant-design:tool-outlined" />
              <div>{{ t('agent.toolConfig.noToolsInServer') }}</div>
            </div>
            <div v-else class="system-tool-items">
              <div
                v-for="tool in filteredSystemTools"
                :key="tool.name"
                class="tool-item"
                :class="{ selected: isToolSelectedInline(tool.name) }"
              >
                <a-checkbox
                  :checked="isToolSelectedInline(tool.name)"
                  @change="toggleToolInline(tool.name)"
                  class="tech-checkbox"
                >
                  <div class="tool-content">
                    <div class="tool-name">{{ tool.name }}</div>
                    <div v-if="tool.description" class="tool-desc-wrapper">
                      <div class="tool-desc" :class="{ expanded: isToolDescExpanded(tool.name) }">
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
        </a-tab-pane>

        <!-- ── MCP 工具 Tab ────────────────────────────── -->
        <a-tab-pane key="mcp" :tab="t('agent.toolConfig.mcpToolsTab')">
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
          <div class="selector-content">
            <div class="server-list">
              <div class="list-header">
                <span>{{ t('agent.toolConfig.serverList') }}</span>
                <span class="count-badge">{{ filteredServers.length }}</span>
              </div>
              <div v-if="loading" class="loading-container"><a-spin /></div>
              <div v-else-if="filteredServers.length === 0" class="empty-container">
                <Icon icon="ant-design:info-circle-outlined" />
                <div>{{ t('agent.toolConfig.noServerFound') }}</div>
              </div>
              <div v-else class="server-items">
                <div
                  v-for="server in filteredServers"
                  :key="server.id"
                  class="server-item"
                  :class="{ active: activeServerId === server.id }"
                  @click="selectServer(server.id)"
                >
                  <div class="server-info">
                    <Icon :icon="getServerIcon(server)" class="server-icon" />
                    <div class="server-details">
                      <div class="server-name">
                        {{ server.name }}
                        <a-tag v-if="server.scope === 1" color="blue" style="margin-left:4px;font-size:10px;padding:0 4px;">PERSONAL</a-tag>
                      </div>
                      <div class="server-meta">
                        <span class="status-dot" :class="{ active: server.enabled }"></span>
                        <span class="tool-count">{{ server.toolCount ?? 0 }} tools</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="tool-list">
              <div class="list-header">
                <span>{{ t('agent.toolConfig.toolList') }}</span>
                <span class="count-badge">{{ filteredTools.length }}</span>
                <div class="header-actions">
                  <a-button type="link" size="small" @click="selectAllToolsInline" :disabled="filteredTools.length === 0" class="action-btn">{{ t('common.selectAll') }}</a-button>
                  <a-button type="link" size="small" @click="clearAllToolsInline" :disabled="filteredTools.length === 0" class="action-btn">{{ t('common.clear') }}</a-button>
                </div>
              </div>
              <div v-if="!activeServerId" class="empty-container">
                <Icon icon="ant-design:arrow-left-outlined" />
                <div>{{ t('agent.toolConfig.selectServerFirst') }}</div>
              </div>
              <div v-else-if="loadingServerIds[activeServerId]" class="loading-container">
                <a-spin />
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
                  :class="{ selected: isToolSelectedInline(tool.name) }"
                >
                  <a-checkbox
                    :checked="isToolSelectedInline(tool.name)"
                    @change="toggleToolInline(tool.name)"
                    class="tech-checkbox"
                  >
                    <div class="tool-content">
                      <div class="tool-name">{{ tool.name }}</div>
                      <div v-if="tool.description" class="tool-desc-wrapper">
                        <div class="tool-desc" :class="{ expanded: isToolDescExpanded(tool.name) }">
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
        </a-tab-pane>

      </a-tabs>

      <!-- 已选工具显示 -->
      <div class="selected-tools-bar">
        <div class="selected-header">
          <span>{{ t('agent.toolConfig.selectedTools') }} ({{ selectedTools.length }})</span>
          <a-button
            type="link"
            size="small"
            @click="clearAllInline"
            :disabled="selectedTools.length === 0"
            class="clear-btn"
          >
            {{ t('common.clear') }}
          </a-button>
        </div>
        <div class="selected-tags">
          <a-tag
            v-for="tool in selectedTools"
            :key="tool"
            closable
            @close="removeTool(tool)"
            class="tech-tag"
          >
            {{ tool }}
          </a-tag>
          <span v-if="selectedTools.length === 0" class="empty-hint">{{ t('agent.toolConfig.noToolSelected') }}</span>
        </div>
      </div>
    </div>

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
              :key="server.id"
              :header="getServerHeader(server)"
            >
              <div class="server-info">
                <div class="info-item">
                  <span class="info-label">ID:</span>
                  <span class="info-value">{{ server.id }}</span>
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
                  <span>{{ t('agent.toolConfig.toolListTitle') }} ({{ getServerTools(server.id).length }})</span>
                </div>
                <div v-if="getServerTools(server.id).length === 0" class="no-tools">
                  {{ t('agent.toolConfig.noToolsInServer') }}
                </div>
                <div v-else class="tools-items">
                  <div
                    v-for="tool in getServerTools(server.id)"
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
import { getMcpServers, getMcpServerTools, prefetchPersonalMcpTools, getAvailableSystemToolsForAgent } from '../agent.api';
import { getMcpSecret } from '@/utils/mcpSecretStore';
import { message } from 'ant-design-vue';
import type { McpServerInfo, McpToolInfo, SystemToolInfo } from '../agent.types';

// 系统工具组的虚拟 serverId（与 AgentChat.vue 保持一致）
const SYSTEM_TOOLS_SERVER_ID = '__system_tools__';

const { t } = useI18n();

const props = withDefaults(
  defineProps<{
    modelValue?: string[];
    placeholder?: string;
    showHint?: boolean;
    /** 内嵌模式：直接平铺渲染，不使用弹窗。适合 Agent 管理编辑页等表单场景。 */
    inlineMode?: boolean;
  }>(),
  {
    showHint: true,
    inlineMode: false,
  }
);

// 工具元数据：工具名 → serverId（系统工具用 SYSTEM_TOOLS_SERVER_ID）
export type ToolsMetaMap = Record<string, string>;

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
  (e: 'change', value: string[]): void;
  (e: 'tools-meta-change', meta: ToolsMetaMap): void;
}>();

const selectedTools = ref<string[]>(props.modelValue || []);
const loading = ref(false);
const maxVisibleTags = 3;

// MCP 服务器 + 工具数据
const serverGroups = ref<McpServerInfo[]>([]);
const allTools = ref<McpToolInfo[]>([]);

// 系统工具数据（独立存储，不混入 serverGroups）
const systemToolsList = ref<SystemToolInfo[]>([]);

// 工具选择Modal相关
const showToolModal = ref(false);
// 当前激活的 tab: 'system' | 'mcp'
const activeTab = ref<'system' | 'mcp'>('system');
const activeServerId = ref<string>('');
const tempSelectedTools = ref<string[]>([]);
// 搜索关键词（MCP tab 用）
const searchKeyword = ref('');
// 系统工具搜索关键词
const systemSearchKeyword = ref('');
// 记录正在加载工具的服务器 ID 集合（用 Record 保证 Vue 响应式追踪）
const loadingServerIds = ref<Record<string, boolean>>({});

// MCP服务器查看Modal相关
const showServerModal = ref(false);
// serverLoading 仅用于服务器查看 Modal，不与主 loading 共用
const serverLoading = ref(false);
const activeServerKeys = ref<string[]>([]);

// 工具描述展开状态管理
const expandedToolDescs = ref<Set<string>>(new Set());
const expandedServerToolDescs = ref<Set<string>>(new Set());

// 从 McpServerInfo 加载单个服务器的工具列表
const loadServerTools = async (server: McpServerInfo): Promise<McpToolInfo[]> => {
  if (server.scope === 1) {
    const localHeaders: Record<string, string> = {};
    for (const key of Object.keys(server.authHeaders || {})) {
      const val = getMcpSecret(server.id, key);
      if (val) localHeaders[key] = val;
    }
    const schemas = await prefetchPersonalMcpTools(server, localHeaders);
    return schemas.map((s) => ({
      name: s.name,
      description: s.description || '',
      enabled: true,
      serverId: server.id,
      personal: true,
      connectionType: server.connectionType,
    }));
  }
  const tools = await getMcpServerTools(server.id);
  // 后端已设置 serverId，双重保险再覆盖一次
  return tools.map((t) => ({ ...t, serverId: server.id }));
};

/**
 * 内部数据加载核心：并发拉取 MCP 服务器列表 + 系统工具列表。
 * 系统工具独立存入 systemToolsList，MCP 工具存入 serverGroups / allTools。
 * 不操作 loading / serverLoading，由外部调用方控制。
 */
const fetchAllData = async () => {
  // 并发拉取 MCP 服务器列表 + 系统工具列表
  const [servers, sysTools] = await Promise.all([
    getMcpServers(),
    getAvailableSystemToolsForAgent().catch(() => []),
  ]);

  // 系统工具独立存储
  systemToolsList.value = sysTools;

  const sorted = [...servers].sort((a, b) => a.scope - b.scope);
  serverGroups.value = sorted;

  // 标记所有 MCP 服务器为 loading 中
  loadingServerIds.value = Object.fromEntries(sorted.map((s) => [s.id, true]));

  const toolResults = await Promise.allSettled(
    sorted.map(async (s) => {
      try {
        return await loadServerTools(s);
      } finally {
        delete loadingServerIds.value[s.id];
      }
    })
  );

  const mcpTools = toolResults.flatMap((r) => (r.status === 'fulfilled' ? r.value : []));
  allTools.value = mcpTools;

  // 回填 toolCount
  serverGroups.value = sorted.map((s) => ({
    ...s,
    toolCount: mcpTools.filter((t) => t.serverId === s.id).length,
  }));

  // 默认选中第一个 MCP 服务器（仅首次）
  if (sorted.length > 0 && !activeServerId.value) {
    activeServerId.value = sorted[0].id;
  }
};

// 主加载（工具选择 Modal 刷新按钮 + onMounted 触发）
const loadData = async () => {
  loading.value = true;
  try {
    await fetchAllData();
  } catch (error) {
    console.error('加载数据失败:', error);
    message.error(t('agent.toolConfig.loadingError'));
    serverGroups.value = [];
    allTools.value = [];
  } finally {
    loading.value = false;
  }
};

// 服务器查看 Modal 专用加载（独立 loading，不影响主 loading）
const loadServerGroups = async () => {
  serverLoading.value = true;
  try {
    await fetchAllData();
    if (serverGroups.value.length > 0 && activeServerKeys.value.length === 0) {
      activeServerKeys.value = [serverGroups.value[0].id];
    }
  } catch (error) {
    console.error('加载服务器列表失败:', error);
    message.error(t('agent.toolConfig.loadingError'));
  } finally {
    serverLoading.value = false;
  }
};

// 过滤后的系统工具列表
const filteredSystemTools = computed(() => {
  if (!systemSearchKeyword.value) return systemToolsList.value;
  const keyword = systemSearchKeyword.value.toLowerCase();
  return systemToolsList.value.filter((tool) => {
    const name = tool.name.toLowerCase();
    const desc = (tool.description || '').toLowerCase();
    return name.includes(keyword) || desc.includes(keyword);
  });
});

// 过滤后的 MCP 服务器列表
const filteredServers = computed(() => {
  if (!searchKeyword.value) return serverGroups.value;
  const keyword = searchKeyword.value.toLowerCase();
  return serverGroups.value.filter(server => {
    const name = (server.name || server.id).toLowerCase();
    const desc = (server.description || '').toLowerCase();
    return name.includes(keyword) || desc.includes(keyword);
  });
});

// 过滤后的 MCP 工具列表
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
const getServerIcon = (server: McpServerInfo): string => {
  if (server.id === SYSTEM_TOOLS_SERVER_ID) return 'ant-design:setting-outlined';
  if (server.scope === 1) return 'ant-design:user-outlined';
  const id = server.id.toLowerCase();
  if (id.includes('device')) return 'ant-design:mobile-outlined';
  if (id.includes('analytics')) return 'ant-design:bar-chart-outlined';
  if (id.includes('file')) return 'ant-design:file-outlined';
  if (id.includes('aliyun')) return 'ant-design:cloud-outlined';
  return 'ant-design:api-outlined';
};

// 全选系统工具
const selectAllSystemTools = () => {
  for (const tool of filteredSystemTools.value) {
    if (!tempSelectedTools.value.includes(tool.name)) {
      tempSelectedTools.value.push(tool.name);
    }
  }
};

// 清空系统工具选择
const clearAllSystemTools = () => {
  const names = new Set(filteredSystemTools.value.map((t) => t.name));
  tempSelectedTools.value = tempSelectedTools.value.filter((n) => !names.has(n));
};

// 打开工具选择弹窗（先同步 tempSelectedTools，再打开弹窗，修复打钩 bug）
const openToolModal = () => {
  tempSelectedTools.value = [...selectedTools.value];
  // 若 MCP 无已选服务器，默认选中第一个
  if (!activeServerId.value && serverGroups.value.length > 0) {
    activeServerId.value = serverGroups.value[0].id;
  }
  // 有系统工具时默认显示系统工具 tab，否则显示 MCP tab
  activeTab.value = systemToolsList.value.length > 0 ? 'system' : 'mcp';
  showToolModal.value = true;
};

// 构建并 emit 工具元数据（工具名 → serverId）
// 系统工具用 SYSTEM_TOOLS_SERVER_ID，MCP 工具从 allTools 查找
const emitToolsMeta = (toolNames: string[]) => {
  const meta: ToolsMetaMap = {};
  const sysNames = new Set(systemToolsList.value.map((t) => t.name));
  for (const name of toolNames) {
    if (sysNames.has(name)) {
      meta[name] = SYSTEM_TOOLS_SERVER_ID;
    } else {
      const tool = allTools.value.find((t) => t.name === name);
      if (tool?.serverId) {
        meta[name] = tool.serverId;
      }
    }
  }
  emit('tools-meta-change', meta);
};

// 确认选择
const handleConfirm = () => {
  selectedTools.value = [...tempSelectedTools.value];
  handleChange(selectedTools.value);
  emitToolsMeta(selectedTools.value);
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
const getServerHeader = (server: McpServerInfo) => {
  const scopeLabel = server.scope === 1 ? ' [个人]' : ' [云端]';
  return `${server.name}${scopeLabel} (${server.toolCount ?? 0} 个工具)`;
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


// 监听外部值变化
watch(
  () => props.modelValue,
  (newValue) => {
    selectedTools.value = newValue || [];
    // 外部值变化时同步 emit 元数据（确保父组件元数据与工具列表一致）
    if (allTools.value.length > 0) {
      emitToolsMeta(newValue || []);
    }
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

// ─── 内嵌模式辅助函数 ────────────────────────────────────────────────────────

// 内嵌模式：判断工具是否已选中（直接操作 selectedTools）
const isToolSelectedInline = (toolName: string) => {
  return selectedTools.value.includes(toolName);
};

// 内嵌模式：切换工具选中状态（即时生效，无需确认）
const toggleToolInline = (toolName: string) => {
  const index = selectedTools.value.indexOf(toolName);
  if (index > -1) {
    selectedTools.value.splice(index, 1);
  } else {
    selectedTools.value.push(toolName);
  }
  handleChange(selectedTools.value);
  emitToolsMeta(selectedTools.value);
};

// 内嵌模式：全选当前 MCP 服务器工具
const selectAllToolsInline = () => {
  for (const tool of filteredTools.value) {
    if (!selectedTools.value.includes(tool.name)) {
      selectedTools.value.push(tool.name);
    }
  }
  handleChange(selectedTools.value);
  emitToolsMeta(selectedTools.value);
};

// 内嵌模式：清空当前 MCP 服务器工具
const clearAllToolsInline = () => {
  const names = new Set(filteredTools.value.map((t) => t.name));
  selectedTools.value = selectedTools.value.filter((n) => !names.has(n));
  handleChange(selectedTools.value);
  emitToolsMeta(selectedTools.value);
};

// 内嵌模式：全选系统工具
const selectAllSystemToolsInline = () => {
  for (const tool of filteredSystemTools.value) {
    if (!selectedTools.value.includes(tool.name)) {
      selectedTools.value.push(tool.name);
    }
  }
  handleChange(selectedTools.value);
  emitToolsMeta(selectedTools.value);
};

// 内嵌模式：清空系统工具
const clearAllSystemToolsInline = () => {
  const names = new Set(filteredSystemTools.value.map((t) => t.name));
  selectedTools.value = selectedTools.value.filter((n) => !names.has(n));
  handleChange(selectedTools.value);
  emitToolsMeta(selectedTools.value);
};

// 内嵌模式：清空所有已选工具
const clearAllInline = () => {
  selectedTools.value = [];
  handleChange(selectedTools.value);
  emitToolsMeta(selectedTools.value);
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
  // Tab 样式
  .tool-tabs {
    :deep(.ant-tabs-nav) {
      margin-bottom: 0;
      &::before { border-bottom-color: rgba(59, 130, 246, 0.2); }
    }
    :deep(.ant-tabs-tab) {
      color: #94a3b8;
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
      &:hover { color: #60A5FA; }
    }
    :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) { color: #60A5FA; }
    :deep(.ant-tabs-ink-bar) { background: #60A5FA; }
    :deep(.ant-tabs-content-holder) { padding-top: 16px; }
  }

  // ── 共用搜索框 ───────────────────────────────────────────────────────────
  .search-bar {
    margin-bottom: 12px;

    .tech-input {
      background: rgba(0, 0, 0, 0.2);
      border: 1px solid rgba(59, 130, 246, 0.2);
      border-radius: 4px;
      color: #fff;
      font-family: 'JetBrains Mono', monospace;

      :deep(.ant-input) {
        background: transparent;
        color: #fff;
        &::placeholder { color: rgba(148, 163, 184, 0.4); }
      }

      &:hover, &:focus, &:focus-within {
        border-color: #60A5FA;
        box-shadow: 0 0 8px rgba(59, 130, 246, 0.2);
      }

      .anticon { color: rgba(148, 163, 184, 0.6); }
    }
  }

  // ── 共用列表头（系统工具 pane 和 MCP 右侧均使用） ─────────────────────────
  .list-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    background: rgba(59, 130, 246, 0.1);
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 6px 6px 0 0;
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
      font-size: 12px;
      &:hover { color: #93C5FD; }
    }
  }

  // ── 共用 loading / empty 状态 ─────────────────────────────────────────────
  .loading-container,
  .empty-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 60px 20px;
    color: #94a3b8;
    font-size: 13px;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-top: none;
    border-radius: 0 0 6px 6px;
    background: rgba(0, 0, 0, 0.2);

    .anticon {
      font-size: 40px;
      margin-bottom: 12px;
      opacity: 0.5;
      color: #60A5FA;
    }
  }

  // ── 共用工具列表项（tool-items / system-tool-items 内部） ──────────────────
  .tool-item {
    padding: 12px;
    margin-bottom: 6px;
    border-radius: 4px;
    border: 1px solid rgba(255, 255, 255, 0.05);
    background: rgba(255, 255, 255, 0.02);
    transition: all 0.2s;

    &:last-child { margin-bottom: 0; }

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

      :deep(.ant-checkbox) { top: 4px; }

      :deep(.ant-checkbox-inner) {
        background-color: transparent;
        border-color: rgba(148, 163, 184, 0.5);
      }

      :deep(.ant-checkbox-checked .ant-checkbox-inner) {
        background-color: #60A5FA;
        border-color: #60A5FA;
      }

      :deep(span) { color: #e2e8f0; }
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
          &:hover { color: #93C5FD; }
        }
      }
    }
  }

  // ── 共用滚动条样式 mixin ───────────────────────────────────────────────────
  .scrollbar-mixin() {
    &::-webkit-scrollbar { width: 4px; }
    &::-webkit-scrollbar-track { background: rgba(255, 255, 255, 0.02); }
    &::-webkit-scrollbar-thumb { background: rgba(59, 130, 246, 0.2); border-radius: 2px; }
  }

  // ── 系统工具 pane ─────────────────────────────────────────────────────────
  .system-tools-pane {
    display: flex;
    flex-direction: column;
    min-height: 420px;
    max-height: 520px;

    .system-tool-items {
      .scrollbar-mixin();
      flex: 1;
      overflow-y: auto;
      border: 1px solid rgba(59, 130, 246, 0.2);
      border-top: none;
      border-radius: 0 0 6px 6px;
      background: rgba(0, 0, 0, 0.2);
      padding: 8px;
    }
  }

  // ── MCP 工具左右分栏容器 ──────────────────────────────────────────────────
  .selector-content {
    display: flex;
    gap: 0;
    min-height: 420px;
    max-height: 520px;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-radius: 6px;
    overflow: hidden;
    background: rgba(0, 0, 0, 0.2);

    // 在 selector-content 内部，list-header 取消独立 border/radius（由容器控制）
    .list-header {
      border-radius: 0;
      border-left: none;
      border-right: none;
      border-top: none;
      border-bottom: 1px solid rgba(59, 130, 246, 0.2);
      background: rgba(59, 130, 246, 0.08);
    }

    .loading-container,
    .empty-container {
      border: none;
      border-radius: 0;
      background: transparent;
    }

    .server-list,
    .tool-list {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .server-list {
      width: 260px;
      flex-shrink: 0;
      border-right: 1px solid rgba(59, 130, 246, 0.2);
      background: rgba(0, 0, 0, 0.1);

      .server-items {
        .scrollbar-mixin();
        flex: 1;
        overflow-y: auto;
        padding: 8px;

        .server-item {
          padding: 10px 12px;
          margin-bottom: 6px;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.2s;
          background: rgba(255, 255, 255, 0.02);
          border: 1px solid transparent;

          &:last-child { margin-bottom: 0; }

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
            gap: 10px;

            .server-icon {
              font-size: 16px;
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
                font-size: 12px;
                font-family: 'JetBrains Mono', monospace;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
              }

              .server-meta {
                display: flex;
                align-items: center;
                gap: 6px;
                font-size: 11px;

                .status-dot {
                  width: 5px;
                  height: 5px;
                  border-radius: 50%;
                  background: #475569;
                  flex-shrink: 0;

                  &.active {
                    background: #10B981;
                    box-shadow: 0 0 4px #10B981;
                  }
                }

                .tool-count { color: #94a3b8; }
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
        .scrollbar-mixin();
        flex: 1;
        overflow-y: auto;
        padding: 8px;
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

// ── 内嵌模式特定样式 ─────────────────────────────────────────────────────────
.agent-tool-config {
  .tool-selector-modal.inline-mode {
    // 内嵌时去掉 padding，铺满容器
    padding: 0;
    background: transparent;
    border: none;
    box-shadow: none;

    .inline-toolbar {
      display: flex;
      justify-content: flex-end;
      margin-bottom: 8px;
    }
  }
}
</style>