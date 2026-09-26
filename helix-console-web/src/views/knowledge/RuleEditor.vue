<template>
  <div class="rule-editor">
    <section class="sect">
      <div class="sect__label">Basic Info</div>
      <div class="grid">
        <div class="field">
          <label class="field__label">Rule Name <i>*</i></label>
          <el-input v-model="form.name" placeholder="e.g. Anti-fraud hit reject" />
        </div>
        <div class="field">
          <label class="field__label">Rule Code</label>
          <el-input v-model="form.code" placeholder="e.g. R_BL_ANTI_FRAUD" />
        </div>
      </div>
      <div class="field">
        <label class="field__label">Description</label>
        <el-input v-model="form.description" type="textarea" :rows="2" resize="none" placeholder="Describe the business meaning of this rule" />
      </div>
      <div class="field">
        <label class="field__label">Parent Folder <i>*</i></label>
        <el-tree-select
          v-model="form.parentId"
          :data="dirOptions"
          :props="{ label: 'name' }"
          node-key="id"
          check-strictly
          default-expand-all
          filterable
          placeholder="Select the folder this rule belongs to"
          style="width: 100%"
        />
      </div>
    </section>

    <section class="sect">
      <div class="sect__label">Action on Hit</div>
      <div class="field">
        <label class="field__label">Rule Type</label>
        <div class="seg-cards">
          <label
            v-for="opt in ruleTypeOptions"
            :key="opt.value"
            class="seg-card"
            :class="{ 'is-on': form.ruleType === opt.value }"
          >
            <input v-model="form.ruleType" type="radio" :value="opt.value" />
            <span class="seg-card__dot" :style="{ background: opt.color }" />
            <div class="seg-card__body">
              <div class="seg-card__title">{{ opt.label }}</div>
              <div class="seg-card__desc">{{ opt.desc }}</div>
            </div>
          </label>
        </div>
      </div>

      <div v-if="form.ruleType === 0" class="field">
        <label class="field__label">Audit Result</label>
        <el-select v-model="form.ruleAudit" style="width: 100%">
          <el-option :value="2" label="Reject — reject the application directly" />
          <el-option :value="3" label="Manual Review — route to manual review" />
        </el-select>
      </div>

      <div v-else class="grid">
        <div class="field">
          <label class="field__label">Score</label>
          <el-input-number v-model="form.score" :min="-100" :max="100" style="width: 100%" />
        </div>
        <div class="field">
          <label class="field__label">Audit Result</label>
          <el-select v-model="form.ruleAudit" style="width: 100%">
            <el-option :value="4" label="Simplified Flow" />
            <el-option :value="5" label="Pass" />
            <el-option :value="3" label="Manual Review" />
          </el-select>
        </div>
      </div>

      <div class="field">
        <label class="field__label">Priority <span class="hint-inline">lower value executes first</span></label>
        <el-input-number v-model="form.priority" :min="1" :max="9999" style="width: 100%" />
      </div>
    </section>

    <section class="sect">
      <div class="sect__label">
        Trigger Conditions
        <span class="sect__count">{{ form.conditions.length }}</span>
        <div class="view-toggle">
          <button type="button" :class="{ on: conditionView === 'list' }" @click="switchView('list')">List Editor</button>
          <button type="button" :class="{ on: conditionView === 'tree' }" :disabled="!ruleId" @click="switchView('tree')">Condition Tree</button>
        </div>
      </div>

      <template v-if="conditionView === 'list'">
      <div v-if="form.conditions.length" class="conds">
        <div v-for="(c, i) in form.conditions" :key="i" class="cond">
          <div class="cond__logic">
            <template v-if="i > 0">
              <button
                class="logic-btn"
                :class="{ 'is-or': c.logical === '||' }"
                @click="toggleLogic(i)"
              >
                {{ c.logical === '||' ? 'OR' : 'AND' }}
              </button>
            </template>
            <span v-else class="logic-btn is-first">IF</span>
          </div>

          <div class="cond__body">
            <el-select
              v-model="c.fieldId"
              filterable
              placeholder="Select field"
              class="cond__field"
              @change="onFieldChange(c)"
            >
              <el-option-group v-for="g in groupedFields" :key="g.name" :label="g.name">
                <el-option
                  v-for="f in g.items"
                  :key="f.id"
                  :label="`${f.fieldCn} (${f.fieldEn})`"
                  :value="`${f.id}|${f.fieldEn}`"
                >
                  <div class="opt">
                    <span>{{ f.fieldCn }}</span>
                    <span class="opt__code">{{ f.fieldEn }}</span>
                  </div>
                </el-option>
              </el-option-group>
            </el-select>

            <el-select v-model="c.operator" class="cond__op">
              <el-option v-for="op in operators" :key="op.value" :label="op.label" :value="op.value" />
            </el-select>

            <span v-if="isNoValueOp(c.operator)" class="cond__novalue">—</span>
            <el-select
              v-else-if="isMultiValueOp(c.operator)"
              class="cond__value"
              :model-value="splitTags(c.fieldValue)"
              multiple
              filterable
              allow-create
              default-first-option
              no-data-text="Type a value and press Enter"
              placeholder="Type and press Enter"
              @update:model-value="(v: string[]) => (c.fieldValue = v.join(','))"
            />
            <template v-else-if="c.operator === 'between'">
              <el-input
                class="cond__value"
                :model-value="betweenPart(c, 0)"
                placeholder="Min"
                @update:model-value="(v: string) => setBetween(c, 0, v)"
              />
              <span class="cond__tilde">~</span>
              <el-input
                class="cond__value"
                :model-value="betweenPart(c, 1)"
                placeholder="Max"
                @update:model-value="(v: string) => setBetween(c, 1, v)"
              />
            </template>
            <el-input
              v-else
              v-model="c.fieldValue"
              class="cond__value"
              :placeholder="valuePlaceholder(c)"
            />

            <button class="icon-btn" title="Delete this condition" @click="removeCondition(i)">
              <el-icon :size="13"><Close /></el-icon>
            </button>
          </div>
        </div>
      </div>

      <div v-else class="conds-empty">No conditions configured yet; the rule will hit unconditionally</div>

      <div class="cond-actions">
        <button class="add-btn" @click="addCondition">
          <el-icon :size="13"><Plus /></el-icon>
          <span>Add Condition</span>
        </button>
        <el-checkbox v-model="isNonBool" class="cond-actions__not">Invert whole group (NOT)</el-checkbox>
      </div>
      </template>

      <div v-else class="cond-tree">
        <div v-if="treeError" class="cond-tree__hint">{{ treeError }}</div>
        <template v-else>
          <div ref="treeBox" class="cond-tree__canvas"></div>
          <div class="cond-tree__legend">
            <span><i class="dot is-hit"></i>Hit</span>
            <span><i class="dot is-miss"></i>Miss</span>
            <span><i class="dot is-unknown"></i>Unknown operator</span>
          </div>
        </template>
      </div>
    </section>

    <section v-if="previewExpression" class="sect">
      <div class="sect__label">
        Generated Expression
        <button class="dry-btn" @click="openDryRun">
          <el-icon :size="12"><VideoPlay /></el-icon>
          <span>Dry Run</span>
        </button>
      </div>
      <pre class="expr">{{ previewExpression }}</pre>
      <div class="hint">The expression is generated by the backend from the conditions; the engine evaluates accordingly</div>
    </section>

    <el-dialog v-model="dryVisible" title="Rule Dry Run" width="560px" :close-on-click-modal="false">
      <div class="dry">
        <div class="dry__hint">
          Variable rows are prefilled from the fields used in the conditions; fill in values and run. Evaluation happens in the production engine, matching real execution results.
        </div>
        <div class="dry__rows">
          <div v-for="(row, i) in dryVars" :key="i" class="dry__row">
            <el-select v-model="row.key" filterable placeholder="Variable (field English name)" class="dry__key">
              <el-option-group v-for="g in groupedFields" :key="g.name" :label="g.name">
                <el-option v-for="f in g.items" :key="f.id" :label="f.fieldEn" :value="f.fieldEn">
                  <div class="opt">
                    <span>{{ f.fieldEn }}</span>
                    <span class="opt__code">{{ f.fieldCn }}</span>
                  </div>
                </el-option>
              </el-option-group>
            </el-select>
            <el-input v-model="row.value" placeholder="Value" class="dry__val" />
            <button class="icon-btn" title="Delete" @click="dryVars.splice(i, 1)">
              <el-icon :size="13"><Close /></el-icon>
            </button>
          </div>
        </div>
        <button class="add-btn" @click="dryVars.push({ key: '', value: '' })">
          <el-icon :size="13"><Plus /></el-icon>
          <span>Add Variable</span>
        </button>

        <div v-if="dryResult" class="dry__result">
          <div class="dry__verdict" :class="dryResult.matched ? 'is-hit' : 'is-miss'">
            {{ dryResult.empty ? 'No conditions' : dryResult.matched ? 'Hit' : 'Miss' }}
            <span v-if="dryResult.inverted" class="dry__inv">Whole-group NOT applied</span>
          </div>
          <table v-if="dryResult.leaves.length" class="dry__table">
            <thead>
              <tr><th>Field</th><th>Operator</th><th>Expected</th><th>Actual</th><th>Result</th></tr>
            </thead>
            <tbody>
              <tr v-for="(l, i) in dryResult.leaves" :key="i">
                <td class="mono">{{ l.field }}</td>
                <td>{{ opLabel(l.operator) }}</td>
                <td class="mono">{{ l.value || '—' }}</td>
                <td class="mono" :class="{ 'is-null': l.actual === null || l.actual === undefined }">
                  {{ l.actual === null || l.actual === undefined ? '(missing)' : l.actual }}
                </td>
                <td>
                  <span class="dry__hit" :class="l.hit ? 'is-yes' : 'is-no'">{{ l.hit ? '✓' : '✗' }}</span>
                  <span v-if="l.unknownOperator" class="dry__warn">Unknown operator</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <template #footer>
        <el-button @click="dryVisible = false">Close</el-button>
        <el-button type="primary" :loading="dryLoading" @click="runDry">Run</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, ref, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { groupByCatalog } from '@/utils/fieldGroup'
