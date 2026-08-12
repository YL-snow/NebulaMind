import { create } from 'zustand'
import type { User } from '@/api/types'

interface AuthState {
  user: User | null
  accessToken: string | null
  refreshToken: string | null
  isAuthenticated: boolean
  login: (user: User, accessToken: string, refreshToken: string) => void
  logout: () => void
  refreshTokenFn: (newAccessToken: string, newRefreshToken: string) => void
}

function isStoredTokenValid(token: string | null): boolean {
  if (!token) return false
  try {
    const base64Url = token.split('.')[1]
    if (!base64Url) return false
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const payload = JSON.parse(atob(padded))
    if (typeof payload.exp !== 'number') return true
    return payload.exp * 1000 > Date.now()
  } catch {
    return false
  }
}

function loadStoredSession() {
  const accessToken = localStorage.getItem('accessToken')
  const refreshToken = localStorage.getItem('refreshToken')
  let user: User | null = null
  try {
    user = JSON.parse(localStorage.getItem('user') || 'null')
  } catch {
    user = null
  }

  if (accessToken && !isStoredTokenValid(accessToken)) {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
    return { accessToken: null, refreshToken: null, user: null, isAuthenticated: false }
  }

  return { accessToken, refreshToken, user, isAuthenticated: !!accessToken }
}

const storedSession = loadStoredSession()

export const useAuthStore = create<AuthState>((set) => ({
  user: storedSession.user,
  accessToken: storedSession.accessToken,
  refreshToken: storedSession.refreshToken,
  isAuthenticated: storedSession.isAuthenticated,

  login: (user, accessToken, refreshToken) => {
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    localStorage.setItem('user', JSON.stringify(user))
    set({ user, accessToken, refreshToken, isAuthenticated: true })
  },

  logout: () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
    set({ user: null, accessToken: null, refreshToken: null, isAuthenticated: false })
  },

  refreshTokenFn: (newAccessToken, newRefreshToken) => {
    localStorage.setItem('accessToken', newAccessToken)
    localStorage.setItem('refreshToken', newRefreshToken)
    set({ accessToken: newAccessToken, refreshToken: newRefreshToken })
  },
}))
