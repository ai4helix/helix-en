import { Graph } from '@antv/x6'
import { register } from '@antv/x6-vue-shape'
import LineageNode from '@/views/datamanage/components/LineageNode.vue'
import type { GraphNode, LineageGraphDTO, LineageNodeType, EdgeKind } from '@/types/lineage'


const COL_W = 244
const GAP_Y = 80
const PAD_Y = 40
const NODE_W = 172
const NODE_H = 54

const COL_X: Record<LineageNodeType, number> = {
  FIELD: 0,
  KNOWLEDGE: COL_W,
  NODE: COL_W * 2,
  VERSION: COL_W * 3
}

let registered = false
function ensureRegistered() {
  if (registered) return
  register({
    shape: 'lineage-node',
    width: NODE_W,
    height: NODE_H,
    component: LineageNode
  })
  registered = true
}

const EDGE_COLOR = '#c0c4cc'
const VERSION_EDGE_COLOR = '#909399'
function edgeColor(kind: EdgeKind): string {
  return kind === 'NODE_VERSION' ? VERSION_EDGE_COLOR : EDGE_COLOR
}

export interface LineageGraphHandlers {
  onSelect?: (node: GraphNode | null) => void
}

interface EdgeMeta {
  cellId: string
  source: string
  target: string
  kind: EdgeKind
}

