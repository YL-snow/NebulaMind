import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Download, Edit3, Trash2, Clock, User, Tag, Shield, FileText, Sparkles, ChevronDown, ChevronUp, RefreshCw, History, Brain, Save, Upload, Unlock, Lock, Copy } from 'lucide-react'
import { Button } from '@/components/common/Button'
import { Card, CardHeader, CardBody } from '@/components/common/Card'
import { TextArea } from '@/components/common/Input'
import { Modal } from '@/components/common/Modal'
import { Loading } from '@/components/common/Loading'
import { useToast } from '@/components/common/Toast'
import { filesApi } from '@/api/files'
import { qaApi } from '@/api/qa'
import { securityApi } from '@/api/security'
import { generateApi } from '@/api/generate'
import { formatFileSize, formatDate, formatDateTime } from '@/utils/format'
import { encryptBlobWithKeyBase64, decryptBlobWithFileKey, encodeText, decodeText, generateFileKey, encryptBlobWithFileKey } from '@/utils/e2eeCrypto'
import { FILE_TYPES, SENSITIVE_LEVELS, AI_STATUS, ARCHIVE_FILE_TYPES, TEXT_EDITABLE_EXTENSIONS } from '@/utils/constants'
import type { FileItem, QAResponse, VersionItem, SensitiveItem } from '@/api/types'

