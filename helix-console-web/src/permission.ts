import router from './router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { useAuthStore } from '@/stores/auth'
import usePermissionStore from '@/store/modules/permission'
import useSettingsStore from '@/store/modules/settings'
import defaults from '@/settings'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login']

router.beforeEach(async (to, _from, next) => {
  NProgress.start()

  if (!getToken()) {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
      NProgress.done()
    }
    return
  }

  if (to.path === '/login') {
    next({ path: '/' })
    NProgress.done()
    return
  }

  const auth = useAuthStore()
  const permissionStore = usePermissionStore()

  if (permissionStore.addRoutes.length === 0) {
    try {
      if (!auth.userId) {
        const ok = await auth.restore()
        if (!ok) throw new Error('Session expired')
      }
      await permissionStore.generateRoutes()
      next({ path: to.fullPath, replace: true })
    } catch (err: any) {
      auth.logout()
      ElMessage.error(err?.message || 'Session expired, please sign in again')
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
      NProgress.done()
    }
    return
  }

  next()
})

router.afterEach((to) => {
  const title = (to.meta.title as string) || ''
  const settings = useSettingsStore()
  if (title && settings.dynamicTitle) {
    document.title = `${title} - ${defaults.title}`
  } else if (title) {
    document.title = title
  }
  NProgress.done()
})
