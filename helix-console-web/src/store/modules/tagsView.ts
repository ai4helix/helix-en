import { defineStore } from 'pinia'

export interface TagView {
  path: string
  fullPath: string
  name?: string
  title: string
  query?: Record<string, any>
  affix?: boolean
  keepAlive?: boolean
}

const useTagsViewStore = defineStore('tagsView', {
  state: () => ({
    visitedViews: [] as TagView[],
    cachedViews: [] as string[]
  }),
  actions: {
    addView(view: TagView) {
      this.addVisitedView(view)
      this.addCachedView(view)
    },
    addVisitedView(view: TagView) {
      if (this.visitedViews.some((v) => v.path === view.path)) return
      this.visitedViews.push(
        Object.assign({}, view, {
          title: view.title || 'no-name'
        })
      )
    },
    addCachedView(view: TagView) {
      if (!view.name) return
      if (this.cachedViews.includes(view.name)) return
      if (view.keepAlive === false) return
      this.cachedViews.push(view.name)
    },
    delView(view: TagView) {
      return new Promise<{ visitedViews: TagView[]; cachedViews: string[] }>((resolve) => {
        this.delVisitedView(view)
        this.delCachedView(view)
        resolve({ visitedViews: this.visitedViews, cachedViews: this.cachedViews })
      })
    },
    delVisitedView(view: TagView) {
      const i = this.visitedViews.findIndex((v) => v.path === view.path)
      if (i > -1) this.visitedViews.splice(i, 1)
    },
    delCachedView(view: TagView) {
      if (!view.name) return
      const i = this.cachedViews.indexOf(view.name)
      if (i > -1) this.cachedViews.splice(i, 1)
    },
    delOthersViews(view: TagView) {
      this.visitedViews = this.visitedViews.filter((v) => v.affix || v.path === view.path)
      this.cachedViews = this.cachedViews.filter(
        (n) => n === view.name || this.visitedViews.some((v) => v.name === n)
      )
    },
    delAllViews() {
      this.visitedViews = this.visitedViews.filter((v) => v.affix)
      this.cachedViews = this.visitedViews.map((v) => v.name!).filter(Boolean)
    },
    async delViewAndReturn(view: TagView) {
      await this.delView(view)
      const latest = this.visitedViews[this.visitedViews.length - 1]
      return latest ? latest.fullPath || latest.path : '/'
    },
    updateVisitedView(view: TagView) {
      const i = this.visitedViews.findIndex((v) => v.path === view.path)
      if (i > -1) Object.assign(this.visitedViews[i], view)
    }
  }
})

export default useTagsViewStore
