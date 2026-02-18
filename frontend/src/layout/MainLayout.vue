<template>
  <a-layout class="main-layout">
    <!-- Sidebar -->
    <a-layout-sider
      v-model:collapsed="collapsed"
      :trigger="null"
      collapsible
      class="google-sidebar"
      theme="light"
      :width="240"
      :collapsedWidth="60"
    >
      <div class="logo-container" :class="{ 'collapsed': collapsed }">
        <div class="logo-icon">
          <!-- Placeholder for Logo -->
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" fill="#1A73E8"/>
          </svg>
        </div>
        <span class="logo-text" v-show="!collapsed">Zeno Agent</span>
      </div>
      
      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="inline"
        class="google-menu"
      >
        <a-menu-item key="agent">
          <router-link to="/agent">
            <template #icon>
              <message-outlined />
            </template>
            <span>智能对话</span>
          </router-link>
        </a-menu-item>
        
        <a-menu-item key="knowledge-bases">
          <router-link to="/knowledge-bases">
            <template #icon>
              <book-outlined />
            </template>
            <span>知识库</span>
          </router-link>
        </a-menu-item>
        
        <a-menu-divider />
        
        <a-menu-item key="settings">
          <template #icon>
            <setting-outlined />
          </template>
          <span>设置</span>
        </a-menu-item>
      </a-menu>
      
      <div class="sidebar-footer" @click="toggleCollapse">
        <menu-fold-outlined v-if="!collapsed" />
        <menu-unfold-outlined v-else />
      </div>
    </a-layout-sider>

    <!-- Main Content -->
    <a-layout>
      <!-- Top Header -->
      <a-layout-header class="google-header">
        <div class="header-left">
          <h2 class="page-title">{{ currentRouteTitle }}</h2>
        </div>
        <div class="header-right">
          <a-avatar src="https://ui-avatars.com/api/?name=User&background=0D8ABC&color=fff" />
        </div>
      </a-layout-header>

      <!-- Content -->
      <a-layout-content class="google-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRoute } from 'vue-router';
import {
  MessageOutlined,
  BookOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons-vue';

const collapsed = ref(false);
const route = useRoute();
const selectedKeys = ref<string[]>([]);

// Sync menu selection with route
watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/agent')) {
      selectedKeys.value = ['agent'];
    } else if (path.startsWith('/knowledge-bases')) {
      selectedKeys.value = ['knowledge-bases'];
    }
  },
  { immediate: true }
);

const currentRouteTitle = computed(() => {
  return route.meta.title || 'Zeno Agent';
});

const toggleCollapse = () => {
  collapsed.value = !collapsed.value;
};
</script>

<style lang="less" scoped>
.main-layout {
  min-height: 100vh;
  background: var(--color-background);
}

.google-sidebar {
  background: #FFFFFF;
  border-right: 1px solid rgba(0,0,0,0.08);
  box-shadow: 2px 0 8px rgba(0,0,0,0.02);
  z-index: 10;
  
  :deep(.ant-layout-sider-children) {
    display: flex;
    flex-direction: column;
  }
}

.logo-container {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 18px;
  overflow: hidden;
  white-space: nowrap;
  transition: all 0.2s;
  border-bottom: 1px solid transparent;
  
  &.collapsed {
    padding: 0 18px;
    justify-content: center;
  }
  
  .logo-icon {
    width: 24px;
    height: 24px;
    margin-right: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  .logo-text {
    font-family: 'Google Sans', sans-serif;
    font-size: 18px;
    font-weight: 500;
    color: #5F6368;
  }
}

.google-menu {
  flex: 1;
  border-right: none;
  padding-top: 8px;
  
  :deep(.ant-menu-item) {
    height: 40px;
    line-height: 40px;
    margin: 4px 8px;
    width: auto;
    border-radius: 0 20px 20px 0; /* Google style rounded selection */
    border-radius: 20px;
    
    &.ant-menu-item-selected {
      background-color: #E8F0FE;
      color: #1967D2;
      font-weight: 500;
      
      .anticon {
        color: #1967D2;
      }
    }
    
    &:not(.ant-menu-item-selected):hover {
      color: #202124;
      background-color: #F1F3F4;
    }
    
    .anticon {
      font-size: 18px;
    }
  }
}

.sidebar-footer {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #5F6368;
  border-top: 1px solid rgba(0,0,0,0.06);
  transition: background 0.2s;
  
  &:hover {
    background: #F1F3F4;
  }
}

.google-header {
  background: #FFFFFF;
  height: 60px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(0,0,0,0.06);
  
  .page-title {
    margin: 0;
    font-size: 18px;
    font-weight: 400;
    color: #202124;
  }
}

.google-content {
  margin: 0;
  padding: 0;
  background: var(--color-background);
  overflow: hidden; /* Prevent double scrollbar */
  position: relative;
  height: calc(100vh - 60px); /* Ensure fixed height for inner scrolling */
  display: flex;
  flex-direction: column;
}

:deep(.ant-layout) {
  background: var(--color-background);
}

/* Transitions */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
