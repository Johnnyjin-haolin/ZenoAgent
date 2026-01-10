<template>
  <a-card title="统计信息" class="stats-card">
    <a-row :gutter="16">
      <a-col :span="6">
        <a-statistic
          title="总文档数"
          :value="stats.totalDocuments"
          :loading="loading"
        >
          <template #prefix>
            <Icon icon="ant-design:file-outlined" />
          </template>
        </a-statistic>
      </a-col>
      <a-col :span="6">
        <a-statistic
          title="已完成"
          :value="stats.completedDocuments"
          :value-style="{ color: '#3f8600' }"
          :loading="loading"
        >
          <template #prefix>
            <Icon icon="ant-design:check-circle-outlined" />
          </template>
        </a-statistic>
      </a-col>
      <a-col :span="6">
        <a-statistic
          title="处理中"
          :value="stats.buildingDocuments"
          :value-style="{ color: '#1890ff' }"
          :loading="loading"
        >
          <template #prefix>
            <Icon icon="ant-design:loading-outlined" />
          </template>
        </a-statistic>
      </a-col>
      <a-col :span="6">
        <a-statistic
          title="失败"
          :value="stats.failedDocuments"
          :value-style="{ color: '#cf1322' }"
          :loading="loading"
        >
          <template #prefix>
            <Icon icon="ant-design:close-circle-outlined" />
          </template>
        </a-statistic>
      </a-col>
    </a-row>

    <!-- 进度条 -->
    <div v-if="stats.totalDocuments > 0" class="progress-section">
      <a-progress
        :percent="progressPercent"
        :status="progressStatus"
        :stroke-color="progressColor"
      />
      <div class="progress-text">
        向量化进度: {{ stats.completedDocuments }} / {{ stats.totalDocuments }}
      </div>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { Icon } from '@/components/Icon';
import { getKnowledgeBaseStats } from '@/api/knowledge-base.api';
import type { KnowledgeBaseStats } from '@/types/knowledge-base.types';

const props = defineProps<{
  knowledgeBaseId: string;
}>();

const emit = defineEmits<{
  (e: 'refresh'): void;
}>();

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

// 进度状态
const progressStatus = computed(() => {
  if (stats.value.failedDocuments > 0) {
    return 'exception' as const;
  }
  if (stats.value.buildingDocuments > 0) {
    return 'active' as const;
  }
  return 'success' as const;
});

// 进度颜色
const progressColor = computed(() => {
  if (stats.value.failedDocuments > 0) {
    return '#ff4d4f';
  }
  return '#1890ff';
});

// 加载统计信息
const loadStats = async () => {
  loading.value = true;
  try {
    const data = await getKnowledgeBaseStats(props.knowledgeBaseId);
    stats.value = data;
  } catch (error: any) {
    console.error('加载统计信息失败:', error);
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
  margin-bottom: 16px;
  background: #fff;

  .progress-section {
    margin-top: 24px;
    padding-top: 24px;
    border-top: 1px solid #f0f0f0;

    .progress-text {
      margin-top: 8px;
      font-size: 12px;
      color: #8c8c8c;
      text-align: center;
    }
  }
}
</style>

