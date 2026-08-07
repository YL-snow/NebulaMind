import { useState, useEffect } from 'react'
import { Shield, Lock, AlertTriangle, CheckCircle, FileText, Search, Settings, User, Key, Eye } from 'lucide-react'
import { Button } from '@/components/common/Button'
import { Card, CardHeader, CardBody } from '@/components/common/Card'
import { Input } from '@/components/common/Input'
import { Loading } from '@/components/common/Loading'
import { useToast } from '@/components/common/Toast'
import { securityApi } from '@/api/security'
import { filesApi } from '@/api/files'
import { useFileStore } from '@/stores/fileStore'
import { SENSITIVE_LEVELS } from '@/utils/constants'
import { formatFileSize, formatDate } from '@/utils/format'
import type { FileItem, SensitiveItem } from '@/api/types'

export const Security = () => {
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedFile, setSelectedFile] = useState<FileItem | null>(null)
  const [sensitiveItems, setSensitiveItems] = useState<SensitiveItem[]>([])
  const [loading, setLoading] = useState(false)
  const [filesLoading, setFilesLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'files' | 'settings'>('files')
  interface SecuritySettings {
    autoDetect: boolean
    twoFactorAuth: boolean
    realtimeAlert: boolean
    encryptHighSensitive: boolean
    maxFileSize: number
    allowedFileTypes: string[]
  }

  const [securitySettings, setSecuritySettings] = useState<SecuritySettings>(() => {
    const saved = localStorage.getItem('securitySettings')
    if (saved) {
      try {
        return JSON.parse(saved) as SecuritySettings
      } catch {
        // ignore parse error
      }
    }
    return {
      autoDetect: true,
      twoFactorAuth: false,
      realtimeAlert: true,
      encryptHighSensitive: true,
      maxFileSize: 100,
      allowedFileTypes: ['pdf', 'docx', 'xlsx', 'pptx', 'txt', 'md', 'jpg', 'png'],
    }
  })

  const { files, setFiles } = useFileStore()
  const { error, success } = useToast()

  // 加载文件列表（始终重新获取以确保数据最新）
  useEffect(() => {
    setFilesLoading(true)
    filesApi.list({ page: 0, pageSize: 500 })
      .then((response) => {
        console.log('[Security] API response:', response)
        console.log('[Security] response.content:', response?.content)
        console.log('[Security] files count:', response?.content?.length)
        const fileList = response?.content || []
        setFiles(fileList)
        if (fileList.length === 0) {
          console.warn('[Security] 后端返回了0个文件，请检查是否有已上传的文件')
        }
      })
      .catch((err) => {
        console.error('[Security] 加载文件列表失败:', err)
        error('加载文件列表失败: ' + (err as Error).message)
        setFiles([])
      })
      .finally(() => {
        setFilesLoading(false)
      })
  }, [])

  const handleDetect = async (file: FileItem) => {
    setLoading(true)
    setSelectedFile(file)
    try {
      const response = await securityApi.detect({ fileId: file.id })
      setSensitiveItems(response.sensitiveItems)
      setFiles(files.map((f) => (f.id === file.id ? { ...f, sensitiveLevel: response.sensitiveLevel } : f)))
      success('安全检测完成')
    } catch (err) {
      error((err as Error).message || '检测失败')
    } finally {
      setLoading(false)
    }
  }

  const handleEncrypt = async (file: FileItem) => {
    if (!confirm(`确定要加密文件 ${file.name} 吗？`)) return
    setLoading(true)
    try {
      await securityApi.encrypt({ fileId: file.id })
      setFiles(files.map((f) => (f.id === file.id ? { ...f, isEncrypted: true } : f)))
      success('文件加密成功')
    } catch (err) {
      error((err as Error).message || '加密失败')
    } finally {
      setLoading(false)
    }
  }

  const handleSaveSettings = () => {
    localStorage.setItem('securitySettings', JSON.stringify(securitySettings))
    success('安全设置已保存')
  }

  // 标准化 sensitiveLevel：兼容大写(HIGH/MEDIUM/NORMAL)、小写、null/undefined（默认 normal）
  const getLevel = (f: FileItem) => (f.sensitiveLevel || 'normal').toLowerCase()

  const filteredFiles = (files || []).filter((file) =>
    file.name.toLowerCase().includes(searchQuery.toLowerCase())
  )

  const highSensitiveFiles = filteredFiles.filter((f) => getLevel(f) === 'high')
  const mediumSensitiveFiles = filteredFiles.filter((f) => getLevel(f) === 'medium')
  const normalFiles = filteredFiles.filter((f) => getLevel(f) === 'normal' || getLevel(f) === 'low')

  console.log('[Security] files total:', files?.length, 'filtered:', filteredFiles.length,
    'high:', highSensitiveFiles.length, 'medium:', mediumSensitiveFiles.length, 'normal:', normalFiles.length)

  if (filesLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loading size="lg" text="正在加载文件列表..." />
      </div>
    )
  }

  const stats = [
    { label: '高敏感文件', value: highSensitiveFiles.length, color: 'text-red-500', bgColor: 'bg-red-50' },
    { label: '中敏感文件', value: mediumSensitiveFiles.length, color: 'text-yellow-500', bgColor: 'bg-yellow-50' },
    { label: '安全文件', value: normalFiles.length, color: 'text-green-500', bgColor: 'bg-green-50' },
    { label: '已加密文件', value: filteredFiles.filter((f) => f.isEncrypted).length, color: 'text-blue-500', bgColor: 'bg-blue-50' },
  ]

  const tabs = [
    { key: 'files', label: '敏感文件', icon: AlertTriangle },
    { key: 'settings', label: '安全设置', icon: Settings },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex-1 max-w-md">
          <Input
            placeholder="搜索文件..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            prefix={<Search className="h-4 w-4" />}
          />
        </div>
        <div className="flex bg-neutral-100 rounded-button p-1">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key as typeof activeTab)}
              className={`flex items-center gap-2 px-4 py-2 rounded-button text-sm font-medium transition-all ${
                activeTab === tab.key
                  ? 'bg-white text-accent-blue shadow-sm'
                  : 'text-text-secondary'
              }`}
            >
              <tab.icon className="h-4 w-4" />
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat) => (
          <Card key={stat.label}>
            <CardBody className="flex flex-col items-center justify-center text-center gap-2 py-6">
              <div className={`w-12 h-12 rounded-button ${stat.bgColor} flex items-center justify-center`}>
                <Shield className={`h-6 w-6 ${stat.color}`} />
              </div>
              <div>
                <p className="text-sm text-text-secondary">{stat.label}</p>
                <p className="text-xl font-bold text-text-primary">{stat.value}</p>
              </div>
            </CardBody>
          </Card>
        ))}
      </div>

      {activeTab === 'files' ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <div className="space-y-4">
              {highSensitiveFiles.length > 0 && (
                <div>
                  <div className="flex items-center gap-2 mb-3">
                    <AlertTriangle className="h-5 w-5 text-red-500" />
                    <h3 className="font-semibold text-red-600">高敏感文件</h3>
                  </div>
                  <div className="grid gap-3">
                    {highSensitiveFiles.map((file) => (
                      <Card key={file.id} hoverable onClick={() => handleDetect(file)}>
                        <CardBody className="flex items-center gap-4">
                          <div className="w-10 h-10 rounded-button bg-red-50 flex items-center justify-center">
                            <AlertTriangle className="h-5 w-5 text-red-500" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <p className="font-medium text-text-primary truncate">{file.name}</p>
                              {file.isEncrypted && (
                                <Lock className="h-4 w-4 text-green-500" />
                              )}
                            </div>
                            <div className="flex items-center gap-3 mt-1">
                              <span className="text-xs text-text-secondary">{formatFileSize(file.size)}</span>
                              <span className="text-xs text-text-secondary">{formatDate(file.createdAt)}</span>
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            <Button variant="outline" size="sm" onClick={(e) => { e.stopPropagation(); handleDetect(file) }}>
                              检测
                            </Button>
                            {!file.isEncrypted && (
                              <Button variant="danger" size="sm" onClick={(e) => { e.stopPropagation(); handleEncrypt(file) }}>
                                <Lock className="h-4 w-4 mr-1" />
                                加密
                              </Button>
                            )}
                          </div>
                        </CardBody>
                      </Card>
                    ))}
                  </div>
                </div>
              )}

              {mediumSensitiveFiles.length > 0 && (
                <div>
                  <div className="flex items-center gap-2 mb-3">
                    <AlertTriangle className="h-5 w-5 text-yellow-500" />
                    <h3 className="font-semibold text-yellow-600">中敏感文件</h3>
                  </div>
                  <div className="grid gap-3">
                    {mediumSensitiveFiles.map((file) => (
                      <Card key={file.id} hoverable onClick={() => handleDetect(file)}>
                        <CardBody className="flex items-center gap-4">
                          <div className="w-10 h-10 rounded-button bg-yellow-50 flex items-center justify-center">
                            <AlertTriangle className="h-5 w-5 text-yellow-500" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <p className="font-medium text-text-primary truncate">{file.name}</p>
                              {file.isEncrypted && (
                                <Lock className="h-4 w-4 text-green-500" />
                              )}
                            </div>
                            <div className="flex items-center gap-3 mt-1">
                              <span className="text-xs text-text-secondary">{formatFileSize(file.size)}</span>
                              <span className="text-xs text-text-secondary">{formatDate(file.createdAt)}</span>
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            <Button variant="outline" size="sm" onClick={(e) => { e.stopPropagation(); handleDetect(file) }}>
                              检测
                            </Button>
                            {!file.isEncrypted && (
                              <Button variant="primary" size="sm" onClick={(e) => { e.stopPropagation(); handleEncrypt(file) }}>
                                <Lock className="h-4 w-4 mr-1" />
                                加密
                              </Button>
                            )}
                          </div>
                        </CardBody>
                      </Card>
                    ))}
                  </div>
                </div>
              )}

              {normalFiles.length > 0 && (
                <div>
                  <div className="flex items-center gap-2 mb-3">
                    <CheckCircle className="h-5 w-5 text-green-500" />
                    <h3 className="font-semibold text-green-600">安全文件</h3>
                  </div>
                  <div className="grid gap-3">
                    {normalFiles.map((file) => (
                      <Card key={file.id} hoverable onClick={() => handleDetect(file)}>
                        <CardBody className="flex items-center gap-4">
                          <div className="w-10 h-10 rounded-button bg-green-50 flex items-center justify-center">
                            <CheckCircle className="h-5 w-5 text-green-500" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <p className="font-medium text-text-primary truncate">{file.name}</p>
                              {file.isEncrypted && (
                                <Lock className="h-4 w-4 text-green-500" />
                              )}
                            </div>
                            <div className="flex items-center gap-3 mt-1">
                              <span className="text-xs text-text-secondary">{formatFileSize(file.size)}</span>
                              <span className="text-xs text-text-secondary">{formatDate(file.createdAt)}</span>
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            <Button variant="outline" size="sm" onClick={(e) => { e.stopPropagation(); handleDetect(file) }}>
                              检测
                            </Button>
                            {!file.isEncrypted && (
                              <Button variant="ghost" size="sm" onClick={(e) => { e.stopPropagation(); handleEncrypt(file) }}>
                                <Lock className="h-4 w-4 mr-1" />
                                加密
                              </Button>
                            )}
                          </div>
                        </CardBody>
                      </Card>
                    ))}
                  </div>
                </div>
              )}

              {filteredFiles.length === 0 && (
                <div className="text-center py-16">
                  <Shield className="h-16 w-16 text-neutral-300 mx-auto mb-4" />
                  <p className="text-text-secondary">暂无文件</p>
                </div>
              )}
            </div>
          </div>

          <div className="space-y-6">
            <Card>
              <CardHeader>
                <h3 className="font-semibold text-text-primary">检测结果</h3>
              </CardHeader>
              <CardBody>
                {loading ? (
                  <Loading text="检测中..." />
                ) : selectedFile ? (
                  <div className="space-y-4">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 bg-neutral-100 rounded-button flex items-center justify-center">
                        <FileText className="h-5 w-5 text-text-secondary" />
                      </div>
                      <div>
                        <p className="font-medium text-text-primary">{selectedFile.name}</p>
                        <span className="text-xs px-2 py-0.5 rounded-full whitespace-nowrap flex-shrink-0" style={{ backgroundColor: SENSITIVE_LEVELS[selectedFile.sensitiveLevel?.toLowerCase() as keyof typeof SENSITIVE_LEVELS]?.bgColor, color: SENSITIVE_LEVELS[selectedFile.sensitiveLevel?.toLowerCase() as keyof typeof SENSITIVE_LEVELS]?.color }}>
                          {SENSITIVE_LEVELS[selectedFile.sensitiveLevel?.toLowerCase() as keyof typeof SENSITIVE_LEVELS]?.label}
                        </span>
                      </div>
                    </div>

                    {sensitiveItems.length > 0 ? (
                      <div className="space-y-3">
                        <p className="text-sm font-medium text-text-secondary">检测到的敏感内容：</p>
                        {sensitiveItems.map((item, idx) => (
                          <div key={idx} className="p-3 bg-neutral-50 rounded-button">
                            <div className="flex items-center justify-between mb-1">
                              <span className="text-sm font-medium text-text-primary">
                                {item.type === 'id_card' && '身份证号'}
                                {item.type === 'phone' && '手机号'}
                                {item.type === 'bank_card' && '银行卡号'}
                                {item.type === 'email' && '邮箱'}
                                {item.type === 'address' && '地址'}
                                {item.type === 'other' && '其他'}
                              </span>
                              <span className={`text-xs px-2 py-0.5 rounded-full ${
                                item.riskLevel === 'high' ? 'bg-red-50 text-red-500' :
                                item.riskLevel === 'medium' ? 'bg-yellow-50 text-yellow-500' :
                                'bg-blue-50 text-blue-500'
                              }`}>
                                {item.riskLevel === 'high' ? '高风险' :
                                 item.riskLevel === 'medium' ? '中风险' : '低风险'}
                              </span>
                            </div>
                            <p className="text-sm text-text-secondary">{item.content}</p>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-center py-8">
                        <CheckCircle className="h-12 w-12 text-green-500 mx-auto mb-2" />
                        <p className="text-green-600">未检测到敏感内容</p>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="text-center py-8">
                    <Shield className="h-12 w-12 text-neutral-300 mx-auto mb-2" />
                    <p className="text-text-secondary">选择文件进行检测</p>
                  </div>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader>
                <h3 className="font-semibold text-text-primary">安全建议</h3>
              </CardHeader>
              <CardBody>
                <ul className="space-y-2 text-sm text-text-secondary">
                  <li className="flex items-start gap-2">
                    <span className="text-accent-blue">•</span>
                    定期对敏感文件进行安全检测
                  </li>
                  <li className="flex items-start gap-2">
                    <span className="text-accent-blue">•</span>
                    对高敏感文件进行加密存储
                  </li>
                  <li className="flex items-start gap-2">
                    <span className="text-accent-blue">•</span>
                    限制敏感文件的访问权限
                  </li>
                  <li className="flex items-start gap-2">
                    <span className="text-accent-blue">•</span>
                    定期备份重要文件
                  </li>
                </ul>
              </CardBody>
            </Card>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card>
            <CardHeader>
              <h3 className="font-semibold text-text-primary">安全设置</h3>
            </CardHeader>
            <CardBody className="space-y-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-text-primary">自动敏感检测</p>
                  <p className="text-sm text-text-secondary">上传文件时自动检测敏感内容</p>
                </div>
                <button
                  onClick={() => setSecuritySettings((prev) => ({ ...prev, autoDetect: !prev.autoDetect }))}
                  className={`w-12 h-6 rounded-full transition-colors ${securitySettings.autoDetect ? 'bg-accent-blue' : 'bg-neutral-200'}`}
                >
                  <span className={`block w-5 h-5 rounded-full bg-white shadow-sm transform transition-transform ${securitySettings.autoDetect ? 'translate-x-6' : 'translate-x-0.5'}`} />
                </button>
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-text-primary">高敏感文件自动加密</p>
                  <p className="text-sm text-text-secondary">检测到高敏感内容自动加密存储</p>
                </div>
                <button
                  onClick={() => setSecuritySettings((prev) => ({ ...prev, encryptHighSensitive: !prev.encryptHighSensitive }))}
                  className={`w-12 h-6 rounded-full transition-colors ${securitySettings.encryptHighSensitive ? 'bg-accent-blue' : 'bg-neutral-200'}`}
                >
                  <span className={`block w-5 h-5 rounded-full bg-white shadow-sm transform transition-transform ${securitySettings.encryptHighSensitive ? 'translate-x-6' : 'translate-x-0.5'}`} />
                </button>
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-text-primary">实时安全告警</p>
                  <p className="text-sm text-text-secondary">异常访问或敏感操作实时通知</p>
                </div>
                <button
                  onClick={() => setSecuritySettings((prev) => ({ ...prev, realtimeAlert: !prev.realtimeAlert }))}
                  className={`w-12 h-6 rounded-full transition-colors ${securitySettings.realtimeAlert ? 'bg-accent-blue' : 'bg-neutral-200'}`}
                >
                  <span className={`block w-5 h-5 rounded-full bg-white shadow-sm transform transition-transform ${securitySettings.realtimeAlert ? 'translate-x-6' : 'translate-x-0.5'}`} />
                </button>
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-text-primary">二次验证</p>
                  <p className="text-sm text-text-secondary">访问敏感文件需要额外验证</p>
                </div>
                <button
                  onClick={() => setSecuritySettings((prev) => ({ ...prev, twoFactorAuth: !prev.twoFactorAuth }))}
                  className={`w-12 h-6 rounded-full transition-colors ${securitySettings.twoFactorAuth ? 'bg-accent-blue' : 'bg-neutral-200'}`}
                >
                  <span className={`block w-5 h-5 rounded-full bg-white shadow-sm transform transition-transform ${securitySettings.twoFactorAuth ? 'translate-x-6' : 'translate-x-0.5'}`} />
                </button>
              </div>

              <div>
                <p className="font-medium text-text-primary mb-2">最大文件大小</p>
                <Input
                  type="number"
                  value={securitySettings.maxFileSize}
                  onChange={(e) => setSecuritySettings((prev) => ({ ...prev, maxFileSize: parseInt(e.target.value) || 0 }))}
                  suffix="MB"
                />
              </div>

              <Button variant="primary" onClick={handleSaveSettings}>
                <Settings className="h-4 w-4 mr-2" />
                保存设置
              </Button>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <h3 className="font-semibold text-text-primary">权限管理</h3>
            </CardHeader>
            <CardBody className="space-y-6">
              <div className="flex items-center gap-3 p-3 bg-neutral-50 rounded-button">
                <div className="w-10 h-10 bg-accent-blue/10 rounded-button flex items-center justify-center">
                  <User className="h-5 w-5 text-accent-blue" />
                </div>
                <div className="flex-1">
                  <p className="font-medium text-text-primary">管理员</p>
                  <p className="text-sm text-text-secondary">admin@nebulamind.com</p>
                </div>
                <span className="px-2 py-1 bg-red-50 text-red-600 text-sm rounded-full">管理员</span>
              </div>

              <div className="flex items-center gap-3 p-3 bg-neutral-50 rounded-button">
                <div className="w-10 h-10 bg-accent-blue/10 rounded-button flex items-center justify-center">
                  <User className="h-5 w-5 text-accent-blue" />
                </div>
                <div className="flex-1">
                  <p className="font-medium text-text-primary">普通用户</p>
                  <p className="text-sm text-text-secondary">user@nebulamind.com</p>
                </div>
                <span className="px-2 py-1 bg-blue-50 text-blue-600 text-sm rounded-full">用户</span>
              </div>

              <div className="pt-4 border-t border-neutral-200">
                <p className="text-sm text-text-secondary mb-3">角色权限说明：</p>
                <ul className="space-y-2 text-sm text-text-secondary">
                  <li className="flex items-center gap-2">
                    <Eye className="h-4 w-4 text-text-secondary" />
                    管理员：全部权限，包括用户管理、权限设置、系统配置
                  </li>
                  <li className="flex items-center gap-2">
                    <Key className="h-4 w-4 text-text-secondary" />
                    用户：文件管理、AI功能使用、安全检测
                  </li>
                  <li className="flex items-center gap-2">
                    <Lock className="h-4 w-4 text-text-secondary" />
                    访客：仅查看权限
                  </li>
                </ul>
              </div>
            </CardBody>
          </Card>
        </div>
      )}
    </div>
  )
}