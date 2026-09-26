<template>
  <div class="db">
    <header class="db__top">
      <div class="db__lead">
        <div class="seg-tabs">
          <button
            class="seg-tabs__btn"
            :class="{ 'is-on': tab === 'black' }"
            @click="switchTab('black')"
          >
            Blacklist
          </button>
          <button
            class="seg-tabs__btn"
            :class="{ 'is-on': tab === 'white' }"
            @click="switchTab('white')"
          >
            Whitelist
          </button>
        </div>
      </div>
      <div class="db__actions">
        <el-input
          v-model="keyword"
          placeholder="Search list DBs by name or attribute"
          clearable
          class="db__search"
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <button class="btn" :class="{ 'btn--danger': isRecycle }" @click="switchRecycle">
          <el-icon><Delete /></el-icon><span>Recycle Bin</span>
        </button>
        <button class="btn btn--primary" @click="openCreate">
          <el-icon><Plus /></el-icon><span>New {{ isWhite ? 'Whitelist' : 'Blacklist' }}</span>
        </button>
      </div>
    </header>

    <main class="db__main">
      <div v-if="!records.length" class="db__empty">
        <div class="db__empty-icon"><el-icon :size="24"><Files /></el-icon></div>
        <div class="db__empty-title">{{ isRecycle ? 'Recycle Bin is empty' : 'No list DB yet' }}</div>
        <div class="db__empty-desc">
          {{ isRecycle ? 'Deleted list DBs will appear here' : 'Click "New" at the top right to create one' }}
        </div>
      </div>

      <div v-else class="cards">
        <article v-for="r in records" :key="r.id" class="card" :class="{ 'is-off': r.status === 0 }">
          <div class="card__head">
            <div class="card__title u-truncate" :title="r.listName">{{ r.listName }}</div>
            <span class="card__tag" :class="r.status === 1 ? 'is-primary' : 'is-muted'">
              {{ r.status === 1 ? 'Active' : r.status === 0 ? 'Disabled' : 'Deleted' }}
            </span>
          </div>

          <div class="card__attr u-truncate">{{ r.listAttr || 'No type attribute' }}</div>

          <div class="card__desc">{{ r.listDesc || 'No description' }}</div>

          <dl class="meta">
            <div class="meta__row">
              <dt>Match Fields</dt>
              <dd class="u-truncate">{{ fieldNames(r.tableColumn) }}</dd>
            </div>
            <div class="meta__row">
              <dt>Match Type</dt>
              <dd>{{ r.matchType === 1 ? 'Exact' : 'Fuzzy' }}</dd>
            </div>
            <div class="meta__row">
              <dt>Multi-field Logic</dt>
              <dd>{{ r.queryType === 1 ? 'AND' : 'OR' }}</dd>
            </div>
            <div class="meta__row">
              <dt>Data Source</dt>
              <dd>{{ sourceName(r.dataSource) }}</dd>
            </div>
          </dl>

          <div class="card__foot">
            <div class="card__ops">
              <button v-if="!isRecycle" class="op" title="List Entries" @click="openEntries(r)">
                <el-icon :size="13"><Tickets /></el-icon>
              </button>
              <button v-if="!isRecycle" class="op" title="Edit" @click="openEdit(r)">
                <el-icon :size="13"><EditPen /></el-icon>
              </button>
              <button v-if="!isRecycle" class="op" title="Copy" @click="handleCopy(r)">
                <el-icon :size="13"><CopyDocument /></el-icon>
              </button>
              <button v-if="!isRecycle" class="op" :title="r.status === 1 ? 'Disable' : 'Enable'" @click="handleToggle(r)">
                <el-icon :size="13">
                  <component :is="r.status === 1 ? 'VideoPause' : 'VideoPlay'" />
                </el-icon>
              </button>
              <button class="op op--danger" :title="isRecycle ? 'Delete Permanently' : 'Move to Recycle Bin'" @click="handleDelete(r)">
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
    </main>

    <el-drawer
      v-model="editorVisible"
      :title="editingId ? 'Edit List DB' : 'New List DB'"
      size="600px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="form">
        <div class="sect">
          <div class="sect__label">Basic Info</div>
          <div class="field">
            <label class="field__label">List DB Name <i>*</i></label>
            <el-input v-model="form.listName" :placeholder="isWhite ? 'e.g. Premium customer whitelist' : 'e.g. Bank micro-loan blacklist'" />
          </div>
          <div class="field">
            <label class="field__label">Type Attribute</label>
            <el-input v-model="form.listAttr" placeholder="e.g. Internal blacklist / Past-due customers" />
          </div>
          <div class="field">
            <label class="field__label">Description</label>
            <el-input v-model="form.listDesc" type="textarea" :rows="2" resize="none" />
          </div>
        </div>

        <div class="sect">
          <div class="sect__label">Match Config</div>
          <div class="field">
            <label class="field__label">Match Fields <i>*</i></label>
            <el-select
              v-model="selectedFields"
              multiple
              filterable
              placeholder="Select fields"
              style="width: 100%"
            >
              <el-option
                v-for="f in fields"
                :key="f.id"
                :label="`${f.fieldCn} (${f.fieldEn})`"
                :value="f.id"
              />
            </el-select>
            <div class="hint">List hits are determined by the values of these fields</div>
          </div>
          <div class="grid">
            <div class="field">
              <label class="field__label">Match Type</label>
              <el-select v-model="form.matchType" style="width: 100%">
                <el-option :value="1" label="Exact Match" />
                <el-option :value="0" label="Fuzzy Match" />
              </el-select>
            </div>
            <div class="field">
              <label class="field__label">Multi-field Logic</label>
              <el-select v-model="form.queryType" style="width: 100%">
                <el-option :value="1" label="AND (all hit)" />
                <el-option :value="0" label="OR (any hit)" />
              </el-select>
            </div>
          </div>
          <div class="field">
            <label class="field__label">Data Source</label>
            <el-select v-model="form.dataSource" style="width: 100%">
              <el-option :value="2" label="Internal List" />
              <el-option :value="1" label="External List" />
              <el-option :value="0" label="TBD" />
            </el-select>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="drawer-foot">
          <button class="btn" @click="editorVisible = false">Cancel</button>
          <button class="btn btn--primary" :disabled="submitting" @click="handleSubmit">
            {{ submitting ? 'Saving…' : 'Save' }}
          </button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="entriesVisible"
      :title="`List Entries: ${currentList?.listName || ''}`"
      size="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="entries-bar">
        <el-input
          v-model="entryKeyword"
          placeholder="Search by value / remark"
          clearable
          style="width: 200px"
          @keyup.enter="reloadEntries()"
          @clear="reloadEntries()"
        />
        <el-select v-model="entryStatus" style="width: 100px" @change="reloadEntries()">
          <el-option :value="undefined" label="All" />
          <el-option :value="1" label="Enabled" />
          <el-option :value="0" label="Disabled" />
        </el-select>
        <div style="flex: 1" />
        <el-button type="primary" plain size="small" @click="addVisible = true">Batch Add</el-button>
      </div>

      <el-table :data="entries" v-loading="entriesLoading" size="small" border>
        <el-table-column prop="entryValue" label="Entry Value" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="Remark" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="Validity" width="190">
          <template #default="{ row }">
            <span v-if="!row.effectiveFrom && !row.effectiveTo" class="muted">Long-term</span>
            <span v-else class="muted">
              {{ (row.effectiveFrom || '…').slice(0, 10) }} ~ {{ (row.effectiveTo || '…').slice(0, 10) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? 'Enabled' : 'Disabled' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="Created At" width="160" />
        <el-table-column label="Actions" width="110" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="toggleEntry(row)">
              {{ row.status === 1 ? 'Disable' : 'Enable' }}
            </el-button>
            <el-button size="small" link type="danger" @click="deleteEntry(row)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="entriesTotal > entryPageSize" class="pager" style="margin-top: 10px">
        <el-pagination
          :current-page="entryPageNo"
          :page-size="entryPageSize"
          :total="entriesTotal"
          layout="prev, pager, next"
          background
          @current-change="onEntryPageChange"
        />
      </div>

      <el-dialog v-model="addVisible" title="Batch Add Entries" width="480px" append-to-body>
        <el-form label-width="70px" size="small">
          <el-form-item label="Entries" required>
            <el-input
              v-model="addForm.values"
              type="textarea"
              :rows="6"
              placeholder="One value per line; comma/semicolon separated also supported. Duplicates are skipped automatically"
            />
          </el-form-item>
          <el-form-item label="Remark">
            <el-input v-model="addForm.remark" placeholder="e.g. Reason for blacklisting" />
          </el-form-item>
          <el-form-item label="Validity">
            <el-date-picker
              v-model="addForm.range"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              start-placeholder="Valid from (optional)"
              end-placeholder="Valid to (optional)"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="adding" @click="handleAddEntries">Add</el-button>
        </template>
      </el-dialog>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFields, type FieldVO } from '@/api/knowledge'
import {
  pageListDbs, createListDb, updateListDb, copyListDb, changeListDbStatus,
  recycleListDbs, deleteListDbsPermanently,
  pageListEntries, addListEntries, setListEntryStatus, removeListEntries,
  type ListDb, type ListEntry
} from '@/api/datamanage'
const tab = ref<'black' | 'white'>('black')
const isRecycle = ref(false)
const keyword = ref('')
const records = ref<ListDb[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(12)

const editorVisible = ref(false)
const editingId = ref<number>()
const submitting = ref(false)

const fields = ref<FieldVO[]>([])
const selectedFields = ref<number[]>([])

const form = reactive<Partial<ListDb>>({
  listName: '',
  listAttr: '',
  listDesc: '',
  matchType: 1,
  queryType: 1,
  dataSource: 2
})

const isWhite = computed(() => tab.value === 'white')

onMounted(async () => {
  fields.value = await listFields()
  await reload()
})

async function reload() {
  const res = await pageListDbs({
    listType: isWhite.value ? 'w' : 'b',
    status: isRecycle.value ? -1 : undefined,
    keyword: keyword.value || undefined,
    pageNo: pageNo.value,
    pageSize: pageSize.value
  })
  records.value = res.records
  total.value = res.total
}

function switchTab(t: 'black' | 'white') {
  if (tab.value === t && !isRecycle.value) return
  tab.value = t
  isRecycle.value = false
  pageNo.value = 1
  reload()
}

function switchRecycle() {
  isRecycle.value = !isRecycle.value
  pageNo.value = 1
  reload()
}

function onPageChange(p: number) {
  pageNo.value = p
  reload()
}

function fieldNames(ids?: string) {
  if (!ids) return '—'
  const fieldMap = new Map(fields.value.map((f) => [String(f.id), f.fieldCn]))
  return ids
    .split(',')
    .map((id) => fieldMap.get(id.trim()) || `#${id.trim()}`)
    .join(', ')
}

function sourceName(v?: number) {
  return v === 2 ? 'Internal List' : v === 1 ? 'External List' : 'TBD'
}

function openCreate() {
  editingId.value = undefined
  Object.assign(form, {
    listName: '',
    listAttr: '',
    listDesc: '',
    matchType: 1,
    queryType: 1,
    dataSource: 2,
    tableColumn: '',
    queryField: ''
  })
  selectedFields.value = []
  editorVisible.value = true
}

function openEdit(r: ListDb) {
  editingId.value = r.id
  Object.assign(form, r)
  selectedFields.value = (r.tableColumn || '')
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => !isNaN(n))
  editorVisible.value = true
}

async function handleSubmit() {
  if (!form.listName?.trim()) {
    ElMessage.warning('List DB name is required')
    return
  }
  if (!selectedFields.value.length) {
    ElMessage.warning('Select at least one match field')
    return
  }
  const csv = selectedFields.value.join(',')
  const payload: Partial<ListDb> = {
    ...form,
    listType: isWhite.value ? 'w' : 'b',
    tableColumn: csv,
    queryField: csv,
    organId: 1
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await updateListDb({ ...payload, id: editingId.value })
    } else {
      await createListDb(payload)
    }
    ElMessage.success('Saved')
    editorVisible.value = false
    await reload()
  } finally {
    submitting.value = false
  }
}

