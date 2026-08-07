import { create } from 'zustand'
import type { SearchResponse, SearchResultItem } from '@/api/types'

interface SearchState {
  results: SearchResultItem[]
  totalCount: number
  currentQuery: string
  loading: boolean
  searchHistory: string[]
  setResults: (response: SearchResponse) => void
  setCurrentQuery: (query: string) => void
  setLoading: (loading: boolean) => void
  addSearchHistory: (query: string) => void
  clearSearchHistory: () => void
}

export const useSearchStore = create<SearchState>((set, get) => ({
  results: [],
  totalCount: 0,
  currentQuery: '',
  loading: false,
  searchHistory: JSON.parse(localStorage.getItem('searchHistory') || '[]'),

  setResults: (response) =>
    set({
      results: response?.items ?? [],
      totalCount: response?.totalCount ?? 0,
    }),

  setCurrentQuery: (query) => set({ currentQuery: query }),

  setLoading: (loading) => set({ loading }),

  addSearchHistory: (query) => {
    const { searchHistory } = get()
    const newHistory = [query, ...searchHistory.filter((q) => q !== query)].slice(0, 10)
    localStorage.setItem('searchHistory', JSON.stringify(newHistory))
    set({ searchHistory: newHistory })
  },

  clearSearchHistory: () => {
    localStorage.removeItem('searchHistory')
    set({ searchHistory: [] })
  },
}))