import { defineStore } from 'pinia'
import defaultSettings from '@/settings'

const storageSetting = JSON.parse(localStorage.getItem('layout-setting') || '{}')

const useSettingsStore = defineStore('settings', {
  state: () => ({
    title: '',
    theme: storageSetting.theme || '#0a84ff',
    sideTheme: storageSetting.sideTheme || defaultSettings.sideTheme,
    showSettings: defaultSettings.showSettings,
    topNav: storageSetting.topNav === undefined ? defaultSettings.topNav : storageSetting.topNav,
    tagsView: storageSetting.tagsView === undefined ? defaultSettings.tagsView : storageSetting.tagsView,
    fixedHeader: storageSetting.fixedHeader === undefined ? defaultSettings.fixedHeader : storageSetting.fixedHeader,
    sidebarLogo: storageSetting.sidebarLogo === undefined ? defaultSettings.sidebarLogo : storageSetting.sidebarLogo,
    dynamicTitle: storageSetting.dynamicTitle === undefined ? defaultSettings.dynamicTitle : storageSetting.dynamicTitle,
    isDark: false
  }),
  actions: {
    changeSetting(data: { key: string; value: any }) {
      const { key, value } = data
      if (Object.prototype.hasOwnProperty.call(this, key)) {
        // @ts-ignore dynamic assignment
        this[key] = value
      }
    },
    setTitle(title: string) {
      this.title = title
      if (this.dynamicTitle) {
        document.title = `${title} - ${defaultSettings.title}`
      } else {
        document.title = defaultSettings.title
      }
    },
    toggleTheme() {
      this.isDark = !this.isDark
      const el = document.documentElement
      if (this.isDark) {
        el.classList.add('dark')
      } else {
        el.classList.remove('dark')
      }
      localStorage.setItem('layout-setting', JSON.stringify({ isDark: this.isDark }))
    },
    initTheme() {
      const saved = JSON.parse(localStorage.getItem('layout-setting') || '{}')
      if (saved.isDark) {
        this.isDark = true
        document.documentElement.classList.add('dark')
      }
    }
  }
})

export default useSettingsStore
