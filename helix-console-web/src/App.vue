<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import useSettingsStore from '@/store/modules/settings'

const settingsStore = useSettingsStore()

onMounted(() => {
  settingsStore.initTheme()
})
</script>
