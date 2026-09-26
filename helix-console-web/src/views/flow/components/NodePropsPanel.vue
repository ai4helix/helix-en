<template>
  <div class="props">
    <div class="props__head">
      <div class="props__ident">
        <div class="props__icon" :style="{ color: meta.color, background: `${meta.color}1a` }">
          <NodeIcon :name="meta.icon" :size="16" />
        </div>
        <div class="props__title-group">
          <div class="props__title">{{ meta.label }}</div>
          <div class="props__subtitle u-truncate">{{ form.nodeCode || 'Unsaved' }}</div>
        </div>
      </div>
    </div>

    <div class="props__body">
      <section class="sect">
        <div class="sect__label">Basic Info</div>
        <div class="field">
          <label class="field__label">Node Name</label>
          <el-input v-model="form.nodeName" placeholder="Name this node" @change="emitUpdate" />
        </div>
        <div class="field">
          <label class="field__label">Node Code</label>
          <el-input v-model="form.nodeCode" disabled />
        </div>
      </section>

      <section v-if="meta.type === NodeType.SANDBOX" class="sect">
        <div class="sect__label">Branching Config</div>
        <div class="field">
          <label class="field__label">
            Sandbox Ratio
            <span class="field__value">{{ ratio }}%</span>
          </label>
          <el-slider v-model="ratio" :min="0" :max="100" :step="5" @change="onRatioChange" />
          <div class="hint">This share of traffic goes to the sandbox branch, the rest to the main flow</div>
        </div>
      </section>

      <section v-if="meta.type === NodeType.DECISION" class="sect">
        <div class="sect__label">Decision Table</div>
        <div class="field">
          <el-select
            v-model="decisionTableId"
            placeholder="Not bound (use inline conditions)"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="t in dtableOptions"
              :key="t.id"
              :label="`${t.name} (${t.code})`"
              :value="t.id"
              :disabled="t.status !== 1"
            />
          </el-select>
          <div class="hint">When bound, execution follows the decision table (DMN mode); otherwise inline conditions and the default result apply</div>
        </div>
      </section>

      <section v-if="meta.type === NodeType.DECISION" class="sect">
        <div class="sect__label">Decision Result</div>
        <div class="field">
          <label class="field__label">Default Result</label>
          <el-radio-group v-model="decisionResult" class="radio-cards" @change="onDecisionChange">
            <label
              v-for="opt in resultOptions"
              :key="opt.value"
              class="radio-card"
              :class="{ 'is-on': decisionResult === opt.value }"
            >
              <input v-model="decisionResult" type="radio" :value="opt.value" @change="onDecisionChange" />
              <span class="radio-card__dot" :style="{ background: opt.color }" />
              <span class="radio-card__text">{{ opt.label }}</span>
            </label>
          </el-radio-group>
          <div class="hint">Used when no score bins are configured</div>
        </div>
      </section>

      <section v-if="showKnowledge" class="sect">
        <div class="sect__label">
          Knowledge Refs
          <span class="sect__count">{{ form.knowledge?.length || 0 }}</span>
        </div>
        <div v-if="form.knowledge?.length" class="chips">
          <span v-for="(k, i) in form.knowledge" :key="i" class="chip">
            <span class="chip__text u-truncate">{{ k.name || `#${k.knowledgeId}` }}</span>
            <button class="chip__x" @click="removeKnowledge(i)">
              <el-icon :size="11"><Close /></el-icon>
            </button>
          </span>
        </div>
        <button class="add-btn" @click="openPicker">
          <el-icon :size="13"><Plus /></el-icon>
          <span>Select {{ meta.label }}</span>
        </button>
      </section>

      <section v-if="showRuleLogic" class="sect">
        <div class="sect__label">
          Rule Logic
          <span class="sect__count">{{ ruleBriefs.length }}</span>
        </div>

        <div v-if="logicLoading && !ruleBriefs.length" class="rule-logic__loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>Loading rule logic…</span>
        </div>

        <template v-else>
          <div v-for="b in ruleBriefs" :key="b.id" class="rule-logic">
            <div class="rule-logic__head">
              <span class="rule-logic__name u-truncate" :title="b.name">{{ b.name }}</span>
              <span class="rule-logic__badge" :class="badgeOf(b).cls">{{ badgeOf(b).text }}</span>
            </div>
            <pre class="rule-logic__expr">{{ b.content || '(no expression generated yet)' }}</pre>
            <div class="rule-logic__foot">
              <span class="rule-logic__code">{{ b.code || `#${b.id}` }}</span>
              <button class="rule-logic__edit" @click="openRuleEdit(b.id)">
                <el-icon :size="11"><EditPen /></el-icon>
                <span>Edit</span>
              </button>
            </div>
          </div>
          <div v-if="!form.knowledge?.length" class="rule-logic__empty">
            No rules referenced
          </div>
        </template>
      </section>

      <section v-if="showListDb" class="sect">
        <div class="sect__label">List DB Refs</div>
        <div class="field">
          <el-select v-model="listDbIds" multiple filterable placeholder="Select List DBs" style="width: 100%">
            <el-option v-for="db in listDbs" :key="db.id" :label="db.listName" :value="db.id" />
          </el-select>
          <div class="hint">Hit test: the match field value appears in an active entry of any selected list</div>
        </div>
        <div class="field">
          <label class="field__label">Match Fields</label>
          <el-select
            v-model="matchFields"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="Select fields or type a variable name"
            style="width: 100%"
          >
            <el-option-group v-for="g in groupedFieldOptions" :key="g.name" :label="g.name">
              <el-option
                v-for="f in g.items"
                :key="f.fieldEn"
                :label="`${f.fieldCn} (${f.fieldEn})`"
                :value="f.fieldEn"
              />
            </el-option-group>
          </el-select>
          <div class="hint">Which input variables to compare against lists; if empty, falls back to the upstream "variable=1" flag semantics</div>
        </div>
      </section>

      <section class="sect">
        <div class="sect__label">Remarks / Script</div>
        <el-input
          v-model="form.nodeScript"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="Node description or execution script"
          @change="emitUpdate"
        />
      </section>
    </div>

    <div class="props__foot">
      <button class="danger-btn" @click="$emit('delete')">
        <el-icon :size="14"><Delete /></el-icon>
        <span>Delete Node</span>
      </button>
    </div>

    <el-dialog v-model="pickerVisible" :title="`Select ${meta.label}`" width="620px" append-to-body>
      <el-input
        v-model="keyword"
        placeholder="Search by name or code"
        clearable
        class="picker-search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="picker-list">
        <button
          v-for="row in filteredOptions"
          :key="row.id"
          class="picker-row"
          @click="pickKnowledge(row)"
        >
          <div class="picker-row__main">
            <div class="picker-row__name u-truncate">{{ row.name }}</div>
            <div class="picker-row__desc u-truncate">{{ row.description || '—' }}</div>
          </div>
          <span class="picker-row__code">{{ row.code }}</span>
        </button>
        <div v-if="!filteredOptions.length" class="picker-empty">No matches</div>
      </div>
    </el-dialog>

    <el-dialog v-model="ruleEditVisible" title="Edit Rule" width="720px" append-to-body :close-on-click-modal="false">
      <RuleEditor v-if="ruleEditVisible" ref="ruleEditorRef" :initial="ruleEditInitial" />
      <template #footer>
        <el-button @click="ruleEditVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="ruleEditSaving" @click="saveRuleEdit">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { NodeType, KnowledgeType, type NodeData } from '@/types/flow'
