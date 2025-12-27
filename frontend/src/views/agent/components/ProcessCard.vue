<template>
  <div class="process-card">
    <!-- 卡片头部 -->
    <div class="process-header" @click="toggleCollapse">
      <div class="header-left">
        <Icon icon="ant-design:thunderbolt-outlined" class="process-icon" />
        <span class="process-title">执行过程</span>
        
        <!-- 进度标签 -->
        <a-tag v-if="completedCount > 0" size="small" :color="isAllCompleted ? 'success' : 'processing'">
          {{ completedCount }}/{{ totalSteps }} 步骤
        </a-tag>

        <!-- 工具调用统计 -->
        <span v-if="toolCallCount > 0 && process.collapsed" class="header-meta">
          · 调用{{ toolCallCount }}个工具
        </span>
      </div>

      <div class="header-right">
        <!-- 总耗时 -->
        <span v-if="process.totalDuration" class="duration-text">
          耗时 {{ formatDuration(process.totalDuration) }}
        </span>

        <!-- 折叠图标 -->
        <Icon 
          :icon="process.collapsed ? 'ant-design:down-outlined' : 'ant-design:up-outlined'" 
          class="collapse-icon"
        />
      </div>
    </div>

    <!-- 步骤列表 -->
    <transition name="collapse">
      <div v-show="!process.collapsed" class="process-body">
        <ProcessStep
          v-for="step in process.steps"
          :key="step.id"
          :step="step"
          @toggle-expand="handleToggleStepExpand"
        />
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Icon } from '/@/components/Icon';
import ProcessStep from './ProcessStep.vue';
import type { ExecutionProcess } from '../agent.types';

const props = defineProps<{
  process: ExecutionProcess;
}>();

const emit = defineEmits<{
  toggleCollapse: [];
  toggleStepExpand: [stepId: string];
}>();

// 总步骤数
const totalSteps = computed(() => props.process.steps.length);

// 完成步骤数
const completedCount = computed(() => {
  return props.process.steps.filter(
    (step) => step.status === 'success' || step.status === 'error' || step.status === 'skipped'
  ).length;
});

// 是否全部完成
const isAllCompleted = computed(() => {
  return completedCount.value === totalSteps.value && totalSteps.value > 0;
});

// 工具调用次数
const toolCallCount = computed(() => {
  return props.process.steps.filter((step) => step.type === 'tool_call').length;
});

// 切换折叠状态
const toggleCollapse = () => {
  emit('toggleCollapse');
};

// 切换步骤展开状态
const handleToggleStepExpand = (stepId: string) => {
  emit('toggleStepExpand', stepId);
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
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  transition: all 0.3s;

  &:hover {
    border-color: #d9d9d9;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }
}

.process-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;

  &:hover {
    background: #fafafa;
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.process-icon {
  font-size: 16px;
  color: #1890ff;
  flex-shrink: 0;
}

.process-title {
  font-size: 13px;
  font-weight: 600;
  color: #262626;
  flex-shrink: 0;
}

.header-meta {
  font-size: 12px;
  color: #8c8c8c;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.duration-text {
  font-size: 12px;
  color: #8c8c8c;
  font-weight: 500;
}

.collapse-icon {
  font-size: 12px;
  color: #8c8c8c;
  transition: transform 0.3s;
}

.process-body {
  padding: 12px 14px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

// 折叠动画
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.3s ease;
  max-height: 1000px;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}
</style>


