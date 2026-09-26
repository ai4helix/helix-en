<template>
  <div class="guide">
    <header class="g-head">
      <div>
        <h2 class="g-head__title">User Guide</h2>
        <p class="g-head__sub">Follow four steps to build a working decision from scratch; what to do on each page and where to click — all here</p>
      </div>
      <nav class="g-chain">
        <span v-for="(s, i) in CHAIN" :key="s.label" class="g-chain__step">
          <b class="g-chain__no" :style="{ background: s.color }">{{ i + 1 }}</b>
          {{ s.label }}
          <el-icon v-if="i < CHAIN.length - 1"><ArrowRight /></el-icon>
        </span>
      </nav>
    </header>

    <main class="g-body">
      <section class="sect">
        <h3 class="sect__title"><span class="sect__no">1</span>Three-minute quick start: build your first decision</h3>
        <ol class="flow-steps">
          <li v-for="s in quickSteps" :key="s.title" class="flow-step" :class="{ 'is-done': false }">
            <div class="flow-step__head">
              <b>{{ s.title }}</b>
              <button class="flow-step__go" @click="go(s.path)">Go to {{ s.where }} →</button>
            </div>
            <p class="flow-step__desc">{{ s.desc }}</p>
          </li>
        </ol>
        <div class="note note--primary">
          <b>Fastest path:</b> "New Decision" at the top right of the Workbench completes step 1 in one click
          (auto-creates an engine and a draft version); then click "Go to Designer" to start drawing the flow.
        </div>
      </section>

      <section class="sect">
        <h3 class="sect__title"><span class="sect__no">2</span>Decision flow canvas: arrange by dragging and connecting</h3>
        <div class="cols2">
          <div class="kvlist">
            <h4>Basic operations</h4>
            <dl>
              <dt>Add nodes</dt><dd><b>Drag nodes from the left palette onto the canvas</b>; duplicate names are allowed (only one start node)</dd>
              <dt>Connect</dt><dd>Hover a node, <b>drag from the right dot to the left side of the target node</b> and release</dd>
              <dt>Configure nodes</dt><dd>Select a node and use the <b>right property panel</b> to name it and bind rules/scorecards/list libraries</dd>
              <dt>Delete node/edge</dt><dd>Select and press <kbd>Delete</kbd> (<kbd>⌫</kbd> on Mac)</dd>
              <dt>Auto layout</dt><dd>Click "Auto Layout" in the top bar; overlaps detected on load also prompt a tidy-up</dd>
              <dt>Save/Publish</dt><dd>"Save" keeps a draft; only after "Publish" does it take effect in Run Center</dd>
            </dl>
          </div>
          <div class="kvlist">
            <h4>View and shortcuts</h4>
            <dl>
              <dt>Zoom</dt><dd>Top-bar −/+ buttons, or <kbd>Ctrl/⌘</kbd> + mouse wheel; the "%" button fits the canvas in one click</dd>
              <dt>Pan</dt><dd>Hold the left mouse button and drag the blank area</dd>
              <dt>Copy/Paste</dt><dd><kbd>Ctrl/⌘ + C</kbd> / <kbd>Ctrl/⌘ + V</kbd></dd>
              <dt>Undo/Redo</dt><dd><kbd>Ctrl/⌘ + Z</kbd> / <kbd>Ctrl/⌘ + ⇧ + Z</kbd></dd>
              <dt>Select all</dt><dd><kbd>Ctrl/⌘ + A</kbd></dd>
              <dt>Publish check</dt><dd>Before publishing, the system validates flow integrity (start/end nodes, knowledge references, etc.)</dd>
            </dl>
          </div>
        </div>
      </section>

      <section class="sect">
        <h3 class="sect__title"><span class="sect__no">3</span>Run and verify: single test vs batch run</h3>
        <div class="cols2">
          <div class="panel">
            <header class="panel__head is-blue">Single Test · debug one sample</header>
            <ol class="panel__steps">
              <li>Open "Run Center", pick an engine and version (live version by default)</li>
              <li>Fill one sample in the field form, or switch to JSON and paste directly</li>
              <li>Click "Run Test" — the verdict plus <b>per-node hit traces</b> appear instantly</li>
            </ol>
            <p class="panel__tip">Best for: quickly checking why a sample was rejected/passed while tuning rules.</p>
          </div>
          <div class="panel">
            <header class="panel__head is-orange">Batch Run · process a batch at once</header>
            <ol class="panel__steps">
              <li>On the "Batch Run" page follow <b>① fields → ② data → ③ run</b> in three steps</li>
              <li>In ② pick the engine, download the template (columns = indicator fields), upload a CSV to create a task</li>
              <li>In ③ click "Start Batch"; when done, <b>download results</b> or click "Trace" to drill into a single record</li>
            </ol>
            <p class="panel__tip">Best for: validating overall pass rate and false rejections with historical data before go-live.</p>
          </div>
        </div>
      </section>

      <!-- 4. FAQ -->
      <section class="sect">
        <h3 class="sect__title"><span class="sect__no">4</span>FAQ</h3>
        <el-collapse class="faq">
          <el-collapse-item title="Which page should I see after signing in?">
            Business users land on the "Workbench" — an aggregate page of the four-layer chain, each column with stats and direct entries;
            platform admins go to "Platform Operations". The left menu shows all your available features.
          </el-collapse-item>
          <el-collapse-item title="Test says 'Execution service not responding/unreachable'?">
            Decision execution is handled by the decision engine; tests fail when the engine is not started or not ready.
            Ask an admin to confirm the engine is up; published versions are loaded automatically when the engine starts.
          </el-collapse-item>
          <el-collapse-item title="Published a version — why does the test result differ from the design?">
            Tests are anchored to a "version": Run Center uses the current live version by default, or you can pick a historical version to compare.
            If you just published, confirm the engine has reloaded (auto-notified on publish; the self-healing reconciliation also catches up).
          </el-collapse-item>
          <el-collapse-item title="How do indicator fields, rules, and decision flows relate?">
            <b>Indicator fields</b> are the input contract (the single-test form and batch CSV columns align with them);
            <b>rules/scorecards/list libraries</b> are knowledge objects; a <b>decision flow</b> arranges knowledge objects into nodes and decides execution order.
            See the "Data Lineage" page for the full reference graph of fields → knowledge objects → nodes → versions.
          </el-collapse-item>
          <el-collapse-item title="Difference between a draft version and a running version?">
            What you save in the designer is a draft (visible to editors only); clicking "Publish" produces an artifact and notifies the engine to load it before it takes effect.
            An engine can have multiple versions; Run Center can target a specific version for tests.
          </el-collapse-item>
        </el-collapse>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'


