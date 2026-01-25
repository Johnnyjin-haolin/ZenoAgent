<template>
  <div class="chat-header">
    <div class="header-title">
      <Icon icon="ant-design:robot-outlined" class="title-icon" />
      <img v-if="brandConfig.logo" :src="brandConfig.logo" class="brand-logo" alt="brand" />
      <h2 v-if="showBrandTitle">{{ brandConfig.name }}</h2>
    </div>

    <div class="header-actions">
      <a-button size="small" @click="emit('navigate-knowledge')" style="margin-right: 8px;">
        <template #icon>
          <Icon icon="ant-design:book-outlined" />
        </template>
        知识库管理
      </a-button>
      <a-button size="small" @click="emit('open-config')">
        <template #icon>
          <Icon icon="ant-design:setting-outlined" />
        </template>
        配置
      </a-button>
      <a-dropdown v-if="brandLinks.length > 0" placement="bottomRight">
        <a-button size="small" type="default" class="header-help-button">
          <template #icon>
            <Icon icon="ant-design:question-circle-outlined" />
          </template>
          帮助
        </a-button>
        <template #overlay>
          <a-menu>
            <a-menu-item v-for="link in brandLinks" :key="link.label">
              <a :href="link.url" target="_blank" rel="noopener noreferrer">{{ link.label }}</a>
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
      <a-tag v-if="brandVersion" class="header-version-tag">{{ brandVersion }}</a-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@/components/Icon';

type BrandLink = {
  label: string;
  url: string;
};

type BrandConfig = {
  name: string;
  logo?: string;
  primaryColor?: string;
  links?: BrandLink[];
  version?: string;
  showFooter?: boolean;
  showTitle?: boolean;
  embedMode?: boolean;
};

defineProps<{
  brandConfig: BrandConfig;
  showBrandTitle: boolean;
  brandLinks: BrandLink[];
  brandVersion: string;
}>();

const emit = defineEmits<{
  (e: 'navigate-knowledge'): void;
  (e: 'open-config'): void;
}>();
</script>

<style scoped lang="less">
.chat-header {
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;

  .header-title {
    display: flex;
    align-items: center;
    gap: 10px;

    .title-icon {
      font-size: 24px;
      color: var(--brand-primary);
    }

    .brand-logo {
      width: 24px;
      height: 24px;
      border-radius: 4px;
      object-fit: contain;
    }

    h2 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      color: #262626;
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .header-help-button {
    padding: 0 8px;
  }

  .header-version-tag {
    margin-left: 4px;
    background: #f0f5ff;
    color: #2f54eb;
    border-color: #adc6ff;
  }
}
</style>


