<template>
  <div class="knowledge-base-detail" v-if="knowledgeBase">
    <!-- 头部导航栏 -->
    <div class="detail-header">
      <div class="header-left">
        <a-button type="link" @click="handleBack" class="back-btn">
          <template #icon>
            <Icon icon="ant-design:arrow-left-outlined" />
          </template>
          {{ t('common.return') }}
        </a-button>
        <div class="kb-title-wrapper">
          <h1 class="kb-title">{{ knowledgeBase.name }}</h1>
          <a-tag class="kb-id-tag">{{ knowledgeBase.id }}</a-tag>
        </div>
      </div>
      <div class="header-right">
        <a-button class="tech-btn" @click="handleEdit">
          <template #icon>
            <Icon icon="ant-design:setting-outlined" />
          </template>
          {{ t('agent.config') }}
        </a-button>
      </div>
    </div>

    <!-- 基本信息卡片 -->
    <div class="tech-card info-card">
      <div class="card-header">
        <Icon icon="ant-design:info-circle-outlined" class="header-icon" />
        <span class="header-title">{{ t('knowledgeBase.detail.systemParams') }}</span>
      </div>
      <div class="info-grid">
        <div class="info-item">
          <span class="label">{{ t('knowledgeBase.form.embeddingModel') }}</span>
          <span class="value highlight">{{ knowledgeBase.embeddingModelId }}</span>
        </div>
        <div class="info-item">
          <span class="label">{{ t('common.created') }}</span>
          <span class="value">{{ formatTime(knowledgeBase.createTime) }}</span>
        </div>
        <div class="info-item">
          <span class="label">{{ t('knowledgeBase.detail.lastUpdated') }}</span>
          <span class="value">{{ formatTime(knowledgeBase.updateTime) }}</span>
        </div>
        <div class="info-item full-width">
          <span class="label">{{ t('knowledgeBase.form.desc') }}</span>
          <span class="value">{{ knowledgeBase.description || t('common.noDesc') }}</span>
        </div>
      </div>
    </div>

    <!-- 统计信息卡片 -->
    <KnowledgeBaseStats
      :knowledge-base-id="knowledgeBaseId"
      @refresh="loadKnowledgeBase"
    />

    <!-- 文档管理卡片 -->
    <div class="tech-card document-card">
      <div class="card-header">
        <div class="header-left">
          <Icon icon="ant-design:folder-open-outlined" class="header-icon" />
          <span class="header-title">{{ t('knowledgeBase.document.title') }}</span>
        </div>
        <div class="header-actions">
          <a-button class="tech-btn primary" @click="showUploadModal = true">
            <template #icon>
              <Icon icon="ant-design:upload-outlined" />
            </template>
            {{ t('knowledgeBase.document.upload') }}
          </a-button>
          <a-button class="tech-btn" @click="showTextEditor = true">
            <template #icon>
              <Icon icon="ant-design:file-text-outlined" />
            </template>
            {{ t('knowledgeBase.document.createText') }}
          </a-button>
        </div>
      </div>
      
      <div class="document-list-container">
        <DocumentList
          ref="documentListRef"
          :knowledge-base-id="knowledgeBaseId"
          @refresh="handleDocumentRefresh"
        />
      </div>
    </div>

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
    <div class="tech-loader">
      <div class="loader-ring"></div>
      <div class="loader-text">{{ t('common.loading') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
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
const { t } = useI18n();

const knowledgeBaseId = computed(() => route.params.id as string);
const knowledgeBase = ref<KnowledgeBase | null>(null);
const loading = ref(false);

// DocumentList 组件引用
const documentListRef = ref<InstanceType<typeof DocumentList>>();

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
    message.error('Failed to load details: ' + (error?.message || 'Unknown error'));
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
  // 刷新文档列表
  documentListRef.value?.refresh();
};

// 文本文档创建成功回调
const handleTextCreateSuccess = () => {
  showTextEditor.value = false;
  // 刷新文档列表
  documentListRef.value?.refresh();
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
  padding: 24px 40px;
  background: transparent;
  height: 100%;
  overflow-y: auto;
  font-family: 'Inter', sans-serif;
  
  /* Custom Scrollbar */
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-track {
    background: rgba(255, 255, 255, 0.02);
  }
  
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
    
    &:hover {
      background: rgba(255, 255, 255, 0.2);
    }
  }
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  
  .header-left {
    display: flex;
    flex-direction: column;
    gap: 8px;
    
    .back-btn {
      color: rgba(96, 165, 250, 0.6);
      padding: 0;
      font-family: 'JetBrains Mono', monospace;
      font-size: 12px;
      height: auto;
      justify-content: flex-start;
      
      &:hover {
        color: #60A5FA;
      }
    }
    
    .kb-title-wrapper {
      display: flex;
      align-items: center;
      gap: 12px;
      
      .kb-title {
        font-family: 'JetBrains Mono', monospace;
        font-size: 24px;
        font-weight: 700;
        color: #fff;
        margin: 0;
        letter-spacing: 1px;
      }
      
      .kb-id-tag {
        background: rgba(59, 130, 246, 0.1);
        border: 1px solid rgba(59, 130, 246, 0.3);
        color: #60A5FA;
        font-family: 'JetBrains Mono', monospace;
        font-size: 11px;
      }
    }
  }
}

.tech-btn {
  height: 32px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  
  &:hover {
    background: rgba(255, 255, 255, 0.1);
    color: #fff;
    border-color: rgba(255, 255, 255, 0.2);
  }
  
  &.primary {
    background: rgba(59, 130, 246, 0.2);
    border: 1px solid rgba(59, 130, 246, 0.5);
    color: #60A5FA;
    
    &:hover {
      background: rgba(59, 130, 246, 0.3);
      border-color: #60A5FA;
      color: #fff;
      box-shadow: 0 0 10px rgba(59, 130, 246, 0.3);
    }
  }
}

.tech-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 24px;
  backdrop-filter: blur(10px);
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 12px;
    border-bottom: 1px solid rgba(59, 130, 246, 0.2);
    
    .header-left {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    
    .header-icon {
      color: #60A5FA;
      font-size: 18px;
    }
    
    .header-title {
      font-family: 'JetBrains Mono', monospace;
      font-size: 14px;
      font-weight: 600;
      color: #e2e8f0;
      letter-spacing: 1px;
    }
    
    .header-actions {
      display: flex;
      gap: 12px;
    }
  }
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  
  .info-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
    
    &.full-width {
      grid-column: span 3;
    }
    
    .label {
      font-size: 11px;
      color: rgba(148, 163, 184, 0.6);
      font-family: 'JetBrains Mono', monospace;
      letter-spacing: 0.5px;
    }
    
    .value {
      font-size: 14px;
      color: #e2e8f0;
      font-family: 'Inter', sans-serif;
      
      &.highlight {
        color: #60A5FA;
        font-family: 'JetBrains Mono', monospace;
      }
    }
  }
}

.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 64px);
  
  .tech-loader {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    
    .loader-ring {
      width: 40px;
      height: 40px;
      border: 2px solid rgba(59, 130, 246, 0.3);
      border-top-color: #60A5FA;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }
    
    .loader-text {
      font-family: 'JetBrains Mono', monospace;
      font-size: 12px;
      color: #60A5FA;
      letter-spacing: 2px;
    }
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>

