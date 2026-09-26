import request from './request'


export interface FeedbackRecord {
  id: number
  organId: number
  organName?: string | null
  userId: number
  username: string
  category: number
  content: string
  images: string[]
  status: number
  reply?: string | null
  handledBy?: string | null
  handledTime?: string | null
  createdTime: string
}

export interface FeedbackPage {
  records: FeedbackRecord[]
  total: number
  pageNo: number
  pageSize: number
  pages: number
}

export function submitFeedback(data: {
  category: number
  content: string
  images: string[]
}): Promise<number> {
  return request.post('/feedback', data)
}

export function uploadFeedbackImage(file: File): Promise<string> {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/feedback/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function fetchFeedbackImage(name: string): Promise<Blob> {
  return request.get(`/feedback/file/${name}`, { responseType: 'blob' })
}

export function pageFeedback(params: {
  status?: number
  keyword?: string
  pageNo: number
  pageSize: number
}): Promise<FeedbackPage> {
  return request.get('/feedback/page', { params })
}

export function handleFeedback(id: number, reply: string): Promise<Record<string, unknown>> {
  return request.post(`/feedback/${id}/handle`, { reply })
}