async function handleCopy(r: ListDb) {
  await copyListDb(r.id)
  ElMessage.success('Copied; the duplicate is disabled')
  await reload()
}

async function handleToggle(r: ListDb) {
  const next = r.status === 1 ? 0 : 1
  await changeListDbStatus([r.id], next)
  ElMessage.success(next === 1 ? 'Enabled' : 'Disabled')
  await reload()
}

async function handleDelete(r: ListDb) {
  if (isRecycle.value) {
    await ElMessageBox.confirm(`Permanently deleting cannot be undone. Delete "${r.listName}"?`, 'Delete Permanently', {
      type: 'error',
      confirmButtonText: 'Delete Permanently',
      cancelButtonText: 'Cancel'
    })
    await deleteListDbsPermanently([r.id])
    ElMessage.success('Deleted permanently')
  } else {
    await ElMessageBox.confirm(`"${r.listName}" will be moved to the Recycle Bin. Continue?`, 'Delete List DB', {
      type: 'warning',
      confirmButtonText: 'Move to Recycle Bin',
      cancelButtonText: 'Cancel'
    })
    await recycleListDbs([r.id])
    ElMessage.success('Moved to Recycle Bin')
  }
  await reload()
}

const entriesVisible = ref(false)
const entriesLoading = ref(false)
const currentList = ref<ListDb | null>(null)
const entries = ref<ListEntry[]>([])
const entriesTotal = ref(0)
const entryPageNo = ref(1)
const entryPageSize = 10
const entryKeyword = ref('')
const entryStatus = ref<number | undefined>(undefined)
const addVisible = ref(false)
const adding = ref(false)
const addForm = reactive({ values: '', remark: '', range: null as [string, string] | null })

