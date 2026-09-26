<template>
  <div class="run">
    <header class="run__top">
      <div class="run__title">
        <el-icon><VideoPlay /></el-icon>
        <span>Run Center</span>
      </div>
      <div class="run__ctx">
        <el-select v-model="engineCode" placeholder="Select engine" class="run__engine">
          <el-option v-for="e in engines" :key="e.id" :label="e.name" :value="e.code" />
        </el-select>
        <el-select
          v-model="versionId"
          placeholder="Live version"
          clearable
          class="run__version"
          :disabled="!versions.length"
        >
          <el-option v-for="v in versions" :key="v.id" :label="versionLabel(v)" :value="v.id">
            <div class="vopt">
              <span>{{ versionLabel(v) }}</span>
              <span v-if="v.bootState === 1" class="vopt__badge is-live">Running</span>
              <span v-else class="vopt__badge">Draft</span>
            </div>
          </el-option>
        </el-select>
        <el-radio-group v-model="mode" class="run__mode">
          <el-radio-button value="single">Single Test</el-radio-button>
          <el-radio-button value="batch">Batch Test</el-radio-button>
        </el-radio-group>
      </div>
    </header>

    <main class="run__main">
      <section v-if="mode === 'single'" class="panel">
        <div class="panel__head">
          <span>Single Test · one sample through the full chain</span>
          <el-switch
            v-model="jsonMode"
            active-text="JSON"
            inactive-text="Form"
            inline-prompt
            class="panel__switch"
          />
        </div>

        <div v-if="jsonMode" class="field">
          <label class="field__label">Sample (JSON object, keys are field English names; click a field name to insert)</label>
          <el-input
            v-model="singleJson"
            type="textarea"
            :rows="10"
            resize="none"
            placeholder='{"applicant_age":34,"helix_score":760,...}'
          />
          <div v-if="fields.length" class="field-chips">
            <button v-for="f in fields" :key="f.id" class="chip mono" @click="addFieldToJson(f.fieldEn)">
              {{ f.fieldEn }}
            </button>
          </div>
          <div v-if="unknownKeys.length" class="field-warn">
            Unknown fields: {{ unknownKeys.join(', ') }} (treated as unknown variables by the engine)
          </div>
        </div>

        <div v-else class="form">
          <div v-if="!fields.length" class="form__empty">
            {{ fieldsLoadFailed ? 'Failed to load fields — switch to JSON mode to enter directly, or refresh and retry' : 'No fields yet; switch to JSON mode to enter directly' }}
          </div>
          <template v-for="grp in fieldGroups" :key="grp.name">
            <div class="form__group-title">
              {{ grp.name }}
              <small class="form__group-count">{{ grp.items.length }} items</small>
            </div>
            <div v-for="f in grp.items" :key="f.id" class="form__row">
              <div class="form__label">
                <span class="form__cn">{{ f.fieldCn || f.fieldEn }}</span>
                <span class="form__en mono">{{ f.fieldEn }}</span>
              </div>
              <el-input v-model="formData[f.fieldEn]" :placeholder="f.fieldEn" size="default" />
            </div>
          </template>
        </div>

        <div class="actions">
          <button class="btn" @click="fillSingleExample">Fill Example</button>
          <button class="btn btn--primary" :disabled="running" @click="runSingle">
            {{ running ? 'Running…' : 'Run Test' }}
          </button>
        </div>

        <div v-if="singleResult" class="result">
          <div class="verdict" :class="rowClass(singleResult)">
            <div class="verdict__text">{{ singleResult.result }}</div>
            <div class="verdict__sub">Total score {{ singleResult.score ?? 0 }}</div>
          </div>

          <section class="sect">
            <div class="sect__label">Basic Info</div>
            <dl class="kv">
              <div class="kv__row"><dt>Trace ID</dt><dd class="mono">{{ singleResult.traceId }}</dd></div>
              <div class="kv__row"><dt>Engine</dt><dd>{{ singleResult.engineName || singleResult.engineCode }}</dd></div>
              <div class="kv__row"><dt>Version</dt><dd>{{ singleResult.engineVersion ?? 'Live version' }}</dd></div>
            </dl>
          </section>

          <section v-if="singleResult.traces?.length" class="sect">
            <div class="sect__label">Node Execution Trace (see how this sample was judged)</div>
            <TraceList :traces="singleResult.traces" />
          </section>

          <section v-if="singleResult.input" class="sect">
            <div class="sect__label">Input</div>
            <pre class="json">{{ JSON.stringify(singleResult.input, null, 2) }}</pre>
          </section>
        </div>
      </section>

      <section v-else class="panel">
        <div class="panel__head">
          <span>Batch Test · run many samples in one go</span>
          <el-switch
            v-model="batchTrace"
            active-text="Return node traces"
            class="panel__switch"
          />
        </div>

        <div class="field">
          <label class="field__label">
            Test samples (JSON array, each item is one decision input; columns = indicator field English names)
            <button class="link-btn" @click="fillBatchExample">Fill Example</button>
          </label>
          <el-input
            v-model="batchText"
            type="textarea"
            :rows="12"
            resize="none"
            placeholder='[{"applicant_age":34,"helix_score":760,...}, {...}]'
          />
          <div v-if="fields.length" class="field-chips">
            <button v-for="f in fields" :key="f.id" class="chip mono" @click="addFieldToJson(f.fieldEn)">
              {{ f.fieldEn }}
            </button>
          </div>
          <div v-if="unknownKeys.length" class="field-warn">
            Unknown fields: {{ unknownKeys.join(', ') }} (treated as unknown variables by the engine)
          </div>
        </div>

        <div class="actions">
          <button class="btn" @click="downloadTemplate">Download CSV Template</button>
          <button class="btn btn--primary" :disabled="running" @click="runBatch">
            {{ running ? 'Running…' : 'Run Batch' }}
          </button>
        </div>

        <div v-if="batchSummary" class="summary">
          <div class="summary__head">
            <span>Batch {{ batchSummary.batchNo }}</span>
            <span class="summary__cost">{{ batchSummary.costMs }}ms</span>
          </div>
          <div class="summary__grid">
            <div class="summary__item"><span>Total</span><b>{{ batchSummary.total }}</b></div>
            <div class="summary__item is-ok"><span>Pass</span><b>{{ batchSummary.passCount }}</b></div>
            <div class="summary__item is-no"><span>Reject</span><b>{{ batchSummary.rejectCount }}</b></div>
            <div class="summary__item is-warn"><span>Manual</span><b>{{ batchSummary.manualCount }}</b></div>
            <div class="summary__item"><span>Pass rate</span><b>{{ batchSummary.passRate }}%</b></div>
          </div>
        </div>

        <div v-if="batchSummary" class="list">
          <article
            v-for="(r, i) in batchSummary.details"
            :key="i"
            class="row"
            :class="rowClass(r)"
            @click="openBatchDetail(r)"
          >
            <div class="row__idx">{{ i + 1 }}</div>
            <div class="row__verdict">{{ resultLabel(r) }}</div>
            <div class="row__main">
              <div class="row__title">
                <span class="row__engine">{{ r.engineName || r.engineCode }}</span>
                <span class="mono row__trace">{{ (r.traceId || '').slice(0, 12) }}</span>
              </div>
              <div class="row__meta">{{ r.result === 'Execution Failed' ? 'Call error' : 'Click to view node trace' }}</div>
            </div>
            <div class="row__score">
              <span class="row__score-label">Score</span>
              <span class="row__score-value">{{ r.score ?? 0 }}</span>
            </div>
            <el-icon class="row__arrow" :size="14"><ArrowRight /></el-icon>
          </article>
        </div>
      </section>
    </main>

    <el-drawer v-model="batchDetailVisible" title="Decision Detail" size="640px">
      <div v-if="batchDetail" class="detail">
        <div class="verdict" :class="rowClass(batchDetail)">
          <div class="verdict__text">{{ batchDetail.result }}</div>
          <div class="verdict__sub">Total score {{ batchDetail.score ?? 0 }}</div>
        </div>
        <section v-if="batchDetail.traces?.length" class="sect">
          <div class="sect__label">Node Execution Trace</div>
          <TraceList :traces="batchDetail.traces" />
        </section>
        <section v-if="batchDetail.input" class="sect">
          <div class="sect__label">Input</div>
          <pre class="json">{{ JSON.stringify(batchDetail.input, null, 2) }}</pre>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listEngines, listVersions } from '@/api/flow'
