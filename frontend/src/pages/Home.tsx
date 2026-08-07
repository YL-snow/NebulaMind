import { useState, useEffect } from 'react'
import { Plus, Filter, Grid, List, TrendingUp, Folder, FileText, Shield, Cloud, Calendar, Star, Clock, ArrowRight, Copy, Trash2 } from 'lucide-react'
import { Button } from '@/components/common/Button'
import { Card, CardBody } from '@/components/common/Card'
import { FileCard } from '@/components/business/FileCard'
import { SearchBox } from '@/components/business/SearchBox'
import { Uploader, type UploadFile } from '@/components/business/Uploader'
import { Modal } from '@/components/common/Modal'
import { Loading } from '@/components/common/Loading'
import { useFileStore } from '@/stores/fileStore'
import { filesApi } from '@/api/files'
import { useToast } from '@/components/common/Toast'
import { formatFileSize } from '@/utils/format'
import type { DuplicateGroup } from '@/api/types'

export const Home = () => {
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [filterCategory, setFilterCategory] = useState('')
  const [uploadingFiles, setUploadingFiles] = useState<UploadFile[]>([])
  const [duplicateGroups, setDuplicateGroups] = useState<DuplicateGroup[]>([])
  const [showDuplicateModal, setShowDuplicateModal] = useState(false)
  const [duplicateLoading, setDuplicateLoading] = useState(false)

  const { files, loading, setFiles } = useFileStore()
  const { error, success } = useToast()

  useEffect(() => {
    fetchFiles()
  }, [])

  const fetchFiles = async () => {
    try {
      const response = await filesApi.list()
      setFiles(response.content || [])
    } catch (err) {
      error((err as Error).message || '获取文件列表失败')
    }
  }

  const handleSearch = (query: string) => {
    setSearchQuery(query)
  }

  const handleFileSelect = async (selectedFiles: File[]) => {
    const newFiles: UploadFile[] = selectedFiles.map((file, index) => ({
      id: `${Date.now()}-${index}-${Math.random().toString(36).substr(2, 5)}`,
      name: file.name,
      size: file.size,
      type: file.type.split('/')[1] || 'unknown',
      progress: 0,
      status: 'pending',
    }))
    setUploadingFiles(newFiles)

    let successCount = 0
    for (const [index, file] of selectedFiles.entries()) {
      const uploadId = newFiles[index].id
      
      setUploadingFiles((prev) =>
        prev.map((f, i) => (i === index ? { ...f, status: 'uploading' } : f))
      )

      const formData = new FormData()
      formData.append('file', file)

      try {
        await filesApi.upload(formData, (progress) => {
          setUploadingFiles((prev) =>
            prev.map((f) => (f.id === uploadId ? { ...f, progress } : f))
          )
        })

        setUploadingFiles((prev) =>
          prev.map((f) =>
            f.id === uploadId ? { ...f, progress: 100, status: 'completed' } : f
          )
        )
        successCount++
      } catch (err) {
        const errorMessage = (err as Error).message || ''
        setUploadingFiles((prev) =>
          prev.map((f) =>
            f.id === uploadId
              ? { ...f, status: 'error', error: errorMessage }
              : f
          )
        )
        if (errorMessage.includes('already exists')) {
          error(`文件 ${file.name} 已存在`)
        } else {
          error(errorMessage || `文件 ${file.name} 上传失败`)
        }
      }
    }

    if (successCount > 0) {
      await fetchFiles()
      success(`成功上传 ${successCount} 个文件`)
    }
  }

  const handleRemoveUploading = (id: string) => {
    setUploadingFiles((prev) => prev.filter((f) => f.id !== id))
  }

  const handleClearCompleted = () => {
    setUploadingFiles((prev) => prev.filter((f) => f.status !== 'completed'))
  }

  const handleDetectDuplicates = async () => {
    setDuplicateLoading(true)
    try {
      const groups = await filesApi.duplicates()
      setDuplicateGroups(groups)
      setShowDuplicateModal(true)
      if (groups.length === 0) {
        success('未发现重复文件')
      } else {
        success(`发现 ${groups.length} 组重复文件`)
      }
    } catch (err) {
      error((err as Error).message || '检测重复文件失败')
    } finally {
      setDuplicateLoading(false)
    }
  }

  const handleDeleteDuplicate = async (fileId: string, fileName: string) => {
    if (!confirm(`确定要删除重复文件 ${fileName} 吗？`)) return
    try {
      await filesApi.delete(fileId)
      setDuplicateGroups((prev) =>
        prev
          .map((g) => ({
            ...g,
            files: g.files.filter((f) => f.id !== fileId),
          }))
          .filter((g) => g.files.length > 1)
      )
      setFiles(files.filter((f) => f.id !== fileId))
      success('重复文件已删除')
    } catch (err) {
      error((err as Error).message || '删除失败')
    }
  }

  const handleDelete = async (fileId: string, fileName: string) => {
    if (!confirm(`确定要删除文件 ${fileName} 吗？`)) return
    try {
      await filesApi.delete(fileId)
      setFiles(files.filter((f) => f.id !== fileId))
      success('文件删除成功')
    } catch (err) {
      error((err as Error).message || '删除文件失败')
    }
  }

  const filteredFiles = files.filter((file) => {
    const matchesSearch = file.name.toLowerCase().includes(searchQuery.toLowerCase())
    const matchesCategory = !filterCategory || file.category === filterCategory
    return matchesSearch && matchesCategory
  })

  const totalSize = files.reduce((sum, file) => sum + file.size, 0)
  const maxSize = 500 * 1024 * 1024 * 1024
  const usagePercent = Math.round((totalSize / maxSize) * 100)

  const categoryStats = files.reduce((acc, file) => {
    const category = file.category || '未分类'
    acc[category] = (acc[category] || 0) + 1
    return acc
  }, {} as Record<string, number>)

  const completedCount = files.filter((f) => f.aiStatus === 'completed').length
  const encryptedCount = files.filter((f) => f.isEncrypted).length
  const highSensitiveCount = files.filter((f) => f.sensitiveLevel === 'high').length

  const recentFiles = [...files]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5)

  const importantFiles = files.filter((f) => f.sensitiveLevel === 'high' || f.category === '财务报告' || f.category === '合同').slice(0, 5)

  const stats = [
    {
      icon: Cloud,
      label: '存储空间',
      value: `${(totalSize / 1024 / 1024).toFixed(1)}GB / 500GB`,
      color: 'text-primary-500',
      bgColor: 'bg-primary-50',
      progress: usagePercent,
    },
    {
      icon: FileText,
      label: '总文件数',
      value: `${files?.length || 0}`,
      color: 'text-primary-500',
      bgColor: 'bg-primary-50',
    },
    {
      icon: TrendingUp,
      label: 'AI处理率',
      value: files?.length ? `${Math.round((completedCount / files.length) * 100)}%` : '0%',
      color: 'text-primary-500',
      bgColor: 'bg-primary-50',
    },
    {
      icon: Shield,
      label: '安全文件',
      value: `${encryptedCount}`,
      color: 'text-primary-500',
      bgColor: 'bg-primary-50',
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex-1 max-w-xl">
          <SearchBox onSearch={handleSearch} />
        </div>
        <div className="flex items-center gap-3">
          <Button variant="ghost" onClick={handleDetectDuplicates} disabled={duplicateLoading}>
            <Copy className="h-4 w-4 mr-2" />
            {duplicateLoading ? '检测中...' : '检测重复'}
          </Button>
          <Button variant="ghost" onClick={() => setShowUploadModal(true)}>
            <Plus className="h-4 w-4 mr-2" />
            上传文件
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat) => (
          <Card key={stat.label}>
            <CardBody className="flex items-center gap-4 h-full">
              <div className={`w-12 h-12 ${stat.bgColor} flex items-center justify-center`}>
                <stat.icon className={`h-6 w-6 ${stat.color}`} />
              </div>
              <div className="flex-1 flex flex-col justify-center">
                <p className="text-sm text-text-secondary">{stat.label}</p>
                <p className="text-xl font-bold text-text-primary">{stat.value}</p>
                {stat.progress !== undefined && (
                  <div className="mt-2 h-1.5 bg-neutral-100 overflow-hidden">
                    <div
                      className="h-full"
                      style={{ width: `${stat.progress}%`, backgroundColor: stat.color.replace('text-', 'bg-') }}
                    />
                  </div>
                )}
              </div>
            </CardBody>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Filter className="h-4 w-4 text-neutral-400" />
              <select
                value={filterCategory}
                onChange={(e) => setFilterCategory(e.target.value)}
                className="px-3 py-1.5 border border-neutral-200 text-sm text-neutral-700 focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                <option value="">全部分类</option>
                {Object.keys(categoryStats).map((category) => (
                  <option key={category} value={category}>
                    {category} ({categoryStats[category]})
                  </option>
                ))}
              </select>
            </div>
            <div className="flex items-center gap-2 bg-neutral-100 p-1">
              <button
                onClick={() => setViewMode('grid')}
                className={`p-2 transition-colors ${viewMode === 'grid' ? 'bg-white text-accent-blue' : 'text-text-secondary'}`}
              >
                <Grid className="h-4 w-4" />
              </button>
              <button
                onClick={() => setViewMode('list')}
                className={`p-2 transition-colors ${viewMode === 'list' ? 'bg-white text-accent-blue' : 'text-text-secondary'}`}
              >
                <List className="h-4 w-4" />
              </button>
            </div>
          </div>

          {loading ? (
            <Loading text="加载文件列表..." />
          ) : filteredFiles.length > 0 ? (
            <div className={`grid gap-4 ${viewMode === 'grid' ? 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3' : 'grid-cols-1'}`}>
              {filteredFiles.map((file) => (
                <FileCard
                  key={file.id}
                  file={file}
                  onClick={() => window.location.href = `/files/${file.id}`}
                  onDownload={async () => {
                    try {
                      const blob: Blob = await filesApi.download(file.id) as unknown as Blob;
                      const url = window.URL.createObjectURL(new Blob([blob]));
                      const a = document.createElement('a');
                      a.href = url;
                      a.download = file.name;
                      document.body.appendChild(a);
                      a.click();
                      document.body.removeChild(a);
                      window.URL.revokeObjectURL(url);
                    } catch (err) {
                      const msg = (err as Error).message || '下载失败';
                      console.error('Download failed:', msg);
                    }
                  }}
                  onDelete={() => handleDelete(file.id, file.name)}
                />
              ))}
            </div>
          ) : (
            <div className="text-center py-16">
              <Folder className="h-16 w-16 text-neutral-300 mx-auto mb-4" />
              <p className="text-text-secondary">暂无文件，点击上传按钮添加文件</p>
            </div>
          )}
        </div>

        <div className="space-y-6">
          <Card>
            <div className="p-4 border-b border-neutral-200">
              <div className="flex items-center gap-2">
                <Clock className="h-5 w-5 text-accent-blue" />
                <h3 className="font-semibold text-text-primary">最近上传</h3>
              </div>
            </div>
            <CardBody>
              {recentFiles.length > 0 ? (
                <div className="space-y-3">
                  {recentFiles.map((file) => (
                    <div
                      key={file.id}
                      className="flex items-center gap-3 p-2 hover:bg-neutral-50 cursor-pointer transition-colors"
                      onClick={() => window.location.href = `/files/${file.id}`}
                    >
                      <div className="w-8 h-8 bg-neutral-100 flex items-center justify-center">
                        <FileText className="h-4 w-4 text-text-secondary" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-text-primary truncate">{file.name}</p>
                        <p className="text-xs text-text-secondary">{new Date(file.createdAt).toLocaleDateString()}</p>
                      </div>
                      <ArrowRight className="h-4 w-4 text-neutral-400" />
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-neutral-400 text-center py-4">暂无最近上传的文件</p>
              )}
            </CardBody>
          </Card>

          {highSensitiveCount > 0 && (
            <Card>
              <div className="p-4 border-b border-neutral-200">
                <div className="flex items-center gap-2">
                  <Star className="h-5 w-5 text-yellow-500" />
                  <h3 className="font-semibold text-text-primary">重要文件</h3>
                </div>
              </div>
              <CardBody>
                {importantFiles.length > 0 ? (
                  <div className="space-y-3">
                    {importantFiles.map((file) => (
                      <div
                        key={file.id}
                        className="flex items-center gap-3 p-2 hover:bg-neutral-50 cursor-pointer transition-colors"
                        onClick={() => window.location.href = `/files/${file.id}`}
                      >
                        <div className="w-8 h-8 bg-red-50 flex items-center justify-center">
                          <Shield className="h-4 w-4 text-red-500" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-text-primary truncate">{file.name}</p>
                          <p className="text-xs text-text-secondary">敏感级别: {file.sensitiveLevel}</p>
                        </div>
                        <ArrowRight className="h-4 w-4 text-neutral-400" />
                      </div>
                    ))}
                  </div>
                ) : null}
              </CardBody>
            </Card>
          )}

          <Card>
            <div className="p-4 border-b border-neutral-200">
              <div className="flex items-center gap-2">
                <Calendar className="h-5 w-5 text-accent-blue" />
                <h3 className="font-semibold text-text-primary">今日待办</h3>
              </div>
            </div>
            <CardBody>
              <ul className="space-y-2 text-sm text-neutral-600">
                {completedCount < files.length && (
                  <li className="flex items-start gap-2">
                    <input type="checkbox" className="mt-1 w-4 h-4 rounded border-neutral-300 text-accent-blue" />
                    <span>等待AI分析 {files.length - completedCount} 个文件</span>
                  </li>
                )}
                {highSensitiveCount > encryptedCount && (
                  <li className="flex items-start gap-2">
                    <input type="checkbox" className="mt-1 w-4 h-4 rounded border-neutral-300 text-accent-blue" />
                    <span>加密 {highSensitiveCount - encryptedCount} 个敏感文件</span>
                  </li>
                )}
                <li className="flex items-start gap-2">
                  <input type="checkbox" className="mt-1 w-4 h-4 rounded border-neutral-300 text-accent-blue" />
                  <span>整理文件分类</span>
                </li>
              </ul>
            </CardBody>
          </Card>
        </div>
      </div>

      <Modal isOpen={showUploadModal} onClose={() => setShowUploadModal(false)} title="上传文件">
        <Uploader
          files={uploadingFiles}
          onFileSelect={handleFileSelect}
          onRemove={handleRemoveUploading}
          onClearCompleted={handleClearCompleted}
        />
      </Modal>

      <Modal isOpen={showDuplicateModal} onClose={() => setShowDuplicateModal(false)} title="重复文件检测">
        {duplicateGroups.length > 0 ? (
          <div className="space-y-4">
            <p className="text-sm text-text-secondary">发现 {duplicateGroups.length} 组重复文件，每组保留一份即可</p>
            {duplicateGroups.map((group, idx) => (
              <div key={group.hash} className="border border-neutral-200 rounded-lg p-3">
                <p className="text-xs text-text-secondary mb-2">重复组 #{idx + 1} · Hash: {group.hash.slice(0, 12)}...</p>
                <div className="space-y-2">
                  {group.files.map((file) => (
                    <div key={file.id} className="flex items-center justify-between p-2 bg-neutral-50 rounded">
                      <div className="flex items-center gap-3 min-w-0">
                        <FileText className="h-4 w-4 text-text-secondary flex-shrink-0" />
                        <div className="min-w-0">
                          <p className="text-sm text-text-primary truncate">{file.name}</p>
                          <p className="text-xs text-text-secondary">{formatFileSize(file.size)}</p>
                        </div>
                      </div>
                      <button
                        onClick={() => handleDeleteDuplicate(file.id, file.name)}
                        className="p-1.5 text-text-secondary hover:text-red-500 transition-colors flex-shrink-0"
                        title="删除此副本"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-8">
            <FileText className="h-12 w-12 text-green-500 mx-auto mb-3" />
            <p className="text-text-primary font-medium">未发现重复文件</p>
            <p className="text-sm text-text-secondary mt-1">所有文件都是唯一的</p>
          </div>
        )}
      </Modal>
    </div>
  )
}