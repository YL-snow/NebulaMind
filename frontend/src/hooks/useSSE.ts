import { useEffect, useRef, useCallback, useState } from 'react'
import type { SSEEvent } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
import { useFileStore } from '@/stores/fileStore'

interface UseSSEOptions {
  onEvent?: (event: SSEEvent) => void
  onError?: (error: Error) => void
  onClose?: () => void
}

const MAX_RECONNECT_ATTEMPTS = 5

export const useSSE = ({ onEvent, onError, onClose }: UseSSEOptions = {}) => {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const abortControllerRef = useRef<AbortController | null>(null)
  const retryTimerRef = useRef<number | null>(null)
  const reconnectAttemptsRef = useRef(0)
  const onEventRef = useRef(onEvent)
  const onErrorRef = useRef(onError)
  const onCloseRef = useRef(onClose)
  const [isConnected, setIsConnected] = useState(false)

  useEffect(() => {
    onEventRef.current = onEvent
    onErrorRef.current = onError
    onCloseRef.current = onClose
  })

  const handleFileEvent = useCallback((event: SSEEvent) => {
    const { eventType, data } = event

    switch (eventType) {
      case 'file.processing':
        useFileStore.getState().updateFile(data.fileId, { aiStatus: 'processing' })
        break
      case 'file.progress':
        break
      case 'file.completed':
        if (data.aiResult) {
          useFileStore.getState().updateFile(data.fileId, {
            aiStatus: 'completed',
            tags: JSON.stringify(data.aiResult.tags),
            category: data.aiResult.category,
            summary: data.aiResult.summary,
            sensitiveLevel: data.aiResult.sensitiveItems.length > 0 ? 'medium' : 'normal',
          })
        } else {
          useFileStore.getState().updateFile(data.fileId, { aiStatus: 'completed' })
        }
        break
      case 'file.failed':
        useFileStore.getState().updateFile(data.fileId, { aiStatus: 'failed' })
        break
      default:
        break
    }
  }, [])

  const connect = useCallback(async () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }
    if (retryTimerRef.current !== null) {
      window.clearTimeout(retryTimerRef.current)
      retryTimerRef.current = null
    }

    const token = localStorage.getItem('accessToken')
    const userId = useAuthStore.getState().user?.id
    if (!token || !userId) return

    const abortController = new AbortController()
    abortControllerRef.current = abortController

    const apiOrigin = import.meta.env.VITE_API_BASE_URL ? import.meta.env.VITE_API_BASE_URL.replace(/\/$/, '') : ''

    try {
      const response = await fetch(`${apiOrigin}/sse/subscribe/${userId}`, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
        },
        signal: abortController.signal,
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      reconnectAttemptsRef.current = 0
      setIsConnected(true)

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('No response body')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6)
            try {
              const parsedEvent: SSEEvent = JSON.parse(data)
              handleFileEvent(parsedEvent)
              onEventRef.current?.(parsedEvent)
            } catch {
              console.error('Failed to parse SSE event')
            }
          }
        }
      }

      setIsConnected(false)
      onCloseRef.current?.()
    } catch (error) {
      if (error instanceof Error && error.name !== 'AbortError') {
        onErrorRef.current?.(error)
        setIsConnected(false)

        if (reconnectAttemptsRef.current < MAX_RECONNECT_ATTEMPTS) {
          const delay = Math.pow(2, reconnectAttemptsRef.current) * 1000
          reconnectAttemptsRef.current += 1
          retryTimerRef.current = window.setTimeout(() => {
            retryTimerRef.current = null
            connect()
          }, delay)
        }
      }
    }
  }, [handleFileEvent])

  const disconnect = useCallback(() => {
    if (retryTimerRef.current !== null) {
      window.clearTimeout(retryTimerRef.current)
      retryTimerRef.current = null
    }
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }
    reconnectAttemptsRef.current = 0
    setIsConnected(false)
  }, [])

  useEffect(() => {
    if (!isAuthenticated) return
    connect()

    return () => {
      disconnect()
    }
  }, [connect, disconnect, isAuthenticated])

  return { connect, disconnect, isConnected }
}
