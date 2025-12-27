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
          步骤 {{ step.stepProgress.current }}/{{ step.stepProgress.total }}
        </a-tag>

        <!-- 工具名称标签 -->
        <a-tag v-if="step.type === 'tool_call' && step.metadata?.toolName" size="small" color="blue">
          {{ step.metadata.toolName }}
        </a-tag>

        <!-- 检索数量标签 -->
        <a-tag v-if="step.type === 'rag_retrieve' && step.metadata?.retrieveCount" size="small" color="green">
          {{ step.metadata.retrieveCount }}条知识
        </a-tag>

        <!-- 子步骤数量提示 -->
        <span v-if="hasSubSteps && !step.expanded" class="substeps-count">
          ({{ step.subSteps!.length }}条记录)
        </span>
      </div>

      <div class="step-right">
        <!-- 耗时 -->
        <span v-if="step.duration" class="step-duration">
          {{ formatDuration(step.duration) }}
        </span>
        <span v-else-if="step.status === 'running'" class="step-duration running">
          执行中...
        </span>

        <!-- 展开图标 -->
        <Icon
          v-if="hasDetails"
          :icon="step.expanded ? 'ant-design:up-outlined' : 'ant-design:down-outlined'"
          class="expand-icon"
        />
      </div>
    </div>

    <!-- 步骤详情 -->
    <transition name="slide-fade">
      <div v-if="step.expanded && hasDetails" class="step-details">
        <!-- 子步骤列表（思考步骤） -->
        <template v-if="hasSubSteps">
          <div class="detail-section">
            <div class="detail-label">📋 执行过程</div>
            <div class="substeps-list">
              <div v-for="(subStep, idx) in step.subSteps" :key="subStep.id" class="substep-item">
                <div class="substep-message">{{ subStep.message }}</div>
                
                <!-- 步骤进度信息 -->
                <div v-if="subStep.stepProgress" class="substep-progress">
                  <a-tag size="small" color="blue">
                    步骤 {{ subStep.stepProgress.current }}/{{ subStep.stepProgress.total }}: {{ subStep.stepProgress.description }}
                  </a-tag>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 规划信息 -->
        <template v-if="step.planInfo && step.planInfo.steps">
          <div class="detail-section">
            <div class="detail-label">📋 执行计划</div>
            <div class="plan-info">
              <div v-if="step.planInfo.taskType" class="plan-type">
                任务类型: <a-tag size="small">{{ step.planInfo.taskType }}</a-tag>
              </div>
              <div v-if="step.planInfo.planId" class="plan-id">
                规划ID: <span class="plan-id-text">{{ step.planInfo.planId }}</span>
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
            <div class="detail-label">📝 调用参数</div>
            <pre class="detail-code">{{ formatJson(step.metadata.toolParams) }}</pre>
          </div>

          <!-- 结果 -->
          <div v-if="step.metadata?.toolResult" class="detail-section">
            <div class="detail-label">📊 执行结果</div>
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
            <div class="detail-label">📚 检索结果</div>
            <div class="rag-results-list">
              <div v-for="(item, idx) in step.metadata.ragResults" :key="idx" class="rag-result-item">
                <div class="rag-content">{{ item.content }}</div>
                <div class="rag-meta">
                  <a-tag v-if="item.score" size="small" color="green">
                    相似度: {{ (item.score * 100).toFixed(1) }}%
                  </a-tag>
                  <span v-if="item.source" class="rag-source">来源: {{ item.source }}</span>
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
import { Icon } from '/@/components/Icon';
import type { ProcessStep } from '../agent.types';

const props = defineProps<{
  step: ProcessStep;
}>();

const emit = defineEmits<{
  toggleExpand: [stepId: string];
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
    background: #e8e8e8;
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
  background: #fafafa;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: #f0f0f0;
  }
}

.step-left {
  display: flex;
  align-items: center;
  gap: 8px;
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
  font-size: 16px;
  position: absolute;
  left: 0;
  background: #fff;
  border-radius: 50%;

  &.waiting {
    color: #d9d9d9;
  }

  &.running {
    color: #1890ff;
  }

  &.success {
    color: #52c41a;
  }

  &.error {
    color: #ff4d4f;
  }

  &.skipped {
    color: #faad14;
  }
}

.step-name {
  font-size: 13px;
  color: #262626;
  font-weight: 500;
}

.substeps-count {
  font-size: 12px;
  color: #8c8c8c;
  margin-left: 4px;
}

.step-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.step-duration {
  font-size: 12px;
  color: #8c8c8c;

  &.running {
    color: #1890ff;
  }
}

.expand-icon {
  font-size: 12px;
  color: #8c8c8c;
  transition: transform 0.2s;
}

.step-details {
  margin-top: 8px;
  padding: 12px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.detail-section {
  margin-bottom: 12px;

  &:last-child {
    margin-bottom: 0;
  }
}

.detail-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 6px;
  font-weight: 500;
}

.detail-code {
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  margin: 0;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
}

.rag-results-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rag-result-item {
  padding: 8px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
}

.rag-content {
  font-size: 13px;
  color: #262626;
  line-height: 1.6;
  margin-bottom: 6px;
}

.rag-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.rag-source {
  color: #8c8c8c;
}

// 子步骤列表
.substeps-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.substep-item {
  padding: 6px 10px;
  background: #fafafa;
  border-left: 2px solid #e8e8e8;
  border-radius: 4px;
}

.substep-message {
  font-size: 12px;
  color: #595959;
  line-height: 1.5;
  margin-bottom: 4px;
}

.substep-progress {
  margin-top: 4px;
}

// 规划信息
.plan-info {
  padding: 8px;
  background: #fafafa;
  border-radius: 4px;
}

.plan-type,
.plan-id {
  font-size: 12px;
  color: #595959;
  margin-bottom: 8px;
}

.plan-id-text {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  color: #1890ff;
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
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
}

.plan-step-number {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1890ff;
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
}

.plan-step-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plan-step-desc {
  font-size: 13px;
  color: #262626;
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

