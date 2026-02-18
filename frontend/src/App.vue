<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup lang="ts">
// 使用路由视图，不再直接导入组件
</script>

<style>
/* Global Resets */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  width: 100%;
  min-height: 100vh;
  background-color: #020408; /* Fallback */
  color: #F8FAFC;
}

body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background-color: #020408;
  color: #F8FAFC;
  overflow-x: hidden;
}

/* Custom Scrollbar */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* Global Selection Color */
::selection {
  background: rgba(59, 130, 246, 0.3);
  color: #F8FAFC;
}
</style>

