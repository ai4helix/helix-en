<template>
  <div class="kb">
    <header class="kb__top">
      <div class="kb__lead">
        <div class="seg-tabs">
          <button
            v-for="t in tabs"
            :key="t.value"
            class="seg-tabs__btn"
            :class="{ 'is-on': tab === t.value }"
            @click="switchTab(t.value as 'rule' | 'scorecard')"
          >
            {{ t.label }}
          </button>
        </div>
      </div>
      <div class="kb__actions">
        <el-input
          v-model="keyword"
          placeholder="Search by name or code"
          clearable
          class="kb__search"
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <button class="btn btn--primary" @click="openCreate">
          <el-icon><Plus /></el-icon><span>New {{ isRuleTab ? 'Rule' : 'Scorecard' }}</span>
        </button>
      </div>
    </header>

    <div class="kb__body">
      <aside class="kb__tree u-scroll-y">
        <div class="tree-head">
          <span class="tree-head__label">{{ isRecycleView ? 'Recycle Bin' : 'Folders' }}</span>
          <button v-if="!isRecycleView" class="tree-head__add" title="New Folder" @click="openCreateDir()">
            <el-icon :size="13"><FolderAdd /></el-icon>
          </button>
        </div>

        <div class="tree-item" :class="{ 'is-on': !currentParentId }" @click="selectParent(undefined)">
          <el-icon :size="13"><Files /></el-icon>
          <span class="tree-item__text">All</span>
          <span class="tree-item__count">{{ total }}</span>
        </div>

        <TreeNodeItem
          v-for="n in tree"
          :key="n.id"
          :node="n"
          :current="currentParentId"
          :drag-id="dragId"
          :drag-kind="dragKind"
          @select="selectParent"
          @rename="openRename"
          @remove="handleDeleteDir"
          @add-child="openCreateDir"
          @drag-start="dragId = $event.id; dragKind = 'dir'"
          @drag-end="dragId = null; dragKind = null"
          @move-node="handleMoveNode"
          @move-rule="handleMoveRule"
        />
        <div
          v-if="dragId && dragKind === 'dir'"
          class="root-drop"
          :class="{ 'is-drop': rootDropHover }"
          @dragover.prevent="rootDropHover = true"
          @dragleave="rootDropHover = false"
        >
          <el-icon><Top /></el-icon>
          Drop here to move to top level
        </div>

        <div class="tree-foot">
          <button class="tree-foot__btn" :class="{ 'is-on': isRecycleView }" @click="switchRecycle">
            <el-icon :size="13"><Delete /></el-icon>
            <span>Recycle Bin</span>
          </button>
        </div>
      </aside>

      <main class="kb__main">
        <div v-if="loading" class="kb__loading"><el-icon class="is-loading" :size="20"><Loading /></el-icon></div>

        <template v-else>
          <div v-if="!records.length" class="kb__empty">
            <div class="kb__empty-icon"><el-icon :size="24"><DocumentRemove /></el-icon></div>
            <div class="kb__empty-title">{{ isRecycleView ? 'Recycle Bin is empty' : 'Nothing here yet' }}</div>
            <div class="kb__empty-desc">
              {{ isRecycleView ? 'Deleted items will appear here' : `Click "New ${isRuleTab ? 'Rule' : 'Scorecard'}" in the top right` }}
            </div>
          </div>

          <div v-else class="cards">
            <article
              v-for="r in records"
              :key="r.id"
              class="card"
              :class="{ 'is-off': r.status === 0, 'is-draggable': isRuleTab && !isRecycleView }"
              :draggable="isRuleTab && !isRecycleView"
              :title="isRuleTab && !isRecycleView ? 'Drag to a folder on the left to re-categorize' : ''"
              @dragstart="onCardDragStart($event, r as RuleVO)"
              @dragend="dragId = null; dragKind = null"
              @click="openEdit(r)"
            >
              <div class="card__head">
                <div class="card__title u-truncate" :title="r.name">{{ r.name }}</div>
                <span class="card__tag" :class="tagClass(r)">{{ tagText(r) }}</span>
              </div>

              <div class="card__code u-truncate">{{ r.code || '—' }}</div>

              <div class="card__desc">{{ r.description || 'No description' }}</div>

              <div class="card__foot">
                <span class="card__dir u-truncate">
                  <el-icon :size="11"><Folder /></el-icon>
                  {{ (r as RuleVO).parentName || 'Uncategorized' }}
                </span>
                <div class="card__ops" @click.stop>
                  <button class="op" title="Copy" @click="handleCopy(r)">
                    <el-icon :size="13"><CopyDocument /></el-icon>
                  </button>
                  <button
                    v-if="!isRecycleView"
                    class="op"
                    :title="r.status === 1 ? 'Disable' : 'Enable'"
                    @click="handleToggle(r)"
                  >
                    <el-icon :size="13">
                      <component :is="r.status === 1 ? 'VideoPause' : 'VideoPlay'" />
                    </el-icon>
                  </button>
                  <button class="op op--danger" title="Delete" @click="handleRecycle(r)">
                    <el-icon :size="13"><Delete /></el-icon>
                  </button>
                </div>
              </div>
            </article>
          </div>

          <div v-if="total > pageSize" class="pager">
            <el-pagination
              :current-page="pageNo"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              background
              @current-change="onPageChange"
            />
          </div>
        </template>
      </main>
    </div>

    <el-drawer
      v-model="editorVisible"
      :title="editingId ? `Edit ${isRuleTab ? 'Rule' : 'Scorecard'}` : `New ${isRuleTab ? 'Rule' : 'Scorecard'}`"
      size="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <template v-if="isRuleTab">
        <RuleEditor ref="ruleEditorRef" :initial="ruleInitial" />
      </template>
      <template v-else>
        <ScorecardEditor ref="scorecardEditorRef" :initial="scorecardInitial" />
      </template>

      <template #footer>
        <div class="drawer-foot">
          <button class="btn" @click="editorVisible = false">Cancel</button>
          <button class="btn btn--primary" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? 'Saving…' : 'Save' }}
          </button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RuleEditor from './RuleEditor.vue'
