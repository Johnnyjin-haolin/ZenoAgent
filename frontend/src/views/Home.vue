<template>
  <div class="home-container dark-theme">
    <!-- Background Texture -->
    <TechBackground />
    
    <!-- Hero Section -->
    <section class="hero">
      <div class="hero-content">
        <h1 class="hero-title">{{ t('home.hero.title.line1') }} <br/>{{ t('home.hero.title.line2') }} <br/><span class="highlight-text">{{ t('home.hero.title.line3') }}</span></h1>
        <p class="hero-subtitle" style="white-space: pre-wrap">{{ t('home.hero.subtitle') }}</p>
        <div class="hero-actions">
          <router-link to="/agent" class="cta-button white-hole">
            <span class="btn-text">{{ t('home.hero.start') }}</span>
            <div class="btn-glow"></div>
          </router-link>
          <a href="https://github.com/Johnnyjin-haolin/ZenoAgent" class="cta-button link-text">{{ t('home.hero.docs') }}</a>
        </div>
      </div>
      <div class="hero-image">
        <ZenoTesseract />
      </div>
    </section>

    <!-- Features Section -->
    <section class="features">
      <div class="feature-card glass">
        <div class="icon-box blue">🧠</div>
        <h3>{{ t('home.capabilities.context.title') }}</h3>
        <p>{{ t('home.capabilities.context.desc') }}</p>
      </div>
      
      <div class="feature-card glass">
        <div class="icon-box cyan">🔍</div>
        <h3>{{ t('home.capabilities.rag.title') }}</h3>
        <p>{{ t('home.capabilities.rag.desc') }}</p>
      </div>
      
      <div class="feature-card glass">
        <div class="icon-box purple">⚡️</div>
        <h3>{{ t('home.capabilities.mcp.title') }}</h3>
        <p>{{ t('home.capabilities.mcp.desc') }}</p>
      </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
      <p>{{ t('home.footer') }}</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import ZenoTesseract from '@/components/ZenoTesseract.vue';
import TechBackground from '@/components/TechBackground.vue';

const { t } = useI18n();
</script>

<style lang="less" scoped>
.home-container {
  min-height: 100vh;
  /* background removed - using global TechBackground */
  font-family: 'Inter', 'Google Sans', sans-serif;
  color: #F8FAFC;
  position: relative;
  overflow-x: hidden;
  width: 100%;
}

/* Navbar removed - now in MainLayout */

/* Hero */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 120px 64px;
  max-width: 1440px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
  overflow: hidden;

  .hero-content {
    flex: 1;
    padding-right: 80px;
    z-index: 2;

    .hero-title {
      font-size: 80px;
      line-height: 1.05;
      font-weight: 800;
      margin-bottom: 32px;
      letter-spacing: -2px;
      color: #F8FAFC;

      .highlight-text {
        color: #3B82F6; /* Electric Blue */
      }
    }

    .hero-subtitle {
      font-size: 20px;
      color: #94A3B8;
      line-height: 1.6;
      margin-bottom: 48px;
      max-width: 600px;
      font-weight: 400;
    }

    .hero-actions {
      display: flex;
      gap: 24px;
      align-items: center;

      .cta-button {
        text-decoration: none;
        transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        cursor: pointer;
      }

      .white-hole {
        position: relative;
        background: #F8FAFC;
        color: #0F172A;
        padding: 16px 40px;
        border-radius: 30px;
        font-weight: 600;
        font-size: 16px;
        overflow: hidden;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        box-shadow: 0 0 30px rgba(59, 130, 246, 0.3);

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 0 50px rgba(59, 130, 246, 0.5);
        }
      }

      .link-text {
        color: #F8FAFC;
        font-weight: 500;
        &:hover { text-decoration: underline; }
      }
    }
  }

  .hero-image {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    z-index: 1;
  }
}

/* Features */
.features {
  display: flex;
  justify-content: center;
  gap: 32px;
  padding: 80px 64px;
  position: relative;
  z-index: 1;

  .feature-card {
    flex: 1;
    max-width: 360px;
    padding: 32px;
    border-radius: 16px;
    background: rgba(15, 23, 42, 0.4);
    border: 1px solid rgba(255, 255, 255, 0.05);
    backdrop-filter: blur(10px);
    transition: transform 0.2s, background 0.2s;

    &:hover {
      transform: translateY(-5px);
      background: rgba(15, 23, 42, 0.6);
      border-color: rgba(59, 130, 246, 0.2);
    }

    .icon-box {
      font-size: 32px;
      margin-bottom: 24px;
    }

    h3 {
      font-size: 20px;
      font-weight: 600;
      margin-bottom: 12px;
      color: #F8FAFC;
    }

    p {
      color: #94A3B8;
      line-height: 1.6;
      font-size: 15px;
    }
  }
}

/* Footer */
.footer {
  padding: 40px;
  text-align: center;
  color: #94A3B8;
  font-size: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  position: relative;
  z-index: 1;
}

@media (max-width: 1024px) {
  .hero {
    flex-direction: column;
    padding: 80px 32px;
    text-align: center;
    
    .hero-content {
      padding-right: 0;
      margin-bottom: 80px;
      
      .hero-title { font-size: 56px; }
      .hero-subtitle { margin-left: auto; margin-right: auto; }
      .hero-actions { justify-content: center; }
    }
  }
  
  .features {
    flex-direction: column;
    align-items: center;
  }
}
</style>
