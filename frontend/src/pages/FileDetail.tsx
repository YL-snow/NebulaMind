import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Download, Edit3, Trash2, Clock, User, Tag, Shield, FileText, Sparkles, ChevronDown, ChevronUp, RefreshCw, History, Brain, Save } from 'lucide-react'
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
import { FILE_TYPES, SENSITIVE_LEVELS, AI_STATUS } from '@/utils/constants'
import { ARCHIVE_FILE_TYPES } from '@/utils/constants'
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
      navigate('/')
    } catch (err) {
      error((err as Error).message || '删除文件失败')
    }
  }

  const handleDownload = async () => {
    if (!file) return
    try {
      const response: Blob = await filesApi.download(file.id) as unknown as Blob
      const url = window.URL.createObjectURL(new Blob([response]))
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

  const handleApplyTag = async (tag: string) => {
    if (!file) return
    try {
      let currentTags: string[] = []
      try { currentTags = JSON.parse(file.tags || '[]') } catch { currentTags = [] }
      if (!Array.isArray(currentTags)) currentTags = []
      if (!currentTags.includes(tag)) {
        currentTags.push(tag)
        const newTags = JSON.stringify(currentTags)
        await filesApi.update(file.id, { tags: newTags })
        setFile((prev) => prev ? { ...prev, tags: newTags } : null)
        success(`标签 "${tag}" 已应用`)
      }
    } catch (err) {
      error((err as Error).message || '应用标签失败')
    }
  }

  const handleGenerateSummary = async () => {
    if (!file) return
    if (ARCHIVE_FILE_TYPES.includes(file.fileType?.toLowerCase())) {
      error('压缩包不支持直接生成摘要，请先解压后上传文件再试')
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
      await filesApi.restoreVersion(file.id, version.version)
      setFile((prev) => prev ? { ...prev, version: version.version } : null)
      fetchVersionHistory(file.id)
      success(`文件已恢复到版本 ${version.version}`)
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
            onClick={() => navigate('/')}
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
          <Button variant="ghost" onClick={handleDownload}>
            <Download className="h-4 w-4 mr-2" />
            下载
          </Button>
          <Button variant="ghost" onClick={handleEdit}>
            <Edit3 className="h-4 w-4 mr-2" />
            编辑
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
                              <p className="text-sm font-medium text-text-primary">{version.comment || '更新'}</p>
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
                  {file.isEncrypted ? '已加密' : '未加密'}
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
                      onClick={(e) => { e.stopPropagation(); handleApplyTag(tag); }}
                      className="px-2 py-1 bg-accent-blue/10 text-accent-blue text-sm rounded-full flex items-center gap-1 hover:bg-accent-blue/20 cursor-pointer transition-colors"
                      title="点击应用此标签"
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

    </div>
  )
}