import {
  listFields,
  getTree,
  dryRunRule,
  getRuleAst,
  type TreeNode,
  type DryRunResult,
  type FieldVO,
  type RuleCondition,
  type RuleSavePayload
} from '@/api/knowledge'
import type { RuleConditionNode } from '@/types/rule'
import { useConditionGraph, type NodeEval } from '@/composables/useConditionGraph'

const props = defineProps<{ initial?: Partial<RuleSavePayload> }>()

const fields = ref<FieldVO[]>([])
const groupedFields = computed(() => groupByCatalog(fields.value))
const isNonBool = ref(false)

const form = reactive<RuleSavePayload>({
  name: '',
  code: '',
  description: '',
  parentId: undefined,
  priority: 100,
  ruleType: 0,
  ruleAudit: 2,
  score: 0,
  isNon: 0,
  conditions: []
})

const ruleTypeOptions = [
  { value: 0, label: 'Hard Reject', desc: 'Stops the flow immediately on hit', color: '#ff453a' },
  { value: 1, label: 'Score Adjust', desc: 'On hit only affects the score', color: '#0a84ff' }
]

const operators = [
  { value: '==', label: 'Equal' },
  { value: '!=', label: 'Not Equal' },
  { value: '>', label: 'Greater Than' },
  { value: '>=', label: 'Greater or Equal' },
  { value: '<', label: 'Less Than' },
  { value: '<=', label: 'Less or Equal' },
  { value: 'in', label: 'In' },
  { value: 'notIn', label: 'Not In' },
  { value: 'contains', label: 'Contains' },
  { value: 'notContains', label: 'Not Contains' },
  { value: 'startsWith', label: 'Starts With' },
  { value: 'endsWith', label: 'Ends With' },
  { value: 'between', label: 'Between' },
  { value: 'isNull', label: 'Is Null' },
  { value: 'notNull', label: 'Not Null' }
]

