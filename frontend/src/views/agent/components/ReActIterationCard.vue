<template>
  <div class="react-iteration-card" :class="{ 
    running: iteration.status === 'running', 
    completed: iteration.status === 'completed' 
  }">
    <!-- 迭代头部 -->
    <div class="iteration-header" @click="toggleCollapse">
      <div class="header-left">
        <Icon :icon="statusIcon" :class="['status-icon', iteration.status]" :spin="iteration.status === 'running'" />
        <span class="iteration-title">第 {{ iteration.iterationNumber }} 轮推理</span>
        
        <!-- 步骤统计 -->
        <a-tag size="small" :color="iteration.status === 'running' ? 'processing' : 'default'">
          {{ iteration.steps.length }}个步骤
        </a-tag>
        
        <!-- 耗时 -->
        <span v-if="iteration.totalDuration" class="duration-badge">
          {{ formatDuration(iteration.totalDuration) }}
        </span>
        
        <!-- 状态提示（折叠时显示） -->
        <span v-if="iteration.status === 'completed' && iteration.collapsed" class="status-hint">
          {{ iteration.terminationMessage || '完成' }}
        </span>
      </div>
      
      <Icon 
        :icon="iteration.collapsed ? 'ant-design:down-outlined' : 'ant-design:up-outlined'" 
        class="collapse-icon"
      />
    </div>

    <!-- 迭代内容（步骤列表） -->
    <transition name="collapse">
      <div v-show="!iteration.collapsed" class="iteration-body">
        <ProcessStep
          v-for="step in iteration.steps"
          :key="step.id"
          :step="step"
          @toggle-expand="$emit('toggle-step-expand', step.id)"
          @confirm-tool="$emit('confirm-tool')"
          @reject-tool="$emit('reject-tool')"
        />
        
        <!-- 迭代结束提示 -->
        <div v-if="iteration.status === 'completed'" class="iteration-footer">
          <div v-if="iteration.shouldContinue === false" class="termination-notice">
            <Icon icon="ant-design:check-circle-filled" />
            <span>{{ iteration.terminationMessage || '推理结束' }}</span>
          </div>
          <div v-else class="continuation-notice">
            <Icon icon="ant-design:arrow-right-outlined" />
            <span>{{ iteration.terminationMessage || '继续下一轮' }}</span>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Icon } from '@/components/Icon';
import ProcessStep from './ProcessStep.vue';
import type { ReActIteration } from '../agent.types';

const props = defineProps<{
  iteration: ReActIteration;
}>();

const emit = defineEmits<{
  toggleStepExpand: [stepId: string];
  confirmTool: [];
  rejectTool: [];
}>();

const statusIcon = computed(() => {
  return props.iteration.status === 'running' 
    ? 'ant-design:sync-outlined' 
    : 'ant-design:check-circle-outlined';
});

const toggleCollapse = () => {
  props.iteration.collapsed = !props.iteration.collapsed;
};

const formatDuration = (ms: number) => {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
};
</script>

<style scoped lang="less">
.react-iteration-card {
  margin-bottom: 12px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fff;
  transition: all 0.3s;

  &.running {
    border-color: #1890ff;
    box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
  }

  &.completed {
    border-color: #d9d9d9;
  }

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  }
}

.iteration-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  cursor: pointer;
  background: linear-gradient(135deg, #f6f8fa 0%, #ffffff 100%);
  transition: background 0.2s;
  
  &:hover {
    background: linear-gradient(135deg, #eef1f5 0%, #f6f8fa 100%);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.status-icon {
  font-size: 18px;
  flex-shrink: 0;
  
  &.running {
    color: #1890ff;
  }
  
  &.completed {
    color: #52c41a;
  }
}

.iteration-title {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  flex-shrink: 0;
}

.duration-badge {
  font-size: 12px;
  color: #8c8c8c;
  padding: 2px 8px;
  background: #f0f0f0;
  border-radius: 10px;
  font-weight: 500;
}

.status-hint {
  font-size: 12px;
  color: #8c8c8c;
  font-style: italic;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.collapse-icon {
  font-size: 12px;
  color: #8c8c8c;
  flex-shrink: 0;
}

.iteration-body {
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.iteration-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e8e8e8;
}

.termination-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
  color: #52c41a;
  font-size: 13px;
  font-weight: 500;
}

.continuation-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
  color: #1890ff;
  font-size: 13px;
  font-weight: 500;
}

.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.3s ease;
  max-height: 2000px;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
