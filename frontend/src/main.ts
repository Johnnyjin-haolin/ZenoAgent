import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(Antd);
app.mount('#app');
