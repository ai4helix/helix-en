import request from './request'
import type { PageResult } from '@/types/flow'
import type { RuleConditionNode } from '@/types/rule'


export interface RuleCondition {
  id?: number
  logical?: string
  operator: string
  fieldValue?: string
  fieldId: string
  fieldEn?: string
}

export interface RuleVO {
  id: number
  name: string
  code?: string
  description?: string
  priority?: number
  parentId?: number
  parentName?: string
  type?: number
  engineId?: number
  organId?: number
  status?: number
  ruleType?: number
  ruleAudit?: number
  score?: number
  isNon?: number
  content?: string
  created?: string
  updated?: string
  conditions?: RuleCondition[]
  showType?: number
  children?: RuleVO[]
}

export interface RuleSavePayload {
  id?: number
  name: string
  code?: string
  description?: string
  priority?: number
  parentId?: number
  type?: number
  engineId?: number
  organId?: number
  ruleType?: number
  ruleAudit?: number
  score?: number
  isNon?: number
  conditions: RuleCondition[]
}

export function pageRules(params: {
  parentId?: number
  engineId?: number
  status?: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<RuleVO>> {
  return request.get('/knowledge/rule/page', { params })
}

export function getRule(id: number): Promise<RuleVO> {
  return request.get(`/knowledge/rule/${id}`)
}

export function getRuleAst(id: number): Promise<RuleConditionNode[]> {
  return request.get(`/knowledge/rule/${id}/ast`)
}


export interface RuleBrief {
  id: number
  name: string
  code?: string
  content?: string
  resultTypeV2?: string
  scoreValue?: number
  priority?: number
}

export function listRuleBriefs(ids: number[]): Promise<RuleBrief[]> {
  return request.get('/knowledge/rule/briefs', { params: { ids: ids.join(',') } })
}

export function createRule(data: RuleSavePayload): Promise<number> {
  return request.post('/knowledge/rule', data)
}


export interface DryRunLeaf {
  field: string
  operator: string
  value: string
  actual?: unknown
  hit: boolean
  unknownOperator?: boolean
}

export interface DryRunResult {
  matched: boolean
  empty: boolean
  inverted: boolean
  leaves: DryRunLeaf[]
}

export function dryRunRule(payload: {
  conditions: RuleCondition[]
  isNon?: number
  variables?: Record<string, unknown>
}): Promise<DryRunResult> {
  return request.post('/knowledge/rule/dry-run', payload)
}

export function updateRule(data: RuleSavePayload): Promise<void> {
  return request.put('/knowledge/rule', data)
}

export function copyRule(id: number): Promise<number> {
  return request.post(`/knowledge/rule/${id}/copy`)
}

export function changeRuleStatus(ids: number[], status: number): Promise<void> {
  return request.post('/knowledge/rule/status', null, { params: { ids: ids.join(','), status } })
}

export function recycleRules(ids: number[]): Promise<void> {
  return request.post('/knowledge/rule/recycle', ids)
}

export function restoreRules(ids: number[]): Promise<void> {
  return request.post('/knowledge/rule/restore', ids)
}

export function deleteRulesPermanently(ids: number[]): Promise<void> {
  return request.delete('/knowledge/rule/permanent', { data: ids })
}


export interface ScorecardBin {
  min?: number
  max?: number
  score?: number
}

export interface ScorecardDimension {
  field: string
  fieldCn?: string
  weight?: number
  type?: string
  bins: ScorecardBin[]
}

export interface ScorecardVO {
  id: number
  name: string
  code?: string
  description?: string
  version?: string
  parentId?: number
  type?: number
  engineId?: number
  status?: number
  created?: string
}

export interface ScorecardDetail {
  id?: number
  name: string
  code?: string
  description?: string
  version?: string
  parentId?: number
  type?: number
  engineId?: number
  organId?: number
  dimensions: ScorecardDimension[]
  pd?: Record<string, string>
  odds?: Record<string, string>
}

export function pageScorecards(params: {
  parentId?: number
  engineId?: number
  status?: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<ScorecardVO>> {
  return request.get('/knowledge/scorecard/page', { params })
}

export function getScorecard(id: number): Promise<ScorecardDetail> {
  return request.get(`/knowledge/scorecard/${id}`)
}

export function createScorecard(data: ScorecardDetail): Promise<number> {
  return request.post('/knowledge/scorecard', data)
}

export function updateScorecard(data: ScorecardDetail): Promise<void> {
  return request.put('/knowledge/scorecard', data)
}

export function copyScorecard(id: number): Promise<number> {
  return request.post(`/knowledge/scorecard/${id}/copy`)
}

export function recycleScorecards(ids: number[]): Promise<void> {
  return request.post('/knowledge/scorecard/recycle', ids)
}

export function restoreScorecards(ids: number[]): Promise<void> {
  return request.post('/knowledge/scorecard/restore', ids)
}

export function deleteScorecardsPermanently(ids: number[]): Promise<void> {
  return request.delete('/knowledge/scorecard/permanent', { data: ids })
}


export interface TreeNode {
  id: number
  name: string
  parentId?: number
  type?: number
  treeType?: number
  engineId?: number
  count?: number
  system?: boolean
  children?: TreeNode[]
}

export function getTree(treeType: number, engineId?: number): Promise<TreeNode[]> {
  return request.get('/knowledge/tree', { params: { treeType, engineId } })
}

export function createTreeNode(data: {
  name: string
  parentId?: number
  treeType?: number
  engineId?: number
  organId?: number
}): Promise<number> {
  return request.post('/knowledge/tree', data)
}

export function renameTreeNode(id: number, name: string): Promise<void> {
  return request.put(`/knowledge/tree/${id}/name`, null, { params: { name } })
}

export function moveTreeNode(id: number, parentId?: number): Promise<void> {
  return request.put(`/knowledge/tree/${id}/parent`, null, { params: { parentId } })
}

export function deleteTreeNode(id: number): Promise<void> {
  return request.delete(`/knowledge/tree/${id}`)
}

export function moveRule(id: number, parentId: number): Promise<void> {
  return request.put(`/knowledge/rule/${id}/parent`, { parentId })
}


export interface FieldVO {
  id: number
  fieldEn: string
  fieldCn: string
  fieldTypeid?: number
  fieldTypeName?: string
  catalogId?: number
  catalogName?: string
  valueType?: number
  valueScope?: string
  status?: number
  isDerivative?: number
  isOutput?: number
}

export function listFields(keyword?: string): Promise<FieldVO[]> {
  return request.get('/datamanage/field/list', { params: { keyword } })
}

export function pageFields(params: {
  keyword?: string
  fieldTypeId?: number
  isOutput?: number
  catalogId?: number
  recycle?: boolean
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<FieldVO>> {
  return request.get('/datamanage/field/page', { params })
}

export function recycleFields(ids: number[]): Promise<void> {
  return request.post('/datamanage/field/recycle', ids)
}

export function restoreFields(ids: number[]): Promise<void> {
  return request.post('/datamanage/field/restore', ids)
}

export function deleteFieldsPermanently(ids: number[]): Promise<void> {
  return request.delete('/datamanage/field/permanent', { data: ids })
}

export function listFieldTypes(): Promise<Array<{ id: number; name: string; count: number }>> {
  return request.get('/datamanage/field/types')
}

export interface FieldSaveDTO {
  fieldEn: string
  fieldCn: string
  fieldTypeid?: number
  catalogId?: number
  valueType?: number
  valueScope?: string
  isDerivative?: number
  isOutput?: number
}

export function createField(payload: FieldSaveDTO): Promise<FieldVO> {
  return request.post('/datamanage/field', payload)
}

export function updateField(id: number, payload: FieldSaveDTO): Promise<FieldVO> {
  return request.put(`/datamanage/field/${id}`, payload)
}

export function deleteField(id: number): Promise<void> {
  return request.delete(`/datamanage/field/${id}`)
}

export function moveFieldCatalog(id: number, catalogId?: number): Promise<void> {
  return request.put(`/datamanage/field/${id}/catalog`, { catalogId })
}
