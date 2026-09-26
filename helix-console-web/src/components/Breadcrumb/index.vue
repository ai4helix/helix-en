<template>
  <nav class="breadcrumb" aria-label="Breadcrumb">
    <span
      v-for="(item, index) in levelList"
      :key="item.path"
      class="breadcrumb__item"
      :class="{ 'is-last': index === levelList.length - 1 }"
    >
      <router-link v-if="item.redirect === 'noRedirect' || index === levelList.length - 1" to="" class="breadcrumb__link">
        {{ item.meta.title }}
      </router-link>
      <router-link v-else :to="item.redirect || item.path" class="breadcrumb__link">
        {{ item.meta.title }}
      </router-link>
      <span v-if="index < levelList.length - 1" class="breadcrumb__sep">
        <el-icon><ArrowRight /></el-icon>
      </span>
    </span>
  </nav>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import usePermissionStore from '@/store/modules/permission'

const route = useRoute()
const permissionStore = usePermissionStore()
const levelList = ref<any[]>([])

function homeRoute(): { path: string; title: string } {
  const first = (permissionStore.sidebarRouters || [])[0] as any
  const path = first?.path || '/flow'
  const title = first?.meta?.title || 'Home'
  return { path, title }
}

function getBreadcrumb() {
  const home = homeRoute()
  let matched: any[] = route.matched.filter(
    (item) => item.meta && item.meta.title && item.path !== home.path
  )
  matched = [{ path: home.path, meta: { title: home.title } } as any].concat(matched)
  levelList.value = matched
}

watch(() => route.path, getBreadcrumb, { immediate: true })
</script>

<style scoped lang="scss">
.breadcrumb {
  display: flex;
  align-items: center;
  min-width: 0;
  font-size: var(--fs-sm);

  &__item {
    display: inline-flex;
    align-items: center;
    min-width: 0;
  }

  &__link {
    color: var(--c-text-secondary);
    text-decoration: none;
    font-weight: var(--fw-medium);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: color var(--dur-fast) var(--ease-standard);

    &:hover {
      color: var(--c-primary);
    }
  }

  &__item.is-last &__link {
    color: var(--c-text);
    font-weight: var(--fw-semibold);
    cursor: default;
    pointer-events: none;
  }

  &__sep {
    display: inline-flex;
    align-items: center;
    margin: 0 var(--sp-2);
    color: var(--c-text-quaternary);
    font-size: 11px;
  }
}
</style>
