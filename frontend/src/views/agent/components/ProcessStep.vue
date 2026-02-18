<template>
  <div class="process-step" :class="[step.status, { expanded: step.expanded }]">
    <!-- 步骤主体 -->
    <div class="step-main" @click="toggleExpand">
      <div class="step-left">
        <!-- 状态图标 -->
        <div class="step-icon" :class="step.status">
          <Icon v-if="step.status === 'running'" icon="ant-design:loading-outlined" :spin="true" />
          <Icon v-else-if="step.status === 'success'" icon="ant-design:check-circle-filled" />
          <Icon v-else-if="step.status === 'error'" icon="ant-design:close-circle-filled" />
          <Icon v-else-if="step.status === 'skipped'" icon="ant-design:pause-circle-filled" />
          <Icon v-else icon="ant-design:clock-circle-outlined" />
        </div>

        <!-- 步骤名称 -->
        <span class="step-name">{{ step.name }}</span>

        <!-- 步骤进度标签（如"步骤 1/3"） -->
        <a-tag v-if="step.stepProgress" size="small" color="blue">
          {{ t('agent.process.stepProgress', { current: step.stepProgress.current, total: step.stepProgress.total }) }}
        </a-tag>

        <!-- 工具名称标签 -->
        <a-tag v-if="step.type === 'tool_call' && step.metadata?.toolName" size="small" color="blue">
          {{ step.metadata.toolName }}
        </a-tag>

        <!-- 检索数量标签 -->
        <a-tag v-if="step.type === 'rag_retrieve' && step.metadata?.retrieveCount" size="small" color="green">
          {{ t('agent.process.retrieveCount', { count: step.metadata.retrieveCount }) }}
        </a-tag>

        <!-- 子步骤数量提示 -->
        <span v-if="hasSubSteps && !step.expanded" class="substeps-count">
          {{ t('agent.process.subStepsCount', { count: step.subSteps!.length }) }}
        </span>
      </div>

      <div class="step-right">
        <!-- 耗时 -->
        <span v-if="step.duration" class="step-duration">
          {{ formatDuration(step.duration) }}
        </span>
        <span v-else-if="step.status === 'running'" class="step-duration running">
          {{ t('agent.process.running') }}
        </span>

        <!-- 展开图标 -->
        <Icon
          v-if="hasDetails"
          :icon="step.expanded ? 'ant-design:up-outlined' : 'ant-design:down-outlined'"
          class="expand-icon"
        />
      </div>
    </div>

    <!-- 工具确认（紧凑内联） -->
    <div v-if="shouldConfirmTool" class="tool-confirm-row">
      <div class="tool-confirm-left">
        <span class="tool-confirm-icon">🔧</span>
        <span class="tool-confirm-name">{{ step.metadata?.toolName }}</span>
        <span class="tool-confirm-params">{{ t('agent.process.toolParams', { params: toolParamsSummary }) }}</span>
      </div>
      <div class="tool-confirm-actions">
        <a-button type="primary" size="small" @click.stop="emitConfirm">
          {{ t('agent.process.confirmExecute') }}
        </a-button>
        <a-button size="small" danger @click.stop="emitReject">
          {{ t('agent.process.cancel') }}
        </a-button>
      </div>
    </div>

    <!-- 步骤详情 -->
    <transition name="slide-fade">
      <div v-if="step.expanded && hasDetails" class="step-details">
        <!-- 子步骤列表（思考步骤） -->
        <template v-if="hasSubSteps">
          <div class="detail-section">
            <div class="detail-label">{{ t('agent.process.executionProcess') }}</div>
            <div class="substeps-list">
              <div v-for="(subStep, idx) in step.subSteps" :key="subStep.id" class="substep-item">
                <div class="substep-message">{{ subStep.message }}</div>
                
                <!-- 步骤进度信息 -->
                <div v-if="subStep.stepProgress" class="substep-progress">
                  <a-tag size="small" color="blue">
                    {{ t('agent.process.subStepProgress', { current: subStep.stepProgress.current, total: subStep.stepProgress.total, description: subStep.stepProgress.description }) }}
                  </a-tag>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 规划信息 -->
        <template v-if="step.planInfo && step.planInfo.steps">
          <div class="detail-section">
            <div class="detail-label">{{ t('agent.process.executionPlan') }}</div>
            <div class="plan-info">
              <div v-if="step.planInfo.taskType" class="plan-type">
                {{ t('agent.process.taskType') }} <a-tag size="small">{{ step.planInfo.taskType }}</a-tag>
              </div>
              <div v-if="step.planInfo.planId" class="plan-id">
                {{ t('agent.process.planId') }} <span class="plan-id-text">{{ step.planInfo.planId }}</span>
              </div>
              <div class="plan-steps">
                <div
                  v-for="(planStep, idx) in step.planInfo.steps"
                  :key="planStep.stepId"
                  class="plan-step-item"
                >
                  <div class="plan-step-number">{{ planStep.stepNumber }}</div>
                  <div class="plan-step-content">
                    <div class="plan-step-desc">{{ planStep.description }}</div>
                    <a-tag size="small" color="default">{{ planStep.type }}</a-tag>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 工具调用详情 -->
        <template v-if="step.type === 'tool_call'">
          <!-- 参数 -->
          <div v-if="step.metadata?.toolParams" class="detail-section">
            <div class="detail-label">{{ t('agent.process.callParams') }}</div>
            <pre class="detail-code">{{ formatJson(step.metadata.toolParams) }}</pre>
          </div>

          <!-- 结果 -->
          <div v-if="step.metadata?.toolResult" class="detail-section">
            <div class="detail-label">{{ t('agent.process.executionResult') }}</div>
            <pre class="detail-code">{{ formatResult(step.metadata.toolResult) }}</pre>
          </div>

          <!-- 错误 -->
          <div v-if="step.metadata?.toolError" class="detail-section">
            <a-alert type="error" :message="step.metadata.toolError" show-icon />
          </div>
        </template>

        <!-- RAG 检索详情 -->
        <template v-if="step.type === 'rag_retrieve' && step.metadata?.ragResults">
          <div class="detail-section">
            <div class="detail-label">{{ t('agent.process.ragResults') }}</div>
            <div class="rag-results-list">
              <div v-for="(item, idx) in step.metadata.ragResults" :key="idx" class="rag-result-item">
                <div class="rag-content">{{ item.content }}</div>
                <div class="rag-meta">
                  <a-tag v-if="item.score" size="small" color="green">
                    {{ t('agent.process.similarity', { score: (item.score * 100).toFixed(1) }) }}
                  </a-tag>
                  <span v-if="item.source" class="rag-source">{{ t('agent.process.source', { source: item.source }) }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 错误信息 -->
        <div v-if="step.metadata?.errorMessage" class="detail-section">
          <a-alert type="error" :message="step.metadata.errorMessage" show-icon />
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Icon } from '@/components/Icon';
import type { ProcessStep } from '../agent.types';

