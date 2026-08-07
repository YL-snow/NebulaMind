import { request } from '@/utils/request'
import type { QARequest, QAResponse } from './types'

export const qaApi = {
  ask: (data: Omit<QARequest, 'fileIds'>) => request.post<QAResponse>('/qa', data),

  crossAsk: (data: Omit<QARequest, 'fileId'>) => request.post<QAResponse>('/qa/cross', data),
}