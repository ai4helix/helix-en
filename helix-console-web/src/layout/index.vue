<template>
  <div :class="classObj" class="app-wrapper">
    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside" />

    <Sidebar v-if="!sidebar.hide" class="sidebar-container" />

    <div
      :class="{ hasTagsView: needTagsView, sidebarHide: sidebar.hide, hideSidebar: !sidebar.opened }"
      class="main-container"
    >
      <div :class="{ 'fixed-header': fixedHeader }">
        <Navbar @set-layout="setLayout" />
        <TagsView v-if="needTagsView" />
      </div>
      <AppMain />
      <Settings ref="settingRef" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, watchEffect, onMounted } from 'vue'
import Sidebar from './components/Sidebar/index.vue'
import AppMain from './components/AppMain.vue'
import Navbar from './components/Navbar.vue'
import TagsView from './components/TagsView/index.vue'
import Settings from './components/Settings/index.vue'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'

const appStore = useAppStore()
const settingsStore = useSettingsStore()

const sidebar = computed(() => appStore.sidebar)
const device = computed(() => appStore.device)
const needTagsView = computed(() => settingsStore.tagsView)
const fixedHeader = computed(() => settingsStore.fixedHeader)

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile'
}))

const WIDTH = 992

function resizeHandler() {
  if (document.body.getBoundingClientRect().width - 1 < WIDTH) {
    appStore.toggleDevice('mobile')
    appStore.closeSideBar({ withoutAnimation: true })
  } else {
    appStore.toggleDevice('desktop')
  }
}

onMounted(() => {
  settingsStore.initTheme()
  resizeHandler()
  window.addEventListener('resize', resizeHandler)
})

watch(() => device.value, () => {
  if (device.value === 'mobile' && sidebar.value.opened) {
    appStore.closeSideBar({ withoutAnimation: false })
  }
})

function handleClickOutside() {
  appStore.closeSideBar({ withoutAnimation: false })
}

const settingRef = ref<InstanceType<typeof Settings> | null>(null)
function setLayout() {
  settingRef.value?.openSetting()
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss' as *;

.app-wrapper {
  position: relative;
  height: 100%;
  width: 100%;

  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

.main-container {
  position: relative;
  min-height: 100%;
  margin-left: var(--w-sidebar);
  transition: margin-left var(--dur-slow) var(--ease-standard);
}

.hideSidebar .main-container {
  margin-left: var(--w-sidebar-collapsed);
}

.sidebarHide .main-container {
  margin-left: 0;
}

.mobile .main-container {
  margin-left: 0;
}

.drawer-bg {
  position: absolute;
  top: 0;
  width: 100%;
  height: 100%;
  z-index: 999;
  background: #000;
  opacity: 0.3;
}

.fixed-header {
  position: fixed;
  top: 0;
  right: 0;
  z-index: var(--z-header);
  width: calc(100% - var(--w-sidebar));
  transition: width var(--dur-slow) var(--ease-standard);
}

.hideSidebar .fixed-header {
  width: calc(100% - var(--w-sidebar-collapsed));
}

.sidebarHide .fixed-header {
  width: 100%;
}

.mobile .fixed-header {
  width: 100%;
}
</style>