const router = useRouter()

const CHAIN = [
  { label: 'Indicator Fields', color: '#0a84ff' },
  { label: 'Rule Knowledge', color: '#5e5ce6' },
  { label: 'Decision Flow', color: '#e6a23c' },
  { label: 'Run', color: '#34a46f' }
]

const quickSteps = [
  {
    title: '① Define indicator fields (decision inputs)',
    where: 'Batch Run page',
    path: '/batch',
    desc: 'Fields are the input contract for all tests. The Workbench "New Decision" wizard shows existing fields; if none, go to the Batch Run page "① Indicator field import", download the template, fill it in and upload.'
  },
  {
    title: '② Configure rule knowledge (scorecards/lists/rules)',
    where: 'Knowledge Base',
    path: '/knowledge',
    desc: 'Create rules (reject/score adjustments), scorecards (dimension binning), and list libraries (black/white lists) in the Knowledge Base; decision tables are maintained separately under the "Decision Table" menu.'
  },
  {
    title: '③ Design the decision flow and publish',
    where: 'Flow Designer',
    path: '/flow',
    desc: 'Drag nodes such as "Blacklist → Policy rules → Scorecard → Decision", connect them, and bind knowledge objects in the right panel; save the draft and click "Publish" to take effect.'
  },
  {
    title: '④ Run tests to verify the result',
    where: 'Run Center',
    path: '/run',
    desc: 'Single test fills one sample to see the full-chain trace; batch test pastes a JSON array to run all at once. Always anchored to the decision flow version.'
  }
]

