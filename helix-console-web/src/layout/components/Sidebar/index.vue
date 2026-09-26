<template>
  <div :class="{ 'has-logo': showLogo, [sideTheme]: true }" class="sidebar-container">
    <Logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :unique-opened="true"
        :collapse-transition="false"
        mode="vertical"
        class="sidebar-menu"
      >
        <SidebarItem
          v-for="(route, index) in sidebarRouters"
          :key="route.path + index"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Logo from './Logo.vue'
import SidebarItem from './SidebarItem.vue'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const route = useRoute()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()

const sidebarRouters = computed(() => permissionStore.sidebarRouters)
const showLogo = computed(() => settingsStore.sidebarLogo)
const sideTheme = computed(() => settingsStore.sideTheme)
const isCollapse = computed(() => !appStore.sidebar.opened)

const activeMenu = computed(() => {
  const { meta, path } = route
  return (meta.activeMenu as string) || path
})
</script>

<style scoped lang="scss">
.sidebar-container {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: var(--z-panel);
  width: var(--w-sidebar);
  height: 100%;
  overflow: hidden;
  background: var(--c-surface);
  border-right: 1px solid var(--c-border);
  transition: width var(--dur-slow) var(--ease-standard);

  .scrollbar-wrapper {
    height: calc(100% - var(--h-header));
    overflow-x: hidden;

    :deep(.el-scrollbar__view) {
      height: 100%;
    }
  }

  .sidebar-menu {
    height: 100%;
    border-right: none;
    padding: var(--sp-2);
    background: transparent;

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      height: 40px;
      line-height: 40px;
      margin-bottom: 2px;
      border-radius: var(--r-sm);
      color: var(--sidebar-text);
      font-size: var(--fs-sm);
      font-weight: var(--fw-medium);
      transition: background-color var(--dur-fast) var(--ease-standard),
        color var(--dur-fast) var(--ease-standard);

      &:hover {
        background-color: var(--sidebar-hover-bg);
        color: var(--c-text);
      }
    }

    :deep(.el-menu-item.is-active) {
      background-color: var(--sidebar-active-bg);
      color: var(--sidebar-text-active);
      font-weight: var(--fw-semibold);
    }

    :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
      color: var(--sidebar-text-active);
    }

    :deep(.el-menu--collapse) {
      width: var(--w-sidebar-collapsed);
    }
  }
}

.hideSidebar .sidebar-container {
  width: var(--w-sidebar-collapsed);
}

.theme-dark {
  background: #1c1c1e;
  border-right-color: rgba(255, 255, 255, 0.08);
  --sidebar-text: rgba(255, 255, 255, 0.66);
  --sidebar-text-active: #fff;
  --sidebar-active-bg: rgba(10, 132, 255, 0.24);
  --sidebar-hover-bg: rgba(255, 255, 255, 0.08);
}
</style>