import type { Engine, EngineVersion } from '@/types/flow'
import { listAllFields } from '@/api/datamanage'
import { downloadDataTemplate } from '@/api/batch'
import { runDecisionTest } from '@/api/run'
import type { DecisionField } from '@/types/run'
import type { ResultSetVO, BatchTestResultVO } from '@/api/result'
import { resultClass, resultLabel } from '@/utils/result'
import TraceList from './TraceList.vue'

const engines = ref<Engine[]>([])
const versions = ref<EngineVersion[]>([])
const fields = ref<DecisionField[]>([])

const fieldGroups = computed(() => {
  const map = new Map<string, DecisionField[]>()
  for (const f of fields.value) {
    const name = (f.catalogName && String(f.catalogName).trim()) || 'Uncategorized'
    if (!map.has(name)) map.set(name, [])
    map.get(name)!.push(f)
  }
  const groups = Array.from(map.entries()).map(([name, items]) => ({ name, items }))
  groups.sort((a, b) => {
    const au = a.name === 'Uncategorized' ? 1 : 0
    const bu = b.name === 'Uncategorized' ? 1 : 0
    if (au !== bu) return au - bu
    return a.name.localeCompare(b.name, 'zh-Hans-CN')
  })
  return groups
})
const route = useRoute()

const engineCode = ref('')
const versionId = ref<number | null>(null)
const mode = ref<'single' | 'batch'>('single')

