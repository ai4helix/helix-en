<template>
  <div class="designer">
    <header class="topbar">
      <div class="topbar__lead">
        <div class="picker">
          <el-select
            v-model="engineId"
            placeholder="Select engine"
            class="picker__select"
            :teleported="true"
            @change="onEngineChange"
          >
            <el-option v-for="e in engines" :key="e.id" :label="e.name" :value="e.id">
              <div class="opt">
                <span class="opt__name">{{ e.name }}</span>
                <span class="opt__code">{{ e.code }}</span>
              </div>
            </el-option>
          </el-select>

          <el-select
            v-model="versionId"
            placeholder="Version"
            class="picker__version"
            :teleported="true"
            @change="onVersionChange"
          >
            <el-option
              v-for="v in versions"
              :key="v.id"
              :label="versionLabel(v)"
              :value="v.id"
            >
              <div class="opt">
                <span class="opt__name">{{ versionLabel(v) }}</span>
                <span v-if="v.bootState === 1" class="opt__badge">Running</span>
                <span v-else class="opt__code">Draft</span>
              </div>
            </el-option>
          </el-select>

          <el-dropdown
            :disabled="!versionId"
            trigger="click"
            @command="onCreateVersion"
          >
            <button class="ver-add" title="Create a new version for this engine" :disabled="!versionId">
              <el-icon><Plus /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="copy">
                  <el-icon><CopyDocument /></el-icon>Copy current version as new draft
                </el-dropdown-item>
                <el-dropdown-item command="blank">
                  <el-icon><Plus /></el-icon>New blank version
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided :disabled="currentVersion?.bootState === 1">
                  <el-icon><Delete /></el-icon>
                  <span :class="{ 'ver-del-danger': currentVersion?.bootState !== 1 }">Delete current version</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <button class="btn btn--primary btn--sm" @click="wizardOpen = true">
            <el-icon><Plus /></el-icon><span>New Decision</span>
          </button>
        </div>
      </div>

      <div class="topbar__actions">
        <div class="seg">
          <button class="seg__btn" :disabled="!graph" title="Undo ⌘Z" @click="undo">
            <el-icon><RefreshLeft /></el-icon>
          </button>
          <button class="seg__btn" :disabled="!graph" title="Redo ⌘⇧Z" @click="redo">
            <el-icon><RefreshRight /></el-icon>
          </button>
        </div>

        <div class="seg">
          <button class="seg__btn" title="Zoom Out" @click="zoom(-0.1)"><el-icon><ZoomOut /></el-icon></button>
          <button class="seg__btn seg__btn--zoom" title="Fit Canvas" @click="fitContent">
            {{ Math.round(zoomLevel * 100) }}%
          </button>
          <button class="seg__btn" title="Zoom In" @click="zoom(0.1)"><el-icon><ZoomIn /></el-icon></button>
        </div>

        <button class="btn btn--ghost" @click="handleAutoLayout">
          <el-icon><MagicStick /></el-icon><span>Auto Layout</span>
        </button>

        <button class="btn btn--ghost save-dot" :class="{ 'has-unsaved': unsavedEdits }" :disabled="saving" @click="handleSave">
          <el-icon><Check /></el-icon><span>Save</span>
        </button>

        <button class="btn btn--ghost" :disabled="!versionId" @click="grayVisible = true">
          <el-icon><Compass /></el-icon><span>Gray</span>
        </button>

        <button class="btn btn--ghost" title="Canvas operations and shortcuts" @click="router.push('/guide')">
          <el-icon><QuestionFilled /></el-icon><span>Help</span>
        </button>

        <el-dropdown
          :disabled="!engineId"
          trigger="click"
          @command="(m: string) => goRun(m as 'single' | 'batch')"
        >
          <button class="btn btn--ghost" :disabled="!engineId" title="Run a trial with the current engine + version in the Run Center">
            <el-icon><VideoPlay /></el-icon><span>Run</span>
            <el-icon class="btn__caret"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="single">Single Trial · one sample through the full chain</el-dropdown-item>
              <el-dropdown-item command="batch">Batch Trial · run many samples at once</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button class="btn btn--primary" :disabled="publishing" @click="handlePublish">
          <el-icon><Upload /></el-icon><span>Publish</span>
        </button>
      </div>
    </header>

    <GrayPanel v-model="grayVisible" :version-id="versionId" />

    <div class="body">
      <aside class="stencil u-scroll-y">
        <div v-for="group in stencilGroups" :key="group.title" class="stencil__group">
          <div class="stencil__label">{{ group.title }}</div>
          <div
            v-for="type in group.types"
            :key="type"
            class="item"
            draggable="true"
            @dragstart="onDragStart($event, type)"
          >
            <div class="item__icon" :style="iconStyle(type)">
              <NodeIcon :name="metaOf(type).icon" :size="14" />
            </div>
            <span class="item__text">{{ metaOf(type).label }}</span>
            <el-icon class="item__grip" :size="12"><Rank /></el-icon>
          </div>
        </div>

        <div class="stencil__tip">
          <el-icon :size="12"><InfoFilled /></el-icon>
          <span>Drag to canvas to add nodes</span>
        </div>
      </aside>

      <main class="canvas-wrap">
        <transition name="tips-fade">
          <div v-if="overlapFixed" class="overlap-tip">
            <el-icon :size="13"><MagicStick /></el-icon>
            <span>Overlapping nodes detected; layout has been auto-arranged</span>
            <button class="overlap-tip__save" @click="handleSave">Save Layout</button>
            <button class="overlap-tip__close" title="Dismiss" @click="overlapFixed = false">
              <el-icon :size="12"><Close /></el-icon>
            </button>
          </div>
        </transition>

        <div ref="containerRef" class="canvas" @drop="onDrop" @dragover.prevent />
        <TeleportContainer v-if="!SAFARI_MODE" />

        <div v-if="currentVersion" class="floating-status" :class="{ 'is-live': currentVersion.bootState === 1 }">
          <span class="floating-status__dot" />
          <span>{{ currentVersion.bootState === 1 ? 'Published & Running' : 'Draft (unpublished)' }}</span>
        </div>

        <div v-if="graph && !nodeCount" class="canvas-empty">
          <div class="canvas-empty__icon"><el-icon :size="28"><Share /></el-icon></div>
          <div class="canvas-empty__title">Start designing your Decision Flow</div>
          <div class="canvas-empty__desc">Drag nodes from the left onto the canvas and connect them to build the flow</div>
        </div>
      </main>

      <aside class="props u-scroll-y">
        <template v-if="currentNodeData">
          <NodePropsPanel
            :data="currentNodeData"
            @update="onNodeDataUpdate"
            @delete="onDeleteNode"
          />
        </template>
        <div v-else class="props__empty">
          <div class="props__empty-icon"><el-icon :size="22"><Pointer /></el-icon></div>
          <div class="props__empty-title">No Node Selected</div>
          <div class="props__empty-desc">Click a node on the canvas to edit its properties</div>
        </div>
      </aside>
    </div>

    <NewDecisionWizard v-model="wizardOpen" @created="onWizardCreated" />

    <Teleport to="body">
      <div v-if="edgeMenu" class="edge-menu" :style="{ left: edgeMenu.x + 'px', top: edgeMenu.y + 'px' }">
        <div class="edge-menu__title">Branch Type</div>
        <button
          v-for="b in EDGE_BRANCHES"
          :key="b.label"
          class="edge-menu__opt"
          :class="[b.cls, { 'is-on': edgeMenu.edge.getData()?.kind === b.kind }]"
          @click="applyEdgeBranch(b)"
        >
          {{ b.label }}
        </button>
        <button class="edge-menu__close" @click="closeEdgeMenu">×</button>
        <span class="edge-menu__sep" />
        <button class="edge-menu__del" title="Delete Edge" @click="applyEdgeDelete">Delete Edge</button>
      </div>
      <div v-if="edgeMenu" class="edge-menu__mask" @click="closeEdgeMenu" />
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, shallowRef, nextTick, computed } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { Graph, type Edge } from '@antv/x6'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createGraph, TeleportContainer, SAFARI_MODE } from './graphSetup'
import { syncSafariNode } from './safariNode'
import { NODE_META, STENCIL_GROUPS, getNodeMeta } from './nodeMeta'
import { autoLayout, hasOverlap } from './flowLayout'
import NodePropsPanel from './components/NodePropsPanel.vue'
import NodeIcon from './components/NodeIcon.vue'
import GrayPanel from './GrayPanel.vue'
import { NodeType, type CellVO, type GraphVO, type NodeData, type Engine, type EngineVersion } from '@/types/flow'
import { loadGraph, saveGraph, listEngines, listVersions, publishVersion, moveAndLink, createEngineVersion, saveAsDraft, removeVersion, saveNode, removeNodes } from '@/api/flow'
import NewDecisionWizard from '@/views/workbench/NewDecisionWizard.vue'

