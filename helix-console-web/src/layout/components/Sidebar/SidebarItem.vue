<template>
  <template v-if="!isHidden(item)">
    <template v-if="hasOneShowingChild(item.children, item) && !onlyOneChild.children">
      <Link v-if="onlyOneChild.meta" :to="resolvePath(onlyOneChild.path)">
        <el-menu-item :index="resolvePath(onlyOneChild.path)" class="sidebar-menu-item">
          <el-icon v-if="onlyOneChild.meta.icon" class="sidebar-menu-item__icon">
            <component :is="onlyOneChild.meta.icon" />
          </el-icon>
          <template #title>
            <span>{{ onlyOneChild.meta.title }}</span>
          </template>
        </el-menu-item>
      </Link>
    </template>

    <el-sub-menu v-else :index="resolvePath(item.path)" teleported>
      <template #title>
        <el-icon v-if="item.meta && item.meta.icon" class="sidebar-menu-item__icon">
          <component :is="item.meta.icon" />
        </el-icon>
        <span>{{ item.meta && item.meta.title }}</span>
      </template>
      <SidebarItem
        v-for="child in item.children"
        :key="child.path"
        :item="child"
        :base-path="resolvePath(child.path)"
      />
    </el-sub-menu>
  </template>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Link from './Link.vue'

const props = defineProps<{
  item: any
  basePath: string
}>()

interface OnlyOneChild {
  path: string
  meta?: { title?: string; icon?: string; hidden?: boolean }
  children?: any[]
}

const onlyOneChild = ref<OnlyOneChild>({ path: '' })

function isHidden(node: any): boolean {
  return !!(node?.hidden || node?.meta?.hidden)
}

function hasOneShowingChild(children: any[] = [], parent: any): boolean {
  const showingChildren = children.filter((c) => {
    if (isHidden(c)) return false
    onlyOneChild.value = c
    return true
  })

  if (showingChildren.length === 1) {
    return true
  }

  if (showingChildren.length === 0) {
    onlyOneChild.value = { ...parent, path: '' }
    return true
  }

  return false
}

function resolvePath(routePath: string): string {
  if (/^https?:\/\//.test(routePath)) return routePath
  if (routePath.startsWith('/')) return routePath
  if (!routePath) return props.basePath
  return `${props.basePath}/${routePath}`.replace(/\/+/g, '/')
}
</script>

<style scoped lang="scss">
.sidebar-menu-item {
  &__icon {
    margin-right: var(--sp-2);
    font-size: 16px;
    width: 16px;
    flex-shrink: 0;
  }
}

:deep(.el-sub-menu__icon-arrow) {
  right: var(--sp-3);
}
</style>
