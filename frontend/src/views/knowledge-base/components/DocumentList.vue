<template>
  <div class="document-list">
    <!-- 搜索和筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <div class="search-wrapper">
          <a-input
            v-model:value="searchKeyword"
            :placeholder="t('knowledgeBase.document.search')"
            class="tech-input"
            allow-clear
            @pressEnter="handleSearch"
          >
            <template #prefix>
              <Icon icon="ant-design:search-outlined" class="search-icon" />
            </template>
          </a-input>
        </div>
        
        <div class="select-wrapper">
          <a-select
            v-model:value="statusFilter"
            :placeholder="t('knowledgeBase.document.status')"
            class="tech-select"
            :dropdown-class-name="'tech-select-dropdown'"
            allow-clear
            @change="loadDocuments(true)"
          >
            <a-select-option value="">{{ t('knowledgeBase.document.allStatus') }}</a-select-option>
            <a-select-option value="DRAFT">{{ t('knowledgeBase.document.statuses.DRAFT') }}</a-select-option>
            <a-select-option value="BUILDING">{{ t('knowledgeBase.document.statuses.BUILDING') }}</a-select-option>
            <a-select-option value="COMPLETE">{{ t('knowledgeBase.document.statuses.COMPLETE') }}</a-select-option>
            <a-select-option value="FAILED">{{ t('knowledgeBase.document.statuses.FAILED') }}</a-select-option>
          </a-select>
        </div>
        
        <div class="select-wrapper">
          <a-select
            v-model:value="typeFilter"
            :placeholder="t('knowledgeBase.document.type')"
            class="tech-select"
            :dropdown-class-name="'tech-select-dropdown'"
            allow-clear
            @change="loadDocuments(true)"
          >
            <a-select-option value="">{{ t('knowledgeBase.document.allTypes') }}</a-select-option>
            <a-select-option value="FILE">{{ t('knowledgeBase.document.types.FILE') }}</a-select-option>
            <a-select-option value="TEXT">{{ t('knowledgeBase.document.types.TEXT') }}</a-select-option>
            <a-select-option value="WEB">{{ t('knowledgeBase.document.types.WEB') }}</a-select-option>
          </a-select>
        </div>
      </div>
      
      <div class="filter-right">
        <a-button type="text" class="refresh-btn" @click="loadDocuments(false)" :loading="loading">
          <template #icon><Icon icon="ant-design:sync-outlined" /></template>
        </a-button>
      </div>
    </div>

    <!-- 文档表格 -->
    <a-table
      class="tech-table"
      :columns="columns"
      :data-source="documents"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <!-- 文档名称列 -->
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <div class="document-title">
            <Icon :icon="getDocumentIcon(record.type)" class="doc-icon" />
            <span class="doc-name">{{ record.title }}</span>
          </div>
        </template>

        <!-- 类型列 -->
        <template v-else-if="column.key === 'type'">
          <span class="tech-text-tag">{{ getTypeText(record.type) }}</span>
        </template>

        <!-- 状态列 -->
        <template v-else-if="column.key === 'status'">
          <div class="status-wrapper">
            <DocumentStatusTag :status="record.status" />
            <a-tooltip v-if="record.status === 'FAILED' && record.failedReason" :title="record.failedReason">
              <Icon icon="ant-design:info-circle-outlined" class="error-icon" />
            </a-tooltip>
          </div>
        </template>

        <!-- 创建时间列 -->
        <template v-else-if="column.key === 'createTime'">
          <span class="tech-date">{{ formatTime(record.createTime) }}</span>
        </template>

        <!-- 操作列 -->
        <template v-else-if="column.key === 'action'">
          <div class="action-buttons">
            <a-tooltip :title="t('knowledgeBase.document.rebuild')" v-if="record.status === 'FAILED'">
              <a-button type="text" size="small" class="action-btn" @click="handleRebuild(record)">
                <Icon icon="ant-design:reload-outlined" />
              </a-button>
            </a-tooltip>
            
            <a-popconfirm
              :title="t('knowledgeBase.document.confirmDelete')"
              :description="t('knowledgeBase.document.confirmDeleteDesc')"
              :ok-text="t('common.confirm')"
              :cancel-text="t('common.cancel')"
              @confirm="handleDelete(record)"
            >
              <a-button type="text" size="small" class="action-btn danger">
                <Icon icon="ant-design:delete-outlined" />
              </a-button>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import { Icon } from '@/components/Icon';
import DocumentStatusTag from './DocumentStatusTag.vue';
import { getDocumentList, rebuildDocument, deleteDocument } from '@/api/knowledge-base.api';
import type { Document } from '@/types/knowledge-base.types';

const props = defineProps<{
  knowledgeBaseId: string;
}>();

const emit = defineEmits<{
  (e: 'refresh'): void;
}>();

const { t } = useI18n();

// 列表数据
const documents = ref<Document[]>([]);
const loading = ref(false);
const searchKeyword = ref('');
const statusFilter = ref<string>('');
const typeFilter = ref<string>('');
const orderBy = ref<string>('');
const orderDirection = ref<'ASC' | 'DESC'>('DESC');

