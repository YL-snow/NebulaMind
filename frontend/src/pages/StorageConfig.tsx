import { useEffect, useState } from 'react'
import {
  Plus,
  Trash2,
  RefreshCw,
  Server,
  Cloud,
  CheckCircle,
  XCircle,
  Loader2,
} from 'lucide-react'
import { Card } from '@/components/common/Card'
import { storageApi, type CloudStorageConfig } from '@/api/storage'
import { useAuthStore } from '@/stores/authStore'

const providerOptions = [
  { value: 'S3', label: 'S3 兼容存储', icon: Server },
  { value: 'UNICOM', label: '联通云盘', icon: Cloud },
] as const

export const StorageConfig = () => {
  const { isAuthenticated } = useAuthStore()
  const [configs, setConfigs] = useState<CloudStorageConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [testingId, setTestingId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)

  // Form state
  const [form, setForm] = useState({
    name: '',
    providerType: 'S3' as 'S3' | 'UNICOM',
    endpointUrl: '',
    accessKey: '',
    secretKey: '',
    bucketName: '',
    region: '',
    redirectUri: '',
  })

  const [error, setError] = useState('')
  const [successMsg, setSuccessMsg] = useState('')

  useEffect(() => {
    loadConfigs()
  }, [])

  const loadConfigs = async () => {
    setLoading(true)
    try {
      const res = await storageApi.list()
      setConfigs(res)
    } catch {
      setError(isAuthenticated ? '加载配置失败' : '登录查看')
    } finally {
      setLoading(false)
    }
  }

  const resetForm = () => {
    setForm({
      name: '',
      providerType: 'S3',
      endpointUrl: '',
      accessKey: '',
      secretKey: '',
      bucketName: '',
      region: '',
      redirectUri: '',
    })
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
      redirectUri: config.redirectUri || '',
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
      if (editingId) {
        await storageApi.update(editingId, form)
      } else {
        await storageApi.create(form)
      }
      resetForm()
      await loadConfigs()
      setSuccessMsg(editingId ? '配置已更新' : '配置已创建')
      setTimeout(() => setSuccessMsg(''), 3000)
    } catch {
      setError('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await storageApi.delete(id)
      await loadConfigs()
      setSuccessMsg('配置已删除')
      setTimeout(() => setSuccessMsg(''), 3000)
    } catch {
      setError('删除失败')
    }
  }

  const handleTest = async (id: string) => {
    setTestingId(id)
    try {
      const res = await storageApi.testConnection(id)
      setSuccessMsg(res.message)
      setTimeout(() => setSuccessMsg(''), 5000)
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

  return (
    <div className="max-w-4xl mx-auto py-6 px-4 space-y-6">
      {/* 顶部标题 + 添加按钮 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-text-primary">云存储配置</h2>
          <p className="text-sm text-text-secondary mt-1">
            配置 S3 兼容存储或联通云盘等外部存储服务
          </p>
        </div>
        <div className="flex items-center gap-3">
          {successMsg && (
            <span className="text-sm text-green-600 bg-green-50 px-3 py-1">
              {successMsg}
            </span>
          )}
          <button
            onClick={loadConfigs}
            className="flex items-center gap-2 px-3 py-2 text-sm text-text-secondary hover:bg-neutral-100 transition-colors"
          >
            <RefreshCw className="h-4 w-4" />
            刷新
          </button>
          <button
            onClick={() => {
              resetForm()
              setShowForm(true)
            }}
            className="flex items-center gap-2 px-4 py-2 text-sm bg-accent-blue text-white hover:bg-accent-blue/90 transition-colors"
          >
            <Plus className="h-4 w-4" />
            新增配置
          </button>
        </div>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className="px-4 py-3 bg-red-50 border border-red-200 text-sm text-red-600 flex items-center justify-between">
          {error === '登录查看' ? (
            <a href="/login" className="text-accent-blue hover:underline">登录查看</a>
          ) : (
            <span>{error}</span>
          )}
          <button onClick={() => setError('')} className="text-red-400 hover:text-red-600">
            <XCircle className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* 新增/编辑表单 */}
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
                  placeholder="例如：我的 MinIO 存储"
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-text-primary">存储类型 *</label>
                <select
                  value={form.providerType}
                  onChange={(e) => setForm({ ...form, providerType: e.target.value as 'S3' | 'UNICOM' })}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                >
                  {providerOptions.map((opt) => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>
              <div className="col-span-2 space-y-1.5">
                <label className="text-sm font-medium text-text-primary">接口地址 *</label>
                <input
                  type="text"
                  value={form.endpointUrl}
                  onChange={(e) => setForm({ ...form, endpointUrl: e.target.value })}
                  placeholder={form.providerType === 'S3' ? 'http://localhost:9000' : 'https://maas-api.ai-yuanjing.com'}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-text-primary">
                  {form.providerType === 'S3' ? '访问密钥' : '应用ID'}
                </label>
                <input
                  type="text"
                  value={form.accessKey}
                  onChange={(e) => setForm({ ...form, accessKey: e.target.value })}
                  placeholder={form.providerType === 'S3' ? 'minioadmin' : 'app-xxx'}
                  className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-text-primary">
                  {form.providerType === 'S3' ? '秘密密钥' : '应用密钥'}
                </label>
                <input
                  type="password"
                  value={form.secretKey}
                  onChange={(e) => setForm({ ...form, secretKey: e.target.value })}
                  placeholder={form.providerType === 'S3' ? 'minioadmin' : 'secret-xxx'}
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
              {form.providerType === 'UNICOM' && (
                <div className="col-span-2 space-y-1.5">
                  <label className="text-sm font-medium text-text-primary">OAuth2 重定向 URI</label>
                  <input
                    type="text"
                    value={form.redirectUri}
                    onChange={(e) => setForm({ ...form, redirectUri: e.target.value })}
                    placeholder="http://localhost:8080/api/v1/cloud-drive/callback"
                    className="w-full px-3 py-2 text-sm border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
                  />
                </div>
              )}
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <button
                onClick={resetForm}
                className="px-4 py-2 text-sm text-text-secondary hover:bg-neutral-100 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleSubmit}
                disabled={saving}
                className="px-4 py-2 text-sm bg-accent-blue text-white hover:bg-accent-blue/90 disabled:opacity-50 transition-colors flex items-center gap-2"
              >
                {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                {editingId ? '保存修改' : '创建配置'}
              </button>
            </div>
          </div>
        </Card>
      )}

      {/* 配置列表 */}
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
              (o) => o.value === config.providerType
            )?.icon || Server
            return (
              <Card key={config.id}>
                <div className="p-5">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-primary-50 flex items-center justify-center">
                        <ProviderIcon className="h-5 w-5 text-primary-500" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h4 className="font-medium text-text-primary">{config.name}</h4>
                          <span className="text-xs px-2 py-0.5 bg-neutral-100 text-text-secondary">
                            {providerOptions.find((o) => o.value === config.providerType)?.label}
                          </span>
                          {config.isActive && (
                            <span className="text-xs px-2 py-0.5 bg-green-50 text-green-600">
                              已启用
                            </span>
                          )}
                        </div>
                        <p className="text-sm text-text-secondary mt-0.5">
                          {config.endpointUrl}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      {/* 连接状态 */}
                      {config.lastTestSuccess === true && (
                        <span className="flex items-center gap-1 text-xs text-green-600">
                          <CheckCircle className="h-3.5 w-3.5" />
                          连接正常
                        </span>
                      )}
                      {config.lastTestSuccess === false && (
                        <span className="flex items-center gap-1 text-xs text-red-500">
                          <XCircle className="h-3.5 w-3.5" />
                          连接失败
                        </span>
                      )}

                      {/* 测试连接 */}
                      <button
                        onClick={() => handleTest(config.id)}
                        disabled={testingId === config.id}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-text-secondary hover:bg-neutral-100 transition-colors disabled:opacity-50"
                      >
                        {testingId === config.id ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <RefreshCw className="h-4 w-4" />
                        )}
                        测试
                      </button>

                      {/* 启用/禁用 */}
                      <button
                        onClick={() => handleToggleActive(config)}
                        className={`px-3 py-1.5 text-sm transition-colors ${
                          config.isActive
                            ? 'text-orange-600 hover:bg-orange-50'
                            : 'text-green-600 hover:bg-green-50'
                        }`}
                      >
                        {config.isActive ? '禁用' : '启用'}
                      </button>

                      {/* 编辑 */}
                      <button
                        onClick={() => handleEdit(config)}
                        className="px-3 py-1.5 text-sm text-text-secondary hover:bg-neutral-100 transition-colors"
                      >
                        编辑
                      </button>

                      {/* 删除 */}
                      <button
                        onClick={() => handleDelete(config.id)}
                        className="flex items-center gap-1 px-3 py-1.5 text-sm text-red-500 hover:bg-red-50 transition-colors"
                      >
                        <Trash2 className="h-4 w-4" />
                        删除
                      </button>
                    </div>
                  </div>

                  {/* 配置详情 */}
                  {config.bucketName && (
                    <div className="mt-3 pt-3 border-t border-neutral-100 flex gap-6 text-sm text-text-secondary">
                      {config.bucketName && <span>Bucket: {config.bucketName}</span>}
                      {config.region && <span>Region: {config.region}</span>}
                      {config.lastTestAt && (
                        <span>上次测试: {new Date(config.lastTestAt).toLocaleString('zh-CN')}</span>
                      )}
                    </div>
                  )}
                </div>
              </Card>
            )
          })
        )}
      </div>
    </div>
  )
}
