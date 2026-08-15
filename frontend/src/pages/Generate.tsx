import { useState } from 'react'
import { FileText, Sparkles, Download, CheckCircle, Loader2 } from 'lucide-react'
import { Button } from '@/components/common/Button'
import { Card, CardHeader, CardBody } from '@/components/common/Card'

import { useToast } from '@/components/common/Toast'
import { generateApi } from '@/api/generate'
import { useFileStore } from '@/stores/fileStore'
import { GENERATE_STYLES, SUMMARY_STYLES, EXTRACT_TYPES, REPORT_TYPES, ARCHIVE_FILE_TYPES } from '@/utils/constants'
import { formatFileSize } from '@/utils/format'

export const Generate = () => {
  const [activeTab, setActiveTab] = useState<'summary' | 'extract' | 'report'>('summary')
  const [selectedFiles, setSelectedFiles] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [results, setResults] = useState<Record<string, string>>({
    summary: '',
    extract: '',
    report: '',
  })
  const [summaryStyle, setSummaryStyle] = useState('concise')
  const [extractType, setExtractType] = useState('keywords')
  const [reportType, setReportType] = useState('analysis')
  const [reportStyle, setReportStyle] = useState('formal')
  const [progress, setProgress] = useState(0)
  const [progressText, setProgressText] = useState('')

  const { files } = useFileStore()
  const { error, success } = useToast()

  const handleFileSelect = (fileId: string) => {
    setSelectedFiles((prev) =>
      prev.includes(fileId) ? prev.filter((id) => id !== fileId) : [...prev, fileId]
    )
  }

  const handleGenerate = async () => {
    if (selectedFiles.length === 0) {
      error('请选择至少一个文件')
      return
    }

    const selectedItems = files.filter((file) => selectedFiles.includes(file.id))
    if (selectedItems.some((file) => ARCHIVE_FILE_TYPES.includes(file.fileType?.toLowerCase()))) {
      error('压缩包不支持直接使用 AI 生成，请先解压后上传文件再试')
      return
    }

    setLoading(true)
    setResults((prev) => ({ ...prev, [activeTab]: '' }))
    setProgress(0)
    setProgressText('开始生成...')

    const updateProgress = (step: number, text: string) => {
      setProgress(step)
      setProgressText(text)
    }

    try {
      updateProgress(10, '正在请求生成服务...')

      if (activeTab === 'summary') {
        updateProgress(30, '正在生成摘要...')
        const response = await generateApi.summary({
          fileId: selectedFiles[0],
          style: summaryStyle as 'concise' | 'detailed' | 'bullet',
        })

        updateProgress(90, '整理结果...')
        setResults((prev) => ({ ...prev, [activeTab]: response.content }))
        success('摘要生成完成')
      } else if (activeTab === 'extract') {
        updateProgress(40, '正在提取关键信息...')
        const response = await generateApi.extract({
          fileId: selectedFiles[0],
          extractType: extractType as 'keywords' | 'keypoints' | 'entities',
        })

        updateProgress(90, '整理结果...')
        setResults((prev) => ({ ...prev, [activeTab]: response.content || (response.keyPoints?.join('\n- ') ?? '') }))
        success('提取完成')
      } else if (activeTab === 'report') {
        updateProgress(30, '正在生成报告...')
        const response = await generateApi.report({
          fileIds: selectedFiles,
          reportType: reportType as 'analysis' | 'summary' | 'meeting',
          style: reportStyle as 'formal' | 'concise' | 'detailed',
        })

        updateProgress(90, '格式化报告...')
        setResults((prev) => ({ ...prev, [activeTab]: response.content }))
        success('报告生成完成')
      }

      updateProgress(100, '生成完成')
    } catch (err) {
      const msg = (err as any)?.response?.data?.message || (err as Error).message || '生成失败'
      error(msg)
    } finally {
      setLoading(false)
      setProgress(0)
      setProgressText('')
    }
  }

  const handleDownload = () => {
    const currentResult = results[activeTab]
    if (!currentResult) return

    const blob = new Blob([currentResult], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${activeTab}-output.txt`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    success('文件已下载')
  }

  const tabs = [
    { key: 'summary', label: '摘要生成', icon: FileText },
    { key: 'extract', label: '信息提取', icon: Sparkles },
    { key: 'report', label: '报告生成', icon: FileText },
  ]

  return (
    <div className="h-[calc(100vh-112px)] grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div className="lg:col-span-1 overflow-y-auto scrollbar-hover space-y-6">
        <Card>
          <CardHeader>
            <h3 className="font-semibold text-text-primary">选择文件</h3>
          </CardHeader>
          <CardBody>
            <div className="space-y-2 max-h-[216px] overflow-y-auto">
              {files.length > 0 ? (
                files.map((file) => (
                  <div
                    key={file.id}
                    onClick={() => handleFileSelect(file.id)}
                    className={`flex items-center gap-3 p-3 rounded-button cursor-pointer transition-colors ${
                      selectedFiles.includes(file.id)
                        ? 'bg-accent-blue/10 border-2 border-accent-blue'
                        : 'bg-neutral-50 border-2 border-transparent hover:border-neutral-200'
                    }`}
                  >
                    <div className="w-10 h-10 bg-neutral-200 rounded-button flex items-center justify-center">
                      <FileText className="h-5 w-5 text-text-secondary" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-text-primary truncate">{file.name}</p>
                      <p className="text-xs text-text-secondary">{formatFileSize(file.size)}</p>
                    </div>
                    {selectedFiles.includes(file.id) && (
                      <CheckCircle className="h-5 w-5 text-accent-blue" />
                    )}
                  </div>
                ))
              ) : (
                <p className="text-center text-sm text-text-secondary py-8">暂无文件</p>
              )}
            </div>
          </CardBody>
        </Card>

        {activeTab === 'summary' && (
          <Card>
            <CardHeader>
              <h3 className="font-semibold text-text-primary">摘要样式</h3>
            </CardHeader>
            <CardBody>
              <div className="space-y-2">
                {SUMMARY_STYLES.map((style) => (
                  <button
                    key={style.value}
                    onClick={() => setSummaryStyle(style.value)}
                    className={`w-full p-3 text-left rounded-button transition-colors ${
                      summaryStyle === style.value
                        ? 'bg-accent-blue/10 border border-accent-blue/30'
                        : 'bg-neutral-50 border border-transparent hover:border-neutral-200'
                    }`}
                  >
                    <p className="font-medium text-text-primary">{style.label}</p>
                  </button>
                ))}
              </div>
            </CardBody>
          </Card>
        )}

        {activeTab === 'extract' && (
          <Card>
            <CardHeader>
              <h3 className="font-semibold text-text-primary">提取类型</h3>
            </CardHeader>
            <CardBody>
              <div className="space-y-2">
                {EXTRACT_TYPES.map((type) => (
                  <button
                    key={type.value}
                    onClick={() => setExtractType(type.value)}
                    className={`w-full p-3 text-left rounded-button transition-colors ${
                      extractType === type.value
                        ? 'bg-accent-blue/10 border border-accent-blue/30'
                        : 'bg-neutral-50 border border-transparent hover:border-neutral-200'
                    }`}
                  >
                    <p className="font-medium text-text-primary">{type.label}</p>
                  </button>
                ))}
              </div>
            </CardBody>
          </Card>
        )}

        {activeTab === 'report' && (
          <>
            <Card>
              <CardHeader>
                <h3 className="font-semibold text-text-primary">报告类型</h3>
              </CardHeader>
              <CardBody>
                <div className="space-y-2">
                  {REPORT_TYPES.map((type) => (
                    <button
                      key={type.value}
                      onClick={() => setReportType(type.value)}
                      className={`w-full p-3 text-left rounded-button transition-colors ${
                      reportType === type.value
                        ? 'bg-accent-blue/10 border border-accent-blue/30'
                        : 'bg-neutral-50 border border-transparent hover:border-neutral-200'
                    }`}
                  >
                    <p className="font-medium text-text-primary">{type.label}</p>
                    </button>
                  ))}
                </div>
              </CardBody>
            </Card>
            <Card>
              <CardHeader>
                <h3 className="font-semibold text-text-primary">报告风格</h3>
              </CardHeader>
              <CardBody>
                <div className="space-y-2">
                  {GENERATE_STYLES.map((style) => (
                    <button
                      key={style.value}
                      onClick={() => setReportStyle(style.value)}
                      className={`w-full p-3 text-left rounded-button transition-colors ${
                      reportStyle === style.value
                        ? 'bg-accent-blue/10 border border-accent-blue/30'
                        : 'bg-neutral-50 border border-transparent hover:border-neutral-200'
                    }`}
                  >
                    <p className="font-medium text-text-primary">{style.label}</p>
                    </button>
                  ))}
                </div>
              </CardBody>
            </Card>
          </>
        )}
      </div>

      <div className="lg:col-span-2 flex flex-col h-full gap-6">
        <div className="flex bg-neutral-100 rounded-button p-1 flex-shrink-0">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key as typeof activeTab)}
              className={`flex-1 flex items-center justify-center gap-2 py-3 rounded-button text-sm font-medium transition-all ${
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

        <Card className="flex-1 min-h-0 flex flex-col">
          <CardHeader className="flex-shrink-0 flex items-center justify-between">
            <h3 className="font-semibold text-text-primary">生成结果</h3>
            {results[activeTab] && (
              <Button variant="outline" size="sm" onClick={handleDownload}>
                <Download className="h-4 w-4 mr-2" />
                下载
              </Button>
            )}
          </CardHeader>
          <CardBody className="flex-1 min-h-0">
            {loading ? (
              <div className="h-full flex flex-col items-center justify-center">
                <div className="flex flex-col items-center">
                  <Loader2 className="h-8 w-8 text-accent-blue animate-spin mb-4" />
                  <p className="text-text-secondary mb-4">{progressText}</p>
                  <div className="w-full max-w-md h-2 bg-neutral-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-accent-blue rounded-full transition-all duration-300"
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                  <p className="text-sm text-text-secondary mt-2">{progress}%</p>
                </div>
              </div>
            ) : results[activeTab] ? (
              <pre className="h-full overflow-y-auto scrollbar-hover whitespace-pre-wrap text-text-primary font-sans leading-relaxed">
                {results[activeTab]}
              </pre>
            ) : (
              <div className="h-full flex flex-col items-center justify-center">
                <p className="text-text-secondary">选择文件并点击生成按钮开始</p>
              </div>
            )}
          </CardBody>
        </Card>

        <Button variant="amber" size="lg" className="w-full flex-shrink-0" loading={loading} onClick={handleGenerate}>
          开始生成
        </Button>
      </div>
    </div>
  )
}
