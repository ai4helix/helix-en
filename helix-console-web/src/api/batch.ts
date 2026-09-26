import request from './request'
import type { PageResult } from '@/types/flow'

export interface IndicatorBatch {
  id: number
  organId?: number
  name: string
  engineCode: string
  keyField: string
  status: number
  totalRows?: number
  successRows?: number
  failRows?: number
  fileName?: string
  errorMsg?: string
  createdTime?: string
  updatedTime?: string
  finishedTime?: string
}

export interface IndicatorBatchItem {
  id: number
  batchId: number
  rowNo: number
  bizKey?: string
  status: number
  traceId?: string
  resultText?: string
  totalScore?: number
  hitRules?: string
  errorMsg?: string
  dataJson?: string
}

export interface FieldImportSummary {
  inserted: number
  updated: number
  total: number
}

export interface DataImportSummary {
  batchId: number
  totalRows: number
  warning?: string
}

export interface EngineTask {
  id: number
  organId?: number
  taskCode: string
  taskName: string
  engineCode: string
  keyField: string
  description?: string
  status: number
  createdTime?: string
  updatedTime?: string
}

export interface EngineTaskImportSummary {
  inserted: number
  updated: number
  total: number
  errors: { row: number; taskCode?: string; message: string }[]
}

async function downloadBlob(url: string, params: Record<string, any>, fallbackName: string) {
  const resp = (await request.get(url, { params, responseType: 'blob' })) as unknown as any
  const blob: Blob = resp instanceof Blob ? resp : (resp?.data as Blob)
  const url_ = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url_
  a.download = fallbackName
  a.click()
  URL.revokeObjectURL(url_)
}

export function downloadFieldTemplate(): Promise<void> {
  return downloadBlob('/batch/template/field', {}, 'indicator-field-import-template.csv')
}

export function downloadDataTemplate(
  engineCode?: string,
  keyField = 'uid',
  example = true
): Promise<void> {
  return downloadBlob(
    '/batch/template/data',
    { engineCode, keyField, example },
    `user-indicator-data-import-template_${keyField}.csv`
  )
}

export function downloadEngineTaskTemplate(): Promise<void> {
  return downloadBlob('/batch/engine-task/template', {}, 'engine-task-import-template.csv')
}

export function importEngineTasks(file: File): Promise<EngineTaskImportSummary> {
  const form = new FormData()
  form.append('file', file)
  return request.post('/batch/engine-task/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function pageEngineTasks(
  pageNo: number,
  pageSize: number,
  params?: { keyword?: string; status?: number }
): Promise<PageResult<EngineTask>> {
  return request.get('/batch/engine-task/page', { params: { pageNo, pageSize, ...params } })
}

export function changeEngineTaskStatus(id: number, status: number): Promise<void> {
  return request.post(`/batch/engine-task/${id}/status`, null, { params: { status } })
}

export function deleteEngineTask(id: number): Promise<void> {
  return request.delete(`/batch/engine-task/${id}`)
}

export function importFields(file: File): Promise<FieldImportSummary> {
  const form = new FormData()
  form.append('file', file)
  return request.post('/batch/field/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function importData(
  name: string,
  engineCode: string,
  keyField: string,
  file: File
): Promise<DataImportSummary> {
  const form = new FormData()
  form.append('name', name)
  form.append('engineCode', engineCode)
  form.append('keyField', keyField)
  form.append('file', file)
  return request.post('/batch/data/import', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function runBatch(id: number): Promise<void> {
  return request.post(`/batch/${id}/run`)
}

export function getBatch(id: number): Promise<IndicatorBatch> {
  return request.get(`/batch/${id}`)
}

export function pageBatches(pageNo: number, pageSize: number): Promise<PageResult<IndicatorBatch>> {
  return request.get('/batch/page', { params: { pageNo, pageSize } })
}

export function pageItems(
  id: number,
  pageNo: number,
  pageSize: number,
  params?: { status?: number; keyword?: string }
): Promise<PageResult<IndicatorBatchItem>> {
  return request.get(`/batch/${id}/items`, {
    params: { pageNo, pageSize, ...params }
  })
}

export async function downloadResults(id: number, taskName: string): Promise<void> {
  const blob = (await request.get(`/batch/${id}/download`, {
    responseType: 'blob'
  })) as unknown as Blob
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `batch_${id}_${taskName || 'results'}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
