<template>
  <div class="zeno-space-container" ref="containerRef" @mousemove="handleMouseMove" @mouseleave="handleMouseLeave">
    <div class="scene" :style="sceneStyle">
      <!-- 核心光环 (Outer Glow) -->
      <div class="core-glow"></div>
      
      <!-- 智能体核心 (The Core) -->
      <div class="core">
        <div class="face front"></div>
        <div class="face back"></div>
        <div class="face right"></div>
        <div class="face left"></div>
        <div class="face top"></div>
        <div class="face bottom"></div>
        <div class="inner-light"></div>
      </div>
      
      <!-- 环绕轨道 (Orbits) -->
      <div class="orbit-system">
        <!-- 轨道 1: Thinking -->
        <div class="orbit ring-1">
          <div class="planet thinking">
            <div class="planet-icon">🧠</div>
            <div class="planet-label">Thinking</div>
          </div>
        </div>
        
        <!-- 轨道 2: Coding -->
        <div class="orbit ring-2">
          <div class="planet coding">
            <div class="planet-icon">⚡️</div>
            <div class="planet-label">Coding</div>
          </div>
        </div>
        
        <!-- 轨道 3: Knowledge -->
        <div class="orbit ring-3">
          <div class="planet knowledge">
            <div class="planet-icon">📚</div>
            <div class="planet-label">Knowledge</div>
          </div>
        </div>
      </div>
      
      <!-- 底部全息网格 (Holographic Base) -->
      <div class="holographic-base">
        <div class="grid-lines"></div>
        <div class="base-glow"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

const containerRef = ref<HTMLElement | null>(null);
const mouseX = ref(0);
const mouseY = ref(0);

const handleMouseMove = (e: MouseEvent) => {
  if (!containerRef.value) return;
  const rect = containerRef.value.getBoundingClientRect();
  const centerX = rect.width / 2;
  const centerY = rect.height / 2;
  
  // Calculate offset from center (-1 to 1)
  mouseX.value = (e.clientX - rect.left - centerX) / centerX;
  mouseY.value = (e.clientY - rect.top - centerY) / centerY;
};

const handleMouseLeave = () => {
  mouseX.value = 0;
  mouseY.value = 0;
};

const sceneStyle = computed(() => {
  // Parallax effect: rotate scene slightly based on mouse position
  const rotateX = -mouseY.value * 15; // Max 15deg tilt
  const rotateY = mouseX.value * 15;
  
  return {
    transform: `rotateX(${rotateX}deg) rotateY(${rotateY}deg)`
  };
});
</script>

<style scoped lang="less">
.zeno-space-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
  perspective: 1000px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
}

.scene {
  position: relative;
  width: 200px;
  height: 200px;
  transform-style: preserve-3d;
  transition: transform 0.1s ease-out;
}

/* --- Core (Cube) --- */
.core {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100px;
  height: 100px;
  margin-top: -50px;
  margin-left: -50px;
  transform-style: preserve-3d;
  animation: spin 20s infinite linear;
  
  .face {
    position: absolute;
    width: 100px;
    height: 100px;
    background: rgba(138, 180, 248, 0.1);
    border: 1px solid rgba(138, 180, 248, 0.4);
    box-shadow: 0 0 20px rgba(138, 180, 248, 0.2) inset;
    backdrop-filter: blur(2px);
  }
  
  .front  { transform: translateZ(50px); }
  .back   { transform: rotateY(180deg) translateZ(50px); }
  .right  { transform: rotateY(90deg) translateZ(50px); }
  .left   { transform: rotateY(-90deg) translateZ(50px); }
  .top    { transform: rotateX(90deg) translateZ(50px); }
  .bottom { transform: rotateX(-90deg) translateZ(50px); }
  
  .inner-light {
    position: absolute;
    top: 20px;
    left: 20px;
    width: 60px;
    height: 60px;
    background: radial-gradient(circle, #fff, #8AB4F8);
    border-radius: 50%;
    filter: blur(15px);
    opacity: 0.8;
    transform: translateZ(0);
    animation: pulse 3s infinite ease-in-out;
  }
}

.core-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 300px;
  height: 300px;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, rgba(138, 180, 248, 0.15) 0%, transparent 70%);
  filter: blur(20px);
  pointer-events: none;
}

/* --- Orbits --- */
.orbit-system {
  position: absolute;
  top: 50%;
  left: 50%;
  transform-style: preserve-3d;
}

.orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  transform-style: preserve-3d;
}

.ring-1 {
  width: 240px;
  height: 240px;
  margin-top: -120px;
  margin-left: -120px;
  transform: rotateX(70deg) rotateY(-10deg);
  animation: rotate-ring 12s infinite linear;
}

.ring-2 {
  width: 320px;
  height: 320px;
  margin-top: -160px;
  margin-left: -160px;
  transform: rotateX(60deg) rotateY(45deg);
  animation: rotate-ring 18s infinite linear reverse;
}

.ring-3 {
  width: 400px;
  height: 400px;
  margin-top: -200px;
  margin-left: -200px;
  transform: rotateX(80deg) rotateY(-30deg);
  animation: rotate-ring 25s infinite linear;
}

.planet {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  
  /* Counter-rotate to keep icon upright visually (simplified) */
  animation: counter-rotate 12s infinite linear; 
}

.planet-icon {
  width: 32px;
  height: 32px;
  background: rgba(19, 22, 31, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  box-shadow: 0 0 10px rgba(138, 180, 248, 0.5);
}

.planet-label {
  margin-top: 4px;
  font-size: 10px;
  color: #8AB4F8;
  text-shadow: 0 0 5px rgba(0,0,0,0.8);
  white-space: nowrap;
}

/* --- Holographic Base --- */
.holographic-base {
  position: absolute;
  bottom: -150px;
  left: 50%;
  transform: translateX(-50%) rotateX(90deg);
  width: 400px;
  height: 400px;
  transform-style: preserve-3d;
}

.grid-lines {
  width: 100%;
  height: 100%;
  background-image: 
    linear-gradient(rgba(138, 180, 248, 0.3) 1px, transparent 1px),
    linear-gradient(90deg, rgba(138, 180, 248, 0.3) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(circle, black 30%, transparent 70%);
  animation: grid-scan 4s infinite linear;
}

.base-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(138, 180, 248, 0.2) 0%, transparent 70%);
  filter: blur(20px);
}

/* --- Animations --- */
@keyframes spin {
  0% { transform: translate(-50%, -50%) rotateX(0) rotateY(0); }
  100% { transform: translate(-50%, -50%) rotateX(360deg) rotateY(360deg); }
}

@keyframes pulse {
  0%, 100% { opacity: 0.8; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.9); }
}

@keyframes rotate-ring {
  0% { transform: rotateZ(0); }
  100% { transform: rotateZ(360deg); }
}

@keyframes grid-scan {
  0% { background-position: 0 0; }
  100% { background-position: 0 40px; }
}

/* Adjust ring animations for different speeds */
.ring-2 .planet { animation-duration: 18s; }
.ring-3 .planet { animation-duration: 25s; }

</style>
