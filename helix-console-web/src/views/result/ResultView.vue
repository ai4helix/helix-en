<template>
  <div class="rs">
    <header class="rs__top">

      <div class="rs__actions">
        <el-select v-model="query.engineCode" placeholder="All engines" clearable class="rs__engine" @change="reload">
          <el-option v-for="e in engines" :key="e.id" :label="e.name" :value="e.code" />
        </el-select>
        <el-select v-model="query.result" placeholder="All results" clearable class="rs__result" @change="reload">
          <el-option label="Pass" value="1" />
          <el-option label="Reject" value="2" />
          <el-option label="Manual Review" value="3" />
        </el-select>
        <button class="btn" :disabled="!records.length" @click="openBatch">
          <el-icon><DataLine /></el-icon><span>Batch Test</span>
        </button>
      </div>
    </header>

    <main class="rs__main">
      <div class="stats">
        <div class="stat">
          <div class="stat__label">Total Calls</div>
          <div class="stat__value">{{ total }}</div>
        </div>
        <div class="stat">
          <div class="stat__label">Pass</div>
          <div class="stat__value is-ok">{{ countOf('pass') }}</div>
        </div>
        <div class="stat">
          <div class="stat__label">Reject</div>
          <div class="stat__value is-no">{{ countOf('reject') }}</div>
        </div>
        <div class="stat">
          <div class="stat__label">Manual Review</div>
          <div class="stat__value is-warn">{{ countOf('manual') }}</div>
        </div>
      </div>

      <div v-if="!records.length" class="rs__empty">
        <div class="rs__empty-icon"><el-icon :size="24"><DocumentRemove /></el-icon></div>
        <div class="rs__empty-title">No execution records</div>
        <div class="rs__empty-desc">Results will appear here after calls to the decision API</div>
      </div>

      <div v-else class="list">
        <article
          v-for="r in records"
          :key="r.id"
          class="row"
          :class="rowClass(r)"
          @click="openDetail(r)"
        >
          <div class="row__verdict">{{ resultLabel(r) }}</div>

          <div class="row__main">
            <div class="row__title">
              <span class="row__engine">{{ r.engineName || r.engineCode }}</span>
              <span v-if="r.batchNo" class="row__batch">{{ r.batchNo }}</span>
            </div>
            <div class="row__meta">
              <span>{{ formatTime(r.createdTime) }}</span>
              <span v-if="r.traceId" class="row__trace">{{ r.traceId.slice(0, 12) }}</span>
            </div>
          </div>

          <div class="row__score">
            <span class="row__score-label">Score</span>
            <span class="row__score-value">{{ r.score ?? 0 }}</span>
          </div>

          <div class="row__hits">
            <span v-if="r.hitDetails?.length" class="row__hit">{{ r.hitDetails.length }} hits</span>
            <span v-else class="row__hit is-none">No hits</span>
          </div>

          <el-icon class="row__arrow" :size="14"><ArrowRight /></el-icon>
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

    <el-drawer v-model="detailVisible" title="Decision Detail" size="640px">
      <div v-if="detail" class="detail">
        <div class="detail__verdict" :class="rowClass(detail)">
          <div class="detail__verdict-text">{{ detail.result }}</div>
          <div class="detail__verdict-sub">Total score {{ detail.score ?? 0 }}</div>
        </div>

        <section class="sect">
          <div class="sect__label">Basic Info</div>
          <dl class="kv">
            <div class="kv__row"><dt>Trace ID</dt><dd class="mono">{{ detail.traceId }}</dd></div>
            <div class="kv__row"><dt>Engine</dt><dd>{{ detail.engineName || detail.engineCode }}</dd></div>
            <div class="kv__row"><dt>Version</dt><dd>{{ detail.engineVersion ?? '—' }}</dd></div>
            <div class="kv__row"><dt>Call Time</dt><dd>{{ formatTime(detail.createdTime) }}</dd></div>
            <div v-if="detail.batchNo" class="kv__row"><dt>Batch No</dt><dd class="mono">{{ detail.batchNo }}</dd></div>
          </dl>
        </section>

        <section v-if="detail.traces?.length" class="sect">
          <div class="sect__label">Node Execution Trace</div>
          <div class="traces">
            <div
              v-for="(t, i) in detail.traces"
              :key="i"
              class="trace"
              :class="{ 'is-hit': t.hit }"
            >
              <div class="trace__idx" :class="{ 'is-hit': t.hit }">{{ i + 1 }}</div>
              <div class="trace__body">
                <div class="trace__head">
                  <span class="trace__name">{{ t.nodeName }}</span>
                  <span v-if="t.scoreDelta" class="trace__delta" :class="t.scoreDelta > 0 ? 'is-up' : 'is-down'">
                    {{ t.scoreDelta > 0 ? '+' : '' }}{{ t.scoreDelta }}
                  </span>
                </div>
                <div class="trace__msg">{{ t.message || '—' }}</div>
                <ul v-if="t.hitDetails?.length" class="trace__hits">
                  <li v-for="(h, j) in t.hitDetails" :key="j">{{ h }}</li>
                </ul>
              </div>
            </div>
          </div>
        </section>

        <section v-if="detail.input" class="sect">
          <div class="sect__label">Input</div>
          <pre class="json">{{ JSON.stringify(detail.input, null, 2) }}</pre>
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="batchVisible" title="Batch Test" width="760px" append-to-body>
      <div class="batch">
        <div class="field">
          <label class="field__label">Engine</label>
          <el-select v-model="batchForm.engineCode" style="width: 100%">
            <el-option v-for="e in engines" :key="e.id" :label="e.name" :value="e.code" />
          </el-select>
        </div>
        <div class="field">
          <label class="field__label">
            Test samples (JSON array, each item is one decision input)
            <button class="link-btn" @click="fillSample">Fill Example</button>
          </label>
          <el-input
            v-model="batchForm.samplesText"
            type="textarea"
            :rows="10"
            resize="none"
            placeholder='[{"applicant_age":34,"helix_score":760,...}]'
          />
        </div>
        <div v-if="batchResult" class="summary">
          <div class="summary__head">
            <span>Batch {{ batchResult.batchNo }}</span>
            <span class="summary__cost">{{ batchResult.costMs }}ms</span>
          </div>
          <div class="summary__grid">
            <div class="summary__item"><span>Total</span><b>{{ batchResult.total }}</b></div>
            <div class="summary__item is-ok"><span>Pass</span><b>{{ batchResult.passCount }}</b></div>
            <div class="summary__item is-no"><span>Reject</span><b>{{ batchResult.rejectCount }}</b></div>
            <div class="summary__item is-warn"><span>Manual</span><b>{{ batchResult.manualCount }}</b></div>
            <div class="summary__item"><span>Pass rate</span><b>{{ batchResult.passRate }}%</b></div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="drawer-foot">
          <button class="btn" @click="batchVisible = false">Close</button>
          <button class="btn btn--primary" :disabled="running" @click="runBatch">
            {{ running ? 'Running…' : 'Run Test' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listEngines } from '@/api/flow'
import type { Engine } from '@/types/flow'
import {
  pageResults, getResult, batchTest,
  type ResultSetVO, type ResultQuery, type BatchTestResultVO
} from '@/api/result'
import { resultClass, resultKind, resultLabel } from '@/utils/result'

const engines = ref<Engine[]>([])
const records = ref<ResultSetVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const query = reactive<ResultQuery>({
  engineCode: undefined,
  result: undefined
})

const detailVisible = ref(false)
const detail = ref<ResultSetVO>()

const batchVisible = ref(false)
const running = ref(false)
const batchResult = ref<BatchTestResultVO>()
const batchForm = reactive({ engineCode: '', samplesText: '' })

onMounted(async () => {
  engines.value = await listEngines()
  await reload()
})

async function reload() {
  const res = await pageResults({ ...query }, pageNo.value, pageSize.value)
  records.value = res.records
  total.value = res.total
}

function onPageChange(p: number) {
  pageNo.value = p
  reload()
}

function countOf(kind: 'pass' | 'reject' | 'manual') {
  return records.value.filter((r) => resultKind(r) === kind).length
}

function rowClass(r: ResultSetVO) {
  return resultClass(r)
}

function formatTime(t?: string) {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 19)
}

