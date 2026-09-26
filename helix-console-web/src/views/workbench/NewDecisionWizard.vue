<template>
  <el-dialog
    v-model="visible"
    title="New Decision"
    width="560px"
    :close-on-click-modal="false"
    @closed="resetAll"
  >
    <div class="map">
      <span v-for="(s, i) in FULL_CHAIN" :key="s" class="map__item" :class="mapClass(i)">
        {{ i + 1 }}. {{ s }}
      </span>
    </div>

    <el-steps :active="step" align-center finish-status="success" class="steps">
      <el-step title="Basic Info" />
      <el-step title="Indicator Fields" />
      <el-step title="Create Flow" />
      <el-step title="Done" />
    </el-steps>

    <div v-if="step === 0" class="body">
      <div class="tip">Name this decision. After creation you can drag nodes and bind rules in the flow designer.</div>
      <el-form label-width="84px" label-position="left">
        <el-form-item label="Name" required>
          <el-input v-model="form.name" placeholder="e.g. Consumer loan approval" maxlength="50" />
        </el-form-item>
        <el-form-item label="Code" required>
          <el-input v-model="form.code" placeholder="Starts with a letter, e.g. TD_LOAN_V2" maxlength="40">
            <template #append>
              <button class="gen" type="button" title="Generate from name" @click="genCode">Auto</button>
            </template>
          </el-input>
          <div class="form-hint">Globally unique, used by API callers; uppercase letters + underscores recommended</div>
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" placeholder="Business context, applicable scenarios (optional)" />
        </el-form-item>
      </el-form>
    </div>

    <div v-else-if="step === 1" class="body">
      <div class="tip">
        Indicator fields are the decision's <strong>input contract</strong> — the single-test form and batch data columns align with them.
      </div>
      <div class="fieldbox">
        <template v-if="fieldCount > 0">
          <div class="fieldbox__ok">
            <el-icon><CircleCheckFilled /></el-icon>
            <b>{{ fieldCount }}</b> indicator fields registered, ready to use
          </div>
          <div class="fieldbox__chips">
            <span v-for="f in fieldPreview" :key="f" class="chip">{{ f }}</span>
            <span v-if="fieldCount > fieldPreview.length" class="chip chip--more">+{{ fieldCount - fieldPreview.length }}</span>
          </div>
          <button class="link" @click="gotoFields">View / add fields in Field Management →</button>
        </template>
        <template v-else>
          <div class="fieldbox__none">
            <el-icon><WarningFilled /></el-icon>
            No indicator fields yet. You can create the flow first and define them later, or import now.
          </div>
          <div class="fieldbox__acts">
            <button class="opt" @click="gotoFields">
              <b>Define indicator fields</b><span>Go to the batch import page, download the template, fill by column and upload</span>
            </button>
          </div>
        </template>
      </div>
    </div>

    <div v-else-if="step === 2" class="body">
      <div class="tip">Confirm the info and click create; this generates an <b>engine + v1 draft version</b> (draft-only until published).</div>
      <div class="review">
        <div class="review__row"><span>Engine Name</span><b>{{ form.name }}</b></div>
        <div class="review__row"><span>Engine Code</span><b class="mono">{{ form.code }}</b></div>
        <div class="review__row"><span>Indicator Fields</span><b>{{ fieldCount > 0 ? `${fieldCount} registered` : 'None (add later)' }}</b></div>
        <div v-if="form.description" class="review__row"><span>Description</span><b>{{ form.description }}</b></div>
      </div>
      <div v-if="creating" class="creating">
        <el-icon class="is-loading"><Loading /></el-icon>
        Creating engine and version…
      </div>
    </div>

    <div v-else class="body">
      <div class="done">
        <el-icon class="done__icon"><CircleCheckFilled /></el-icon>
        <div class="done__title">Created</div>
        <div class="done__sub">
          Engine <b class="mono">{{ form.code }}</b> and draft version v1 are ready
        </div>
      </div>
      <div class="outs">
        <button class="opt opt--primary" @click="gotoDesigner">
          <b>Configure the flow in the Designer</b><span>Recommended: drag nodes (blacklist/policy/scorecard/decision), bind rules, then publish</span>
        </button>
        <button class="opt" @click="gotoKnowledge">
          <b>Configure rule knowledge first</b><span>Create rules and scorecards, then reference them directly in the designer</span>
        </button>
        <button class="opt" @click="closeAll">
          <b>Later</b><span>Saved as draft; continue anytime from the Workbench</span>
        </button>
      </div>
      <div class="remind">
        After publishing, return to Run Center for <strong>single</strong> or <strong>batch tests</strong>,
        always anchored to this decision flow version.
      </div>
    </div>

    <template #footer>
      <div class="footer">
        <el-button v-if="step > 0 && step < 3" @click="step--">Previous</el-button>
        <span class="footer__space" />
        <el-button v-if="step < 2" type="primary" :disabled="!canNext" @click="step++">
          Next
        </el-button>
        <el-button v-if="step === 2" type="primary" :disabled="creating" @click="doCreate">
          Create Engine and Version
        </el-button>
        <el-button v-if="step === 3" type="primary" @click="gotoDesigner">Go to Designer</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createEngine, createEngineVersion } from '@/api/flow'
import { listAllFields } from '@/api/datamanage'


const emit = defineEmits<{ (e: 'created', payload: { engineId: number; versionId: number; code: string }): void }>()

const visible = defineModel<boolean>({ default: false })
const router = useRouter()

const FULL_CHAIN = ['Indicator Fields', 'Rule Knowledge', 'Decision Flow', 'Run']

const step = ref(0)
const creating = ref(false)
const fieldCount = ref(0)
const fieldPreview = ref<string[]>([])
const created = reactive({ engineId: 0, versionId: 0 })

