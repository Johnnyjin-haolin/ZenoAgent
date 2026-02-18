<template>
  <div class="tesseract-container" @mousemove="handleMouseMove" @mouseleave="handleMouseLeave">
    <!-- Inner Universe (Background Particles) -->
    <div class="particle-field">
      <div v-for="i in 20" :key="i" class="particle" :style="getParticleStyle(i)"></div>
    </div>

    <div class="scene" :style="sceneStyle">
      <!-- 1. The Core: Context Neural Network -->
      <div class="cube context-core">
        <div class="face front"></div>
        <div class="face back"></div>
        <div class="face right"></div>
        <div class="face left"></div>
        <div class="face top"></div>
        <div class="face bottom"></div>
        
        <!-- Neural Nodes -->
        <div class="neural-node node-1"></div>
        <div class="neural-node node-2"></div>
        <div class="neural-node node-3"></div>
        <div class="neural-connection c1"></div>
        <div class="neural-connection c2"></div>
      </div>

      <!-- 2. The Middle: RAG Data Matrix (Scanning Planes) -->
      <div class="rag-matrix">
        <div class="data-plane plane-x"></div>
        <div class="data-plane plane-y"></div>
        <div class="data-plane plane-z"></div>
        <div class="scan-beam"></div>
      </div>

      <!-- 3. The Outer: MCP Satellites -->
      <div class="mcp-orbit">
        <div class="satellite sat-mcp">
          <span class="icon">🔌</span>
          <div class="label">MCP</div>
        </div>
        <div class="satellite sat-rag">
          <span class="icon">📚</span>
          <div class="label">RAG</div>
        </div>
        <div class="satellite sat-context">
          <span class="icon">🧠</span>
          <div class="label">Context</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

const mouseX = ref(0);
const mouseY = ref(0);

const handleMouseMove = (e: MouseEvent) => {
  const rect = (e.target as HTMLElement).getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  mouseX.value = (x / rect.width - 0.5) * 2; // -1 to 1
  mouseY.value = (y / rect.height - 0.5) * 2;
};

const handleMouseLeave = () => {
  mouseX.value = 0;
  mouseY.value = 0;
};

const sceneStyle = computed(() => {
  const rotateX = -mouseY.value * 20;
  const rotateY = mouseX.value * 20;
  return {
    transform: `rotateX(${rotateX}deg) rotateY(${rotateY}deg)`
  };
});

const getParticleStyle = (i: number) => {
  const size = Math.random() * 4 + 1;
  const x = Math.random() * 300 - 150;
  const y = Math.random() * 300 - 150;
  const z = Math.random() * 300 - 150;
  const delay = Math.random() * 5;
  return {
    width: `${size}px`,
    height: `${size}px`,
    transform: `translate3d(${x}px, ${y}px, ${z}px)`,
    animationDelay: `${delay}s`
  };
};
</script>

<style scoped lang="less">
.tesseract-container {
  width: 100%;
  height: 400px;
  perspective: 1000px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  /* overflow: visible; Removed to prevent scrollbar issues */
  contain: layout paint; /* Optimize rendering performance */
}

.scene {
  width: 200px;
  height: 200px;
  position: relative;
  transform-style: preserve-3d;
  transition: transform 0.1s ease-out;
  animation: float-scene 6s infinite ease-in-out;
}

/* --- 1. Context Core (Cube) --- */
.cube {
  width: 140px;
  height: 140px;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  transform-style: preserve-3d;
  animation: spin-core 20s infinite linear;

  .face {
    position: absolute;
    width: 140px;
    height: 140px;
    background: rgba(59, 130, 246, 0.05); /* Very transparent blue */
    border: 1px solid rgba(59, 130, 246, 0.4);
    box-shadow: 0 0 15px rgba(59, 130, 246, 0.1) inset;
  }

  .front  { transform: translateZ(70px); }
  .back   { transform: rotateY(180deg) translateZ(70px); }
  .right  { transform: rotateY(90deg) translateZ(70px); }
  .left   { transform: rotateY(-90deg) translateZ(70px); }
  .top    { transform: rotateX(90deg) translateZ(70px); }
  .bottom { transform: rotateX(-90deg) translateZ(70px); }
}

/* Neural Nodes inside Core */
.neural-node {
  position: absolute;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 0 10px #60A5FA;
  transform-style: preserve-3d;
}

.node-1 { width: 8px; height: 8px; top: 30%; left: 30%; transform: translateZ(20px); animation: pulse 2s infinite; }
.node-2 { width: 6px; height: 6px; top: 60%; left: 70%; transform: translateZ(-20px); animation: pulse 2s infinite 0.5s; }
.node-3 { width: 10px; height: 10px; top: 40%; left: 60%; transform: translateZ(10px); animation: pulse 2s infinite 1s; }

