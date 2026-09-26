<template>
  <div
    class="flow-node"
    :class="[`flow-node--t${meta.type}`, { 'is-selected': isSelected, 'is-active': isActive }]"
    :style="nodeStyle"
  >
    <div class="flow-node__rail" :style="{ background: railGradient }"></div>

    <div class="flow-node__body">
      <div class="flow-node__icon" :style="{ color: meta.color, background: iconBg }">
        <NodeIcon :name="meta.icon" :size="13" />
      </div>

      <div class="flow-node__text">
        <div class="flow-node__name u-truncate" :title="nodeName">{{ nodeName }}</div>
        <div class="flow-node__meta">
          <span class="flow-node__type">{{ meta.label }}</span>
          <span v-if="knowledgeCount" class="flow-node__dot">·</span>
          <span v-if="knowledgeCount" class="flow-node__k">{{ knowledgeCount }} refs</span>
        </div>
      </div>

      <div v-if="badgeText" class="flow-node__badge" :style="{ background: meta.color }">
        {{ badgeText }}
      </div>
    </div>

    <div class="flow-node__port flow-node__port--in" />
    <div class="flow-node__port flow-node__port--out" :class="{ 'is-live': isSelected }" />
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref, onMounted, onUnmounted } from 'vue'
import { getNodeMeta } from '../nodeMeta'
import { NodeType, type NodeData } from '@/types/flow'
import NodeIcon from './NodeIcon.vue'

const getNode = inject<() => any>('getNode')
const getGraph = inject<() => any>('getGraph')
const node = getNode?.()

const isSelected = ref(false)
const isActive = ref(false)

const data = computed<NodeData>(() => node?.getData() || {})
const nodeName = computed(() => data.value.nodeName || 'Unnamed Node')
const meta = computed(() => getNodeMeta(data.value.nodeType ?? 0))
const knowledgeCount = computed(() => data.value.knowledge?.length || 0)

const nodeStyle = computed(() => ({
  width: `${meta.value.width}px`,
  height: `${meta.value.height}px`
}))

const railGradient = computed(
  () => `linear-gradient(180deg, ${meta.value.color} 0%, ${meta.value.color}b3 100%)`
)

const iconBg = computed(() => `${meta.value.color}1a`)

const badgeText = computed(() => {
  const t = meta.value.type
  const json = data.value.nodeJson as Record<string, any> | undefined
  if (t === NodeType.SANDBOX && json?.ratio != null) return `${json.ratio}%`
  if (t === NodeType.DECISION && json?.result != null) {
    return { '1': 'Pass', '2': 'Reject', '3': 'Manual' }[String(json.result)] || ''
  }
  return ''
})

let disposers: Array<() => void> = []
onMounted(() => {
  const graph = getGraph?.()
  if (!graph || !node) return

  const syncSelection = () => {
    isSelected.value = graph.isSelected(node)
  }
  const syncActive = () => {
    isActive.value = false
  }

  syncSelection()
  graph.on('node:selected', syncSelection)
  graph.on('node:unselected', syncSelection)
  graph.on('node:mouseenter', syncActive)

  disposers = [
    () => graph.off('node:selected', syncSelection),
    () => graph.off('node:unselected', syncSelection),
    () => graph.off('node:mouseenter', syncActive)
  ]
})

onUnmounted(() => disposers.forEach((d) => d()))
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.flow-node {
  position: relative;
  box-sizing: border-box;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  box-shadow: var(--sh-sm);
  cursor: move;
  user-select: none;
  overflow: visible;
  transition: box-shadow var(--dur-normal) var(--ease-standard),
    border-color var(--dur-normal) var(--ease-standard),
    transform var(--dur-normal) var(--ease-spring);

  &:hover {
    border-color: var(--c-border-strong);
    box-shadow: var(--sh-md);
    transform: translateY(-3px);
  }

  &.is-selected {
    border-color: var(--c-primary);
    box-shadow: 0 0 0 3px var(--c-primary-ring), var(--sh-md);
    transform: translateY(-3px);
  }

  &__rail {
    position: absolute;
    top: 10px;
    bottom: 10px;
    left: 0;
    width: 3px;
    border-radius: 0 var(--r-xs) var(--r-xs) 0;
    transition: top var(--dur-normal) var(--ease-standard),
      bottom var(--dur-normal) var(--ease-standard);
  }

  &:hover &__rail,
  &.is-selected &__rail {
    top: 6px;
    bottom: 6px;
  }

  &__body {
    display: flex;
    align-items: center;
    height: 100%;
    padding: 0 var(--sp-3) 0 11px;
    gap: 8px;
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: var(--r-sm);
    flex-shrink: 0;
  }

  &__text {
    min-width: 0;
    flex: 1;
  }

  &__name {
    font-size: var(--fs-xs);
    font-weight: var(--fw-semibold);
    letter-spacing: var(--ls-snug);
    color: var(--c-text);
    line-height: 1.25;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    word-break: break-all;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    line-height: 1.4;
    margin-top: 1px;
  }

  &__dot {
    opacity: 0.5;
  }

  &__k {
    color: var(--c-primary);
    font-weight: var(--fw-medium);
  }

  &__badge {
    position: absolute;
    top: -7px;
    right: -7px;
    height: 18px;
    min-width: 18px;
    padding: 0 6px;
    border-radius: var(--r-full);
    color: var(--c-text-inverse);
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    line-height: 18px;
    text-align: center;
    box-shadow: var(--sh-xs);
    letter-spacing: 0;
  }

  &__port {
    position: absolute;
    width: 9px;
    height: 9px;
    border-radius: var(--r-full);
    background: var(--c-surface);
    border: 2px solid var(--c-border-strong);
    opacity: 0;
    pointer-events: none;
    transition: opacity var(--dur-fast) var(--ease-standard),
      transform var(--dur-fast) var(--ease-spring),
      border-color var(--dur-fast) var(--ease-standard);

    &--in {
      left: -5px;
      top: 50%;
      transform: translateY(-50%) scale(0.6);
    }

    &--out {
      right: -5px;
      top: 50%;
      transform: translateY(-50%) scale(0.6);
    }
  }

  &:hover &__port,
  &.is-selected &__port {
    opacity: 1;
    border-color: var(--c-primary);
    transform: translateY(-50%) scale(1);
  }

  &__port--out.is-live {
    background: var(--c-primary);
    box-shadow: 0 0 0 4px var(--c-primary-soft);
  }
}
</style>
