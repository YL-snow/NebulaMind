import { useState, useEffect, useRef } from 'react'
import { Search, X, ArrowRight } from 'lucide-react'
import { useSearchStore } from '@/stores/searchStore'

interface SearchBoxProps {
  onSearch: (query: string) => void
  onFocus?: () => void
}

export const SearchBox = ({ onSearch, onFocus }: SearchBoxProps) => {
  const [query, setQuery] = useState('')
  const { searchHistory, addSearchHistory, clearSearchHistory } = useSearchStore()
  const [showHistory, setShowHistory] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setShowHistory(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleSearch = () => {
    const trimmed = query.trim()
    if (trimmed) {
      addSearchHistory(trimmed)
    }
    onSearch(trimmed)
    setShowHistory(false)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch()
    } else if (e.key === 'Escape') {
      handleClear()
    }
  }

  const handleClear = () => {
    setQuery('')
    onSearch('')
    setShowHistory(false)
  }

  const handleHistoryClick = (historyItem: string) => {
    setQuery(historyItem)
    onSearch(historyItem)
    setShowHistory(false)
  }

  return (
    <div ref={containerRef} className="relative w-full max-w-2xl">
      <div className="relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-text-secondary" />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setShowHistory(true)
          }}
          onFocus={() => { setShowHistory(true); onFocus?.() }}
          onKeyDown={handleKeyDown}
          placeholder="搜索文件、文档内容..."
          className="w-full pl-12 pr-24 py-3 border border-neutral-200 text-sm text-text-primary placeholder-neutral-400 focus:outline-none focus:ring-2 focus:ring-accent-blue focus:border-transparent transition-all duration-200"
        />
        {query && (
          <button
            onClick={handleClear}
            className="absolute right-16 top-1/2 -translate-y-1/2 p-1 text-text-secondary hover:text-text-primary"
          >
            <X className="h-4 w-4" />
          </button>
        )}
        <button
          onClick={handleSearch}
          className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-text-secondary hover:text-text-primary"
        >
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
      {showHistory && searchHistory.length > 0 && (
        <div className="absolute top-full left-0 right-0 mt-2 bg-white shadow-card border border-neutral-200 p-4 z-50">
          <div className="flex items-center justify-between mb-3">
            <span className="text-xs font-medium text-text-secondary">搜索历史</span>
            <button
              onClick={clearSearchHistory}
              className="text-xs text-text-secondary hover:text-red-500"
            >
              清空
            </button>
          </div>
          <div className="space-y-1">
            {searchHistory.map((item) => (
              <button
                key={item}
                onClick={() => handleHistoryClick(item)}
                className="w-full flex items-center gap-3 px-3 py-2 text-sm text-text-primary hover:bg-neutral-100 transition-colors"
              >
                <Search className="h-4 w-4 text-text-secondary" />
                <span>{item}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}