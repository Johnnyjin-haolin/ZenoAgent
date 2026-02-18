<template>
  <div class="react-iteration-card" :class="{ 
    running: iteration.status === 'running', 
    completed: iteration.status === 'completed' 
  }">
    <!-- 迭代头部 -->
    <div class="iteration-header" @click="toggleCollapse">
      <div class="header-left">
        <Icon :icon="statusIcon" :class="['status-icon', iteration.status]" :spin="iteration.status === 'running'" />
        <span class="iteration-title">{{ t('agent.process.iterationTitle', { number: iteration.iterationNumber }) }}</span>
        
        <!-- 步骤统计 -->
        <a-tag size="small" :color="iteration.status === 'running' ? 'processing' : 'default'" class="step-count-tag">
          {{ t('agent.process.stepCount', { count: iteration.steps.length }) }}
        </a-tag>
        
        <!-- 耗时 -->
        <span v-if="iteration.totalDuration" class="duration-badge">
          {{ formatDuration(iteration.totalDuration) }}
        </span>
        
        <!-- 状态提示（折叠时显示） -->
      <span v-if="iteration.status === 'completed' && iteration.collapsed" class="status-hint">
        {{ displayMessage || t('agent.process.completed') }}
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
        @toggle-expand="$emit('toggleStepExpand', step.id)"
        @confirm-tool="$emit('confirmTool')"
        @reject-tool="$emit('rejectTool')"
      />
      
      <!-- 迭代结束提示 -->
      <div v-if="iteration.status === 'completed'" class="iteration-footer">
        <div v-if="iteration.shouldContinue === false" class="termination-notice">
          <Icon icon="ant-design:check-circle-filled" />
          <span>{{ displayMessage || t('agent.process.reasoningEnded') }}</span>
        </div>
        <div v-else class="continuation-notice">
          <Icon icon="ant-design:arrow-right-outlined" />
          <span>{{ displayMessage || t('agent.process.continueNext') }}</span>
        </div>
      </div>
    </div>
  </transition>
</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import ProcessStep from './ProcessStep.vue';
import type { ReActIteration } from '../agent.types';

const { t } = useI18n();

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

const displayMessage = computed(() => {
  const { terminationReason, terminationMessage } = props.iteration;
  
  if (terminationReason) {
    return t(`agent.process.termination.${terminationReason}`);
  }
  
  if (terminationMessage === 'CONTINUE') {
    return t('agent.process.termination.CONTINUE');
  }
  
  return terminationMessage;
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
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.4);
  transition: all 0.3s;
  overflow: hidden;

  &.running {
    border-color: rgba(59, 130, 246, 0.4);
    box-shadow: 0 0 15px rgba(59, 130, 246, 0.1);
    background: rgba(15, 23, 42, 0.6);
  }

  &.completed {
    border-color: rgba(16, 185, 129, 0.2);
  }

  &:hover {
    background: rgba(15, 23, 42, 0.6);
    border-color: rgba(59, 130, 246, 0.3);
  }
}

.iteration-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.02);
  transition: background 0.2s;
  
  &:hover {
    background: rgba(255, 255, 255, 0.04);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.status-icon {
  font-size: 16px;
  flex-shrink: 0;
  
  &.running {
    color: #60A5FA;
  }
  
  &.completed {
    color: #10B981;
  }
}

.iteration-title {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  flex-shrink: 0;
  font-family: 'JetBrains Mono', monospace;
}

.step-count-tag {
  color: #94a3b8; // 默认浅灰色
  background: rgba(148, 163, 184, 0.1);
  border-color: rgba(148, 163, 184, 0.2);
  
  &.ant-tag-processing {
    color: #60A5FA; // 进行中为蓝色
    background: rgba(59, 130, 246, 0.1);
    border-color: rgba(59, 130, 246, 0.2);
  }
}

.duration-badge {
  font-size: 11px;
  color: #94a3b8;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
}

.status-hint {
  font-size: 11px;
  color: #64748b;
  font-style: italic;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.collapse-icon {
  font-size: 12px;
  color: #64748b;
  flex-shrink: 0;
  transition: transform 0.3s;
}

.iteration-body {
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.2);
}

.iteration-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
}

.termination-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: 4px;
  color: #34D399;
  font-size: 12px;
  font-weight: 500;
  font-family: 'JetBrains Mono', monospace;
}

.continuation-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 4px;
  color: #60A5FA;
  font-size: 12px;
  font-weight: 500;
  font-family: 'JetBrains Mono', monospace;
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