import ScorecardEditor from './ScorecardEditor.vue'
import TreeNodeItem from './TreeNodeItem.vue'
import { Top } from '@element-plus/icons-vue'
import {
  pageRules, getRule, createRule, updateRule, copyRule, changeRuleStatus, recycleRules,
  deleteRulesPermanently,
  pageScorecards, getScorecard, createScorecard, updateScorecard, copyScorecard,
  recycleScorecards, deleteScorecardsPermanently,
  getTree, createTreeNode, renameTreeNode, deleteTreeNode, moveTreeNode, moveRule,
  type RuleVO, type RuleSavePayload, type ScorecardVO, type ScorecardDetail, type TreeNode
} from '@/api/knowledge'

const tabs = [
  { value: 'rule', label: 'Rule' },
  { value: 'scorecard', label: 'Scorecard' }
]

const tab = ref<'rule' | 'scorecard'>('rule')
const keyword = ref('')
const loading = ref(false)
const records = ref<Array<RuleVO | ScorecardVO>>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(12)
const currentParentId = ref<number | undefined>()
const isRecycleView = ref(false)
const tree = ref<TreeNode[]>([])
const dragId = ref<number | null>(null)
const dragKind = ref<'dir' | 'rule' | null>(null)
const rootDropHover = ref(false)

