import { Graph } from '@antv/x6'
import type { Node } from '@antv/x6'
import { getNodeMeta } from './nodeMeta'


const NAME_MAX = 10

export function isSafari(): boolean {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent
  return /safari/i.test(ua) && !/chrome|chromium|crios|edg|fxios|android/i.test(ua)
}

export function registerSafariShape(): void {
  Graph.registerNode('x6-node', {
    shape: 'x6-node',
    inherit: 'rect',
    width: 172,
    height: 54,
    ports: {
      groups: {
        in: { position: 'left', attrs: { circle: { r: 8, magnet: true, stroke: 'transparent', fill: 'transparent' } } },
        out: { position: 'right', attrs: { circle: { r: 8, magnet: true, stroke: 'transparent', fill: 'transparent' } } }
      },
      items: [
        { id: 'in', group: 'in' },
        { id: 'out', group: 'out' }
      ]
    },
    markup: [
      { tagName: 'rect', selector: 'body' },
      { tagName: 'rect', selector: 'rail' },
      { tagName: 'text', selector: 'name' },
      { tagName: 'text', selector: 'meta' }
    ],
    attrs: {
      body: {
        x: 0.5,
        y: 0.5,
        width: 171,
        height: 53,
        fill: '#ffffff',
        stroke: '#d2d2d7',
        strokeWidth: 1,
        rx: 10,
        ry: 10
      },
      rail: {
        x: 12,
        y: 15,
        width: 4,
        height: 24,
        rx: 2,
        ry: 2,
        fill: '#0a84ff'
      },
      name: {
        x: 0,
        y: -9,
        fill: '#1d1d1f',
        fontSize: 13,
        fontWeight: 600,
        fontFamily: '-apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif'
      },
      meta: {
        x: 0,
        y: 11,
        fill: '#86868b',
        fontSize: 11,
        fontFamily: '-apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif'
      }
    }
  })
}

export function syncSafariNode(cell: Node, data?: Record<string, unknown>): void {
  if (!cell || typeof (cell as { isNode?: unknown }).isNode !== 'function' || !cell.isNode()) {
    return
  }
  const d = (data ?? cell.getData() ?? {}) as { nodeName?: string; nodeType?: number }
  const meta = getNodeMeta(d.nodeType ?? 0)
  const rawName = d.nodeName || meta.label
  const name = rawName.length > NAME_MAX ? rawName.slice(0, NAME_MAX) + '…' : rawName
  cell.setAttrs({
    rail: { fill: meta.color },
    name: { text: name },
    meta: { text: meta.label }
  })
}