const graph = shallowRef<Graph>()
const containerRef = ref<HTMLElement>()
const stencilGroups = STENCIL_GROUPS
const router = useRouter()

const engines = ref<Engine[]>([])
const versions = ref<EngineVersion[]>([])
const engineId = ref<number>()
const versionId = ref<number>()
const zoomLevel = ref(1)
const nodeCount = ref(0)
const overlapFixed = ref(false)

const currentNodeData = ref<NodeData>()
const saving = ref(false)
const publishing = ref(false)

const currentVersion = computed(() => versions.value.find((v) => v.id === versionId.value))

const currentEngineCode = computed(() => engines.value.find((e) => e.id === engineId.value)?.code)

function goRun(mode: 'single' | 'batch') {
  if (!currentEngineCode.value) return
  router.push({
    path: '/run',
    query: {
      engineCode: currentEngineCode.value,
      ...(versionId.value ? { versionId: String(versionId.value) } : {}),
      mode
    }
  })
}

let pendingNodes: Array<{ nodeId: number; nodeX: number; nodeY: number }> = []
let pendingEdges: Array<{
  action: 'ADD' | 'REMOVE'
  sourceNodeCode: string
  targetNodeCode: string
  label?: string
  kind?: number
}> = []
let flushTimer: ReturnType<typeof setTimeout> | undefined
const unsavedEdits = ref(false)

const autoPersist = computed(() => currentVersion.value?.bootState !== 1)

const pendingNodeSaves = new Map<number, Partial<NodeData>>()
let nodeSaveTimer: ReturnType<typeof setTimeout> | undefined

let flowEpoch = 0