const isNoValueOp = (op: string) => op === 'isNull' || op === 'notNull'
const isMultiValueOp = (op: string) => op === 'in' || op === 'notIn'

const dirTree = ref<TreeNode[]>([])

const dirOptions = computed<TreeNode[]>(() => {
  const strip = (nodes: TreeNode[]): TreeNode[] =>
    nodes.map((n) => ({
      ...n,
      children: n.children && n.children.length ? strip(n.children) : undefined
    }))
  return strip(dirTree.value)
})

onMounted(async () => {
  fields.value = await listFields()
  getTree(0)
    .then((tree) => (dirTree.value = tree || []))
    .catch(() => (dirTree.value = []))
  if (props.initial) {
    Object.assign(form, props.initial)
    isNonBool.value = props.initial.isNon === 1
    if (!form.conditions) form.conditions = []
  }
})

watch(isNonBool, (v) => {
  form.isNon = v ? 1 : 0
})

const previewExpression = computed(() => {
  if (!form.conditions.length) return ''
  const parts = form.conditions.map((c) => {
    const en = resolveFieldEn(c)
    if (c.operator === 'isNull') return `${en} == null`
    if (c.operator === 'notNull') return `${en} != null`
    const raw = (c.fieldValue || '').trim()
    if (c.operator === 'in' || c.operator === 'notIn') {
      const items = raw.split(',').map((s) => s.trim()).filter(Boolean).map(literal)
      const joined = `[${items.join(', ')}]`
      return c.operator === 'notIn' ? `!(${joined}.contains(${en}))` : `${joined}.contains(${en})`
    }
    if (c.operator === 'contains') return `${en} =~ '.*${raw}.*'`
    if (c.operator === 'notContains') return `!(${en} =~ '.*${raw}.*')`
    if (c.operator === 'startsWith') return `${en} =^ '${raw}'`
    if (c.operator === 'endsWith') return `${en} =$ '${raw}'`
    if (c.operator === 'between') {
      const [min, max] = raw.split(',')
      return `${en} >= ${literal((min || '').trim())} && ${en} <= ${literal((max || '').trim())}`
    }
    return `${en} ${c.operator} ${literal(raw)}`
  })

  const logicOf = (i: number) => (form.conditions[i].logical === '||' ? '||' : '&&')
  let hasOr = false
  let hasAnd = false
  for (let i = 0; i < form.conditions.length - 1; i++) {
    if (logicOf(i) === '||') hasOr = true
    else hasAnd = true
  }

  let expr: string
  if (hasOr && hasAnd) {
    const groups: string[] = []
    let cur = parts[0]
    for (let i = 0; i < form.conditions.length - 1; i++) {
      if (logicOf(i) === '||') {
        groups.push(cur)
        cur = parts[i + 1]
      } else {
        cur += ` && ${parts[i + 1]}`
      }
    }
    groups.push(cur)
    expr = groups.map((g) => (g.includes(' && ') ? `(${g})` : g)).join(' || ')
  } else {
    expr = parts[0]
    for (let i = 1; i < parts.length; i++) {
      expr += ` ${logicOf(i - 1)} ${parts[i]}`
    }
  }

  return form.isNon === 1 ? `!(${expr})` : expr
})

