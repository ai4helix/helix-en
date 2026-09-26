
export enum NodeType {
  START = 1,
  POLICY = 2,
  CLASSIFY = 3,
  SCORECARD = 4,
  BLACKLIST = 5,
  WHITELIST = 6,
  SANDBOX = 7,
  HELIX_LEVEL = 8,
  DECISION = 9,
  QUOTA_CALC = 10,
  REPORT = 11,
  CUSTOMIZE = 12,
  COMPLEX_RULE = 13
}

export enum KnowledgeType {
  RULE = 1,
  SCORECARD = 2,
  DECISION_OPTION = 3,
  COMPLEX_RULE = 4
}

export interface NodeTypeMeta {
  type: NodeType
  label: string
  color: string
  icon: string
  width: number
  height: number
  unique?: boolean
}

export interface KnowledgeRef {
  knowledgeId?: number
  knowledgeType?: number
  name?: string
  code?: string
}

export interface NodeData {
  nodeId?: number
  nodeName?: string
  nodeCode?: string
  nodeType?: number
  nodeOrder?: number
  nodeX?: number
  nodeY?: number
  nodeJson?: Record<string, any>
  nodeScript?: string
  knowledge?: KnowledgeRef[]
  nextNodes?: string[]
  innerListDbs?: number[]
  outerListDbs?: number[]
}

export interface CellVO {
  id: string
  shape: string
  position?: { x: number; y: number }
  size?: { width: number; height: number }
  source?: { cell: string; port?: string }
  target?: { cell: string; port?: string }
  data?: NodeData
  label?: string
  kind?: number
}

export interface GraphVO {
  versionId: number
  zoom?: number
  cells: CellVO[]
}

export interface Result<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface Engine {
  id: number
  code: string
  name: string
  description?: string
  status?: number
  organId?: number
}

export interface EngineVersion {
  id: number
  engineId: number
  version: number
  subVersion?: number
  bootState: number
  status?: number
  layout?: number
  userId?: number
  createdTime?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
  pages: number
}
