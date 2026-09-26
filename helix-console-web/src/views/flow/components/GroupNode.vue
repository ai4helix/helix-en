<template>
  <div class="group-node" :class="typeClass">
    <span>{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
import { inject, onMounted, ref } from 'vue'
import type { Node } from '@antv/x6'
import { groupLabel } from '@/types/rule'

const getNode = inject<() => Node>('getNode')!

const data = ref<{ nodeType?: number }>({})
const label = ref('')
const typeClass = ref('')

function sync(d: typeof data.value) {
  data.value = d || {}
  label.value = groupLabel(d?.nodeType ?? 0)
  typeClass.value =
    d?.nodeType === 3 ? 'is-or' : d?.nodeType === 4 ? 'is-not' : 'is-and'
}

onMounted(() => {
  const node = getNode()
  sync(node.getData())
  node.on('change:data', ({ current }: { current: typeof data.value }) => sync(current))
})
</script>

<style scoped>
.group-node {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  border: 1px solid rgba(0, 0, 0, 0.12);
}
.group-node.is-and {
  background: #2f54eb;
}
.group-node.is-or {
  background: #fa8c16;
}
.group-node.is-not {
  background: #cf1322;
}
</style>
