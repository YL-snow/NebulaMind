import { Link, useLocation } from 'react-router-dom'
import {
  FolderOpen,
  FileText,
  Shield,
  Cloud,
  ChevronLeft,
  ChevronRight,
  LogOut,
} from 'lucide-react'
import { useAuthStore } from '@/stores/authStore'

interface SidebarProps {
  collapsed: boolean
  onToggle: () => void
}

const menuItems = [
  { icon: FolderOpen, label: '文件概览', path: '/home' },
  { icon: FileText, label: '内容生成', path: '/generate' },
  { icon: Shield, label: '安全管理', path: '/security' },
  { icon: Cloud, label: '云存储对接', path: '/storage-config' },
]

export const Sidebar = ({ collapsed, onToggle }: SidebarProps) => {
  const { logout } = useAuthStore()
  const location = useLocation()

  return (
    <aside
      className={`fixed left-0 top-0 h-screen bg-white border-r border-neutral-200 transition-all duration-300 z-40 flex flex-col ${
        collapsed ? 'w-16' : 'w-64'
      }`}
    >
      <div className="flex items-center justify-between h-16 px-4 border-b border-neutral-200">
        {!collapsed && (
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-accent-blue flex items-center justify-center">
              <FolderOpen className="h-5 w-5 text-white" />
            </div>
            <span className="font-semibold tracking-tight text-text-primary">NebulaMind</span>
          </div>
        )}
        <button
          onClick={onToggle}
          className="p-2 hover:bg-neutral-100 transition-colors"
        >
          {collapsed ? (
            <ChevronRight className="h-5 w-5 text-text-secondary" />
          ) : (
            <ChevronLeft className="h-5 w-5 text-text-secondary" />
          )}
        </button>
      </div>

      <nav className="flex-1 py-4">
        {menuItems.map((item) => (
          <Link
            key={item.path}
            to={item.path}
            className={`flex items-center gap-3 px-4 py-2.5 mx-2 transition-all duration-200 ${
              location.pathname === item.path
                ? 'bg-accent-blue/10 text-accent-blue font-medium'
                : 'text-text-secondary hover:bg-neutral-100 hover:text-text-primary'
            } ${collapsed ? 'justify-center mx-0 px-0' : ''}`}
          >
            <item.icon className="h-5 w-5 flex-shrink-0" />
            {!collapsed && <span>{item.label}</span>}
          </Link>
        ))}
      </nav>

      <div className="border-t border-neutral-200 p-4">
        <button
          onClick={logout}
          className={`flex items-center gap-3 px-4 py-2.5 text-text-secondary hover:text-red-500 hover:bg-red-50 transition-all duration-200 w-full ${
            collapsed ? 'justify-center' : ''
          }`}
        >
          <LogOut className="h-5 w-5" />
          {!collapsed && <span>退出登录</span>}
        </button>
      </div>
    </aside>
  )
}