function openEntries(r: ListDb) {
  currentList.value = r
  entryPageNo.value = 1
  entryKeyword.value = ''
  entryStatus.value = undefined
  entriesVisible.value = true
  reloadEntries()
}

async function reloadEntries() {
  if (!currentList.value) return
  entriesLoading.value = true
  try {
    const res = await pageListEntries(currentList.value.id, {
      keyword: entryKeyword.value || undefined,
      status: entryStatus.value,
      pageNo: entryPageNo.value,
      pageSize: entryPageSize
    })
    entries.value = res.records
    entriesTotal.value = res.total
  } finally {
    entriesLoading.value = false
  }
}

function onEntryPageChange(p: number) {
  entryPageNo.value = p
  reloadEntries()
}

async function handleAddEntries() {
  if (!currentList.value) return
  if (!addForm.values.trim()) {
    ElMessage.warning('Please enter entry values')
    return
  }
  adding.value = true
  try {
    const n = await addListEntries(currentList.value.id, {
      values: addForm.values,
      remark: addForm.remark || undefined,
      effectiveFrom: addForm.range?.[0],
      effectiveTo: addForm.range?.[1]
    })
    ElMessage.success(`Added ${n} entries${addForm.values.split(/[\n,;\s]+/).filter(Boolean).length - n > 0 ? ' (duplicates skipped)' : ''}`)
    addVisible.value = false
    addForm.values = ''
    addForm.remark = ''
    addForm.range = null
    reloadEntries()
  } finally {
    adding.value = false
  }
}