import { getNodeMeta, KNOWLEDGE_NODE_TYPES, LIST_DB_NODE_TYPES } from '../nodeMeta'
import {
  pageRules,
  pageScorecards,
  listRuleBriefs,
  getRule,
  updateRule,
  type RuleBrief,
  type RuleSavePayload,
  listFields
} from '@/api/knowledge'
import { listAvailableListDbs } from '@/api/datamanage'
import { listDTables } from '@/api/dtable'
import { groupByCatalog } from '@/utils/fieldGroup'
import NodeIcon from './NodeIcon.vue'
import RuleEditor from '@/views/knowledge/RuleEditor.vue'

const props = defineProps<{ data: NodeData }>()
const emit = defineEmits<{
  (e: 'update', partial: Partial<NodeData>): void
  (e: 'delete'): void
}>()

const form = reactive<NodeData>({ ...props.data, knowledge: props.data.knowledge ?? [] })
const meta = computed(() => getNodeMeta(props.data.nodeType ?? 0))

watch(
  () => props.data,
  (v) => Object.assign(form, v, { knowledge: v.knowledge ?? [] }),
  { deep: true }
)

const showKnowledge = computed(() => KNOWLEDGE_NODE_TYPES.includes(meta.value.type))
const showListDb = computed(() => LIST_DB_NODE_TYPES.includes(meta.value.type))


const showRuleLogic = computed(() => showKnowledge.value && meta.value.type !== NodeType.SCORECARD)

const briefCache = ref<Map<number, RuleBrief>>(new Map())
const logicLoading = ref(false)

