import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    activeMenu?: string
    hidden?: boolean
    affix?: boolean
    keepAlive?: boolean
    permissions?: string[]
    roles?: string[]
    link?: string
  }
}