function onCardDragStart(e: DragEvent, r: RuleVO) {
  dragId.value = r.id
  dragKind.value = 'rule'
  e.dataTransfer?.setData('text/plain', String(r.id))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

async function handleMoveRule(p: { id: number; parentId: number }) {
  try {
    await moveRule(p.id, p.parentId)
    ElMessage.success('Rule moved')
  } catch {
    ElMessage.error('Move failed')
  }
  dragId.value = null
  dragKind.value = null
  await Promise.all([loadTree(), loadList()])
}

async function handleMoveNode(p: { id: number; parentId: number }) {
  try {
    await moveTreeNode(p.id, p.parentId)
    ElMessage.success('Folder moved')
  } catch {
    ElMessage.error('Move failed (cannot move into itself or its own subfolders)')
  }
  dragId.value = null
  await loadTree()
}

function onWinDrop(e: DragEvent) {
  if (dragId.value == null) return
  const t = e.target as HTMLElement | null
  if (t && t.closest('.tn')) return
  e.preventDefault()
  e.stopPropagation()
  onDropToRoot()
}

watch(dragId, (v) => {
  if (v != null) window.addEventListener('drop', onWinDrop, true)
  else window.removeEventListener('drop', onWinDrop, true)
})
onBeforeUnmount(() => window.removeEventListener('drop', onWinDrop, true))

async function onDropToRoot() {
  rootDropHover.value = false
  const id = dragId.value
  dragId.value = null
  if (id == null) return
  try {
    await moveTreeNode(id, 0)
    ElMessage.success('Moved to top level')
  } catch {
    ElMessage.error('Move failed')
  }
  await loadTree()
}

const editorVisible = ref(false)
const editingId = ref<number>()
const submitting = ref(false)
const ruleEditorRef = ref<InstanceType<typeof RuleEditor>>()
const scorecardEditorRef = ref<InstanceType<typeof ScorecardEditor>>()
const ruleInitial = ref<Partial<RuleSavePayload>>()
const scorecardInitial = ref<Partial<ScorecardDetail>>()

const isRuleTab = computed(() => tab.value === 'rule')

onMounted(reload)

watch(tab, () => {
  currentParentId.value = undefined
  isRecycleView.value = false
  pageNo.value = 1
  reload()
})

async function reload() {
  loading.value = true
  try {
    await loadTree()
    await loadList()
  } finally {
    loading.value = false
  }
}

async function loadTree() {
  const treeType = isRecycleView.value
    ? (isRuleTab.value ? 2 : 3)
    : (isRuleTab.value ? 0 : 1)
  tree.value = await getTree(treeType)
}

async function loadList() {
  const status = isRecycleView.value ? -1 : undefined
  const params = {
    parentId: isRecycleView.value ? undefined : currentParentId.value,
    status,
    keyword: keyword.value || undefined,
    pageNo: pageNo.value,
    pageSize: pageSize.value
  }
  if (isRuleTab.value) {
    const res = await pageRules(params)
    records.value = res.records
    total.value = res.total
  } else {
    const res = await pageScorecards(params)
    records.value = res.records
    total.value = res.total
  }
}

function switchTab(v: 'rule' | 'scorecard') {
  if (tab.value === v) return
  tab.value = v
}

function switchRecycle() {
  isRecycleView.value = !isRecycleView.value
  currentParentId.value = undefined
  pageNo.value = 1
  reload()
}

function selectParent(id?: number) {
  isRecycleView.value = false
  currentParentId.value = id
  pageNo.value = 1
  reload()
}

function onPageChange(p: number) {
  pageNo.value = p
  loadList()
}

function tagText(r: RuleVO | ScorecardVO) {
  if (r.status === -1) return 'Deleted'
  if (r.status === 0) return 'Disabled'
  const rt = (r as RuleVO).ruleType
  if (isRuleTab.value && rt != null) return rt === 0 ? 'Hard Reject' : 'Score Adjust'
  return 'Enabled'
}

function tagClass(r: RuleVO | ScorecardVO) {
  if (r.status === -1) return 'is-danger'
  if (r.status === 0) return 'is-muted'
  const rt = (r as RuleVO).ruleType
  if (isRuleTab.value && rt === 0) return 'is-danger'
  return 'is-primary'
}

function openCreate() {
  editingId.value = undefined
  ruleInitial.value = { parentId: currentParentId.value }
  scorecardInitial.value = { parentId: currentParentId.value }
  editorVisible.value = true
}

async function openEdit(r: RuleVO | ScorecardVO) {
  if (isRecycleView.value) return
  editingId.value = r.id
  if (isRuleTab.value) {
    const detail = await getRule(r.id)
    ruleInitial.value = { ...detail }
  } else {
    const detail = await getScorecard(r.id)
    scorecardInitial.value = { ...detail }
  }
  editorVisible.value = true
}

async function handleSubmit() {
  if (isRuleTab.value) {
    const editor = ruleEditorRef.value
    if (!editor) return
    const err = editor.validate()
    if (err) {
      ElMessage.warning(err)
      return
    }
    submitting.value = true
    try {
      const payload = editor.getPayload()
      payload.engineId = undefined
      payload.organId = 1
      if (editingId.value) {
        await updateRule({ ...payload, id: editingId.value })
      } else {
        await createRule(payload)
      }
      ElMessage.success('Saved')
      editorVisible.value = false
      await reload()
    } finally {
      submitting.value = false
    }
  } else {
    const editor = scorecardEditorRef.value
    if (!editor) return
    const err = editor.validate()
    if (err) {
      ElMessage.warning(err)
      return
    }
    submitting.value = true
    try {
      const payload = editor.getPayload()
      payload.organId = 1
      if (editingId.value) {
        await updateScorecard({ ...payload, id: editingId.value })
      } else {
        await createScorecard(payload)
      }
      ElMessage.success('Saved')
      editorVisible.value = false
      await reload()
    } finally {
      submitting.value = false
    }
  }
}

async function handleCopy(r: RuleVO | ScorecardVO) {
  if (isRuleTab.value) await copyRule(r.id)
  else await copyScorecard(r.id)
  ElMessage.success('Copied; the duplicate is disabled')
  await reload()
}

async function handleToggle(r: RuleVO | ScorecardVO) {
  const next = r.status === 1 ? 0 : 1
  await changeRuleStatus([r.id], next)
  ElMessage.success(next === 1 ? 'Enabled' : 'Disabled')
  await loadList()
}

async function handleRecycle(r: RuleVO | ScorecardVO) {
  const label = isRuleTab.value ? 'Rule' : 'Scorecard'

  if (isRecycleView.value) {
    await ElMessageBox.confirm(`Permanently deleting cannot be undone. Delete "${r.name}"?`, 'Delete Permanently', {
      type: 'error',
      confirmButtonText: 'Delete Permanently',
      cancelButtonText: 'Cancel'
    })
    if (isRuleTab.value) await deleteRulesPermanently([r.id])
    else await deleteScorecardsPermanently([r.id])
    ElMessage.success('Deleted permanently')
    await reload()
    return
  }

  await ElMessageBox.confirm(`"${r.name}" will be moved to the Recycle Bin. Continue?`, `Delete ${label}`, {
    type: 'warning',
    confirmButtonText: 'Move to Recycle Bin',
    cancelButtonText: 'Cancel'
  })
  if (isRuleTab.value) await recycleRules([r.id])
  else await recycleScorecards([r.id])
  ElMessage.success('Moved to Recycle Bin')
  await reload()
}

async function openCreateDir(parentId?: number) {
  const { value } = await ElMessageBox.prompt('Enter folder name', 'New Folder', {
    confirmButtonText: 'Create',
    cancelButtonText: 'Cancel',
    inputPattern: /\S+/,
    inputErrorMessage: 'Name cannot be empty'
  })
  await createTreeNode({
    name: value,
    parentId,
    treeType: isRuleTab.value ? 0 : 1,
    organId: 1
  })
  ElMessage.success('Folder created')
  await reload()
}

async function openRename(node: TreeNode) {
  const { value } = await ElMessageBox.prompt('Enter new name', 'Rename', {
    confirmButtonText: 'Save',
    cancelButtonText: 'Cancel',
    inputValue: node.name,
    inputPattern: /\S+/,
    inputErrorMessage: 'Name cannot be empty'
  })
  await renameTreeNode(node.id, value)
  ElMessage.success('Renamed')
  await reload()
}

async function handleDeleteDir(node: TreeNode) {
  await ElMessageBox.confirm(`Delete folder "${node.name}"?`, 'Delete Folder', {
    type: 'warning',
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel'
  })
  await deleteTreeNode(node.id)
  ElMessage.success('Folder deleted')
  if (currentParentId.value === node.id) currentParentId.value = undefined
  await reload()
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.kb {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--c-bg);
  overflow: hidden;

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: var(--h-header);
    flex-shrink: 0;
    padding: 0 var(--sp-4);
    gap: var(--sp-4);
    background: var(--c-surface-raised);
    border-bottom: 1px solid var(--c-border);
  }

  &__lead {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__search {
    width: 220px;
  }

  &__body {
    flex: 1;
    display: flex;
    min-height: 0;
  }

  &__tree {
    width: 230px;
    flex-shrink: 0;
    padding: var(--sp-3);
    background: var(--c-surface-sunken);
    border-right: 1px solid var(--c-border);
    display: flex;
    flex-direction: column;
  }

  &__main {
    flex: 1;
    min-width: 0;
    padding: var(--sp-5);
    overflow-y: auto;
    position: relative;
  }

  &__loading {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    color: var(--c-text-tertiary);
  }

  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 6px;
    text-align: center;
  }

  &__empty-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 52px;
    height: 52px;
    border-radius: var(--r-lg);
    background: var(--c-fill-quaternary);
    color: var(--c-text-quaternary);
    margin-bottom: var(--sp-2);
  }

  &__empty-title {
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text-secondary);
  }

  &__empty-desc {
    font-size: var(--fs-sm);
    color: var(--c-text-tertiary);
  }
}


