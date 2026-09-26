<template>
  <div class="lineage-page">
    <el-card class="toolbar" shadow="never">
      <div class="toolbar-row">
        <span class="title">Decision Flow Lineage Graph</span>
        <el-popover placement="bottom-start" width="380" trigger="click">
          <template #reference>
            <button class="help" title="What is this?">
              <el-icon><QuestionFilled /></el-icon>
            </button>
          </template>
          <div class="help-body">
            <p><b>What it is:</b> a full reference map of one decision flow version: "Fields → Knowledge objects (Rules / Scorecards / Decision Tables / List DBs) → Decision nodes → Project version".</p>
            <p><b>What it is for:</b></p>
            <ul>
              <li><b>Impact analysis</b> — before changing a field or rule, check its "downstream" to see which nodes and versions use it, avoiding breaking live decisions;</li>
              <li><b>Source tracing</b> — see why a node decides as it does by following its "upstream" to the rules and fields it references;</li>
              <li><b>Config health check</b> — spot at a glance which fields are not referenced by any decision flow (cleanable) and which knowledge objects are isolated.</li>
            </ul>
            <p><b>How to use:</b> (1) Select an engine and version at the top to auto-generate the graph → (2) Click any node to highlight its full upstream/downstream chain, with details on the right → (3) Use the search box to locate nodes; click type chips to show/hide a whole type.</p>
          </div>
        </el-popover>
        <span class="subtitle">See which fields and knowledge a decision flow version uses — check impact before changing</span>
        <div class="spacer" />
        <el-select v-model="engineId" placeholder="Select engine" style="width: 180px" @change="loadVersions">
          <el-option v-for="e in engines" :key="e.id" :label="e.name" :value="e.id" />
        </el-select>
        <el-select
          v-model="versionId"
          placeholder="Select version"
          style="width: 200px"
          :disabled="!engineId"
          @change="loadGraph"
        >
          <el-option v-for="v in versions" :key="v.id" :label="`Version v${v.version}${v.subVersion != null ? '.' + v.subVersion : ''}`" :value="v.id" />
        </el-select>
      </div>

      <div class="toolbar-row sub">
        <el-input
          v-model="keyword"
          placeholder="Search nodes (name / type)"
          clearable
          style="width: 240px"
          @input="onKeyword"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <div class="filters">
          <span class="filter-label">Show/Hide:</span>
          <button
            v-for="t in typeDefs"
            :key="t.key"
            class="chip"
            :class="{ off: hiddenTypes.includes(t.key) }"
            :style="chipStyle(t)"
            @click="onToggleType(t.key)"
          >
            {{ t.label }}
          </button>
        </div>

        <div class="spacer" />
        <el-button text :icon="Refresh" @click="onReset">Reset View</el-button>
        <el-button text :icon="FullScreen" @click="graphApi?.fit()">Fit Canvas</el-button>
        <span v-if="stats" class="stats">
          Fields {{ stats.fieldCount }} · Knowledge {{ stats.knowledgeCount }} · Nodes {{ stats.nodeCount }}
        </span>
      </div>
    </el-card>

    <div class="canvas-wrap">
      <div ref="canvasEl" class="canvas" />
      <div v-if="!versionId" class="empty-hint">
        <div class="onboard">
          <div class="onboard__title">Build the lineage graph in 3 steps</div>
          <div class="onboard__steps">
            <div class="onboard__step">
              <span class="onboard__idx">1</span>
              <span class="onboard__text">Select the <b>engine and version</b> at the top (top right)</span>
            </div>
            <div class="onboard__step">
              <span class="onboard__idx">2</span>
              <span class="onboard__text">Auto-plot the four-layer reference graph "Fields → Knowledge → Nodes → Version"</span>
            </div>
            <div class="onboard__step">
              <span class="onboard__idx">3</span>
              <span class="onboard__text"><b>Click a node</b> to highlight upstream/downstream links; the right drawer shows details and neighbors</span>
            </div>
          </div>
          <div class="onboard__hint">
            Typical use: before changing a field/rule, click it here to see its "downstream" — how many decision nodes use it — and assess the impact first.
          </div>
        </div>
      </div>
    </div>

    <el-drawer v-model="drawerOpen" :title="drawerTitle" size="320px" direction="rtl">
      <div v-if="selectedNode" class="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="Type">{{ typeLabel(selectedNode.type) }}</el-descriptions-item>
          <el-descriptions-item v-if="selectedNode.refType" label="Object Type">
            {{ knowledgeLabel(selectedNode.refType) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="selectedNode.subType" label="Subtype / Value Type">
            {{ selectedNode.subType }}
          </el-descriptions-item>
          <el-descriptions-item v-if="selectedNode.refId != null" label="Business ID">
            {{ selectedNode.refId }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="rel-block">
          <div class="rel-title">
            Upstream
            <span class="rel-count">{{ upstream.length }}</span>
          </div>
          <div v-if="!upstream.length" class="rel-empty">None (source node)</div>
          <div
            v-for="n in upstream"
            :key="n.id"
            class="rel-item"
            @click="navigate(n.id)"
          >
            <span class="rel-dot" :style="{ background: paletteColor(n.type) }" />
            <span class="rel-name">{{ n.label }}</span>
          </div>
        </div>

        <div class="rel-block">
          <div class="rel-title">
            Downstream
            <span class="rel-count">{{ downstream.length }}</span>
          </div>
          <div v-if="!downstream.length" class="rel-empty">None (terminal node)</div>
          <div
            v-for="n in downstream"
            :key="n.id"
            class="rel-item"
            @click="navigate(n.id)"
          >
            <span class="rel-dot" :style="{ background: paletteColor(n.type) }" />
            <span class="rel-name">{{ n.label }}</span>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed } from 'vue'
import { Search, Refresh, FullScreen, QuestionFilled } from '@element-plus/icons-vue'
import { listEngines, listVersions } from '@/api/flow'
import { getLineageGraph } from '@/api/datamanage'
import { useLineageGraph } from '@/composables/useLineageGraph'
import type { Engine, EngineVersion } from '@/types/flow'
import type { GraphNode, LineageGraphDTO } from '@/types/lineage'

const engines = ref<Engine[]>([])
const versions = ref<EngineVersion[]>([])
const engineId = ref<number | undefined>()
const versionId = ref<number | undefined>()
const keyword = ref('')
const hiddenTypes = ref<string[]>([])
const canvasEl = ref<HTMLElement>()
const graphApi = ref<ReturnType<typeof useLineageGraph>>()
const currentData = ref<LineageGraphDTO | null>(null)
const selectedNode = ref<GraphNode | null>(null)
const drawerOpen = ref(false)

const PALETTE: Record<string, { bg: string; border: string }> = {
  FIELD: { bg: '#eaf4ff', border: '#409eff' },
  KNOWLEDGE: { bg: '#f3ecff', border: '#8e54e9' },
  NODE: { bg: '#fff3e0', border: '#e6a23c' },
  VERSION: { bg: '#1f6fe0', border: '#1f6fe0' }
}
const typeDefs = [
  { key: 'FIELD', label: 'Field', color: '#409eff' },
  { key: 'KNOWLEDGE', label: 'Knowledge', color: '#8e54e9' },
  { key: 'NODE', label: 'Decision Node', color: '#e6a23c' },
  { key: 'VERSION', label: 'Project Version', color: '#303133' }
]
const KNOWLEDGE_TAG: Record<string, string> = {
  RULE: 'Rule',
  SCORECARD: 'Scorecard',
  DECISION_TABLE: 'Decision Table',
  LIST_DB: 'List DB'
}

function paletteColor(t: string) {
  return PALETTE[t]?.border || '#dcdfe6'
}
function typeLabel(t: string) {
  return typeDefs.find((d) => d.key === t)?.label || t
}
function knowledgeLabel(rt: string) {
  return KNOWLEDGE_TAG[rt] || rt
}
function chipStyle(t: { color: string }) {
  return { '--c': t.color } as any
}
function drawerTitle() {
  return selectedNode.value ? `Lineage Details · ${typeLabel(selectedNode.value.type)}` : 'Lineage Details'
}

const stats = computed(() => {
  const d = currentData.value
  if (!d) return null
  return {
    fieldCount: d.fieldCount,
    knowledgeCount: d.knowledgeCount,
    nodeCount: d.nodeCount
  }
})

const EMPTY_GRAPH: LineageGraphDTO = {
  version: { id: '', type: 'VERSION', label: '' },
  nodes: [],
  edges: [],
  nodeCount: 0,
  knowledgeCount: 0,
  fieldCount: 0
}

const nodeById = computed(() => {
  const m = new Map<string, GraphNode>()
  currentData.value?.nodes.forEach((n) => m.set(n.id, n))
  return m
})
const upstream = computed(() => {
  const id = selectedNode.value?.id
  if (!id || !currentData.value) return []
  return currentData.value.edges
    .filter((e) => e.target === id)
    .map((e) => nodeById.value.get(e.source))
    .filter((n): n is GraphNode => !!n)
})
const downstream = computed(() => {
  const id = selectedNode.value?.id
  if (!id || !currentData.value) return []
  return currentData.value.edges
    .filter((e) => e.source === id)
    .map((e) => nodeById.value.get(e.target))
    .filter((n): n is GraphNode => !!n)
})

async function loadVersions() {
  if (!engineId.value) return
  const res = await listVersions(engineId.value)
  versions.value = res || []
  versionId.value = undefined
  currentData.value = null
  graphApi.value?.render(EMPTY_GRAPH)
}
async function loadGraph() {
  if (!versionId.value) return
  const data = await getLineageGraph(versionId.value)
  currentData.value = data
  selectedNode.value = null
  drawerOpen.value = false
  keyword.value = ''
  hiddenTypes.value = []
  graphApi.value?.render(data)
}

function onKeyword(v: string) {
  graphApi.value?.setKeyword(v)
}
function onToggleType(t: string) {
  const i = hiddenTypes.value.indexOf(t)
  if (i >= 0) hiddenTypes.value.splice(i, 1)
  else hiddenTypes.value.push(t)
  graphApi.value?.toggleType(t)
}
function onReset() {
  keyword.value = ''
  hiddenTypes.value = []
  graphApi.value?.reset()
}
function navigate(nodeId: string) {
  graphApi.value?.selectNodeById(nodeId)
}

onMounted(async () => {
  const api = useLineageGraph()
  graphApi.value = api
  if (canvasEl.value) {
    api.mount(canvasEl.value, {
      onSelect: (node) => {
        selectedNode.value = node
        drawerOpen.value = !!node
      }
    })
  }
  engines.value = (await listEngines()) || []
})
onBeforeUnmount(() => {
  graphApi.value?.dispose()
})
</script>

<style scoped>
.lineage-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  flex: 0 0 auto;
}
.toolbar-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.toolbar-row.sub {
  margin-top: 12px;
}
.title {
  font-size: 15px;
  font-weight: 600;
}
.subtitle {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}
.help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: #909399;
  cursor: pointer;
  border-radius: 50%;
  padding: 0;
}
.help:hover {
  color: #409eff;
  background: #ecf5ff;
}
.help-body {
  font-size: 12px;
  color: #606266;
  line-height: 1.7;
}
.help-body p {
  margin: 0 0 8px;
}
.help-body ul {
  margin: 0 0 8px;
  padding-left: 18px;
}
.help-body b {
  color: #303133;
}
.onboard {
  max-width: 460px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 22px 26px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  pointer-events: auto;
}
.onboard__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 14px;
}
.onboard__steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.onboard__step {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #606266;
}
.onboard__idx {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  flex: 0 0 auto;
}
.onboard__text b {
  color: #303133;
  font-weight: 600;
}
.onboard__hint {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f4f8ff;
  color: #606266;
  font-size: 12px;
  line-height: 1.7;
}
.spacer {
  flex: 1 1 auto;
}
.filters {
  display: flex;
  align-items: center;
  gap: 6px;
}
.filter-label {
  color: #909399;
  font-size: 12px;
}
.chip {
  border: 1px solid var(--c, #dcdfe6);
  background: #fff;
  color: var(--c, #606266);
  border-radius: 14px;
  padding: 3px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.chip:hover {
  background: var(--c);
  color: #fff;
}
.chip.off {
  background: #f4f4f5;
  color: #c0c4cc;
  border-color: #e4e7ed;
}
.stats {
  color: #909399;
  font-size: 12px;
}
.canvas-wrap {
  position: relative;
  flex: 1 1 auto;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  background: #f7f8fa;
}
.canvas {
  width: 100%;
  height: 100%;
}
.empty-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.empty-hint .onboard {
  pointer-events: auto;
}
.detail {
  font-size: 13px;
}
.rel-block {
  margin-top: 18px;
}
.rel-title {
  font-weight: 600;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.rel-count {
  background: #ecf0f5;
  color: #909399;
  border-radius: 10px;
  padding: 0 8px;
  font-size: 12px;
}
.rel-empty {
  color: #c0c4cc;
  font-size: 12px;
}
.rel-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
}
.rel-item:hover {
  background: #f2f6fc;
}
.rel-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}
.rel-name {
  line-height: 1.3;
  word-break: break-all;
}
</style>