// 分页配置
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `TOTAL ${total}`,
  size: 'small'
});

// 状态刷新定时器
let refreshTimer: number | null = null;

// 表格列定义
const columns = computed(() => [
  {
    title: t('knowledgeBase.document.name'),
    key: 'title',
    dataIndex: 'title',
    ellipsis: true,
  },
  {
    title: t('knowledgeBase.document.type'),
    key: 'type',
    dataIndex: 'type',
    width: 100,
  },
  {
    title: t('knowledgeBase.document.status'),
    key: 'status',
    dataIndex: 'status',
    width: 140,
  },
  {
    title: t('knowledgeBase.document.createdAt'),
    key: 'createTime',
    dataIndex: 'createTime',
    width: 180,
    sorter: true,
  },
  {
    title: t('knowledgeBase.document.actions'),
    key: 'action',
    width: 100,
    fixed: 'right' as const,
    align: 'center',
  },
]);

// 检查是否有处理中的文档（用于自动刷新）
const hasBuildingDocuments = computed(() => {
  return documents.value.some((doc) => doc.status === 'BUILDING');
});

// 获取文档图标
const getDocumentIcon = (type: Document['type']) => {
  const icons = {
    FILE: 'ant-design:file-outlined',
    TEXT: 'ant-design:file-text-outlined',
    WEB: 'ant-design:global-outlined',
  };
  return icons[type] || 'ant-design:file-outlined';
};

// 获取类型文本
const getTypeText = (type: Document['type']) => {
  return type;
};

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-';
  return new Date(time).toLocaleString('zh-CN');
};

// 加载文档列表
const loadDocuments = async (resetPage = false) => {
  if (resetPage) {
    pagination.value.current = 1;
  }
  
  loading.value = true;
  try {
    const pageResult = await getDocumentList(props.knowledgeBaseId, {
      pageNo: pagination.value.current,
      pageSize: pagination.value.pageSize,
      keyword: searchKeyword.value || undefined,
      status: statusFilter.value || undefined,
      type: typeFilter.value || undefined,
      orderBy: orderBy.value || undefined,
      orderDirection: orderDirection.value,
    });
    documents.value = pageResult.records;
    pagination.value.total = pageResult.total;
  } catch (error: any) {
    message.error('Failed to load documents: ' + (error?.message || 'Unknown error'));
  } finally {
    loading.value = false;
  }
};

// 刷新文档列表（不重置页码）
const refreshDocuments = async () => {
  try {
    const pageResult = await getDocumentList(props.knowledgeBaseId, {
      pageNo: pagination.value.current,
      pageSize: pagination.value.pageSize,
      keyword: searchKeyword.value || undefined,
      status: statusFilter.value || undefined,
      type: typeFilter.value || undefined,
      orderBy: orderBy.value || undefined,
      orderDirection: orderDirection.value,
    });
    documents.value = pageResult.records;
    pagination.value.total = pageResult.total;
  } catch (error: any) {
    console.error('Failed to refresh documents:', error);
  }
};

// 启动定时刷新
const startAutoRefresh = () => {
  if (refreshTimer) return;

  refreshTimer = window.setInterval(() => {
    if (hasBuildingDocuments.value) {
      refreshDocuments();
    } else {
      stopAutoRefresh();
    }
  }, 5000); // 每5秒刷新一次
};

// 停止定时刷新
const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
};

// 搜索
const handleSearch = () => {
  loadDocuments(true); // 搜索时重置到第一页
};

// 重建文档
const handleRebuild = async (record: Document) => {
  try {
    await rebuildDocument(record.id);
    message.success('Rebuild task started');
    refreshDocuments();
  } catch (error: any) {
    message.error('Failed to rebuild: ' + (error?.message || 'Unknown error'));
  }
};

// 删除文档
const handleDelete = async (record: Document) => {
  try {
    await deleteDocument(record.id);
    message.success('Document deleted');
    refreshDocuments();
    emit('refresh'); // 通知父组件刷新统计信息
  } catch (error: any) {
    message.error('Failed to delete: ' + (error?.message || 'Unknown error'));
  }
};

// 表格变化处理（分页、排序）
const handleTableChange = (pag: any, filters: any, sorter: any) => {
  // 处理分页
  if (pag) {
    pagination.value.current = pag.current;
    pagination.value.pageSize = pag.pageSize;
  }
  
  // 处理排序
  if (sorter && sorter.field) {
    orderBy.value = sorter.field === 'createTime' ? 'create_time' : sorter.field;
    orderDirection.value = sorter.order === 'ascend' ? 'ASC' : 'DESC';
  } else {
    orderBy.value = '';
    orderDirection.value = 'DESC';
  }
  
  // 重新加载数据
  loadDocuments();
};

// 监听是否有处理中的文档，启动/停止自动刷新
watch(
  hasBuildingDocuments,
  (hasBuilding) => {
    if (hasBuilding) {
      startAutoRefresh();
    } else {
      stopAutoRefresh();
    }
  },
  { immediate: true }
);