.seg-tabs {
  display: flex;
  background: var(--c-fill-quaternary);
  border-radius: var(--r-sm);
  padding: 2px;

  &__btn {
    height: 26px;
    padding: 0 var(--sp-4);
    border: none;
    background: transparent;
    color: var(--c-text-secondary);
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    border-radius: var(--r-xs);
    cursor: pointer;
    transition: all var(--dur-fast) var(--ease-standard);

    &:hover {
      color: var(--c-text);
    }

    &.is-on {
      background: var(--c-surface);
      color: var(--c-text);
      box-shadow: var(--sh-xs);
    }
  }
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text);
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
  cursor: pointer;
  box-shadow: var(--sh-xs);
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover:not(:disabled) {
    border-color: var(--c-border-strong);
    box-shadow: var(--sh-sm);
  }

  &:active:not(:disabled) {
    transform: scale(0.97);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &--primary {
    background: var(--c-primary);
    border-color: transparent;
    color: var(--c-text-inverse);
    box-shadow: var(--sh-xs), var(--sh-inset);

    &:hover:not(:disabled) {
      background: var(--c-primary-hover);
      box-shadow: var(--sh-sm), var(--sh-inset);
    }
  }
}

.root-drop {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin: var(--sp-2) 0 0;
  padding: 10px;
  border: 1.5px dashed var(--c-border-strong);
  border-radius: var(--r-sm);
  color: var(--c-text-tertiary);
  font-size: var(--fs-xs);

  &.is-drop {
    border-color: var(--c-primary);
    color: var(--c-primary);
    background: var(--c-primary-soft);
  }
}

.tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sp-2) var(--sp-2);

  &__label {
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    color: var(--c-text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.06em;
  }

  &__add {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border: none;
    border-radius: var(--r-xs);
    background: transparent;
    color: var(--c-text-tertiary);
    cursor: pointer;
    transition: all var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-primary-soft);
      color: var(--c-primary);
    }
  }
}

.tree-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px var(--sp-2);
  border-radius: var(--r-sm);
  cursor: pointer;
  color: var(--c-text-secondary);
  font-size: var(--fs-sm);
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-fill-quaternary);
    color: var(--c-text);
  }

  &.is-on {
    background: var(--c-primary-soft);
    color: var(--c-primary);
    font-weight: var(--fw-medium);
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
}

.tree-foot {
  margin-top: auto;
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-separator);

  &__btn {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    padding: 6px var(--sp-2);
    border: none;
    border-radius: var(--r-sm);
    background: transparent;
    color: var(--c-text-secondary);
    font-size: var(--fs-sm);
    cursor: pointer;
    transition: all var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-fill-quaternary);
      color: var(--c-text);
    }

    &.is-on {
      background: var(--c-danger-soft);
      color: var(--c-danger);
    }
  }
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--sp-3);
}

.card {
  padding: var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  cursor: pointer;
  box-shadow: var(--sh-xs);
  transition: all var(--dur-normal) var(--ease-standard);

  &.is-draggable {
    cursor: grab;

    &:active {
      cursor: grabbing;
    }
  }


  &:hover {
    border-color: var(--c-border-strong);
    box-shadow: var(--sh-md);
    transform: translateY(-2px);
  }

  &.is-off {
    opacity: 0.6;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: 4px;
  }

  &__title {
    flex: 1;
    min-width: 0;
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    letter-spacing: var(--ls-snug);
  }

  &__tag {
    flex-shrink: 0;
    padding: 2px 7px;
    border-radius: var(--r-full);
    font-size: var(--fs-2xs);
    font-weight: var(--fw-medium);

    &.is-primary {
      background: var(--c-primary-soft);
      color: var(--c-primary);
    }

    &.is-danger {
      background: var(--c-danger-soft);
      color: var(--c-danger);
    }

    &.is-muted {
      background: var(--c-fill-tertiary);
      color: var(--c-text-tertiary);
    }
  }

  &__code {
    font-family: var(--font-mono);
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    margin-bottom: var(--sp-2);
  }

  &__desc {
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
    line-height: var(--lh-normal);
    min-height: 32px;
    margin-bottom: var(--sp-3);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-2);
    padding-top: var(--sp-3);
    border-top: 1px solid var(--c-separator);
  }

  &__dir {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    min-width: 0;
  }

  &__ops {
    display: flex;
    gap: 2px;
    flex-shrink: 0;
  }
}

.op {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
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

.pager {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-6);
}

.drawer-foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}

.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);

  &__code {
    font-family: var(--font-mono);
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }
}
</style>
