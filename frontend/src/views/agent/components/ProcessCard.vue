<template>
  <div class="process-card">
    <!-- ReAct 模式：显示迭代列表 -->
    <div class="react-mode-container">
      <div class="react-mode-header">
        <Icon icon="ant-design:sync-outlined" class="react-icon" />
        <span class="react-title">ReAct 推理过程</span>
        <a-tag size="small" color="blue">{{ iterationCount }}次迭代</a-tag>
        <span v-if="process.totalDuration" class="total-duration">
          总耗时 {{ formatDuration(process.totalDuration) }}
        </span>
      </div>
      
      <div class="react-iterations">
        <ReActIterationCard
          v-for="iteration in process.iterations"
          :key="iteration.iterationNumber"
          :iteration="iteration"
          @toggle-step-expand="handleToggleStepExpand"
          @confirm-tool="handleConfirmTool"
          @reject-tool="handleRejectTool"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Icon } from '@/components/Icon';
import ReActIterationCard from './ReActIterationCard.vue';
import type { ExecutionProcess } from '../agent.types';

const props = defineProps<{
  process: ExecutionProcess;
}>();

const emit = defineEmits<{
  toggleStepExpand: [stepId: string];
  confirmTool: [];
  rejectTool: [];
}>();

// 迭代数量
const iterationCount = computed(() => props.process.iterations?.length || 0);

// 切换步骤展开状态
const handleToggleStepExpand = (stepId: string) => {
  emit('toggleStepExpand', stepId);
};

const handleConfirmTool = () => {
  emit('confirmTool');
};

const handleRejectTool = () => {
  emit('rejectTool');
};

// 格式化耗时
const formatDuration = (ms: number) => {
  if (ms < 1000) {
    return `${ms}ms`;
  }
  return `${(ms / 1000).toFixed(1)}s`;
};
</script>

<style scoped lang="less">
.process-card {
  margin-bottom: 12px;
}

.react-mode-container {
  // 容器样式
}

.react-mode-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #e6f7ff 0%, #f0f5ff 100%);
  border: 1px solid #91d5ff;
  border-radius: 8px;
  margin-bottom: 12px;
}

.react-icon {
  font-size: 18px;
  color: #1890ff;
  flex-shrink: 0;
}

.react-title {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  flex-shrink: 0;
}

.total-duration {
  margin-left: auto;
  font-size: 12px;
  color: #595959;
  font-weight: 500;
}

.react-iterations {
  // 迭代列表容器
}
</style>


