<template>
  <div class="sc-editor">
    <section class="sect">
      <div class="sect__label">Basic Info</div>
      <div class="grid">
        <div class="field">
          <label class="field__label">Scorecard Name <i>*</i></label>
          <el-input v-model="form.name" placeholder="e.g. Credit Composite Scorecard" />
        </div>
        <div class="field">
          <label class="field__label">Code</label>
          <el-input v-model="form.code" placeholder="e.g. SC_HELIX_COMPOSITE" />
        </div>
      </div>
      <div class="grid">
        <div class="field">
          <label class="field__label">Version</label>
          <el-input v-model="form.version" placeholder="e.g. v1.0" />
        </div>
        <div class="field">
          <label class="field__label">
            Total Score
            <span class="total" :class="{ 'is-warn': totalScore > 100 }">{{ totalScore }} pts</span>
          </label>
          <div class="hint-inline">Auto-summed from each dimension's bin scores</div>
        </div>
      </div>
      <div class="field">
        <label class="field__label">Description</label>
        <el-input v-model="form.description" type="textarea" :rows="2" resize="none" />
      </div>
    </section>

    <section class="sect">
      <div class="sect__label">
        Scoring Dimensions
        <span class="sect__count">{{ form.dimensions.length }}</span>
      </div>

      <div v-for="(dim, di) in form.dimensions" :key="di" class="dim">
        <div class="dim__head">
          <span class="dim__idx">{{ di + 1 }}</span>
          <el-select
            v-model="dim.field"
            filterable
            placeholder="Select dimension field"
            class="dim__field"
            @change="onDimFieldChange(dim)"
          >
            <el-option-group v-for="g in groupedFields" :key="g.name" :label="g.name">
              <el-option
                v-for="f in g.items"
                :key="f.id"
                :label="`${f.fieldCn} (${f.fieldEn})`"
                :value="f.fieldEn"
              />
            </el-option-group>
          </el-select>
          <div class="dim__weight">
            <span class="dim__weight-label">Weight</span>
            <el-input-number v-model="dim.weight" :min="0" :max="100" :controls="false" />
          </div>
          <button class="icon-btn" title="Delete Dimension" @click="removeDimension(di)">
            <el-icon :size="13"><Close /></el-icon>
          </button>
        </div>

        <div class="bins">
          <div class="bins__head">
            <span class="bins__col bins__col--min">Lower (incl.)</span>
            <span class="bins__col bins__col--max">Upper (excl.)</span>
            <span class="bins__col bins__col--score">Score</span>
            <span class="bins__col bins__col--op"></span>
          </div>
          <div v-for="(bin, bi) in dim.bins" :key="bi" class="bins__row">
            <el-input-number
              v-model="bin.min"
              class="bins__col bins__col--min"
              :controls="false"
              placeholder="0"
            />
            <el-input-number
              v-model="bin.max"
              class="bins__col bins__col--max"
              :controls="false"
              placeholder="9999"
            />
            <el-input-number
              v-model="bin.score"
              class="bins__col bins__col--score"
              :controls="false"
              placeholder="0"
            />
            <button class="bins__col bins__col--op icon-btn" @click="dim.bins.splice(bi, 1)">
              <el-icon :size="12"><Minus /></el-icon>
            </button>
          </div>
          <button class="bins__add" @click="addBin(dim)">
            <el-icon :size="12"><Plus /></el-icon>Add Bin
          </button>
        </div>

        <div v-if="dimError(dim)" class="dim__err">
          <el-icon :size="12"><WarningFilled /></el-icon>{{ dimError(dim) }}
        </div>
      </div>

      <button class="add-btn" @click="addDimension">
        <el-icon :size="13"><Plus /></el-icon>
        <span>Add Dimension</span>
      </button>
    </section>

    <section class="sect">
      <div class="sect__label">Score Mapping (optional)</div>
      <div class="hint-inline">Maps the total score to PD and Odds; not used in decision computation</div>
      <div class="mapping">
        <div class="mapping__head">
          <span>Score Lower Bound</span><span>PD</span><span>Odds</span><span></span>
        </div>
        <div v-for="(row, i) in mappingRows" :key="i" class="mapping__row">
          <el-input v-model="row.score" placeholder="650" />
          <el-input v-model="row.pd" placeholder="0.08" />
          <el-input v-model="row.odds" placeholder="11.5" />
          <button class="icon-btn" @click="removeMapping(i)">
            <el-icon :size="12"><Minus /></el-icon>
          </button>
        </div>
        <button class="bins__add" @click="mappingRows.push({ score: '', pd: '', odds: '' })">
          <el-icon :size="12"><Plus /></el-icon>Add Mapping
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { listFields, type FieldVO, type ScorecardDetail, type ScorecardDimension } from '@/api/knowledge'
import { groupByCatalog } from '@/utils/fieldGroup'

const props = defineProps<{ initial?: Partial<ScorecardDetail> }>()

const fields = ref<FieldVO[]>([])
const groupedFields = computed(() => groupByCatalog(fields.value))

const form = reactive<ScorecardDetail>({
  name: '',
  code: '',
  description: '',
  version: 'v1.0',
  dimensions: []
})

interface MappingRow {
  score: string
  pd: string
  odds: string
}
const mappingRows = ref<MappingRow[]>([])