async function openDetail(r: ResultSetVO) {
  detailVisible.value = true
  detail.value = await getResult(r.id)
}

function openBatch() {
  batchResult.value = undefined
  batchForm.engineCode = query.engineCode || engines.value[0]?.code || ''
  batchForm.samplesText = ''
  batchVisible.value = true
}

function fillSample() {
  batchForm.samplesText = JSON.stringify(
    [
      {
        applicant_age: 34, applicant_education: 4, helix_score: 760,
        helix_query_3m: 1, helix_query_6m: 3, helix_overdue_cnt: 0,
        helix_overdue_max_days: 0, helix_card_usage_rate: 0.35,
        helix_history_months: 60, loan_org_cnt: 2, monthly_income: 22000,
        monthly_debt: 4000, social_security_months: 36, multi_loan_cnt_30d: 1,
        night_apply_flag: 0, device_risk_score: 20, blacklist_hit_flag: 0, fraud_score: 12
      },
      {
        applicant_age: 26, applicant_education: 2, helix_score: 660,
        helix_query_3m: 11, helix_query_6m: 18, helix_overdue_cnt: 0,
        helix_overdue_max_days: 0, helix_card_usage_rate: 0.85,
        helix_history_months: 30, loan_org_cnt: 6, monthly_income: 4200,
        monthly_debt: 1500, social_security_months: 3, multi_loan_cnt_30d: 5,
        night_apply_flag: 1, device_risk_score: 75, blacklist_hit_flag: 0, fraud_score: 35
      },
      {
        applicant_age: 40, helix_score: 700, helix_overdue_cnt: 0,
        helix_overdue_max_days: 0, helix_history_months: 48, loan_org_cnt: 3,
        monthly_income: 15000, monthly_debt: 5000, social_security_months: 24,
        multi_loan_cnt_30d: 2, night_apply_flag: 0, device_risk_score: 30,
        blacklist_hit_flag: 0, fraud_score: 92
      }
    ],
    null,
    2
  )
}

