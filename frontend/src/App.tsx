import { RouterProvider } from 'react-router-dom'
import { router } from '@/router'
import { useSSE } from '@/hooks/useSSE'
import { Toast, useToast } from '@/components/common/Toast'

function App() {
  const { toasts, removeToast } = useToast()

  // Always call useSSE - it internally checks auth state before connecting
  // This ensures React Hooks are always called in the same order (fixes white screen on login/logout)
  useSSE({
    onEvent: (event) => {
      console.log('SSE event:', event)
    },
    onError: (error) => {
      console.error('SSE error:', error)
    },
    onClose: () => {
      console.log('SSE connection closed')
    },
  })

  return (
    <>
      <RouterProvider router={router} />
      <Toast toasts={toasts} removeToast={removeToast} />
    </>
  )
}

export default App