function literal(v: string) {
  if (/^-?\d+(\.\d+)?$/.test(v)) return v
  if (['true', 'false', 'null'].includes(v)) return v
  return `'${v}'`
}

function resolveFieldEn(c: RuleCondition) {
  if (c.fieldEn) return c.fieldEn
  const idx = c.fieldId?.indexOf('|') ?? -1
  return idx >= 0 ? c.fieldId.slice(idx + 1) : c.fieldId
}

function onFieldChange(c: RuleCondition) {
  c.fieldEn = resolveFieldEn(c)
}

function valuePlaceholder(c: RuleCondition) {
  if (isMultiValueOp(c.operator)) return 'Type and press Enter'
  if (isNoValueOp(c.operator)) return 'Not required'
  if (c.operator === 'between') return 'min,max'
  return 'Comparison value'
}

function splitTags(raw?: string): string[] {
  return (raw || '').split(',').map((s) => s.trim()).filter(Boolean)
}

function betweenPart(c: RuleCondition, idx: number): string {
  const parts = (c.fieldValue || '').split(',')
  return (parts[idx] || '').trim()
}

function setBetween(c: RuleCondition, idx: number, v: string) {
  const parts = [(c.fieldValue || '').split(',')[0] || '', (c.fieldValue || '').split(',')[1] || '']
  parts[idx] = v.trim()
  c.fieldValue = parts.join(',')
}


