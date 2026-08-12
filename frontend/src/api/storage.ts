import { request } from '@/utils/request'

export type StorageProviderType = 'S3' | 'WEBDAV'

export interface CloudStorageConfig {
  id: string
  name: string
  providerType: StorageProviderType
  endpointUrl?: string
  accessKey?: string
  secretKey?: string
  bucketName?: string
  region?: string
  isActive: boolean
  lastTestSuccess?: boolean
  lastTestAt?: string
  extraConfig?: string
  createdAt: string
  updatedAt: string
}

export interface CloudStorageItem {
  path: string
  name: string
  folder: boolean
  size?: number
  mimeType?: string
  updatedAt?: string
}

export interface TestConnectionResult {
  success: boolean
  message: string
  testedAt: string
}

export interface ImportResult {
  imported: boolean
  duplicate: boolean
  message: string
  fileId?: string
}

export const storageApi = {
  list: () => request.get<CloudStorageConfig[]>('/storage-config'),

  create: (config: Partial<CloudStorageConfig>) =>
    request.post<CloudStorageConfig>('/storage-config', config),

  update: (id: string, config: Partial<CloudStorageConfig>) =>
    request.put<CloudStorageConfig>(`/storage-config/${id}`, config),

  delete: (id: string) => request.delete(`/storage-config/${id}`),

  testConnection: (id: string) =>
    request.post<TestConnectionResult>(`/storage-config/${id}/test`),

  listFiles: (id: string, path?: string) =>
    request.get<CloudStorageItem[]>(`/storage-config/${id}/drive/files`, {
      params: { path: path || '' },
    }),

  download: (id: string, path: string) =>
    request.get(`/storage-config/${id}/drive/download`, {
      params: { path },
      responseType: 'blob',
    }),

  upload: (id: string, path: string, file: File, onProgress?: (progress: number) => void) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.upload<{ message: string }>(
      `/storage-config/${id}/drive/files/upload`,
      formData,
      onProgress,
      { params: { path: path || '', name: file.name } },
    )
  },

  removeFile: (id: string, path: string) =>
    request.delete<{ message: string }>(`/storage-config/${id}/drive/files`, {
      params: { path },
    }),

  importFile: (id: string, path: string, name?: string) =>
    request.post<ImportResult>(
      `/storage-config/${id}/drive/files/import`,
      null,
      { params: { path, ...(name ? { name } : {}) } },
    ),
}
