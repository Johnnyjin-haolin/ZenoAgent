import { defineComponent, h } from 'vue';
import * as Icons from '@ant-design/icons-vue';

/**
 * Icon 组件包装器
 * 支持 Ant Design Vue 图标
 */
export const Icon = defineComponent({
  name: 'Icon',
  props: {
    icon: {
      type: String,
      required: true,
    },
  },
  setup(props, { attrs }) {
    return () => {
      const { icon } = props;
      
      // 解析图标名称，支持格式: "ant-design:robot-outlined" 或 "RobotOutlined"
      let iconName = icon;
      if (icon.includes(':')) {
        // 如果是 "ant-design:robot-outlined" 格式，提取后面的部分
        iconName = icon.split(':')[1];
      }
      
      // 转换为 PascalCase: "robot-outlined" -> "RobotOutlined"
      iconName = iconName
        .split('-')
        .map((part: string) => part.charAt(0).toUpperCase() + part.slice(1))
        .join('');
      
      // 尝试从 @ant-design/icons-vue 中获取图标组件
      const IconComponent = (Icons as any)[iconName] || 
                           (Icons as any)[iconName + 'Outlined'] || 
                           (Icons as any)[iconName + 'Filled'] ||
                           (Icons as any)[iconName + 'TwoTone'];
      
      if (!IconComponent) {
        console.warn(`Icon "${icon}" not found, using default`);
        // 返回一个默认图标
        return h((Icons as any).QuestionCircleOutlined, attrs);
      }
      
      return h(IconComponent, attrs);
    };
  },
});

