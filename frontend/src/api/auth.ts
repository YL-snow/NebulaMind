import { request } from '@/utils/request'
import type { AuthResponse } from './types'

export const authApi = {
  login: (data: { email: string; password: string }) =>
    request.post<AuthResponse>('/auth/login', data),

  register: (data: { username: string; email: string; password: string; displayName: string }) =>
    request.post<AuthResponse>('/auth/register', data),

  refresh: (refreshToken: string) =>
    request.post<AuthResponse>('/auth/refresh', null, { params: { token: refreshToken } }),

  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    request.post<{ success: boolean; message: string }>('/auth/change-password', data),
}