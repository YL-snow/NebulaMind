import { useState, useEffect, useCallback } from 'react'
import { CheckCircle, XCircle, AlertCircle, Info, X } from 'lucide-react'

type ToastType = 'success' | 'error' | 'warning' | 'info'

interface Toast {
  id: string
  type: ToastType
  message: string
}

interface ToastProps {
  toasts: Toast[]
  removeToast: (id: string) => void
}

export const Toast = ({ toasts, removeToast }: ToastProps) => {
  useEffect(() => {
    toasts.forEach((toast) => {
      const timer = setTimeout(() => {
        removeToast(toast.id)
      }, 4000)
      return () => clearTimeout(timer)
    })
  }, [toasts, removeToast])

  const icons = {
    success: CheckCircle,
    error: XCircle,
    warning: AlertCircle,
    info: Info,
  }

  const colors = {
    success: 'bg-green-50 border-green-200 text-green-700',
    error: 'bg-red-50 border-red-200 text-red-700',
    warning: 'bg-yellow-50 border-yellow-200 text-yellow-700',
    info: 'bg-blue-50 border-blue-200 text-blue-700',
  }

  const iconColors = {
    success: 'text-green-500',
    error: 'text-red-500',
    warning: 'text-yellow-500',
    info: 'text-blue-500',
  }

  return (
    <div className="fixed top-4 right-4 z-50 space-y-2">
      {toasts.map((toast) => {
        const Icon = icons[toast.type]
        return (
          <div
            key={toast.id}
            className={`flex items-center gap-3 px-4 py-3 border rounded-lg shadow-lg animate-in slide-in-from-right-4 fade-in duration-300 ${colors[toast.type]}`}
          >
            <Icon className={`h-5 w-5 flex-shrink-0 ${iconColors[toast.type]}`} />
            <p className="flex-1 text-sm">{toast.message}</p>
            <button
              onClick={() => removeToast(toast.id)}
              className="p-1 hover:bg-black/5 rounded"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )
      })}
    </div>
  )
}

let toastId = 0
const toastList: Toast[] = []
let notifyListeners: (() => void)[] = []

const notify = () => {
  notifyListeners.forEach((listener) => listener())
}

export const useToast = () => {
  const [toasts, setToasts] = useState<Toast[]>(toastList)

  useEffect(() => {
    const listener = () => setToasts([...toastList])
    notifyListeners.push(listener)
    return () => {
      notifyListeners = notifyListeners.filter((l) => l !== listener)
    }
  }, [])

  const addToast = useCallback((type: ToastType, message: string) => {
    const id = String(++toastId)
    toastList.push({ id, type, message })
    notify()
  }, [])

  const removeToast = useCallback((id: string) => {
    const index = toastList.findIndex((t) => t.id === id)
    if (index !== -1) {
      toastList.splice(index, 1)
      notify()
    }
  }, [])

  const success = useCallback((message: string) => addToast('success', message), [addToast])
  const error = useCallback((message: string) => addToast('error', message), [addToast])
  const warning = useCallback((message: string) => addToast('warning', message), [addToast])
  const info = useCallback((message: string) => addToast('info', message), [addToast])

  return { toasts, addToast, removeToast, success, error, warning, info }
}