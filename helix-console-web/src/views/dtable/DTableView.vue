<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="Search by code / name"
          clearable
          style="width: 240px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button type="primary" @click="load">Search</el-button>
        <div class="toolbar__spacer" />
        <el-button type="primary" plain @click="openCreate">New Decision Table</el-button>
      </div>

      <el-table :data="tables" v-loading="loading" border size="small">
        <el-table-column prop="code" label="Code" width="200" />
        <el-table-column prop="name" label="Name" min-width="180" />
        <el-table-column label="Hit Policy" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.hitPolicy === 'ALL' ? 'warning' : 'primary'">
              {{ row.hitPolicy }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? 'Enabled' : 'Draft' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="Updated At" width="170" />
        <el-table-column label="Actions" width="160" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openEdit(row.id)">Edit</el-button>
            <el-popconfirm title="Referencing decision nodes will fall back to inline config after deletion. Confirm?" width="250" @confirm="onDelete(row.id)">
              <template #reference>
                <el-button size="small" link type="danger">Delete</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="editorVisible"
      :title="form.id ? `Edit Decision Table: ${form.name}` : 'New Decision Table'"
      width="960px"
      top="4vh"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="80px" size="small">
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="Code" required>
              <el-input v-model="form.code" placeholder="DT_XXX" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Name" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Hit Policy">
              <el-select v-model="form.hitPolicy" style="width: 100%">
                <el-option label="FIRST (first hit row)" value="FIRST" />
                <el-option label="ALL (all hit rows)" value="ALL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Status">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="Enabled" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="section-head">
        <span>Condition Columns (combined with AND from left to right)</span>
        <el-button size="small" @click="addColumn">Add Condition Column</el-button>
      </div>
      <el-table :data="columns" size="small" border>
        <el-table-column label="Title" width="160">
          <template #default="{ row }">
            <el-input v-model="row.title" size="small" placeholder="e.g. Age" />
          </template>
        </el-table-column>
        <el-table-column label="Field Code" width="200">
          <template #default="{ row }">
            <el-input v-model="row.fieldCode" size="small" placeholder="f_AGE" />
          </template>
        </el-table-column>
        <el-table-column label="Default Operator" width="130">
          <template #default="{ row }">
            <el-select v-model="row.operator" size="small">
              <el-option v-for="op in OPERATORS" :key="op" :label="op" :value="op" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="Note">
          <template #default>
            <span class="muted">Empty cell = this dimension does not participate in the row's evaluation</span>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="70" align="center">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" @click="removeColumn($index)">Del</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-head">
        <span>Rule Rows</span>
        <el-button size="small" type="primary" plain @click="addRow">Add Rule Row</el-button>
      </div>
      <el-table :data="rows" size="small" border>
        <el-table-column label="#" width="46" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column
          v-for="(col, ci) in columns"
          :key="ci"
          :label="col.title || col.fieldCode || `Condition ${ci + 1}`"
          min-width="120"
        >
          <template #default="{ row }">
            <el-input v-model="row.values[ci]" size="small" placeholder="Empty = no check" />
          </template>
        </el-table-column>
        <el-table-column label="Conclusion" width="110">
          <template #default="{ row }">
            <el-select v-model="row.resultType" size="small" placeholder="Optional" clearable>
              <el-option label="Pass" value="1" />
              <el-option label="Reject" value="2" />
              <el-option label="Manual" value="3" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="Output Value" width="130">
          <template #default="{ row }">
            <el-input v-model="row.resultValue" size="small" placeholder="Business code" />
          </template>
        </el-table-column>
        <el-table-column label="Score" width="90">
          <template #default="{ row }">
            <el-input-number v-model="row.scoreValue" size="small" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="Enabled" width="70" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="70" align="center">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" @click="rows.splice($index, 1)">Del</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="editorVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listDTables,
  getDTable,
  saveDTable,
  removeDTable,
  type DecisionTableBrief
} from '@/api/dtable'

const OPERATORS = ['==', '!=', '>', '>=', '<', '<=', 'between', 'in']

interface ColumnDraft {
  colType: 1
  fieldCode: string
  operator: string
  title: string
  seq: number
}

