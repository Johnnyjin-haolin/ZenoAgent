<template>
  <div class="spotlight-card" ref="cardRef" @mousemove="handleMouseMove" @mouseleave="handleMouseLeave">
    <div class="spotlight-content">
      <slot></slot>
    </div>
    <div class="spotlight-border" :style="borderStyle"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

const cardRef = ref<HTMLElement | null>(null);
const mouseX = ref(0);
const mouseY = ref(0);
const isHovering = ref(false);

const handleMouseMove = (e: MouseEvent) => {
  if (!cardRef.value) return;
  const rect = cardRef.value.getBoundingClientRect();
  mouseX.value = e.clientX - rect.left;
  mouseY.value = e.clientY - rect.top;
  isHovering.value = true;
};

const handleMouseLeave = () => {
  isHovering.value = false;
};

const borderStyle = computed(() => {
  if (!isHovering.value) return { opacity: 0 };
  return {
    opacity: 1,
    background: `radial-gradient(600px circle at ${mouseX.value}px ${mouseY.value}px, rgba(138, 180, 248, 0.4), transparent 40%)`
  };
});
</script>

<style scoped>
.spotlight-card {
  position: relative;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.05);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.spotlight-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 40px -10px rgba(0,0,0,0.5);
  background: rgba(255, 255, 255, 0.05);
}

.spotlight-content {
  position: relative;
  z-index: 2;
  height: 100%;
  padding: 24px;
}

.spotlight-border {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
  mix-blend-mode: overlay;
  transition: opacity 0.3s ease;
}
</style>
