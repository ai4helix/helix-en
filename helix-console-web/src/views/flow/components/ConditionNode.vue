<template>
  <div class="cond-node" :class="evalClass">
    <div class="cond-node__field">{{ data.fieldCode }}</div>
    <div class="cond-node__expr">
      <span class="op">{{ data.operator }}</span>
      <span class="val">{{ displayValue }}</span>
    </div>
    <div v-if="data.eval" class="cond-node__eval">
      <template v-if="data.eval.unknownOperator">⚠ Unknown Operator</template>
      <template v-else>
        Actual {{ formatActual(data.eval.actual) }} · {{ data.eval.hit ? 'Hit' : 'Miss' }}
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { inject, onMounted, ref } from 'vue'
import type { Node } from '@antv/x6'

const getNode = inject<() => Node>('getNode')!

const data = ref<{
  fieldCode?: string
  operator?: string
  value?: string
  eval?: { hit?: boolean; actual?: unknown; unknownOperator?: boolean }
}>({})
const displayValue = ref('')
const evalClass = ref('')

function formatActual(v: unknown) {
  if (v === null || v === undefined) return '(missing)'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function sync(d: typeof data.value) {
  data.value = d || {}
  displayValue.value = d?.value ?? ''
  if (d?.eval) {
    if (d.eval.unknownOperator) evalClass.value = 'is-unknown'
    else evalClass.value = d.eval.hit ? 'is-hit' : 'is-miss'
  } else {
    evalClass.value = ''
  }
}

onMounted(() => {
  const node = getNode()
  sync(node.getData())
  node.on('change:data', ({ current }: { current: typeof data.value }) => sync(current))
})
</script>

<style scoped>
.cond-node {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  background: #fff;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  padding: 6px 10px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  font-size: 12px;
  color: #1f2329;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.cond-node__field {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-weight: 600;
  color: #2b5fd9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cond-node__expr {
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cond-node__expr .op {
  color: #d4380d;
  font-weight: 600;
  margin-right: 4px;
}
.cond-node__eval {
  margin-top: 3px;
  font-size: 11px;
  color: #8a8f99;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cond-node.is-hit {
  border-color: #52c41a;
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.25);
}
.cond-node.is-miss {
  border-color: #f5222d;
  box-shadow: 0 0 0 2px rgba(245, 34, 45, 0.25);
}
.cond-node.is-unknown {
  border-color: #bfbfbf;
  box-shadow: 0 0 0 2px rgba(191, 191, 191, 0.25);
}
</style>
