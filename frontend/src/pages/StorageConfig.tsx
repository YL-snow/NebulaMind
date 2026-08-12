import { useEffect, useRef, useState } from 'react'
import {
  Plus,
  Trash2,
  RefreshCw,
  Server,
  CheckCircle,
  XCircle,
  Loader2,
  Folder,
  File,
  Download,
  Upload,
  ArrowLeft,
  HardDrive,
  Pencil,
  Search,
} from 'lucide-react'
import { Card } from '@/components/common/Card'
import { Button } from '@/components/common/Button'
import { Modal } from '@/components/common/Modal'
import { storageApi, type CloudStorageConfig, type CloudStorageItem, type StorageProviderType } from '@/api/storage'
import { useAuthStore } from '@/stores/authStore'
import { formatFileSize } from '@/utils/format'

const providerOptions: {
  value: StorageProviderType
  label: string
  icon: typeof Server
  note?: string
}[] = [
  { value: 'S3', label: 'S3 兼容存储', icon: Server },
  { value: 'WEBDAV', label: 'WebDAV 云盘', icon: HardDrive },
]

interface FormState {
  name: string
  providerType: StorageProviderType
  endpointUrl: string
  accessKey: string
  secretKey: string
  bucketName: string
  region: string
}

const emptyForm: FormState = {
  name: '',
  providerType: 'S3',
  endpointUrl: '',
  accessKey: '',
  secretKey: '',
  bucketName: '',
  region: '',
}

