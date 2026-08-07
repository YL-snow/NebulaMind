export const FILE_TYPES = {
  pdf: { icon: 'FileText', label: 'PDF文档', color: '#ef4444' },
  docx: { icon: 'FileText', label: 'Word文档', color: '#3b82f6' },
  xlsx: { icon: 'Table', label: 'Excel表格', color: '#22c55e' },
  pptx: { icon: 'Presentation', label: 'PPT演示', color: '#f59e0b' },
  jpg: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  jpeg: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  png: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  gif: { icon: 'Image', label: '动图', color: '#ec4899' },
  txt: { icon: 'FileText', label: '文本文件', color: '#6b7280' },
  zip: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  default: { icon: 'File', label: '其他文件', color: '#6b7280' },
}

export const SENSITIVE_LEVELS = {
  high: { label: '高敏感', color: '#9e3131', bgColor: '#fee2e2' },
  medium: { label: '中敏感', color: '#eba834', bgColor: '#fef3c7' },
  low: { label: '低敏感', color: '#4169a1', bgColor: '#dbeafe' },
  normal: { label: '正常', color: '#205f2d', bgColor: '#f0f6f0' },
}

export const AI_STATUS = {
  pending: { label: '待处理', color: '#6b7280' },
  processing: { label: '处理中', color: '#3b82f6' },
  completed: { label: '已完成', color: '#22c55e' },
  failed: { label: '失败', color: '#ef4444' },
}

export const REPORT_TYPES = [
  { value: 'analysis', label: '分析报告' },
  { value: 'summary', label: '总结报告' },
  { value: 'meeting', label: '会议纪要' },
]

export const GENERATE_STYLES = [
  { value: 'formal', label: '正式' },
  { value: 'concise', label: '简洁' },
  { value: 'detailed', label: '详细' },
]

export const SUMMARY_STYLES = [
  { value: 'concise', label: '简洁' },
  { value: 'detailed', label: '详细' },
  { value: 'bullet', label: '要点列表' },
]

export const EXTRACT_TYPES = [
  { value: 'keywords', label: '关键词' },
  { value: 'keypoints', label: '关键要点' },
  { value: 'entities', label: '实体' },
]