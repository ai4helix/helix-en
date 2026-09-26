<template>
  <div class="ln" :class="{ dim: data.dim, selected: data.selected, 'is-dark': isDarkBg }" :style="boxStyle">
    <div class="ln-tag" :style="{ background: tagColor }">{{ typeTag }}</div>
    <div class="ln-label">{{ data.label }}</div>
    <div v-if="data.subType" class="ln-sub">{{ data.subType }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref, onMounted, onUnmounted } from 'vue'


const getNode = inject<() => any>('getNode')
const node = getNode?.()

const data = ref<any>(node?.getData() || {})

let off: (() => void) | null = null
onMounted(() => {
  if (!node) return
  const handler = () => {
    data.value = node.getData() || {}
  }
  node.on('change:data', handler)
  off = () => node.off('change:data', handler)
})
onUnmounted(() => off?.())

const PALETTE: Record<string, { bg: string; border: string }> = {
  FIELD: { bg: '#eaf4ff', border: '#409eff' },
  KNOWLEDGE: { bg: '#f3ecff', border: '#8e54e9' },
  NODE: { bg: '#fff3e0', border: '#e6a23c' },
  VERSION: { bg: '#1f6fe0', border: '#1f6fe0' }
}

const KNOWLEDGE_TAG: Record<string, string> = {
  RULE: 'Rule',
  SCORECARD: 'Scorecard',
  DECISION_TABLE: 'Decision Table',
  LIST_DB: 'List DB'
}

const palette = computed(() => PALETTE[data.value.type] || { bg: '#fff', border: '#dcdfe6' })
const boxStyle = computed(() => ({
  background: palette.value.bg,
  borderColor: palette.value.border
}))
const isDarkBg = computed(() => data.value.type === 'VERSION')
const tagColor = computed(() =>
  isDarkBg.value ? 'rgba(255, 255, 255, 0.22)' : palette.value.border
)
const typeTag = computed(() => {
  switch (data.value.type) {
    case 'FIELD':
      return 'Field'
    case 'NODE':
      return 'Node'
    case 'VERSION':
      return 'Version'
    default:
      return KNOWLEDGE_TAG[data.value.refType] || 'Knowledge'
  }
})
</script>

<style scoped>
.ln {
  border: 1.5px solid #dcdfe6;
  border-radius: 8px;
  padding: 5px 9px;
  width: 172px;
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
  font-size: 11px;
  color: #303133;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  transition: opacity 0.2s ease;
}
.ln.dim {
  opacity: 0.18;
}
.ln.selected {
  border-width: 2.5px;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.25), 0 2px 8px rgba(0, 0, 0, 0.12);
}
.ln-tag {
  display: inline-block;
  font-size: 10px;
  line-height: 1.4;
  padding: 0 5px;
  border-radius: 4px;
  color: #fff;
  margin-bottom: 2px;
}
.ln-label {
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ln-sub {
  color: #909399;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ln.is-dark .ln-label,
.ln.is-dark .ln-sub {
  color: #fff;
}
</style>
