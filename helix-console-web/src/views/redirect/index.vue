<template>
  <div />
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const { params, query } = route
const path = typeof params.path === 'string' ? params.path : '/'

router.replace({ path: '/' + path.replace(/^\//, ''), query })
</script>
