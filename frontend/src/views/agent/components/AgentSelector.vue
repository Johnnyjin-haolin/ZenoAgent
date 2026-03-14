<template>
  <div class="agent-selector">
    <a-select
      v-model:value="selectedAgentId"
      :placeholder="'选择 Agent'"
      :loading="loading"
      allow-clear
      size="small"
      class="agent-select tech-select"
      :dropdown-match-select-width="false"
      dropdown-class-name="agent-select-dropdown tech-dropdown"
      @change="handleChange"
    >
      <a-select-option
        v-for="agent in agents"
        :key="agent.id"
        :value="agent.id"
      >
        <span class="option-name" :title="agent.description || agent.name">
          <span v-if="agent.builtin" class="builtin-badge">系统</span>
          {{ agent.name }}
        </span>
      </a-select-option>
    </a-select>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { getAgentDefinitions } from '../agent.api';
import type { AgentDefinition } from '../agent.types';

const props = withDefaults(
  defineProps<{
    modelValue?: string;
  }>(),
  {}
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | undefined): void;
  (e: 'change', value: string | undefined, agent: AgentDefinition | null): void;
}>();

const selectedAgentId = ref<string | undefined>(props.modelValue);
const agents = ref<AgentDefinition[]>([]);
const loading = ref(false);

watch(
  () => props.modelValue,
  (val) => {
    selectedAgentId.value = val;
  }
);

async function loadAgents() {
  loading.value = true;
  try {
    agents.value = await getAgentDefinitions();
  } finally {
    loading.value = false;
  }
}

function handleChange(value: string | undefined) {
  emit('update:modelValue', value);
  const found = value ? agents.value.find((a) => a.id === value) || null : null;
  emit('change', value, found);
}

defineExpose({ loadAgents });

onMounted(() => {
  loadAgents();
});
</script>

<style scoped lang="less">
.agent-selector {
  display: flex;
  align-items: center;
  gap: 4px;
}

.agent-select {
  min-width: 120px;
  max-width: 180px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;

  :deep(.ant-select-selector) {
    background-color: rgba(0, 0, 0, 0.2) !important;
    border: 1px solid rgba(59, 130, 246, 0.2) !important;
    border-radius: 4px;
    color: #fff !important;
    height: 28px;
    display: flex;
    align-items: center;

    &:hover {
      border-color: #60a5fa !important;
    }
  }

  :deep(.ant-select-selection-placeholder) {
    color: rgba(148, 163, 184, 0.5);
    font-size: 12px;
  }

  :deep(.ant-select-selection-item) {
    font-size: 12px;
    color: #e2e8f0;
    line-height: 26px;
    display: flex;
    align-items: center;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    max-width: 100%;
  }

  :deep(.ant-select-arrow) {
    color: rgba(96, 165, 250, 0.6);
  }
}

.option-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #e2e8f0;
  font-family: 'JetBrains Mono', monospace;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 100%;
  flex: 1;
  min-width: 0;
}

.builtin-badge {
  font-size: 10px;
  padding: 0 4px;
  background: rgba(59, 130, 246, 0.2);
  color: #60a5fa;
  border-radius: 2px;
  line-height: 16px;
  flex-shrink: 0;
}
</style>

<style lang="less">
.agent-select-dropdown.tech-dropdown {
  background-color: #0f172a !important;
  border: 1px solid rgba(59, 130, 246, 0.2);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
  padding: 4px;

  .ant-select-item {
    padding: 6px 12px;
    font-size: 12px;
    color: #94a3b8;
    border-radius: 4px;
    margin-bottom: 2px;
    font-family: 'JetBrains Mono', monospace;

    &:hover {
      background: rgba(59, 130, 246, 0.1);
      color: #e2e8f0;
    }

    &.ant-select-item-option-selected {
      background: rgba(59, 130, 246, 0.15);
      color: #60a5fa;
      font-weight: 600;
    }
  }
}
</style>
