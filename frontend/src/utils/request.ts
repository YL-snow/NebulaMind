import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL 
  ? `${import.meta.env.VITE_API_BASE_URL.replace(/\/$/, '')}/api/v1` 
  : '/api/v1'

interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  requestId: string
  timestamp: string
}

interface ErrorResponse {
  code?: number
  message?: string
}

const instance: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

instance.interceptors.response.use(
  (response) => {
    const responseData = response.data
    if (responseData && typeof responseData === 'object' && 'code' in responseData) {
      if (responseData.code === 200) {
        return responseData.data
      }
      throw new Error(responseData.message || '请求失败')
    }
    return responseData
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      const errorData = data as ErrorResponse
      if (status === 401) {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        window.location.href = '/login'
      }
      const message = errorData.message || `请求失败 (${status})`
      throw new Error(message)
    } else if (error.request) {
      throw new Error('网络错误，请求未发送')
    } else {
      throw new Error('请求配置错误')
    }
  }
)

export const request = {
  get: <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    instance.get(url, config) as Promise<T>,
  post: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    instance.post(url, data, config) as Promise<T>,
  put: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    instance.put(url, data, config) as Promise<T>,
  delete: <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    instance.delete(url, config) as Promise<T>,
  patch: <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    instance.patch(url, data, config) as Promise<T>,
  upload: <T = unknown>(url: string, data: FormData, onProgress?: (progress: number) => void, config?: AxiosRequestConfig): Promise<T> =>
    instance.post(url, data, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      },
      ...config,
    }) as Promise<T>,
}

export type { ApiResponse }
