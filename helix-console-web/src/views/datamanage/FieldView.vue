<template>
  <div class="kb">
    <header class="kb__top">
      <div class="kb__lead">
        <span class="kb__title">Field Management</span>
        <span class="kb__desc">Contract for decision input parameters — trial form, batch columns, and rule values all align to it</span>
      </div>
      <div class="kb__actions">
        <el-input
          v-model="keyword"
          placeholder="Search by field EN / CN name"
          clearable
          class="kb__search"
          @input="reload(true)"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <span v-if="selectedCat && !isRecycleView" class="kb__scope">
          Category: <b>{{ selectedCat.name }}</b>
          <button class="kb__scope-clear" @click="clearCat">View All</button>
        </span>
        <template v-if="!isRecycleView">
          <button class="btn" @click="downloadFieldTemplate">
            <el-icon><Download /></el-icon>Download Template
          </button>
          <button class="btn" @click="importOpen = true">
            <el-icon><Upload /></el-icon>Batch Import
          </button>
          <button class="btn btn--primary" @click="openCreate">
            <el-icon><Plus /></el-icon>Add Field
          </button>
        </template>
        <template v-else>
          <button class="btn" @click="switchRecycle">
            <el-icon><Top /></el-icon>Back to Folders
          </button>
        </template>
      </div>
    </header>

    <div class="kb__body">
      <aside class="kb__tree u-scroll-y">
        <div class="tree-head">
          <span class="tree-head__label">{{ isRecycleView ? 'Recycle Bin' : 'Folders' }}</span>
          <button v-if="!isRecycleView" class="tree-head__add" title="New top-level category" @click="openCatDialog(null)">
            <el-icon :size="13"><Plus /></el-icon>
          </button>
        </div>

        <div
          v-if="!isRecycleView"
          class="tree-item"
          :class="{ 'is-on': !selectedCat }"
          @click="selectCat(undefined)"
        >
          <el-icon :size="13"><Files /></el-icon>
          <span class="tree-item__text">All</span>
          <span class="tree-item__count">{{ total }}</span>
        </div>

        <div class="tn">
          <TreeNodeItem
            v-for="node in catalogTree"
            :key="node.id"
            :node="node"
            :current="selectedCat?.id"
            :drag-id="activeDragId"
            :drag-kind="dragKind"
            @select="selectCat"
            @rename="renameCat"
            @remove="deleteCat"
            @add-child="onAddChild"
            @drag-start="onCatDragStart"
            @drag-end="onCatDragEnd"
            @move-node="onMoveCat"
          />
        </div>

        <div class="tree-foot">
          <button class="tree-foot__btn" :class="{ 'is-on': isRecycleView }" @click="switchRecycle">
            <el-icon :size="13"><Delete /></el-icon><span>Recycle Bin</span>
          </button>
        </div>

        <div
          v-if="!isRecycleView && (dragId || draggingField)"
          class="root-drop"
          :class="{ 'is-drop': rootDropHover }"
          @dragover.prevent="rootDropHover = true"
          @dragleave="rootDropHover = false"
        >
          <el-icon><Top /></el-icon>
          {{ draggingField ? 'Drop here to set as "Uncategorized"' : 'Drop here to move to top level' }}
        </div>
      </aside>

      <main class="kb__main">
        <el-table :data="rows" v-loading="loading" class="tbl">
          <el-table-column prop="fieldEn" label="EN Name" min-width="140">
            <template #default="{ row }">
              <code
                class="mono"
                :draggable="!isRecycleView"
                @dragstart="onRowDragStart($event, row)"
                @dragend="onRowDragEnd"
              >{{ row.fieldEn }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="fieldCn" label="CN Name" min-width="120" />
          <el-table-column label="Category" width="110">
            <template #default="{ row }">{{ row.catalogName || 'Uncategorized' }}</template>
          </el-table-column>
          <el-table-column label="Value Type" width="90">
            <template #default="{ row }">{{ valueTypeLabel(row.valueType) }}</template>
          </el-table-column>
          <el-table-column prop="valueScope" label="Value Scope" min-width="100">
            <template #default="{ row }">{{ row.valueScope || '—' }}</template>
          </el-table-column>
          <el-table-column label="Flags" width="140">
            <template #default="{ row }">
              <span v-if="row.isDerivative === 1" class="flag is-blue">Derived</span>
              <span v-if="row.isOutput === 1" class="flag is-green">Output</span>
              <span v-if="row.isDerivative !== 1 && row.isOutput !== 1" class="flag is-gray">Input</span>
            </template>
          </el-table-column>
          <el-table-column label="Status" width="90">
            <template #default="{ row }">
              <span v-if="isRecycleView || row.status === -1" class="flag is-gray">Recycle Bin</span>
              <span v-else class="flag is-green">Active</span>
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="!isRecycleView">
                <button class="op" @click="openEdit(row)">Edit</button>
                <button class="op is-danger" @click="onRecycle(row)">Delete</button>
              </template>
              <template v-else>
                <button class="op" @click="onRestore(row)">Restore</button>
                <button class="op is-danger" @click="onPermanentDelete(row)">Delete Permanently</button>
              </template>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="No fields in this scope yet — add a field or batch import" />
          </template>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="pageNo"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="reload(false)"
          />
        </div>
      </main>
    </div>

    <el-dialog v-model="dlgOpen" :title="editingId ? 'Edit Field' : 'Add Field'" width="460px">
      <el-form label-width="86px" label-position="left">
        <el-form-item label="EN Name" required>
          <el-input v-model="form.fieldEn" :disabled="editingId > 0" placeholder="e.g. f_CREDIT_SCORE (expression variable)" />
          <div class="hint">Starts with a letter; letters/digits/underscore only. Immutable after creation (rules reference it)</div>
        </el-form-item>
        <el-form-item label="CN Name" required>
          <el-input v-model="form.fieldCn" placeholder="e.g. Credit Score" maxlength="50" />
        </el-form-item>
        <el-form-item label="Category">
          <el-tree-select
            v-model="form.catalogId"
            :data="catalogSelectData"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            filterable
            default-expand-all
            placeholder="Select a category (empty = Uncategorized)"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="Value Type">
          <el-select v-model="form.valueType" style="width: 100%">
            <el-option :value="1" label="Numeric" />
            <el-option :value="2" label="String" />
            <el-option :value="3" label="Enum" />
            <el-option :value="4" label="Decimal" />
          </el-select>
        </el-form-item>
        <el-form-item label="Value Scope">
          <el-input v-model="form.valueScope" placeholder="e.g. 18-65 / Shanghai,Beijing (optional)" />
        </el-form-item>
        <el-form-item label="Flags">
          <el-checkbox v-model="form.isOutput">Output field (target of rule conclusion assignment)</el-checkbox>
          <el-checkbox v-model="form.isDerivative">Derived field</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgOpen = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="catDlgOpen" :title="catParent ? `Add sub-category (under ${catParent.name})` : catRenameId ? 'Rename Category' : 'New Top-level Category'" width="380px">
      <el-input v-model="catName" placeholder="Category name" maxlength="30" @keyup.enter="saveCat" />
      <template #footer>
        <el-button @click="catDlgOpen = false">Cancel</el-button>
        <el-button type="primary" :loading="catSaving" @click="saveCat">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importOpen" title="Batch Import Fields" width="500px">
      <div class="tip">
        The first CSV row is the header (<code>field_en,field_cn,value_type,is_output,catalog</code>).
        Not sure? Download the import template first. Import dedupes by EN name: existing rows are updated, new rows are added;
        the <code>catalog</code> column takes a category name (must match one on the left; empty = Uncategorized).
      </div>
      <div
        class="drop"
        :class="{ 'is-drag': drag }"
        @dragover.prevent="drag = true"
        @dragleave="drag = false"
        @drop.prevent="onDrop"
      >
        <el-icon :size="26"><UploadFilled /></el-icon>
        <div class="drop__hint">Drag or select a CSV file</div>
        <div class="drop__file" v-if="importFile">{{ importFile.name }}</div>
        <button class="btn btn--primary" @click="pickFile">Choose File</button>
        <input ref="fileInput" type="file" accept=".csv" style="display: none" @change="onPick" />
      </div>
      <div v-if="summary" class="summary">
        Import done: <b>{{ summary.inserted }}</b> added, <b>{{ summary.updated }}</b> updated, {{ summary.total }} rows in total
        <button class="link" @click="closeImport">Refresh list →</button>
      </div>
      <template #footer>
        <el-button @click="importOpen = false">Close</el-button>
        <el-button type="primary" :disabled="!importFile || importing" :loading="importing" @click="doImport">
          {{ importing ? 'Importing…' : 'Start Import' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download, Upload, Plus, UploadFilled, Delete, Top, Files } from '@element-plus/icons-vue'
import {
  pageFields,
  createField,
  updateField,
  moveFieldCatalog,
  getTree,
  createTreeNode,
  renameTreeNode,
  deleteTreeNode,
  moveTreeNode,
  recycleFields,
  restoreFields,
  deleteFieldsPermanently,
  type FieldVO,
  type TreeNode
} from '@/api/knowledge'
import { importFields, downloadFieldTemplate, type FieldImportSummary } from '@/api/batch'
import TreeNodeItem from '@/views/knowledge/TreeNodeItem.vue'


const FIELD_TREE_TYPE = 4

const rows = ref<FieldVO[]>([])
const keyword = ref('')
const pageNo = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const catalogTree = ref<TreeNode[]>([])
const selectedCat = ref<TreeNode | null>(null)
const dragId = ref<number | null>(null)
const rootDropHover = ref(false)
const draggingField = ref<FieldVO | null>(null)
const fieldDropCat = ref<number | null>(null)

const isRecycleView = ref(false)
function switchRecycle() {
  isRecycleView.value = !isRecycleView.value
  selectedCat.value = null
  reload(true)
}

const dragKind = ref<'dir' | 'rule' | 'field'>('dir')
const activeDragId = computed<number | null>(() => dragId.value ?? draggingField.value?.id ?? null)

function onRowDragStart(e: DragEvent, row: FieldVO) {
  draggingField.value = row
  dragKind.value = 'field'
  e.dataTransfer?.setData('text/plain', String(row.id))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}
function onRowDragEnd() {
  draggingField.value = null
  fieldDropCat.value = null
  dragKind.value = 'dir'
}

function onCatDragStart(node: TreeNode) {
  dragId.value = node.id
  dragKind.value = 'dir'
}
function onCatDragEnd() {
  dragId.value = null
  dragKind.value = 'dir'
}
async function onMoveCat(payload: { id: number; parentId: number }) {
  const { id, parentId } = payload
  const node = findNode(catalogTree.value, id)
  if (node && node.parentId === parentId) return
  try {
    await moveTreeNode(id, parentId)
    ElMessage.success(parentId === 0 ? 'Moved to top level' : 'Category moved')
  } catch {
    ElMessage.error('Move failed')
  }
  await loadCatalog()
}

function findNode(ns: TreeNode[], id: number): TreeNode | undefined {
  for (const n of ns) {
    if (n.id === id) return n
    const r = findNode(n.children || [], id)
    if (r) return r
  }
  return undefined
}

function onAddChild(id: number) {
  const node = findNode(catalogTree.value, id)
  if (node) openCatDialog(node)
}
function selectCat(id?: number | undefined) {
  selectedCat.value = id == null
    ? null
    : (findNode(catalogTree.value, id) ?? ({ id, name: '' } as unknown as TreeNode))
  reload(true)
}

function onWinDropForField(e: DragEvent) {
  if (!draggingField.value) return
  const t = e.target as HTMLElement | null
  const catEl = t?.closest('[data-node-id]') as HTMLElement | null
  const rootEl = t?.closest('.root-drop')
  if (!catEl && !rootEl) return
  e.preventDefault()
  e.stopPropagation()
  const field = draggingField.value
  draggingField.value = null
  const targetCat = catEl ? Number(catEl.dataset.nodeId) : null
  fieldDropCat.value = null
  if (targetCat === (field.catalogId ?? null)) return
  moveFieldCatalog(field.id, targetCat ?? undefined)
    .then(() => {
      ElMessage.success(targetCat == null ? 'Moved to Uncategorized' : `Moved to "${catNameById(targetCat)}"`)
    })
    .catch(() => ElMessage.error('Move failed'))
    .finally(() => reload(false))
}

function catNameById(id: number): string {
  const find = (ns: TreeNode[]): string | undefined => {
    for (const n of ns) {
      if (n.id === id) return n.name
      const r = find(n.children || [])
      if (r) return r
    }
  }
  return find(catalogTree.value) || String(id)
}

watch(draggingField, (v) => {
  if (v) window.addEventListener('drop', onWinDropForField, true)
  else window.removeEventListener('drop', onWinDropForField, true)
})
onBeforeUnmount(() => {
  window.removeEventListener('drop', onWinDropForField, true)
  window.removeEventListener('drop', onWinDrop, true)
})

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
  await loadCatalog()
}

const dlgOpen = ref(false)
const editingId = ref(0)
const saving = ref(false)
const form = reactive({
  fieldEn: '',
  fieldCn: '',
  catalogId: undefined as number | undefined,
  valueType: 1,
  valueScope: '',
  isOutput: false,
  isDerivative: false
})

const catDlgOpen = ref(false)
const catSaving = ref(false)
const catParent = ref<TreeNode | null>(null)
const catRenameId = ref(0)
const catName = ref('')

const importOpen = ref(false)
const importFile = ref<File>()
const importing = ref(false)
const drag = ref(false)
const fileInput = ref<HTMLInputElement>()
const summary = ref<FieldImportSummary>()

function valueTypeLabel(v?: number) {
  return v === 1 ? 'Numeric' : v === 2 ? 'String' : v === 3 ? 'Enum' : v === 4 ? 'Decimal' : 'TBD'
}

const catalogSelectData = computed(() => catalogTree.value)

async function loadCatalog() {
  catalogTree.value = (await getTree(FIELD_TREE_TYPE)) || []
}

async function reload(reset: boolean) {
  if (reset) pageNo.value = 1
  loading.value = true
  try {
    const res = await pageFields({
      keyword: keyword.value || undefined,
      catalogId: isRecycleView.value ? undefined : selectedCat.value?.id,
      recycle: isRecycleView.value ? true : undefined,
      pageNo: pageNo.value,
      pageSize
    })
    rows.value = res.records
    total.value = res.total
    await loadCatalog()
  } finally {
    loading.value = false
  }
}


function clearCat() {
  selectedCat.value = null
  reload(true)
}


function openCreate() {
  editingId.value = 0
  form.fieldEn = ''
  form.fieldCn = ''
  form.catalogId = selectedCat.value?.id
  form.valueType = 1
  form.valueScope = ''
  form.isOutput = false
  form.isDerivative = false
  dlgOpen.value = true
}

function openEdit(row: FieldVO) {
  editingId.value = row.id
  form.fieldEn = row.fieldEn
  form.fieldCn = row.fieldCn
  form.catalogId = row.catalogId
  form.valueType = row.valueType ?? 1
  form.valueScope = row.valueScope ?? ''
  form.isOutput = row.isOutput === 1
  form.isDerivative = row.isDerivative === 1
  dlgOpen.value = true
}

async function onSave() {
  if (!form.fieldEn.trim() || !form.fieldCn.trim()) {
    ElMessage.warning('EN name and CN name are both required')
    return
  }
  saving.value = true
  try {
    const payload = {
      fieldEn: form.fieldEn.trim(),
      fieldCn: form.fieldCn.trim(),
      catalogId: form.catalogId,
      valueType: form.valueType,
      valueScope: form.valueScope.trim() || undefined,
      isOutput: form.isOutput ? 1 : 0,
      isDerivative: form.isDerivative ? 1 : 0
    }
    if (editingId.value) {
      await updateField(editingId.value, payload)
      ElMessage.success('Field updated')
    } else {
      await createField(payload)
      ElMessage.success('Field created')
    }
    dlgOpen.value = false
    await reload(false)
  } finally {
    saving.value = false
  }
}


async function onRecycle(row: FieldVO) {
  try {
    await ElMessageBox.confirm(
      `Move "${row.fieldCn} (${row.fieldEn})" to the Recycle Bin? It can be restored there; only permanent deletion from the Recycle Bin removes it physically.`,
      'Move to Recycle Bin',
      { type: 'warning', confirmButtonText: 'Move to Recycle Bin', cancelButtonText: 'Cancel' }
    )
  } catch {
    return
  }
  await recycleFields([row.id])
  ElMessage.success('Moved to Recycle Bin')
  await reload(false)
}

async function onRestore(row: FieldVO) {
  await restoreFields([row.id])
  ElMessage.success('Restored')
  await reload(false)
}

async function onPermanentDelete(row: FieldVO) {
  try {
    await ElMessageBox.confirm(
      `After permanently deleting "${row.fieldCn} (${row.fieldEn})", rule/scorecard expressions referencing it will get no value (fail-close). This cannot be undone!`,
      'Delete Permanently',
      { type: 'warning', confirmButtonText: 'Delete Permanently', cancelButtonText: 'Cancel' }
    )
  } catch {
    return
  }
  await deleteFieldsPermanently([row.id])
  ElMessage.success('Deleted permanently')
  await reload(false)
}


function openCatDialog(parent: TreeNode | null) {
  catParent.value = parent
  catRenameId.value = 0
  catName.value = ''
  catDlgOpen.value = true
}

function renameCat(data: TreeNode) {
  catParent.value = null
  catRenameId.value = data.id
  catName.value = data.name
  catDlgOpen.value = true
}

async function saveCat() {
  const name = catName.value.trim()
  if (!name) {
    ElMessage.warning('Please enter a category name')
    return
  }
  catSaving.value = true
  try {
    if (catRenameId.value) {
      await renameTreeNode(catRenameId.value, name)
      ElMessage.success('Renamed')
    } else {
      await createTreeNode({ name, parentId: catParent.value?.id, treeType: FIELD_TREE_TYPE })
      ElMessage.success('Category created')
    }
    catDlgOpen.value = false
    await loadCatalog()
  } finally {
    catSaving.value = false
  }
}

async function deleteCat(data: TreeNode) {
  try {
    await ElMessageBox.confirm(`Delete category "${data.name}"? Deletion fails if it contains fields or sub-categories.`, 'Delete Category', {
      type: 'warning',
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel'
    })
  } catch {
    return
  }
  await deleteTreeNode(data.id)
  if (selectedCat.value?.id === data.id) {
    selectedCat.value = null
  }
  ElMessage.success('Deleted')
  await reload(true)
}


function onDrop(e: DragEvent) {
  drag.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) importFile.value = f
}