const ruleIds = computed<number[]>(() =>
  (form.knowledge || [])
    .filter((k) => k.knowledgeType !== KnowledgeType.SCORECARD)
    .map((k) => k.knowledgeId)
    .filter((id): id is number => id != null)
)

const ruleBriefs = computed<RuleBrief[]>(() =>
  ruleIds.value.map((id) => briefCache.value.get(id)).filter((b): b is RuleBrief => !!b)
)

watch(
  ruleIds,
  async (ids) => {
    const missing = ids.filter((id) => !briefCache.value.has(id))
    if (!missing.length) return
    logicLoading.value = true
    try {
      const list = await listRuleBriefs(missing)
      const next = new Map(briefCache.value)
      list.forEach((b) => next.set(b.id, b))
      briefCache.value = next
    } catch {
    } finally {
      logicLoading.value = false
    }
  },
  { immediate: true }
)

function badgeOf(b: RuleBrief): { text: string; cls: string } {
  switch (b.resultTypeV2) {
    case 'DENY': return { text: 'Reject', cls: 'is-deny' }
    case 'MANUAL': return { text: 'Manual', cls: 'is-manual' }
    case 'ADD_SCORE': return { text: `+${b.scoreValue ?? ''}`.trim(), cls: 'is-score' }
    case 'SUB_SCORE': return { text: `-${Math.abs(b.scoreValue ?? 0)}`.trim(), cls: 'is-score' }
    case 'PASS': return { text: 'Pass', cls: 'is-pass' }
    default: return { text: '—', cls: 'is-none' }
  }
}


const ruleEditVisible = ref(false)
const ruleEditSaving = ref(false)
const ruleEditingId = ref<number | null>(null)
const ruleEditInitial = ref<Partial<RuleSavePayload>>({})
const ruleEditorRef = ref<InstanceType<typeof RuleEditor>>()

async function openRuleEdit(id: number) {
  try {
    const detail = await getRule(id)
    ruleEditInitial.value = { ...detail }
    ruleEditingId.value = id
    ruleEditVisible.value = true
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : 'Failed to load rule details')
  }
}

async function saveRuleEdit() {
  const editor = ruleEditorRef.value
  if (!editor) return
  const err = editor.validate()
  if (err) {
    ElMessage.warning(err)
    return
  }
  ruleEditSaving.value = true
  try {
    const payload = editor.getPayload()
    payload.engineId = undefined
    payload.organId = 1
    await updateRule({ ...payload, id: ruleEditingId.value! })
    ElMessage.success('Rule saved')
    ruleEditVisible.value = false
    const next = new Map(briefCache.value)
    next.delete(ruleEditingId.value!)
    briefCache.value = next
    const list = await listRuleBriefs([ruleEditingId.value!])
    const merged = new Map(briefCache.value)
    list.forEach((b) => merged.set(b.id, b))
    briefCache.value = merged
  } finally {
    ruleEditSaving.value = false
  }
}

const resultOptions = [
  { value: '1', label: 'Pass', color: '#30d158' },
  { value: '3', label: 'Manual Review', color: '#ff9f0a' },
  { value: '2', label: 'Reject', color: '#ff453a' }
]

const ratio = computed({
  get: () => Number((form.nodeJson as any)?.ratio ?? 50),
  set: (v: number) => {
    form.nodeJson = { ...(form.nodeJson || {}), ratio: v }
  }
})
function onRatioChange() {
  emitUpdate()
}

const decisionResult = computed({
  get: () => String((form.nodeJson as any)?.result ?? '3'),
  set: (v: string) => {
    form.nodeJson = { ...(form.nodeJson || {}), result: v }
  }
})
function onDecisionChange() {
  emitUpdate()
}

const dtableOptions = ref<Array<{ id: number; code: string; name: string; status: number }>>([])
const decisionTableId = computed({
  get: () => (form.nodeJson as any)?.decision_table_id ?? undefined,
  set: (v: number | undefined) => {
    const next = { ...((form.nodeJson as any) || {}) }
    if (v == null) {
      delete next.decision_table_id
    } else {
      next.decision_table_id = v
    }
    form.nodeJson = next
    emitUpdate()
  }
})