.neural-connection {
  position: absolute;
  background: rgba(96, 165, 250, 0.6);
  height: 1px;
  transform-origin: 0 0;
}
/* Simplified connections for visual effect */
.c1 { width: 60px; top: 34%; left: 32%; transform: rotate(25deg) translateZ(15px); }
.c2 { width: 40px; top: 44%; left: 62%; transform: rotate(80deg) translateZ(0px); }


/* --- 2. RAG Matrix (Scanning Planes) --- */
.rag-matrix {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100px;
  height: 100px;
  transform: translate(-50%, -50%);
  transform-style: preserve-3d;
  animation: spin-matrix 15s infinite linear reverse;

  .data-plane {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    border: 1px dashed rgba(34, 211, 238, 0.3); /* Cyan dashed */
    background: rgba(34, 211, 238, 0.05);
  }
  
  .plane-x { transform: rotateY(90deg); }
  .plane-y { transform: rotateX(90deg); }
  .plane-z { transform: translateZ(0); }
  
  .scan-beam {
    position: absolute;
    width: 100%;
    height: 2px;
    background: #22D3EE;
    box-shadow: 0 0 10px #22D3EE;
    top: 0;
    animation: scan 3s infinite linear;
    opacity: 0.8;
  }
}

/* --- 3. MCP Satellites (Outer Orbit) --- */
.mcp-orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 280px;
  height: 280px;
  transform: translate(-50%, -50%);
  transform-style: preserve-3d;
  animation: spin-orbit 30s infinite linear;

  .satellite {
    position: absolute;
    width: 48px;
    height: 48px;
    background: rgba(15, 23, 42, 0.8);
    border: 1px solid #A78BFA;
    border-radius: 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #A78BFA;
    font-size: 16px;
    box-shadow: 0 0 15px rgba(167, 139, 250, 0.3);
    /* Keep satellites facing front */
    animation: counter-spin 30s infinite linear;
    
    .label {
      font-size: 9px;
      margin-top: 2px;
      font-weight: bold;
      color: #E8EAED;
    }
  }

  /* Purple for MCP */
  .sat-mcp { 
    top: 0; left: 50%; 
    transform: translate(-50%, -50%) translateZ(100px); 
    border-color: #A78BFA;
    color: #A78BFA;
    box-shadow: 0 0 15px rgba(167, 139, 250, 0.3);
  }

  /* Cyan for RAG */
  .sat-rag { 
    bottom: 20%; left: 10%; 
    transform: translate(-50%, -50%) translateZ(-50px); 
    border-color: #22D3EE;
    color: #22D3EE;
    box-shadow: 0 0 15px rgba(34, 211, 238, 0.3);
  }

  /* Blue for Context */
  .sat-context { 
    top: 30%; right: 0; 
    transform: translate(50%, -50%); 
    border-color: #60A5FA;
    color: #60A5FA;
    box-shadow: 0 0 15px rgba(96, 165, 250, 0.3);
  }
}

/* --- Particles --- */
.particle-field {
  position: absolute;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  pointer-events: none;
}

.particle {
  position: absolute;
  top: 50%;
  left: 50%;
  background: rgba(255, 255, 255, 0.4);
  border-radius: 50%;
  animation: particle-float 5s infinite ease-in-out;
}

/* --- Animations --- */
@keyframes spin-core {
  0% { transform: translate(-50%, -50%) rotateX(0) rotateY(0); }
  100% { transform: translate(-50%, -50%) rotateX(360deg) rotateY(360deg); }
}

@keyframes spin-matrix {
  0% { transform: translate(-50%, -50%) rotateX(0) rotateY(0); }
  100% { transform: translate(-50%, -50%) rotateX(-360deg) rotateY(-360deg); }
}

@keyframes spin-orbit {
  0% { transform: translate(-50%, -50%) rotateY(0) rotateZ(10deg); }
  100% { transform: translate(-50%, -50%) rotateY(360deg) rotateZ(10deg); }
}

@keyframes counter-spin {
  0% { transform: translate(-50%, -50%) rotateY(360deg); } /* Counteract parent rotation */
  100% { transform: translate(-50%, -50%) rotateY(0deg); }
}

@keyframes scan {
  0% { top: 0%; opacity: 0; }
  20% { opacity: 1; }
  80% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.2); opacity: 1; }
}

@keyframes float-scene {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

@keyframes particle-float {
  0%, 100% { transform: translate3d(var(--x), var(--y), var(--z)); opacity: 0.3; }
  50% { opacity: 0.8; }
}
</style>
