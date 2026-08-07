import { request } from '@/utils/request'

export interface CloudStorageConfig {
  id: string
  name: string
  providerType: 'S3' | 'UNICOM'
  endpointUrl?: string
  accessKey?: string
  secretKey?: string
  bucketName?: string
  region?: string
  redirectUri?: string
  isActive: boolean
  lastTestSuccess?: boolean
  lastTestAt?: string
  extraConfig?: string
  createdAt: string
  updatedAt: string
}

export interface TestConnectionResult {
  success: boolean
  message: string
  testedAt: string
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
}
