import { Graph } from '@antv/x6'
import { register } from '@antv/x6-vue-shape'
import ConditionNode from '@/views/flow/components/ConditionNode.vue'
import GroupNode from '@/views/flow/components/GroupNode.vue'
import type { RuleConditionNode } from '@/types/rule'
import { opLabel } from '@/constants/operators'


const LEAF_W = 220
const LEAF_H = 64
const GROUP_W = 96
const GROUP_H = 40
const GAP_X = 32
const LEVEL_H = 96

const portGroups = {
  in: {
    position: 'top',
    attrs: { circle: { r: 4, magnet: true, stroke: '#c0c4cc', strokeWidth: 1, fill: '#fff' } }
  },
  out: {
    position: 'bottom',
    attrs: { circle: { r: 4, magnet: true, stroke: '#c0c4cc', strokeWidth: 1, fill: '#fff' } }
  }
}
const portItems = [
  { id: 'in', group: 'in' },
  { id: 'out', group: 'out' }
]

let shapesRegistered = false
function ensureShapes() {
  if (shapesRegistered) return
  register({
    shape: 'condition-node',
    width: LEAF_W,
    height: LEAF_H,
    component: ConditionNode,
    ports: { groups: portGroups, items: portItems }
  })
  register({
    shape: 'group-node',
    width: GROUP_W,
    height: GROUP_H,
    component: GroupNode,
    ports: { groups: portGroups, items: portItems }
  })
  shapesRegistered = true
}

function selfW(n: RuleConditionNode) {
  return n.nodeType === 1 ? LEAF_W : GROUP_W
}
function selfH(n: RuleConditionNode) {
  return n.nodeType === 1 ? LEAF_H : GROUP_H
}

interface Box {
  node: RuleConditionNode
  width: number
  x: number
  y: number
  children: Box[]
}

function build(roots: RuleConditionNode[], depth: number): { boxes: Box[]; total: number } {
  const boxes: Box[] = []
  let total = 0
  for (const n of roots) {
    const child =
      n.children && n.children.length
        ? build(n.children, depth + 1)
        : { boxes: [] as Box[], total: 0 }
    const width = Math.max(selfW(n), child.total)
    boxes.push({ node: n, width, x: 0, y: depth * LEVEL_H, children: child.boxes })
    total += width + GAP_X
  }
  return { boxes, total: Math.max(0, total - GAP_X) }
}

function assign(boxes: Box[], left: number) {
  for (const b of boxes) {
    const center = left + b.width / 2
    b.x = center - selfW(b.node) / 2
    const childTotal =
      b.children.reduce((s, c) => s + c.width, 0) +
      (b.children.length ? GAP_X * (b.children.length - 1) : 0)
    const childLeft = left + (b.width - childTotal) / 2
    assign(b.children, childLeft)
    left += b.width + GAP_X
  }
}

function layout(roots: RuleConditionNode[]): Map<RuleConditionNode, { x: number; y: number }> {
  const { boxes } = build(roots, 0)
  assign(boxes, 0)
  const map = new Map<RuleConditionNode, { x: number; y: number }>()
  const collect = (bs: Box[]) => {
    for (const b of bs) {
      map.set(b.node, { x: b.x, y: b.y })
      collect(b.children)
    }
  }
  collect(boxes)
  return map
}

export interface NodeEval {
  hit?: boolean
  actual?: unknown
  unknownOperator?: boolean
}

export function useConditionGraph() {
  let graph: Graph | null = null

  function mount(el: HTMLElement) {
    ensureShapes()
    graph = new Graph({
      container: el,
      autoResize: true,
      background: { color: '#f7f8fa' },
      grid: { visible: true, type: 'dot', size: 12, args: { color: '#e5e7eb', thickness: 1 } },
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
    return graph
  }

  function render(tree: RuleConditionNode[], evals?: Record<string, NodeEval>) {
    if (!graph) return
    graph.clearCells()

    const pos = layout(tree)
    let counter = 0
    const walk = (nodes: RuleConditionNode[], parentCellId?: string) => {
      for (const n of nodes) {
        const cellId = `cn_${counter++}`
        const p = pos.get(n)!
        const isLeaf = n.nodeType === 1
        graph!.addNode({
          id: cellId,
          shape: isLeaf ? 'condition-node' : 'group-node',
          x: p.x,
          y: p.y,
          width: selfW(n),
          height: selfH(n),
          data: isLeaf
            ? {
                fieldCode: n.fieldCode,
                operator: opLabel(n.operator),
                value: n.value,
                eval: evals ? evals[n.fieldCode ?? ''] : undefined
              }
            : { nodeType: n.nodeType }
        })
        if (parentCellId) {
          graph!.addEdge({
            shape: 'edge',
            source: { cell: parentCellId, port: 'out' },
            target: { cell: cellId, port: 'in' },
            attrs: {
              line: { stroke: '#9aa3b2', strokeWidth: 1.5, targetMarker: { name: 'block', size: 6 } }
            },
            zIndex: -1
          })
        }
        if (n.children && n.children.length) walk(n.children, cellId)
      }
    }
    walk(tree)
    graph.zoomToFit({ padding: 30, maxScale: 1, minScale: 0.3 })
    graph.centerContent()
  }

  function dispose() {
    if (graph) {
      graph.dispose()
      graph = null
    }
  }

  return { mount, render, dispose }
}
