<template>
  <div class="workbench">
    <header class="wb__head">
      <div>
        <h2 class="wb__title">Decision Workbench</h2>
        <p class="wb__sub">One entry from field definition to run verification; the five-layer chain in one place</p>
      </div>
      <div class="head-acts">
        <button class="guide-btn" @click="go('/guide')">
          <el-icon><QuestionFilled /></el-icon>User Guide
        </button>
        <button class="cta" @click="wizardOpen = true">
          <el-icon><Plus /></el-icon>New Decision
        </button>
      </div>
    </header>

    <div v-if="demoReady && !demoInitialized" class="demo-banner">
      <span class="demo-banner__icon"><el-icon><MagicStick /></el-icon></span>
      <div class="demo-banner__text">
        <b>Demo data not initialized</b>
        <span>One click generates a complete small consumer-loan demo (fields / rules / scorecards / list libraries / decision table / decision flow) — learn the platform by playing with it</span>
      </div>
      <button class="demo-banner__btn" :disabled="demoLoading" @click="onInitDemo">
        <el-icon v-if="demoLoading" class="is-loading"><Loading /></el-icon>
        {{ demoLoading ? 'Initializing…' : 'Initialize demo data' }}
      </button>
    </div>

    <nav class="chain" aria-label="End-to-end chain">
      <template v-for="(s, i) in chainSteps" :key="s.label">
        <button class="chain__step" :title="`Go to ${s.label}`" @click="go(s.path)">
          <span class="chain__idx">{{ i + 1 }}</span>
          <span class="chain__label">{{ s.label }}</span>
        </button>
        <el-icon v-if="i < chainSteps.length - 1" class="chain__arrow"><ArrowRight /></el-icon>
      </template>
    </nav>

    <main class="wb__grid">
      <section class="card">
        <header class="card__head">
          <span class="card__icon is-blue"><el-icon :size="16"><DataAnalysis /></el-icon></span>
          <div>
            <div class="card__title">Indicator Fields</div>
            <div class="card__desc">Decision input contract · shared by single/batch</div>
          </div>
        </header>
        <button class="card__stat is-link" title="View field list" @click="go('/fields')">
          <b class="mono">{{ stats.fields }}</b>
          <span>Fields</span>
        </button>
        <footer class="card__acts">
          <button class="act act--primary" @click="go('/fields')">Field Management</button>
          <button class="act" @click="go('/lineage')">Lineage Graph</button>
        </footer>
      </section>

      <section class="card">
        <header class="card__head">
          <span class="card__icon is-purple"><el-icon :size="16"><Document /></el-icon></span>
          <div>
            <div class="card__title">Rule Knowledge</div>
            <div class="card__desc">Rules / Scorecards / List libraries</div>
          </div>
        </header>
        <button class="card__stat is-link" title="View rules and scorecards" @click="go('/knowledge')">
          <b class="mono">{{ stats.rules }}</b>
          <span>Rules</span>
          <b class="mono">{{ stats.scorecards }}</b>
          <span>Scorecards</span>
        </button>
        <footer class="card__acts">
          <button class="act act--primary" @click="go('/knowledge')">Knowledge Base</button>
          <button class="act" @click="go('/dtable')">Decision Table</button>
        </footer>
      </section>

      <section class="card">
        <header class="card__head">
          <span class="card__icon is-orange"><el-icon :size="16"><Share /></el-icon></span>
          <div>
            <div class="card__title">Decision Flow</div>
            <div class="card__desc">Canvas orchestration · save draft · publish</div>
          </div>
        </header>
        <button class="card__stat is-link" title="View engines and versions" @click="go('/flow')">
          <b class="mono">{{ stats.engines }}</b>
          <span>Engines</span>
        </button>
        <footer class="card__acts">
          <button class="act act--primary" @click="go('/flow')">Open Designer</button>
        </footer>
      </section>

      <section class="card card--run">
        <header class="card__head">
          <span class="card__icon is-green"><el-icon :size="16"><VideoPlay /></el-icon></span>
          <div>
            <div class="card__title">Run</div>
            <div class="card__desc">Single / batch test · anchored to flow version</div>
          </div>
        </header>
        <button class="card__stat is-link" title="View decision history" @click="go('/result')">
          <b class="mono">{{ stats.results }}</b>
          <span>History</span>
        </button>
        <footer class="card__acts">
          <button class="act act--primary" @click="go('/run', { mode: 'single' })">Single Test</button>
          <button class="act" @click="go('/run', { mode: 'batch' })">Batch Test</button>
          <button class="act" @click="go('/result')">Execution Results</button>
        </footer>
      </section>

      <section class="card card--batch">
        <header class="card__head">
          <span class="card__icon is-cyan"><el-icon :size="16"><Files /></el-icon></span>
          <div>
            <div class="card__title">Batch Run</div>
            <div class="card__desc">Upload user metrics file · engine batch · download results</div>
          </div>
        </header>
        <button class="card__stat is-link" title="View batch tasks" @click="go('/batch')">
          <b class="mono">{{ stats.batches }}</b>
          <span>Batch Tasks</span>
        </button>
        <footer class="card__acts">
          <button class="act act--primary" @click="go('/batch')">Go to Batch</button>
        </footer>
      </section>
    </main>

    <NewDecisionWizard v-model="wizardOpen" @created="onCreated" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listEngines } from '@/api/flow'