const form = reactive({
  name: '',
  code: '',
  description: ''
})

const canNext = computed(() => {
  if (step.value === 0) {
    return form.name.trim().length > 0 && /^[A-Za-z][A-Za-z0-9_]{1,39}$/.test(form.code.trim())
  }
  return true
})

watch(visible, async (v) => {
  if (!v) return
  step.value = 0
  try {
    const fields = await listAllFields()
    fieldCount.value = fields.length
    fieldPreview.value = fields.slice(0, 8).map((f) => f.fieldEn)
  } catch {
    fieldCount.value = 0
  }
})

function mapClass(i: number) {
  if (i === 0 || i === 2) return step.value < 3 ? 'is-current' : 'is-done'
  return 'is-todo'
}

function genCode() {
  const base = form.name.trim().replace(/[^0-9A-Za-z\u4e00-\u9fa5]/g, '')
  const ascii = base.replace(/[^0-9A-Za-z]/g, '').toUpperCase()
  const seed = ascii.length >= 3 ? ascii.slice(0, 12) : 'DECISION'
  form.code = `${seed}_${Date.now().toString(36).toUpperCase()}`
}

function gotoFields() {
  visible.value = false
  router.push('/batch')
}

async function doCreate() {
  if (!form.name.trim() || !form.code.trim()) return
  creating.value = true
  try {
    const engineId = await createEngine({
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim() || undefined
    })
    const versionId = await createEngineVersion(engineId)
    created.engineId = engineId
    created.versionId = versionId
    step.value = 3
    emit('created', { engineId, versionId, code: form.code.trim() })
  } catch (e: any) {
    ElMessage.error(e?.message || 'Creation failed; check whether the code is duplicated')
  } finally {
    creating.value = false
  }
}

function gotoDesigner() {
  visible.value = false
  router.push({
    path: '/flow',
    query: {
      engineId: String(created.engineId),
      versionId: String(created.versionId)
    }
  })
}

function gotoKnowledge() {
  visible.value = false
  router.push('/knowledge')
}

function closeAll() {
  visible.value = false
}

function resetAll() {
  step.value = 0
  creating.value = false
  form.name = ''
  form.code = ''
  form.description = ''
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.map {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);

  &__item {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);

    &.is-current {
      color: var(--c-primary);
      font-weight: var(--fw-semibold);
    }

    &.is-done {
      color: var(--c-success);
    }
  }

  &__item + &__item::before {
    content: '→';
    margin-right: var(--sp-2);
    color: var(--c-border-strong);
  }
}

.steps {
  margin-bottom: var(--sp-5);
}

.body {
  min-height: 180px;
}

.tip {
  font-size: var(--fs-xs);
  color: var(--c-text-secondary);
  margin-bottom: var(--sp-4);
  line-height: 1.6;
}

.form-hint {
  font-size: var(--fs-2xs);
  color: var(--c-text-tertiary);
  line-height: 1.4;
  margin-top: 4px;
}

.gen {
  border: none;
  background: transparent;
  color: var(--c-primary);
  cursor: pointer;
  font-size: var(--fs-xs);

  &:hover {
    text-decoration: underline;
  }
}

.fieldbox {
  &__ok {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--c-success);
    font-size: var(--fs-sm);
    margin-bottom: var(--sp-3);
  }

  &__chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: var(--sp-3);
  }

  &__none {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--c-warning);
    font-size: var(--fs-sm);
    line-height: 1.6;
    margin-bottom: var(--sp-4);
  }

  &__acts {
    display: flex;
    gap: var(--sp-2);
  }
}

.chip {
  padding: 2px 10px;
  border-radius: 10px;
  background: var(--c-surface-sunken);
  border: 1px solid var(--c-border);
  font-family: var(--font-mono);
  font-size: var(--fs-2xs);
  color: var(--c-text-secondary);

  &--more {
    border-style: dashed;
  }
}

.link {
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

.review {
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  overflow: hidden;

  &__row {
    display: flex;
    padding: var(--sp-3) var(--sp-4);
    font-size: var(--fs-xs);
    border-bottom: 1px solid var(--c-separator);

    &:last-child {
      border-bottom: none;
    }

    span {
      width: 88px;
      flex-shrink: 0;
      color: var(--c-text-tertiary);
    }

    b {
      flex: 1;
      color: var(--c-text);
      word-break: break-all;
    }
  }
}

.creating {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--sp-3);
  color: var(--c-text-tertiary);
  font-size: var(--fs-xs);
}

.done {
  text-align: center;
  margin-bottom: var(--sp-5);

  &__icon {
    font-size: 40px;
    color: var(--c-success);
  }

  &__title {
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    margin-top: var(--sp-2);
  }

  &__sub {
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
    margin-top: 4px;
  }
}

.outs {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.opt {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  background: var(--c-surface);
  cursor: pointer;
  text-align: left;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    border-color: var(--c-primary);
  }

  b {
    font-size: var(--fs-sm);
    color: var(--c-text);
  }

  span {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &--primary {
    border-color: var(--c-primary);
    background: var(--c-primary-soft, #eaf4ff);

    &:hover {
      background: var(--c-primary-soft, #eaf4ff);
    }
  }
}

.remind {
  margin-top: var(--sp-4);
  padding: var(--sp-3);
  border-radius: var(--r-md);
  background: var(--c-surface-sunken);
  font-size: var(--fs-2xs);
  color: var(--c-text-secondary);
  line-height: 1.6;
}

.mono {
  font-family: var(--font-mono);
}

.footer {
  display: flex;
  align-items: center;

  &__space {
    flex: 1;
  }
}
</style>
