
export type LineageNodeType = 'VERSION' | 'NODE' | 'KNOWLEDGE' | 'FIELD'

export interface GraphNode {
  id: string
  type: LineageNodeType
  label: string
  subType?: string
  refId?: number
  refType?: string
  dim?: boolean
  selected?: boolean
}

export type EdgeKind = 'FIELD_KNOWLEDGE' | 'KNOWLEDGE_NODE' | 'NODE_VERSION'

export interface GraphEdge {
  source: string
  target: string
  kind: EdgeKind
}

export interface LineageGraphDTO {
  version: GraphNode
  nodes: GraphNode[]
  edges: GraphEdge[]
  nodeCount: number
  knowledgeCount: number
  fieldCount: number
}
