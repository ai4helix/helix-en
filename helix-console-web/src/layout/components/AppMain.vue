<template>
  <section class="app-main">
    <router-view v-slot="{ Component, route }">
      <transition name="fade-transform" mode="out-in">
        <keep-alive :include="tagsViewStore.cachedViews">
          <component v-if="!route.meta.link" :is="Component" :key="route.path" />
        </keep-alive>
      </transition>
    </router-view>
  </section>
</template>

<script setup lang="ts">
import useTagsViewStore from '@/store/modules/tagsView'

const tagsViewStore = useTagsViewStore()
</script>

<style scoped lang="scss">
.app-main {
  position: relative;
  width: 100%;
  height: calc(100vh - var(--h-header));
  overflow: hidden;
}

.hasTagsView .app-main {
  height: calc(100vh - var(--h-header) - var(--h-tags));
}

.fixed-header + .app-main {
  padding-top: calc(var(--h-header) + var(--h-tags));
  height: 100vh;
}

.sidebarHide .app-main {
  width: 100%;
}
</style>
