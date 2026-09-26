import request from './request'
import type { DecisionTestPayload } from '@/types/run'
import type { BatchTestResultVO } from './result'

export function runDecisionTest(payload: DecisionTestPayload): Promise<BatchTestResultVO> {
  return request.post('/result/batch', payload)
}