onMounted(async () => {
  fields.value = await listFields()
  if (props.initial) {
    Object.assign(form, props.initial)
    if (!form.dimensions) form.dimensions = []
    const pd = props.initial.pd || {}
    const odds = props.initial.odds || {}
    mappingRows.value = Object.keys(pd).map((k) => ({
      score: k,
      pd: pd[k] ?? '',
      odds: odds[k] ?? ''
    }))
  }
})

const totalScore = computed(() =>
  form.dimensions.reduce((sum, d) => {
    const max = Math.max(0, ...(d.bins || []).map((b) => Number(b.score) || 0))
    return sum + max
  }, 0)
)

function onDimFieldChange(dim: ScorecardDimension) {
  const f = fields.value.find((x) => x.fieldEn === dim.field)
  if (f) dim.fieldCn = f.fieldCn
}

function addDimension() {
  form.dimensions.push({
    field: '',
    weight: 0,
    type: 'score',
    bins: [{ min: 0, max: 0, score: 0 }]
  })
}

function removeDimension(i: number) {
  form.dimensions.splice(i, 1)
}

function addBin(dim: ScorecardDimension) {
  const last = dim.bins[dim.bins.length - 1]
  dim.bins.push({
    min: last?.max ?? 0,
    max: undefined,
    score: 0
  })
}

function removeMapping(i: number) {
  mappingRows.value.splice(i, 1)
}

function dimError(dim: ScorecardDimension): string | null {
  if (!dim.field) return 'No dimension field selected'
  if (!dim.bins?.length) return 'No bins configured'
  for (let i = 0; i < dim.bins.length; i++) {
    const b = dim.bins[i]
    if (b.min == null || b.max == null) return `Bin ${i + 1}: lower/upper bounds missing`
    if (Number(b.min) >= Number(b.max)) return `Bin ${i + 1}: lower bound must be less than upper bound`
  }
  return null
}

defineExpose({
  getPayload(): ScorecardDetail {
    const pd: Record<string, string> = {}
    const odds: Record<string, string> = {}
    mappingRows.value.forEach((r) => {
      if (!r.score) return
      if (r.pd) pd[r.score] = r.pd
      if (r.odds) odds[r.score] = r.odds
    })
    return {
      ...form,
      dimensions: form.dimensions.map((d) => ({
        ...d,
        bins: (d.bins || []).map((b) => ({
          min: Number(b.min),
          max: Number(b.max),
          score: Number(b.score) || 0
        }))
      })),
      pd,
      odds
    }
  },
  validate(): string | null {
    if (!form.name?.trim()) return 'Scorecard name is required'
    if (!form.dimensions.length) return 'At least one scoring dimension is required'
    const seen = new Set<string>()
    for (let i = 0; i < form.dimensions.length; i++) {
      const d = form.dimensions[i]
      const err = dimError(d)
      if (err) return `Dimension ${i + 1} (${d.field || 'unnamed'}): ${err}`
      if (seen.has(d.field)) return `Duplicate dimension field: ${d.field}`
      seen.add(d.field)
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
    justify-content: space-between;
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

.total {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  font-weight: var(--fw-semibold);
  color: var(--c-primary);

  &.is-warn {
    color: var(--c-warning);
  }
}

.hint-inline {
  font-size: var(--fs-2xs);
  color: var(--c-text-tertiary);
  font-weight: var(--fw-regular);
}

.dim {
  padding: var(--sp-3);
  margin-bottom: var(--sp-3);
  border-radius: var(--r-md);
  border: 1px solid var(--c-border);
  background: var(--c-surface-sunken);

  &__head {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    margin-bottom: var(--sp-3);
  }

  &__idx {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    flex-shrink: 0;
    border-radius: var(--r-xs);
    background: var(--c-primary-soft);
    color: var(--c-primary);
    font-size: var(--fs-2xs);
    font-weight: var(--fw-bold);
  }

  &__field {
    flex: 1;
    min-width: 0;
  }

  &__weight {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;

    &-label {
      font-size: var(--fs-2xs);
      color: var(--c-text-tertiary);
    }

    :deep(.el-input-number) {
      width: 64px;
    }
  }

  &__err {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-top: var(--sp-2);
    font-size: var(--fs-2xs);
    color: var(--c-danger);
  }
}

.bins {
  &__head,
  &__row {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__head {
    padding: 0 2px 6px;
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &__row {
    margin-bottom: 5px;
  }

  &__col {
    &--min,
    &--max {
      flex: 1;
      min-width: 0;
    }

    &--score {
      width: 76px;
      flex-shrink: 0;
    }

    &--op {
      width: 28px;
      flex-shrink: 0;
    }
  }

  &__add {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 26px;
    padding: 0 10px;
    margin-top: 4px;
    border: 1px dashed var(--c-border-strong);
    border-radius: var(--r-xs);
    background: transparent;
    color: var(--c-text-secondary);
    font-size: var(--fs-2xs);
    cursor: pointer;
    transition: all var(--dur-fast) var(--ease-standard);

    &:hover {
      border-color: var(--c-primary);
      color: var(--c-primary);
      background: var(--c-primary-soft);
    }
  }
}

.mapping {
  margin-top: var(--sp-3);

  &__head,
  &__row {
    display: grid;
    grid-template-columns: 1fr 1fr 1fr 28px;
    gap: 6px;
    align-items: center;
  }

  &__head {
    padding: 0 2px 6px;
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &__row {
    margin-bottom: 5px;
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

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  height: 34px;
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
</style>