// 监听knowledgeBaseId变化
watch(
  () => props.knowledgeBaseId,
  () => {
    if (props.knowledgeBaseId) {
      loadDocuments(true); // 切换知识库时重置到第一页
    }
  },
  { immediate: true }
);

// 暴露刷新方法
defineExpose({
  refresh: refreshDocuments,
});

onMounted(() => {
  if (props.knowledgeBaseId) {
    loadDocuments();
  }
  if (hasBuildingDocuments.value) {
    startAutoRefresh();
  }
});

onUnmounted(() => {
  stopAutoRefresh();
});
</script>

<style scoped lang="less">
.document-list {
  .filter-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    
    .filter-left {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    
    .search-wrapper {
      position: relative;
      width: 300px;
    }
    
    .select-wrapper {
      width: 150px;
    }
    
    .tech-input {
      background: rgba(0, 0, 0, 0.2);
      border: 1px solid rgba(59, 130, 246, 0.2);
      border-radius: 4px;
      color: #fff;
      font-family: 'JetBrains Mono', monospace;
      font-size: 12px;
      
      :deep(.ant-input) {
        background: transparent;
        color: #fff;
        &::placeholder {
          color: rgba(148, 163, 184, 0.5);
        }
      }
      
      .search-icon {
        color: rgba(96, 165, 250, 0.6);
      }
      
      &:hover, &:focus-within {
        border-color: rgba(59, 130, 246, 0.5);
      }
    }
    
    .tech-select {
      :deep(.ant-select-selector) {
        background-color: rgba(0, 0, 0, 0.2) !important;
        border-color: rgba(59, 130, 246, 0.2) !important;
        color: #fff !important;
        font-family: 'JetBrains Mono', monospace;
        font-size: 12px;
      }
      
      :deep(.ant-select-arrow) {
        color: rgba(96, 165, 250, 0.6);
      }
    }
    
    .refresh-btn {
      color: rgba(96, 165, 250, 0.6);
      
      &:hover {
        color: #60A5FA;
        background: rgba(59, 130, 246, 0.1);
      }
    }
  }

  .tech-table {
    :deep(.ant-table) {
      background: transparent;
      color: #e2e8f0;
    }
    
    :deep(.ant-table-thead > tr > th) {
      background: rgba(15, 23, 42, 0.4);
      border-bottom: 1px solid rgba(59, 130, 246, 0.2);
      color: rgba(148, 163, 184, 0.8);
      font-family: 'JetBrains Mono', monospace;
      font-size: 11px;
      font-weight: 600;
    }
    
    :deep(.ant-table-tbody > tr > td) {
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      background: transparent !important; /* Ensure no white background */
      transition: background 0.3s;
    }
    
    :deep(.ant-table-tbody > tr:hover > td) {
      background: rgba(59, 130, 246, 0.05) !important;
    }
    
    :deep(.ant-pagination) {
      .ant-pagination-item {
        background: transparent;
        border-color: rgba(255, 255, 255, 0.1);
        
        a { color: #94a3b8; }
        
        &-active {
          border-color: #60A5FA;
          background: rgba(59, 130, 246, 0.1);
          a { color: #60A5FA; }
        }
      }
      
      .ant-pagination-prev .ant-pagination-item-link,
      .ant-pagination-next .ant-pagination-item-link {
        background: transparent;
        border-color: rgba(255, 255, 255, 0.1);
        color: #94a3b8;
      }
    }
  }

  .document-title {
    display: flex;
    align-items: center;
    gap: 10px;
    
    .doc-icon {
      color: #60A5FA;
      font-size: 16px;
    }
    
    .doc-name {
      color: #e2e8f0;
      font-weight: 500;
    }
  }
  
  .tech-text-tag {
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    color: #94a3b8;
    background: rgba(255, 255, 255, 0.05);
    padding: 2px 6px;
    border-radius: 4px;
  }
  
  .status-wrapper {
    display: flex;
    align-items: center;
    gap: 8px;
    
    .error-icon {
      color: #EF4444;
      cursor: help;
    }
  }
  
  .tech-date {
    font-family: 'JetBrains Mono', monospace;
    font-size: 11px;
    color: #94a3b8;
  }
  
  .action-buttons {
    display: flex;
    justify-content: center;
    gap: 8px;
    
    .action-btn {
      color: rgba(96, 165, 250, 0.6);
      
      &:hover {
        color: #60A5FA;
        background: rgba(59, 130, 246, 0.1);
      }
      
      &.danger:hover {
        color: #EF4444;
        background: rgba(239, 68, 68, 0.1);
      }
    }
  }
}
</style>

<style lang="less">
/* Global styles for dropdowns */
.tech-select-dropdown {
  background-color: #0f172a !important;
  border: 1px solid rgba(59, 130, 246, 0.2);
  
  .ant-select-item {
    color: #e2e8f0;
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    
    &-option-active {
      background-color: rgba(59, 130, 246, 0.1) !important;
    }
    
    &-option-selected {
      background-color: rgba(59, 130, 246, 0.2) !important;
      color: #60A5FA;
    }
  }
}
</style>