interface RowDraft {
  rowNo: number
  resultType: string | null
  resultValue: string | null
  scoreValue: number | null
  enabled: number
  remark: string | null
  values: string[]
}

const keyword = ref('')
const loading = ref(false)
const tables = ref<DecisionTableBrief[]>([])

const editorVisible = ref(false)
const saving = ref(false)
const form = ref({ id: undefined as number | undefined, code: '', name: '', hitPolicy: 'FIRST', status: 1, description: '' })
const columns = ref<ColumnDraft[]>([])
const rows = ref<RowDraft[]>([])

onMounted(load)

async function load() {
  loading.value = true
  try {
    tables.value = await listDTables(keyword.value || undefined)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = { id: undefined, code: '', name: '', hitPolicy: 'FIRST', status: 1, description: '' }
  columns.value = []
  rows.value = []
  editorVisible.value = true
}

async function openEdit(id: number) {
  const detail = (await getDTable(id)) as {
    table: { id: number; code: string; name: string; hitPolicy: string; status: number; description: string | null }
    columns: Array<{ colType: number; fieldCode: string; operator: string; title: string; seq: number; id: number }>
    rows: Array<{ row: { rowNo: number; resultType: string; resultValue: string; scoreValue: number; enabled: number; remark: string }; cells: Array<{ colId: number; cellValue: string }> }>
  }
  form.value = {
    id: detail.table.id,
    code: detail.table.code,
    name: detail.table.name,
    hitPolicy: detail.table.hitPolicy,
    status: detail.table.status,
    description: detail.table.description || ''
  }
  const sorted = [...detail.columns].sort((a, b) => a.seq - b.seq)
  columns.value = sorted.map((c, i) => ({
    colType: 1,
    fieldCode: c.fieldCode,
    operator: c.operator || '==',
    title: c.title || '',
    seq: i
  }))
  const idxByColId = new Map<number, number>()
  sorted.forEach((c, i) => idxByColId.set(c.id, i))
  rows.value = detail.rows.map((r, i) => {
    const values = columns.value.map(() => '')
    ;(r.cells || []).forEach(c => {
      const idx = idxByColId.get(c.colId)
      if (idx != null && idx >= 0) {
        values[idx] = c.cellValue
      }
    })
    return {
      rowNo: i + 1,
      resultType: r.row.resultType || null,
      resultValue: r.row.resultValue || null,
      scoreValue: r.row.scoreValue ?? null,
      enabled: r.row.enabled ?? 1,
      remark: r.row.remark || null,
      values
    }
  })
  editorVisible.value = true
}

function addColumn() {
  columns.value.push({ colType: 1, fieldCode: '', operator: '==', title: '', seq: columns.value.length })
  rows.value.forEach(r => r.values.push(''))
}

function removeColumn(index: number) {
  columns.value.splice(index, 1)
  rows.value.forEach(r => r.values.splice(index, 1))
}

function addRow() {
  rows.value.push({
    rowNo: rows.value.length + 1,
    resultType: null,
    resultValue: '',
    scoreValue: null,
    enabled: 1,
    remark: null,
    values: columns.value.map(() => '')
  })
}

async function onSave() {
  if (!form.value.code || !form.value.name) {
    ElMessage.warning('Code and name are required')
    return
  }
  saving.value = true
  try {
    await saveDTable({
      id: form.value.id,
      code: form.value.code,
      name: form.value.name,
      description: form.value.description,
      hitPolicy: form.value.hitPolicy,
      status: form.value.status,
      columns: columns.value,
      rows: rows.value.map((r, i) => ({
        rowNo: i + 1,
        resultType: r.resultType,
        resultValue: r.resultValue,
        scoreValue: r.scoreValue,
        enabled: r.enabled,
        remark: r.remark,
        cells: r.values
          .map((value, colIndex) => ({ colIndex, value: (value || '').trim() }))
          .filter(c => c.value !== '')
      }))
    })
    ElMessage.success('Saved')
    editorVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(id: number) {
  await removeDTable(id)
  ElMessage.success('Deleted')
  await load()
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.toolbar__spacer {
  flex: 1;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0 8px;
  font-weight: 600;
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