function pickFile() {
  fileInput.value?.click()
}

function onPick(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) importFile.value = f
}

async function doImport() {
  if (!importFile.value) return
  importing.value = true
  try {
    summary.value = await importFields(importFile.value)
    ElMessage.success('Import completed')
    await reload(true)
  } finally {
    importing.value = false
  }
}

function closeImport() {
  importOpen.value = false
  importFile.value = undefined
  summary.value = undefined
}

onMounted(() => reload(true))
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
    align-items: baseline;
    gap: var(--sp-3);
    min-width: 0;
  }

  &__title {
    font-size: 15px;
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    white-space: nowrap;
  }

  &__desc {
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    flex-shrink: 0;
  }

  &__search {
    width: 220px;
  }

  &__scope {
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
    white-space: nowrap;

    b {
      color: var(--c-primary);
    }
  }

  &__scope-clear {
    margin-left: 6px;
    border: none;
    background: transparent;
    color: var(--c-text-tertiary);
    font-size: var(--fs-2xs);
    cursor: pointer;

    &:hover {
      color: var(--c-primary);
      text-decoration: underline;
    }
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
}

.root-drop {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin-top: var(--sp-2);
  padding: 9px;
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

.btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 var(--sp-3);
  border-radius: var(--r-sm);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text-secondary);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    border-color: var(--c-primary);
    color: var(--c-primary);
  }

  &--primary {
    background: var(--c-primary);
    border-color: transparent;
    color: var(--c-text-inverse);

    &:hover {
      background: var(--c-primary-hover);
      color: var(--c-text-inverse);
    }
  }
}

