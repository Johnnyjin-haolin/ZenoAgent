<template>
  <div class="knowledge-base-list">
    <!-- 顶部操作栏 -->
    <div class="action-bar">
      <div class="action-left">
        <a-button type="link" @click="handleBackToAgent" class="back-btn">
          <template #icon>
            <Icon icon="ant-design:arrow-left-outlined" />
          </template>
          {{ t('common.return') }}
        </a-button>
        <h2 class="page-title">
          <span class="title-icon"><Icon icon="ant-design:database-outlined" /></span>
          <span class="title-text">{{ t('knowledgeBase.title') }}</span>
        </h2>
      </div>
      <div class="action-right">
        <div class="search-wrapper">
          <a-input
            v-model:value="searchKeyword"
            :placeholder="t('knowledgeBase.search')"
            class="tech-input"
            allow-clear
            @pressEnter="handleSearch"
          >
            <template #prefix>
              <Icon icon="ant-design:search-outlined" class="search-icon" />
            </template>
          </a-input>
        </div>
        <a-button type="primary" @click="handleCreate" class="tech-btn primary">
          <template #icon>
            <Icon icon="ant-design:plus-outlined" />
          </template>
          {{ t('knowledgeBase.create') }}
        </a-button>
      </div>
    </div>

    <!-- 知识库列表 (Grid View instead of Table for better Tech feel) -->
    <div class="kb-grid">
      <div v-for="kb in filteredList" :key="kb.id" class="kb-card" @click="handleView(kb)">
        <div class="kb-card-header">
          <div class="kb-icon">
            <Icon icon="ant-design:hdd-outlined" />
          </div>
          <div class="kb-actions">
            <a-tooltip :title="t('knowledgeBase.form.editTitle')">
              <span class="action-icon" @click.stop="handleEdit(kb)">
                <Icon icon="ant-design:setting-outlined" />
              </span>
            </a-tooltip>
            <a-popconfirm
              :title="t('knowledgeBase.document.confirmDelete')"
              :description="t('knowledgeBase.document.confirmDeleteDesc')"
              :ok-text="t('common.confirm')"
              :cancel-text="t('common.cancel')"
              @confirm="handleDelete(kb)"
            >
              <span class="action-icon danger" @click.stop>
                <Icon icon="ant-design:delete-outlined" />
              </span>
            </a-popconfirm>
          </div>
        </div>
        
        <div class="kb-info">
          <h3 class="kb-name">{{ kb.name }}</h3>
          <p class="kb-desc">{{ kb.description || t('common.info') + '...' }}</p>
        </div>
        
        <div class="kb-meta">
          <div class="meta-item">
            <span class="label">{{ t('knowledgeBase.form.embeddingModel') }}</span>
            <span class="value">{{ kb.embeddingModelId }}</span>
          </div>
          <div class="meta-item">
            <span class="label">{{ t('common.created') }}</span>
            <span class="value">{{ new Date(kb.createTime).toLocaleDateString() }}</span>
          </div>
        </div>
        
        <div class="kb-status-line"></div>
      </div>
      
      <!-- Create New Card Placeholder -->
      <div class="kb-card create-card" @click="handleCreate">
        <div class="create-content">
          <Icon icon="ant-design:plus-outlined" class="create-icon" />
          <span class="create-text">{{ t('knowledgeBase.create') }}</span>
        </div>
      </div>
    </div>

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
import { useI18n } from 'vue-i18n';
import { message } from 'ant-design-vue';
import { Icon } from '@/components/Icon';
import KnowledgeBaseForm from './KnowledgeBaseForm.vue';
import {
  getKnowledgeBaseList,
  deleteKnowledgeBase,
} from '@/api/knowledge-base.api';
import type { KnowledgeBase } from '@/types/knowledge-base.types';

const router = useRouter();
const { t } = useI18n();

// 列表数据
const knowledgeBaseList = ref<KnowledgeBase[]>([]);
const loading = ref(false);
const searchKeyword = ref('');

// 表单弹窗
const formVisible = ref(false);
const currentKnowledgeBase = ref<KnowledgeBase | null>(null);

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