const jsonMode = ref(false)
const batchTrace = ref(false)
const fieldsLoadFailed = ref(false)


const fieldIndex = computed(() => {
  const m = new Map<string, number | undefined>()
  for (const f of fields.value) m.set(f.fieldEn, f.valueType)
  return m
})

const unknownKeys = computed<string[]>(() => {
  if (!jsonMode.value || !fieldIndex.value.size) return []
  const keys = new Set<string>()
  for (const sample of parseLoose(singleJson.value, batchText.value)) {
    for (const k of Object.keys(sample)) {
      if (!fieldIndex.value.has(k)) keys.add(k)
    }
  }
  return [...keys]
})

function parseLoose(single: string, batch: string): Record<string, unknown>[] {
  const out: Record<string, unknown>[] = []
  try {
    const o = JSON.parse(single || '{}')
    if (o && typeof o === 'object' && !Array.isArray(o)) out.push(o)
  } catch { /* ignore */ }
  try {
    const a = JSON.parse(batch || '[]')
    if (Array.isArray(a)) out.push(...a.filter((x) => x && typeof x === 'object'))
  } catch { /* ignore */ }
  return out
}

function applyFieldTypes(sample: Record<string, unknown>): Record<string, unknown> {
  if (!fieldIndex.value.size) return sample
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(sample)) {
    const vt = fieldIndex.value.get(k)
    if (typeof v === 'string' && (vt === 1 || vt === 4 || vt === 3)) {
      out[k] = coerce(v, vt)
    } else {
      out[k] = v
    }
  }
  return out
}

function addFieldToJson(fieldEn: string) {
  const isSingle = jsonMode.value
  const src = isSingle ? singleJson.value : batchText.value
  try {
    if (isSingle) {
      const obj = (JSON.parse(src || '{}') && typeof JSON.parse(src || '{}') === 'object' && !Array.isArray(JSON.parse(src || '{}')))
        ? JSON.parse(src || '{}') as Record<string, unknown>
        : {}
      if (!(fieldEn in obj)) obj[fieldEn] = ''
      singleJson.value = JSON.stringify(obj, null, 2)
    } else {
      const parsed = JSON.parse(src || '[]')
      const arr: Record<string, unknown>[] = Array.isArray(parsed) ? parsed : []
      if (!arr.length) arr.push({})
      const last = arr[arr.length - 1]
      if (!(fieldEn in last)) last[fieldEn] = ''
      batchText.value = JSON.stringify(arr, null, 2)
    }
  } catch {
    ElMessage.warning(isSingle ? 'Failed to parse JSON; fix the format before inserting fields' : 'Sample is not a valid JSON array; fix the format first')
  }
}
const formData = reactive<Record<string, string>>({})
const singleJson = ref('')
const batchText = ref('')

