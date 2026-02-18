<template>
  <div class="process-card">
    <!-- ReAct 模式：显示迭代列表 -->
    <div class="react-mode-container">
      <div class="react-mode-header">
        <Icon icon="ant-design:sync-outlined" class="react-icon" :spin="loading" />
        <span class="react-title">{{ t('agent.process.reactTitle') }}</span>
        <a-tag size="small" color="blue">{{ t('agent.process.iterationCount', { count: iterationCount }) }}</a-tag>
        
        <span v-if="statusText" class="header-status-text" :class="status">
          <span class="separator">|</span>
          <span class="text">{{ statusText }}</span>
        </span>
        <span v-else class="header-spacer"></span>

        <span v-if="process.totalDuration" class="total-duration">
          {{ t('agent.process.totalDuration', { duration: formatDuration(process.totalDuration) }) }}
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
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import ReActIterationCard from './ReActIterationCard.vue';
import type { ExecutionProcess } from '../agent.types';

const { t } = useI18n();

const props = defineProps<{
  process: ExecutionProcess;
  loading?: boolean;
  statusText?: string;
  status?: string;
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
  width: 100%;
}

.react-mode-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  margin-bottom: 12px;
  backdrop-filter: blur(5px);
}

.react-icon {
  font-size: 18px;
  color: #60A5FA;
  flex-shrink: 0;
}

.react-title {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  flex-shrink: 0;
  font-family: 'JetBrains Mono', monospace;
}


.header-status-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
  
  .separator {
    color: rgba(255, 255, 255, 0.1);
  }
  
  &.thinking, &.retrieving, &.calling_tool {
    color: #60A5FA;
    animation: pulse 2s infinite;
  }
  
  &.generating {
    color: #10B981;
  }
  
  &.error {
    color: #EF4444;
  }

  /* Special style for waiting confirmation */
  &.waiting-confirm {
    color: #F59E0B;
  }
}

.header-spacer {
  flex: 1;
}

.total-duration {
  margin-left: 0; /* Reset auto margin since we have flex 1 status text */
  flex-shrink: 0;
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
  font-family: 'JetBrains Mono', monospace;
  background: rgba(0, 0, 0, 0.2);
  padding: 2px 8px;
  border-radius: 4px;
}

@keyframes pulse {
  0% { opacity: 0.6; }
  50% { opacity: 1; }
  100% { opacity: 0.6; }
}
</style>


