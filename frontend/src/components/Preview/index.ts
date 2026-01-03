/**
 * 图片预览工具
 */
import { Modal } from 'ant-design-vue';

export function createImgPreview(imgList: string[] | string, index: number = 0) {
  const images = Array.isArray(imgList) ? imgList : [imgList];
  
  Modal.info({
    title: '图片预览',
    width: '80%',
    content: h('div', {
      style: {
        textAlign: 'center',
      },
    }, [
      h('img', {
        src: images[index] || images[0],
        style: {
          maxWidth: '100%',
          maxHeight: '70vh',
        },
      }),
    ]),
    footer: null,
  });
}

import { h } from 'vue';

