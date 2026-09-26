import type { ResultSetVO } from '@/api/result'

export type ResultKind = 'pass' | 'reject' | 'manual' | 'unknown'

export function resultKind(r: Pick<ResultSetVO, 'pass' | 'manualReview' | 'result'>): ResultKind {
  if (r.pass === true) return 'pass'
  if (r.manualReview === true) return 'manual'
  if (r.pass === false) return 'reject'
  const t = (r.result ?? '').trim()
  if (t === 'Pass') return 'pass'
  if (t === 'Manual Review') return 'manual'
  if (t === 'Reject') return 'reject'
  return 'unknown'
}

export const RESULT_META: Record<ResultKind, { label: string; cls: string }> = {
  pass: { label: 'Pass', cls: 'is-ok' },
  reject: { label: 'Reject', cls: 'is-no' },
  manual: { label: 'Manual Review', cls: 'is-warn' },
  unknown: { label: '—', cls: '' }
}

export function resultClass(r: Pick<ResultSetVO, 'pass' | 'manualReview' | 'result'>): string {
  return RESULT_META[resultKind(r)].cls
}

export function resultLabel(r: Pick<ResultSetVO, 'pass' | 'manualReview' | 'result'>): string {
  const k = resultKind(r)
  return k === 'unknown' ? r.result || '—' : RESULT_META[k].label
}
