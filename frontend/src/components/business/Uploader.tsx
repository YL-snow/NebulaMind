import { useCallback } from 'react'
import { Upload, X, FileText, CheckCircle, AlertCircle } from 'lucide-react'
import { ProgressBar } from '@/components/common/ProgressBar'
import { formatFileSize } from '@/utils/format'

export interface UploadFile {
  id: string
  name: string
  size: number
  type: string
  progress: number
  status: 'pending' | 'uploading' | 'completed' | 'error'
  error?: string
}

interface UploaderProps {
  files: UploadFile[]
  onFileSelect: (files: File[]) => void
  onRemove: (id: string) => void
  onClearCompleted: () => void
}

export const Uploader = ({ files, onFileSelect, onRemove, onClearCompleted }: UploaderProps) => {
  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
  }, [])

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      const droppedFiles = Array.from(e.dataTransfer.files)
      if (droppedFiles.length > 0) {
        onFileSelect(droppedFiles)
      }
    },
    [onFileSelect]
  )

  const handleFileInput = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const selectedFiles = Array.from(e.target.files || [])
      if (selectedFiles.length > 0) {
        onFileSelect(selectedFiles)
      }
      e.target.value = ''
    },
    [onFileSelect]
  )

  const completedCount = files.filter((f) => f.status === 'completed').length

  return (
    <div className="space-y-4">
      <div
        className={`border-2 border-dashed rounded-card p-8 text-center transition-all duration-200 cursor-pointer ${
          'border-neutral-200 hover:border-accent-blue/40 hover:bg-neutral-100/50'
        }`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => document.getElementById('file-input')?.click()}
      >
        <input
          id="file-input"
          type="file"
          multiple
          className="hidden"
          onChange={handleFileInput}
          accept=".pdf,.docx,.xlsx,.pptx,.jpg,.jpeg,.png,.gif,.txt,.zip"
        />
        <div className="flex flex-col items-center gap-3">
          <div className="w-12 h-12 bg-neutral-100 rounded-full flex items-center justify-center">
            <Upload className="h-6 w-6 text-text-secondary" />
          </div>
          <div>
            <p className="font-medium text-text-primary">拖拽文件到此处上传</p>
            <p className="text-sm text-text-secondary mt-1">
              或点击选择文件 · 支持 PDF, Word, Excel, PPT, 图片, 压缩文件
            </p>
          </div>
        </div>
      </div>

      {files.length > 0 && (
        <>
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-text-secondary">
              正在上传 {files.filter((f) => f.status === 'uploading').length} 个文件
              {completedCount > 0 && `，已完成 ${completedCount} 个`}
            </span>
            {completedCount > 0 && (
              <button
                onClick={onClearCompleted}
                className="text-sm text-accent-blue hover:text-accent-blue/80"
              >
                清除已完成
              </button>
            )}
          </div>
          <div className="space-y-2">
            {files.map((file) => (
              <div
                key={file.id}
                className="flex items-center gap-3 p-3 bg-white border border-neutral-200 rounded-card"
              >
                <div className={`w-10 h-10 rounded-button flex items-center justify-center ${
                  file.status === 'completed' ? 'bg-green-50' :
                  file.status === 'error' ? 'bg-red-50' : 'bg-accent-blue/10'
                }`}>
                  {file.status === 'completed' ? (
                    <CheckCircle className="h-5 w-5 text-green-500" />
                  ) : file.status === 'error' ? (
                    <AlertCircle className="h-5 w-5 text-red-500" />
                  ) : (
                    <FileText className="h-5 w-5 text-accent-blue" />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-text-primary truncate">{file.name}</p>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-xs text-text-secondary">{formatFileSize(file.size)}</span>
                    <span
                      className={`text-xs ${
                        file.status === 'completed'
                          ? 'text-green-500'
                          : file.status === 'error'
                            ? 'text-red-500'
                            : 'text-accent-blue'
                      }`}
                    >
                      {file.status === 'pending' && '等待上传'}
                      {file.status === 'uploading' && '上传中...'}
                      {file.status === 'completed' && '上传完成'}
                      {file.status === 'error' && (file.error || '上传失败')}
                    </span>
                  </div>
                  {(file.status === 'uploading' || file.status === 'completed') && (
                    <ProgressBar progress={file.progress} size="sm" className="mt-2" />
                  )}
                </div>
                <button
                  onClick={() => onRemove(file.id)}
                  className="p-1 text-text-secondary hover:text-red-500 transition-colors"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}