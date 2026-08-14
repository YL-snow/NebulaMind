export const FILE_TYPES = {
  pdf: { icon: 'FileText', label: 'PDF文档', color: '#ef4444' },
  doc: { icon: 'FileText', label: 'Word文档', color: '#3b82f6' },
  docx: { icon: 'FileText', label: 'Word文档', color: '#3b82f6' },
  word: { icon: 'FileText', label: 'Word文档', color: '#3b82f6' },
  xls: { icon: 'Table', label: 'Excel表格', color: '#22c55e' },
  xlsx: { icon: 'Table', label: 'Excel表格', color: '#22c55e' },
  excel: { icon: 'Table', label: 'Excel表格', color: '#22c55e' },
  spreadsheet: { icon: 'Table', label: 'Excel表格', color: '#22c55e' },
  csv: { icon: 'Table', label: '数据表格', color: '#22c55e' },
  ppt: { icon: 'Presentation', label: 'PPT演示', color: '#f59e0b' },
  pptx: { icon: 'Presentation', label: 'PPT演示', color: '#f59e0b' },
  presentation: { icon: 'Presentation', label: 'PPT演示', color: '#f59e0b' },
  jpg: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  jpeg: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  png: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  gif: { icon: 'Image', label: '动图', color: '#ec4899' },
  bmp: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  webp: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  tiff: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  image: { icon: 'Image', label: '图片', color: '#8b5cf6' },
  txt: { icon: 'FileText', label: '文本文件', color: '#6b7280' },
  md: { icon: 'FileText', label: 'Markdown文档', color: '#6b7280' },
  zip: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  rar: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  '7z': { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  gz: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  tar: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  bz2: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  xz: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  tgz: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  archive: { icon: 'Archive', label: '压缩文件', color: '#6366f1' },
  document: { icon: 'File', label: '文档', color: '#6b7280' },
  default: { icon: 'File', label: '其他文件', color: '#6b7280' },
}

export const ARCHIVE_FILE_TYPES = ['zip', 'rar', '7z', 'gz', 'tar', 'bz2', 'xz', 'tgz', 'archive']

export const TEXT_EDITABLE_EXTENSIONS = [
  'txt', 'md', 'markdown', 'log', 'csv', 'json', 'xml', 'yml', 'yaml',
  'ini', 'conf', 'properties', 'sql', 'html', 'htm', 'css', 'js', 'ts',
  'jsx', 'tsx', 'java', 'kt', 'py', 'c', 'cpp', 'h', 'hpp', 'sh',
  'bat', 'ps1', 'env', 'gitignore',
]

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
  skipped: { label: '需先解压', color: '#6366f1' },
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
