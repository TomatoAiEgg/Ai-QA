import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './styles/markdown.css'  // 全局 Markdown 样式（用于 v-html 内容）
import router from './router'
import App from './App.vue'

const app = createApp(App)

app.use(ElementPlus, { locale: zhCn })
app.use(router)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
