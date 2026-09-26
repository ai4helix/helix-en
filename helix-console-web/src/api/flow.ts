import request from './request'
import type { Engine, EngineVersion, GraphVO, NodeData } from '@/types/flow'

export function loadGraph(versionId: number): Promise<GraphVO> {
  return request.get(`/engine/flow/graph/${versionId}`)
}

export function saveGraph(versionId: number, graph: GraphVO, layout?: number): Promise<void> {
  return request.post('/engine/flow/graph/save', { versionId, graph, layout })
}

export function saveNode(payload: Partial<NodeData> & { versionId: number }): Promise<number> {
  return request.post('/engine/flow/node', payload)
}

export function removeNode(versionId: number, nodeId: number): Promise<void> {
  return request.delete(`/engine/flow/node/${versionId}/${nodeId}`)
}

export function removeNodes(versionId: number, nodeIds: number[]): Promise<void> {
  return request.delete(`/engine/flow/node/${versionId}`, { data: nodeIds })
}

export function moveAndLink(payload: {
  versionId: number
  nodes?: Array<{ nodeId: number; nodeX: number; nodeY: number }>
  edges?: Array<{
    action: 'ADD' | 'REMOVE'
    sourceNodeCode: string
    targetNodeCode: string
    label?: string
    kind?: number
  }>
}): Promise<void> {
  return request.post('/engine/flow/node/move', payload)
}

export function copyNode(nodeId: number): Promise<number> {
  return request.post(`/engine/flow/node/${nodeId}/copy`)
}

export function validateGraph(versionId: number): Promise<void> {
  return request.post(`/engine/flow/graph/${versionId}/validate`)
}

export function publishVersion(versionId: number): Promise<void> {
  return request.post(`/engine/flow/version/${versionId}/publish`)
}

export function saveAsDraft(versionId: number): Promise<number> {
  return request.post(`/engine/flow/version/${versionId}/draft`)
}

export function removeVersion(versionId: number): Promise<void> {
  return request.delete(`/engine/flow/version/${versionId}`)
}

export function listEngines(): Promise<Engine[]> {
  return request.get('/engine/list')
}

export function listVersions(engineId: number): Promise<EngineVersion[]> {
  return request.get(`/engine/${engineId}/versions`)
}

export function createEngine(payload: {
  code: string
  name: string
  description?: string
}): Promise<number> {
  return request.post('/engine/create', payload)
}

export function createEngineVersion(engineId: number): Promise<number> {
  return request.post(`/engine/${engineId}/version`)
}


export interface PublishTrack {
  publishId: number | null
  publishSeq: number | null
  trafficWeight: number
  shadow: boolean
  status: number
  sha256: string | null
  remark: string | null
  publishedTime: string | null
  routed: boolean
  routable: boolean
}

export interface RoutingView {
  publishId: number | null
  publishSeq: number | null
  trafficWeight: number
  shadow: boolean
  routable: boolean
  liveFallback: boolean
  nodeCount: number
  rulePlanCount: number
}

export function listTracks(versionId: number): Promise<PublishTrack[]> {
  return request.get(`/engine/flow/version/${versionId}/tracks`)
}

export function routingView(versionId: number): Promise<RoutingView[]> {
  return request.get(`/engine/flow/version/${versionId}/routing`)
}

export function grayPublish(versionId: number, weight: number): Promise<Record<string, unknown>> {
  return request.post(`/engine/flow/version/${versionId}/gray`, { weight })
}

export function setTrackWeight(publishId: number, weight: number): Promise<PublishTrack[]> {
  return request.post(`/engine/flow/track/${publishId}/weight`, { weight })
}

export function setTrackShadow(publishId: number, on: boolean): Promise<PublishTrack[]> {
  return request.post(`/engine/flow/track/${publishId}/shadow`, { on })
}

export function promoteTrack(publishId: number): Promise<PublishTrack[]> {
  return request.post(`/engine/flow/track/${publishId}/promote`)
}
