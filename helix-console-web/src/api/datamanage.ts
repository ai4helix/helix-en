import request from './request'
import type { PageResult } from '@/types/flow'
import type { LineageGraphDTO } from '@/types/lineage'
import type { DecisionField } from '@/types/run'

export interface ListDb {
  id: number
  listType: string
  listName: string
  dataSource?: number
  listAttr?: string
  listDesc?: string
  tableColumn: string
  matchType?: number
  /** 1 AND 0 OR */
  queryType?: number
  queryField?: string
  organId?: number
  status?: number
  userId?: number
  created?: string
}

export function pageListDbs(params: {
  listType?: string
  status?: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}): Promise<PageResult<ListDb>> {
  return request.get('/datamanage/listdb/page', { params })
}

export function listAvailableListDbs(listType?: string): Promise<ListDb[]> {
  return request.get('/datamanage/listdb/available', { params: { listType } })
}


export interface ListEntry {
  id: number
  listId: number
  entryValue: string
  entryType?: string | null
  remark?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
  status: number
  createdTime?: string
}

export function pageListEntries(
  listId: number,
  params: { keyword?: string; status?: number; pageNo?: number; pageSize?: number }
): Promise<PageResult<ListEntry>> {
  return request.get(`/datamanage/listdb/${listId}/entries`, { params })
}

export function addListEntries(
  listId: number,
  data: { values: string; remark?: string; effectiveFrom?: string; effectiveTo?: string }
): Promise<number> {
  return request.post(`/datamanage/listdb/${listId}/entries`, data)
}

export function setListEntryStatus(listId: number, entryIds: number[], status: number): Promise<void> {
  return request.post(`/datamanage/listdb/${listId}/entries/status`, { entryIds, status })
}

export function removeListEntries(listId: number, entryIds: number[]): Promise<void> {
  return request.delete(`/datamanage/listdb/${listId}/entries`, { data: entryIds })
}

export function getListDb(id: number): Promise<ListDb> {
  return request.get(`/datamanage/listdb/${id}`)
}

export function createListDb(data: Partial<ListDb>): Promise<number> {
  return request.post('/datamanage/listdb', data)
}

export function updateListDb(data: Partial<ListDb>): Promise<void> {
  return request.put('/datamanage/listdb', data)
}

export function copyListDb(id: number): Promise<number> {
  return request.post(`/datamanage/listdb/${id}/copy`)
}

export function changeListDbStatus(ids: number[], status: number): Promise<void> {
  return request.post('/datamanage/listdb/status', null, { params: { ids: ids.join(','), status } })
}

export function recycleListDbs(ids: number[]): Promise<void> {
  return request.post('/datamanage/listdb/recycle', ids)
}

export function restoreListDbs(ids: number[]): Promise<void> {
  return request.post('/datamanage/listdb/restore', ids)
}

export function deleteListDbsPermanently(ids: number[]): Promise<void> {
  return request.delete('/datamanage/listdb/permanent', { data: ids })
}


export function getLineageGraph(versionId: number): Promise<LineageGraphDTO> {
  return request.get('/datamanage/field/lineage/graph', { params: { versionId } })
}


export function listAllFields(): Promise<DecisionField[]> {
  return request.get('/datamanage/field/list')
}
