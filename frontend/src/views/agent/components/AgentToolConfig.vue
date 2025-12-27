<template>
  <div class="agent-tool-config">
    <div class="selector-label">
      <Icon icon="ant-design:tool-outlined" />
      <span>可用工具</span>
      <a-tooltip title="配置可调用的工具，支持通配符（如 device-*）">
        <Icon icon="ant-design:question-circle-outlined" class="help-icon" />
      </a-tooltip>
    </div>

    <a-select
      v-model:value="selectedTools"
      mode="tags"
      :placeholder="placeholder"
      :max-tag-count="3"
      style="width: 100%"
      @change="handleChange"
    >
      <a-select-option
        v-for="tool in commonTools"
        :key="tool.value"
        :value="tool.value"
      >
        <div class="tool-option">
          <span class="tool-icon">{{ tool.icon }}</span>
          <div class="tool-info">
            <div class="tool-name">{{ tool.label }}</div>
            <div class="tool-desc">{{ tool.description }}</div>
          </div>
        </div>
      </a-select-option>
    </a-select>

    <div v-if="showHint" class="tool-hint">
      <Icon icon="ant-design:info-circle-outlined" />
      <span>提示：留空表示允许所有工具，支持通配符（如 device-*）</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { Icon } from '/@/components/Icon';

const props = withDefaults(
  defineProps<{
    modelValue?: string[];
    placeholder?: string;
    showHint?: boolean;
  }>(),
  {
    placeholder: '选择或输入工具名称',
    showHint: true,
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
  (e: 'change', value: string[]): void;
}>();

const selectedTools = ref<string[]>(props.modelValue || []);

// 常用工具列表
const commonTools = [
  {
    value: 'device-*',
    label: '设备工具（全部）',
    description: '所有设备相关工具',
    icon: '📱',
  },
  {
    value: 'list-device-info',
    label: '查询设备信息',
    description: '查询设备详细信息',
    icon: '🔍',
  },
  {
    value: 'invoke-device-command',
    label: '执行设备命令',
    description: '向设备发送控制命令',
    icon: '⚙️',
  },
  {
    value: 'query-device-documents',
    label: '查询设备文档',
    description: '检索设备相关文档',
    icon: '📄',
  },
  {
    value: 'query-device-history',
    label: '查询设备历史',
    description: '查询设备历史数据',
    icon: '📊',
  },
  {
    value: 'query-*',
    label: '查询工具（全部）',
    description: '所有查询类工具',
    icon: '🔎',
  },
];

// 监听外部值变化
watch(
  () => props.modelValue,
  (newValue) => {
    selectedTools.value = newValue || [];
  }
);

// 处理选择变化
const handleChange = (value: string[]) => {
  emit('update:modelValue', value);
  emit('change', value);
};
</script>

<style scoped lang="less">
.agent-tool-config {
  .selector-label {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 8px;
    font-size: 13px;
    font-weight: 500;
    color: #262626;

    .help-icon {
      color: #8c8c8c;
      font-size: 14px;
      cursor: help;
    }
  }

  :deep(.ant-select) {
    .ant-select-selector {
      border-radius: 6px;
    }
  }

  .tool-hint {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin-top: 8px;
    padding: 8px 12px;
    background: #f0f2f5;
    border-radius: 6px;
    font-size: 12px;
    color: #595959;
    line-height: 1.5;

    .anticon {
      color: #1890ff;
      margin-top: 2px;
      flex-shrink: 0;
    }
  }
}

.tool-option {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 4px 0;

  .tool-icon {
    font-size: 20px;
    flex-shrink: 0;
    margin-top: 2px;
  }

  .tool-info {
    flex: 1;
    min-width: 0;

    .tool-name {
      font-weight: 500;
      color: #262626;
      margin-bottom: 2px;
    }

    .tool-desc {
      font-size: 12px;
      color: #8c8c8c;
      line-height: 1.4;
    }
  }
}
</style>

