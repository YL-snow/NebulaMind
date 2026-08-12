import { createBrowserRouter, Navigate } from 'react-router-dom'
import { Layout } from '@/components/layout/Layout'
import { Login } from '@/pages/Login'
import { Home } from '@/pages/Home'
import { FileDetail } from '@/pages/FileDetail'
import { Generate } from '@/pages/Generate'
import { Security } from '@/pages/Security'
import { StorageConfig } from '@/pages/StorageConfig'
import { useAuthStore } from '@/stores/authStore'

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { isAuthenticated } = useAuthStore()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Login />,
  },
  {
    path: '/login',
    element: <Login />,
  },
  {
    path: '/home',
    element: (
      <ProtectedRoute>
        <Layout title="文件概览">
          <Home />
        </Layout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/files/:id',
    element: (
      <ProtectedRoute>
        <Layout title="文件详情">
          <FileDetail />
        </Layout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/generate',
    element: (
      <ProtectedRoute>
        <Layout title="内容生成">
          <Generate />
        </Layout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/security',
    element: (
      <ProtectedRoute>
        <Layout title="安全管理">
          <Security />
        </Layout>
      </ProtectedRoute>
    ),
  },
  {
    path: '/storage-config',
    element: (
      <ProtectedRoute>
        <Layout title="云存储对接">
          <StorageConfig />
        </Layout>
      </ProtectedRoute>
    ),
  },
])
