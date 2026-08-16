import { useState, useEffect } from 'react'
import { Plus, Filter, Grid, List, TrendingUp, Folder, FileText, Shield, Cloud, Star, Clock, ArrowRight, Copy, Trash2, Download, KeyRound } from 'lucide-react'
import { Lock, LockOpen } from 'lucide-react'
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
import { copyText } from '@/utils/clipboard'
import { generateFileKey, encryptBlobWithFileKey, decryptBlobWithFileKey } from '@/utils/e2eeCrypto'
import type { DuplicateGroup, FileItem } from '@/api/types'

export const Home = () => {
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [filterCategory, setFilterCategory] = useState('')
  const [uploadingFiles, setUploadingFiles] = useState<UploadFile[]>([])
  const [duplicateGroups, setDuplicateGroups] = useState<DuplicateGroup[]>([])
  const [showDuplicateModal, setShowDuplicateModal] = useState(false)
  const [duplicateLoading, setDuplicateLoading] = useState(false)
  const [e2eeUpload, setE2eeUpload] = useState(false)
  const [oneTimeKeys, setOneTimeKeys] = useState<{ fileName: string; key: string }[]>([])
  const [showOneTimeKeyModal, setShowOneTimeKeyModal] = useState(false)
  const [copiedKeyIndex, setCopiedKeyIndex] = useState<number | null>(null)
  const [keyPrompt, setKeyPrompt] = useState<{ fileId: string; fileName: string } | null>(null)
  const [keyPromptInput, setKeyPromptInput] = useState('')
  const [keyPromptError, setKeyPromptError] = useState('')
  const [keyPromptLoading, setKeyPromptLoading] = useState(false)
  const [unlockedFileKeys, setUnlockedFileKeys] = useState<Record<string, string>>({})

  const { files, loading, setFiles } = useFileStore()
  const { error, success } = useToast()

  useEffect(() => {
    fetchFiles()
  }, [])

  const fetchFiles = async () => {
    try {
      const pageSize = 500
      const allFiles: FileItem[] = []
      let page = 0
      let totalElements = 0
      do {
        const response = await filesApi.list({ page, pageSize })
        const content = response.content || []
        allFiles.push(...content)
        totalElements = response.totalElements ?? allFiles.length
        page += 1
      } while (allFiles.length < totalElements && page < 50)
      setFiles(allFiles)
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
      status: e2eeUpload ? 'encrypting' : 'pending',
    }))
    setUploadingFiles(newFiles)

    const newKeys: { fileName: string; key: string }[] = []

    let successCount = 0
    for (const [index, file] of selectedFiles.entries()) {
      const uploadId = newFiles[index].id
      
      setUploadingFiles((prev) =>
        prev.map((f, i) => (i === index ? { ...f, status: e2eeUpload ? 'encrypting' : 'uploading' } : f))
      )

      const formData = new FormData()
      formData.append('file', file)
      let pendingKey: string | null = null
      if (e2eeUpload) {
        const fileKey = await generateFileKey()
        const plain = new Uint8Array(await file.arrayBuffer())
        const encryptedData = await encryptBlobWithFileKey(plain, fileKey)
        formData.set('file', new File([encryptedData], file.name, { type: file.type }))
        pendingKey = fileKey.base64
        setUploadingFiles((prev) =>
          prev.map((f) => (f.id === uploadId ? { ...f, status: 'uploading', progress: 0 } : f))
        )
      }

      try {
        await filesApi.upload(
          formData,
          (progress) => {
            setUploadingFiles((prev) =>
              prev.map((f) => (f.id === uploadId ? { ...f, progress } : f))
            )
          },
          e2eeUpload
        )

        setUploadingFiles((prev) =>
          prev.map((f) =>
            f.id === uploadId ? { ...f, progress: 100, status: 'completed' } : f
          )
        )
        successCount++
        if (pendingKey) {
          newKeys.push({ fileName: file.name, key: pendingKey })
        }
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
      if (newKeys.length > 0) {
        setOneTimeKeys(newKeys)
        setShowOneTimeKeyModal(true)
        success(`成功上传 ${successCount} 个文件，请立即保存文件密钥`)
      } else {
        success(`成功上传 ${successCount} 个文件`)
      }
    }
  }

  const handleRemoveUploading = (id: string) => {
    setUploadingFiles((prev) => prev.filter((f) => f.id !== id))
  }

  const handleClearCompleted = () => {
    setUploadingFiles((prev) => prev.filter((f) => f.status !== 'completed'))
  }

  const handleCopyOneTimeKey = async (index: number) => {
    const item = oneTimeKeys[index]
    if (!item) return
    const copied = await copyText(item.key)
    if (!copied) {
      error('复制失败，请手动选择密钥后复制')
      return
    }
    setCopiedKeyIndex(index)
    setTimeout(() => setCopiedKeyIndex(null), 2000)
  }

  const handleDownloadOneTimeKeys = () => {
    if (oneTimeKeys.length === 0) return
    const content = [
      'NebulaMind 文件密钥备份',
      '请妥善保管以下密钥，服务器不会保存。密钥只在加密上传时显示一次，丢失后文件无法解密。',
      '',
      ...oneTimeKeys.map((item) => `文件名: ${item.fileName}\n文件密钥: ${item.key}`)
    ].join('\n\n')
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'nebulamind-file-keys.txt'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  }

  const handleDownloadFile = async (file: FileItem) => {
    try {
      let key: string | undefined
      if (file.isEncrypted && file.encryptionMode !== 'SERVER') {
        key = unlockedFileKeys[file.id]
        if (!key) {
          setKeyPrompt({ fileId: file.id, fileName: file.name })
          setKeyPromptInput('')
          setKeyPromptError('')
          return
        }
      }
      const blob: Blob = await filesApi.download(file.id) as unknown as Blob
      let bytes = new Uint8Array(await blob.arrayBuffer())
      if (file.isEncrypted && file.encryptionMode !== 'SERVER' && key) {
        bytes = await decryptBlobWithFileKey(bytes, key)
      }
      const url = window.URL.createObjectURL(new Blob([bytes], { type: file.mimeType || blob.type }))
      const a = document.createElement('a')
      a.href = url
      a.download = file.name
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
    } catch (err) {
      const msg = (err as Error).message || '下载失败'
      error(msg)
      console.error('Download failed:', msg)
    }
  }

  const handleConfirmKeyPrompt = async () => {
    if (!keyPrompt) return
    const key = keyPromptInput.trim()
    if (!key) {
      setKeyPromptError('请输入文件密钥')
      return
    }
    setKeyPromptLoading(true)
    try {
      const blob: Blob = await filesApi.download(keyPrompt.fileId) as unknown as Blob
      const bytes = new Uint8Array(await blob.arrayBuffer())
      const plain = await decryptBlobWithFileKey(bytes, key)
      setUnlockedFileKeys((prev) => ({ ...prev, [keyPrompt.fileId]: key }))
      const target = files.find((f) => f.id === keyPrompt.fileId)
      const url = window.URL.createObjectURL(new Blob([plain], { type: target?.mimeType || blob.type }))
      const a = document.createElement('a')
      a.href = url
      a.download = target?.name || keyPrompt.fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      setKeyPrompt(null)
      setKeyPromptInput('')
      setKeyPromptError('')
    } catch (err) {
      setKeyPromptError((err as Error).message || '文件密钥不正确')
    } finally {
      setKeyPromptLoading(false)
    }
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

  const getAiStatus = (file: FileItem) => (file.aiStatus || 'pending').toLowerCase()
  const getSensitiveLevel = (file: FileItem) => (file.sensitiveLevel || 'normal').toLowerCase()

  const completedCount = files.filter((f) => getAiStatus(f) === 'completed').length
  const safeCount = files.filter((f) => {
    const level = getSensitiveLevel(f)
    return level === 'normal' || level === 'low'
  }).length
  const highSensitiveCount = files.filter((f) => getSensitiveLevel(f) === 'high').length

  const recentFiles = [...files]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5)

  const importantFiles = files.filter((f) => getSensitiveLevel(f) === 'high' || f.category === '财务报告' || f.category === '合同').slice(0, 5)

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
      value: `${safeCount}`,
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
                  onDownload={() => handleDownloadFile(file)}
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

        </div>
      </div>

      <Modal isOpen={showUploadModal} onClose={() => setShowUploadModal(false)} title="上传文件">
        <div className="flex items-center justify-between p-3 mb-4 bg-neutral-50 border border-neutral-200 rounded-card">
          <div className="flex items-center gap-2">
            {e2eeUpload ? <Lock className="h-4 w-4 text-green-500" /> : <LockOpen className="h-4 w-4 text-text-secondary" />}
            <div>
              <p className="text-sm font-medium text-text-primary">端到端加密上传</p>
              <p className="text-xs text-text-secondary">文件在浏览器本地加密，每个文件生成专属密钥，仅显示一次，请妥善保存</p>
            </div>
          </div>
          <button
            onClick={() => setE2eeUpload((prev) => !prev)}
            className={`w-12 h-6 rounded-full transition-colors ${e2eeUpload ? 'bg-green-500' : 'bg-neutral-200'}`}
          >
            <span className={`block w-5 h-5 rounded-full bg-white shadow-sm transform transition-transform ${e2eeUpload ? 'translate-x-6' : 'translate-x-0.5'}`} />
          </button>
        </div>
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

      <Modal isOpen={showOneTimeKeyModal} onClose={() => { setShowOneTimeKeyModal(false); setOneTimeKeys([]) }} title="文件密钥（仅显示一次）">
        <div className="mb-4 px-3 py-2 bg-red-50 border border-red-200 text-sm text-red-600">
          请立即复制并妥善保存以下密钥，关闭后将无法再次查看。服务器不会保存密钥，丢失后文件将无法解密。
        </div>
        <div className="space-y-4 max-h-80 overflow-y-auto">
          {oneTimeKeys.map((item, index) => (
            <div key={index} className="border border-neutral-200 rounded-lg p-3">
              <p className="text-sm font-medium text-text-primary mb-2 truncate">{item.fileName}</p>
              <div className="flex items-start gap-2">
                <code className="flex-1 min-w-0 px-3 py-2 bg-neutral-50 border border-neutral-200 text-xs font-mono break-all">{item.key}</code>
                <button
                  onClick={() => handleCopyOneTimeKey(index)}
                  className="px-3 py-2 text-sm bg-accent-blue text-white hover:bg-accent-blue/90 transition-colors flex items-center gap-1 flex-shrink-0"
                >
                  <Copy className="h-4 w-4" />
                  {copiedKeyIndex === index ? '已复制' : '复制'}
                </button>
              </div>
            </div>
          ))}
        </div>
        <div className="flex justify-end gap-3 mt-4">
          <Button variant="outline" onClick={handleDownloadOneTimeKeys}>
            <Download className="h-4 w-4 mr-2" />
            下载密钥文件
          </Button>
          <Button variant="primary" onClick={() => { setShowOneTimeKeyModal(false); setOneTimeKeys([]) }}>
            我已保存密钥
          </Button>
        </div>
      </Modal>

      <Modal isOpen={!!keyPrompt} onClose={() => { setKeyPrompt(null); setKeyPromptInput(''); setKeyPromptError('') }} title={keyPrompt ? `输入文件密钥 - ${keyPrompt.fileName}` : '输入文件密钥'}>
        <div className="space-y-4">
          <p className="text-sm text-text-secondary flex items-center gap-2">
            <KeyRound className="h-4 w-4 text-accent-blue" />
            该文件使用专属密钥加密，请输入加密上传时显示的密钥进行解密。
          </p>
          {keyPromptError && (
            <div className="px-3 py-2 bg-red-50 border border-red-200 text-sm text-red-600">{keyPromptError}</div>
          )}
          <input
            type="text"
            value={keyPromptInput}
            onChange={(e) => setKeyPromptInput(e.target.value)}
            className="w-full px-3 py-2 text-sm font-mono border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
            placeholder="粘贴文件密钥"
            autoFocus
          />
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => { setKeyPrompt(null); setKeyPromptInput(''); setKeyPromptError('') }}>
              取消
            </Button>
            <Button variant="primary" onClick={handleConfirmKeyPrompt} loading={keyPromptLoading}>
              解密并下载
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
