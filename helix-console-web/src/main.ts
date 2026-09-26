import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import en from 'element-plus/es/locale/lang/en'
import App from './App.vue'
import router from './router'
import './permission'
import { getToken, removeToken } from './utils/auth'
import { useAuthStore } from './stores/auth'
import usePermissionStore from './store/modules/permission'
import './styles/tokens.scss'
import './styles/global.scss'
import './styles/element-apple.scss'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(ElementPlus, { locale: en })

async function bootstrap(): Promise<void> {
  if (getToken()) {
    try {
      const auth = useAuthStore()
      if (!auth.userId) {
        const ok = await auth.restore()
        if (!ok) throw new Error('Session expired')
      }
      await usePermissionStore().generateRoutes()
    } catch {
      removeToken()
    }
  }
  app.use(router)
  app.mount('#app')
}

bootstrap()
