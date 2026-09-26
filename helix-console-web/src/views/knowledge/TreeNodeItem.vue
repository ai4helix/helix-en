<template>
  <div class="tn">
    <div
      class="tree-item"
      :class="{ 'is-on': current === node.id, 'is-drop': dropTarget === node.id }"
      :style="{ paddingLeft: `${8 + depth * 14}px` }"
      :data-node-id="node.id"
      draggable="true"
      @click="$emit('select', node.id)"
      @dragstart="onDragStart"
      @dragover.prevent="dropTarget = node.id"
      @dragleave="dropTarget === node.id && (dropTarget = null)"
      @drop.prevent="onDrop"
      @dragend="onDragEnd"
    >
      <button
        v-if="hasChildren"
        class="tree-item__caret"
        :class="{ 'is-open': expanded }"
        @click.stop="expanded = !expanded"
      >
        <el-icon :size="11"><CaretRight /></el-icon>
      </button>
      <span v-else class="tree-item__caret tree-item__caret--leaf" />

      <el-icon :size="13">
        <component :is="expanded && hasChildren ? 'FolderOpened' : 'Folder'" />
      </el-icon>
      <span class="tree-item__text" :title="node.name">{{ node.name }}</span>

      <span v-if="node.count" class="tree-item__count">{{ node.count }}</span>

      <div v-if="!node.system" class="tree-item__ops" @click.stop>
        <button class="mini" title="New Subfolder" @click="$emit('add-child', node.id)">
          <el-icon :size="11"><Plus /></el-icon>
        </button>
        <button class="mini" title="Rename" @click="$emit('rename', node)">
          <el-icon :size="11"><EditPen /></el-icon>
        </button>
        <button class="mini mini--danger" title="Delete" @click="$emit('remove', node)">
          <el-icon :size="11"><Delete /></el-icon>
        </button>
      </div>
    </div>

    <template v-if="expanded && hasChildren">
      <TreeNodeItem
        v-for="c in node.children"
        :key="c.id"
        :node="c"
        :current="current"
        :depth="depth + 1"
        :drag-id="dragId"
        :drag-kind="dragKind"
        @select="$emit('select', $event)"
        @rename="$emit('rename', $event)"
        @remove="$emit('remove', $event)"
        @add-child="$emit('add-child', $event)"
        @drag-start="$emit('drag-start', $event)"
        @drag-end="$emit('drag-end')"
        @move-node="(p) => $emit('move-node', p)"
        @move-rule="(p) => $emit('move-rule', p)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { TreeNode } from '@/api/knowledge'


const props = withDefaults(
  defineProps<{
    node: TreeNode
    current?: number
    depth?: number
    dragId?: number | null
    dragKind?: 'dir' | 'rule' | 'field' | null
  }>(),
  { depth: 0, dragId: null, dragKind: 'dir' }
)

const emit = defineEmits<{
  (e: 'select', id: number | undefined): void
  (e: 'rename', node: TreeNode): void
  (e: 'remove', node: TreeNode): void
  (e: 'add-child', id: number): void
  (e: 'drag-start', node: TreeNode): void
  (e: 'drag-end'): void
  (e: 'move-node', payload: { id: number; parentId: number }): void
  (e: 'move-rule', payload: { id: number; parentId: number }): void
}>()

const expanded = ref(true)
const hasChildren = computed(() => (props.node.children?.length || 0) > 0)
const dropTarget = ref<number | null>(null)

function onDragStart(e: DragEvent) {
  e.dataTransfer?.setData('text/plain', String(props.node.id))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
  emit('drag-start', props.node)
}

function onDragEnd() {
  dropTarget.value = null
  emit('drag-end')
}

function onDrop() {
  dropTarget.value = null
  if (props.dragId == null) return
  if (props.dragKind === 'rule') {
    emit('move-rule', { id: props.dragId, parentId: props.node.id })
    return
  }
  if (props.dragKind === 'field') return
  if (props.dragId === props.node.id) return
  emit('move-node', { id: props.dragId, parentId: props.node.id })
}
</script>

<style scoped lang="scss">
.tree-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px var(--sp-2);
  border-radius: var(--r-sm);
  cursor: pointer;
  color: var(--c-text-secondary);
  font-size: var(--fs-sm);
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-fill-quaternary);
    color: var(--c-text);

    .tree-item__ops {
      opacity: 1;
    }
  }

  &.is-on {
    background: var(--c-primary-soft);
    color: var(--c-primary);
    font-weight: var(--fw-medium);
  }

  &.is-drop {
    background: var(--c-primary-soft);
    outline: 1.5px dashed var(--c-primary);
    outline-offset: -1.5px;
    color: var(--c-primary);
  }

  &__caret {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 14px;
    height: 14px;
    flex-shrink: 0;
    border: none;
    background: transparent;
    color: var(--c-text-quaternary);
    cursor: pointer;
    transition: transform var(--dur-fast) var(--ease-standard);

    &.is-open {
      transform: rotate(90deg);
    }

    &--leaf {
      cursor: default;
    }
  }

  &__text {
    flex: 1;
    min-width: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__count {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    font-variant-numeric: tabular-nums;
  }

  &__ops {
    display: flex;
    gap: 1px;
    opacity: 0;
    transition: opacity var(--dur-fast) var(--ease-standard);
  }
}

.mini {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: var(--r-xs);
  background: transparent;
  color: var(--c-text-tertiary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-fill-tertiary);
    color: var(--c-text);
  }

  &--danger:hover {
    background: var(--c-danger-soft);
    color: var(--c-danger);
  }
}
</style>
