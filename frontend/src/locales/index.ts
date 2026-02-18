import { createI18n } from 'vue-i18n';
import enUS from './en-US';
import zhCN from './zh-CN';

const i18n = createI18n({
  legacy: false, // Composition API
  locale: localStorage.getItem('locale') || 'en-US',
  fallbackLocale: 'en-US',
  messages: {
    'en-US': enUS,
    'zh-CN': zhCN,
  },
});

export default i18n;