const singleResult = ref<ResultSetVO>()
const batchSummary = ref<BatchTestResultVO>()
const batchDetail = ref<ResultSetVO>()
const batchDetailVisible = ref(false)
const running = ref(false)

onMounted(async () => {
  engines.value = await listEngines()
  fields.value = await listAllFields().catch(() => {
    fieldsLoadFailed.value = true
    return []
  })

  const q = route.query
  if (q.mode === 'batch' || q.mode === 'single') {
    mode.value = q.mode
  }
  const qCode = typeof q.engineCode === 'string' ? q.engineCode : ''
  const target = engines.value.find((e) => e.code === qCode) || engines.value[0]
  if (target) {
    engineCode.value = target.code
    await onEngineChange(target.code)
    const qVersion = Number(q.versionId)
    if (qVersion && versions.value.some((v) => v.id === qVersion)) {
      versionId.value = qVersion
    }
  }
})

watch(engineCode, (code) => {
  if (code) onEngineChange(code)
})

async function onEngineChange(code: string) {
  versionId.value = null
  const e = engines.value.find((x) => x.code === code)
  versions.value = e ? await listVersions(e.id).catch(() => []) : []
  const live = versions.value.find((v) => v.bootState === 1)
  if (live) versionId.value = live.id
}

function versionLabel(v: EngineVersion) {
  return `V${v.version}.${v.subVersion ?? 0}`
}

function coerce(v: string, valueType?: number): unknown {
  const t = (v ?? '').trim()
  if (t === '') return ''
  if (valueType === 1 || valueType === 4) {
    return /^-?\d+(\.\d+)?$/.test(t) ? Number(t) : t
  }
  if (valueType === 3) {
    if (t === 'true') return true
    if (t === 'false') return false
    return t
  }
  if (/^-?\d+(\.\d+)?$/.test(t)) return Number(t)
  if (t === 'true') return true
  if (t === 'false') return false
  return t
}

function buildSingleSample(): Record<string, unknown> | null {
  if (jsonMode.value) {
    try {
      const obj = JSON.parse(singleJson.value || '{}')
      if (typeof obj !== 'object' || obj === null || Array.isArray(obj)) {
        ElMessage.warning('Single sample must be a JSON object')
        return null
      }
      const known = applyFieldTypes(obj as Record<string, unknown>)
      const bad = Object.keys(obj).filter((k) => fieldIndex.value.size && !fieldIndex.value.has(k))
      if (bad.length) ElMessage.warning(`Unknown fields (treated as unknown variables by the engine): ${bad.join(', ')}`)
      return known
    } catch {
      ElMessage.error('Failed to parse JSON; check the format')
      return null
    }
  }
  const data: Record<string, unknown> = {}
  for (const f of fields.value) {
    const raw = formData[f.fieldEn]
    if (raw !== undefined && raw !== '') data[f.fieldEn] = coerce(raw, f.valueType)
  }
  if (!Object.keys(data).length) {
    ElMessage.warning('Fill in at least one indicator field')
    return null
  }
  return data
}

function buildBatchSamples(): Record<string, unknown>[] | null {
  try {
    const arr = JSON.parse(batchText.value || '[]')
    if (!Array.isArray(arr) || !arr.length) {
      ElMessage.warning('Enter at least one test sample (JSON array)')
      return null
    }
    const known = arr.map((x) => applyFieldTypes(x as Record<string, unknown>))
    const bad = new Set<string>()
    for (const x of arr) {
      for (const k of Object.keys(x as Record<string, unknown>)) {
        if (fieldIndex.value.size && !fieldIndex.value.has(k)) bad.add(k)
      }
    }
    if (bad.size) ElMessage.warning(`Unknown fields (treated as unknown variables by the engine): ${[...bad].join(', ')}`)
    return known
  } catch {
    ElMessage.error('Sample is not a valid JSON array')
    return null
  }
}

async function runSingle() {
  const sample = buildSingleSample()
  if (!sample || !engineCode.value) return
  running.value = true
  try {
    const res = await runDecisionTest({
      engineCode: engineCode.value,
      versionId: versionId.value,
      samples: [sample],
      withTrace: true
    })
    singleResult.value = res.details?.[0]
    if (!singleResult.value) ElMessage.warning('No result returned')
  } catch (e: any) {
    ElMessage.error(e?.message || 'Test failed')
  } finally {
    running.value = false
  }
}

