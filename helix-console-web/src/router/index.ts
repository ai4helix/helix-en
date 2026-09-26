import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

/**
 * Constant routes: pages accessible without authentication.
 *
 * Business pages are NOT registered here; they are built dynamically from the
 * backend menu tree by the permission store. Registering the same path in both
 * places would trigger duplicate-route warnings, and the dynamic registration
 * would win — silently bypassing menu-based access control.
 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: 'Sign in', hidden: true }
  },
  {
    // Tags-view "refresh" relay: land here then replace back to force the component to re-create
    path: '/redirect/:path(.*)',
    component: () => import('@/views/redirect/index.vue'),
    meta: { title: 'Redirecting', hidden: true }
  },
  {
    path: '/404',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true }
  },
  {
    path: '/401',
    component: () => import('@/views/error/401.vue'),
    meta: { title: '401', hidden: true }
  },
  {
    // Root container: children are mounted dynamically (addRoute('Root', ...)).
    // No static redirect: a hardcoded path would 404 for users without that menu.
    // After dynamic routes load, setRootRedirect() points it to the first accessible menu.
    path: '/',
    name: 'Root',
    component: Layout,
    children: []
  }
]

/**
 * 404 fallback route.
 *
 * NOT registered in constantRoutes — otherwise the first match on a refresh of
 * a business page would resolve to /404 and never recover once dynamic routes
 * are registered. Appended afterwards via registerFallbackRoute() by the
 * permission store.
 */
export const FALLBACK_ROUTE: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  redirect: '/404',
  meta: { hidden: true }
}

/**
 * Frontend-only dynamic routes, registered after permissions / roles filtering.
 * Run Center (/run) comes from the menu tree via the permission store's
 * VIEW_PATH_MAP['/run'], so it is not listed here to avoid duplicate-route warnings.
 */
export const dynamicRoutes: RouteRecordRaw[] = []

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 })
})

let fallbackRegistered = false

export function registerFallbackRoute() {
  if (fallbackRegistered) return
  fallbackRegistered = true
  router.addRoute(FALLBACK_ROUTE)
}

let rootRedirectPath = ''

export function setRootRedirect(path?: string) {
  rootRedirectPath = path || ''
}

router.beforeEach((to) => {
  if (to.path === '/' && to.name === 'Root' && rootRedirectPath) {
    return { path: rootRedirectPath, replace: true }
  }
})

export default router