const { t } = useI18n();

const props = defineProps<{
  step: ProcessStep;
}>();

const emit = defineEmits<{
  toggleExpand: [stepId: string];
  confirmTool: [];
  rejectTool: [];
}>();

// 是否有子步骤
const hasSubSteps = computed(() => {
  return !!(props.step.subSteps && props.step.subSteps.length > 0);
});

// 是否有规划信息
const hasPlanInfo = computed(() => {
  return !!(props.step.planInfo && props.step.planInfo.steps && props.step.planInfo.steps.length > 0);
});

// 是否有详情可展开
const hasDetails = computed(() => {
  // 如果有子步骤或规划信息，可以展开
  if (hasSubSteps.value || hasPlanInfo.value) {
    return true;
  }

  const meta = props.step.metadata;
  if (!meta) return false;

  // 工具调用有参数或结果
  if (props.step.type === 'tool_call') {
    return !!(meta.toolParams || meta.toolResult || meta.toolError);
  }

  // RAG 检索有结果
  if (props.step.type === 'rag_retrieve') {
    return !!(meta.ragResults && meta.ragResults.length > 0);
  }

  // 有错误信息
  return !!meta.errorMessage;
});

const shouldConfirmTool = computed(() => {
  return props.step.type === 'tool_call' &&
    props.step.status === 'waiting' &&
    props.step.metadata?.requiresConfirmation;
});

const toolParamsSummary = computed(() => {
  const params = props.step.metadata?.toolParams || {};
  const raw = JSON.stringify(params);
  if (raw.length <= 80) {
    return raw;
  }
  return raw.slice(0, 80) + '...';
});