const dryVisible = ref(false)
const dryLoading = ref(false)
const dryVars = ref<Array<{ key: string; value: string }>>([])
const dryResult = ref<DryRunResult | null>(null)

function openDryRun() {
  const seen = new Set<string>()
  dryVars.value = []
  for (const c of form.conditions) {
    const en = resolveFieldEn(c)
    if (en && isNoValueOp(c.operator) === false && !seen.has(en)) {
      seen.add(en)
      dryVars.value.push({ key: en, value: '' })
    }
  }
  if (!dryVars.value.length) dryVars.value.push({ key: '', value: '' })
  dryResult.value = null
  dryVisible.value = true
}

async function runDry() {
  const variables: Record<string, unknown> = {}
  for (const r of dryVars.value) {
    const k = r.key.trim()
    if (!k) continue
    const v = r.value.trim()
    variables[k] = /^-?\d+(\.\d+)?$/.test(v) ? Number(v) : v
  }
  dryLoading.value = true
  try {
    dryResult.value = await dryRunRule({
      conditions: form.conditions.map((c) => ({ ...c, fieldEn: resolveFieldEn(c) })),
      isNon: form.isNon,
      variables
    })
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : 'Dry run failed')
  } finally {
    dryLoading.value = false
  }
}

function opLabel(raw: string): string {
  const hit = operators.find((o) => o.value === raw)
  if (hit) return hit.label
  const norm = raw.replace(/[_-]/g, '').toLowerCase()
  const hit2 = operators.find((o) => o.value.replace(/[_-]/g, '').toLowerCase() === norm)
  return hit2 ? hit2.label : raw
}

const conditionView = ref<'list' | 'tree'>('list')
const treeBox = ref<HTMLElement | null>(null)
const treeError = ref('')
const lastAst = ref<RuleConditionNode[]>([])
const ruleId = computed(() => props.initial?.id)
const g = useConditionGraph()
let mountedOnce = false

function buildEvals(): Record<string, NodeEval> | undefined {
  const leaves = dryResult.value?.leaves
  if (!leaves || !leaves.length) return undefined
  const m: Record<string, NodeEval> = {}
  for (const l of leaves) {
    m[l.field] = { hit: l.hit, actual: l.actual, unknownOperator: l.unknownOperator }
  }
  return m
}

async function renderTree() {
  if (!ruleId.value) {
    treeError.value = 'Save the rule to view the condition tree'
    return
  }
  treeError.value = ''
  await nextTick()
  try {
    if (!lastAst.value.length) lastAst.value = await getRuleAst(ruleId.value)
    if (!mountedOnce && treeBox.value) {
      g.mount(treeBox.value)
      mountedOnce = true
    }
    g.render(lastAst.value, buildEvals())
  } catch (e) {
    treeError.value = e instanceof Error ? e.message : 'Failed to load the condition tree'
  }
}

function disposeTree() {
  g.dispose()
  mountedOnce = false
}

function switchView(v: 'list' | 'tree') {
  if (v === conditionView.value) return
  conditionView.value = v
  if (v === 'tree') renderTree()
  else disposeTree()
}

watch(
  () => props.initial?.id,
  () => {
    lastAst.value = []
    treeError.value = ''
  }
)

watch(dryResult, () => {
  if (conditionView.value === 'tree' && ruleId.value && mountedOnce) {
    g.render(lastAst.value, buildEvals())
  }
})

onBeforeUnmount(disposeTree)

function addCondition() {
  form.conditions.push({
    logical: '&&',
    operator: '==',
    fieldId: '',
    fieldValue: ''
  } as RuleCondition)
}

function removeCondition(i: number) {
  form.conditions.splice(i, 1)
}

