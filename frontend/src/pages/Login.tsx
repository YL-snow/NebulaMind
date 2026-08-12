import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff, FolderOpen } from 'lucide-react'
import { Input } from '@/components/common/Input'
import { useAuthStore } from '@/stores/authStore'
import { authApi } from '@/api/auth'
import { useToast } from '@/components/common/Toast'

export const Login = () => {
  const [isLogin, setIsLogin] = useState(true)
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)

  const { login } = useAuthStore()
  const { success, error } = useToast()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!isLogin) {
      const trimmedUsername = username.trim()
      const trimmedDisplayName = displayName.trim()
      if (trimmedUsername.length < 2 || trimmedUsername.length > 100) {
        error('用户名需为 2-100 个字符')
        return
      }
      if (!trimmedDisplayName) {
        error('请输入显示名称')
        return
      }
    }
    if (password.length < 6) {
      error('密码至少需要 6 位')
      return
    }

    setLoading(true)

    try {
      if (isLogin) {
        const response = await authApi.login({ email: email.trim(), password })
        login(
          {
            id: response.userId,
            username: response.email.split('@')[0],
            displayName: response.displayName,
            role: response.role.toLowerCase(),
          },
          response.accessToken,
          response.refreshToken,
        )
        success('登录成功')
        navigate('/home', { replace: true })
      } else {
        await authApi.register({
          username: username.trim(),
          email: email.trim(),
          password,
          displayName: displayName.trim(),
        })
        success('注册成功，请登录')
        setIsLogin(true)
        setEmail('')
        setUsername('')
        setPassword('')
        setDisplayName('')
      }
    } catch (err) {
      error((err as Error).message || '操作失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-base via-white to-base flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-accent-blue rounded-card mb-4">
            <FolderOpen className="h-8 w-8 text-white" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-text-primary">NebulaMind</h1>
        </div>

        <div className="bg-white rounded-card shadow-card p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            {isLogin ? (
              <Input
                label="邮箱"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="请输入邮箱"
                required
              />
            ) : (
              <>
                <Input
                  label="用户名"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="请输入用户名（2-100 个字符）"
                  required
                  minLength={2}
                  maxLength={100}
                />
                <Input
                  label="邮箱"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="请输入邮箱"
                  required
                />
                <Input
                  label="显示名称"
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="请输入显示名称"
                  required
                />
              </>
            )}

            <Input
              label="密码"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="请输入密码（至少 6 位）"
              required
              minLength={6}
              suffix={
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="text-neutral-400 hover:text-neutral-600"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              }
            />

            {isLogin && (
              <div className="flex items-center justify-between text-sm">
                <label className="flex items-center gap-2 text-neutral-500 cursor-pointer">
                  <input type="checkbox" className="rounded border-neutral-300 text-accent-blue" />
                  记住我
                </label>
                <a href="#" className="text-accent-blue hover:text-accent-blue/80">
                  忘记密码？
                </a>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full px-6 py-3 text-base font-medium rounded-button bg-accent-blue text-white hover:bg-accent-blue/90 disabled:opacity-50 transition-all shadow-sm"
            >
              {loading ? '处理中...' : isLogin ? '登录' : '注册'}
            </button>
          </form>
        </div>

        <p className="text-center text-sm text-text-secondary mt-6">
          {isLogin ? '还没有账号？' : '已有账号？'}
          <button
            onClick={() => setIsLogin(!isLogin)}
            className="text-accent-blue hover:text-accent-blue/80 ml-1"
          >
            {isLogin ? '立即注册' : '立即登录'}
          </button>
        </p>
      </div>
    </div>
  )
}
