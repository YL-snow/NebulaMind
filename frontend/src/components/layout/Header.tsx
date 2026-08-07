import { Bell, User, X, Shield, ChevronDown, Key, LogOut, Loader2 } from 'lucide-react'
import { useAuthStore } from '@/stores/authStore'
import { useFileStore } from '@/stores/fileStore'
import { authApi } from '@/api/auth'
import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'

interface HeaderProps {
  title: string
}

export const Header = ({ title }: HeaderProps) => {
  const { user, logout } = useAuthStore()
  const files = useFileStore((state) => state.files)
  const navigate = useNavigate()
  const [showNotifications, setShowNotifications] = useState(false)
  const [showUserMenu, setShowUserMenu] = useState(false)
  const [showChangePassword, setShowChangePassword] = useState(false)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [passwordError, setPasswordError] = useState('')
  const [passwordSuccess, setPasswordSuccess] = useState('')
  const [dismissedIds, setDismissedIds] = useState<string[]>(() => {
    try {
      return JSON.parse(localStorage.getItem('dismissedNotifIds') || '[]')
    } catch {
      return []
    }
  })
  const notifRef = useRef<HTMLDivElement>(null)
  const userMenuRef = useRef<HTMLDivElement>(null)

  // 持久化已读状态到 localStorage
  useEffect(() => {
    localStorage.setItem('dismissedNotifIds', JSON.stringify(dismissedIds))
  }, [dismissedIds])

  const highSensitiveFiles = files.filter(
    (f) => (f.sensitiveLevel === 'high' || f.sensitiveLevel === 'medium') && !dismissedIds.includes(f.id)
  )
  const hasUnread = highSensitiveFiles.length > 0

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) {
        setShowNotifications(false)
      }
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setShowUserMenu(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const dismissNotification = (fileId: string) => {
    setDismissedIds((prev) => [...prev, fileId])
  }

  const markAllRead = () => {
    setDismissedIds((prev) => [
      ...prev,
      ...highSensitiveFiles.map((f) => f.id),
    ])
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const handleChangePassword = async () => {
    setPasswordError('')
    setPasswordSuccess('')

    if (!passwordForm.currentPassword) {
      setPasswordError('请输入当前密码')
      return
    }
    if (!passwordForm.newPassword || passwordForm.newPassword.length < 6) {
      setPasswordError('新密码至少6位')
      return
    }
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordError('两次输入的新密码不一致')
      return
    }

    setPasswordLoading(true)
    try {
      await authApi.changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      })
      setPasswordSuccess('密码修改成功')
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
      setTimeout(() => {
        setShowChangePassword(false)
        setPasswordSuccess('')
      }, 1500)
    } catch (err) {
      setPasswordError((err as Error).message || '密码修改失败')
    } finally {
      setPasswordLoading(false)
    }
  }

  return (
    <header className="h-16 bg-white border-b border-neutral-200 px-6 flex items-center justify-between sticky top-0 z-30">
          <h1 className="text-xl font-semibold tracking-tight text-text-primary">
        {title}
      </h1>
      <div className="flex items-center gap-4">
        <div ref={notifRef} className="relative">
          <button
            onClick={() => setShowNotifications(!showNotifications)}
            className="relative p-2 hover:bg-neutral-100 transition-colors"
          >
            <Bell className="h-5 w-5 text-text-secondary" />
            {hasUnread && (
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500" />
            )}
          </button>
          {showNotifications && (
            <div className="absolute right-0 top-full mt-2 w-80 bg-white border border-neutral-200 shadow-card z-50">
              <div className="p-4 border-b border-neutral-200 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Shield className="h-4 w-4 text-red-500" />
                  <h3 className="font-semibold text-sm text-text-primary">
                    安全警报
                  </h3>
                  <span className="text-xs text-text-secondary">
                    {highSensitiveFiles.length} 条未读
                  </span>
                </div>
                {hasUnread && (
                  <button
                    onClick={markAllRead}
                    className="text-xs text-accent-blue hover:text-accent-blue/80"
                  >
                    全部标为已读
                  </button>
                )}
              </div>
              {highSensitiveFiles.length > 0 ? (
                <div className="max-h-80 overflow-y-auto">
                  {highSensitiveFiles.map((file) => (
                    <div
                      key={file.id}
                      className="flex items-center gap-3 p-3 hover:bg-neutral-50 cursor-pointer border-b border-neutral-100 last:border-b-0 transition-colors"
                      onClick={() => {
                        setShowNotifications(false)
                        navigate('/security')
                      }}
                    >
                      <Shield className="h-5 w-5 text-red-500 flex-shrink-0" />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-text-primary truncate">
                          {file.name}
                        </p>
                        <p className="text-xs text-text-secondary">
                          检测到敏感内容
                        </p>
                      </div>
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          dismissNotification(file.id)
                        }}
                        className="p-1 text-text-secondary hover:text-text-primary transition-colors flex-shrink-0"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-8 text-center">
                  <Bell className="h-8 w-8 text-neutral-300 mx-auto mb-2" />
                  <p className="text-sm text-text-secondary">暂无新消息</p>
                </div>
              )}
            </div>
          )}
        </div>
        <div ref={userMenuRef} className="relative">
          <button
            onClick={() => setShowUserMenu(!showUserMenu)}
            className="flex items-center gap-3 pl-4 border-l border-neutral-200 hover:bg-neutral-50 transition-colors py-1 pr-2"
          >
            <div className="w-8 h-8 bg-accent-blue/10 flex items-center justify-center">
              <User className="h-4 w-4 text-accent-blue" />
            </div>
            <div className="hidden md:block text-left">
              <p className="text-sm font-medium text-text-primary">
                {user?.displayName}
              </p>
              <p className="text-xs text-text-secondary">{user?.role}</p>
            </div>
            <ChevronDown className="h-4 w-4 text-text-secondary hidden md:block" />
          </button>

          {showUserMenu && (
            <div className="absolute right-0 top-full mt-2 w-56 bg-white border border-neutral-200 shadow-card z-50">
              {/* 用户信息 */}
              <div className="p-4 border-b border-neutral-200">
                <p className="text-sm font-medium text-text-primary">{user?.displayName}</p>
                <p className="text-xs text-text-secondary mt-0.5">{user?.username}</p>
                <p className="text-xs text-text-secondary">{user?.role === 'ADMIN' ? '管理员' : '普通用户'}</p>
              </div>

              {/* 菜单项 */}
              <div className="py-1">
                <button
                  onClick={() => {
                    setShowUserMenu(false)
                    setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
                    setPasswordError('')
                    setPasswordSuccess('')
                    setShowChangePassword(true)
                  }}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text-primary hover:bg-neutral-50 transition-colors"
                >
                  <Key className="h-4 w-4 text-text-secondary" />
                  修改密码
                </button>
                <button
                  onClick={handleLogout}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors"
                >
                  <LogOut className="h-4 w-4" />
                  退出登录
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 修改密码弹窗 */}
      {showChangePassword && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-6 w-full max-w-md mx-4">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-lg font-semibold text-text-primary">修改密码</h3>
              <button
                onClick={() => setShowChangePassword(false)}
                className="p-1 text-text-secondary hover:text-text-primary"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {passwordError && (
              <div className="px-3 py-2 bg-red-50 border border-red-200 text-sm text-red-600 mb-4">
                {passwordError}
              </div>
            )}
            {passwordSuccess && (
              <div className="px-3 py-2 bg-green-50 border border-green-200 text-sm text-green-600 mb-4">
                {passwordSuccess}
              </div>
            )}

            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-text-primary block mb-1">当前密码</label>
                <input
                  type="password"
                  value={passwordForm.currentPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, currentPassword: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                  placeholder="输入当前密码"
                />
              </div>
              <div>
                <label className="text-sm font-medium text-text-primary block mb-1">新密码</label>
                <input
                  type="password"
                  value={passwordForm.newPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                  placeholder="至少6位"
                />
              </div>
              <div>
                <label className="text-sm font-medium text-text-primary block mb-1">确认新密码</label>
                <input
                  type="password"
                  value={passwordForm.confirmPassword}
                  onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                  placeholder="再次输入新密码"
                />
              </div>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={() => setShowChangePassword(false)}
                className="px-4 py-2 text-sm text-text-secondary hover:bg-neutral-100 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleChangePassword}
                disabled={passwordLoading}
                className="px-4 py-2 text-sm bg-accent-blue text-white hover:bg-accent-blue/90 disabled:opacity-50 transition-colors flex items-center gap-2"
              >
                {passwordLoading && <Loader2 className="h-4 w-4 animate-spin" />}
                确认修改
              </button>
            </div>
          </div>
        </div>
      )}
    </header>
  )
}