function toggleLogic(i: number) {
  const c = form.conditions[i]
  c.logical = c.logical === '||' ? '&&' : '||'
}

defineExpose({
  getPayload(): RuleSavePayload {
    return {
      ...form,
      conditions: form.conditions.map((c) => ({ ...c, fieldEn: resolveFieldEn(c) }))
    }
  },
  validate(): string | null {
    if (!form.name?.trim()) return 'Rule name is required'
    if (!form.parentId) return 'Please select a parent folder'
    if (form.ruleType === 1 && form.ruleAudit === 2) return 'A score-adjust rule cannot use the "Reject" audit result'
    for (let i = 0; i < form.conditions.length; i++) {
      const c = form.conditions[i]
      if (!c.fieldId) return `Condition ${i + 1}: no field selected`
      if (!c.operator) return `Condition ${i + 1}: no operator selected`
      if (isNoValueOp(c.operator)) continue
      if (c.operator === 'between') {
        const [min, max] = (c.fieldValue || '').split(',')
        if (!min?.trim() || !max?.trim()) return `Condition ${i + 1}: both min and max of the range are required`
        continue
      }
      if (!c.fieldValue?.trim()) {
        return `Condition ${i + 1}: comparison value is required`
      }
    }
    return null
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

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

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
}

.field {
  margin-bottom: var(--sp-3);

  &__label {
    display: flex;
    align-items: center;
    gap: 6px;
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

.hint-inline {
  font-size: var(--fs-2xs);
  color: var(--c-text-tertiary);
  font-weight: var(--fw-regular);
}

.hint {
  font-size: var(--fs-2xs);
  color: var(--c-text-tertiary);
  margin-top: 6px;
  line-height: var(--lh-normal);
}

.seg-cards {
  display: flex;
  gap: var(--sp-2);
}

.seg-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--r-md);
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

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
    width: 8px;
    height: 8px;
    border-radius: var(--r-full);
    flex-shrink: 0;
  }

  &__body {
    min-width: 0;
  }

  &__title {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
    line-height: var(--lh-snug);
  }

  &__desc {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    line-height: 1.4;
  }
}

.conds {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cond {
  display: flex;
  align-items: flex-start;
  gap: 8px;

  &__logic {
    width: 46px;
    flex-shrink: 0;
    display: flex;
    justify-content: center;
    padding-top: 4px;
  }

  &__body {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }

  &__field {
    flex: 1.3;
    min-width: 0;
  }

  &__op {
    width: 108px;
    flex-shrink: 0;
  }

  &__value {
    flex: 1;
    min-width: 0;
  }

  &__novalue {
    flex: 1;
    color: var(--c-text-quaternary);
    font-size: var(--fs-xs);
    text-align: center;
    user-select: none;
  }

  &__tilde {
    flex-shrink: 0;
    color: var(--c-text-tertiary);
    font-size: var(--fs-xs);
  }
}

.dry-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 20px;
  margin-left: auto;
  padding: 0 8px;
  border: none;
  border-radius: var(--r-full);
  background: var(--c-primary-soft);
  color: var(--c-primary);
  font-size: var(--fs-2xs);
  font-weight: var(--fw-medium);
  cursor: pointer;
  text-transform: none;
  letter-spacing: 0;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-primary);
    color: var(--c-text-inverse);
  }
}

