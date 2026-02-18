<template>
  <div class="stats-card">
    <div class="stats-header">
      <div class="header-icon"><Icon icon="ant-design:dashboard-outlined" /></div>
      <div class="header-title">{{ t('knowledgeBase.stats.title') }}</div>
    </div>
    
    <div class="stats-grid">
      <div class="stat-item total">
        <div class="stat-label">{{ t('knowledgeBase.stats.total') }}</div>
        <div class="stat-value">
          <span class="value-text">{{ stats.totalDocuments }}</span>
          <Icon icon="ant-design:file-outlined" class="stat-icon" />
        </div>
      </div>
      
      <div class="stat-item success">
        <div class="stat-label">{{ t('knowledgeBase.stats.indexed') }}</div>
        <div class="stat-value">
          <span class="value-text">{{ stats.completedDocuments }}</span>
          <Icon icon="ant-design:check-circle-outlined" class="stat-icon" />
        </div>
      </div>
      
      <div class="stat-item processing">
        <div class="stat-label">{{ t('knowledgeBase.stats.processing') }}</div>
        <div class="stat-value">
          <span class="value-text">{{ stats.buildingDocuments }}</span>
          <Icon icon="ant-design:loading-outlined" :spin="true" class="stat-icon" />
        </div>
      </div>
      
      <div class="stat-item failed">
        <div class="stat-label">{{ t('knowledgeBase.stats.failed') }}</div>
        <div class="stat-value">
          <span class="value-text">{{ stats.failedDocuments }}</span>
          <Icon icon="ant-design:close-circle-outlined" class="stat-icon" />
        </div>
      </div>
    </div>

    <!-- 进度条 -->
    <div v-if="stats.totalDocuments > 0" class="progress-section">
      <div class="progress-info">
        <span class="progress-label">{{ t('knowledgeBase.stats.progress') }}</span>
        <span class="progress-value">{{ progressPercent }}%</span>
      </div>
      <div class="tech-progress-bar">
        <div 
          class="progress-fill" 
          :style="{ width: `${progressPercent}%`, backgroundColor: progressColor }"
          :class="{ 'glowing': stats.buildingDocuments > 0 }"
        ></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import { getKnowledgeBaseStats } from '@/api/knowledge-base.api';
import type { KnowledgeBaseStats } from '@/types/knowledge-base.types';

const props = defineProps<{
  knowledgeBaseId: string;
}>();

const emit = defineEmits<{
  (e: 'refresh'): void;
}>();

const { t } = useI18n();

const stats = ref<KnowledgeBaseStats>({
  knowledgeBaseId: props.knowledgeBaseId,
  knowledgeBaseName: '',
  totalDocuments: 0,
  completedDocuments: 0,
  failedDocuments: 0,
  buildingDocuments: 0,
});

const loading = ref(false);

// 计算进度百分比
const progressPercent = computed(() => {
  if (stats.value.totalDocuments === 0) return 0;
  return Math.round(
    (stats.value.completedDocuments / stats.value.totalDocuments) * 100
  );
});

// 进度颜色
const progressColor = computed(() => {
  if (stats.value.failedDocuments > 0) {
    return '#EF4444';
  }
  return '#10B981';
});

// 加载统计信息
const loadStats = async () => {
  loading.value = true;
  try {
    const data = await getKnowledgeBaseStats(props.knowledgeBaseId);
    stats.value = data;
  } catch (error: any) {
    console.error('Failed to load stats:', error);
  } finally {
    loading.value = false;
  }
};

// 监听knowledgeBaseId变化
watch(
  () => props.knowledgeBaseId,
  () => {
    if (props.knowledgeBaseId) {
      loadStats();
    }
  },
  { immediate: true }
);

// 暴露刷新方法
defineExpose({
  refresh: loadStats,
});

onMounted(() => {
  if (props.knowledgeBaseId) {
    loadStats();
  }
});
</script>

<style scoped lang="less">
.stats-card {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 24px;
  backdrop-filter: blur(10px);
}

.stats-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(59, 130, 246, 0.2);
  
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
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-item {
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.3s ease;
  
  &:hover {
    background: rgba(0, 0, 0, 0.3);
    transform: translateY(-2px);
  }
  
  .stat-label {
    font-size: 11px;
    color: rgba(148, 163, 184, 0.6);
    font-family: 'JetBrains Mono', monospace;
    letter-spacing: 0.5px;
  }
  
  .stat-value {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .value-text {
      font-size: 24px;
      font-weight: 700;
      font-family: 'JetBrains Mono', monospace;
      color: #fff;
    }
    
    .stat-icon {
      font-size: 20px;
      opacity: 0.5;
    }
  }
  
  &.total {
    border-left: 3px solid #60A5FA;
    .stat-value .stat-icon { color: #60A5FA; }
  }
  
  &.success {
    border-left: 3px solid #10B981;
    .stat-value .stat-icon { color: #10B981; }
    .stat-value .value-text { color: #10B981; }
  }
  
  &.processing {
    border-left: 3px solid #F59E0B;
    .stat-value .stat-icon { color: #F59E0B; }
    .stat-value .value-text { color: #F59E0B; }
  }
  
  &.failed {
    border-left: 3px solid #EF4444;
    .stat-value .stat-icon { color: #EF4444; }
    .stat-value .value-text { color: #EF4444; }
  }
}

.progress-section {
  .progress-info {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
    font-family: 'JetBrains Mono', monospace;
    font-size: 12px;
    
    .progress-label {
      color: rgba(148, 163, 184, 0.8);
    }
    
    .progress-value {
      color: #60A5FA;
      font-weight: 600;
    }
  }
  
  .tech-progress-bar {
    height: 6px;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 3px;
    overflow: hidden;
    position: relative;
    
    .progress-fill {
      height: 100%;
      border-radius: 3px;
      transition: width 0.5s ease;
      box-shadow: 0 0 10px currentColor;
      
      &.glowing {
        animation: progress-glow 2s infinite;
      }
    }
  }
}

@keyframes progress-glow {
  0%, 100% { opacity: 1; box-shadow: 0 0 10px currentColor; }
  50% { opacity: 0.7; box-shadow: 0 0 5px currentColor; }
}
</style>

