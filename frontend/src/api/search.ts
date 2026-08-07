import { request } from '@/utils/request'
import type { SearchRequest, SearchResponse } from './types'

export const searchApi = {
  semanticSearch: (data: SearchRequest) => request.post<SearchResponse>('/search', data),
}