const listDbIds = computed({
  get: () => ((form.nodeJson as any)?.list_db_ids ?? []) as number[],
  set: (v: number[]) => {
    const next = { ...((form.nodeJson as any) || {}) }
    if (!v || v.length === 0) {
      delete next.list_db_ids
    } else {
      next.list_db_ids = v
    }
    form.nodeJson = next
    emitUpdate()
  }
})
const matchFields = computed({
  get: () => ((form.nodeJson as any)?.matchFields ?? []) as string[],
  set: (v: string[]) => {
    const next = { ...((form.nodeJson as any) || {}) }
    if (!v || v.length === 0) {
      delete next.matchFields
    } else {
      next.matchFields = v
    }
    form.nodeJson = next
    emitUpdate()
  }
})
const fieldOptions = ref<Array<{ fieldEn: string; fieldCn: string; catalogName?: string }>>([])
const groupedFieldOptions = computed(() => groupByCatalog(fieldOptions.value))
async function loadFieldOptions() {
  try {
    fieldOptions.value = await listFields()
  } catch {
    fieldOptions.value = []
  }
}
async function loadDTables() {
  try {
    dtableOptions.value = await listDTables()
  } catch {
    dtableOptions.value = []
  }
}

const listDbs = ref<Array<{ id: number; listName: string; listAttr?: string }>>([])

const pickerVisible = ref(false)
const keyword = ref('')
const pickerLoading = ref(false)
const knowledgeOptions = ref<Array<{ id: number; name: string; code: string; description?: string }>>([])

const filteredOptions = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return knowledgeOptions.value
  return knowledgeOptions.value.filter(
    (o) => o.name.toLowerCase().includes(k) || (o.code || '').toLowerCase().includes(k)
  )
})

async function openPicker() {
  pickerVisible.value = true
  pickerLoading.value = true
  keyword.value = ''
  try {
    if (meta.value.type === NodeType.SCORECARD) {
      const res = await pageScorecards({ pageNo: 1, pageSize: 200 })
      knowledgeOptions.value = res.records.map((s) => ({
        id: s.id,
        name: s.name,
        code: s.code || '',
        description: s.description
      }))
    } else {
      const res = await pageRules({ pageNo: 1, pageSize: 200 })
      knowledgeOptions.value = res.records.map((r) => ({
        id: r.id,
        name: r.name,
        code: r.code || '',
        description: r.description
      }))
    }
  } finally {
    pickerLoading.value = false
  }
}

async function loadListDbs() {
  if (!showListDb.value) return
  const type = meta.value.type === NodeType.WHITELIST ? 'w' : 'b'
  listDbs.value = await listAvailableListDbs(type)
}

onMounted(loadListDbs)
onMounted(loadDTables)
onMounted(loadFieldOptions)
watch(() => props.data.nodeType, loadListDbs)

function pickKnowledge(row: any) {
  const type = meta.value.type === NodeType.SCORECARD ? KnowledgeType.SCORECARD : KnowledgeType.RULE
  const list = form.knowledge ? [...form.knowledge] : []
  if (!list.some((k) => k.knowledgeId === row.id)) {
    list.push({ knowledgeId: row.id, knowledgeType: type, name: row.name, code: row.code })
    form.knowledge = list
    emitUpdate()
  }
  pickerVisible.value = false
  keyword.value = ''
}

function removeKnowledge(i: number) {
  if (form.knowledge) {
    form.knowledge.splice(i, 1)
    emitUpdate()
  }
}

function emitUpdate() {
  emit('update', {
    nodeName: form.nodeName,
    nodeScript: form.nodeScript,
    nodeJson: form.nodeJson,
    knowledge: form.knowledge
  })
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.props {
  display: flex;
  flex-direction: column;

  &__head {
    padding-bottom: var(--sp-4);
    margin-bottom: var(--sp-4);
    border-bottom: 1px solid var(--c-separator);
  }

  &__ident {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: var(--r-sm);
    flex-shrink: 0;
  }

  &__title-group {
    min-width: 0;
    flex: 1;
  }

  &__title {
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    letter-spacing: var(--ls-tight);
    color: var(--c-text);
    line-height: var(--lh-snug);
  }

  &__subtitle {
    font-size: var(--fs-xs);
    font-family: var(--font-mono);
    color: var(--c-text-tertiary);
    line-height: 1.4;
  }

  &__body {
    flex: 1;
  }

  &__foot {
    padding-top: var(--sp-4);
    margin-top: var(--sp-2);
    border-top: 1px solid var(--c-separator);
  }
}

.sect {
  margin-bottom: var(--sp-5);

  &__label {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    color: var(--c-text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    margin-bottom: var(--sp-3);
  }

  &__count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border-radius: var(--r-full);
    background: var(--c-primary);
    color: var(--c-text-inverse);
    font-size: 10px;
    font-weight: var(--fw-semibold);
    letter-spacing: 0;
  }
}

.field {
  margin-bottom: var(--sp-3);

  &__label {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text-secondary);
    margin-bottom: 6px;
  }

  &__value {
    font-family: var(--font-mono);
    font-size: var(--fs-xs);
    color: var(--c-primary);
    font-weight: var(--fw-semibold);
  }
}

