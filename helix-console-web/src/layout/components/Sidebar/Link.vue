<template>
  <component :is="type" v-bind="linkProps">
    <slot />
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ to: string }>()

const isExternal = computed(() => /^https?:\/\//.test(props.to))

const type = computed(() => (isExternal.value ? 'a' : 'router-link'))

const linkProps = computed(() =>
  isExternal.value ? { href: props.to, target: '_blank', rel: 'noopener' } : { to: props.to }
)
</script>