.tbl {
  width: 100%;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-3);
}

.mono {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
}

.flag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: var(--fs-2xs);
  margin-right: 4px;

  &.is-blue {
    background: #eaf4ff;
    color: #409eff;
  }

  &.is-green {
    background: #e8f7ee;
    color: #34a46f;
  }

  &.is-gray {
    background: var(--c-surface-sunken);
    color: var(--c-text-tertiary);
  }
}

.op {
  border: none;
  background: transparent;
  color: var(--c-primary);
  font-size: var(--fs-xs);
  cursor: pointer;
  padding: 0 var(--sp-2);

  &.is-danger {
    color: var(--c-danger);
  }

  &:hover {
    text-decoration: underline;
  }
}

.hint {
  font-size: var(--fs-2xs);
  color: var(--c-text-tertiary);
  line-height: 1.5;
  margin-top: 4px;
  width: 100%;
}

.tip {
  font-size: var(--fs-xs);
  color: var(--c-text-secondary);
  line-height: 1.7;
  margin-bottom: var(--sp-3);

  code {
    font-family: var(--font-mono);
    background: var(--c-surface-sunken);
    padding: 1px 6px;
    border-radius: 4px;
    font-size: var(--fs-2xs);
  }
}

.drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: var(--sp-5);
  border: 1.5px dashed var(--c-border-strong);
  border-radius: var(--r-md);
  background: var(--c-surface);
  transition: border-color var(--dur-fast) var(--ease-standard);

  &.is-drag {
    border-color: var(--c-primary);
    background: var(--c-primary-soft, #eaf4ff);
  }

  &__hint {
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
  }

  &__file {
    font-size: var(--fs-xs);
    color: var(--c-primary);
    font-weight: var(--fw-medium);
  }
}

.summary {
  margin-top: var(--sp-3);
  padding: var(--sp-3);
  border-radius: var(--r-md);
  background: var(--c-surface-sunken);
  font-size: var(--fs-xs);
  color: var(--c-text-secondary);

  b {
    color: var(--c-success);
  }

  .link {
    margin-left: var(--sp-2);
    border: none;
    background: transparent;
    color: var(--c-primary);
    cursor: pointer;
    font-size: var(--fs-xs);

    &:hover {
      text-decoration: underline;
    }
  }
}

.tree-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sp-1) var(--sp-2);

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

.tn {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 0;
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
</style>
