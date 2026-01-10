<template>
  <div class="knowledge-base-list">
    <!-- 顶部操作栏 -->
    <div class="action-bar">
      <div class="action-left">
        <a-button type="link" @click="handleBackToAgent" style="padding: 0; margin-right: 16px;">
          <template #icon>
            <Icon icon="ant-design:arrow-left-outlined" />
          </template>
          返回助手
        </a-button>
        <h2 class="page-title">
          <Icon icon="ant-design:book-outlined" />
          知识库管理
        </h2>
      </div>
      <div class="action-right">
        <a-input-search
          v-model:value="searchKeyword"
          placeholder="搜索知识库名称或描述"
          style="width: 300px; margin-right: 16px;"
          allow-clear
          @search="handleSearch"
        />
        <a-button type="primary" @click="handleCreate">
          <template #icon>
            <Icon icon="ant-design:plus-outlined" />
          </template>
          创建知识库
        </a-button>
      </div>
    </div>

    <!-- 知识库表格 -->
    <a-card>
      <a-table
        :columns="columns"
        :data-source="filteredList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <!-- 名称列 -->
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-button type="link" @click="handleView(record)" style="padding: 0;">
              <Icon icon="ant-design:book-outlined" style="margin-right: 8px;" />
              {{ record.name }}
            </a-button>
          </template>

          <!-- 描述列 -->
          <template v-else-if="column.key === 'description'">
            <span v-if="record.description">{{ record.description }}</span>
            <span v-else style="color: #bfbfbf;">-</span>
          </template>

          <!-- 向量模型列 -->
          <template v-else-if="column.key === 'embeddingModelId'">
            <a-tag color="blue">{{ record.embeddingModelId }}</a-tag>
          </template>

          <!-- 文档数量列（需要从统计信息获取，暂时显示-） -->
          <template v-else-if="column.key === 'documentCount'">
            <span>-</span>
          </template>

          <!-- 创建时间列 -->
          <template v-else-if="column.key === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record)">
                查看
              </a-button>
              <a-button type="link" size="small" @click="handleEdit(record)">
                编辑
              </a-button>
              <a-popconfirm
                title="确定要删除这个知识库吗？"
                description="删除后将无法恢复，且会删除所有关联文档"
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
    </a-card>

    <!-- 创建/编辑表单弹窗 -->
    <KnowledgeBaseForm
      v-model:open="formVisible"
      :knowledge-base="currentKnowledgeBase"
      @success="handleFormSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { Icon } from '@/components/Icon';
import KnowledgeBaseForm from './KnowledgeBaseForm.vue';
import {
  getKnowledgeBaseList,
  deleteKnowledgeBase,
} from '@/api/knowledge-base.api';
import type { KnowledgeBase } from '@/types/knowledge-base.types';

const router = useRouter();

// 列表数据
const knowledgeBaseList = ref<KnowledgeBase[]>([]);
const loading = ref(false);
const searchKeyword = ref('');

// 表单弹窗
const formVisible = ref(false);
const currentKnowledgeBase = ref<KnowledgeBase | null>(null);

// 表格列定义
const columns = [
  {
    title: '名称',
    key: 'name',
    dataIndex: 'name',
    width: 200,
  },
  {
    title: '描述',
    key: 'description',
    dataIndex: 'description',
    ellipsis: true,
  },
  {
    title: '向量模型',
    key: 'embeddingModelId',
    dataIndex: 'embeddingModelId',
    width: 150,
  },
  {
    title: '文档数量',
    key: 'documentCount',
    width: 120,
    align: 'center' as const,
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
    width: 200,
    fixed: 'right' as const,
  },
];

// 分页配置
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
});

// 筛选后的列表
const filteredList = computed(() => {
  if (!searchKeyword.value) {
    return knowledgeBaseList.value;
  }
  const keyword = searchKeyword.value.toLowerCase();
  return knowledgeBaseList.value.filter(
    (kb) =>
      kb.name.toLowerCase().includes(keyword) ||
      (kb.description && kb.description.toLowerCase().includes(keyword))
  );
});

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-';
  return new Date(time).toLocaleString('zh-CN');
};

// 加载知识库列表
const loadList = async () => {
  loading.value = true;
  try {
    const list = await getKnowledgeBaseList();
    knowledgeBaseList.value = list;
    pagination.value.total = list.length;
  } catch (error: any) {
    message.error('加载知识库列表失败: ' + (error?.message || '未知错误'));
  } finally {
    loading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  // 搜索已在computed中处理，这里可以添加其他逻辑
};

// 创建知识库
const handleCreate = async () => {
  // 先关闭弹窗并重置数据（如果弹窗已打开）
  formVisible.value = false;
  currentKnowledgeBase.value = null;
  // 等待弹窗关闭完成和状态更新
  await nextTick();
  // 再打开弹窗，此时 currentKnowledgeBase 一定是 null
  formVisible.value = true;
};

// 查看知识库
const handleView = (record: KnowledgeBase) => {
  router.push(`/knowledge-bases/${record.id}`);
};

// 编辑知识库
const handleEdit = async (record: KnowledgeBase) => {
  console.log('handleEdit called with record:', record);
  // 先关闭弹窗（如果已打开）
  if (formVisible.value) {
    formVisible.value = false;
    // 等待弹窗完全关闭
    await nextTick();
  }
  // 设置知识库数据（深拷贝，避免引用问题）
  currentKnowledgeBase.value = {
    id: record.id,
    name: record.name,
    description: record.description,
    embeddingModelId: record.embeddingModelId,
    createTime: record.createTime,
    updateTime: record.updateTime,
  };
  console.log('currentKnowledgeBase set to:', currentKnowledgeBase.value);
  // 等待 Vue 更新 props
  await nextTick();
  console.log('Opening modal with currentKnowledgeBase:', currentKnowledgeBase.value);
  formVisible.value = true;
};

// 删除知识库
const handleDelete = async (record: KnowledgeBase) => {
  try {
    await deleteKnowledgeBase(record.id);
    message.success('删除成功');
    loadList();
  } catch (error: any) {
    message.error('删除失败: ' + (error?.message || '未知错误'));
  }
};

// 表单成功回调
const handleFormSuccess = () => {
  formVisible.value = false;
  // 立即重置，确保下次打开时数据正确
  currentKnowledgeBase.value = null;
  loadList();
};

// 表格变化处理
const handleTableChange = (pag: any, filters: any, sorter: any) => {
  pagination.value.current = pag.current;
  pagination.value.pageSize = pag.pageSize;
  // 可以在这里添加排序逻辑
};

// 返回助手页面
const handleBackToAgent = () => {
  router.push('/agent');
};

onMounted(() => {
  loadList();
});
</script>

<style scoped lang="less">
.knowledge-base-list {
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 64px);

  .action-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .action-left {
      .page-title {
        margin: 0;
        font-size: 20px;
        font-weight: 500;
        display: flex;
        align-items: center;
        gap: 8px;
      }
    }

    .action-right {
      display: flex;
      align-items: center;
    }
  }

  :deep(.ant-card) {
    background: #fff;
  }
}
</style>