export const StorageConfig = () => {
  const { isAuthenticated } = useAuthStore()
  const [configs, setConfigs] = useState<CloudStorageConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [testingId, setTestingId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>(emptyForm)
  const [error, setError] = useState('')
  const [successMsg, setSuccessMsg] = useState('')

  const [browseConfigId, setBrowseConfigId] = useState<string | null>(null)
  const [browsePath, setBrowsePath] = useState('')
  const [items, setItems] = useState<CloudStorageItem[]>([])
  const [filesLoading, setFilesLoading] = useState(false)
  const [busyPath, setBusyPath] = useState<string | null>(null)
  const [showUploadModal, setShowUploadModal] = useState(false)
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    loadConfigs()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const showSuccess = (msg: string) => {
    setSuccessMsg(msg)
    setTimeout(() => setSuccessMsg(''), 5000)
  }

  const loadConfigs = async () => {
    setLoading(true)
    try {
      const res = await storageApi.list()
      setConfigs(res)
    } catch {
      setError(isAuthenticated ? '加载配置失败' : '请登录查看')
    } finally {
      setLoading(false)
    }
  }

  const resetForm = () => {
    setForm(emptyForm)
    setEditingId(null)
    setShowForm(false)
    setError('')
  }

  const handleEdit = (config: CloudStorageConfig) => {
    setForm({
      name: config.name,
      providerType: config.providerType,
      endpointUrl: config.endpointUrl || '',
      accessKey: config.accessKey || '',
      secretKey: '',
      bucketName: config.bucketName || '',
      region: config.region || '',
    })
    setEditingId(config.id)
    setShowForm(true)
  }

  const handleSubmit = async () => {
    if (!form.name.trim()) {
      setError('请输入配置名称')
      return
    }
    if (!form.endpointUrl.trim()) {
      setError('请输入 Endpoint URL')
      return
    }

    setSaving(true)
    setError('')
    try {
      const payload = {
        ...form,
        secretKey: form.secretKey || undefined,
      }
      if (editingId) {
        await storageApi.update(editingId, payload)
      } else {
        await storageApi.create(payload)
      }
      resetForm()
      await loadConfigs()
      showSuccess(editingId ? '配置已更新' : '配置已创建')
    } catch {
      setError('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: string) => {
    if (!confirm('确定删除该云存储配置吗？')) return
    try {
      await storageApi.delete(id)
      await loadConfigs()
      showSuccess('配置已删除')
    } catch {
      setError('删除失败')
    }
  }

  const handleTest = async (id: string) => {
    setTestingId(id)
    try {
      const res = await storageApi.testConnection(id)
      showSuccess(res.message)
      await loadConfigs()
    } catch {
      setError('测试连接失败')
    } finally {
      setTestingId(null)
    }
  }

  const handleToggleActive = async (config: CloudStorageConfig) => {
    try {
      await storageApi.update(config.id, { isActive: !config.isActive })
      await loadConfigs()
    } catch {
      setError('切换状态失败')
    }
  }

  const loadFiles = async (configId: string, path: string) => {
    setFilesLoading(true)
    setError('')
    try {
      const res = await storageApi.listFiles(configId, path)
      setItems(res)
    } catch (err) {
      setError((err as Error).message || '加载文件失败')
      setItems([])
    } finally {
      setFilesLoading(false)
    }
  }

  const openBrowser = async (config: CloudStorageConfig) => {
    setBrowseConfigId(config.id)
    setBrowsePath('')
    setItems([])
    await loadFiles(config.id, '')
  }

  const closeBrowser = () => {
    setBrowseConfigId(null)
    setBrowsePath('')
    setItems([])
  }

  const navigateFolder = async (item: CloudStorageItem) => {
    if (!browseConfigId) return
    setBrowsePath(item.path)
    await loadFiles(browseConfigId, item.path)
  }

  const breadcrumbs = () => {
    const parts = browsePath.split('/').filter((p) => p.length > 0)
    const crumbs: { label: string; path: string }[] = []
    let current = ''
    parts.forEach((part) => {
      current = current ? `${current}/${part}` : part
      crumbs.push({ label: part, path: current.endsWith('/') ? current : current + '/' })
    })
    return crumbs
  }

  const handleDownload = async (item: CloudStorageItem) => {
    if (!browseConfigId) return
    setBusyPath(item.path)
    try {
      const blob = await storageApi.download(browseConfigId, item.path) as unknown as Blob
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = item.name
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      showSuccess('下载开始')
    } catch (err) {
      setError((err as Error).message || '下载失败')
    } finally {
      setBusyPath(null)
    }
  }

  const handleImport = async (item: CloudStorageItem) => {
    if (!browseConfigId) return
    setBusyPath(item.path)
    try {
      const res = await storageApi.importFile(browseConfigId, item.path, item.name)
      showSuccess(res.message)
    } catch (err) {
      setError((err as Error).message || '导入失败')
    } finally {
      setBusyPath(null)
    }
  }

  const handleRemove = async (item: CloudStorageItem) => {
    if (!browseConfigId) return
    if (!confirm(`确定删除云盘文件 ${item.name} 吗？`)) return
    setBusyPath(item.path)
    try {
      const res = await storageApi.removeFile(browseConfigId, item.path)
      showSuccess(res.message)
      await loadFiles(browseConfigId, browsePath)
    } catch (err) {
      setError((err as Error).message || '删除失败')
    } finally {
      setBusyPath(null)
    }
  }

  const openUploadModal = () => {
    setUploadFile(null)
    setUploadProgress(0)
    setShowUploadModal(true)
    setTimeout(() => fileInputRef.current?.click(), 50)
  }

  const submitUpload = async () => {
    if (!browseConfigId || !uploadFile) return
    setUploading(true)
    setUploadProgress(0)
    try {
      await storageApi.upload(browseConfigId, browsePath, uploadFile, setUploadProgress)
      setShowUploadModal(false)
      setUploadFile(null)
      showSuccess('上传成功')
      await loadFiles(browseConfigId, browsePath)
    } catch (err) {
      setError((err as Error).message || '上传失败')
    } finally {
      setUploading(false)
    }
  }

  const browseConfig = configs.find((c) => c.id === browseConfigId) || null

  return (
    <div className="max-w-5xl mx-auto py-6 px-4 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-text-primary">云存储对接</h2>
          <p className="text-sm text-text-secondary mt-1">
            连接 S3 兼容存储或 WebDAV 云盘，浏览远端文件并导入到 NebulaMind
          </p>
        </div>
        <div className="flex items-center gap-3">
          {successMsg && (
            <span className="text-sm text-green-600 bg-green-50 px-3 py-1">
              {successMsg}
            </span>
          )}
          <Button variant="outline" size="sm" onClick={loadConfigs}>
            <RefreshCw className="h-4 w-4 mr-1.5" />
            刷新
          </Button>
          <Button
            size="sm"
            onClick={() => {
              resetForm()
              setShowForm(true)
            }}
          >
            <Plus className="h-4 w-4 mr-1.5" />
            新增配置
          </Button>
        </div>
      </div>

      {error && (
        <div className="px-4 py-3 bg-red-50 border border-red-200 text-sm text-red-600 flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError('')} className="text-red-400 hover:text-red-600">
            <XCircle className="h-4 w-4" />
          </button>
        </div>
      )}

      {showForm && (
        <Card>
          <div className="p-6 space-y-4">
            <h3 className="font-semibold text-text-primary">
              {editingId ? '编辑配置' : '新增云存储配置'}
            </h3>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-text-primary">配置名称 *</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="例如：我的坚果云"
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-text-primary">存储类型 *</label>
                <select
                  value={form.providerType}
                  onChange={(e) => setForm({ ...form, providerType: e.target.value as StorageProviderType })}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                >
                  {providerOptions.map((opt) => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>
              <div className="col-span-2 space-y-1.5">
                <label className="text-sm font-medium text-text-primary">
                  {form.providerType === 'S3' ? 'Endpoint URL *' : 'WebDAV 地址 *'}
                </label>
                <input
                  type="text"
                  value={form.endpointUrl}
                  onChange={(e) => setForm({ ...form, endpointUrl: e.target.value })}
                  placeholder={
                    form.providerType === 'S3'
                      ? 'http://localhost:9000'
                      : 'https://dav.jianguoyun.com/dav'
                  }
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-text-primary">
                  {form.providerType === 'S3' ? '访问密钥' : '账号'}
                </label>
                <input
                  type="text"
                  value={form.accessKey}
                  onChange={(e) => setForm({ ...form, accessKey: e.target.value })}
                  placeholder={form.providerType === 'S3' ? 'nebulamind' : 'user@nebulamind.com'}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-text-primary">
                  {form.providerType === 'S3' ? '秘密密钥' : '密码'}
                </label>
                <input
                  type="password"
                  value={form.secretKey}
                  onChange={(e) => setForm({ ...form, secretKey: e.target.value })}
                  placeholder={editingId ? '留空则保持原密钥' : form.providerType === 'S3' ? 'your-minio-password' : 'user123'}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                />
              </div>
              {form.providerType === 'S3' && (
                <>
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium text-text-primary">存储桶名称</label>
                    <input
                      type="text"
                      value={form.bucketName}
                      onChange={(e) => setForm({ ...form, bucketName: e.target.value })}
                      placeholder="nebulamind-files"
                      className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium text-text-primary">区域</label>
                    <input
                      type="text"
                      value={form.region}
                      onChange={(e) => setForm({ ...form, region: e.target.value })}
                      placeholder="us-east-1"
                      className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                    />
                  </div>
                </>
              )}
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <Button variant="ghost" onClick={resetForm}>
                取消
              </Button>
              <Button onClick={handleSubmit} loading={saving}>
                {editingId ? '保存修改' : '创建配置'}
              </Button>
            </div>
          </div>
        </Card>
      )}

      <div className="space-y-3">
        {loading ? (
          <div className="flex items-center justify-center py-12 text-text-secondary">
            <Loader2 className="h-6 w-6 animate-spin mr-2" />
            加载中...
          </div>
        ) : configs.length === 0 ? (
          <div className="text-center py-12 text-text-secondary">
            <Server className="h-12 w-12 mx-auto mb-3 text-neutral-300" />
            <p>暂无云存储配置</p>
            <p className="text-sm mt-1">点击上方"新增配置"添加</p>
          </div>
        ) : (
          configs.map((config) => {
            const ProviderIcon = providerOptions.find(
              (o) => o.value === config.providerType,
            )?.icon || Server
            const providerLabel = providerOptions.find((o) => o.value === config.providerType)?.label || config.providerType
            return (
              <Card key={config.id}>
                <div className="p-5">
                  <div className="flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-10 h-10 bg-primary-50 flex items-center justify-center shrink-0">
                        <ProviderIcon className="h-5 w-5 text-primary-500" />
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h4 className="font-medium text-text-primary truncate">{config.name}</h4>
                          <span className="text-xs px-2 py-0.5 bg-neutral-100 text-text-secondary whitespace-nowrap">
                            {providerLabel}
                          </span>
                          {config.isActive && (
                            <span className="text-xs px-2 py-0.5 bg-green-50 text-green-600 whitespace-nowrap">
                              已启用
                            </span>
                          )}
                          {config.lastTestSuccess === true && (
                            <span className="flex items-center gap-1 text-xs text-green-600 whitespace-nowrap">
                              <CheckCircle className="h-3.5 w-3.5" />
                              连接正常
                            </span>
                          )}
                          {config.lastTestSuccess === false && (
                            <span className="flex items-center gap-1 text-xs text-red-500 whitespace-nowrap">
                              <XCircle className="h-3.5 w-3.5" />
                              连接失败
                            </span>
                          )}
                        </div>
                        <p className="text-sm text-text-secondary mt-0.5 truncate">
                          {config.endpointUrl}
                          {config.bucketName && ` · ${config.bucketName}`}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleTest(config.id)}
                        disabled={testingId === config.id}
                      >
                        {testingId === config.id ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <RefreshCw className="h-4 w-4" />
                        )}
                        测试
                      </Button>
                        <Button
                          size="sm"
                          onClick={() => openBrowser(config)}
                          disabled={browseConfigId === config.id}
                        >
                          <Folder className="h-4 w-4 mr-1.5" />
                          浏览文件
                        </Button>
                      <Button
                        variant={config.isActive ? 'amber' : 'ghost'}
                        size="sm"
                        onClick={() => handleToggleActive(config)}
                      >
                        {config.isActive ? '禁用' : '启用'}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => handleEdit(config)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="danger" size="sm" onClick={() => handleDelete(config.id)}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>

                  {config.bucketName && (
                    <div className="mt-3 pt-3 border-t border-neutral-100 flex gap-6 text-sm text-text-secondary">
                      <span>Bucket: {config.bucketName}</span>
                      {config.region && <span>Region: {config.region}</span>}
                      {config.lastTestAt && (
                        <span>上次测试: {new Date(config.lastTestAt).toLocaleString('zh-CN')}</span>
                      )}
                    </div>
                  )}

                </div>

                {browseConfigId === config.id && browseConfig && (
                  <div className="border-t border-neutral-200">
                    <div className="p-5">
                      <div className="flex items-center justify-between mb-4">
                        <div className="flex items-center gap-2 min-w-0">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={closeBrowser}
                            className="shrink-0"
                          >
                            <ArrowLeft className="h-4 w-4" />
                          </Button>
                          <div className="flex items-center gap-1 text-sm min-w-0 overflow-x-auto">
                            <button
                              onClick={() => loadFiles(config.id, '')}
                              className="text-accent-blue hover:underline whitespace-nowrap"
                            >
                              根目录
                            </button>
                            {breadcrumbs().map((crumb, index) => (
                              <span key={crumb.path} className="flex items-center gap-1 whitespace-nowrap">
                                <span className="text-neutral-300">/</span>
                                <button
                                  onClick={() => loadFiles(config.id, crumb.path)}
                                  className="text-accent-blue hover:underline"
                                >
                                  {crumb.label}
                                </button>
                                {index === breadcrumbs().length - 1 && (
                                  <span className="text-text-secondary">/</span>
                                )}
                              </span>
                            ))}
                          </div>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => loadFiles(config.id, browsePath)}
                            disabled={filesLoading}
                          >
                            <RefreshCw className={`h-4 w-4 mr-1.5 ${filesLoading ? 'animate-spin' : ''}`} />
                            刷新
                          </Button>
                          <Button size="sm" onClick={openUploadModal} disabled={filesLoading}>
                            <Upload className="h-4 w-4 mr-1.5" />
                            上传
                          </Button>
                        </div>
                      </div>

                      {filesLoading ? (
                        <div className="flex items-center justify-center py-12 text-text-secondary">
                          <Loader2 className="h-6 w-6 animate-spin mr-2" />
                          加载文件...
                        </div>
                      ) : items.length === 0 ? (
                        <div className="text-center py-12 text-text-secondary">
                          <Search className="h-10 w-10 mx-auto mb-2 text-neutral-300" />
                          <p>当前目录没有文件</p>
                        </div>
                      ) : (
                        <div className="divide-y divide-neutral-100 border border-neutral-200 rounded-button">
                          {items.map((item) => (
                            <div
                              key={item.path}
                              className="flex items-center justify-between gap-4 px-4 py-3 hover:bg-neutral-50"
                            >
                              <div className="flex items-center gap-3 min-w-0">
                                {item.folder ? (
                                  <Folder className="h-5 w-5 text-accent-amber shrink-0" />
                                ) : (
                                  <File className="h-5 w-5 text-accent-blue shrink-0" />
                                )}
                                <div className="min-w-0">
                                  <button
                                    onClick={() => item.folder && navigateFolder(item)}
                                    className={`text-sm font-medium truncate block max-w-[360px] ${
                                      item.folder ? 'text-accent-blue hover:underline' : 'text-text-primary'
                                    }`}
                                    title={item.name}
                                  >
                                    {item.name}
                                  </button>
                                  <p className="text-xs text-text-secondary mt-0.5">
                                    {item.folder
                                      ? '文件夹'
                                      : `${formatFileSize(item.size || 0)}${item.mimeType ? ` · ${item.mimeType}` : ''}`}
                                  </p>
                                </div>
                              </div>
                              <div className="flex items-center gap-2 shrink-0">
                                {!item.folder && (
                                  <>
                                    <Button
                                      variant="outline"
                                      size="sm"
                                      onClick={() => handleDownload(item)}
                                      disabled={busyPath === item.path}
                                    >
                                      <Download className="h-4 w-4 mr-1" />
                                      下载
                                    </Button>
                                    <Button
                                      size="sm"
                                      onClick={() => handleImport(item)}
                                      disabled={busyPath === item.path}
                                    >
                                      <Plus className="h-4 w-4 mr-1" />
                                      导入
                                    </Button>
                                  </>
                                )}
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => handleRemove(item)}
                                  disabled={busyPath === item.path}
                                >
                                  <Trash2 className="h-4 w-4 text-red-500" />
                                </Button>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </Card>
            )
          })
        )}
      </div>

      <Modal
        isOpen={showUploadModal}
        onClose={() => setShowUploadModal(false)}
        title="上传到当前目录"
      >
        <div className="space-y-4">
          <p className="text-sm text-text-secondary">
            当前目录：{browsePath || '根目录'}
          </p>
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
          />
          <Button
            variant="outline"
            className="w-full"
            onClick={() => fileInputRef.current?.click()}
          >
            {uploadFile ? uploadFile.name : '选择文件'}
          </Button>
          {uploading && (
            <div>
              <div className="h-2 bg-neutral-100 rounded-full overflow-hidden">
                <div
                  className="h-full bg-accent-blue transition-all duration-200"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
              <p className="text-xs text-text-secondary mt-1 text-right">{uploadProgress}%</p>
            </div>
          )}
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setShowUploadModal(false)}>
              取消
            </Button>
            <Button onClick={submitUpload} disabled={!uploadFile || uploading} loading={uploading}>
              上传
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
