<template>
  <div class="knowledge-base-detail" v-if="knowledgeBase">
    <!-- 返回按钮 -->
    <div class="back-button">
      <a-button type="link" @click="handleBack">
        <template #icon>
          <Icon icon="ant-design:arrow-left-outlined" />
        </template>
        返回列表
      </a-button>
    </div>

    <!-- 基本信息卡片 -->
    <a-card title="基本信息" class="info-card">
      <template #extra>
        <a-button @click="handleEdit">编辑</a-button>
      </template>
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="名称">
          {{ knowledgeBase.name }}
        </a-descriptions-item>
        <a-descriptions-item label="向量模型">
          <a-tag color="blue">{{ knowledgeBase.embeddingModelId }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="描述" :span="2">
          {{ knowledgeBase.description || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">
          {{ formatTime(knowledgeBase.createTime) }}
        </a-descriptions-item>
        <a-descriptions-item label="更新时间">
          {{ formatTime(knowledgeBase.updateTime) }}
        </a-descriptions-item>
      </a-descriptions>
    </a-card>

    <!-- 统计信息卡片 -->
    <KnowledgeBaseStats
      :knowledge-base-id="knowledgeBaseId"
      @refresh="loadKnowledgeBase"
    />

    <!-- 文档列表卡片 -->
    <a-card title="文档管理" class="document-card">
      <template #extra>
        <a-space>
          <a-button @click="showUploadModal = true">
            <template #icon>
              <Icon icon="ant-design:upload-outlined" />
            </template>
            上传文档
          </a-button>
          <a-button @click="showTextEditor = true">
            <template #icon>
              <Icon icon="ant-design:file-text-outlined" />
            </template>
            创建文本
          </a-button>
        </a-space>
      </template>
      <DocumentList
        :knowledge-base-id="knowledgeBaseId"
        @refresh="handleDocumentRefresh"
      />
    </a-card>

    <!-- 编辑表单弹窗 -->
    <KnowledgeBaseForm
      v-model:open="formVisible"
      :knowledge-base="knowledgeBase"
      @success="handleFormSuccess"
    />

    <!-- 上传文档弹窗 -->
    <DocumentUploadModal
      v-model:open="showUploadModal"
      :knowledge-base-id="knowledgeBaseId"
      @success="handleUploadSuccess"
    />

    <!-- 文本编辑器弹窗 -->
    <TextDocumentEditor
      v-model:open="showTextEditor"
      :knowledge-base-id="knowledgeBaseId"
      @success="handleTextCreateSuccess"
    />
  </div>

  <!-- 加载中 -->
  <div v-else class="loading-container">
    <a-spin size="large" tip="加载中..." />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { Icon } from '@/components/Icon';
import KnowledgeBaseForm from './KnowledgeBaseForm.vue';
import KnowledgeBaseStats from './components/KnowledgeBaseStats.vue';
import DocumentList from './components/DocumentList.vue';
import DocumentUploadModal from './components/DocumentUploadModal.vue';
import TextDocumentEditor from './components/TextDocumentEditor.vue';
import { getKnowledgeBase } from '@/api/knowledge-base.api';
import type { KnowledgeBase } from '@/types/knowledge-base.types';

const route = useRoute();
const router = useRouter();

const knowledgeBaseId = computed(() => route.params.id as string);
const knowledgeBase = ref<KnowledgeBase | null>(null);
const loading = ref(false);

// 弹窗状态
const formVisible = ref(false);
const showUploadModal = ref(false);
const showTextEditor = ref(false);

// 加载知识库详情
const loadKnowledgeBase = async () => {
  loading.value = true;
  try {
    const kb = await getKnowledgeBase(knowledgeBaseId.value);
    knowledgeBase.value = kb;
  } catch (error: any) {
    message.error('加载知识库详情失败: ' + (error?.message || '未知错误'));
    router.push('/knowledge-bases');
  } finally {
    loading.value = false;
  }
};

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-';
  return new Date(time).toLocaleString('zh-CN');
};

// 返回列表
const handleBack = () => {
  router.push('/knowledge-bases');
};

// 编辑知识库
const handleEdit = () => {
  formVisible.value = true;
};

// 表单成功回调
const handleFormSuccess = () => {
  formVisible.value = false;
  loadKnowledgeBase();
};

// 文档上传成功回调
const handleUploadSuccess = () => {
  showUploadModal.value = false;
  // DocumentList组件会自动刷新
};

// 文本文档创建成功回调
const handleTextCreateSuccess = () => {
  showTextEditor.value = false;
  // DocumentList组件会自动刷新
};

// 文档刷新回调（可以触发统计信息刷新）
const handleDocumentRefresh = () => {
  // 如果需要，可以在这里触发统计信息刷新
};

onMounted(() => {
  loadKnowledgeBase();
});
</script>

<style scoped lang="less">
.knowledge-base-detail {
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 64px);

  .back-button {
    margin-bottom: 16px;
  }

  .info-card,
  .document-card {
    margin-bottom: 16px;
    background: #fff;
  }

  .loading-container {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: calc(100vh - 64px);
  }
}
</style>

