<template>
  <div class="tags-view">
    <el-scrollbar class="tags-view__scroll">
      <router-link
        v-for="tag in visitedViews"
        :key="tag.path"
        :to="{ path: tag.path, query: tag.query }"
        class="tags-view__item"
        :class="{ 'is-active': isActive(tag) }"
        @contextmenu.prevent="openMenu(tag, $event)"
      >
        <span class="tags-view__dot" />
        <span class="tags-view__label">{{ tag.title }}</span>
        <el-icon v-if="!tag.affix" class="tags-view__close" @click.prevent.stop="closeTag(tag)">
          <Close />
        </el-icon>
      </router-link>
    </el-scrollbar>

    <ul v-show="menuVisible" :style="menuStyle" class="tags-view__menu">
      <li @click="refreshTag(selectedTag)">Refresh</li>
      <li v-if="!selectedTag?.affix" @click="closeTag(selectedTag!)">Close</li>
      <li @click="closeOthers(selectedTag!)">Close Others</li>
      <li @click="closeAll">Close All</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import useTagsViewStore, { type TagView } from '@/store/modules/tagsView'

const route = useRoute()
const router = useRouter()
const tagsViewStore = useTagsViewStore()

const visitedViews = computed(() => tagsViewStore.visitedViews)
const menuVisible = ref(false)
const selectedTag = ref<TagView | null>(null)
const menuLeft = ref(0)
const menuTop = ref(0)

const menuStyle = computed(() => ({ left: menuLeft.value + 'px', top: menuTop.value + 'px' }))

function isActive(tag: TagView) {
  return tag.path === route.path
}

function addTags() {
  const title = route.meta?.title as string | undefined
  if (!route.name || !title) return
  tagsViewStore.addView({
    path: route.path,
    fullPath: route.fullPath,
    name: route.name as string,
    title,
    query: route.query,
    affix: !!route.meta.affix,
    keepAlive: route.meta.keepAlive !== false
  } as TagView)
}

async function closeTag(tag: TagView) {
  tagsViewStore.delView(tag)
  if (isActive(tag)) {
    const target = await tagsViewStore.delViewAndReturn({ ...tag })
    router.push(target)
  }
  hideMenu()
}

function closeOthers(tag: TagView) {
  tagsViewStore.delOthersViews(tag)
  if (!isActive(tag)) router.push(tag.fullPath || tag.path)
  hideMenu()
}

function closeAll() {
  tagsViewStore.delAllViews()
  const remain = tagsViewStore.visitedViews
  const target = remain.length ? remain[0].fullPath || remain[0].path : router.getRoutes()
    .find((r) => r.meta && (r.meta as any).title && !(r.meta as any).hidden)?.path || '/404'
  router.push(target)
  hideMenu()
}

function refreshTag(tag: TagView | null) {
  hideMenu()
  if (!tag) return
  router.replace({ path: '/redirect' + tag.path, query: tag.query }).catch(() => {
    window.location.reload()
  })
}

function openMenu(tag: TagView, e: MouseEvent) {
  selectedTag.value = tag
  menuLeft.value = e.clientX + 4
  menuTop.value = e.clientY + 4
  menuVisible.value = true
}

function hideMenu() {
  menuVisible.value = false
}

function closeMenu(e: MouseEvent) {
  const el = e.target as HTMLElement
  if (el.closest('.tags-view__menu')) return
  hideMenu()
}

function initAffixTags() {
  const affixTags = router.getRoutes().filter((r) => r.meta && (r.meta as any).affix)
  affixTags.forEach((r) => {
    const title = (r.meta as any).title as string | undefined
    if (r.name && title) {
      tagsViewStore.addView({
        path: r.path,
        fullPath: r.path,
        name: r.name as string,
        title,
        affix: true
      } as TagView)
    }
  })
}

onMounted(() => {
  initAffixTags()
  addTags()
  document.addEventListener('click', closeMenu)
})

watch(() => route.path, () => {
  nextTick(addTags)
})
</script>

<style scoped lang="scss">
.tags-view {
  position: relative;
  height: var(--h-tags);
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);

  &__scroll {
    height: 100%;
    white-space: nowrap;

    :deep(.el-scrollbar__view) {
      display: inline-flex;
      align-items: center;
      height: 100%;
      padding: 0 var(--sp-2);
      gap: var(--sp-1);
    }
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 26px;
    padding: 0 var(--sp-2);
    border-radius: var(--r-sm);
    background: transparent;
    color: var(--c-text-secondary);
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    text-decoration: none;
    white-space: nowrap;
    transition: background-color var(--dur-fast) var(--ease-standard),
      color var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-fill-quaternary);
      color: var(--c-text);
    }

    &.is-active {
      background: var(--c-primary-soft);
      color: var(--c-primary);
    }
  }

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: var(--r-full);
    background: var(--c-text-quaternary);
    flex-shrink: 0;
  }

  &__item.is-active &__dot {
    background: var(--c-primary);
  }

  &__close {
    font-size: 11px;
    border-radius: var(--r-full);
    transition: background-color var(--dur-instant) var(--ease-standard);

    &:hover {
      background: var(--c-fill-secondary);
      color: var(--c-danger);
    }
  }

  &__menu {
    position: fixed;
    z-index: var(--z-toast);
    margin: 0;
    padding: var(--sp-1);
    list-style: none;
    min-width: 120px;
    border-radius: var(--r-md);
    background: var(--c-surface);
    border: 1px solid var(--c-border);
    box-shadow: var(--sh-md);

    li {
      padding: 6px var(--sp-3);
      border-radius: var(--r-xs);
      color: var(--c-text-secondary);
      font-size: var(--fs-sm);
      cursor: pointer;
      transition: background-color var(--dur-instant) var(--ease-standard);

      &:hover {
        background: var(--c-fill-quaternary);
        color: var(--c-text);
      }
    }
  }
}
</style>
