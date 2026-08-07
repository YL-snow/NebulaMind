import { request } from '@/utils/request'
import type {
  GenerateSummaryRequest,
  GenerateSummaryResponse,
  GenerateExtractRequest,
  GenerateExtractResponse,
  GenerateReportRequest,
  GenerateReportResponse,
} from './types'

export const generateApi = {
  summary: (data: GenerateSummaryRequest) => request.post<GenerateSummaryResponse>('/generate/summary', data, { timeout: 120000 }),

  extract: (data: GenerateExtractRequest) => request.post<GenerateExtractResponse>('/generate/extract', data),

  report: (data: GenerateReportRequest) => request.post<GenerateReportResponse>('/generate/report', data),
}
