import { computed, ref } from 'vue';

type BrandLink = {
  label: string;
  url: string;
};

export type BrandConfig = {
  name: string;
  logo?: string;
  primaryColor?: string;
  links?: BrandLink[];
  version?: string;
  showFooter?: boolean;
  showTitle?: boolean;
  embedMode?: boolean;
};

const defaultBrandConfig: BrandConfig = {
  name: 'ZenoAgent',
  primaryColor: '#1890ff',
  showFooter: true,
  showTitle: true,
  embedMode: false,
  links: [],
};

const normalizeBrandConfig = (config?: Partial<BrandConfig>): BrandConfig => {
  const merged = {
    ...defaultBrandConfig,
    ...(config || {}),
  };
  return {
    ...merged,
    links: Array.isArray(merged.links) ? merged.links : defaultBrandConfig.links,
  };
};

const resolveBrandConfig = (fileConfig?: Partial<BrandConfig>): BrandConfig => {
  const windowConfig = window.__ZENO_AGENT_BRAND__ || {};
  return normalizeBrandConfig({
    ...fileConfig,
    ...windowConfig,
  });
};

export const useBrandConfig = () => {
  const brandConfig = ref<BrandConfig>(normalizeBrandConfig());
  const brandStyle = computed(() => ({
    '--brand-primary': brandConfig.value.primaryColor || defaultBrandConfig.primaryColor,
  }));
  const showBrandTitle = computed(() => {
    if (typeof brandConfig.value.showTitle === 'boolean') {
      return brandConfig.value.showTitle;
    }
    return defaultBrandConfig.showTitle;
  });
  const brandLinks = computed(() => brandConfig.value.links || []);
  const brandVersion = computed(() => brandConfig.value.version || '');

  const loadBrandConfig = async () => {
    try {
      const response = await fetch('/brand.json', { cache: 'no-store' });
      if (!response.ok) {
        brandConfig.value = resolveBrandConfig();
        return;
      }
      const fileConfig = await response.json();
      brandConfig.value = resolveBrandConfig(fileConfig);
    } catch (error) {
      console.warn('加载品牌配置失败:', error);
      brandConfig.value = resolveBrandConfig();
    }
  };

  return {
    brandConfig,
    brandStyle,
    showBrandTitle,
    brandLinks,
    brandVersion,
    loadBrandConfig,
  };
};