export const FileDetail = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [file, setFile] = useState<FileItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [question, setQuestion] = useState('')
  const [qaResponse, setQaResponse] = useState<QAResponse | null>(null)
  const [qaLoading, setQaLoading] = useState(false)
  const [expandedSections, setExpandedSections] = useState({ summary: true, qa: true, security: true, version: true })
  const [versions, setVersions] = useState<VersionItem[]>([])
  const [versionsLoading, setVersionsLoading] = useState(false)
  const [classifyLoading, setClassifyLoading] = useState(false)
  const [summaryLoading, setSummaryLoading] = useState(false)
  const [securityLoading, setSecurityLoading] = useState(false)
  const [securityItems, setSecurityItems] = useState<SensitiveItem[]>([])
  const [showEditModal, setShowEditModal] = useState(false)
  const [editName, setEditName] = useState('')
  const [editSaving, setEditSaving] = useState(false)
  const [showUploadVersionModal, setShowUploadVersionModal] = useState(false)
  const [uploadVersionFile, setUploadVersionFile] = useState<File | null>(null)
  const [uploadVersionComment, setUploadVersionComment] = useState('')
  const [uploadingVersion, setUploadingVersion] = useState(false)
  const [showTextEditModal, setShowTextEditModal] = useState(false)
  const [textContent, setTextContent] = useState('')
  const [textComment, setTextComment] = useState('')
  const [textContentLoading, setTextContentLoading] = useState(false)
  const [textSaving, setTextSaving] = useState(false)
  const [diffResult, setDiffResult] = useState<{
    diff: string
    additions: number
    deletions: number
    modifications: number
    versionA: number
    versionB: number
    versionACreator: string
    versionBCreator: string
    diffFormat: string
  } | null>(null)
  const [diffLoading, setDiffLoading] = useState(false)
  const [compareVersions, setCompareVersions] = useState<{ a: number | null; b: number | null }>({ a: null, b: null })
  const [unlockedFileKey, setUnlockedFileKey] = useState<string | null>(null)
  const [showKeyPrompt, setShowKeyPrompt] = useState(false)
  const [keyPromptInput, setKeyPromptInput] = useState('')
  const [keyPromptError, setKeyPromptError] = useState('')
  const [keyPromptLoading, setKeyPromptLoading] = useState(false)
  const [pendingKeyAction, setPendingKeyAction] = useState<'decrypt' | 'decrypt-download' | 'encrypt' | 'text-edit' | 'upload-version' | 'save-text'>('decrypt')
  const [pendingUploadVersion, setPendingUploadVersion] = useState<{ file: File; comment: string } | null>(null)
  const [encryptLoading, setEncryptLoading] = useState(false)
  const [showEncryptKeyModal, setShowEncryptKeyModal] = useState(false)
  const [oneTimeKey, setOneTimeKey] = useState<{ fileName: string; key: string } | null>(null)
  const [copiedKey, setCopiedKey] = useState(false)

  const { error, success } = useToast()

  useEffect(() => {
    if (id) {
      fetchFileDetail(id)
      fetchVersionHistory(id)
    }
  }, [id])

  const fetchFileDetail = async (fileId: string) => {
    setLoading(true)
    try {
      const response = await filesApi.detail(fileId)
      setFile(response)
    } catch (err) {
      error((err as Error).message || '获取文件详情失败')
    } finally {
      setLoading(false)
    }
  }

  const fetchVersionHistory = async (fileId: string) => {
    setVersionsLoading(true)
    try {
      const response = await filesApi.versionHistory(fileId)
      setVersions(response.versions || [])
    } catch (err) {
      console.log('获取版本历史失败:', err)
    } finally {
      setVersionsLoading(false)
    }
  }

  const handleAsk = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!question.trim() || !file) return
    if (file.encryptionMode === 'CLIENT') {
      error('端到端加密文件无法进行服务器端智能问答，请先在本地解密')
      return
    }

    setQaLoading(true)
    try {
      const response = await qaApi.ask({ question: question.trim(), fileId: file.id })
      setQaResponse(response)
      setQuestion('')
      if (response.answer.includes('暂时不可用')) {
        error(response.answer)
      } else {
        success('问答完成')
      }
    } catch (err) {
      error((err as Error).message || '问答失败')
    } finally {
      setQaLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!file || !confirm(`确定要删除文件 ${file.name} 吗？`)) return
    try {
      await filesApi.delete(file.id)
      success('文件删除成功')
      navigate('/home')
    } catch (err) {
      error((err as Error).message || '删除文件失败')
    }
  }


  const runDownload = async (currentKey?: string) => {
    if (!file) return
    try {
      const response = await filesApi.download(file.id) as unknown as Blob
      let bytes = new Uint8Array(await response.arrayBuffer())
      if (file.encryptionMode === 'CLIENT') {
        const key = currentKey || unlockedFileKey
        if (!key) throw new Error('请输入该文件的密钥')
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

  const openKeyPrompt = (action: 'decrypt' | 'decrypt-download' | 'encrypt' | 'text-edit' | 'upload-version' | 'save-text') => {
    setPendingKeyAction(action)
    setKeyPromptInput('')
    setKeyPromptError('')
    setShowKeyPrompt(true)
  }

  const handleDecrypt = async () => {
    if (!file) return
    if (file.encryptionMode !== 'CLIENT') return
    setPendingUploadVersion(null)
    openKeyPrompt('decrypt')
  }

  const handleDownload = async () => {
    if (!file) return
    if (file.encryptionMode === 'CLIENT' && !unlockedFileKey) {
      error('文件仍处于加密状态，请先点击“解密”输入文件密钥')
      return
    }
    await runDownload()
  }

  const handleEncryptFile = async () => {
    if (!file) return
    if (file.encryptionMode === 'CLIENT' && !unlockedFileKey) {
      if (!confirm(`确定要重新加密文件 ${file.name} 吗？重新加密需要先输入当前密钥。`)) return
      setPendingUploadVersion(null)
      openKeyPrompt('encrypt')
      return
    }
    if (!confirm(`确定要${file.encryptionMode === 'CLIENT' ? '重新加密' : '加密'}文件 ${file.name} 吗？`)) return
    await runEncryptFile()
  }

  const runEncryptFile = async (currentKey?: string) => {
    if (!file) return
    setEncryptLoading(true)
    try {
      const blob = await filesApi.download(file.id) as unknown as Blob
      let plain = new Uint8Array(await blob.arrayBuffer())
      if (file.encryptionMode === 'CLIENT') {
        const key = currentKey || unlockedFileKey
        if (!key) throw new Error('请先解密该文件，再使用新密钥重新加密')
        plain = await decryptBlobWithFileKey(plain, key)
      }
      const fileKey = await generateFileKey()
      const encryptedData = await encryptBlobWithFileKey(plain, fileKey)
      const encryptedFile = new File([encryptedData], file.name, { type: file.mimeType })
      const comment = file.encryptionMode === 'CLIENT' ? '重新加密' : '端到端加密'
      const updated = await filesApi.uploadVersion(file.id, encryptedFile, comment, undefined, true)
      setFile(updated)
      await fetchVersionHistory(file.id)
      setUnlockedFileKey(null)
      setOneTimeKey({ fileName: file.name, key: fileKey.base64 })
      setCopiedKey(false)
      setShowEncryptKeyModal(true)
      success('文件已使用新的独立密钥完成加密，请立即保存密钥')
    } catch (err) {
      error((err as Error).message || '加密失败')
    } finally {
      setEncryptLoading(false)
    }
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



  const handleEdit = () => {
    if (!file) return
    setEditName(file.name)
    setShowEditModal(true)
  }

  const handleEditSave = async () => {
    if (!file || !editName.trim()) return
    setEditSaving(true)
    try {
      const updated = await filesApi.update(file.id, { name: editName.trim() })
      setFile(updated)
      setShowEditModal(false)
      success('文件重命名成功')
    } catch (err) {
      error((err as Error).message || '重命名失败')
    } finally {
      setEditSaving(false)
    }
  }

  const handleDetectSecurity = async () => {
    if (!file) return
    if (file.encryptionMode === 'CLIENT') {
      error('端到端加密文件无法进行服务器端敏感检测，请先在本地解密后上传普通文件')
      return
    }
    setSecurityLoading(true)
    setSecurityItems([])
    try {
      const response = await securityApi.detect({ fileId: file.id })
      setFile((prev) => prev ? { ...prev, sensitiveLevel: response.sensitiveLevel } : null)
      if (response.sensitiveItems && response.sensitiveItems.length > 0) {
        setSecurityItems(response.sensitiveItems)
        success(`安全检测完成，发现 ${response.sensitiveItems.length} 处敏感内容`)
      } else {
        setSecurityItems([])
        success('安全检测完成，未发现敏感内容')
      }
    } catch (err) {
      error((err as Error).message || '安全检测失败')
    } finally {
      setSecurityLoading(false)
    }
  }

  const handleRemoveTag = async (tag: string) => {
    if (!file) return
    try {
      let currentTags: string[] = []
      try { currentTags = JSON.parse(file.tags || '[]') } catch { currentTags = [] }
      if (!Array.isArray(currentTags)) currentTags = []
      const newTags = JSON.stringify(currentTags.filter((t) => t !== tag))
      await filesApi.update(file.id, { tags: newTags })
      setFile((prev) => prev ? { ...prev, tags: newTags } : null)
      success(`标签 "${tag}" 已移除`)
    } catch (err) {
      error((err as Error).message || '移除标签失败')
    }
  }

  const handleGenerateSummary = async () => {
    if (!file) return
    if (ARCHIVE_FILE_TYPES.includes(file.fileType?.toLowerCase())) {
      error('压缩包不支持直接生成摘要，请先解压后上传文件再试')
      return
    }
    if (file.encryptionMode === 'CLIENT') {
      error('端到端加密文件无法进行服务器端 AI 摘要，请先在本地解密')
      return
    }
    setSummaryLoading(true)
    try {
      const response = await generateApi.summary({ fileId: file.id })
      if (response.content.includes('暂时不可用') || response.content.includes('失败')) {
        error(response.content)
      } else {
        setFile((prev) =>
          prev ? { ...prev, summary: response.content } : null
        )
        success('AI 摘要生成完成')
      }
    } catch (err) {
      error((err as Error).message || '摘要生成失败')
    } finally {
      setSummaryLoading(false)
    }
  }

  const handleClassify = async () => {
    if (!file) return
    if (file.encryptionMode === 'CLIENT') {
      error('端到端加密文件无法进行服务器端 AI 分类，请先在本地解密')
      return
    }
    setClassifyLoading(true)
    try {
      const response = await filesApi.classify(file.id)
      setFile((prev) =>
        prev ? { ...prev, tags: JSON.stringify(response.tags), category: response.category } : null
      )
      success(`AI 分类完成: ${response.category}`)
    } catch (err) {
      error((err as Error).message || 'AI 分类失败')
      // 重新获取文件详情以获取服务器端可能已保存的分类结果
      if (file) fetchFileDetail(file.id)
    } finally {
      setClassifyLoading(false)
    }
  }

  const handleRestoreVersion = async (version: VersionItem) => {
    if (!file || !confirm(`确定要将文件恢复到版本 ${version.version} 吗？`)) return
    try {
      const result = await filesApi.restoreVersion(file.id, version.version)
      success(result.message || `文件已恢复到版本 ${version.version}`)
      await Promise.all([fetchFileDetail(file.id), fetchVersionHistory(file.id)])
    } catch (err) {
      error((err as Error).message || '恢复失败')
    }
  }

  const handleVersionDiff = async (versionA: number, versionB: number) => {
    if (!file) return
    setDiffLoading(true)
    setDiffResult(null)
    try {
      const result = await filesApi.versionDiff(file.id, versionA, versionB)
      setDiffResult(result)
    } catch (err) {
      error((err as Error).message || '版本对比失败')
    } finally {
      setDiffLoading(false)
    }
  }

  const handleSelectCompare = (version: number) => {
    if (compareVersions.a === null) {
      setCompareVersions({ a: version, b: null })
    } else if (compareVersions.a === version) {
      setCompareVersions({ a: null, b: null })
    } else {
      setCompareVersions({ ...compareVersions, b: version })
      handleVersionDiff(
        Math.min(compareVersions.a, version),
        Math.max(compareVersions.a, version)
      )
    }
  }

  const handleCloseDiff = () => {
    setDiffResult(null)
    setCompareVersions({ a: null, b: null })
  }

  const handleDiffKeyChange = (versionA: number, versionB: number) => {
    if (versionA !== versionB) {
      handleVersionDiff(versionA, versionB)
    }
  }

  const isTextEditable = (target: FileItem) => {
    const name = target.name?.toLowerCase() || ''
    const dot = name.lastIndexOf('.')
    const extension = dot >= 0 && dot < name.length - 1 ? name.slice(dot + 1) : ''
    return TEXT_EDITABLE_EXTENSIONS.includes(extension) || TEXT_EDITABLE_EXTENSIONS.includes(target.fileType?.toLowerCase() || '')
  }

  const handleOpenUploadVersion = () => {
    setUploadVersionFile(null)
    setUploadVersionComment('')
    setShowUploadVersionModal(true)
  }

  const handleUploadVersion = async () => {
    if (!file || !uploadVersionFile) {
      error('请选择要上传的新版本文件')
      return
    }
    if (file.encryptionMode === 'CLIENT' && !unlockedFileKey) {
      setPendingUploadVersion({ file: uploadVersionFile, comment: uploadVersionComment.trim() })
      openKeyPrompt('upload-version')
      return
    }
    await runUploadVersion(uploadVersionFile, uploadVersionComment.trim())
  }

  const runUploadVersion = async (target: File, comment: string, currentKey?: string) => {
    if (!file) return
    setUploadingVersion(true)
    try {
      let uploadTarget: File = target
      let encrypted = false
      if (file.encryptionMode === 'CLIENT') {
        const key = currentKey || unlockedFileKey
        if (!key) throw new Error('请输入该文件的密钥')
        const plain = new Uint8Array(await target.arrayBuffer())
        const encryptedData = await encryptBlobWithKeyBase64(plain, key)
        uploadTarget = new File([encryptedData], target.name, { type: target.type || file.mimeType })
        encrypted = true
      }
      const updated = await filesApi.uploadVersion(file.id, uploadTarget, comment, undefined, encrypted)
      setFile(updated)
      await fetchVersionHistory(file.id)
      setShowUploadVersionModal(false)
      setUploadVersionFile(null)
      setUploadVersionComment('')
      setPendingUploadVersion(null)
      success('新版本上传成功')
    } catch (err) {
      error((err as Error).message || '新版本上传失败')
    } finally {
      setUploadingVersion(false)
    }
  }

  const handleOpenTextEdit = async () => {
    if (!file) return
    if (file.encryptionMode === 'CLIENT' && !unlockedFileKey) {
      setPendingUploadVersion(null)
      openKeyPrompt('text-edit')
      return
    }
    await runOpenTextEdit()
  }

  const runOpenTextEdit = async (currentKey?: string) => {
    if (!file) return
    setTextContentLoading(true)
    try {
      const blob = await filesApi.download(file.id) as unknown as Blob
      const bytes = new Uint8Array(await blob.arrayBuffer())
      const key = currentKey || unlockedFileKey
      const content = file.encryptionMode === 'CLIENT'
        ? decodeText(await decryptBlobWithFileKey(bytes, key || ''))
        : await blob.text()
      setTextContent(content)
      setTextComment('')
      setShowTextEditModal(true)
    } catch (err) {
      error((err as Error).message || '读取文件内容失败')
    } finally {
      setTextContentLoading(false)
    }
  }

  const handleSaveTextVersion = async () => {
    if (!file || !textContent.trim()) {
      error('文件内容不能为空')
      return
    }
    if (file.encryptionMode === 'CLIENT' && !unlockedFileKey) {
      setPendingUploadVersion(null)
      openKeyPrompt('save-text')
      return
    }
    await runSaveTextVersion(textContent, textComment.trim())
  }

  const runSaveTextVersion = async (content: string, comment: string, currentKey?: string) => {
    if (!file) return
    setTextSaving(true)
    try {
      let updated
      if (file.encryptionMode === 'CLIENT') {
        const key = currentKey || unlockedFileKey
        if (!key) throw new Error('请输入该文件的密钥')
        const encryptedData = await encryptBlobWithKeyBase64(encodeText(content), key)
        const encryptedFile = new File([encryptedData], file.name, { type: file.mimeType })
        updated = await filesApi.uploadVersion(file.id, encryptedFile, comment, undefined, true)
      } else {
        updated = await filesApi.saveTextVersion(file.id, content, comment)
      }
      setFile(updated)
      await fetchVersionHistory(file.id)
      setShowTextEditModal(false)
      success('在线编辑已保存为新版本')
    } catch (err) {
      error((err as Error).message || '保存新版本失败')
    } finally {
      setTextSaving(false)
    }
  }

  const handleConfirmKeyPrompt = async () => {
    const key = keyPromptInput.trim()
    if (!key) {
      setKeyPromptError('请输入文件密钥')
      return
    }
    if (!file) return
    setKeyPromptLoading(true)
    try {
      const blob = await filesApi.download(file.id) as unknown as Blob
      const bytes = new Uint8Array(await blob.arrayBuffer())
      if (file.encryptionMode === 'CLIENT') {
        await decryptBlobWithFileKey(bytes, key)
      }
      setUnlockedFileKey(key)
      setKeyPromptInput('')
      setKeyPromptError('')
      setShowKeyPrompt(false)
      if (pendingKeyAction === 'decrypt') {
        success('文件解密成功，可点击“下载”保存明文文件')
      } else if (pendingKeyAction === 'decrypt-download') {
        await runDownload(key)
      } else if (pendingKeyAction === 'text-edit') {
        await runOpenTextEdit(key)
      } else if (pendingKeyAction === 'upload-version') {
        if (pendingUploadVersion) {
          await runUploadVersion(pendingUploadVersion.file, pendingUploadVersion.comment, key)
        }
      } else if (pendingKeyAction === 'save-text') {
        await runSaveTextVersion(textContent, textComment.trim(), key)
      } else if (pendingKeyAction === 'encrypt') {
        await runEncryptFile(key)
      }
    } catch (err) {
      setKeyPromptError((err as Error).message || '文件密钥不正确')
    } finally {
      setKeyPromptLoading(false)
    }
  }

  if (loading) {
    return <Loading text="加载文件详情..." />
  }

  if (!file) {
    return <div className="text-center py-16">文件不存在</div>
  }

  const fileType = FILE_TYPES[file.fileType as keyof typeof FILE_TYPES] || FILE_TYPES.default
  const sensitiveLevel = SENSITIVE_LEVELS[file.sensitiveLevel?.toLowerCase() as keyof typeof SENSITIVE_LEVELS] || SENSITIVE_LEVELS.normal
  const aiStatus = AI_STATUS[file.aiStatus?.toLowerCase() as keyof typeof AI_STATUS] || AI_STATUS.pending

  const toggleSection = (section: keyof typeof expandedSections) => {
    setExpandedSections((prev) => ({ ...prev, [section]: !prev[section] }))
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/home')}
            className="p-2 rounded-button hover:bg-neutral-100 transition-colors"
          >
            <ArrowLeft className="h-5 w-5 text-text-secondary" />
          </button>
          <div>
            <h2 className="text-xl font-semibold text-text-primary">{file.name}</h2>
            <div className="flex items-center gap-4 mt-1 text-sm text-text-secondary">
              <span>{fileType.label}</span>
              <span>{formatFileSize(file.size)}</span>
              <span>{formatDate(file.createdAt)}</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-3">
            {file.encryptionMode === 'CLIENT' && !unlockedFileKey && (
            <Button variant="outline" onClick={handleDecrypt}>
              <Unlock className="h-4 w-4 mr-2" />
              解密
            </Button>
          )}
          <Button variant="ghost" onClick={handleDownload}>
            <Download className="h-4 w-4 mr-2" />
            下载
            {file.encryptionMode === 'CLIENT' && unlockedFileKey && (
              <span className="ml-2 px-1.5 py-0.5 rounded-full bg-green-50 text-green-600 text-xs">已解密</span>
            )}
          </Button>
          <Button variant="outline" onClick={handleEncryptFile} loading={encryptLoading}>
            <Lock className="h-4 w-4 mr-2" />
            {file.encryptionMode === 'CLIENT' ? '重新加密' : '加密'}
          </Button>
          <Button variant="ghost" onClick={handleEdit}>
            <Edit3 className="h-4 w-4 mr-2" />
            重命名
          </Button>
          <Button variant="danger" onClick={handleDelete}>
            <Trash2 className="h-4 w-4 mr-2" />
            删除
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card>
          <CardBody className="flex items-center gap-4 h-full">
            <div className="w-12 h-12 bg-primary-50 flex items-center justify-center">
              <FileText className="h-6 w-6 text-primary-500" />
            </div>
            <div className="flex-1 flex flex-col justify-center">
              <p className="text-sm text-text-secondary">文件类型</p>
              <p className="text-xl font-bold text-text-primary">{fileType.label}</p>
            </div>
          </CardBody>
        </Card>
        <Card>
          <CardBody className="flex items-center gap-4 h-full">
            <div className="w-12 h-12 bg-primary-50 flex items-center justify-center">
              <Shield className="h-6 w-6 text-primary-500" />
            </div>
            <div className="flex-1 flex flex-col justify-center">
              <p className="text-sm text-text-secondary">敏感级别</p>
              <p className="text-xl font-bold text-text-primary">{sensitiveLevel.label}</p>
            </div>
          </CardBody>
        </Card>
        <Card>
          <CardBody className="flex items-center gap-4 h-full">
            <div className="w-12 h-12 bg-primary-50 flex items-center justify-center">
              <Sparkles className="h-6 w-6 text-primary-500" />
            </div>
            <div className="flex-1 flex flex-col justify-center">
              <p className="text-sm text-text-secondary">AI状态</p>
              <p className="text-xl font-bold text-text-primary">{aiStatus.label}</p>
            </div>
          </CardBody>
        </Card>
        <Card>
          <CardBody className="flex items-center gap-4 h-full">
            <div className="w-12 h-12 bg-primary-50 flex items-center justify-center">
              <History className="h-6 w-6 text-primary-500" />
            </div>
            <div className="flex-1 flex flex-col justify-center">
              <p className="text-sm text-text-secondary">版本数</p>
              <p className="text-xl font-bold text-text-primary">{versions.length || file.version}</p>
            </div>
          </CardBody>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader className="flex items-center justify-between cursor-pointer" onClick={() => toggleSection('summary')}>
              <div className="flex items-center gap-2">
                <FileText className="h-5 w-5 text-accent-blue" />
                <h3 className="font-semibold text-text-primary">AI摘要</h3>
              </div>
              {expandedSections.summary ? <ChevronUp className="h-5 w-5" /> : <ChevronDown className="h-5 w-5" />}
            </CardHeader>
            {expandedSections.summary && (
              <CardBody>
                {file.summary ? (
                  <div>
                    <p className="text-neutral-700 leading-relaxed mb-4">{file.summary}</p>
                    <Button variant="outline" size="sm" onClick={handleGenerateSummary} loading={summaryLoading}>
                      <RefreshCw className="h-4 w-4 mr-2" />
                      重新生成
                    </Button>
                  </div>
                ) : (
                  <div className="text-center py-4">
                    <p className="text-text-secondary mb-3">等待AI分析生成摘要...</p>
                    <Button variant="outline" size="sm" onClick={handleGenerateSummary} loading={summaryLoading}>
                      <Sparkles className="h-4 w-4 mr-2" />
                      生成摘要
                    </Button>
                  </div>
                )}
              </CardBody>
            )}
          </Card>

          <Card>
            <CardHeader className="flex items-center justify-between cursor-pointer" onClick={() => toggleSection('qa')}>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-accent-blue" />
                <h3 className="font-semibold text-text-primary">智能问答</h3>
              </div>
              {expandedSections.qa ? <ChevronUp className="h-5 w-5" /> : <ChevronDown className="h-5 w-5" />}
            </CardHeader>
            {expandedSections.qa && (
              <CardBody className="space-y-4">
                <form onSubmit={handleAsk}>
                  <TextArea
                    placeholder="输入您的问题，AI将根据文档内容进行回答..."
                    value={question}
                    onChange={(e) => setQuestion(e.target.value)}
                    rows={3}
                    className="mb-3"
                  />
                  <Button type="submit" variant="primary" loading={qaLoading}>
                    提问
                  </Button>
                </form>

                {qaResponse && (
                  <div className="border border-neutral-200 rounded-lg p-4">
                    <p className="text-sm font-medium text-text-secondary mb-2">回答：</p>
                    <p className="text-neutral-700">{qaResponse.answer}</p>
                    {qaResponse.sourceSnippets && qaResponse.sourceSnippets.length > 0 && (
                      <div className="mt-4 pt-4 border-t border-neutral-200">
                        <p className="text-sm font-medium text-text-secondary mb-2">引用来源：</p>
                        <div className="space-y-2">
                          {qaResponse.sourceSnippets.map((snippet, idx) => (
                            <p key={idx} className="text-sm text-text-secondary">
                              {snippet.slice(0, 100)}...
                            </p>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </CardBody>
            )}
          </Card>

          <Card>
            <CardHeader className="flex items-center justify-between cursor-pointer" onClick={() => toggleSection('version')}>
              <div className="flex items-center gap-2">
                <History className="h-5 w-5 text-accent-blue" />
                <h3 className="font-semibold text-text-primary">版本历史</h3>
              </div>
              {expandedSections.version ? <ChevronUp className="h-5 w-5" /> : <ChevronDown className="h-5 w-5" />}
            </CardHeader>
            {expandedSections.version && (
              <CardBody>
                <div className="flex flex-wrap items-center gap-3 mb-4 pb-4 border-b border-neutral-100">
                  <Button variant="outline" size="sm" onClick={handleOpenUploadVersion} disabled={uploadingVersion}>
                    <Upload className="h-4 w-4 mr-2" />
                    上传新版本
                  </Button>
                  {isTextEditable(file) ? (
                    <Button variant="outline" size="sm" onClick={handleOpenTextEdit} disabled={textContentLoading}>
                      <Edit3 className="h-4 w-4 mr-2" />
                      {textContentLoading ? '读取中...' : '在线编辑'}
                    </Button>
                  ) : (
                    <p className="text-xs text-text-secondary">该格式不支持在线编辑，请下载后在本地修改并上传新版本</p>
                  )}
                </div>
                {versionsLoading ? (
                  <Loading text="加载版本历史..." />
                ) : versions.length > 0 ? (
                  <>
                    <div className="space-y-3 mb-4">
                      {versions.map((version) => (
                        <div key={version.version} className="flex items-center justify-between p-3 bg-neutral-50 rounded-lg">
                          <div className="flex items-center gap-4">
                            <div className="w-10 h-10 bg-neutral-200 rounded-lg flex items-center justify-center">
                              <span className="text-sm font-medium text-text-secondary">v{version.version}</span>
                            </div>
                            <div>
                              <p className="text-sm font-medium text-text-primary">
                                {version.comment || '更新'}
                                {version.version === file.version && (
                                  <span className="ml-2 px-2 py-0.5 rounded-full bg-accent-blue/10 text-accent-blue text-xs">当前版本</span>
                                )}
                              </p>
                              <p className="text-xs text-text-secondary">
                                {version.modifiedBy?.displayName || '未知用户'} · {formatDateTime(version.createdAt)}
                              </p>
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleSelectCompare(version.version)}
                              disabled={diffLoading}
                            >
                              {compareVersions.b !== null && compareVersions.a === version.version ? null : null}
                              {compareVersions.a === version.version ? '已选 v' + version.version : '对比'}
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleRestoreVersion(version)}
                              disabled={version.version === file.version}
                            >
                              <RefreshCw className="h-4 w-4 mr-1" />
                              恢复
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>

                    {compareVersions.a !== null && compareVersions.b === null && (
                      <p className="text-sm text-accent-blue text-center py-2">
                        已选 v{compareVersions.a}，请选择另一个版本进行对比
                      </p>
                    )}

                    {diffResult && (
                      <div className="border border-neutral-200 rounded-lg overflow-hidden">
                        <div className="p-3 bg-neutral-50 border-b border-neutral-200 flex items-center justify-between">
                          <div className="flex items-center gap-4 text-sm">
                            <span className="font-medium">v{diffResult.versionA} → v{diffResult.versionB}</span>
                            <span className="text-green-600">+{diffResult.additions}</span>
                            <span className="text-red-600">-{diffResult.deletions}</span>
                            <span className="text-yellow-600">~{diffResult.modifications}</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <div className="flex items-center gap-1 text-xs">
                              <select
                                value={diffResult.versionA}
                                onChange={(e) => handleDiffKeyChange(Number(e.target.value), diffResult.versionB)}
                                className="px-2 py-1 border border-neutral-200 rounded text-xs"
                              >
                                {versions.map((v) => (
                                  <option key={v.version} value={v.version}>v{v.version}</option>
                                ))}
                              </select>
                              <span>→</span>
                              <select
                                value={diffResult.versionB}
                                onChange={(e) => handleDiffKeyChange(diffResult.versionA, Number(e.target.value))}
                                className="px-2 py-1 border border-neutral-200 rounded text-xs"
                              >
                                {versions.map((v) => (
                                  <option key={v.version} value={v.version}>v{v.version}</option>
                                ))}
                              </select>
                            </div>
                            <button
                              onClick={handleCloseDiff}
                              className="p-1 text-text-secondary hover:text-text-primary"
                            >
                              ✕
                            </button>
                          </div>
                        </div>
                        {diffResult.diffFormat === 'binary' ? (
                          <div className="p-6 text-center text-text-secondary text-sm">
                            无法计算文本差异（该文件可能为二进制文件）
                          </div>
                        ) : (
                          <pre className="p-4 text-xs leading-relaxed overflow-x-auto max-h-80 overflow-y-auto bg-white font-mono">
                            {diffResult.diff.split('\n').slice(0, 200).map((line, i) => {
                              let bgColor = ''
                              let textColor = ''
                              if (line.startsWith('+')) {
                                bgColor = 'bg-green-50'
                                textColor = 'text-green-800'
                              } else if (line.startsWith('-')) {
                                bgColor = 'bg-red-50'
                                textColor = 'text-red-800'
                              } else if (line.startsWith('@@')) {
                                bgColor = 'bg-yellow-50'
                                textColor = 'text-yellow-700'
                              }
                              return (
                                <div key={i} className={`${bgColor} ${textColor} px-2 whitespace-pre`}>
                                  {line}
                                </div>
                              )
                            })}
                            {diffResult.diff.split('\n').length > 200 && (
                              <div className="text-center text-text-secondary py-2">
                                ...仅显示前 200 行差异...
                              </div>
                            )}
                          </pre>
                        )}
                      </div>
                    )}
                  </>
                ) : (
                  <p className="text-center py-8 text-text-secondary">暂无版本历史</p>
                )}
              </CardBody>
            )}
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <h3 className="font-semibold text-text-primary">文件信息</h3>
            </CardHeader>
            <CardBody className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-text-secondary flex items-center gap-2">
                  <Clock className="h-4 w-4" />
                  创建时间
                </span>
                <span className="text-text-primary">{formatDateTime(file.createdAt)}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-text-secondary flex items-center gap-2">
                  <Clock className="h-4 w-4" />
                  更新时间
                </span>
                <span className="text-text-primary">{formatDateTime(file.updatedAt)}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-text-secondary flex items-center gap-2">
                  <User className="h-4 w-4" />
                  创建者
                </span>
                <span className="text-text-primary">当前用户</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-text-secondary">版本</span>
                <span className="text-text-primary">v{file.version}</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-text-secondary">加密状态</span>
                <span className={file.isEncrypted ? 'text-green-500' : 'text-text-secondary'}>
                  {file.isEncrypted
                    ? file.encryptionMode === 'CLIENT' ? '端到端加密（文件密钥）' : '已加密'
                    : '未加密'}
                </span>
              </div>
            </CardBody>
          </Card>

          <Card>
            <CardHeader className="flex items-center justify-between cursor-pointer" onClick={() => toggleSection('security')}>
              <div className="flex items-center gap-2">
                <Shield className="h-5 w-5 text-accent-blue" />
                <h3 className="font-semibold text-text-primary">安全信息</h3>
              </div>
              {expandedSections.security ? <ChevronUp className="h-5 w-5" /> : <ChevronDown className="h-5 w-5" />}
            </CardHeader>
            {expandedSections.security && (
              <CardBody className="space-y-4">
                <div className="flex items-center justify-between mb-4">
                  <span className="text-sm text-text-secondary">敏感级别</span>
                  <span className="px-2 py-1 rounded-full text-sm" style={{ backgroundColor: sensitiveLevel.bgColor, color: sensitiveLevel.color }}>
                    {sensitiveLevel.label}
                  </span>
                </div>
                {securityLoading && (
                  <div className="flex items-center justify-center py-2">
                    <RefreshCw className="h-4 w-4 animate-spin text-accent-blue mr-2" />
                    <span className="text-sm text-text-secondary">正在扫描敏感信息...</span>
                  </div>
                )}
                {!securityLoading && securityItems.length > 0 && (
                  <div className="space-y-2 mb-4">
                    <p className="text-sm font-medium text-text-primary">检测到的敏感内容：</p>
                    {securityItems.map((item, idx) => (
                      <div key={idx} className="p-3 bg-neutral-50 rounded-lg border border-neutral-200">
                        <div className="flex items-center justify-between mb-1">
                          <span className="text-sm font-medium text-text-primary">
                            {item.type === 'id_card' && '身份证号'}
                            {item.type === 'phone' && '手机号'}
                            {item.type === 'bank_card' && '银行卡号'}
                            {item.type === 'email' && '邮箱'}
                            {item.type === 'address' && '地址'}
                            {item.type === 'company_secret' && '公司机密'}
                            {!['id_card', 'phone', 'bank_card', 'email', 'address', 'company_secret'].includes(item.type) && (item.type || '其他')}
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
                        <p className="text-sm text-text-secondary font-mono">{item.content}</p>
                      </div>
                    ))}
                  </div>
                )}
                <Button variant="outline" size="sm" className="w-full" onClick={handleDetectSecurity} loading={securityLoading}>
                  <Shield className="h-4 w-4 mr-2" />
                  {securityLoading ? '安全检测中...' : '安全检测'}
                </Button>
              </CardBody>
            )}
          </Card>

          <Card>
            <CardHeader>
              <h3 className="font-semibold text-text-primary">标签 / 分类</h3>
            </CardHeader>
            <CardBody>
              {(() => { try { const tags = JSON.parse(file.tags); return Array.isArray(tags) && tags.length > 0; } catch { return false; } })() ? (
                <div className="flex flex-wrap gap-2 mb-3">
                  {(JSON.parse(file.tags) as string[]).map((tag) => (
                    <button
                      key={tag}
                      onClick={(e) => { e.stopPropagation(); handleRemoveTag(tag); }}
                      className="px-2 py-1 bg-accent-blue/10 text-accent-blue text-sm rounded-full flex items-center gap-1 hover:bg-accent-blue/20 cursor-pointer transition-colors"
                      title="点击移除标签"
                    >
                      <Tag className="h-3 w-3" />
                      {tag}
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-text-secondary mb-3">暂无标签，点击下方按钮进行 AI 分类</p>
              )}
              <Button
                variant="outline"
                size="sm"
                className="w-full"
                onClick={handleClassify}
                disabled={classifyLoading}
              >
                <Brain className="h-4 w-4 mr-2" />
                {classifyLoading ? '分类中...' : 'AI 分类'}
              </Button>
            </CardBody>
          </Card>
        </div>
      </div>

      <Modal isOpen={showEditModal} onClose={() => setShowEditModal(false)} title="重命名文件">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">文件名</label>
            <input
              type="text"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="w-full px-3 py-2 border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
              placeholder="输入新的文件名"
            />
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setShowEditModal(false)}>
              取消
            </Button>
            <Button variant="primary" onClick={handleEditSave} loading={editSaving}>
              <Save className="h-4 w-4 mr-2" />
              保存
            </Button>
          </div>
        </div>
      </Modal>

      <Modal isOpen={showUploadVersionModal} onClose={() => setShowUploadVersionModal(false)} title="上传新版本">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">选择文件</label>
            <input
              type="file"
              onChange={(e) => setUploadVersionFile(e.target.files?.[0] || null)}
              className="block w-full text-sm text-text-secondary file:mr-3 file:px-3 file:py-2 file:rounded-button file:border-0 file:bg-primary-50 file:text-primary-500 file:cursor-pointer"
            />
            {uploadVersionFile && (
              <p className="mt-2 text-sm text-text-secondary">已选择：{uploadVersionFile.name}（{formatFileSize(uploadVersionFile.size)}）</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">版本备注</label>
            <input
              type="text"
              value={uploadVersionComment}
              onChange={(e) => setUploadVersionComment(e.target.value)}
              className="w-full px-3 py-2 border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
              placeholder="例如：更新了第 3 章内容（选填）"
            />
          </div>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setShowUploadVersionModal(false)}>
              取消
            </Button>
            <Button variant="primary" onClick={handleUploadVersion} loading={uploadingVersion} disabled={!uploadVersionFile}>
              <Upload className="h-4 w-4 mr-2" />
              上传
            </Button>
          </div>
        </div>
      </Modal>

      <Modal isOpen={showTextEditModal} onClose={() => setShowTextEditModal(false)} title={`在线编辑 - ${file.name}`} size="lg">
        <div className="space-y-4">
          <TextArea
            label="文件内容"
            value={textContent}
            onChange={(e) => setTextContent(e.target.value)}
            rows={16}
            className="font-mono"
            placeholder="在此修改文本内容，保存后会生成新版本"
          />
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1">版本备注</label>
            <input
              type="text"
              value={textComment}
              onChange={(e) => setTextComment(e.target.value)}
              className="w-full px-3 py-2 border border-neutral-200 bg-white focus:outline-none focus:border-accent-blue"
              placeholder="例如：修正错别字（选填）"
            />
          </div>
          <p className="text-xs text-text-secondary">保存后将创建新版本，原版本会保留在版本历史中。</p>
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setShowTextEditModal(false)}>
              取消
            </Button>
            <Button variant="primary" onClick={handleSaveTextVersion} loading={textSaving}>
              <Save className="h-4 w-4 mr-2" />
              保存为新版本
            </Button>
          </div>
        </div>
      </Modal>

      <Modal isOpen={showEncryptKeyModal} onClose={() => setShowEncryptKeyModal(false)} title="文件密钥（仅显示一次）">
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
              <Button variant="primary" onClick={() => { setShowEncryptKeyModal(false); setOneTimeKey(null) }}>
                我已保存密钥
              </Button>
            </div>
          </div>
        )}
      </Modal>

      <Modal isOpen={showKeyPrompt} onClose={() => { setShowKeyPrompt(false); setPendingUploadVersion(null) }} title="输入文件密钥">
        <div className="space-y-4">
          <p className="text-sm text-text-secondary">该文件使用专属密钥加密，请输入加密上传时显示的密钥。</p>
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
            <Button variant="ghost" onClick={() => { setShowKeyPrompt(false); setPendingUploadVersion(null) }}>
              取消
            </Button>
            <Button variant="primary" onClick={handleConfirmKeyPrompt} loading={keyPromptLoading}>
              确认
            </Button>
          </div>
        </div>
      </Modal>

    </div>
  )
}