async function runBatch() {
  const samples = buildBatchSamples()
  if (!samples || !engineCode.value) return
  running.value = true
  try {
    const res = await runDecisionTest({
      engineCode: engineCode.value,
      versionId: versionId.value,
      samples,
      withTrace: batchTrace.value
    })
    batchSummary.value = res
    ElMessage.success(`Batch test completed: ${res.total} samples`)
  } catch (e: any) {
    ElMessage.error(e?.message || 'Test failed')
  } finally {
    running.value = false
  }
}

function rowClass(r: ResultSetVO) {
  return resultClass(r)
}

function fillSingleExample() {
  jsonMode.value = true
  singleJson.value = JSON.stringify(
    {
      applicant_age: 34,
      helix_score: 760,
      helix_overdue_cnt: 0,
      monthly_income: 22000,
      blacklist_hit_flag: 0
    },
    null,
    2
  )
}

function fillBatchExample() {
  batchText.value = JSON.stringify(
    [
      { applicant_age: 34, helix_score: 760, helix_overdue_cnt: 0, monthly_income: 22000, blacklist_hit_flag: 0 },
      { applicant_age: 26, helix_score: 660, helix_overdue_cnt: 0, monthly_income: 4200, blacklist_hit_flag: 0 }
    ],
    null,
    2
  )
}

function downloadTemplate() {
  downloadDataTemplate(engineCode.value || undefined)
}

function openBatchDetail(r: ResultSetVO) {
  batchDetail.value = r
  batchDetailVisible.value = true
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.run {
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

  &__title {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
  }

  &__ctx {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
  }

  &__engine {
    width: 180px;
  }

  &__version {
    width: 140px;
  }

  &__main {
    flex: 1;
    overflow-y: auto;
    padding: var(--sp-5);
  }
}

.vopt {
  display: flex;
  align-items: center;
  gap: 8px;

  &__badge {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    border: 1px solid var(--c-border);
    border-radius: 8px;
    padding: 0 6px;
    line-height: 16px;

    &.is-live {
      color: #34a46f;
      border-color: #34a46f;
    }
  }
}

.field-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;

  .chip {
    border: 1px solid var(--c-border);
    border-radius: 10px;
    background: transparent;
    color: var(--c-text-secondary);
    font-size: var(--fs-2xs);
    padding: 1px 8px;
    cursor: pointer;
    transition: all var(--dur-fast) var(--ease-standard);

    &:hover {
      border-color: var(--c-primary);
      color: var(--c-primary);
      background: var(--c-primary-soft);
    }
  }
}

.field-warn {
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--c-warning, #e6a23c);
}

.panel {
  max-width: 920px;
  margin: 0 auto;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    margin-bottom: var(--sp-4);
  }

  &__switch {
    font-size: var(--fs-xs);
  }
}

.field {
  margin-bottom: var(--sp-4);

  &__label {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text-secondary);
    margin-bottom: 6px;
  }
}

