export interface User {
  id: string
  username: string
  displayName: string
  role: string
  avatar?: string
}

export interface AuthResponse {
  userId: string
  email: string
  displayName: string
  role: string
  accessToken: string
  refreshToken: string
}

export type RegisterResponse = AuthResponse

export interface FileItem {
  id: string
  name: string
  path: string
  size: number
  mimeType: string
  fileType: string
  hash: string
  tags: string           // JSON 字符串：'["tag1","tag2"]'
  category: string
  summary?: string
  sensitiveLevel: 'high' | 'medium' | 'low' | 'normal'
  isEncrypted: boolean
  encryptionKeyId?: string
  encryptionMode?: 'NONE' | 'SERVER' | 'CLIENT'
  aiStatus: 'pending' | 'processing' | 'completed' | 'failed' | 'skipped'
  version: number
  createdAt: string
  updatedAt: string
}

export interface AIResult {
  tags: string[]
  category: string
  summary: string
  keywords: string[]
  sensitiveItems: SensitiveItem[]
}

export interface SensitiveItem {
  type: 'id_card' | 'phone' | 'bank_card' | 'email' | 'address' | 'company_secret' | 'other'
  content: string
  position: string
  riskLevel: 'high' | 'medium' | 'low'
}

export interface FileListResponse {
  content: FileItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface UploadResponse {
  fileId: string
  fileName: string
  uploadId?: string
  completed?: boolean
}

export interface MultipartInitResponse {
  uploadId: string
  chunkSize: number
  chunkCount: number
}

export interface MultipartChunkResponse {
  uploadId: string
  chunkIndex: number
  etag: string
}

export interface MultipartCompleteResponse {
  id: string
  name: string
  size: number
  status: string
  createdAt: string
}

export interface ClassifyResponse {
  fileId: string
  category: string
  tags: string[]
  confidence: number
  processingTime: number
}

export interface DuplicateFileInfo {
  id: string
  name: string
  size: number
}

export interface DuplicateGroup {
  hash: string
  files: DuplicateFileInfo[]
}

export interface SearchRequest {
  query: string
  page?: number
  pageSize?: number
  category?: string
  tags?: string[]
  fileTypes?: string[]
}

export interface SearchResultItem {
  fileId: string
  fileName: string
  fileType: string
  size: number
  relevance: number
  summary: string
  highlights: string[]
  matchedChunks: MatchedChunk[]
}

export interface MatchedChunk {
  content: string
  score: number
  page?: number
}

export interface SearchResponse {
  query: string
  items: SearchResultItem[]
  totalCount: number
  page: number
  pageSize: number
}

export interface QARequest {
  question: string
  fileId?: string
  fileIds?: string[]
  stream?: boolean
}

export interface QASource {
  fileId: string
  fileName: string
  chunkContent: string
  relevance: number
  page?: number
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface QAResponse {
  question: string
  answer: string
  sourceSnippets: string[]
  sourceFileId: string
  confidence: number
}

export interface GenerateSummaryRequest {
  fileId: string
  maxLength?: number
  style?: 'concise' | 'detailed' | 'bullet'
}

export interface GenerateSummaryResponse {
  fileId: string
  content: string
  keyPoints: string[]
  format: string
}

export interface GenerateExtractRequest {
  fileId: string
  extractType: 'keywords' | 'keypoints' | 'entities'
  maxItems?: number
}

export interface GenerateExtractResponse {
  fileId: string
  content: string
  keyPoints: string[]
  format: string
}

export interface GenerateReportRequest {
  fileIds: string[]
  reportType: 'analysis' | 'summary' | 'meeting'
  title?: string
  style?: 'formal' | 'concise' | 'detailed'
  format?: 'markdown' | 'html'
}

export interface GenerateReportResponse {
  fileId: string
  content: string
  keyPoints: string[]
  format: string
}

export interface SecurityDetectResponse {
  fileId: string
  sensitiveLevel: 'high' | 'medium' | 'low' | 'normal'
  sensitiveItems: SensitiveItem[]
  scannedAt: string
  detectionMethod: string
  warning?: string
  message?: string
  autoEncrypted: boolean
}

export interface SecurityEncryptResponse {
  fileId: string
  isEncrypted: boolean
  encryptedAt: string
  keyId: string
  encryptionMode?: 'NONE' | 'SERVER' | 'CLIENT'
}

export interface SecurityDecryptResponse {
  fileId: string
  isEncrypted: boolean
  decryptedAt: string
  encryptionMode?: 'NONE' | 'SERVER' | 'CLIENT'
}

export interface VersionItem {
  version: number
  fileSize: number
  modifiedBy: User
  comment: string
  createdAt: string
}

export interface VersionHistoryResponse {
  fileId: string
  versions: VersionItem[]
}

export interface SSEEvent {
  eventType: 'file.processing' | 'file.progress' | 'file.completed' | 'file.failed' | 'qa.stream' | 'qa.done'
  data: {
    fileId: string
    status?: string
    progress?: number
    aiResult?: AIResult
    error?: string
    chunk?: string
    done?: boolean
  }
  eventId: string
}


