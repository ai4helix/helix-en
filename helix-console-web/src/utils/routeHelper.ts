import { hasPermiOr, hasRoleOr } from '@/utils/permission'

const modules = import.meta.glob('../views/**/*.vue')

export function filterAsyncRouter(
  asyncRouterMap: any[],
  Layout: any,
  ParentView: any,
  type = false
): any[] {
  return asyncRouterMap.filter((route) => {
    if (type && route.children) {
      route.children = filterChildren(route.children)
    }
    if (route.component) {
      if (route.component === 'Layout') {
        route.component = Layout
      } else if (route.component === 'ParentView') {
        route.component = ParentView
      } else {
        route.component = loadView(route.component)
      }
    }
    if (route.children != null && route.children && route.children.length) {
      route.children = filterAsyncRouter(route.children, Layout, ParentView, type)
    } else {
      delete route.children
      delete route.redirect
    }
    return true
  })
}

function filterChildren(childrenMap: any[], lastRouter: any = null): any[] {
  let children: any[] = []
  childrenMap.forEach((el) => {
    el.path = lastRouter ? `${lastRouter.path}/${el.path}` : el.path
    if (el.children && el.children.length && el.component === 'ParentView') {
      children = children.concat(filterChildren(el.children, el))
    } else {
      children.push(el)
    }
  })
  return children
}

export function filterDynamicRoutes(routes: any[]): any[] {
  const res: any[] = []
  routes.forEach((route) => {
    if (route.permissions) {
      if (hasPermiOr(route.permissions)) res.push(route)
    } else if (route.roles) {
      if (hasRoleOr(route.roles)) res.push(route)
    }
  })
  return res
}

export function loadView(view: string) {
  let res: any
  for (const path in modules) {
    const dir = path.split('views/')[1].split('.vue')[0]
    if (dir === view) {
      res = modules[path]
    }
  }
  return res
}