function scheduleNodeSave(nodeId: number, partial: Partial<NodeData>) {
  pendingNodeSaves.set(nodeId, { ...(pendingNodeSaves.get(nodeId) || {}), ...partial })
  if (nodeSaveTimer) clearTimeout(nodeSaveTimer)
  nodeSaveTimer = setTimeout(() => void flushNodeSaves(), 600)
}

async function flushNodeSaves() {
  const myEpoch = flowEpoch
  if (nodeSaveTimer) {
    clearTimeout(nodeSaveTimer)
    nodeSaveTimer = undefined
  }
  if (!versionId.value || pendingNodeSaves.size === 0) return
  const entries = [...pendingNodeSaves.entries()]
  pendingNodeSaves.clear()
  for (const [nodeId, partial] of entries) {
    if (myEpoch !== flowEpoch) return
    try {
      await saveNode({ versionId: versionId.value, nodeId, ...partial })
    } catch {
      if (myEpoch !== flowEpoch) return
      pendingNodeSaves.set(nodeId, partial)
      unsavedEdits.value = true
      ElMessage.warning('Auto-save of node properties failed; click "Save" later to retry')
      break
    }
  }
}

function metaOf(type: NodeType) {
  return getNodeMeta(type)
}

function iconStyle(type: NodeType) {
  const c = metaOf(type).color
  return { color: c, background: `${c}1a` }
}

function versionLabel(v: EngineVersion) {
  return `V${v.version}.${v.subVersion ?? 0}`
}