// 加载知识库列表
const loadList = async () => {
  loading.value = true;
  try {
    const list = await getKnowledgeBaseList();
    knowledgeBaseList.value = list;
  } catch (error: any) {
    message.error('Failed to load knowledge bases: ' + (error?.message || 'Unknown error'));
  } finally {
    loading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  // Search is handled by computed property
};

// 创建知识库
const handleCreate = async () => {
  formVisible.value = false;
  currentKnowledgeBase.value = null;
  await nextTick();
  formVisible.value = true;
};

// 查看知识库
const handleView = (record: KnowledgeBase) => {
  router.push(`/knowledge-bases/${record.id}`);
};

// 编辑知识库
const handleEdit = async (record: KnowledgeBase) => {
  if (formVisible.value) {
    formVisible.value = false;
    await nextTick();
  }
  currentKnowledgeBase.value = { ...record };
  await nextTick();
  formVisible.value = true;
};

// 删除知识库
const handleDelete = async (record: KnowledgeBase) => {
  try {
    await deleteKnowledgeBase(record.id);
    message.success('Database deleted successfully');
    loadList();
  } catch (error: any) {
    message.error('Failed to delete: ' + (error?.message || 'Unknown error'));
  }
};

// 表单成功回调
const handleFormSuccess = () => {
  formVisible.value = false;
  currentKnowledgeBase.value = null;
  loadList();
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

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;

  .action-left {
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

    .page-title {
      margin: 0;
      display: flex;
      align-items: center;
      gap: 12px;
      
      .title-icon {
        color: #60A5FA;
        font-size: 24px;
        filter: drop-shadow(0 0 8px rgba(96, 165, 250, 0.5));
      }
      
      .title-text {
        font-family: 'JetBrains Mono', monospace;
        font-size: 24px;
        font-weight: 700;
        color: #fff;
        letter-spacing: 1px;
        text-shadow: 0 0 10px rgba(96, 165, 250, 0.3);
      }
    }
  }

  .action-right {
    display: flex;
    align-items: center;
    gap: 16px;
    
    .search-wrapper {
      position: relative;
      width: 300px;
      
      .tech-input {
        background: rgba(15, 23, 42, 0.6);
        border: 1px solid rgba(59, 130, 246, 0.3);
        border-radius: 4px;
        color: #fff;
        font-family: 'JetBrains Mono', monospace;
        
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
          border-color: rgba(59, 130, 246, 0.6);
          box-shadow: 0 0 10px rgba(59, 130, 246, 0.1);
        }
      }
    }
    
    .tech-btn {
      height: 36px;
      border-radius: 4px;
      font-family: 'JetBrains Mono', monospace;
      font-weight: 600;
      font-size: 12px;
      letter-spacing: 0.5px;
      
      &.primary {
        background: rgba(59, 130, 246, 0.2);
        border: 1px solid rgba(59, 130, 246, 0.5);
        color: #60A5FA;
        box-shadow: 0 0 10px rgba(59, 130, 246, 0.2);
        
        &:hover {
          background: rgba(59, 130, 246, 0.3);
          border-color: #60A5FA;
          color: #fff;
          box-shadow: 0 0 15px rgba(59, 130, 246, 0.4);
        }
      }
    }
  }
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
}

.kb-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  backdrop-filter: blur(10px);
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  min-height: 200px;
  
  &:hover {
    transform: translateY(-4px);
    border-color: rgba(59, 130, 246, 0.5);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3), 0 0 15px rgba(59, 130, 246, 0.1);
    background: rgba(15, 23, 42, 0.7);
    
    .kb-icon {
      color: #60A5FA;
      text-shadow: 0 0 10px rgba(59, 130, 246, 0.5);
    }
    
    .kb-status-line {
      width: 100%;
      opacity: 1;
    }
  }
  
  .kb-card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
    
    .kb-icon {
      font-size: 24px;
      color: rgba(96, 165, 250, 0.5);
      transition: all 0.3s ease;
    }
    
    .kb-actions {
      display: flex;
      gap: 12px;
      opacity: 0;
      transform: translateX(10px);
      transition: all 0.3s ease;
      
      .action-icon {
        color: rgba(148, 163, 184, 0.6);
        font-size: 16px;
        transition: color 0.2s;
        
        &:hover {
          color: #60A5FA;
        }
        
        &.danger:hover {
          color: #EF4444;
        }
      }
    }
  }
  
  &:hover .kb-actions {
    opacity: 1;
    transform: translateX(0);
  }
  
  .kb-info {
    flex: 1;
    
    .kb-name {
      font-family: 'JetBrains Mono', monospace;
      font-size: 16px;
      color: #fff;
      margin-bottom: 8px;
      font-weight: 600;
    }
    
    .kb-desc {
      font-size: 13px;
      color: rgba(148, 163, 184, 0.8);
      line-height: 1.5;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }
  
  .kb-meta {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid rgba(255, 255, 255, 0.05);
    display: flex;
    justify-content: space-between;
    
    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
      
      .label {
        font-size: 10px;
        color: rgba(148, 163, 184, 0.5);
        font-family: 'JetBrains Mono', monospace;
        text-transform: uppercase;
      }
      
      .value {
        font-size: 12px;
        color: #94a3b8;
        font-family: 'JetBrains Mono', monospace;
      }
    }
  }
  
  .kb-status-line {
    position: absolute;
    bottom: 0;
    left: 0;
    height: 2px;
    width: 0;
    background: linear-gradient(90deg, #3B82F6, #60A5FA);
    opacity: 0;
    transition: all 0.3s ease;
  }
}

.create-card {
  border-style: dashed;
  background: rgba(255, 255, 255, 0.02);
  align-items: center;
  justify-content: center;
  
  &:hover {
    border-color: #60A5FA;
    background: rgba(59, 130, 246, 0.05);
    
    .create-content {
      transform: scale(1.05);
      
      .create-icon, .create-text {
        color: #60A5FA;
      }
    }
  }
  
  .create-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    transition: all 0.3s ease;
    
    .create-icon {
      font-size: 32px;
      color: rgba(96, 165, 250, 0.4);
    }
    
    .create-text {
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
      color: rgba(96, 165, 250, 0.6);
      font-weight: 600;
      letter-spacing: 1px;
    }
  }
}
</style>

