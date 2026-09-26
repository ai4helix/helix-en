<template>
  <div class="batch">
    <header class="batch__top">
      <div>
        <h2 class="batch__title">Batch Run</h2>
        <p class="batch__desc">Upload a user metrics file → engine batch execution → download results. Click "New Batch" to finish in three steps.</p>
      </div>
      <button class="btn btn--primary btn--lg" @click="openWizard">
        <el-icon><Plus /></el-icon>New Batch
      </button>
    </header>

    <el-tabs v-model="tab" class="batch__tabs">
      <el-tab-pane label="Batch Tasks" name="task">
        <div class="pane">
          <div class="pane__bar">
            <span class="pane__bar-hint">
              Running tasks refresh progress every 3 seconds; view details or download the CSV after completion.
            </span>
          </div>
          <el-table :data="batches" v-loading="loadingTasks" size="default" class="batch__table">
            <el-table-column prop="id" label="ID" width="64" />
            <el-table-column prop="name" label="Task Name" min-width="140" show-overflow-tooltip />
            <el-table-column prop="engineCode" label="Engine" width="130" show-overflow-tooltip />
            <el-table-column label="Status" width="96">
              <template #default="{ row }">
                <span class="tag" :class="statusClass(row.status)">{{ statusText(row.status) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Progress" width="150">
              <template #default="{ row }">
                <div class="progress">
                  <div class="progress__bar">
                    <div class="progress__ok" :style="{ width: pct(row.successRows, row.totalRows) }" />
                    <div class="progress__fail" :style="{ width: pct(row.failRows, row.totalRows) }" />
                  </div>
                  <span class="progress__text">
                    {{ (row.successRows || 0) + (row.failRows || 0) }}/{{ row.totalRows || 0 }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="fileName" label="Source File" min-width="120" show-overflow-tooltip />
            <el-table-column prop="createdTime" label="Created At" width="160" />
            <el-table-column prop="finishedTime" label="Finished At" width="160" />
            <el-table-column label="Actions" width="230" fixed="right">
              <template #default="{ row }">
                <button
                  class="btn btn--small btn--primary"
                  :disabled="row.status === 1 || runningId === row.id"
                  @click="doRun(row)"
                >
                  {{ row.status === 0 ? 'Start Batch' : 'Re-run' }}
                </button>
                <button class="btn btn--small" @click="openItems(row)">Results</button>
                <button
                  class="btn btn--small"
                  :class="{ 'btn--accent': row.status === 2 }"
                  :disabled="!row.totalRows"
                  @click="doDownload(row)"
                >
                  Download
                </button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="taskPage"
              :page-size="taskSize"
              :total="taskTotal"
              layout="total, prev, pager, next"
              @current-change="reloadTasks"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Advanced" name="adv">
        <div class="adv-section">
          <div class="adv-section__head">
            <h3 class="adv-section__title">Indicator Field Dictionary</h3>
            <span class="pane__bar-hint">
              Every column in the data file must be registered here first; <b>{{ allFields.length }}</b> fields registered so far
            </span>
          </div>
          <div class="pane">
            <div class="pane__bar">
              <button class="btn btn--small" @click="doDownloadFieldTemplate">
                <el-icon><Download /></el-icon>Download Template
              </button>
              <span class="pane__bar-hint">The template includes a header and example rows; fill it in by column, then upload</span>
            </div>
            <div
              class="dropzone dropzone--slim"
              :class="{ 'is-drag': fieldDrag }"
              @dragover.prevent="fieldDrag = true"
              @dragleave="fieldDrag = false"
              @drop.prevent="onFieldDrop"
            >
              <div class="dropzone__row">
                <el-icon :size="22"><UploadFilled /></el-icon>
                <div class="dropzone__hint">Drag or select a CSV file (UTF-8)</div>
                <div class="dropzone__file" v-if="fieldFile">{{ fieldFile.name }}</div>
                <button class="btn btn--primary" @click="pickFieldFile">Select File</button>
              </div>
              <input
                ref="fieldInput"
                type="file"
                accept=".csv"
                style="display: none"
                @change="(e: Event) => onPick((e.target as HTMLInputElement), (f: File) => onFieldFilePicked(f))"
              />
            </div>
            <div class="pane__tips">
              <div class="pane__tips-title">Template Column Guide</div>
              <div class="pane__tips-line"><code>field_en</code> Indicator name in English (required, starts with a letter; letters/digits/underscores)</div>
              <div class="pane__tips-line"><code>field_cn</code> Indicator Chinese name (optional)</div>
              <div class="pane__tips-line"><code>value_type</code> Value type: 1 number / 2 string / 3 enum / 4 decimal (default 1)</div>
              <div class="pane__tips-line"><code>is_output</code> Output field: 0 no / 1 yes (default 0)</div>
              <div class="pane__tips-line">
                Existing English names will update their type/name/output flag; import ownership is determined automatically by the current user's organization.
              </div>
            </div>
            <div class="pane__actions">
              <button class="btn btn--primary" :disabled="!fieldFile || importingFields" @click="doImportFields">
                {{ importingFields ? 'Importing…' : 'Start Import' }}
              </button>
              <span v-if="fieldSummary" class="pane__summary">
                Inserted <b>{{ fieldSummary.inserted }}</b>, updated <b>{{ fieldSummary.updated }}</b>, {{ fieldSummary.total }} rows in total
              </span>
            </div>
          </div>
        </div>

        <el-divider />

        <div class="adv-section">
          <div class="adv-section__head">
            <h3 class="adv-section__title">Engine Task Definitions (Batch Binding)</h3>
            <span class="pane__bar-hint">Task code, name, bound engine, key field — import via CSV when creating them one by one is too slow</span>
          </div>
          <div class="pane">
            <div class="pane__bar">
              <button class="btn btn--small" @click="doDownloadEngineTaskTemplate">
                <el-icon><Download /></el-icon>Download Template
              </button>
            </div>
            <div
              class="dropzone dropzone--slim"
              :class="{ 'is-drag': taskDrag }"
              @dragover.prevent="taskDrag = true"
              @dragleave="taskDrag = false"
              @drop.prevent="onTaskDrop"
            >
              <div class="dropzone__row">
                <el-icon :size="22"><UploadFilled /></el-icon>
                <div class="dropzone__hint">Drag or select a CSV file (UTF-8, with a task_code header column)</div>
                <div class="dropzone__file" v-if="taskFile">{{ taskFile.name }}</div>
                <button class="btn btn--primary" @click="pickTaskFile">Select File</button>
              </div>
              <input
                ref="taskInput"
                type="file"
                accept=".csv"
                style="display: none"
                @change="(e: Event) => onPick((e.target as HTMLInputElement), (f: File) => (taskFile = f))"
              />
            </div>
            <div class="pane__tips">
              <div class="pane__tips-title">Template Column Guide</div>
              <div class="pane__tips-line"><code>task_code</code> Task code (required, unique within tenant, starts with a letter/underscore)</div>
              <div class="pane__tips-line"><code>task_name</code> Task name (required)</div>
              <div class="pane__tips-line"><code>engine_code</code> Bound engine code (required, must be a visible and enabled engine)</div>
              <div class="pane__tips-line"><code>key_field</code> Key field: uid / pid (optional, default uid)</div>
              <div class="pane__tips-line"><code>description</code> Description (optional)</div>
            </div>
            <div class="pane__actions">
              <button class="btn btn--primary" :disabled="!taskFile || importingTasks" @click="doImportTasks">
                {{ importingTasks ? 'Importing…' : 'Start Import' }}
              </button>
              <span v-if="taskSummary" class="pane__summary">
                Inserted <b>{{ taskSummary.inserted }}</b>, updated <b>{{ taskSummary.updated }}</b>, {{ taskSummary.total }} rows in total
              </span>
            </div>
            <el-table
              v-if="taskSummary && taskSummary.errors.length"
              :data="taskSummary.errors"
              size="small"
              class="pane__errors"
              max-height="240"
            >
              <el-table-column prop="row" label="Row" width="72" />
              <el-table-column prop="taskCode" label="Task Code" width="160" show-overflow-tooltip />
              <el-table-column prop="message" label="Failure Reason" min-width="240" show-overflow-tooltip />
            </el-table>

            <div class="pane__bar" style="margin-top: 20px">
              <el-input
                v-model="etKeyword"
                placeholder="Search task code/name"
                clearable
                size="small"
                style="width: 220px"
                @keyup.enter="reloadEngineTasks"
                @clear="reloadEngineTasks"
              />
              <span class="pane__bar-hint">{{ etTotal }} engine tasks in total</span>
            </div>
            <el-table :data="engineTasks" v-loading="loadingEt" size="small" class="batch__table">
              <el-table-column prop="taskCode" label="Task Code" width="170" show-overflow-tooltip />
              <el-table-column prop="taskName" label="Task Name" min-width="150" show-overflow-tooltip />
              <el-table-column prop="engineCode" label="Engine" width="130" show-overflow-tooltip />
              <el-table-column prop="keyField" label="Key Field" width="80" />
              <el-table-column prop="description" label="Description" min-width="180" show-overflow-tooltip />
              <el-table-column label="Status" width="90">
                <template #default="{ row }">
                  <span class="tag" :class="row.status === 1 ? 'is-primary' : 'is-muted'">
                    {{ row.status === 1 ? 'Enabled' : 'Disabled' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="Actions" width="150" fixed="right">
                <template #default="{ row }">
                  <button class="btn btn--small" @click="toggleEngineTask(row)">
                    {{ row.status === 1 ? 'Disable' : 'Enable' }}
                  </button>
                  <button class="btn btn--small" @click="removeEngineTask(row)">Delete</button>
                </template>
              </el-table-column>
            </el-table>
            <div class="pager">
              <el-pagination
                v-model:current-page="etPageNo"
                :page-size="etPageSize"
                :total="etTotal"
                layout="total, prev, pager, next"
                @current-change="reloadEngineTasks"
              />
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="wizardOpen"
      title="New Batch"
      width="720px"
      :close-on-click-modal="false"
      @closed="onWizardClosed"
    >
      <el-steps :active="wizStep" align-center finish-status="success" class="wiz-steps">
        <el-step title="Upload Data" />
        <el-step title="Execute" />
        <el-step title="Done" />
      </el-steps>

      <div v-if="wizStep === 0" class="wiz-body">
        <div class="form-grid">
          <label class="form-grid__item">
            <span class="form-grid__label">Task Name<i>*</i></span>
            <el-input v-model="taskName" placeholder="e.g. 2026-09 monthly batch" maxlength="100" />
          </label>
          <label class="form-grid__item">
            <span class="form-grid__label">Engine<i>*</i></span>
            <el-select v-model="engineCode" placeholder="Select engine" filterable>
              <el-option v-for="e in engines" :key="e.code" :label="`${e.name} (${e.code})`" :value="e.code" />
            </el-select>
          </label>
          <label class="form-grid__item">
            <span class="form-grid__label">User Key Column<i>*</i></span>
            <el-radio-group v-model="keyField">
              <el-radio value="uid">uid</el-radio>
              <el-radio value="pid">pid</el-radio>
            </el-radio-group>
          </label>
        </div>

        <div
          class="dropzone"
          :class="{ 'is-drag': dataDrag }"
          @dragover.prevent="dataDrag = true"
          @dragleave="dataDrag = false"
          @drop.prevent="onDataDrop"
        >
          <el-icon :size="28"><UploadFilled /></el-icon>
          <div class="dropzone__hint">Drag or select a CSV file (first column must be {{ keyField }}, other columns are indicator field names, ≤ 5000 rows)</div>
          <div class="dropzone__file" v-if="dataFile">{{ dataFile.name }}</div>
          <button class="btn btn--primary" @click="pickDataFile">Select File</button>
          <input
            ref="dataInput"
            type="file"
            accept=".csv"
            style="display: none"
            @change="(e: Event) => onPick((e.target as HTMLInputElement), (f: File) => onDataFilePicked(f))"
          />
        </div>

        <div v-if="preCheck" class="precheck" :class="{ 'has-error': preCheck.errors.length }">
          <div class="precheck__line is-ok">Detected <b>{{ preCheck.rows }}</b> data rows</div>
          <div class="precheck__line" :class="preCheck.keyOk ? 'is-ok' : 'is-err'">
            {{ preCheck.keyOk ? '✓' : '✗' }} First column is <code>{{ preCheck.firstCol || '—' }}</code>
            <span v-if="!preCheck.keyOk">(should be {{ keyField }}, switch the key column above if needed)</span>
          </div>
          <div class="precheck__line is-ok">{{ preCheck.knownCols.length }} indicator columns registered</div>
          <div v-if="preCheck.unknownCols.length" class="precheck__line is-err">
            ✗ Unregistered columns: {{ preCheck.unknownCols.join(', ') }} — the import will be rejected.
            Register them under "Advanced → Indicator Field Dictionary" first, or fix the file headers
          </div>
        </div>

        <div class="pane__tips">
          <div class="pane__tips-title">No template?</div>
          <div class="pane__tips-line">
            <button class="link-btn" @click="doDownloadDataTemplate">Download data template</button>
            (generates indicator columns from registered fields, currently {{ allFields.length }} columns); numbers are auto-detected.
          </div>
        </div>
      </div>

      <div v-if="wizStep === 1" class="wiz-body wiz-run">
        <el-progress
          :percentage="runPct"
          :status="runFailed ? 'exception' : runPct >= 100 ? 'success' : undefined"
        />
        <p class="wiz-run__phase">{{ runPhaseText }}</p>
        <p v-if="runError" class="wiz-run__error">{{ runError }}</p>
        <p class="wiz-run__hint">Execution started automatically; close this window and keep watching in "Batch Tasks"</p>
      </div>

      <div v-if="wizStep === 2" class="wiz-body wiz-done">
        <template v-if="!runFailed">
          <div class="wiz-done__row">
            <span class="wiz-done__num is-ok">{{ wizardBatch?.successRows ?? 0 }}</span>
            <span class="wiz-done__label">Succeeded</span>
          </div>
          <div class="wiz-done__row" v-if="wizardBatch?.failRows">
            <span class="wiz-done__num is-no">{{ wizardBatch.failRows }}</span>
            <span class="wiz-done__label">Failed</span>
          </div>
          <div class="wiz-done__row">
            <span class="wiz-done__num">{{ wizardBatch?.totalRows ?? 0 }}</span>
            <span class="wiz-done__label">Total rows</span>
          </div>
        </template>
        <p v-else class="wiz-run__error">Execution failed. Check details in "Batch Tasks" and retry</p>
        <div class="wiz-done__actions">
          <button v-if="!runFailed" class="btn btn--primary" @click="doDownload(wizardBatch!)">
            <el-icon><Download /></el-icon>Download Results CSV
          </button>
          <button v-if="!runFailed" class="btn" @click="openItems(wizardBatch!); wizardOpen = false">View Details</button>
          <button class="btn" @click="resetWizard">Run Another Batch</button>
        </div>
      </div>

      <template #footer>
        <template v-if="wizStep === 0">
          <button class="btn" @click="wizardOpen = false">Cancel</button>
          <button
            class="btn btn--primary"
            :disabled="!canSubmit"
            @click="wizardSubmit"
          >
            Upload and Execute
          </button>
        </template>
        <template v-else-if="wizStep === 1">
          <button class="btn" @click="wizardOpen = false">Keep Running in Background</button>
        </template>
        <template v-else>
          <button class="btn" @click="wizardOpen = false">Close</button>
        </template>
      </template>
    </el-dialog>

    <el-drawer v-model="itemsVisible" :title="`Task #${currentBatch?.id} Results`" size="62%">
      <div class="items">
        <div class="items__bar">
          <el-radio-group v-model="itemStatus" size="small" @change="reloadItems(true)">
            <el-radio-button :value="-1">All</el-radio-button>
            <el-radio-button :value="1">Success</el-radio-button>
            <el-radio-button :value="2">Failed</el-radio-button>
            <el-radio-button :value="0">Pending</el-radio-button>
          </el-radio-group>
          <el-input
            v-model="itemKeyword"
            placeholder="Search user key"
            clearable
            size="small"
            style="width: 180px"
            @keyup.enter="reloadItems(true)"
            @clear="reloadItems(true)"
          />
          <button class="btn btn--small" @click="doDownload(currentBatch!)">Download CSV</button>
        </div>
        <el-table :data="items" v-loading="loadingItems" size="small" height="calc(100vh - 220px)">
          <el-table-column prop="rowNo" label="Row" width="64" />
          <el-table-column prop="bizKey" label="User Key" width="120" show-overflow-tooltip />
          <el-table-column label="Status" width="84">
            <template #default="{ row }">
              <span class="tag" :class="itemClass(row.status)">{{ itemText(row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="resultText" label="Result" width="90" />
          <el-table-column prop="totalScore" label="Score" width="72" />
          <el-table-column prop="hitRules" label="Hit Rules" min-width="220" show-overflow-tooltip />
          <el-table-column prop="traceId" label="TraceId" width="140" show-overflow-tooltip />
          <el-table-column prop="errorMsg" label="Error" min-width="160" show-overflow-tooltip />
          <el-table-column label="Actions" width="86" fixed="right">
            <template #default="{ row }">
              <button
                class="btn btn--small"
                :disabled="!row.traceId"
                title="View node-level execution trace of this decision"
                @click="openTrace(row)"
              >
                Trace
              </button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="itemPage"
            :page-size="itemSize"
            :total="itemTotal"
            layout="total, prev, pager, next"
            @current-change="reloadItems(false)"
          />
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="traceVisible" title="Node Execution Trace" size="480px">
      <div v-if="traceLoading" class="trace-loading">
        <el-icon class="is-loading"><Loading /></el-icon>Loading trace…
      </div>
      <template v-else-if="traceResult">
        <div class="trace-head">
          <span class="tag" :class="traceResult.pass ? 'is-primary' : 'is-danger'">
            {{ traceResult.result || 'Unknown' }}
          </span>
          <span class="trace-head__meta">Score {{ traceResult.score ?? 0 }}</span>
        </div>
        <TraceList v-if="traceResult.traces?.length" :traces="traceResult.traces" />
        <div v-else class="trace-empty">No trace recorded for this decision (historical batches may not include node details)</div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Plus, UploadFilled, Loading } from '@element-plus/icons-vue'
import { listEngines } from '@/api/flow'
import { listFields } from '@/api/knowledge'
import { getResultByTrace, type ResultSetVO } from '@/api/result'
import TraceList from '@/views/run/TraceList.vue'
import type { Engine } from '@/types/flow'
import {
  changeEngineTaskStatus,
  deleteEngineTask,
  downloadDataTemplate,
  downloadEngineTaskTemplate,
  downloadFieldTemplate,
  downloadResults,
  importData,
  importEngineTasks,
  importFields,
  pageBatches,
  pageEngineTasks,
  pageItems,
  runBatch,
  type DataImportSummary,
  type EngineTask,
  type EngineTaskImportSummary,
  type FieldImportSummary,
  type IndicatorBatch,
  type IndicatorBatchItem
} from '@/api/batch'

const tab = ref('task')

const wizardOpen = ref(false)
const wizStep = ref(0)
const wizardBatch = ref<IndicatorBatch | null>(null)
const runFailed = ref(false)
const runPct = ref(0)
const runPhaseText = ref('')
const runError = ref('')
let wizPollTimer: number | undefined
const wizardRunning = ref(false)

const allFields = ref<string[]>([])

function openWizard() {
  resetWizard()
  wizardOpen.value = true
}

function resetWizard() {
  wizStep.value = 0
  wizardBatch.value = null
  runFailed.value = false
  runPct.value = 0
  runPhaseText.value = ''
  runError.value = ''
  wizardRunning.value = false
  taskName.value = ''
  engineCode.value = ''
  dataFile.value = null
  preCheck.value = null
  window.clearTimeout(wizPollTimer)
}

function onWizardClosed() {
  window.clearTimeout(wizPollTimer)
  wizardRunning.value = false
  reloadTasks()
}

const preCheck = ref<null | {
  rows: number
  firstCol: string
  keyOk: boolean
  knownCols: string[]
  unknownCols: string[]
  errors: string[]
}>(null)

function onDataFilePicked(f: File) {
  dataFile.value = f
  preCheck.value = null
  const reader = new FileReader()
  reader.onload = () => {
    try {
      const text = String(reader.result || '')
      const lines = text.split(/\r?\n/).filter((l) => l.trim() !== '')
      if (lines.length < 2) {
        preCheck.value = { rows: 0, firstCol: '', keyOk: false, knownCols: [], unknownCols: [], errors: ['The file has no data rows'] }
        return
      }
      const headers = lines[0].split(',').map((h) => h.trim())
      const rows = lines.length - 1
      const firstCol = headers[0] || ''
      const known = new Set(allFields.value)
      const knownCols = headers.slice(1).filter((h) => h && known.has(h))
      const unknownCols = headers.slice(1).filter((h) => h && !known.has(h))
      const errors: string[] = []
      if (rows > 5000) errors.push(`Exceeds the 5000-row per-task limit (current ${rows} rows)`)
      preCheck.value = { rows, firstCol, keyOk: firstCol === keyField.value, knownCols, unknownCols, errors }
    } catch {
      preCheck.value = { rows: 0, firstCol: '', keyOk: false, knownCols: [], unknownCols: [], errors: ['Failed to read the file'] }
    }
  }
  reader.readAsText(f, 'utf-8')
}

const canSubmit = computed(() => {
  if (!taskName.value || !engineCode.value || !dataFile.value) return false
  if (preCheck.value) {
    if (preCheck.value.errors.length) return false
    if (!preCheck.value.keyOk) return false
    if (preCheck.value.unknownCols.length) return false
  }
  return true
})

async function wizardSubmit() {
  if (!canSubmit.value) return
  wizStep.value = 1
  wizardRunning.value = true
  runError.value = ''
  runPhaseText.value = 'Importing data…'
  runPct.value = 8
  wizPollAborted = false
  try {
    const summary: DataImportSummary = await importData(taskName.value, engineCode.value, keyField.value, dataFile.value!)
    const batchId = summary.batchId
    runPhaseText.value = 'Import done, starting engine execution…'
    runPct.value = 20
    await runBatch(batchId)
    runPhaseText.value = 'Engine executing…'
    await pollWizardBatch(batchId)
  } catch (e: any) {
    runFailed.value = true
    runPhaseText.value = 'Operation failed'
    runError.value = e?.message || 'Import or start failed; check the file and retry'
  } finally {
    wizardRunning.value = false
  }
}

let wizPollAborted = false

async function pollWizardBatch(batchId: number) {
  for (let i = 0; i < 150; i++) {
    if (wizPollAborted) return
    await new Promise((r) => setTimeout(r, 2000))
    if (wizPollAborted) return
    try {
      const page = await pageBatches(1, 50)
      const b = page.records.find((x) => x.id === batchId)
      if (!b) continue
      wizardBatch.value = b
      const done = (b.successRows || 0) + (b.failRows || 0)
      runPct.value = b.totalRows ? Math.min(100, Math.round((done / b.totalRows) * 100)) : runPct.value
      runPhaseText.value = `Engine executing… ${done}/${b.totalRows}`
      if (b.status === 2) {
        runPct.value = 100
        runPhaseText.value = 'Execution completed'
        wizStep.value = 2
        reloadTasks()
        return
      }
      if (b.status === 3) {
        runFailed.value = true
        runPhaseText.value = 'Execution failed'
        runError.value = 'Some or all rows failed; download the results to view error messages'
        wizStep.value = 2
        reloadTasks()
        return
      }
    } catch {
    }
  }
  runPhaseText.value = 'Execution is taking long; moved to background'
  runError.value = ''
  runFailed.value = false
  wizardBatch.value = null
  reloadTasks()
}

const fieldFile = ref<File | null>(null)
const fieldInput = ref<HTMLInputElement>()
const fieldDrag = ref(false)
const importingFields = ref(false)
const fieldSummary = ref<FieldImportSummary | null>(null)

function pickFieldFile() {
  fieldInput.value?.click()
}
function onFieldDrop(e: DragEvent) {
  fieldDrag.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) onFieldFilePicked(f)
}
function onFieldFilePicked(f: File) {
  fieldFile.value = f
  fieldSummary.value = null
}
async function doImportFields() {
  if (!fieldFile.value) return
  importingFields.value = true
  try {
    fieldSummary.value = await importFields(fieldFile.value)
    ElMessage.success('Indicator fields imported')
    await loadAllFields()
  } finally {
    importingFields.value = false
  }
}

async function loadAllFields() {
  try {
    const list = await listFields()
    allFields.value = (list || []).map((f: any) => f.fieldEn)
  } catch {
    allFields.value = []
  }
}

const taskName = ref('')
const engineCode = ref('')
const keyField = ref('uid')
const engines = ref<Engine[]>([])
const dataFile = ref<File | null>(null)
const dataInput = ref<HTMLInputElement>()
const dataDrag = ref(false)

function pickDataFile() {
  dataInput.value?.click()
}
function onDataDrop(e: DragEvent) {
  dataDrag.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) onDataFilePicked(f)
}

function onPick(input: HTMLInputElement, set: (f: File) => void) {
  const f = input.files?.[0]
  if (f) set(f)
  input.value = ''
}

async function doDownloadFieldTemplate() {
  await downloadFieldTemplate()
  ElMessage.success('Template downloaded')
}

async function doDownloadDataTemplate() {
  await downloadDataTemplate(engineCode.value || undefined, keyField.value, true)
  ElMessage.success('Template downloaded (with example rows)')
}

const taskFile = ref<File | null>(null)
const taskInput = ref<HTMLInputElement>()
const taskDrag = ref(false)
const importingTasks = ref(false)
const taskSummary = ref<EngineTaskImportSummary | null>(null)

function pickTaskFile() {
  taskInput.value?.click()
}
function onTaskDrop(e: DragEvent) {
  taskDrag.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) taskFile.value = f
}
async function doDownloadEngineTaskTemplate() {
  await downloadEngineTaskTemplate()
  ElMessage.success('Template downloaded')
}
async function doImportTasks() {
  if (!taskFile.value) return
  importingTasks.value = true
  try {
    taskSummary.value = await importEngineTasks(taskFile.value)
    if (taskSummary.value.errors.length) {
      ElMessage.warning(
        `Import done: ${taskSummary.value.inserted} inserted, ${taskSummary.value.updated} updated, ${taskSummary.value.errors.length} rows failed`
      )
    } else {
      ElMessage.success(`Import succeeded: ${taskSummary.value.inserted} inserted, ${taskSummary.value.updated} updated`)
    }
    reloadEngineTasks()
  } finally {
    importingTasks.value = false
  }
}

const engineTasks = ref<EngineTask[]>([])
const etPageNo = ref(1)
const etPageSize = 10
const etTotal = ref(0)
const etKeyword = ref('')
const loadingEt = ref(false)

async function reloadEngineTasks() {
  loadingEt.value = true
  try {
    const page = await pageEngineTasks(etPageNo.value, etPageSize, {
      keyword: etKeyword.value || undefined
    })
    engineTasks.value = page.records
    etTotal.value = page.total
  } finally {
    loadingEt.value = false
  }
}

async function toggleEngineTask(row: EngineTask) {
  await changeEngineTaskStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(row.status === 1 ? 'Disabled' : 'Enabled')
  reloadEngineTasks()
}

async function removeEngineTask(row: EngineTask) {
  await deleteEngineTask(row.id)
  ElMessage.success('Deleted')
  reloadEngineTasks()
}

const batches = ref<IndicatorBatch[]>([])
const taskPage = ref(1)
const taskSize = 10
const taskTotal = ref(0)
const loadingTasks = ref(false)
const runningId = ref<number | null>(null)
let pollTimer: number | undefined

const statusText = (s: number) => ['Pending', 'Running', 'Completed', 'Failed'][s] ?? 'Unknown'
const statusClass = (s: number) => ['is-muted', 'is-running', 'is-primary', 'is-danger'][s] ?? 'is-muted'

async function reloadTasks() {
  loadingTasks.value = true
  try {
    const page = await pageBatches(taskPage.value, taskSize)
    batches.value = page.records
    taskTotal.value = page.total
  } finally {
    loadingTasks.value = false
  }
  schedulePoll()
}

function schedulePoll() {
  window.clearTimeout(pollTimer)
  if (batches.value.some((b) => b.status === 1)) {
    pollTimer = window.setTimeout(reloadTasks, 3000)
  }
}

async function doRun(row: IndicatorBatch) {
  runningId.value = row.id
  try {
    await runBatch(row.id)
    ElMessage.success(`Task #${row.id} execution started`)
    reloadTasks()
  } finally {
    runningId.value = null
  }
}

const itemsVisible = ref(false)
const currentBatch = ref<IndicatorBatch | null>(null)
const items = ref<IndicatorBatchItem[]>([])
const itemPage = ref(1)
const itemSize = 20
const itemTotal = ref(0)
const itemStatus = ref(-1)
const itemKeyword = ref('')
const loadingItems = ref(false)

const itemText = (s: number) => (s === 1 ? 'Success' : s === 2 ? 'Failed' : 'Pending')
const itemClass = (s: number) => (s === 1 ? 'is-primary' : s === 2 ? 'is-danger' : 'is-muted')

function openItems(row: IndicatorBatch) {
  currentBatch.value = row
  itemsVisible.value = true
  itemPage.value = 1
  itemStatus.value = -1
  itemKeyword.value = ''
  reloadItems(true)
}

const traceVisible = ref(false)
const traceLoading = ref(false)
const traceResult = ref<ResultSetVO>()

async function openTrace(row: IndicatorBatchItem) {
  if (!row.traceId) return
  traceVisible.value = true
  traceLoading.value = true
  traceResult.value = undefined
  try {
    traceResult.value = await getResultByTrace(row.traceId)
  } catch {
    ElMessage.error('Failed to load trace')
    traceVisible.value = false
  } finally {
    traceLoading.value = false
  }
}

async function reloadItems(reset: boolean) {
  if (!currentBatch.value) return
  if (reset) itemPage.value = 1
  loadingItems.value = true
  try {
    const page = await pageItems(currentBatch.value.id, itemPage.value, itemSize, {
      status: itemStatus.value === -1 ? undefined : itemStatus.value,
      keyword: itemKeyword.value || undefined
    })
    items.value = page.records
    itemTotal.value = page.total
  } finally {
    loadingItems.value = false
  }
}

async function doDownload(row: IndicatorBatch) {
  await downloadResults(row.id, row.name)
  ElMessage.success('Results downloaded')
}

const pct = (part?: number, total?: number) =>
  `${Math.min(100, total ? Math.round(((part || 0) / total) * 100) : 0)}%`

onMounted(async () => {
  reloadTasks()
  reloadEngineTasks()
  try {
    engines.value = (await listEngines()) || []
  } catch {
    engines.value = []
  }
  await loadAllFields()
})
onBeforeUnmount(() => {
  window.clearTimeout(pollTimer)
  window.clearTimeout(wizPollTimer)
  wizPollAborted = true
})
</script>

<style scoped>
.batch {
  padding: 20px 24px;
  height: 100%;
  overflow: auto;
}
.batch__top {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.batch__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.batch__desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.btn--lg {
  padding: 9px 18px;
  font-size: 14px;
}
.btn--accent {
  border-color: var(--el-color-success);
  color: var(--el-color-success);
}
.adv-section {
  margin-bottom: 8px;
}
.adv-section__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 4px;
}
.adv-section__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}
.pane {
  padding: 8px 4px;
}
.pane__bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.pane__bar-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.pane__errors {
  width: 100%;
  margin-top: 14px;
}
.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 10px;
  color: var(--el-text-color-secondary);
  transition: border-color 0.2s;
}
.dropzone--slim {
  padding: 14px 16px;
}
.dropzone__row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dropzone.is-drag {
  border-color: var(--el-color-primary);
}
.dropzone__hint {
  font-size: 13px;
}
.dropzone__file {
  font-size: 12px;
  color: var(--el-color-primary);
}
.form-grid {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.form-grid__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 240px;
}
.form-grid__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.form-grid__label i {
  color: var(--el-color-danger);
  font-style: normal;
  margin-left: 2px;
}
.pane__tips {
  margin-top: 14px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 2;
  color: var(--el-text-color-secondary);
}
.pane__tips code {
  background: var(--el-fill-color);
  padding: 1px 6px;
  border-radius: 4px;
  margin-right: 6px;
}
.pane__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}
.pane__summary {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.link-btn {
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
}
.link-btn:hover {
  text-decoration: underline;
}
.precheck {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 1.9;
}
.precheck__line.is-ok {
  color: var(--el-text-color-regular);
}
.precheck__line.is-err {
  color: var(--el-color-danger);
}
.precheck.has-error {
  border: 1px solid var(--el-color-danger-light-5);
}
.wiz-steps {
  margin-bottom: 20px;
}
.wiz-body {
  min-height: 220px;
}
.wiz-run {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px 0;
}
.wiz-run__phase {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.wiz-run__error {
  margin: 0;
  font-size: 13px;
  color: var(--el-color-danger);
}
.wiz-run__hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.wiz-done {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 16px 0;
}
.wiz-done__row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.wiz-done__num {
  font-size: 30px;
  font-weight: 700;
}
.wiz-done__num.is-ok {
  color: var(--el-color-success);
}
.wiz-done__num.is-no {
  color: var(--el-color-danger);
}
.wiz-done__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.wiz-done__actions {
  display: flex;
  gap: 10px;
  margin-top: 6px;
}
.trace-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 20px 0;
}
.trace-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  &__meta {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
.trace-empty {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  padding: 24px 0;
  text-align: center;
}
.batch__table {
  width: 100%;
}
.progress {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress__bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  overflow: hidden;
  background: var(--el-fill-color);
  display: flex;
}
.progress__ok {
  background: var(--el-color-success);
  height: 100%;
}
.progress__fail {
  background: var(--el-color-danger);
  height: 100%;
}
.progress__text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.tag.is-primary {
  background: var(--el-color-success-light-8);
  color: var(--el-color-success);
}
.tag.is-muted {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}
.tag.is-running {
  background: var(--el-color-warning-light-8);
  color: var(--el-color-warning);
}
.tag.is-danger {
  background: var(--el-color-danger-light-8);
  color: var(--el-color-danger);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.items__bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  border-radius: 6px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn:hover:not(:disabled) {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn--primary {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}
.btn--primary:hover:not(:disabled) {
  color: #fff;
  filter: brightness(1.05);
}
.btn--small {
  padding: 4px 10px;
  font-size: 12px;
  margin-right: 6px;
}
</style>
