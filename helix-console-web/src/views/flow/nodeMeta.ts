import { NodeType, type NodeTypeMeta } from '@/types/flow'

/**
 * Node metadata table. Drives the left stencil panel, canvas node styles and
 * the props panel; adding a new node type only requires appending an entry here.
 *
 * Colors: grouped by business semantics so similar nodes share a color family,
 * making them easy to identify in complex flows.
 *  - Entry / branching: neutral gray-green
 *  - Rule judgment: blue (reject-leaning red, pass-leaning green)
 *  - Calculation / scoring: cyan-purple
 *  - Lists: red/yellow warning colors
 *  - Output: magenta
 */
export const NODE_META: NodeTypeMeta[] = [
  { type: NodeType.START, label: 'Start', color: '#30d158', icon: 'VideoPlay', width: 108, height: 46, unique: true },
  { type: NodeType.POLICY, label: 'Policy Rule', color: '#0a84ff', icon: 'Document', width: 172, height: 54 },
  { type: NodeType.CLASSIFY, label: 'Customer Segment', color: '#5e5ce6', icon: 'Share', width: 172, height: 54 },
  { type: NodeType.SCORECARD, label: 'Scorecard', color: '#40c8e0', icon: 'DataAnalysis', width: 172, height: 54 },
  { type: NodeType.BLACKLIST, label: 'Blacklist', color: '#ff453a', icon: 'CircleClose', width: 172, height: 54 },
  { type: NodeType.WHITELIST, label: 'Whitelist', color: '#ff9f0a', icon: 'CircleCheck', width: 172, height: 54 },
  { type: NodeType.SANDBOX, label: 'Sandbox Ratio', color: '#ff9f0a', icon: 'PieChart', width: 172, height: 54 },
  { type: NodeType.HELIX_LEVEL, label: 'Credit Rating', color: '#5e5ce6', icon: 'Medal', width: 172, height: 54 },
  { type: NodeType.DECISION, label: 'Decision Option', color: '#bf5af2', icon: 'Promotion', width: 172, height: 54 },
  { type: NodeType.QUOTA_CALC, label: 'Quota Calc', color: '#40c8e0', icon: 'Money', width: 172, height: 54 },
  { type: NodeType.REPORT, label: 'Report Analysis', color: '#8e8e93', icon: 'Histogram', width: 172, height: 54 },
  { type: NodeType.CUSTOMIZE, label: 'Custom Node', color: '#8e8e93', icon: 'Grid', width: 172, height: 54 },
  { type: NodeType.COMPLEX_RULE, label: 'Complex Rule', color: '#ff6482', icon: 'SetUp', width: 172, height: 54 }
]

const metaMap = new Map<number, NodeTypeMeta>(NODE_META.map((m) => [m.type, m]))

export function getNodeMeta(type: number): NodeTypeMeta {
  return (
    metaMap.get(type) || {
      type,
      label: 'Unknown Node',
      color: '#8e8e93',
      icon: 'QuestionFilled',
      width: 172,
      height: 54
    }
  )
}

/** Stencil panel display order and group names, grouped by business semantics */
export const STENCIL_GROUPS: Array<{ title: string; types: NodeType[] }> = [
  { title: 'Flow Control', types: [NodeType.START, NodeType.SANDBOX] },
  { title: 'Rule Judgment', types: [NodeType.POLICY, NodeType.COMPLEX_RULE, NodeType.CLASSIFY] },
  { title: 'List Validation', types: [NodeType.BLACKLIST, NodeType.WHITELIST] },
  { title: 'Scoring & Calculation', types: [NodeType.SCORECARD, NodeType.HELIX_LEVEL, NodeType.QUOTA_CALC] },
  { title: 'Output', types: [NodeType.DECISION, NodeType.REPORT, NodeType.CUSTOMIZE] }
]

/** Node types that reference the knowledge base (rules / scorecards) */
export const KNOWLEDGE_NODE_TYPES = [
  NodeType.POLICY,
  NodeType.SCORECARD,
  NodeType.COMPLEX_RULE,
  NodeType.DECISION
]

/** Node types that reference list DBs */
export const LIST_DB_NODE_TYPES = [NodeType.BLACKLIST, NodeType.WHITELIST]

/** Node types that can act as an endpoint (decision nodes) */
export const TERMINAL_NODE_TYPES = [NodeType.DECISION]
