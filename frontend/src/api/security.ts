import { request } from '@/utils/request'
import type {
  SecurityDetectResponse,
  SecurityEncryptResponse,
} from './types'

export const securityApi = {
  detect: (data: { fileId: string; autoEncrypt?: boolean }) => request.post<SecurityDetectResponse>('/security/detect', data),

  encrypt: (data: { fileId: string; reason?: string }) => request.post<SecurityEncryptResponse>('/security/encrypt', data),

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
}