import { listAllFields } from '@/api/datamanage'
import { pageRules, pageScorecards } from '@/api/knowledge'
import { pageResults } from '@/api/result'
import { pageBatches } from '@/api/batch'
import { getDemoStatus, initDemoData } from '@/api/workbench'
import NewDecisionWizard from './NewDecisionWizard.vue'


const router = useRouter()
const wizardOpen = ref(false)

const demoReady = ref(false)
const demoInitialized = ref(true)
const demoLoading = ref(false)

async function onInitDemo() {
  demoLoading.value = true
  try {
    const res = await initDemoData()
    demoInitialized.value = res.initialized
    ElMessage.success(
      res.published
        ? `Demo data initialized (engine ${res.engineCode}, published)`
        : `Demo data generated (engine ${res.engineCode}); publish it on the "Decision Flow" page to test`
    )
    await loadStats()
  } finally {
    demoLoading.value = false
  }
}

function onCreated(p: { engineId: number; versionId: number; code: string }) {
  ElMessage.success(`Decision "${p.code}" created`)
  stats.engines += 1
}

const chainSteps = [
  { label: 'Indicator Fields', path: '/fields' },
  { label: 'Rule Knowledge', path: '/knowledge' },
  { label: 'Decision Flow', path: '/flow' },
  { label: 'Run', path: '/run' },
  { label: 'Batch Run', path: '/batch' }
]

const stats = reactive({
  fields: 0,
  rules: 0,
  scorecards: 0,
  engines: 0,
  results: 0,
  batches: 0
})

function go(path: string, query?: Record<string, string>) {
  router.push({ path, ...(query ? { query } : {}) })
}

async function loadStats() {
  const [fields, rules, scorecards, engines, results, batches] = await Promise.allSettled([
    listAllFields(),
    pageRules({ pageNo: 1, pageSize: 1 }),
    pageScorecards({ pageNo: 1, pageSize: 1 }),
    listEngines(),
    pageResults({}, 1, 1),
    pageBatches(1, 1)
  ])
  if (fields.status === 'fulfilled') stats.fields = (fields.value || []).length
  if (rules.status === 'fulfilled') stats.rules = rules.value?.total ?? 0
  if (scorecards.status === 'fulfilled') stats.scorecards = scorecards.value?.total ?? 0
  if (engines.status === 'fulfilled') stats.engines = (engines.value || []).length
  if (results.status === 'fulfilled') stats.results = results.value?.total ?? 0
  if (batches.status === 'fulfilled') stats.batches = batches.value?.total ?? 0
}

