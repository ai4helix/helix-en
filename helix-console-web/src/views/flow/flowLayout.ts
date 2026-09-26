import type { Graph, Node } from '@antv/x6'
import { NodeType } from '@/types/flow'

export const LAYOUT = {
  originX: 60,
  originY: 60,
  gapX: 244,
  gapY: 88,
  overlapPadding: 8
} as const

export function hasOverlap(graph: Graph): boolean {
  const nodes = graph.getNodes()
  if (nodes.length < 2) return false

  const boxes = nodes.map((n) => {
    const { x, y } = n.getPosition()
    const { width, height } = n.getSize()
    return { x, y, w: width, h: height }
  })

  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      const a = boxes[i]
      const b = boxes[j]
      const overlapX = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x)
      const overlapY = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y)
      if (overlapX > LAYOUT.overlapPadding && overlapY > LAYOUT.overlapPadding) {
        return true
      }
    }
  }
  return false
}

export function autoLayout(graph: Graph): number {
  const nodes = graph.getNodes()
  if (!nodes.length) return 0

  const level = new Map<string, number>()
  const starts = nodes.filter((n) => n.getData()?.nodeType === NodeType.START)
  const queue: Array<{ id: string; lv: number }> = []

  if (starts.length) {
    starts.forEach((n) => {
      level.set(n.id, 0)
      queue.push({ id: n.id, lv: 0 })
    })
  } else {
    const hasIncoming = new Set<string>()
    nodes.forEach((n) => {
      graph.getIncomingEdges(n)?.forEach((e) => hasIncoming.add(n.id))
    })
    nodes
      .filter((n) => !hasIncoming.has(n.id))
      .forEach((n) => {
        level.set(n.id, 0)
        queue.push({ id: n.id, lv: 0 })
      })
  }

  if (!queue.length) {
    nodes.forEach((n) => {
      level.set(n.id, 0)
      queue.push({ id: n.id, lv: 0 })
    })
  }

  while (queue.length) {
    const cur = queue.shift()!
    const cell = graph.getCellById(cur.id)
    if (!cell || !cell.isNode()) continue
    graph.getOutgoingEdges(cell)?.forEach((edge) => {
      const target = edge.getTargetCellId()
      if (target && !level.has(target)) {
        level.set(target, cur.lv + 1)
        queue.push({ id: target, lv: cur.lv + 1 })
      }
    })
  }

  const maxLevel = level.size ? Math.max(...Array.from(level.values())) : 0
  nodes.forEach((n) => {
    if (!level.has(n.id)) level.set(n.id, maxLevel + 1)
  })

  const byLevel = new Map<number, Node[]>()
  nodes.forEach((n) => {
    const lv = level.get(n.id) ?? 0
    if (!byLevel.has(lv)) byLevel.set(lv, [])
    byLevel.get(lv)!.push(n)
  })

  byLevel.forEach((list) => {
    list.sort((a, b) => a.getPosition().y - b.getPosition().y)
  })

  let moved = 0

  Array.from(byLevel.keys())
    .sort((a, b) => a - b)
    .forEach((lv) => {
      const list = byLevel.get(lv)!
      const totalH = list.length * LAYOUT.gapY

      list.forEach((node, idx) => {
        const nextX = LAYOUT.originX + lv * LAYOUT.gapX
        const nextY = LAYOUT.originY + idx * LAYOUT.gapY - totalH / 2 + LAYOUT.gapY
        const pos = node.getPosition()
        if (Math.abs(pos.x - nextX) > 1 || Math.abs(pos.y - nextY) > 1) {
          node.setPosition(nextX, nextY)
          moved++
        }
      })
    })

  return moved
}
