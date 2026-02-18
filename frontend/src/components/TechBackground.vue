<template>
  <div class="tech-bg">
    <!-- Layer 1: Holographic Grid -->
    <div class="grid-layer"></div>
    
    <!-- Layer 2: Circuit / Code Blocks -->
    <div class="circuit-layer">
      <!-- Randomly distributed decorative boxes simulating code blocks/windows -->
      <div class="circuit-box box-1"></div>
      <div class="circuit-box box-2"></div>
      <div class="circuit-box box-3"></div>
      <div class="circuit-box box-4"></div>
      <div class="circuit-box box-5"></div>
      <div class="circuit-box box-6"></div>
    </div>
    
    <!-- Layer 3: Vignette & Glow Overlay -->
    <div class="glow-layer"></div>
  </div>
</template>

<style scoped>
.tech-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 0;
  pointer-events: none;
  background-color: #020408; /* Deep Abyss Black base */
}

/* --- Layer 1: Grid --- */
.grid-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: 
    linear-gradient(rgba(59, 130, 246, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.03) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(circle at 50% 50%, black 40%, transparent 100%);
  opacity: 0.6;
}

/* --- Layer 2: Circuit Boxes --- */
.circuit-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  /* Perspective for slight depth if needed, currently flat */
}

.circuit-box {
    position: absolute;
    border: 1px solid rgba(59, 130, 246, 0.12); /* Slightly brighter border */
    border-radius: 8px;
    background: rgba(15, 23, 42, 0.4); /* Darker, more visible fill */
    box-shadow: 
      0 0 20px rgba(59, 130, 246, 0.05),
      inset 0 0 10px rgba(59, 130, 246, 0.05); /* Inner glow */
    animation: float-box 10s infinite ease-in-out alternate;
    /* backdrop-filter: blur(2px); Removed to prevent GPU flickering on animation */
    will-change: transform;
  }

/* Box Positions & Sizes - Adjusted for better visibility */
.box-1 { width: 300px; height: 200px; top: 15%; right: 10%; animation-delay: 0s; border-color: rgba(59, 130, 246, 0.15); background: linear-gradient(135deg, rgba(30, 58, 138, 0.2), transparent); }
.box-2 { width: 200px; height: 100px; top: 35%; right: 5%; animation-delay: -2s; border-color: rgba(6, 182, 212, 0.1); background: linear-gradient(135deg, rgba(15, 23, 42, 0.3), transparent); }
.box-3 { width: 400px; height: 150px; bottom: 15%; left: 5%; animation-delay: -5s; border-color: rgba(139, 92, 246, 0.1); background: linear-gradient(135deg, rgba(46, 16, 101, 0.2), transparent); }
.box-4 { width: 150px; height: 150px; bottom: 45%; left: 2%; animation-delay: -3s; border-radius: 12px; border: 1px dashed rgba(59, 130, 246, 0.1); }
.box-5 { width: 250px; height: 80px; top: 20%; left: 15%; animation-delay: -7s; border-color: rgba(59, 130, 246, 0.1); }
.box-6 { width: 180px; height: 240px; bottom: 10%; right: 25%; animation-delay: -1s; border-color: rgba(6, 182, 212, 0.1); background: linear-gradient(180deg, rgba(8, 145, 178, 0.1), transparent); }

/* Internal details for boxes (optional lines inside) */
.circuit-box::before {
  content: '';
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  height: 4px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 2px;
}
.circuit-box::after {
  content: '';
  position: absolute;
  top: 24px;
  left: 12px;
  width: 60%;
  height: 4px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 2px;
}

/* --- Layer 3: Glow & Vignette --- */
.glow-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: 
    /* Central Blue Glow */
    radial-gradient(circle at 50% 40%, rgba(59, 130, 246, 0.08) 0%, transparent 60%),
    /* Bottom Purple Glow */
    radial-gradient(circle at 80% 80%, rgba(168, 85, 247, 0.05) 0%, transparent 50%);
  mix-blend-mode: screen;
}

@keyframes float-box {
  0% { transform: translateY(0) scale(1); opacity: 0.4; }
  100% { transform: translateY(-10px) scale(1.02); opacity: 0.7; }
}
</style>
