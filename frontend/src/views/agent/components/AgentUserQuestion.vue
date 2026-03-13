<template>
  <div v-if="question" class="user-question-overlay">
    <div class="user-question-card">
      <!-- 头部 -->
      <div class="question-header">
        <span class="question-icon">🤔</span>
        <span class="question-title">{{ t('agent.question.title') }}</span>
      </div>

      <!-- 问题内容 -->
      <div class="question-body">
        <p class="question-text">{{ question.question }}</p>

        <!-- PREVIEW：展示预览内容 -->
        <div v-if="question.questionType === 'PREVIEW' && question.previewContent" class="preview-content">
          <pre>{{ question.previewContent }}</pre>
        </div>

        <!-- SINGLE_SELECT：单选 -->
        <div v-if="question.questionType === 'SINGLE_SELECT'" class="options-list">
          <button
            v-for="opt in question.options"
            :key="opt"
            class="option-btn"
            :class="{ selected: selectedOptions.includes(opt) }"
            @click="selectSingle(opt)"
          >
            {{ opt }}
          </button>
        </div>

        <!-- MULTI_SELECT：多选 -->
        <div v-if="question.questionType === 'MULTI_SELECT'" class="options-list">
          <button
            v-for="opt in question.options"
            :key="opt"
            class="option-btn multi"
            :class="{ selected: selectedOptions.includes(opt) }"
            @click="toggleMulti(opt)"
          >
            <span class="check-icon">{{ selectedOptions.includes(opt) ? '✓' : '' }}</span>
            {{ opt }}
          </button>
        </div>

        <!-- INPUT / PREVIEW confirm：文本输入 -->
        <div
          v-if="question.questionType === 'INPUT' || question.questionType === 'PREVIEW'"
          class="input-area"
        >
          <textarea
            v-model="inputValue"
            :placeholder="t('agent.question.inputPlaceholder')"
            rows="3"
            class="answer-input"
          />
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="question-footer">
        <button class="btn btn-cancel" @click="handleSkip">
          {{ t('agent.question.skip') }}
        </button>
        <button class="btn btn-confirm" :disabled="!canSubmit" @click="handleSubmit">
          {{ t('agent.question.submit') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import type { UserQuestion } from '../agent.types';

interface Props {
  question: UserQuestion | null;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'submit', answer: string): void;
}>();

const { t } = useI18n();

const selectedOptions = ref<string[]>([]);
const inputValue = ref('');

watch(
  () => props.question,
  () => {
    selectedOptions.value = [];
    inputValue.value = '';
  }
);

const canSubmit = computed(() => {
  if (!props.question) return false;
  switch (props.question.questionType) {
    case 'SINGLE_SELECT':
      return selectedOptions.value.length === 1;
    case 'MULTI_SELECT':
      return selectedOptions.value.length > 0;
    case 'INPUT':
      return inputValue.value.trim().length > 0;
    case 'PREVIEW':
      return true;
    default:
      return false;
  }
});

function selectSingle(opt: string) {
  selectedOptions.value = [opt];
}

function toggleMulti(opt: string) {
  const idx = selectedOptions.value.indexOf(opt);
  if (idx === -1) {
    selectedOptions.value.push(opt);
  } else {
    selectedOptions.value.splice(idx, 1);
  }
}

function handleSubmit() {
  if (!props.question) return;
  let answer = '';
  if (props.question.questionType === 'SINGLE_SELECT') {
    answer = selectedOptions.value[0] || '';
  } else if (props.question.questionType === 'MULTI_SELECT') {
    answer = selectedOptions.value.join(', ');
  } else {
    answer = inputValue.value.trim();
  }
  emit('submit', answer);
}

function handleSkip() {
  emit('submit', t('agent.question.skipped'));
}
</script>

<style scoped>
.user-question-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  padding: 0 16px 12px;
  z-index: 100;
}

.user-question-card {
  width: 100%;
  max-width: 640px;
  background: var(--color-bg-elevated, #fff);
  border: 1px solid var(--color-border, #e8e8e8);
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  padding: 16px;
  animation: slideUp 0.2s ease-out;
}

@keyframes slideUp {
  from { transform: translateY(12px); opacity: 0; }
  to   { transform: translateY(0);    opacity: 1; }
}

.question-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.question-icon {
  font-size: 18px;
}

.question-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary, #666);
}

.question-body {
  margin-bottom: 14px;
}

.question-text {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text-primary, #1a1a1a);
  margin: 0 0 12px;
  line-height: 1.5;
}

.preview-content {
  background: var(--color-bg-container, #f5f5f5);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
  max-height: 160px;
  overflow-y: auto;
}

.preview-content pre {
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  color: var(--color-text-primary, #1a1a1a);
}

.options-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.option-btn {
  padding: 6px 14px;
  border: 1px solid var(--color-border, #d9d9d9);
  border-radius: 20px;
  background: transparent;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  gap: 4px;
}

.option-btn:hover {
  border-color: #4096ff;
  color: #4096ff;
}

.option-btn.selected {
  border-color: #4096ff;
  background: #e6f4ff;
  color: #1677ff;
  font-weight: 500;
}

.check-icon {
  font-size: 12px;
  min-width: 12px;
}

.input-area {
  margin-top: 4px;
}

.answer-input {
  width: 100%;
  border: 1px solid var(--color-border, #d9d9d9);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  resize: none;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
}

.answer-input:focus {
  border-color: #4096ff;
}

.question-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  padding: 6px 18px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  border: none;
  transition: all 0.15s;
}

.btn-cancel {
  background: transparent;
  color: var(--color-text-secondary, #666);
  border: 1px solid var(--color-border, #d9d9d9);
}

.btn-cancel:hover {
  background: var(--color-bg-container, #f5f5f5);
}

.btn-confirm {
  background: #1677ff;
  color: #fff;
}

.btn-confirm:hover:not(:disabled) {
  background: #4096ff;
}

.btn-confirm:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