export function useLineageGraph() {
  let graph: Graph | null = null
  let handlers: LineageGraphHandlers = {}

  const cellToNode: Record<string, string> = {}
  const nodeToCell: Record<string, string> = {}
  const cellData: Record<string, GraphNode> = {}
  const adj: Record<string, string[]> = {}
  const radj: Record<string, string[]> = {}
  let edges: EdgeMeta[] = []

  let activeNodeId: string | null = null
  let keyword = ''
  let hiddenTypes = new Set<string>()

  function mount(el: HTMLElement, h?: LineageGraphHandlers): Graph {
    ensureRegistered()
    handlers = h || {}
    graph = new Graph({
      container: el,
      autoResize: true,
      background: { color: '#f7f8fa' },
      grid: { visible: true, type: 'dot', size: 12, args: { color: '#e9ebf0', thickness: 1 } },
      interacting: {
        nodeMovable: false,
        edgeMovable: false,
        magnetConnectable: false
      },
      connecting: {
        allowBlank: false,
        allowLoop: false,
        allowMulti: false,
        allowNode: false,
        allowEdge: false,
        allowPort: false
      },
      panning: { enabled: true, eventTypes: ['leftMouseDown', 'mouseWheel'] },
      mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'], minScale: 0.3, maxScale: 2 }
    })
    graph.on('node:click', ({ node }) => selectNode(node.id))
    graph.on('blank:click', () => {
      activeNodeId = null
      handlers.onSelect?.(null)
      refresh()
    })
    return graph
  }

  function render(data: LineageGraphDTO) {
    if (!graph) return
    graph.clearCells()
    for (const k of Object.keys(cellToNode)) delete cellToNode[k]
    for (const k of Object.keys(nodeToCell)) delete nodeToCell[k]
    for (const k of Object.keys(cellData)) delete cellData[k]
    for (const k of Object.keys(adj)) delete adj[k]
    for (const k of Object.keys(radj)) delete radj[k]
    edges = []

    const byType: Record<string, GraphNode[]> = { FIELD: [], KNOWLEDGE: [], NODE: [], VERSION: [] }
    data.nodes.forEach((n) => {
      ;(byType[n.type] = byType[n.type] || []).push(n)
    })

    const place = (list: GraphNode[]) => {
      list.forEach((node, i) => {
        const cellId = `n_${node.id}`
        const x = COL_X[node.type] + (COL_W - NODE_W) / 2
        const y = PAD_Y + i * GAP_Y
        graph!.addNode({
          id: cellId,
          shape: 'lineage-node',
          x,
          y,
          width: NODE_W,
          height: NODE_H,
          data: { ...node, dim: false, selected: false }
        })
        cellToNode[cellId] = node.id
        nodeToCell[node.id] = cellId
        cellData[cellId] = node
      })
    }
    place(byType.FIELD || [])
    place(byType.KNOWLEDGE || [])
    place(byType.NODE || [])
    const fieldH = ((byType.FIELD || []).length - 1) * GAP_Y
    const knowH = ((byType.KNOWLEDGE || []).length - 1) * GAP_Y
    const nodeH = ((byType.NODE || []).length - 1) * GAP_Y
    const mid = PAD_Y + Math.max(fieldH, knowH, nodeH) / 2
    ;(byType.VERSION || []).forEach((node) => {
      const cellId = `n_${node.id}`
      const x = COL_X.VERSION + (COL_W - NODE_W) / 2
      const y = Math.max(PAD_Y, mid - NODE_H / 2)
      graph!.addNode({
        id: cellId,
        shape: 'lineage-node',
        x,
        y,
        width: NODE_W,
        height: NODE_H,
        data: { ...node, dim: false, selected: false }
      })
      cellToNode[cellId] = node.id
      nodeToCell[node.id] = cellId
      cellData[cellId] = node
    })

    data.edges.forEach((e) => {
      const sCell = nodeToCell[e.source]
      const tCell = nodeToCell[e.target]
      if (!sCell || !tCell) return
      const cellId = graph!.addEdge({
        shape: 'edge',
        source: { cell: sCell },
        target: { cell: tCell },
        zIndex: -1,
        attrs: {
          line: {
            stroke: edgeColor(e.kind),
            strokeWidth: 1.5,
            targetMarker: { name: 'block', size: 6 },
            connector: { name: 'rounded', args: { radius: 8 } }
          }
        }
      }).id
      edges.push({ cellId, source: e.source, target: e.target, kind: e.kind })
      ;(adj[e.source] = adj[e.source] || []).push(e.target)
      ;(radj[e.target] = radj[e.target] || []).push(e.source)
    })

    graph.zoomToFit({ padding: 40, maxScale: 1, minScale: 0.3 })
    graph.centerContent()
  }

  function connectedBidirectional(startId: string): Set<string> {
    const seen = new Set<string>([startId])
    const fstack = [startId]
    while (fstack.length) {
      const cur = fstack.pop()!
      for (const nxt of adj[cur] || []) {
        if (!seen.has(nxt)) {
          seen.add(nxt)
          fstack.push(nxt)
        }
      }
    }
    const rstack = [startId]
    while (rstack.length) {
      const cur = rstack.pop()!
      for (const nxt of radj[cur] || []) {
        if (!seen.has(nxt)) {
          seen.add(nxt)
          rstack.push(nxt)
        }
      }
    }
    return seen
  }

  function selectNode(cellId: string) {
    const nId = cellToNode[cellId]
    if (!nId) return
    activeNodeId = nId
    handlers.onSelect?.(cellData[cellId])
    refresh()
  }

  function selectNodeById(nodeId: string) {
    const cellId = nodeToCell[nodeId]
    if (!cellId) return
    activeNodeId = nodeId
    handlers.onSelect?.(cellData[cellId])
    refresh()
    const cell = graph?.getCellById(cellId)
    if (cell) graph?.centerCell(cell as any)
  }

  function setKeyword(k: string) {
    keyword = k.trim()
    refresh()
  }

  function toggleType(t: string) {
    if (hiddenTypes.has(t)) hiddenTypes.delete(t)
    else hiddenTypes.add(t)
    refresh()
  }

  function reset() {
    activeNodeId = null
    keyword = ''
    hiddenTypes.clear()
    handlers.onSelect?.(null)
    refresh()
  }

  function matchesFilter(n: GraphNode): boolean {
    if (hiddenTypes.has(n.type)) return false
    if (keyword) {
      const hay = `${n.label} ${n.refType || ''} ${n.subType || ''}`.toLowerCase()
      if (!hay.includes(keyword.toLowerCase())) return false
    }
    return true
  }

  function refresh() {
    if (!graph) return
    const keep = activeNodeId ? connectedBidirectional(activeNodeId) : null
    const filteredOut: Record<string, boolean> = {}
    graph.getNodes().forEach((node) => {
      const nId = cellToNode[node.id]
      const data = cellData[node.id]
      if (!data) return
      const fo = !matchesFilter(data)
      filteredOut[nId] = fo
      const highlightOff = keep ? !keep.has(nId) : false
      node.setData({ ...data, dim: fo || highlightOff, selected: activeNodeId === nId })
    })
    graph.getEdges().forEach((edge) => {
      const meta = edges.find((e) => e.cellId === edge.id)
      if (!meta) return
      const sFo = filteredOut[meta.source]
      const tFo = filteredOut[meta.target]
      const baseDim = sFo || tFo
      const on = keep ? keep.has(meta.source) && keep.has(meta.target) : false
      const dim = baseDim || (keep ? !on : false)
      edge.attr('line/stroke', on ? '#409eff' : edgeColor(meta.kind))
      edge.attr('line/strokeWidth', on ? 2 : 1.5)
      edge.attr('line/opacity', dim ? 0.08 : 1)
    })
  }

  function fit() {
    if (!graph) return
    graph.zoomToFit({ padding: 40, maxScale: 1, minScale: 0.3 })
    graph.centerContent()
  }

  function dispose() {
    if (graph) {
      graph.dispose()
      graph = null
    }
    activeNodeId = null
    keyword = ''
    hiddenTypes.clear()
  }

  return {
    mount,
    render,
    selectNodeById,
    setKeyword,
    toggleType,
    reset,
    fit,
    dispose
  }
}

export default useLineageGraph