.form {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);

  &__empty {
    grid-column: 1 / -1;
    color: var(--c-text-tertiary);
    font-size: var(--fs-xs);
    padding: var(--sp-3);
  }

  &__group-title {
    grid-column: 1 / -1;
    margin: var(--sp-2) 0 var(--sp-1);
    padding: 4px 10px;
    font-size: var(--fs-xs);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    background: var(--c-bg-soft);
    border-left: 3px solid var(--c-accent, #409eff);
    border-radius: 3px;
  }

  &__group-count {
    margin-left: 8px;
    font-weight: var(--fw-normal);
    color: var(--c-text-tertiary);
  }

  &__row {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__label {
    width: 180px;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    line-height: 1.3;
  }

  &__cn {
    font-size: var(--fs-xs);
    color: var(--c-text);
  }

  &__en {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }
}

.actions {
  display: flex;
  gap: var(--sp-2);
  margin: var(--sp-4) 0;
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
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &--primary {
    background: var(--c-primary);
    border-color: transparent;
    color: var(--c-text-inverse);

    &:hover:not(:disabled) {
      background: var(--c-primary-hover);
    }
  }
}

.link-btn {
  border: none;
  background: transparent;
  color: var(--c-primary);
  font-size: var(--fs-xs);
  cursor: pointer;
  padding: 0;

  &:hover {
    text-decoration: underline;
  }
}

.verdict {
  padding: var(--sp-4);
  border-radius: var(--r-md);
  text-align: center;
  margin-bottom: var(--sp-5);

  &.is-ok {
    background: var(--c-success-soft);
    color: var(--c-success);
  }

  &.is-no {
    background: var(--c-danger-soft);
    color: var(--c-danger);
  }

  &.is-warn {
    background: var(--c-warning-soft);
    color: var(--c-warning);
  }

  &__text {
    font-size: var(--fs-xl);
    font-weight: var(--fw-semibold);
    letter-spacing: var(--ls-tight);
  }

  &__sub {
    font-size: var(--fs-xs);
    opacity: 0.8;
    margin-top: 2px;
  }
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

.kv {
  margin: 0;

  &__row {
    display: flex;
    gap: var(--sp-3);
    padding: 6px 0;
    font-size: var(--fs-xs);
    border-bottom: 1px solid var(--c-separator);

    &:last-child {
      border-bottom: none;
    }

    dt {
      width: 72px;
      flex-shrink: 0;
      color: var(--c-text-tertiary);
    }

    dd {
      margin: 0;
      flex: 1;
      min-width: 0;
      color: var(--c-text);
      word-break: break-all;
    }
  }
}

.mono {
  font-family: var(--font-mono);
}

.json {
  margin: 0;
  padding: var(--sp-3);
  border-radius: var(--r-sm);
  background: var(--c-surface-sunken);
  border: 1px solid var(--c-border);
  font-family: var(--font-mono);
  font-size: var(--fs-2xs);
  line-height: var(--lh-relaxed);
  max-height: 260px;
  overflow: auto;
  color: var(--c-text-secondary);
}

.summary {
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--c-surface-sunken);
  border: 1px solid var(--c-border);
  margin-bottom: var(--sp-5);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
    margin-bottom: var(--sp-3);
  }

  &__cost {
    font-family: var(--font-mono);
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: var(--sp-2);
  }

  &__item {
    text-align: center;

    span {
      display: block;
      font-size: var(--fs-2xs);
      color: var(--c-text-tertiary);
      margin-bottom: 2px;
    }

    b {
      font-size: var(--fs-lg);
      font-weight: var(--fw-semibold);
      font-variant-numeric: tabular-nums;
      color: var(--c-text);
    }

    &.is-ok b {
      color: var(--c-success);
    }

    &.is-no b {
      color: var(--c-danger);
    }

    &.is-warn b {
      color: var(--c-warning);
    }
  }
}

.list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  cursor: pointer;
  box-shadow: var(--sh-xs);
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    border-color: var(--c-border-strong);

    .row__arrow {
      opacity: 1;
      transform: translateX(0);
    }
  }

  &__idx {
    width: 24px;
    flex-shrink: 0;
    text-align: center;
    font-size: var(--fs-2xs);
    font-family: var(--font-mono);
    color: var(--c-text-tertiary);
  }

  &__verdict {
    width: 64px;
    flex-shrink: 0;
    padding: 4px 0;
    border-radius: var(--r-xs);
    text-align: center;
    font-size: var(--fs-xs);
    font-weight: var(--fw-semibold);
  }

  &.is-ok &__verdict {
    background: var(--c-success-soft);
    color: var(--c-success);
  }

  &.is-no &__verdict {
    background: var(--c-danger-soft);
    color: var(--c-danger);
  }

  &.is-warn &__verdict {
    background: var(--c-warning-soft);
    color: var(--c-warning);
  }

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: 2px;
  }

  &__engine {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
  }

  &__trace {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &__meta {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &__score {
    width: 64px;
    flex-shrink: 0;
    text-align: right;

    &-label {
      display: block;
      font-size: var(--fs-2xs);
      color: var(--c-text-tertiary);
    }

    &-value {
      font-size: var(--fs-md);
      font-weight: var(--fw-semibold);
      font-variant-numeric: tabular-nums;
      color: var(--c-text);
    }
  }

  &__arrow {
    opacity: 0;
    transform: translateX(-4px);
    color: var(--c-text-tertiary);
    transition: all var(--dur-fast) var(--ease-standard);
  }
}
</style>
