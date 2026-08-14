import { FileText, Download, Trash2, Eye } from 'lucide-react'
import { Lock } from 'lucide-react'
import type { FileItem } from '@/api/types'
import { formatFileSize, formatDate } from '@/utils/format'
import { FILE_TYPES, SENSITIVE_LEVELS, AI_STATUS } from '@/utils/constants'

interface FileCardProps {
  file: FileItem
  onClick?: () => void
  onDownload?: () => void
  onDelete?: () => void
}

export const FileCard = ({ file, onClick, onDownload, onDelete }: FileCardProps) => {
  const fileType = FILE_TYPES[file.fileType as keyof typeof FILE_TYPES] || FILE_TYPES.default
  const fileTags: string[] = (() => { try { const t = JSON.parse(file.tags); return Array.isArray(t) ? t : []; } catch { return []; } })()
  const sensitiveLevel = SENSITIVE_LEVELS[file.sensitiveLevel?.toLowerCase() as keyof typeof SENSITIVE_LEVELS] || SENSITIVE_LEVELS.normal
  const aiStatus = AI_STATUS[file.aiStatus?.toLowerCase() as keyof typeof AI_STATUS] || AI_STATUS.pending

  return (
    <div
      className="bg-white border border-neutral-200 p-4 hover:shadow-card-hover hover:border-accent-blue/30 transition-all duration-300 cursor-pointer group"
      onClick={onClick}
    >
      <div className="flex items-start gap-4">
        <div
          className="w-12 h-12 flex items-center justify-center flex-shrink-0"
          style={{ backgroundColor: `${fileType.color}12` }}
        >
          <FileText className="h-6 w-6" style={{ color: fileType.color }} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-medium text-text-primary truncate">{file.name}</h3>
            {file.encryptionMode === 'CLIENT' && (
              <span title="端到端加密（文件密钥）" className="flex-shrink-0">
                <Lock className="h-4 w-4 text-green-500" />
              </span>
            )}
            {file.isEncrypted && file.encryptionMode !== 'CLIENT' && (
              <span title="服务端加密" className="flex-shrink-0">
                <Lock className="h-4 w-4 text-neutral-400" />
              </span>
            )}
            <span
              className="px-2 py-0.5 text-xs rounded-full whitespace-nowrap flex-shrink-0"
              style={{ backgroundColor: sensitiveLevel.bgColor, color: sensitiveLevel.color }}
            >
              {sensitiveLevel.label}
            </span>
          </div>
          <div className="flex items-center gap-3 mt-1">
            <span className="text-xs text-text-secondary">{fileType.label}</span>
            <span className="text-xs text-text-secondary">{formatFileSize(file.size)}</span>
            <span className="text-xs text-text-secondary">{formatDate(file.createdAt)}</span>
          </div>
          {fileTags.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-2">
              {fileTags.slice(0, 3).map((tag) => (
                <span
                  key={tag}
                  className="px-2 py-0.5 bg-accent-blue/8 text-accent-blue text-xs rounded-full"
                >
                  {tag}
                </span>
              ))}
              {fileTags.length > 3 && (
                <span className="px-2 py-0.5 bg-neutral-100 text-text-secondary text-xs rounded-full">
                  +{fileTags.length - 3}
                </span>
              )}
            </div>
          )}
        </div>
      </div>
      <div className="flex items-center justify-end gap-2 mt-4 pt-4 border-t border-neutral-100 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={(e) => {
            e.stopPropagation()
            onClick?.()
          }}
          className="p-2 text-text-secondary hover:text-accent-blue hover:bg-accent-blue/8 transition-colors"
          title="查看详情"
        >
          <Eye className="h-4 w-4" />
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onDownload?.()
          }}
          className="p-2 text-text-secondary hover:text-accent-blue hover:bg-accent-blue/8 transition-colors"
          title={file.encryptionMode === 'CLIENT' ? '解密并下载' : '下载'}
        >
          <Download className="h-4 w-4" />
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onDelete?.()
          }}
          className="p-2 text-text-secondary hover:text-red-500 hover:bg-red-50 transition-colors"
          title="删除"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>
      {file.aiStatus !== 'completed' && (
        <div className="mt-3">
          <div className="flex items-center justify-between text-xs text-text-secondary">
            <span>AI处理</span>
            <span style={{ color: aiStatus.color }}>{aiStatus.label}</span>
          </div>
          {file.aiStatus === 'processing' && (
            <div className="w-full bg-neutral-100 h-1 mt-1">
              <div
                className="bg-accent-blue h-1 transition-all duration-300"
                style={{ width: '50%' }}
              />
            </div>
          )}
        </div>
      )}
    </div>
  )
}