function beforeUnloadGuard(e: BeforeUnloadEvent) {
  if (unsavedEdits.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}

onBeforeRouteLeave(async () => {
  if (!unsavedEdits.value) return true
  try {
    await ElMessageBox.confirm(
      'You have unsaved changes that will be lost if you leave. Click "Save" first.',
      'Unsaved Changes',
      { type: 'warning', confirmButtonText: 'Leave Anyway', cancelButtonText: 'Stay' }
    )
    return true
  } catch {
    return false
  }
})

onMounted(async () => {
  await nextTick()
  if (containerRef.value) {
    graph.value = createGraph(containerRef.value)
    bindGraphEvents()
  }
  window.addEventListener('beforeunload', beforeUnloadGuard)
  await loadEngines()
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', beforeUnloadGuard)
  graph.value?.dispose()
  if (flushTimer) clearTimeout(flushTimer)
  if (nodeSaveTimer) clearTimeout(nodeSaveTimer)
})

function bindGraphEvents() {
  const g = graph.value!

  const refreshCount = () => {
    nodeCount.value = g.getNodes().length
  }
  refreshCount()

  g.on('node:click', ({ node }) => {
    const d = node.getData() as NodeData | undefined
    currentNodeData.value = d ? { ...d } : undefined
  })
  g.on('blank:click', () => {
    currentNodeData.value = undefined
  })
  g.on('node:moved', ({ node }) => {
    const d = node.getData() as NodeData | undefined
    if (!d?.nodeId) return
    const pos = node.getPosition()
    pendingNodes.push({ nodeId: d.nodeId, nodeX: pos.x, nodeY: pos.y })
    scheduleFlush()
  })
  g.on('edge:connected', ({ edge, isNew }) => {
    if (!isNew) return
    const sourceCode = (edge.getSourceCell() as any)?.getData()?.nodeCode
    const targetCode = (edge.getTargetCell() as any)?.getData()?.nodeCode
    if (!sourceCode || !targetCode) return
    edge.setData({ label: 'Pass', kind: 1 })
    pendingEdges.push({ action: 'ADD', sourceNodeCode: sourceCode, targetNodeCode: targetCode, label: 'Pass', kind: 1 })
    scheduleFlush()
  })
  g.on('edge:removed', ({ edge }) => {
    if (rendering) return
    const sourceCode = (edge.getSourceCell() as any)?.getData()?.nodeCode
    const targetCode = (edge.getTargetCell() as any)?.getData()?.nodeCode
    if (sourceCode && targetCode) {
      pendingEdges.push({
        action: 'REMOVE',
        sourceNodeCode: sourceCode,
        targetNodeCode: targetCode,
        label: edge.getData()?.label,
        kind: edge.getData()?.kind
      })
      scheduleFlush()
    }
  })
  g.on('node:removed', ({ node }) => {
    if (rendering) return
    const d = node.getData() as NodeData | undefined
    if (!d?.nodeId) return
    if (currentNodeData.value?.nodeId === d.nodeId) currentNodeData.value = undefined
    if (!autoPersist.value || !versionId.value) {
      unsavedEdits.value = true
      return
    }
    removeNodes(versionId.value, [d.nodeId]).catch(() => {
      unsavedEdits.value = true
      ElMessage.warning('Node deletion was not synced to the server; click "Save" to reconcile')
    })
  })
  g.on('edge:click', ({ e, edge }) => {
    edgeMenu.value = { x: e.clientX, y: e.clientY, edge }
  })

  const EDGE_LINE = { stroke: '#a1a1a6', strokeWidth: 1.5 }
  const EDGE_SELECTED = { stroke: '#0a84ff', strokeWidth: 2.5 }
  g.on('edge:selected', ({ edge }) => {
    edge.attr('line/stroke', EDGE_SELECTED.stroke)
    edge.attr('line/strokeWidth', EDGE_SELECTED.strokeWidth)
  })
  g.on('edge:unselected', ({ edge }) => {
    edge.attr('line/stroke', EDGE_LINE.stroke)
    edge.attr('line/strokeWidth', EDGE_LINE.strokeWidth)
  })

  g.on('node:added', refreshCount)
  g.on('node:removed', refreshCount)
  g.on('scale', ({ sx }) => {
    zoomLevel.value = sx
  })
}

function scheduleFlush() {
  if (!autoPersist.value) {
    unsavedEdits.value = true
    return
  }
  if (flushTimer) clearTimeout(flushTimer)
  flushTimer = setTimeout(() => void flushNow(), 400)
}

function clearPendingChanges() {
  flowEpoch++
  if (flushTimer) {
    clearTimeout(flushTimer)
    flushTimer = undefined
  }
  if (nodeSaveTimer) {
    clearTimeout(nodeSaveTimer)
    nodeSaveTimer = undefined
  }
  if (pendingNodes.length || pendingEdges.length || pendingNodeSaves.size) {
    pendingNodes = []
    pendingEdges = []
    pendingNodeSaves.clear()
    if (unsavedEdits.value) ElMessage.warning('Discarded unsaved changes from the previous version')
  }
  unsavedEdits.value = false
}

async function flushNow() {
  const myEpoch = flowEpoch
  if (flushTimer) {
    clearTimeout(flushTimer)
    flushTimer = undefined
  }
  if (!versionId.value) return
  const nodes = pendingNodes.slice()
  const edges = pendingEdges.slice()
  if (!nodes.length && !edges.length) return
  pendingNodes = []
  pendingEdges = []
  try {
    await moveAndLink({ versionId: versionId.value, nodes, edges })
  } catch {
    if (myEpoch !== flowEpoch) return
    pendingNodes = nodes.concat(pendingNodes)
    pendingEdges = edges.concat(pendingEdges)
    ElMessage.warning('Some canvas changes could not be saved; they will retry on the next action or save')
  }
}


const EDGE_BRANCHES = [
  { label: 'Pass', kind: 1, cls: 'is-pass' },
  { label: 'Reject', kind: 2, cls: 'is-reject' },
  { label: 'Manual', kind: 3, cls: 'is-manual' }
] as const

const edgeMenu = shallowRef<{ x: number; y: number; edge: Edge } | null>(null)

function closeEdgeMenu() {
  edgeMenu.value = null
}

function applyEdgeDelete() {
  const edge = edgeMenu.value?.edge
  closeEdgeMenu()
  if (edge) graph.value?.removeCell(edge)
}

function renderEdgeLabel(edge: Edge) {
  const d = edge.getData() as { label?: string; kind?: number }
  if (d?.kind === 1 || (!d?.label && d?.kind == null)) {
    edge.setLabels([])
    return
  }
  const color = d?.kind === 2 || (!d?.kind && d?.label === 'Reject') ? '#d54941' : '#e69b00'
  edge.setLabels([
    {
      attrs: {
        text: { text: d.label, fill: '#fff', fontSize: 10 },
        rect: { fill: color, stroke: 'none', rx: 3, ry: 3 }
      }
    } as any
  ])
}

function applyEdgeBranch(b: { label: string; kind: number }) {
  const edge = edgeMenu.value?.edge
  if (!edge) return
  const prev = edge.getData() as { label?: string; kind?: number } | undefined
  const sourceCode = (edge.getSourceCell() as any)?.getData()?.nodeCode
  const targetCode = (edge.getTargetCell() as any)?.getData()?.nodeCode
  edge.setData({ label: b.label, kind: b.kind })
  renderEdgeLabel(edge)
  if (sourceCode && targetCode) {
    pendingEdges.push({ action: 'REMOVE', sourceNodeCode: sourceCode, targetNodeCode: targetCode, label: prev?.label, kind: prev?.kind })
    pendingEdges.push({ action: 'ADD', sourceNodeCode: sourceCode, targetNodeCode: targetCode, label: b.label, kind: b.kind })
    scheduleFlush()
  }
  closeEdgeMenu()
}

async function loadEngines() {
  try {
    engines.value = await listEngines()
    if (!engines.value.length) return
    const q = router.currentRoute.value.query
    const qEngine = Number(q.engineId)
    const target = engines.value.find((e) => e.id === qEngine) || engines.value[0]
    engineId.value = target.id
    await onEngineChange(target.id)
    const qVersion = Number(q.versionId)
    if (qVersion && qVersion !== versionId.value && versions.value.some((v) => v.id === qVersion)) {
      versionId.value = qVersion
      await onVersionChange(qVersion)
    }
  } catch {
  }
}

async function onEngineChange(id: number) {
  if (!id) return
  versions.value = await listVersions(id)
  if (versions.value.length) {
    const live = versions.value.find((v) => v.bootState === 1)
    const pick = live ?? versions.value[0]
    versionId.value = pick.id
    await onVersionChange(pick.id)
  }
}


const wizardOpen = ref(false)

async function onWizardCreated(created: { engineId: number; versionId: number }) {
  engines.value = await listEngines()
  engineId.value = created.engineId
  versions.value = await listVersions(created.engineId)
  versionId.value = created.versionId
  await onVersionChange(created.versionId)
  ElMessage.success('Engine and draft version created; start designing your flow')
}

async function onCreateVersion(mode: 'copy' | 'blank' | 'delete') {
  if (!engineId.value || !versionId.value) return
  if (mode === 'delete') {
    if (currentVersion.value?.bootState === 1) {
      ElMessage.warning('A running version cannot be deleted; publish another version first to take it offline')
      return
    }
    const isLast = versions.value.length === 1
    const engineName = engines.value.find((e) => e.id === engineId.value)?.name ?? ''
    try {
      await ElMessageBox.confirm(
        isLast
          ? `"${engineName}" has only this version left: deleting it will also delete the whole engine and its decision tables and other data (rules/scorecards in the Knowledge Base are kept). This cannot be undone. Delete the whole decision?`
          : 'This cannot be undone: all nodes, edges and published artifacts of this version will be removed. Confirm delete?',
        isLast ? 'Delete Whole Decision' : 'Delete Version',
        { type: 'warning', confirmButtonText: 'Delete', cancelButtonText: 'Cancel' }
      )
    } catch {
      return
    }
    try {
      await removeVersion(versionId.value)
      engines.value = await listEngines()
      const engineGone = !engines.value.some((e) => e.id === engineId.value)
      if (engineGone) {
        ElMessage.success('Decision and its engine deleted')
        if (engines.value.length) {
          engineId.value = engines.value[0].id
          await onEngineChange(engineId.value)
        } else {
          engineId.value = undefined
          versionId.value = undefined
          versions.value = []
          graph.value?.clearCells()
          currentNodeData.value = undefined
        }
      } else {
        ElMessage.success('Version deleted')
        versions.value = await listVersions(engineId.value)
        if (versions.value.length) {
          versionId.value = versions.value[0].id
          await onVersionChange(versions.value[0].id)
        } else {
          versionId.value = undefined
          graph.value?.clearCells()
          currentNodeData.value = undefined
        }
      }
    } catch {
    }
    return
  }
  try {
    let newId: number
    if (mode === 'copy') {
      newId = await saveAsDraft(versionId.value)
      ElMessage.success('Copied as a new draft version')
    } else {
      newId = await createEngineVersion(engineId.value)
      ElMessage.success('Blank draft version created')
    }
    versions.value = await listVersions(engineId.value)
    versionId.value = newId
    await onVersionChange(newId)
  } catch {
  }
}

async function onVersionChange(id: number) {
  if (!id) return
  clearPendingChanges()
  const data = await loadGraph(id)
  currentNodeData.value = undefined
  renderGraph(data)
}

let firstRender = true
let rendering = false

function renderGraph(data: GraphVO) {
  const g = graph.value
  if (!g) return
  const wasEmpty = g.getCells().length === 0
  const fit = firstRender || wasEmpty
  firstRender = false
  rendering = true
  g.clearCells()
  const cells = data.cells || []
  cells.filter((c) => c.shape === 'x6-node').forEach((c) => addNodeFromCell(c))
  cells.filter((c) => c.shape !== 'x6-node').forEach((c) => addEdgeFromCell(c))
  rendering = false
  nodeCount.value = g.getNodes().length

  if (hasOverlap(g)) {
    autoLayout(g)
    overlapFixed.value = true
  } else {
    overlapFixed.value = false
  }

  if (fit && cells.length) {
    g.zoomToFit({ padding: 40, maxScale: 1, minScale: 0.4 })
    g.centerContent()
    zoomLevel.value = g.zoom()
  } else if (data.zoom && data.zoom !== 1 && wasEmpty) {
    g.zoomTo(data.zoom)
    zoomLevel.value = g.zoom()
  } else {
    zoomLevel.value = g.zoom()
  }
}



function addNodeFromCell(c: CellVO) {
  const g = graph.value!
  const meta = getNodeMeta(c.data?.nodeType ?? NodeType.POLICY)
  const node = g.addNode({
    id: c.id,
    shape: 'x6-node',
    x: c.position?.x ?? 0,
    y: c.position?.y ?? 0,
    width: meta.width,
    height: meta.height,
    data: c.data || {}
  })
  if (SAFARI_MODE) syncSafariNode(node)
}

function addEdgeFromCell(c: CellVO) {
  const g = graph.value!
  if (!c.source?.cell || !c.target?.cell) return
  if (!g.getCellById(c.source.cell) || !g.getCellById(c.target.cell)) return
  const added = g.addEdge({
    id: c.id,
    shape: 'x6-edge',
    source: { cell: c.source.cell, port: c.source.port || 'out' },
    target: { cell: c.target.cell, port: c.target.port || 'in' },
    data: { label: c.label, kind: c.kind }
  })
  renderEdgeLabel(added as Edge)
}

let draggingType: number = NodeType.POLICY

function onDragStart(e: DragEvent, type: number) {
  draggingType = type
  e.dataTransfer?.setData('nodeType', String(type))
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy'
}

async function onDrop(e: DragEvent) {
  e.preventDefault()
  const type = Number(e.dataTransfer?.getData('nodeType') || draggingType)
  const g = graph.value
  if (!g || !versionId.value) return

  const meta = getNodeMeta(type)
  if (meta.unique && hasType(g, type)) {
    ElMessage.warning(`Only one "${meta.label}" node is allowed`)
    return
  }
  const point = g.clientToLocal(e.clientX, e.clientY)
  const nodeCode = `n_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
  const gNode = g.addNode({
    id: `n_${nodeCode}`,
    shape: 'x6-node',
    x: point.x - meta.width / 2,
    y: point.y - meta.height / 2,
    width: meta.width,
    height: meta.height,
    data: {
      nodeName: `${meta.label}${countType(g, type) + 1}`,
      nodeCode,
      nodeType: type,
      knowledge: []
    } as NodeData
  })
  if (SAFARI_MODE) syncSafariNode(gNode)
  g.select(gNode)
  const data = { ...gNode.getData() } as NodeData
  if (autoPersist.value && versionId.value) {
    try {
      const pos = gNode.getPosition()
      const nodeId = await saveNode({
        versionId: versionId.value,
        nodeCode,
        nodeName: data.nodeName,
        nodeType: type,
        nodeX: pos.x,
        nodeY: pos.y
      })
      gNode.setData({ ...data, nodeId })
      currentNodeData.value = { ...data, nodeId }
    } catch {
      unsavedEdits.value = true
      ElMessage.warning('Node added to canvas, but auto-save failed — click "Save" to retry')
    }
  } else {
    currentNodeData.value = { ...data, nodeId: undefined }
    unsavedEdits.value = true
  }
}

function hasType(g: Graph, type: number): boolean {
  return g.getNodes().some((n) => n.getData()?.nodeType === type)
}
function countType(g: Graph, type: number): number {
  return g.getNodes().filter((n) => n.getData()?.nodeType === type).length
}

function onNodeDataUpdate(partial: Partial<NodeData>) {
  const g = graph.value
  if (!g || !currentNodeData.value) return
  const nodeId = currentNodeData.value.nodeId
  if (!nodeId) {
    const selected = g.getSelectedCells().find((c) => c.isNode())
    if (selected) {
      selected.setData({ ...selected.getData(), ...partial })
      currentNodeData.value = { nodeId: undefined, ...selected.getData() }
    }
    return
  }
  const cell = g.getCellById(`n_${nodeId}`)
  if (cell) cell.setData({ ...cell.getData(), ...partial })
  currentNodeData.value = { ...currentNodeData.value, ...partial }
  if (autoPersist.value) {
    scheduleNodeSave(nodeId, partial)
  } else {
    unsavedEdits.value = true
  }
}

async function onDeleteNode() {
  const g = graph.value
  if (!g) return
  const cells = g.getSelectedCells().filter((c) => c.isNode())
  if (!cells.length) return
  await ElMessageBox.confirm(`Delete the ${cells.length} selected node(s)?`, 'Delete Confirmation', {
    type: 'warning',
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel'
  })
  g.removeCells(cells)
  currentNodeData.value = undefined
}

function serialize(): GraphVO {
  const g = graph.value!
  const cells: CellVO[] = []
  g.getNodes().forEach((n) => {
    const pos = n.getPosition()
    const size = n.getSize()
    const data = n.getData() || {}
    cells.push({
      id: n.id,
      shape: 'x6-node',
      position: { x: pos.x, y: pos.y },
      size: { width: size.width, height: size.height },
      data: { ...data, nodeJson: data.nodeJson ?? {} }
    })
  })
  g.getEdges().forEach((e) => {
    const src = e.getSourceCellId()
    const tgt = e.getTargetCellId()
    if (!src || !tgt) return
    const ed = (e.getData() || {}) as { label?: string; kind?: number }
    cells.push({
      id: e.id,
      shape: 'x6-edge',
      source: { cell: src, port: 'out' },
      target: { cell: tgt, port: 'in' },
      label: ed.label ?? 'Pass',
      kind: ed.kind ?? 1
    })
  })
  return { versionId: versionId.value!, zoom: g.zoom(), cells }
}

async function handleSave() {
  if (!versionId.value) {
    ElMessage.warning('Select a version first')
    return
  }
  if (currentVersion.value?.bootState === 1) {
    try {
      await ElMessageBox.confirm(
        'This is a "Running" version: saving only updates the config; production keeps executing the published artifact until you "Publish" again.' +
        ' To iterate without affecting production, use "Copy current version as new draft". Confirm save?',
        'Save Running Version',
        { type: 'warning', confirmButtonText: 'Save Anyway', cancelButtonText: 'Cancel' }
      )
    } catch {
      return
    }
  }
  saving.value = true
  try {
    await flushNodeSaves()
    await flushNow()
    await saveGraph(versionId.value, serialize())
    unsavedEdits.value = false
    ElMessage.success(
      currentVersion.value?.bootState === 1 ? 'Saved (running version: republish to take effect)' : 'Saved'
    )
  } finally {
    saving.value = false
  }
}

async function handlePublish() {
  if (!versionId.value) return
  await ElMessageBox.confirm('After publishing, this version goes live and other versions of the same engine are taken offline automatically.', 'Publish Confirmation', {
    type: 'warning',
    confirmButtonText: 'Publish',
    cancelButtonText: 'Cancel'
  })
  publishing.value = true
  try {
    await flushNodeSaves()
    await flushNow()
    await saveGraph(versionId.value, serialize())
    unsavedEdits.value = false
    await publishVersion(versionId.value)
    ElMessage.success(`Published: v${currentVersion.value?.version}.${currentVersion.value?.subVersion ?? 0} is now live`)
    if (engineId.value) versions.value = await listVersions(engineId.value)
  } finally {
    publishing.value = false
  }
}

const grayVisible = ref(false)

function handleAutoLayout() {
  const g = graph.value
  if (!g || !g.getNodes().length) return
  const moved = autoLayout(g)
  g.zoomToFit({ padding: 40, maxScale: 1, minScale: 0.4 })
  g.centerContent()
  zoomLevel.value = g.zoom()
  ElMessage.success(moved ? `Relayout done (${moved} nodes)` : 'Layout is already tidy')
}

function undo() {
  graph.value?.undo()
}
function redo() {
  graph.value?.redo()
}
function zoom(delta: number) {
  const g = graph.value
  if (!g) return
  const next = Math.min(2, Math.max(0.3, g.zoom() + delta))
  g.zoomTo(next)
  zoomLevel.value = g.zoom()
}
function fitContent() {
  const g = graph.value
  if (!g) return
  g.zoomToFit({ padding: 40, maxScale: 1, minScale: 0.4 })
  g.centerContent()
  zoomLevel.value = g.zoom()
}
</script>

<style scoped lang="scss">
@use '@/styles/tokens.scss';

.designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--c-bg);
  overflow: hidden;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--h-header);
  flex-shrink: 0;
  padding: 0 var(--sp-4);
  gap: var(--sp-4);
  background: var(--c-surface-raised);

  &__lead {
    display: flex;
    align-items: center;
    gap: var(--sp-3);
    min-width: 0;
  }

  border-bottom: 1px solid var(--c-border);

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    flex-shrink: 0;
    flex-wrap: nowrap;
  }
}

.picker {
  display: flex;
  align-items: center;
  gap: var(--sp-2);

  &__select {
    width: 188px;
  }

  &__version {
    width: 140px;
  }
}

.ver-del-danger {
  color: var(--c-danger);
}

.save-dot {
  position: relative;

  &.has-unsaved::after {
    content: '';
    position: absolute;
    top: -2px;
    right: -2px;
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--c-warning, #e6a23c);
  }
}

.edge-menu {
  position: fixed;
  z-index: 2100;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  background: var(--c-surface-raised);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  box-shadow: var(--sh-lg);

  &__title {
    font-size: var(--fs-2xs);
    color: var(--c-text-tertiary);
    margin-right: 2px;
  }

  &__opt {
    padding: 3px 10px;
    border: 1px solid var(--c-border);
    border-radius: var(--r-sm);
    background: transparent;
    font-size: var(--fs-xs);
    color: var(--c-text-secondary);
    cursor: pointer;

    &:hover {
      border-color: var(--c-primary);
      color: var(--c-primary);
    }

    &.is-pass:hover,
    &.is-pass.is-on {
      border-color: #34a46f;
      color: #fff;
      background: #34a46f;
    }

    &.is-reject:hover,
    &.is-reject.is-on {
      border-color: #d54941;
      color: #fff;
      background: #d54941;
    }

    &.is-manual:hover,
    &.is-manual.is-on {
      border-color: #e69b00;
      color: #fff;
      background: #e69b00;
    }
  }

  &__close {
    border: none;
    background: transparent;
    color: var(--c-text-tertiary);
    font-size: 14px;
    cursor: pointer;
    line-height: 1;
  }

  &__sep {
    width: 1px;
    height: 14px;
    background: var(--c-border);
  }

  &__del {
    border: none;
    background: transparent;
    color: var(--c-danger);
    font-size: var(--fs-xs);
    cursor: pointer;
    white-space: nowrap;

    &:hover {
      text-decoration: underline;
    }
  }
}

.edge-menu__mask {
  position: fixed;
  inset: 0;
  z-index: 2050;
}

.ver-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px dashed var(--c-border-strong);
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--c-text-secondary);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-standard);

  &:hover:not(:disabled) {
    border-color: var(--c-primary);
    color: var(--c-primary);
    background: var(--c-primary-soft);
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}

.btn--sm {
  height: 30px;
  padding: 0 12px;
  font-size: var(--fs-xs);
  white-space: nowrap;
}

.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);

  &__name {
    font-weight: var(--fw-medium);
  }

  &__code {
    color: var(--c-text-tertiary);
    font-size: var(--fs-xs);
    font-family: var(--font-mono);
  }

  &__badge {
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    color: var(--c-success);
    background: var(--c-success-soft);
    padding: 1px 6px;
    border-radius: var(--r-full);
  }
}

.seg {
  display: flex;
  align-items: center;
  background: var(--c-fill-quaternary);
  border-radius: var(--r-sm);
  padding: 2px;

  &__btn {
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 30px;
    height: 26px;
    padding: 0 6px;
    border: none;
    background: transparent;
    color: var(--c-text-secondary);
    border-radius: var(--r-xs);
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-standard),
      color var(--dur-fast) var(--ease-standard);

    &:hover:not(:disabled) {
      background: var(--c-surface);
      color: var(--c-text);
      box-shadow: var(--sh-xs);
    }

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }

    &--zoom {
      min-width: 52px;
      font-size: var(--fs-xs);
      font-weight: var(--fw-medium);
      font-variant-numeric: tabular-nums;
    }
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
  letter-spacing: var(--ls-snug);
  cursor: pointer;
  box-shadow: var(--sh-xs);
  transition: transform var(--dur-fast) var(--ease-standard),
    box-shadow var(--dur-fast) var(--ease-standard),
    background-color var(--dur-fast) var(--ease-standard),
    border-color var(--dur-fast) var(--ease-standard);

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

  &__caret {
    margin-left: 2px;
    font-size: 10px;
    color: var(--c-text-tertiary);
  }

  &--primary {
    background: var(--c-primary);
    border-color: transparent;
    color: var(--c-text-inverse);
    box-shadow: var(--sh-xs), var(--sh-inset);

    &:hover:not(:disabled) {
      background: var(--c-primary-hover);
      box-shadow: var(--sh-sm), var(--sh-inset);
    }
  }

  &--ghost {
    background: transparent;
    border-color: transparent;
    box-shadow: none;
    color: var(--c-text-secondary);

    &:hover:not(:disabled) {
      background: var(--c-fill-quaternary);
      color: var(--c-text);
      border-color: transparent;
      box-shadow: none;
    }
  }
}

.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.stencil {
  width: var(--w-stencil);
  flex-shrink: 0;
  padding: var(--sp-4) var(--sp-3) var(--sp-6);
  background: var(--c-surface-sunken);
  border-right: 1px solid var(--c-border);

  &__group {
    margin-bottom: var(--sp-5);
  }

  &__label {
    padding: 0 var(--sp-2) var(--sp-2);
    font-size: var(--fs-2xs);
    font-weight: var(--fw-semibold);
    color: var(--c-text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.06em;
  }

  &__tip {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: var(--sp-4) var(--sp-2) 0;
    padding: var(--sp-3);
    background: var(--c-fill-quaternary);
    border-radius: var(--r-sm);
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
    line-height: var(--lh-snug);
  }
}

.item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 7px var(--sp-2);
  margin-bottom: 2px;
  border-radius: var(--r-sm);
  cursor: grab;
  user-select: none;
  transition: background-color var(--dur-fast) var(--ease-standard),
    transform var(--dur-fast) var(--ease-standard);

  &:hover {
    background: var(--c-surface);
    box-shadow: var(--sh-xs);
    transform: translateX(2px);

    .item__grip {
      opacity: 1;
    }
  }

  &:active {
    cursor: grabbing;
    transform: scale(0.98);
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: var(--r-xs);
    flex-shrink: 0;
  }

  &__text {
    flex: 1;
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text);
    letter-spacing: var(--ls-snug);
  }

  &__grip {
    color: var(--c-text-quaternary);
    opacity: 0;
    transition: opacity var(--dur-fast) var(--ease-standard);
  }
}

.canvas-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
}

.canvas {
  width: 100%;
  height: 100%;
}

.overlap-tip {
  position: absolute;
  top: var(--sp-4);
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 6px 8px 6px 12px;
  border-radius: var(--r-full);
  background: var(--c-surface-raised);
  border: 1px solid var(--c-border);
  box-shadow: var(--sh-md);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  color: var(--c-text-secondary);
  white-space: nowrap;

  &__save {
    height: 22px;
    padding: 0 var(--sp-3);
    border: none;
    border-radius: var(--r-full);
    background: var(--c-primary);
    color: var(--c-text-inverse);
    font-size: var(--fs-xs);
    font-weight: var(--fw-medium);
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-primary-hover);
    }
  }

  &__close {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    padding: 0;
    border: none;
    border-radius: var(--r-full);
    background: transparent;
    color: var(--c-text-tertiary);
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-standard);

    &:hover {
      background: var(--c-fill-quaternary);
      color: var(--c-text);
    }
  }
}

.tips-fade-enter-active,
.tips-fade-leave-active {
  transition: opacity var(--dur-normal) var(--ease-standard),
    transform var(--dur-normal) var(--ease-spring);
}

.tips-fade-enter-from,
.tips-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}

.floating-status {
  position: absolute;
  top: var(--sp-4);
  left: var(--sp-4);
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px 5px 10px;
  border-radius: var(--r-full);
  background: var(--c-surface-raised);
  border: 1px solid var(--c-border);
  box-shadow: var(--sh-sm);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  color: var(--c-text-secondary);
  pointer-events: none;

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: var(--r-full);
    background: var(--c-text-quaternary);
  }

  &.is-live {
    color: var(--c-success);

    .floating-status__dot {
      background: var(--c-success);
      box-shadow: 0 0 0 3px var(--c-success-soft);
    }
  }
}

.canvas-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  pointer-events: none;

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    border-radius: var(--r-lg);
    background: var(--c-fill-quaternary);
    color: var(--c-text-quaternary);
    margin-bottom: var(--sp-2);
  }

  &__title {
    font-size: var(--fs-md);
    font-weight: var(--fw-semibold);
    color: var(--c-text-secondary);
    letter-spacing: var(--ls-snug);
  }

  &__desc {
    font-size: var(--fs-sm);
    color: var(--c-text-tertiary);
  }
}

.props {
  width: var(--w-props);
  flex-shrink: 0;
  padding: var(--sp-5) var(--sp-4);
  background: var(--c-surface);
  border-left: 1px solid var(--c-border);

  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 6px;
    text-align: center;
  }

  &__empty-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    border-radius: var(--r-md);
    background: var(--c-fill-quaternary);
    color: var(--c-text-quaternary);
    margin-bottom: var(--sp-2);
  }

  &__empty-title {
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
    color: var(--c-text-secondary);
  }

  &__empty-desc {
    font-size: var(--fs-xs);
    color: var(--c-text-tertiary);
  }
}

@media (max-width: 1180px) {
  .props {
    display: none;
  }
}
</style>