function go(path: string) {
  router.push(path)
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.guide {
  height: 100%;
  overflow-y: auto;
  padding: var(--sp-6);
  background: var(--c-bg);
}

.g-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
  margin-bottom: var(--sp-5);

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
}

.g-chain {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--c-text-secondary);

  &__step {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  &__no {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    color: #fff;
    font-size: 11px;
    font-weight: var(--fw-semibold);
  }
}

.g-body {
  max-width: 920px;
}

.sect {
  margin-bottom: var(--sp-5);

  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    margin-bottom: var(--sp-3);
  }

  &__no {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: var(--r-sm);
    background: var(--c-primary);
    color: var(--c-text-inverse);
    font-size: var(--fs-xs);
  }
}

.flow-steps {
  list-style: none;
  margin: 0 0 var(--sp-3);
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.flow-step {
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  background: var(--c-surface-raised);
  padding: var(--sp-3) var(--sp-4);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);

    b {
      font-size: var(--fs-sm);
      color: var(--c-text);
    }
  }

  &__go {
    border: none;
    background: transparent;
    color: var(--c-primary);
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    cursor: pointer;
    flex-shrink: 0;

    &:hover {
      text-decoration: underline;
    }
  }

  &__desc {
    margin: 4px 0 0;
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
    line-height: 1.7;
  }
}

.note {
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--c-text-secondary);
  background: var(--c-surface-sunken);

  b {
    color: var(--c-primary);
  }
}

.cols2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-4);

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.kvlist {
  h4 {
    margin: 0 0 var(--sp-2);
    font-size: var(--fs-sm);
    color: var(--c-text);
  }

  dl {
    margin: 0;
    border: 1px solid var(--c-border);
    border-radius: var(--r-md);
    overflow: hidden;
    background: var(--c-surface-raised);
  }

  dt {
    float: left;
    clear: left;
    width: 88px;
    padding: var(--sp-2) var(--sp-3);
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
  }

  dd {
    margin: 0 0 0 88px;
    padding: var(--sp-2) var(--sp-3) var(--sp-2) 0;
    font-size: var(--fs-xs);
    color: var(--c-text);
    line-height: 1.7;
    border-bottom: 1px solid var(--c-separator);
    overflow: hidden;

    &:last-child {
      border-bottom: none;
    }

    b {
      color: var(--c-primary);
      font-weight: var(--fw-semibold);
    }
  }
}

kbd {
  display: inline-block;
  padding: 1px 6px;
  border: 1px solid var(--c-border-strong);
  border-bottom-width: 2px;
  border-radius: 4px;
  background: var(--c-surface);
  font-family: var(--font-mono);
  font-size: var(--fs-2xs);
  color: var(--c-text-secondary);
}

.panel {
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  background: var(--c-surface-raised);
  padding: var(--sp-4);

  &__head {
    font-size: var(--fs-sm);
    font-weight: var(--fw-semibold);
    color: var(--c-text);
    margin-bottom: var(--sp-2);

    &.is-blue {
      color: #0a84ff;
    }

    &.is-orange {
      color: #e6a23c;
    }
  }

  &__steps {
    margin: 0;
    padding-left: 18px;
    font-size: var(--fs-xs);
    color: var(--c-text);
    line-height: 1.9;

    b {
      color: var(--c-primary);
    }
  }

  &__tip {
    margin: var(--sp-2) 0 0;
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    line-height: 1.6;
  }
}

.faq {
  :deep(.el-collapse-item__header) {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
  }

  :deep(.el-collapse-item__content) {
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
    line-height: 1.8;

    b {
      color: var(--c-text);
    }
  }
}
</style>