.dry {
  &__hint {
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
    line-height: var(--lh-normal);
    margin-bottom: var(--sp-3);
  }

  &__rows {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-bottom: var(--sp-3);
  }

  &__row {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__key {
    flex: 1.2;
    min-width: 0;
  }

  &__val {
    flex: 1;
    min-width: 0;
  }

  &__result {
    margin-top: var(--sp-4);
  }

  &__verdict {
    display: inline-flex;
    align-items: center;
    gap: var(--sp-2);
    padding: 4px 12px;
    border-radius: var(--r-full);
    font-size: var(--fs-sm);
    font-weight: var(--fw-semibold);
    margin-bottom: var(--sp-3);

    &.is-hit {
      background: var(--c-danger-soft);
      color: var(--c-danger);
    }

    &.is-miss {
      background: var(--c-success-soft);
      color: var(--c-success);
    }
  }

  &__inv {
    font-size: var(--fs-2xs);
    font-weight: var(--fw-medium);
    opacity: 0.8;
  }

  &__table {
    width: 100%;
    border-collapse: collapse;
    font-size: var(--fs-xs);

    th {
      text-align: left;
      padding: 6px 8px;
      font-size: var(--fs-2xs);
      font-weight: var(--fw-semibold);
      color: var(--c-text-tertiary);
      background: var(--c-surface-sunken);
      border-bottom: 1px solid var(--c-separator);
      white-space: nowrap;
    }

    td {
      padding: 7px 8px;
      border-bottom: 1px solid var(--c-separator);
      color: var(--c-text);
      vertical-align: middle;
    }

    .mono {
      font-family: var(--font-mono);
      font-size: var(--fs-2xs);

      &.is-null {
        color: var(--c-text-quaternary);
      }
    }
  }

  &__hit {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: var(--r-full);
    font-size: 11px;
    font-weight: var(--fw-bold);

    &.is-yes {
      background: var(--c-danger-soft);
      color: var(--c-danger);
    }

    &.is-no {
      background: var(--c-fill-quaternary);
      color: var(--c-text-tertiary);
    }
  }

  &__warn {
    margin-left: 4px;
    font-size: var(--fs-2xs);
    color: var(--c-warning);
  }
}

.logic-btn {
  height: 22px;
  padding: 0 8px;
  border: none;
  border-radius: var(--r-full);
  background: var(--c-primary-soft);
  color: var(--c-primary);
  font-size: 10px;
  font-weight: var(--fw-bold);
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &.is-or {
    background: var(--c-warning-soft);
    color: var(--c-warning);
  }

  &.is-first {
    background: var(--c-fill-tertiary);
    color: var(--c-text-tertiary);
    cursor: default;
  }

  &:not(.is-first):hover {
    transform: scale(1.06);
  }
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border: none;
  border-radius: var(--r-xs);
  background: transparent;
  color: var(--c-text-tertiary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-danger-soft);
    color: var(--c-danger);
  }
}

.conds-empty {
  padding: var(--sp-4);
  border-radius: var(--r-sm);
  background: var(--c-fill-quaternary);
  font-size: var(--fs-xs);
  color: var(--c-text-tertiary);
  text-align: center;
}

.cond-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-3);

  &__not {
    font-size: var(--fs-xs);
  }
}

.view-toggle {
  margin-left: auto;
  display: inline-flex;
  border: 1px solid var(--c-border);
  border-radius: var(--r-full);
  overflow: hidden;

  button {
    height: 20px;
    padding: 0 12px;
    border: none;
    background: var(--c-surface);
    color: var(--c-text-tertiary);
    font-size: var(--fs-2xs);
    font-weight: var(--fw-medium);
    cursor: pointer;
    transition: all var(--dur-fast) var(--ease-standard);

    &.on {
      background: var(--c-primary);
      color: var(--c-text-inverse);
    }

    &:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  }
}

.cond-tree {
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: #f7f8fa;
  overflow: hidden;

  &__canvas {
    height: 380px;
    width: 100%;
  }

  &__hint {
    padding: var(--sp-4);
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
    text-align: center;
  }

  &__legend {
    display: flex;
    gap: var(--sp-3);
    padding: 8px 12px;
    border-top: 1px solid var(--c-separator);
    background: var(--c-surface);
    font-size: var(--fs-2xs);
    color: var(--c-text-secondary);

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .dot {
      width: 8px;
      height: 8px;
      border-radius: var(--r-full);

      &.is-hit {
        background: #52c41a;
      }
      &.is-miss {
        background: #f5222d;
      }
      &.is-unknown {
        background: #bfbfbf;
      }
    }
  }
}

.add-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 var(--sp-3);
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

.expr {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--c-surface-sunken);
  border: 1px solid var(--c-border);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  line-height: var(--lh-relaxed);
  color: var(--c-primary);
  white-space: pre-wrap;
  word-break: break-all;
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
