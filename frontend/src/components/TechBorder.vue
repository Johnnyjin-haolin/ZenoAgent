<template>
  <div class="tech-border-container" :class="{ 'hover-active': isHover }">
    <div class="border-glow"></div>
    <div class="content-wrapper">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const isHover = ref(false);
</script>

<style scoped>
.tech-border-container {
  position: relative;
  border-radius: 12px; /* Matches content radius */
  padding: 1px; /* Border width */
  background: transparent;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.tech-border-container:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px -10px rgba(59, 130, 246, 0.4);
}

.border-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: conic-gradient(
    from 0deg,
    transparent 0deg,
    transparent 80deg,
    var(--google-cyan, #06B6D4) 120deg,
    var(--google-blue, #3B82F6) 160deg,
    transparent 180deg,
    transparent 360deg
  );
  animation: rotate 4s linear infinite;
  opacity: 0; /* Hidden by default */
  transition: opacity 0.3s ease;
  z-index: 0;
}

.tech-border-container:hover .border-glow {
  opacity: 1;
}

.content-wrapper {
  position: relative;
  background: var(--color-surface, #0F172A);
  border-radius: 11px; /* Slightly smaller than container */
  height: 100%;
  width: 100%;
  z-index: 1;
  border: 1px solid rgba(255, 255, 255, 0.05); /* Default static border */
}

@keyframes rotate {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>
