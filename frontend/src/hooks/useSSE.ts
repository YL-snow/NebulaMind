import { useEffect, useRef, useCallback, useState } from 'react'
import type { SSEEvent } from '@/api/types'
import { useAuthStore } from '@/stores/authStore'
import { useFileStore } from '@/stores/fileStore'

interface UseSSEOptions {
  onEvent?: (event: SSEEvent) => void
  onError?: (error: Error) => void
  onClose?: () => void
}

export const useSSE = ({ onEvent, onError, onClose }: UseSSEOptions = {}) => {
  const abortControllerRef = useRef<AbortController | null>(null)
  const [isConnected, setIsConnected] = useState(false)
  const [reconnectAttempts, setReconnectAttempts] = useState(0)

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
    }

    const token = localStorage.getItem('accessToken')
    const userId = useAuthStore.getState().user?.id
    if (!token || !userId) return

    const abortController = new AbortController()
    abortControllerRef.current = abortController
    setIsConnected(true)

    try {
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/events/connect`, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
        },
        signal: abortController.signal,
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

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
              onEvent?.(parsedEvent)
            } catch {
              console.error('Failed to parse SSE event')
            }
          }
        }
      }

      onClose?.()
    } catch (error) {
      if (error instanceof Error && error.name !== 'AbortError') {
        onError?.(error)
        setIsConnected(false)

        if (reconnectAttempts < 5) {
          const delay = Math.pow(2, reconnectAttempts) * 1000
          setTimeout(() => {
            setReconnectAttempts((prev) => prev + 1)
            connect()
          }, delay)
        }
      }
    }
  }, [onEvent, onError, onClose, handleFileEvent, reconnectAttempts])

  const disconnect = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }
    setIsConnected(false)
  }, [])

  useEffect(() => {
    connect()

    return () => {
      disconnect()
    }
  }, [connect, disconnect])

  return { connect, disconnect, isConnected }
}