async function runBatch() {
  if (!batchForm.engineCode) {
    ElMessage.warning('Select an engine')
    return
  }
  let samples: Array<Record<string, any>>
  try {
    samples = JSON.parse(batchForm.samplesText || '[]')
    if (!Array.isArray(samples) || !samples.length) {
      ElMessage.warning('Enter at least one test sample')
      return
    }
  } catch {
    ElMessage.error('Sample is not a valid JSON array')
    return
  }
  running.value = true
  try {
    batchResult.value = await batchTest({ engineCode: batchForm.engineCode, samples })
    ElMessage.success('Batch test completed')
    await reload()
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.rs {
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

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__engine {
    width: 180px;
  }

  &__result {
    width: 130px;
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
    height: 60%;
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

    &:hover:not(:disabled) {
      background: var(--c-primary-hover);
    }
  }
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--sp-3);
  margin-bottom: var(--sp-5);
}

.stat {
  padding: var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  box-shadow: var(--sh-xs);

  &__label {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    margin-bottom: 4px;
  }

  &__value {
    font-size: var(--fs-2xl);
    font-weight: var(--fw-semibold);
    letter-spacing: var(--ls-tight);
    font-variant-numeric: tabular-nums;
    color: var(--c-text);

    &.is-ok {
      color: var(--c-success);
    }

    &.is-no {
      color: var(--c-danger);
    }

    &.is-warn {
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
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  cursor: pointer;
  box-shadow: var(--sh-xs);
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    border-color: var(--c-border-strong);
    box-shadow: var(--sh-sm);
    transform: translateX(2px);

    .row__arrow {
      opacity: 1;
      transform: translateX(0);
    }
  }

  &__verdict {
    width: 68px;
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

  &__batch {
    font-size: var(--fs-2xs);
    font-family: var(--font-mono);
    color: var(--c-text-tertiary);
    padding: 1px 6px;
    border-radius: var(--r-xs);
    background: var(--c-fill-quaternary);
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &__trace {
    font-family: var(--font-mono);
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

  &__hits {
    width: 90px;
    flex-shrink: 0;
    text-align: right;
  }

  &__hit {
    font-size: var(--fs-2xs);
    color: var(--c-danger);
    font-weight: var(--fw-medium);

    &.is-none {
      color: var(--c-text-quaternary);
      font-weight: var(--fw-regular);
    }
  }

  &__arrow {
    opacity: 0;
    transform: translateX(-4px);
    color: var(--c-text-tertiary);
    transition: all var(--dur-fast) var(--ease-standard);
  }
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-6);
}

.detail {
  &__verdict {
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

    &-text {
      font-size: var(--fs-xl);
      font-weight: var(--fw-semibold);
      letter-spacing: var(--ls-tight);
    }

    &-sub {
      font-size: var(--fs-xs);
      opacity: 0.8;
      margin-top: 2px;
    }
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

.traces {
  display: flex;
  flex-direction: column;
}

.trace {
  display: flex;
  gap: var(--sp-3);
  padding-bottom: var(--sp-4);
  position: relative;

  &:not(:last-child)::before {
    content: '';
    position: absolute;
    left: 10px;
    top: 24px;
    bottom: 0;
    width: 1px;
    background: var(--c-border);
  }

  &__idx {
    width: 21px;
    height: 21px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--r-full);
    background: var(--c-fill-tertiary);
    color: var(--c-text-tertiary);
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    z-index: 1;

    &.is-hit {
      background: var(--c-primary);
      color: var(--c-text-inverse);
    }
  }

  &__body {
    flex: 1;
    min-width: 0;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
  }

  &__name {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
  }

  &__delta {
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    font-family: var(--font-mono);

    &.is-up {
      color: var(--c-success);
    }

    &.is-down {
      color: var(--c-danger);
    }
  }

  &__msg {
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
    line-height: var(--lh-normal);
  }

  &__hits {
    margin: 4px 0 0;
    padding-left: 14px;
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);

    li {
      list-style: disc;
      line-height: 1.7;
    }
  }
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

.summary {
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--c-surface-sunken);
  border: 1px solid var(--c-border);

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

.drawer-foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}
</style>
