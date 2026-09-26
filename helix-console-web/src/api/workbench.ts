import request from './request'


export interface DemoStatus {
  organId: number
  initialized: boolean
}

export interface DemoInitResult extends DemoStatus {
  engineCode?: string
  versionId?: number
  published?: boolean
}

export function getDemoStatus(): Promise<DemoStatus> {
  return request.get('/workbench/demo-status')
}

export function initDemoData(): Promise<DemoInitResult> {
  return request.post('/workbench/demo-init')
}
