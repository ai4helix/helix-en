
export interface DecisionField {
  id: number
  fieldEn: string
  fieldCn: string
  fieldTypeid?: number
  fieldTypeName?: string
  valueType?: number
  isOutput?: number
  catalogId?: number | null
  catalogName?: string | null
}

export interface DecisionTestPayload {
  engineCode: string
  versionId?: number | null
  remark?: string
  samples: Record<string, unknown>[]
  withTrace?: boolean
}
