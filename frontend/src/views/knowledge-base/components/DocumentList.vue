<template>
  <div class="document-list">
    <!-- 搜索和筛选栏 -->
    <div class="filter-bar">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索文档名称"
        style="width: 300px; margin-right: 16px;"
        allow-clear
        @search="handleSearch"
      />
      <a-select
        v-model:value="statusFilter"
        placeholder="筛选状态"
        style="width: 150px; margin-right: 16px;"
        allow-clear
      >
        <a-select-option value="">全部状态</a-select-option>
        <a-select-option value="DRAFT">草稿</a-select-option>
        <a-select-option value="BUILDING">处理中</a-select-option>
        <a-select-option value="COMPLETE">完成</a-select-option>
        <a-select-option value="FAILED">失败</a-select-option>
      </a-select>
      <a-select
        v-model:value="typeFilter"
        placeholder="筛选类型"
        style="width: 150px;"
        allow-clear
      >
        <a-select-option value="">全部类型</a-select-option>
        <a-select-option value="FILE">文件</a-select-option>
        <a-select-option value="TEXT">文本</a-select-option>
        <a-select-option value="WEB">网页</a-select-option>
      </a-select>
    </div>

    <!-- 文档表格 -->
    <a-table
      :columns="columns"
      :data-source="filteredList"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <!-- 文档名称列 -->
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <div class="document-title">
            <Icon :icon="getDocumentIcon(record.type)" style="margin-right: 8px;" />
            <span>{{ record.title }}</span>
          </div>
        </template>

        <!-- 类型列 -->
        <template v-else-if="column.key === 'type'">
          <a-tag :color="getTypeColor(record.type)">
            {{ getTypeText(record.type) }}
          </a-tag>
        </template>

        <!-- 状态列 -->
        <template v-else-if="column.key === 'status'">
          <DocumentStatusTag :status="record.status" />
          <div v-if="record.status === 'FAILED' && record.failedReason" class="error-reason">
            <a-tooltip :title="record.failedReason">
              <Icon icon="ant-design:exclamation-circle-outlined" style="color: #ff4d4f;" />
            </a-tooltip>
          </div>
        </template>

        <!-- 创建时间列 -->
        <template v-else-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>

        <!-- 操作列 -->
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button
              v-if="record.status === 'FAILED'"
              type="link"
              size="small"
              @click="handleRebuild(record)"
            >
              重建
            </a-button>
            <a-popconfirm
              title="确定要删除这个文档吗？"
              description="删除后将无法恢复"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleDelete(record)"
            >
              <a-button type="link" size="small" danger>
                删除
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
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

// 列表数据
const documents = ref<Document[]>([]);
const loading = ref(false);
const searchKeyword = ref('');
const statusFilter = ref<string>('');
const typeFilter = ref<string>('');

// 分页配置
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
});

// 状态刷新定时器
let refreshTimer: number | null = null;

// 表格列定义
const columns = [
  {
    title: '文档名称',
    key: 'title',
    dataIndex: 'title',
    ellipsis: true,
  },
  {
    title: '类型',
    key: 'type',
    dataIndex: 'type',
    width: 100,
  },
  {
    title: '状态',
    key: 'status',
    dataIndex: 'status',
    width: 120,
  },
  {
    title: '创建时间',
    key: 'createTime',
    dataIndex: 'createTime',
    width: 180,
    sorter: true,
  },
  {
    title: '操作',
    key: 'action',
    width: 150,
    fixed: 'right' as const,
  },
];

// 筛选后的列表
const filteredList = computed(() => {
  let list = documents.value;

  // 按状态筛选
  if (statusFilter.value) {
    list = list.filter((doc) => doc.status === statusFilter.value);
  }

  // 按类型筛选
  if (typeFilter.value) {
    list = list.filter((doc) => doc.type === typeFilter.value);
  }

  // 按关键词搜索
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase();
    list = list.filter((doc) => doc.title.toLowerCase().includes(keyword));
  }

  return list;
});

// 检查是否有处理中的文档
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

// 获取类型颜色
const getTypeColor = (type: Document['type']) => {
  const colors = {
    FILE: 'blue',
    TEXT: 'green',
    WEB: 'orange',
  };
  return colors[type] || 'default';
};

// 获取类型文本
const getTypeText = (type: Document['type']) => {
  const texts = {
    FILE: '文件',
    TEXT: '文本',
    WEB: '网页',
  };
  return texts[type] || type;
};

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-';
  return new Date(time).toLocaleString('zh-CN');
};

// 加载文档列表
const loadDocuments = async () => {
  loading.value = true;
  try {
    const list = await getDocumentList(props.knowledgeBaseId);
    documents.value = list;
    pagination.value.total = list.length;
  } catch (error: any) {
    message.error('加载文档列表失败: ' + (error?.message || '未知错误'));
  } finally {
    loading.value = false;
  }
};

// 刷新文档列表
const refreshDocuments = async () => {
  try {
    const list = await getDocumentList(props.knowledgeBaseId);
    documents.value = list;
    pagination.value.total = list.length;
  } catch (error: any) {
    console.error('刷新文档列表失败:', error);
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
  // 搜索已在computed中处理
};

// 重建文档
const handleRebuild = async (record: Document) => {
  try {
    await rebuildDocument(record.id);
    message.success('重建任务已启动');
    refreshDocuments();
  } catch (error: any) {
    message.error('重建失败: ' + (error?.message || '未知错误'));
  }
};

// 删除文档
const handleDelete = async (record: Document) => {
  try {
    await deleteDocument(record.id);
    message.success('删除成功');
    refreshDocuments();
    emit('refresh'); // 通知父组件刷新统计信息
  } catch (error: any) {
    message.error('删除失败: ' + (error?.message || '未知错误'));
  }
};

// 表格变化处理
const handleTableChange = (pag: any, filters: any, sorter: any) => {
  pagination.value.current = pag.current;
  pagination.value.pageSize = pag.pageSize;
  // 可以在这里添加排序逻辑
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
      loadDocuments();
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
    align-items: center;
    margin-bottom: 16px;
  }

  .document-title {
    display: flex;
    align-items: center;
  }

  .error-reason {
    display: inline-block;
    margin-left: 8px;
    cursor: pointer;
  }
}
</style>

