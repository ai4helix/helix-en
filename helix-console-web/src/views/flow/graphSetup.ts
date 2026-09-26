import { Graph, type Edge } from '@antv/x6'
import { register, getTeleport } from '@antv/x6-vue-shape'
import { Selection } from '@antv/x6-plugin-selection'
import { Snapline } from '@antv/x6-plugin-snapline'
import { Keyboard } from '@antv/x6-plugin-keyboard'
import { History } from '@antv/x6-plugin-history'
import { Clipboard } from '@antv/x6-plugin-clipboard'
import { Transform } from '@antv/x6-plugin-transform'
import FlowNode from './components/FlowNode.vue'
import { isSafari, registerSafariShape, syncSafariNode } from './safariNode'

export const TeleportContainer = getTeleport()

export const SAFARI_MODE = isSafari()

let registered = false

const PORT_GROUPS = {
  in: { position: 'left', attrs: { circle: { r: 8, magnet: true, stroke: 'transparent', fill: 'transparent' } } },
  out: {
    position: 'right',
    attrs: { circle: { r: 8, magnet: true, stroke: 'transparent', fill: 'transparent' } }
  }
}

const PORT_ITEMS = [
  { id: 'in', group: 'in' },
  { id: 'out', group: 'out' }
]

export function registerShapes() {
  if (registered) return

  if (SAFARI_MODE) {
    registerSafariShape()
  } else {
    register({
      shape: 'x6-node',
      width: 172,
      height: 54,
      component: FlowNode,
      ports: { groups: PORT_GROUPS, items: PORT_ITEMS }
    })
  }

  Graph.registerEdge(
    'x6-edge',
    {
      inherit: 'edge',
      attrs: {
        wrap: {
          connection: true,
          strokeWidth: 16,
          strokeLinecap: 'round',
          stroke: 'transparent'
        },
        line: {
          stroke: '#a1a1a6',
          strokeWidth: 1.5,
          targetMarker: { name: 'block', width: 9, height: 7, offset: 2 }
        }
      },
      router: { name: 'manhattan', args: { padding: 16 } },
      connector: { name: 'rounded', args: { radius: 12 } },
      zIndex: -1
    },
    true
  )

  registered = true
}

export function createGraph(container: HTMLElement): Graph {
  registerShapes()

  const graph: Graph = new Graph({
    container,
    autoResize: true,
    background: { color: 'transparent' },
    grid: { visible: true, size: 16, type: 'dot', args: { color: 'rgba(120,120,128,0.18)', thickness: 1 } },
    panning: { enabled: true, eventTypes: ['leftMouseDown', 'mouseWheel'] },
    mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'], minScale: 0.3, maxScale: 2 },
    connecting: {
      anchor: 'center',
      connectionPoint: 'anchor',
      allowBlank: false,
      allowLoop: false,
      allowMulti: false,
      snap: { radius: 28 },
      highlight: true,
      createEdge(): Edge {
        return graph.createEdge({
          shape: 'x6-edge',
          attrs: {
            line: {
              stroke: '#0a84ff',
              strokeWidth: 1.8,
              strokeDasharray: '5 3',
              targetMarker: { name: 'block', width: 9, height: 7, offset: 2 }
            }
          }
        })
      },
      validateConnection({ sourceCell, targetCell, sourceMagnet, targetMagnet }) {
        if (!sourceCell || !targetCell) return false
        if (sourceCell.id === targetCell.id) return false
        if (sourceMagnet?.getAttribute('port') !== 'out') return false
        if (targetMagnet?.getAttribute('port') !== 'in') return false
        return true
      }
    },
    highlighting: {
      magnetAvailable: {
        name: 'stroke',
        args: { padding: 4, attrs: { stroke: '#0a84ff', strokeWidth: 2, fill: 'rgba(10,132,255,0.12)' } }
      },
      magnetAdsorbed: {
        name: 'stroke',
        args: { padding: 4, attrs: { stroke: '#0a84ff', strokeWidth: 2.5, fill: '#0a84ff' } }
      }
    }
  })

  graph.use(new Selection({ enabled: true, rubberband: true, showNodeSelectionBox: true, multiple: true }))
  graph.use(new Snapline({ enabled: true, sharp: true, tolerance: 6 }))
  graph.use(new Keyboard({ enabled: true, global: false }))
  graph.use(new History({ enabled: true }))
  graph.use(new Clipboard({ enabled: true }))
  graph.use(new Transform({ resizing: false, rotating: false }))

  graph.on('node:change:data', ({ node }) => {
    if (SAFARI_MODE) syncSafariNode(node)
  })

  bindShortcuts(graph)
  return graph
}

function bindShortcuts(graph: Graph) {
  graph.bindKey(['ctrl+c', 'meta+c'], () => {
    const cells = graph.getSelectedCells()
    if (cells.length) graph.copy(cells)
    return false
  })
  graph.bindKey(['ctrl+v', 'meta+v'], () => {
    if (!graph.isClipboardEmpty()) {
      const cells = graph.paste({ offset: 36 })
      graph.cleanSelection()
      graph.select(cells)
    }
    return false
  })
  graph.bindKey(['ctrl+z', 'meta+z'], () => {
    if (graph.canUndo()) graph.undo()
    return false
  })
  graph.bindKey(['ctrl+shift+z', 'meta+shift+z'], () => {
    if (graph.canRedo()) graph.redo()
    return false
  })
  graph.bindKey(['backspace', 'delete'], () => {
    const cells = graph.getSelectedCells()
    if (cells.length) graph.removeCells(cells)
    return false
  })
  graph.bindKey(['ctrl+a', 'meta+a'], () => {
    graph.select(graph.getCells())
    return false
  })
}
