import { defineStore } from 'pinia'
import { menuTree, type SysTreeNode } from '@/api/system'
import Layout from '@/layout/index.vue'
import ParentView from '@/components/ParentView/index.vue'
import { constantRoutes, dynamicRoutes, registerFallbackRoute, setRootRedirect } from '@/router'
import { filterAsyncRouter, filterDynamicRoutes } from '@/utils/routeHelper'
import router from '@/router'

const usePermissionStore = defineStore('permission', {
  state: () => ({
    routes: [] as any[],
    addRoutes: [] as any[],
    sidebarRouters: [] as any[],
    topbarRouters: [] as any[],
    defaultRoutes: [] as any[],
    actionPermissions: [] as string[]
  }),
  actions: {
    setRoutes(routes: any[]) {
      this.addRoutes = routes
      this.routes = constantRoutes.concat(routes)
    },
    setSidebarRouters(routes: any[]) {
      this.sidebarRouters = routes
    },
    setTopbarRoutes(routes: any[]) {
      this.topbarRouters = routes
    },
    setDefaultRoutes(routes: any[]) {
      this.defaultRoutes = routes
    },
    generateRoutes() {
      return new Promise<any[]>((resolve, reject) => {
        menuTree()
          .then((menus) => {
            const actionPermissions = collectActionPermissions(menus || [])

            const sdata = JSON.parse(JSON.stringify(adaptMenus(menus || [], 'Layout')))
            const rdata = JSON.parse(JSON.stringify(adaptMenus(menus || [], 'Page')))

            const sidebarRoutes = filterAsyncRouter(sdata, Layout, ParentView)
            const registerRoutes = filterAsyncRouter(rdata, Layout, ParentView)

            this.actionPermissions = actionPermissions
            this.setRoutes(registerRoutes)
            this.setSidebarRouters(sidebarRoutes)
            this.setDefaultRoutes(sidebarRoutes)
            this.setTopbarRoutes(sidebarRoutes)

            registerRoutes.forEach((route: any) => {
              router.addRoute('Root', route)
            })
            filterDynamicRoutes(dynamicRoutes).forEach((route: any) => {
              router.addRoute(route)
            })
            setRootRedirect(firstMenuPath(sidebarRoutes))
            registerFallbackRoute()
            resolve(registerRoutes)
          })
          .catch((err) => reject(err))
      })
    }
  }
})

function isMenuNode(node: SysTreeNode): boolean {
  return !!(node.url && node.url.trim())
}

function firstMenuPath(routes: any[]): string | undefined {
  const wb = routes?.find((r) => r.path === '/workbench')
  if (wb) return '/workbench'
  const first = routes && routes[0]
  if (!first) return undefined
  if (first.redirect && typeof first.redirect === 'string') return first.redirect
  return first.path
}

function collectActionPermissions(nodes: SysTreeNode[]): string[] {
  const result: string[] = []
  const walk = (list: SysTreeNode[]) => {
    list.forEach((n) => {
      if (!isMenuNode(n) && n.code) {
        result.push(n.code)
      }
      if (n.children && n.children.length) {
        walk(n.children)
      }
    })
  }
  walk(nodes)
  return Array.from(new Set(result))
}

function adaptMenus(nodes: SysTreeNode[], topMode: 'Layout' | 'Page' = 'Layout'): any[] {
  const isTop = topMode === 'Layout'
  return adaptLevel(sortTopMenus(nodes), isTop)
}

const TOP_MENU_ORDER = [
  '/workbench',
  '/flow',
  '/fields',
  '/knowledge',
  '/datamanage',
  '/dtable',
  '/lineage',
  '/run',
  '/batch',
  '/result',
  '/system',
  '/guide',
  '/platform'
]

function sortTopMenus(nodes: SysTreeNode[]): SysTreeNode[] {
  const rank = (n: SysTreeNode) => {
    const i = TOP_MENU_ORDER.indexOf((n.url || '').trim())
    return i === -1 ? TOP_MENU_ORDER.length : i
  }
  return [...nodes].sort((a, b) => rank(a) - rank(b))
}

function adaptLevel(nodes: SysTreeNode[], isTop: boolean): any[] {
  return nodes
    .filter((n) => isMenuNode(n))
    .filter((n) => n.status === undefined || n.status === 1)
    .map((n) => {
      const menuChildren = (n.children || []).filter(isMenuNode)
      const children = menuChildren.length ? adaptLevel(menuChildren, false) : null
      const path = normalizePath(n.url)
      const route: any = {
        name: toRouteName(n.code || n.url || String(n.id)),
        path,
        meta: { title: n.name, icon: n.icon, permissions: [n.code].filter(Boolean) }
      }
      if (children) {
        route.component = isTop ? 'Layout' : 'ParentView'
        route.children = children
        route.redirect = resolveChildPath(path, children[0].path)
      } else {
        route.component = viewPathOf(n.url)
      }
      return route
    })
}

function resolveChildPath(parentPath: string, childPath: string): string {
  if (!childPath) return parentPath
  if (childPath.startsWith('/')) return childPath
  return `${parentPath}/${childPath}`.replace(/\/+/g, '/')
}

function normalizePath(url?: string): string {
  if (!url) return ''
  if (/^https?:\/\//.test(url)) return url
  return url.startsWith('/') ? url : `/${url}`
}

const VIEW_PATH_MAP: Record<string, string> = {
  '/flow': 'flow/FlowDesigner',
  '/knowledge': 'knowledge/KnowledgeView',
  '/datamanage': 'datamanage/ListDbView',
  '/result': 'result/ResultView',
  '/dtable': 'dtable/DTableView',
  '/batch': 'batch/BatchView',
  '/platform': 'platform/PlatformView',
  '/system': 'system/SystemView',
  '/lineage': 'datamanage/LineageGraph',
  '/run': 'run/RunView',
  '/workbench': 'workbench/WorkbenchView',
  '/guide': 'guide/GuideView',
  '/fields': 'datamanage/FieldView',
  '/feedback': 'feedback/FeedbackAdminView'
}

function viewPathOf(url?: string): string {
  if (!url) return ''
  const clean = url.replace(/^\//, '').replace(/\/$/, '')
  if (!clean) return ''
  const normalized = `/${clean}`
  if (VIEW_PATH_MAP[normalized]) {
    return VIEW_PATH_MAP[normalized]
  }
  return `${clean}/index`
}

function toRouteName(raw: string): string {
  return raw.replace(/[^a-zA-Z0-9]/g, '_').replace(/^_+/, '')
}

export default usePermissionStore
