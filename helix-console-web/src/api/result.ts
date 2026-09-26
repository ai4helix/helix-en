import request from './request'
import type { PageResult } from '@/types/flow'

export interface NodeTrace {
  nodeId?: number
  nodeCode?: string
  nodeName?: string
  nodeType?: number
  hit?: boolean
  scoreDelta?: number
  message?: string
  hitDetails?: string[]
}

export interface ResultSetVO {
  id: number
  traceId?: string
  engineCode?: string
  engineName?: string
  engineVersion?: number
  result?: string
  pass?: boolean
  manualReview?: boolean
  score?: number
  pid?: string
  uid?: string
  type?: number
  batchNo?: string
  createdTime?: string
  input?: Record<string, any>
  traces?: NodeTrace[]
  hitDetails?: string[]
}

export interface ResultQuery {
  engineCode?: string
  result?: string
  uuid?: string
  pid?: string
  batchNo?: string
  startTime?: string
  endTime?: string
}

export function pageResults(
  query: ResultQuery,
  pageNo: number,
  pageSize: number
): Promise<PageResult<ResultSetVO>> {
  return request.post('/result/page', query, { params: { pageNo, pageSize } })
}

export function getResult(id: number): Promise<ResultSetVO> {
  return request.get(`/result/${id}`)
}

export function getResultByTrace(traceId: string): Promise<ResultSetVO> {
  return request.get(`/result/trace/${traceId}`)
}

export interface BatchTestResultVO {
  batchNo: string
  engineCode: string
  total: number
  passCount: number
  rejectCount: number
  manualCount: number
  errorCount: number
  passRate: number
  costMs: number
  details: ResultSetVO[]
}

export function batchTest(data: {
  engineCode: string
  remark?: string
  samples: Array<Record<string, any>>
}): Promise<BatchTestResultVO> {
  return request.post('/result/batch', data)
}

export function removeBatch(batchNo: string): Promise<void> {
  return request.delete(`/result/batch/${batchNo}`)
}
