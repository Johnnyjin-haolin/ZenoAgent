<template>
  <div class="aurora-container">
    <div class="aurora-blob blob-1"></div>
    <div class="aurora-blob blob-2"></div>
    <div class="aurora-blob blob-3"></div>
    <div class="grid-overlay"></div>
  </div>
</template>

<style scoped>
.aurora-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 0;
  background: var(--color-background, #0B0F19);
  pointer-events: none;
}

.aurora-blob {
  position: absolute;
  filter: blur(80px);
  opacity: 0.4;
  border-radius: 50%;
  animation: float 20s infinite ease-in-out;
}

.blob-1 {
  background: #4285F4;
  width: 600px;
  height: 600px;
  top: -200px;
  left: -100px;
  animation-delay: 0s;
}

.blob-2 {
  background: #A142F4;
  width: 500px;
  height: 500px;
  top: 20%;
  right: -100px;
  animation-delay: -5s;
}

.blob-3 {
  background: #24C1E0;
  width: 400px;
  height: 400px;
  bottom: -100px;
  left: 30%;
  animation-delay: -10s;
}

.grid-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(circle at center, black 40%, transparent 100%);
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -50px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
}
</style>
