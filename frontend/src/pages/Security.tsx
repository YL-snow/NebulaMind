import { useState, useEffect } from 'react'
import { Shield, Lock, AlertTriangle, CheckCircle, FileText, Search, Settings, Copy, Download, Unlock, KeyRound } from 'lucide-react'
import { Button } from '@/components/common/Button'
import { Card, CardHeader, CardBody } from '@/components/common/Card'
import { Input } from '@/components/common/Input'
import { Loading } from '@/components/common/Loading'
import { Modal } from '@/components/common/Modal'
import { useToast } from '@/components/common/Toast'
import { securityApi } from '@/api/security'
import { filesApi } from '@/api/files'
import { useFileStore } from '@/stores/fileStore'
import { SENSITIVE_LEVELS } from '@/utils/constants'
import { generateFileKey, encryptBlobWithFileKey, decryptBlobWithFileKey } from '@/utils/e2eeCrypto'
import { formatFileSize, formatDate } from '@/utils/format'
import type { FileItem, SensitiveItem } from '@/api/types'

export const Security = () => {
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedFile, setSelectedFile] = useState<FileItem | null>(null)
  const [sensitiveItems, setSensitiveItems] = useState<SensitiveItem[]>([])
  const [warning, setWarning] = useState<string>('')
  const [loading, setLoading] = useState(false)
  const [filesLoading, setFilesLoading] = useState(false)
  const [activeTab, setActiveTab] = useState<'files' | 'settings'>('files')
  const [activeFilter, setActiveFilter] = useState<'high' | 'medium' | 'normal' | 'encrypted' | null>(null)
  const [unlockedFileIds, setUnlockedFileIds] = useState<Set<string>>(new Set())
  const [decryptedFileKeys, setDecryptedFileKeys] = useState<Record<string, string>>({})
  const [showDecryptModal, setShowDecryptModal] = useState(false)
  const [decryptTarget, setDecryptTarget] = useState<FileItem | null>(null)
  const [decryptKeyInput, setDecryptKeyInput] = useState('')
  const [decryptKeyError, setDecryptKeyError] = useState('')
  const [decryptLoading, setDecryptLoading] = useState(false)
  const [encryptLoading, setEncryptLoading] = useState(false)
  const [pendingEncryptFile, setPendingEncryptFile] = useState<FileItem | null>(null)
  const [showOneTimeKeyModal, setShowOneTimeKeyModal] = useState(false)
  const [oneTimeKey, setOneTimeKey] = useState<{ fileName: string; key: string } | null>(null)
  const [copiedKey, setCopiedKey] = useState(false)
  interface SecuritySettings {
    encryptHighSensitive: boolean
  }

  const [securitySettings, setSecuritySettings] = useState<SecuritySettings>(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('securitySettings') || '{}')
      return { encryptHighSensitive: saved?.encryptHighSensitive !== false }
    } catch {
      return { encryptHighSensitive: true }
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
    setWarning('')
    setSelectedFile(file)
    try {
      const response = await securityApi.detect({
        fileId: file.id,
        autoEncrypt: securitySettings.encryptHighSensitive,
      })
      const updatedFile: FileItem = {
        ...file,
        sensitiveLevel: response.sensitiveLevel,
        isEncrypted: response.autoEncrypted ? true : file.isEncrypted,
      }
      setSensitiveItems(response.sensitiveItems)
      setWarning(response.warning || '')
      setWarning(response.warning || (response as unknown as { message?: string }).message || '')
      setSelectedFile(updatedFile)
      setFiles(files.map((f) => (f.id === file.id ? updatedFile : f)))
      if (response.warning) {
        error(response.warning)
      } else {
        success(response.autoEncrypted ? '检测完成，高风险内容已自动加密' : '安全检测完成')
      }
    } catch (err) {
      error((err as Error).message || '检测失败')
    } finally {
      setLoading(false)
    }
  }

  const runEncrypt = async (file: FileItem, existingKey?: string) => {
    setEncryptLoading(true)
    try {
      const blob = await filesApi.download(file.id) as unknown as Blob
      let plain = new Uint8Array(await blob.arrayBuffer())
      if (file.encryptionMode === 'CLIENT') {
        const currentKey = existingKey || decryptedFileKeys[file.id]
        if (!currentKey) throw new Error('请先解密该文件，再使用新密钥重新加密')
        plain = await decryptBlobWithFileKey(plain, currentKey)
      }
      const fileKey = await generateFileKey()
      const encryptedData = await encryptBlobWithFileKey(plain, fileKey)
      const encryptedFile = new File([encryptedData], file.name, { type: file.mimeType })
      const comment = file.encryptionMode === 'CLIENT' ? '重新加密' : '端到端加密'
      const updatedFile = await filesApi.uploadVersion(file.id, encryptedFile, comment, undefined, true)
      setUnlockedFileIds((prev) => {
        const next = new Set(prev)
        next.delete(file.id)
        return next
      })
      setDecryptedFileKeys((prev) => {
        const next = { ...prev }
        delete next[file.id]
        return next
      })
      setFiles(files.map((f) => (f.id === file.id ? updatedFile : f)))
      setSelectedFile((prev) => prev?.id === file.id ? updatedFile : prev)
      setOneTimeKey({ fileName: file.name, key: fileKey.base64 })
      setCopiedKey(false)
      setShowOneTimeKeyModal(true)
      success('文件已使用新的独立密钥完成加密，请立即保存密钥')
    } catch (err) {
      error((err as Error).message || '加密失败')
    } finally {
      setEncryptLoading(false)
    }
  }

  const handleEncrypt = async (file: FileItem) => {
    if (file.encryptionMode === 'CLIENT' && !unlockedFileIds.has(file.id)) {
      if (!confirm(`确定要重新加密文件 ${file.name} 吗？重新加密需要先输入当前密钥。`)) return
      setPendingEncryptFile(file)
      handleDecryptOpen(file)
      return
    }
    if (!confirm(`确定要${file.encryptionMode === 'CLIENT' ? '重新加密' : '加密'}文件 ${file.name} 吗？`)) return
    await runEncrypt(file)
  }

  const handleDecryptOpen = (file: FileItem) => {
    setDecryptTarget(file)
    setDecryptKeyInput('')
    setDecryptKeyError('')
    setShowDecryptModal(true)
  }

  const handleConfirmDecrypt = async () => {
    if (!decryptTarget) return
    const key = decryptKeyInput.trim()
    if (!key) {
      setDecryptKeyError('请输入文件密钥')
      return
    }
    setDecryptLoading(true)
    try {
      const blob = await filesApi.download(decryptTarget.id) as unknown as Blob
      const bytes = new Uint8Array(await blob.arrayBuffer())
      await decryptBlobWithFileKey(bytes, key)
      setUnlockedFileIds((prev) => {
        const next = new Set(prev)
        next.add(decryptTarget.id)
        return next
      })
      setDecryptedFileKeys((prev) => ({ ...prev, [decryptTarget.id]: key }))
      const targetId = decryptTarget.id
      setShowDecryptModal(false)
      setDecryptTarget(null)
      setDecryptKeyInput('')
      if (pendingEncryptFile && pendingEncryptFile.id === targetId) {
        const target = pendingEncryptFile
        setPendingEncryptFile(null)
        await runEncrypt(target, key)
      } else {
        success('文件解密成功，可点击“下载”保存明文文件')
      }
    } catch (err) {
      setDecryptKeyError((err as Error).message || '文件密钥不正确，请检查输入')
    } finally {
      setDecryptLoading(false)
    }
  }

  const handleDecryptedDownload = async (file: FileItem) => {
    try {
      const blob = await filesApi.download(file.id) as unknown as Blob
      let bytes = new Uint8Array(await blob.arrayBuffer())
      if (file.encryptionMode === 'CLIENT') {
        const key = decryptedFileKeys[file.id]
        if (!key) {
          error('文件仍处于加密状态，请先点击“解密”输入文件密钥')
          return
        }
        bytes = await decryptBlobWithFileKey(bytes, key)
      }
      const url = window.URL.createObjectURL(new Blob([bytes], { type: file.mimeType }))
      const a = document.createElement('a')
      a.href = url
      a.download = file.name
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      window.URL.revokeObjectURL(url)
      success('下载开始')
    } catch (err) {
      error((err as Error).message || '下载失败')
    }
  }

  const handleSaveSettings = () => {
    localStorage.setItem('securitySettings', JSON.stringify(securitySettings))
    success('安全设置已保存')
  }

  const handleCopyOneTimeKey = async () => {
    if (!oneTimeKey) return
    await navigator.clipboard.writeText(oneTimeKey.key)
    setCopiedKey(true)
    setTimeout(() => setCopiedKey(false), 2000)
  }

  const handleDownloadOneTimeKey = () => {
    if (!oneTimeKey) return
    const content = [
      'NebulaMind 文件密钥备份',
      '请妥善保管以下密钥，服务器不会保存。密钥只在加密时显示一次，丢失后文件无法解密。',
      '',
      `文件名: ${oneTimeKey.fileName}`,
      `文件密钥: ${oneTimeKey.key}`,
    ].join('\n')
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

  const encryptedFiles = filteredFiles.filter((f) => f.isEncrypted)

  const stats = [
    { key: 'high' as const, label: '高敏感文件', value: highSensitiveFiles.length, color: 'text-red-500', bgColor: 'bg-red-50' },
    { key: 'medium' as const, label: '中敏感文件', value: mediumSensitiveFiles.length, color: 'text-yellow-500', bgColor: 'bg-yellow-50' },
    { key: 'normal' as const, label: '安全文件', value: normalFiles.length, color: 'text-green-500', bgColor: 'bg-green-50' },
    { key: 'encrypted' as const, label: '已加密文件', value: encryptedFiles.length, color: 'text-blue-500', bgColor: 'bg-blue-50' },
  ]

  const tabs = [
    { key: 'files', label: '敏感文件', icon: AlertTriangle },
    { key: 'settings', label: '安全设置', icon: Settings },
  ]

  const displayFiles = activeFilter
    ? filteredFiles.filter((file) => {
        if (activeFilter === 'high') return getLevel(file) === 'high'
        if (activeFilter === 'medium') return getLevel(file) === 'medium'
        if (activeFilter === 'normal') return getLevel(file) === 'normal' || getLevel(file) === 'low'
        return file.isEncrypted
      })
    : filteredFiles

  const fileIcon = (file: FileItem) => {
    const level = getLevel(file)
    if (level === 'high') return AlertTriangle
    if (level === 'medium') return AlertTriangle
    if (level === 'normal' || level === 'low') return CheckCircle
    return FileText
  }

  const fileIconColor = (file: FileItem) => {
    const level = getLevel(file)
    if (level === 'high') return 'text-red-500'
    if (level === 'medium') return 'text-yellow-500'
    if (level === 'normal' || level === 'low') return 'text-green-500'
    return 'text-text-secondary'
  }

  const fileIconBg = (file: FileItem) => {
    const level = getLevel(file)
    if (level === 'high') return 'bg-red-50'
    if (level === 'medium') return 'bg-yellow-50'
    if (level === 'normal' || level === 'low') return 'bg-green-50'
    return 'bg-neutral-100'
  }

  const SecurityFileCard = ({ file }: { file: FileItem }) => {
    const Icon = fileIcon(file)
    const isUnlocked = unlockedFileIds.has(file.id)
    return (
      <Card key={file.id} hoverable onClick={() => handleDetect(file)}>
        <CardBody className="flex items-center gap-4">
          <div className={`w-10 h-10 rounded-button ${fileIconBg(file)} flex items-center justify-center`}>
            <Icon className={`h-5 w-5 ${fileIconColor(file)}`} />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <p className="font-medium text-text-primary truncate">{file.name}</p>
              {file.isEncrypted && (
                <Lock className="h-4 w-4 text-green-500" />
              )}
              {file.encryptionMode === 'CLIENT' && isUnlocked && (
                <span className="px-1.5 py-0.5 rounded-full bg-green-50 text-green-600 text-xs whitespace-nowrap">已解密</span>
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
            {file.encryptionMode === 'CLIENT' && !isUnlocked && (
              <Button variant="outline" size="sm" onClick={(e) => { e.stopPropagation(); handleDecryptOpen(file) }}>
                <Unlock className="h-4 w-4 mr-1" />
                解密
              </Button>
            )}
            {((file.encryptionMode === 'CLIENT' && isUnlocked) || file.encryptionMode !== 'CLIENT') && (
              <Button variant="primary" size="sm" onClick={(e) => { e.stopPropagation(); handleDecryptedDownload(file) }}>
                <Download className="h-4 w-4 mr-1" />
                下载
              </Button>
            )}
            <Button variant="ghost" size="sm" onClick={(e) => { e.stopPropagation(); handleEncrypt(file) }} disabled={encryptLoading}>
              <Lock className="h-4 w-4 mr-1" />
              {file.encryptionMode === 'CLIENT' ? '重新加密' : file.isEncrypted ? '转为端到端加密' : '加密'}
            </Button>
          </div>
        </CardBody>
      </Card>
    )
  }

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
          <button
            key={stat.label}
            onClick={() => setActiveFilter((prev) => prev === stat.key ? null : stat.key)}
            className={`text-left transition-all ${activeFilter === stat.key ? 'ring-2 ring-accent-blue rounded-button' : ''}`}
          >
            <Card hoverable>
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
          </button>
        ))}
      </div>

      {activeTab === 'files' ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
  <div className="space-y-4">
    {activeFilter ? (
      <div>
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            {activeFilter === 'high' && <AlertTriangle className="h-5 w-5 text-red-500" />}
            {activeFilter === 'medium' && <AlertTriangle className="h-5 w-5 text-yellow-500" />}
            {activeFilter === 'normal' && <CheckCircle className="h-5 w-5 text-green-500" />}
            {activeFilter === 'encrypted' && <Lock className="h-5 w-5 text-blue-500" />}
            <h3 className="font-semibold text-text-primary">
              {activeFilter === 'high' && '高敏感文件'}
              {activeFilter === 'medium' && '中敏感文件'}
              {activeFilter === 'normal' && '安全文件'}
              {activeFilter === 'encrypted' && '已加密文件'}
            </h3>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setActiveFilter(null)}>
            清除筛选
          </Button>
        </div>
        {displayFiles.length > 0 ? (
          <div className="grid gap-3">
            {displayFiles.map((file) => (
              <SecurityFileCard key={file.id} file={file} />
            ))}
          </div>
        ) : (
          <div className="text-center py-16">
            <Shield className="h-16 w-16 text-neutral-300 mx-auto mb-4" />
            <p className="text-text-secondary">该分类下暂无文件</p>
            <Button variant="outline" size="sm" className="mt-4" onClick={() => setActiveFilter(null)}>
              查看全部文件
            </Button>
          </div>
        )}
      </div>
    ) : (
      <>
        {highSensitiveFiles.length > 0 && (
          <div>
            <div className="flex items-center gap-2 mb-3">
              <AlertTriangle className="h-5 w-5 text-red-500" />
              <h3 className="font-semibold text-red-600">高敏感文件</h3>
            </div>
            <div className="grid gap-3">
              {highSensitiveFiles.map((file) => (
                <SecurityFileCard key={file.id} file={file} />
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
                <SecurityFileCard key={file.id} file={file} />
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
                <SecurityFileCard key={file.id} file={file} />
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
      </>
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

                    {warning && (
                      <div className="flex items-start gap-2 p-3 bg-yellow-50 border border-yellow-200 rounded-button">
                        <AlertTriangle className="h-4 w-4 text-yellow-600 mt-0.5 flex-shrink-0" />
                        <p className="text-sm text-yellow-700">{warning}</p>
                      </div>
                    )}

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

              <Button variant="primary" onClick={handleSaveSettings}>
                <Settings className="h-4 w-4 mr-2" />
                保存设置
              </Button>
            </CardBody>
          </Card>

          <Card>
            <CardHeader>
              <h3 className="font-semibold text-text-primary">安全能力</h3>
            </CardHeader>
            <CardBody className="space-y-4">
              <div className="flex items-center gap-3 p-3 bg-neutral-50 rounded-button">
                <Shield className="h-5 w-5 text-accent-blue" />
                <span className="text-sm text-text-secondary">敏感检测：本地正则 + AI 内容解析</span>
              </div>
              <div className="flex items-center gap-3 p-3 bg-neutral-50 rounded-button">
                <Lock className="h-5 w-5 text-accent-blue" />
                <span className="text-sm text-text-secondary">文件加密：AES-256-GCM，每个文件独立密钥端到端加密</span>
              </div>
              <div className="flex items-center gap-3 p-3 bg-neutral-50 rounded-button">
                <AlertTriangle className="h-5 w-5 text-accent-blue" />
                <span className="text-sm text-text-secondary">审计日志：检测与加密操作已记录</span>
              </div>
              <div className="flex items-center gap-3 p-3 bg-neutral-50 rounded-button">
                <CheckCircle className="h-5 w-5 text-accent-blue" />
                <span className="text-sm text-text-secondary">下载保护：端到端加密文件在浏览器本地解密</span>
              </div>
            </CardBody>
          </Card>
        </div>
      )}

      <Modal isOpen={showDecryptModal} onClose={() => setShowDecryptModal(false)} title="输入文件密钥">
        {decryptTarget && (
          <div className="space-y-4">
            <p className="text-sm text-text-secondary">该文件使用独立密钥进行端到端加密，请输入加密时显示的密钥完成解密。</p>
            <p className="text-sm font-medium text-text-primary truncate">{decryptTarget.name}</p>
            {decryptKeyError && (
              <div className="px-3 py-2 bg-red-50 border border-red-200 text-sm text-red-600">{decryptKeyError}</div>
            )}
            <input
              type="text"
              value={decryptKeyInput}
              onChange={(e) => setDecryptKeyInput(e.target.value)}
              className="w-full px-3 py-2 text-sm font-mono border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
              placeholder="粘贴文件密钥"
              autoFocus
            />
            <div className="flex justify-end gap-3">
              <Button variant="ghost" onClick={() => setShowDecryptModal(false)}>
                取消
              </Button>
              <Button variant="primary" onClick={handleConfirmDecrypt} loading={decryptLoading}>
                <KeyRound className="h-4 w-4 mr-2" />
                确认解密
              </Button>
            </div>
          </div>
        )}
      </Modal>

      <Modal isOpen={showOneTimeKeyModal} onClose={() => setShowOneTimeKeyModal(false)} title="文件密钥（仅显示一次）">
        {oneTimeKey && (
          <div className="space-y-4">
            <div className="px-3 py-2 bg-red-50 border border-red-200 text-sm text-red-600">
              请立即复制并妥善保存该密钥，关闭后将无法再次查看。服务器不会保存密钥，丢失后文件将无法解密。
            </div>
            <p className="text-sm font-medium text-text-primary truncate">{oneTimeKey.fileName}</p>
            <div className="flex items-start gap-2">
              <code className="flex-1 min-w-0 px-3 py-2 bg-neutral-50 border border-neutral-200 text-xs font-mono break-all">{oneTimeKey.key}</code>
              <button
                onClick={handleCopyOneTimeKey}
                className="px-3 py-2 text-sm bg-accent-blue text-white hover:bg-accent-blue/90 transition-colors flex items-center gap-1 flex-shrink-0"
              >
                <Copy className="h-4 w-4" />
                {copiedKey ? '已复制' : '复制'}
              </button>
            </div>
            <div className="flex justify-end gap-3">
              <Button variant="outline" onClick={handleDownloadOneTimeKey}>
                <Download className="h-4 w-4 mr-2" />
                下载密钥文件
              </Button>
              <Button variant="primary" onClick={() => { setShowOneTimeKeyModal(false); setOneTimeKey(null) }}>
                我已保存密钥
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
