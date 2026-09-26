import request from './request'

export interface TenantStat {
  organId: number
  name: string
  code?: string
  userCount: number
  roleCount: number
  decisionTotal: number
  decisionToday: number
  passCount: number
  rejectCount: number
  manualCount: number
  passRate: number
  lastDecisionTime?: string
  batchCount: number
  batchRows: number
  batchRowsOk: number
}

export interface RoleStat {
  roleId: number
  roleName: string
  roleCode?: string
  roleDesc?: string
  organId: number
  organName: string
  userCount: number
  status: number
}

export interface EngineStat {
  organId: number
  organName: string
  decisionTotal: number
  decisionToday: number
  passCount: number
  rejectCount: number
  manualCount: number
  passRate: number
  lastDecisionTime?: string
  batchCount: number
  batchRows: number
  batchRowsOk: number
}

export interface PlatformOverview {
  tenantCount: number
  userCount: number
  decisionTotal: number
  decisionToday: number
  batchCount: number
  batchRows: number
}

export function getOverview(): Promise<PlatformOverview> {
  return request.get('/platform/overview')
}

export function getTenants(): Promise<TenantStat[]> {
  return request.get('/platform/tenants')
}

export function getRoles(): Promise<RoleStat[]> {
  return request.get('/platform/roles')
}

export function getEngineStats(): Promise<EngineStat[]> {
  return request.get('/platform/engine-stats')
}
