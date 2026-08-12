import { request } from '@/utils/request'
import type {
  FileItem,
  FileListResponse,
  ClassifyResponse,
  DuplicateGroup,
} from './types'

export const filesApi = {
  list: (params?: {
    page?: number
    pageSize?: number
    parentId?: string
    sortBy?: string
    sortOrder?: string
    category?: string
    tag?: string
  }) => request.get<FileListResponse>('/files', { params }),

  detail: (fileId: string) => request.get<FileItem>(`/files/${fileId}`),

  upload: (formData: FormData, onProgress?: (progress: number) => void, encrypted?: boolean) =>
    request.post<FileItem>('/files/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params: encrypted ? { encrypted: 'true' } : undefined,
      onUploadProgress: onProgress
        ? (event) => {
            if (event.total) {
              onProgress(Math.round((event.loaded / event.total) * 100))
            }
          }
        : undefined,
    }),

  download: (fileId: string) =>
    request.get(`/files/${fileId}/download`, { responseType: 'blob' }),

  update: (fileId: string, data: { name?: string; tags?: string }) =>
    request.put<FileItem>(`/files/${fileId}`, data),

  delete: (fileId: string) => request.delete(`/files/${fileId}`),

  classify: (fileId: string) => request.post<ClassifyResponse>(`/files/${fileId}/classify`),

  duplicates: (hash?: string) => {
    const params: Record<string, string> = {}
    if (hash) params.hash = hash
    return request.get<DuplicateGroup[]>('/files/duplicates', { params })
  },

  versionHistory: async (fileId: string) => {
    const versions = await request.get(`/files/${fileId}/versions`);
    const arr = Array.isArray(versions) ? versions : (versions as any)?.content || [];
    return {
      fileId,
      versions: arr.map((v: any) => ({
        version: v.versionNumber ?? v.version,
        fileSize: v.fileSize,
        modifiedBy: v.createdBy || v.modifiedBy,
        comment: v.comment || '',
        createdAt: v.createdAt,
      })),
    };
  },

  uploadVersion: (fileId: string, file: File, comment: string, onProgress?: (progress: number) => void, encrypted?: boolean) => {
    const formData = new FormData()
    formData.append('file', file)
    if (comment) formData.append('comment', comment)
    return request.upload<FileItem>(`/files/${fileId}/versions/upload${encrypted ? '?encrypted=true' : ''}`, formData, onProgress)
  },

  saveTextVersion: (fileId: string, content: string, comment: string) =>
    request.post<FileItem>(`/files/${fileId}/versions`, { content, comment }),

  versionSummary: (fileId: string, versionA?: number, versionB?: number) =>
    request.post<{ fileId: string; versionA: number; versionB: number; summary: string }>(
      `/files/${fileId}/versions/summary`,
      { versionA, versionB },
      { timeout: 60000 }
    ),

  versionDiff: (fileId: string, versionA: number, versionB: number) =>
    request.get<{
      fileId: string
      versionA: number
      versionB: number
      versionACreatedAt: string
      versionBCreatedAt: string
      versionACreator: string
      versionBCreator: string
      sizeDelta: number
      diff: string
      additions: number
      deletions: number
      modifications: number
      diffFormat: string
    }>(`/files/${fileId}/versions/diff`, { params: { versionA, versionB } }),

  restoreVersion: (fileId: string, version: number) =>
    request.post<{ fileId: string; currentVersion: number; rolledBackFrom: number; message: string }>(
      `/files/${fileId}/versions/rollback/${version}`,
    ),
}
