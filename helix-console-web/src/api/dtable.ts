import request from './request'


export interface DtColumn {
  colType: 1 | 2
  fieldCode: string
  operator: string
  title: string
  seq: number
}

export interface DtCell {
  colIndex: number
  value: string
}

export interface DtRow {
  rowNo: number
  resultType: string | null
  resultValue: string | null
  scoreValue: number | null
  enabled: number
  remark: string | null
  cells: DtCell[]
}

export interface DecisionTableSaveDTO {
  id?: number
  code: string
  name: string
  description?: string
  /** FIRST / ALL */
  hitPolicy: string
  engineId?: number | null
  parentId?: number | null
  status: number
  remark?: string
  columns: DtColumn[]
  rows: DtRow[]
}

export interface DecisionTableBrief {
  id: number
  code: string
  name: string
  description: string | null
  hitPolicy: string
  engineId: number | null
  status: number
  columnCount?: number
  rowCount?: number
  updatedTime?: string
}

export function listDTables(keyword?: string): Promise<DecisionTableBrief[]> {
  return request.get('/engine/dtable/list', { params: { keyword } })
}

export function getDTable(id: number): Promise<Record<string, unknown>> {
  return request.get(`/engine/dtable/${id}`)
}

export function saveDTable(dto: DecisionTableSaveDTO): Promise<number> {
  return request.post('/engine/dtable/save', dto)
}

export function removeDTable(id: number): Promise<void> {
  return request.delete(`/engine/dtable/${id}`)
}