async function toggleEntry(row: ListEntry) {
  if (!currentList.value) return
  await setListEntryStatus(currentList.value.id, [row.id], row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? 'Disabled' : 'Enabled')
  reloadEntries()
}

async function deleteEntry(row: ListEntry) {
  if (!currentList.value) return
  await ElMessageBox.confirm(`Delete entry "${row.entryValue}"?`, 'Delete', { type: 'warning' })
  await removeListEntries(currentList.value.id, [row.id])
  ElMessage.success('Deleted')
  reloadEntries()
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.db {
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

  &__main {
    flex: 1;
    overflow-y: auto;
    padding: var(--sp-5);
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

  &--danger {
    background: var(--c-danger-soft);
    border-color: transparent;
    color: var(--c-danger);
  }
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--sp-3);
}

.card {
  padding: var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  box-shadow: var(--sh-xs);
  transition: all var(--dur-normal) var(--ease-standard);

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

    &.is-muted {
      background: var(--c-fill-tertiary);
      color: var(--c-text-tertiary);
    }
  }

  &__attr {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    margin-bottom: 6px;
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
    padding-top: var(--sp-2);
    border-top: 1px solid var(--c-separator);
  }

  &__ops {
    display: flex;
    gap: 2px;
    justify-content: flex-end;
  }
}

.meta {
  margin: 0 0 var(--sp-3);
  padding: var(--sp-3);
  border-radius: var(--r-sm);
  background: var(--c-surface-sunken);

  &__row {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    font-size: var(--fs-2xs);
    line-height: 1.7;

    dt {
      width: 68px;
      flex-shrink: 0;
      color: var(--c-text-tertiary);
    }

    dd {
      margin: 0;
      flex: 1;
      min-width: 0;
      color: var(--c-text-secondary);
    }
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

.sect {
  margin-bottom: var(--sp-5);

  &__label {
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    color: var(--c-text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    margin-bottom: var(--sp-3);
  }
}

.field {
  margin-bottom: var(--sp-3);

  &__label {
    display: block;
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text-secondary);
    margin-bottom: 6px;

    i {
      color: var(--c-danger);
      font-style: normal;
    }
  }
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
}

.hint {
  font-size: var(--fs-2xs);
  color: var(--c-text-tertiary);
  margin-top: 6px;
}

.drawer-foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}

.entries-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