const emitConfirm = () => {
  emit('confirmTool');
};

const emitReject = () => {
  emit('rejectTool');
};

// 切换展开状态
const toggleExpand = () => {
  if (hasDetails.value) {
    emit('toggleExpand', props.step.id);
  }
};

// 格式化耗时
const formatDuration = (ms: number) => {
  if (ms < 1000) {
    return `${ms}ms`;
  }
  return `${(ms / 1000).toFixed(1)}s`;
};

// 格式化 JSON
const formatJson = (obj: any) => {
  return JSON.stringify(obj, null, 2);
};

// 格式化结果
const formatResult = (result: any) => {
  if (typeof result === 'string') {
    // 如果是字符串，尝试截断过长的内容
    if (result.length > 500) {
      return result.substring(0, 500) + '\n... (内容过长，已截断)';
    }
    return result;
  }
  return JSON.stringify(result, null, 2);
};
</script>

<style scoped lang="less">
.process-step {
  position: relative;
  padding-left: 28px;
  margin-bottom: 12px;

  &::before {
    content: '';
    position: absolute;
    left: 10px;
    top: 28px;
    bottom: -12px;
    width: 1px;
    background: rgba(255, 255, 255, 0.1);
  }

  &:last-child::before {
    display: none;
  }
}

.step-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;

  &:hover {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(59, 130, 246, 0.2);
  }
}

.step-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.step-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  position: absolute;
  left: 0;
  background: #0f172a;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.1);
  z-index: 2;

  &.waiting {
    color: #64748b;
    border-color: #64748b;
  }

  &.running {
    color: #60A5FA;
    border-color: #60A5FA;
    box-shadow: 0 0 8px rgba(59, 130, 246, 0.4);
  }

  &.success {
    color: #10B981;
    border-color: #10B981;
  }

  &.error {
    color: #EF4444;
    border-color: #EF4444;
  }

  &.skipped {
    color: #F59E0B;
    border-color: #F59E0B;
  }
}

.step-name {
  font-size: 13px;
  color: #e2e8f0;
  font-weight: 500;
  font-family: 'JetBrains Mono', monospace;
}

.substeps-count {
  font-size: 11px;
  color: #64748b;
  margin-left: 4px;
}

.step-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.step-duration {
  font-size: 11px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;

  &.running {
    color: #60A5FA;
  }
}

.expand-icon {
  font-size: 10px;
  color: #64748b;
  transition: transform 0.2s;
}

.tool-confirm-row {
  margin-top: 8px;
  padding: 8px 10px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.tool-confirm-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;

  .tool-confirm-name {
    font-weight: 600;
    color: #FBBF24;
    flex-shrink: 0;
    font-family: 'JetBrains Mono', monospace;
  }

  .tool-confirm-params {
    color: #94a3b8;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-family: 'JetBrains Mono', monospace;
  }
}

.tool-confirm-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.step-details {
  margin-top: 8px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 6px;
}

.detail-section {
  margin-bottom: 12px;

  &:last-child {
    margin-bottom: 0;
  }
}

.detail-label {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 6px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.detail-code {
  padding: 10px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 4px;
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  margin: 0;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
  color: #94a3b8;
}

.rag-results-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rag-result-item {
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 4px;
}

.rag-content {
  font-size: 12px;
  color: #cbd5e1;
  line-height: 1.6;
  margin-bottom: 6px;
}

.rag-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.rag-source {
  color: #64748b;
}

// 子步骤列表
.substeps-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.substep-item {
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.02);
  border-left: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}

.substep-message {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
  margin-bottom: 4px;
}

.substep-progress {
  margin-top: 4px;
}

// 规划信息
.plan-info {
  padding: 8px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 4px;
}

.plan-type,
.plan-id {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 8px;
}

.plan-id-text {
  font-family: 'JetBrains Mono', monospace;
  color: #60A5FA;
}

.plan-steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.plan-step-item {
  display: flex;
  gap: 10px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 4px;
}

.plan-step-number {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 130, 246, 0.2);
  color: #60A5FA;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.plan-step-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plan-step-desc {
  font-size: 12px;
  color: #e2e8f0;
  flex: 1;
}

// 动画
.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.2s ease-in;
}

.slide-fade-enter-from {
  transform: translateY(-10px);
  opacity: 0;
}

.slide-fade-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}
</style>