onMounted(async () => {
  await loadStats()
  try {
    const st = await getDemoStatus()
    demoInitialized.value = st.initialized
    demoReady.value = true
  } catch {
    demoInitialized.value = true
    demoReady.value = false
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.workbench {
  height: 100%;
  overflow-y: auto;
  padding: var(--sp-6);
  background: var(--c-bg);
}

.wb {
  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-4);
    margin-bottom: var(--sp-4);
    flex-wrap: wrap;
  }

  &__title {
    margin: 0;
    font-size: 20px;
    font-weight: var(--fw-semibold);
    color: var(--c-text);
  }

  &__sub {
    margin: 4px 0 0;
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: var(--sp-4);
  }
}

.demo-banner {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  border: 1px dashed var(--c-primary, #409eff);
  border-radius: var(--r-md);
  background: var(--c-primary-soft, #eaf4ff);

  &__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: var(--c-primary, #409eff);
    color: #fff;
    font-size: 17px;
    flex-shrink: 0;
  }

  &__text {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;

    b {
      font-size: var(--fs-sm);
      color: var(--c-text);
    }

    span {
      font-size: var(--fs-xs);
      color: var(--c-text-secondary);
    }
  }

  &__btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 34px;
    padding: 0 var(--sp-4);
    border: none;
    border-radius: var(--r-md);
    background: var(--c-primary, #409eff);
    color: #fff;
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    cursor: pointer;
    white-space: nowrap;
    flex-shrink: 0;
    transition: all var(--dur-fast) var(--ease-standard);

    &:hover:not(:disabled) {
      filter: brightness(1.08);
    }

    &:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }
  }
}

.head-acts {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.guide-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 36px;
  padding: 0 var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--c-border);
  background: var(--c-surface-raised);
  color: var(--c-text-secondary);
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    border-color: var(--c-primary);
    color: var(--c-primary);
  }
}

.cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 var(--sp-5);
  border-radius: var(--r-md);
  border: none;
  background: var(--c-primary);
  color: var(--c-text-inverse);
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  cursor: pointer;
  box-shadow: var(--sh-xs);
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-primary-hover);
    box-shadow: var(--sh-sm);
  }
}

.chain {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-5);

  &__step {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border: none;
    background: transparent;
    cursor: pointer;
    padding: 2px 4px;
    border-radius: var(--r-sm);
    transition: background var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-primary-soft, #eaf4ff);

      .chain__label {
        color: var(--c-primary);
      }
    }
  }

  &__idx {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--c-primary-soft, #eaf4ff);
    color: var(--c-primary);
    font-size: 11px;
    font-weight: var(--fw-semibold);
  }

  &__label {
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
  }

  &__arrow {
    color: var(--c-text-tertiary);
    font-size: 12px;
  }
}

.card {
  display: flex;
  flex-direction: column;
  padding: var(--sp-5);
  border-radius: var(--r-lg);
  border: 1px solid var(--c-border);
  background: var(--c-surface-raised);
  box-shadow: var(--sh-xs);
  transition: box-shadow var(--dur-fast) var(--ease-standard);

  &:hover {
    box-shadow: var(--sh-sm);
  }

  &--run {
    border-color: var(--c-primary);
  }

  &__head {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    margin-bottom: var(--sp-4);
  }

  &__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: var(--r-md);
    flex-shrink: 0;

    &.is-blue {
      background: #eaf4ff;
      color: #409eff;
    }

    &.is-purple {
      background: #f3ecff;
      color: #8e54e9;
    }

    &.is-orange {
      background: #fff3e0;
      color: #e6a23c;
    }

    &.is-green {
      background: #e8f7ee;
      color: #34a46f;
    }

    &.is-cyan {
      background: #e6f7fb;
      color: #0aa2c0;
    }
  }

  &__title {
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    line-height: 1.2;
  }

  &__desc {
    margin-top: 2px;
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
  }

  &__stat {
    flex: 1;
    display: flex;
    align-items: baseline;
    gap: 6px;
    margin-bottom: var(--sp-4);
    border: none;
    background: transparent;
    padding: 0;
    text-align: left;

    b {
      font-size: 28px;
      font-weight: var(--fw-semibold);
      font-variant-numeric: tabular-nums;
      color: var(--c-text);
    }

    span {
      font-size: var(--fs-xs);
      color: var(--c-text-tertiary);
    }

    &.is-link {
      cursor: pointer;

      &:hover b {
        color: var(--c-primary);
      }

      &:hover span {
        color: var(--c-primary);
      }
    }
  }

  &__acts {
    display: flex;
    flex-wrap: wrap;
    gap: var(--sp-2);
  }
}

.act {
  height: 28px;
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

.mono {
  font-family: var(--font-mono);
}
</style>