.hint {
  font-size: var(--fs-2xs);
  color: var(--c-text-tertiary);
  line-height: var(--lh-normal);
  margin-top: 6px;
}

.radio-cards {
  display: flex;
  gap: 6px;
  width: 100%;
}

.radio-card {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 34px;
  border-radius: var(--r-sm);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);
  user-select: none;

  input {
    display: none;
  }

  &:hover {
    border-color: var(--c-border-strong);
    background: var(--c-fill-quaternary);
  }

  &.is-on {
    border-color: var(--c-primary);
    background: var(--c-primary-soft);
    box-shadow: 0 0 0 2px var(--c-primary-ring);
  }

  &__dot {
    width: 7px;
    height: 7px;
    border-radius: var(--r-full);
    flex-shrink: 0;
  }

  &__text {
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text);
    white-space: nowrap;
  }
}

.chips {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: var(--sp-2);
}

.chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px 6px 10px;
  border-radius: var(--r-sm);
  background: var(--c-fill-quaternary);
  border: 1px solid transparent;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-primary-soft);
    border-color: var(--c-primary-ring);
  }

  &__text {
    flex: 1;
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    color: var(--c-text);
  }

  &__x {
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
    flex-shrink: 0;
    transition: all var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-danger-soft);
      color: var(--c-danger);
    }
  }
}

.rule-logic {
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface);
  margin-bottom: 6px;
  overflow: hidden;

  &__head {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 10px 0;
  }

  &__name {
    flex: 1;
    min-width: 0;
    font-size: var(--fs-xs);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
  }

  &__badge {
    flex-shrink: 0;
    padding: 1px 7px;
    border-radius: var(--r-full);
    font-size: 10px;
    font-weight: var(--fw-semibold);
    letter-spacing: 0.02em;

    &.is-deny {
      background: var(--c-danger-soft);
      color: var(--c-danger);
    }

    &.is-manual {
      background: rgba(255, 159, 10, 0.12);
      color: var(--c-warning);
    }

    &.is-score {
      background: var(--c-primary-soft);
      color: var(--c-primary);
    }

    &.is-pass {
      background: var(--c-success-soft);
      color: var(--c-success);
    }

    &.is-none {
      background: var(--c-fill-quaternary);
      color: var(--c-text-tertiary);
    }
  }

  &__expr {
    margin: 6px 10px 0;
    padding: 7px 9px;
    border-radius: var(--r-xs);
    background: var(--c-surface-sunken);
    font-family: var(--font-mono);
    font-size: 11px;
    line-height: 1.55;
    color: var(--c-text);
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 132px;
    overflow-y: auto;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 5px 10px 7px;
  }

  &__code {
    font-family: var(--font-mono);
    font-size: 10px;
    color: var(--c-text-tertiary);
  }

  &__edit {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    height: 20px;
    padding: 0 7px;
    border: none;
    border-radius: var(--r-full);
    background: transparent;
    color: var(--c-primary);
    font-size: 11px;
    font-weight: var(--fw-medium);
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-primary-soft);
    }
  }

  &__loading {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: var(--sp-3) 0;
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
  }

  &__empty {
    padding: var(--sp-3) 0;
    text-align: center;
    font-size: var(--fs-xs);
    color: var(--c-text-quaternary);
  }
}

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  height: 32px;
  border-radius: var(--r-sm);
  border: 1px dashed var(--c-border-strong);
  background: transparent;
  color: var(--c-text-secondary);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    border-color: var(--c-primary);
    color: var(--c-primary);
    background: var(--c-primary-soft);
  }
}

.danger-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 32px;
  border-radius: var(--r-sm);
  border: 1px solid transparent;
  background: var(--c-danger-soft);
  color: var(--c-danger);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-danger);
    color: var(--c-text-inverse);
  }

  &:active {
    transform: scale(0.98);
  }
}

.picker-search {
  margin-bottom: var(--sp-3);
}

.picker-list {
  max-height: 340px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.picker-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 10px var(--sp-3);
  border: none;
  border-radius: var(--r-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-primary-soft);
  }

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__name {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
    line-height: var(--lh-snug);
  }

  &__desc {
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
    line-height: 1.4;
  }

  &__code {
    font-size: var(--fs-2xs);
    font-family: var(--font-mono);
    color: var(--c-text-tertiary);
    padding: 2px 6px;
    border-radius: var(--r-xs);
    background: var(--c-fill-quaternary);
    flex-shrink: 0;
  }
}

.picker-empty {
  padding: var(--sp-8) 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--c-text-tertiary);
}
</style>
