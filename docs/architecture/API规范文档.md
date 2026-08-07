# API规范文档

**文档版本**：v1.0  
**创建日期**：2026年7月11日  
**所属团队**：云盘智能体开发团队  

---

## 1. API设计总则

### 1.1 设计原则

| 原则 | 说明 |
|------|------|
| **RESTful风格** | 资源导向设计，使用HTTP方法表示操作 |
| **统一前缀** | 所有API以 `/api/v1/` 为前缀 |
| **JSON格式** | 请求体和响应体统一使用JSON |
| **无状态** | 服务端不保存客户端状态，认证信息通过Token传递 |
| **版本控制** | URL路径版本号，向后兼容 |
| **统一响应格式** | 所有接口返回统一的数据结构 |

### 1.2 API命名规范

| 规范 | 规则 | 示例 |
|------|------|------|
| 路径 | 小写字母，单词间用连字符(-)分隔 | `/api/v1/file-list` |
| 参数 | 驼峰命名(camelCase) | `?pageSize=20` |
| 请求体 | 驼峰命名(camelCase) | `{"fileId": "uuid"}` |
| 响应体 | 驼峰命名(camelCase) | `{"totalCount": 100}` |

### 1.3 HTTP方法语义

| 方法 | 语义 | 幂等 | 安全 | 示例 |
|------|------|------|------|------|
| GET | 查询资源 | 是 | 是 | 获取文件列表 |
| POST | 创建资源/执行操作 | 否 | 否 | 上传文件 |
| PUT | 完整更新资源 | 是 | 否 | 更新文件信息 |
| PATCH | 部分更新资源 | 否 | 否 | 更新文件标签 |
| DELETE | 删除资源 | 是 | 否 | 删除文件 |

---

## 2. 统一响应格式

### 2.1 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "requestId": "req-uuid",
  "timestamp": "2026-07-11T10:00:00Z"
}
```

### 2.2 错误响应

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": {
    "errors": [
      {
        "field": "fileName",
        "message": "文件名不能为空"
      }
    ]
  },
  "requestId": "req-uuid",
  "timestamp": "2026-07-11T10:00:00Z"
}
```

### 2.3 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "totalCount": 100,
    "totalPages": 5
  },
  "requestId": "req-uuid",
  "timestamp": "2026-07-11T10:00:00Z"
}
```

### 2.4 错误码定义

| 错误码 | 说明 | 处理方式 |
|--------|------|---------|
| 200 | 成功 | - |
| 400 | 请求参数错误 | 检查请求参数 |
| 401 | 未认证 | 重新登录获取Token |
| 403 | 无权限 | 检查用户权限 |
| 404 | 资源不存在 | 检查资源ID |
| 409 | 资源冲突 | 处理冲突后重试 |
| 413 | 请求体过大 | 减小请求体大小 |
| 429 | 请求频率过高 | 等待后重试 |
| 500 | 服务器内部错误 | 联系运维 |
| 502 | 上游服务不可用 | 稍后重试 |
| 503 | 服务暂时不可用 | 稍后重试 |

---

## 3. 文件管理接口

### 3.1 上传文件

```
POST /api/v1/files/upload
```

**请求**：`multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传的文件 |
| parentId | String | 否 | 父目录ID |
| encrypt | Boolean | 否 | 是否加密存储，默认false |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "file-uuid",
    "name": "document.pdf",
    "path": "/documents/document.pdf",
    "size": 1024000,
    "mimeType": "application/pdf",
    "hash": "sha256-hash",
    "status": "uploading",
    "createdAt": "2026-07-11T10:00:00Z"
  }
}
```

### 3.2 分片上传初始化

```
POST /api/v1/files/upload/multipart-init
```

**请求**：

```json
{
  "fileName": "large-file.zip",
  "fileSize": 104857600,
  "mimeType": "application/zip",
  "chunkCount": 10,
  "parentId": "parent-uuid"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "uploadId": "upload-uuid",
    "chunkSize": 10485760,
    "chunkCount": 10
  }
}
```

### 3.3 上传分片

```
POST /api/v1/files/upload/multipart-chunk
```

**请求**：`multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| uploadId | String | 是 | 分片上传ID |
| chunkIndex | Integer | 是 | 分片索引(0-based) |
| file | File | 是 | 分片文件内容 |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "uploadId": "upload-uuid",
    "chunkIndex": 0,
    "etag": "chunk-etag"
  }
}
```

### 3.4 合并分片

```
POST /api/v1/files/upload/multipart-complete
```

**请求**：

```json
{
  "uploadId": "upload-uuid"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "file-uuid",
    "name": "large-file.zip",
    "size": 104857600,
    "status": "processing",
    "createdAt": "2026-07-11T10:00:00Z"
  }
}
```

### 3.5 获取文件列表

```
GET /api/v1/files?page=1&pageSize=20&parentId=&sortBy=createdAt&sortOrder=desc&category=&tag=
```

**参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|-------|------|
| page | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页数量 |
| parentId | String | 否 | - | 父目录ID |
| sortBy | String | 否 | createdAt | 排序字段 |
| sortOrder | String | 否 | desc | 排序方向 |
| category | String | 否 | - | 分类筛选 |
| tag | String | 否 | - | 标签筛选 |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "file-uuid",
        "name": "document.pdf",
        "size": 1024000,
        "mimeType": "application/pdf",
        "type": "pdf",
        "tags": ["报告", "财务"],
        "category": "财务报告",
        "status": "completed",
        "sensitiveLevel": "normal",
        "createdAt": "2026-07-11T10:00:00Z",
        "updatedAt": "2026-07-11T10:05:00Z"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "totalCount": 100,
    "totalPages": 5
  }
}
```

### 3.6 获取文件详情

```
GET /api/v1/files/{fileId}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "file-uuid",
    "name": "document.pdf",
    "path": "/documents/document.pdf",
    "size": 1024000,
    "mimeType": "application/pdf",
    "type": "pdf",
    "hash": "sha256-hash",
    "tags": ["报告", "财务"],
    "category": "财务报告",
    "summary": "本报告总结了公司2024年度的财务状况...",
    "sensitiveLevel": "normal",
    "isEncrypted": false,
    "aiStatus": "completed",
    "aiResult": {
      "tags": ["报告", "财务"],
      "category": "财务报告",
      "summary": "本报告总结了公司2024年度的财务状况...",
      "keywords": ["营收", "利润", "成本"],
      "sensitiveItems": []
    },
    "version": 1,
    "createdAt": "2026-07-11T10:00:00Z",
    "updatedAt": "2026-07-11T10:05:00Z",
    "createdBy": {
      "id": "user-uuid",
      "name": "用户名"
    }
  }
}
```

### 3.7 删除文件

```
DELETE /api/v1/files/{fileId}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 3.8 下载文件

```
GET /api/v1/files/{fileId}/download
```

**响应**：文件二进制流

| 响应头 | 值 |
|--------|-----|
| Content-Type | application/octet-stream |
| Content-Disposition | attachment; filename="document.pdf" |
| Content-Length | 1024000 |

### 3.9 智能分类

```
POST /api/v1/files/{fileId}/classify
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "file-uuid",
    "category": "财务报告",
    "tags": ["报告", "财务"],
    "confidence": 0.92,
    "processingTime": 1850
  }
}
```

### 3.10 重复检测

```
GET /api/v1/files/duplicates?hash={sha256-hash}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "isDuplicate": true,
    "duplicateOf": {
      "id": "file-uuid",
      "name": "document.pdf",
      "size": 1024000,
      "createdAt": "2026-07-11T10:00:00Z"
    },
    "similarFiles": []
  }
}
```

---

## 4. 语义检索接口

### 4.1 语义搜索

```
POST /api/v1/search
```

**请求**：

```json
{
  "query": "去年的销售数据",
  "page": 1,
  "pageSize": 10,
  "category": "",
  "tags": [],
  "fileTypes": ["pdf", "docx"]
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "query": "去年的销售数据",
    "items": [
      {
        "fileId": "file-uuid",
        "fileName": "销售数据分析.xlsx",
        "fileType": "xlsx",
        "size": 512000,
        "relevance": 0.92,
        "summary": "2024年度销售总额达到1.2亿元...",
        "highlights": ["销售", "数据", "2024"],
        "matchedChunks": [
          {
            "content": "2024年度销售总额达到1.2亿元...",
            "score": 0.92,
            "page": 1
          }
        ]
      }
    ],
    "totalCount": 15,
    "page": 1,
    "pageSize": 10
  }
}
```

### 4.2 文档问答

```
POST /api/v1/qa
```

**请求**：

```json
{
  "question": "去年的净利润是多少？",
  "fileId": "file-uuid",
  "stream": false
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "question": "去年的净利润是多少？",
    "answer": "根据2024年度财务报告，公司去年的净利润为5.2亿元，同比增长18.3%。",
    "sources": [
      {
        "fileId": "file-uuid",
        "fileName": "2024年度财务报告.pdf",
        "chunkContent": "2024年度净利润达到5.2亿元...",
        "relevance": 0.95,
        "page": 5
      }
    ],
    "tokenUsage": {
      "promptTokens": 3892,
      "completionTokens": 36,
      "totalTokens": 3928
    }
  }
}
```

### 4.3 跨文件问答

```
POST /api/v1/qa/cross
```

**请求**：

```json
{
  "question": "对比各季度的销售数据",
  "fileIds": ["file-uuid-1", "file-uuid-2"],
  "stream": false
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "question": "对比各季度的销售数据",
    "answer": "根据销售数据报告，Q1销售额为2500万，Q2为3200万...",
    "sources": [
      {
        "fileId": "file-uuid-1",
        "fileName": "Q1销售报告.pdf",
        "chunkContent": "Q1销售额达到2500万元...",
        "relevance": 0.93
      },
      {
        "fileId": "file-uuid-2",
        "fileName": "Q2销售报告.pdf",
        "chunkContent": "Q2销售额达到3200万元...",
        "relevance": 0.91
      }
    ]
  }
}
```

---

## 5. 内容生成接口

### 5.1 生成摘要

```
POST /api/v1/generate/summary
```

**请求**：

```json
{
  "fileId": "file-uuid",
  "maxLength": 200,
  "style": "concise"
}
```

**参数说明**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|-------|------|
| fileId | String | 是 | - | 文件ID |
| maxLength | Integer | 否 | 200 | 摘要最大字数 |
| style | String | 否 | concise | 风格：concise/detailed/bullet |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "file-uuid",
    "summary": "本报告总结了公司2024年度的财务状况，总营收达到5.8亿元...",
    "style": "concise",
    "wordCount": 186,
    "processingTime": 2340
  }
}
```

### 5.2 内容提炼

```
POST /api/v1/generate/extract
```

**请求**：

```json
{
  "fileId": "file-uuid",
  "extractType": "keywords",
  "maxItems": 10
}
```

**参数说明**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|-------|------|
| fileId | String | 是 | - | 文件ID |
| extractType | String | 是 | - | 提取类型：keywords/keypoints/entities |
| maxItems | Integer | 否 | 10 | 最大提取数量 |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "file-uuid",
    "extractType": "keywords",
    "items": ["营收增长", "成本控制", "利润提升", "市场拓展", "产品创新"],
    "processingTime": 1560
  }
}
```

### 5.3 生成报告

```
POST /api/v1/generate/report
```

**请求**：

```json
{
  "fileIds": ["file-uuid-1", "file-uuid-2"],
  "reportType": "analysis",
  "title": "2024年度财务分析报告",
  "style": "formal",
  "format": "markdown"
}
```

**参数说明**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|-------|------|
| fileIds | String[] | 是 | - | 素材文件ID列表 |
| reportType | String | 是 | - | 报告类型：analysis/summary/meeting |
| title | String | 否 | - | 报告标题 |
| style | String | 否 | formal | 风格：formal/concise/detailed |
| format | String | 否 | markdown | 输出格式：markdown/html |

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "title": "2024年度财务分析报告",
    "content": "# 2024年度财务分析报告\n\n## 概述\n...",
    "format": "markdown",
    "wordCount": 2500,
    "sources": ["file-uuid-1", "file-uuid-2"],
    "processingTime": 8560
  }
}
```

### 5.4 生成PPT

```
POST /api/v1/generate/ppt
```

**请求**：

```json
{
  "fileIds": ["file-uuid-1", "file-uuid-2"],
  "title": "2024年度财务分析报告",
  "template": "professional",
  "slideCount": 10
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "title": "2024年度财务分析报告",
    "downloadUrl": "/api/v1/files/ppt-uuid/download",
    "slideCount": 10,
    "processingTime": 12500
  }
}
```

---

## 6. 安全协作接口

### 6.1 敏感检测

```
POST /api/v1/security/detect
```

**请求**：

```json
{
  "fileId": "file-uuid"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "file-uuid",
    "sensitiveLevel": "high",
    "sensitiveItems": [
      {
        "type": "id_card",
        "content": "110101****1234",
        "position": "第3页第2段",
        "riskLevel": "high"
      },
      {
        "type": "phone",
        "content": "138****1234",
        "position": "第5页第1段",
        "riskLevel": "medium"
      }
    ],
    "processingTime": 3200
  }
}
```

### 6.2 加密文件

```
POST /api/v1/security/encrypt
```

**请求**：

```json
{
  "fileId": "file-uuid",
  "reason": "文件包含敏感信息"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "file-uuid",
    "isEncrypted": true,
    "encryptedAt": "2026-07-11T10:00:00Z",
    "keyId": "key-uuid"
  }
}
```

### 6.3 权限推荐

```
POST /api/v1/collab/recommend
```

**请求**：

```json
{
  "fileId": "file-uuid"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "file-uuid",
    "recommendations": [
      {
        "userId": "user-uuid",
        "userName": "协作人",
        "suggestedPermission": "read",
        "reason": "该用户经常访问同类型文件",
        "confidence": 0.85
      }
    ]
  }
}
```

### 6.4 版本历史

```
GET /api/v1/collab/history/{fileId}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "file-uuid",
    "versions": [
      {
        "version": 3,
        "fileSize": 1048576,
        "modifiedBy": {
          "id": "user-uuid",
          "name": "用户名"
        },
        "comment": "更新数据",
        "createdAt": "2026-07-11T12:00:00Z"
      },
      {
        "version": 2,
        "fileSize": 1024000,
        "modifiedBy": {
          "id": "user-uuid",
          "name": "用户名"
        },
        "comment": "修正错误",
        "createdAt": "2026-07-11T11:00:00Z"
      },
      {
        "version": 1,
        "fileSize": 1000000,
        "modifiedBy": {
          "id": "user-uuid",
          "name": "用户名"
        },
        "comment": "初始版本",
        "createdAt": "2026-07-10T10:00:00Z"
      }
    ]
  }
}
```

---

## 7. 用户认证接口

### 7.1 用户登录

```
POST /api/v1/auth/login
```

**请求**：

```json
{
  "username": "user@example.com",
  "password": "encrypted-password"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "user-uuid",
      "username": "user@example.com",
      "displayName": "用户名",
      "role": "admin",
      "avatar": "https://avatars.nebulamind.com/user-uuid.png"
    }
  }
}
```

### 7.2 刷新Token

```
POST /api/v1/auth/refresh
```

**请求**：

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "new-jwt-access-token",
    "refreshToken": "new-jwt-refresh-token",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

### 7.3 用户注册

```
POST /api/v1/auth/register
```

**请求**：

```json
{
  "username": "user@example.com",
  "password": "encrypted-password",
  "displayName": "用户名",
  "verificationCode": "123456"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": "user-uuid",
    "username": "user@example.com",
    "displayName": "用户名",
    "role": "user",
    "createdAt": "2026-07-11T10:00:00Z"
  }
}
```

---

## 8. 内部API接口（Java后端 ↔ Python AI服务）

### 8.1 接口前缀

所有内部API以 `/api/v1/ai/` 为前缀，仅在Kubernetes集群内网可访问。

### 8.2 认证方式

| 方式 | 说明 |
|------|------|
| 请求头 | `X-Internal-Token: {internal-token}` |
| Token来源 | K8s Secret挂载的环境变量 |

### 8.3 文件理解

```
POST /api/v1/ai/understand
```

**请求**：

```json
{
  "fileId": "file-uuid",
  "fileName": "document.pdf",
  "filePath": "/storage/files/document.pdf",
  "fileType": "pdf",
  "fileSize": 1024000,
  "content": "文件文本内容(解析后)",
  "userId": "user-uuid"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileId": "file-uuid",
    "fileType": "pdf",
    "category": "财务报告",
    "tags": ["报告", "财务"],
    "keywords": ["营收", "利润", "成本"],
    "summary": "文件摘要内容",
    "sensitiveLevel": "normal",
    "sensitiveItems": [],
    "processingTime": 3500
  }
}
```

### 8.4 语义搜索

```
POST /api/v1/ai/search
```

**请求**：

```json
{
  "query": "去年的销售数据",
  "userId": "user-uuid",
  "page": 1,
  "pageSize": 10,
  "filters": {
    "category": "",
    "tags": [],
    "fileTypes": ["pdf", "docx"]
  }
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalCount": 15,
    "items": [
      {
        "fileId": "file-uuid",
        "fileName": "销售数据分析.xlsx",
        "relevance": 0.92,
        "summary": "2024年度销售总额达到1.2亿元...",
        "matchedChunks": [
          {
            "content": "2024年度销售总额达到1.2亿元...",
            "score": 0.92
          }
        ]
      }
    ]
  }
}
```

### 8.5 文档问答

```
POST /api/v1/ai/qa
```

**请求**：

```json
{
  "question": "去年的净利润是多少？",
  "fileId": "file-uuid",
  "userId": "user-uuid",
  "stream": false
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "根据2024年度财务报告，公司去年的净利润为5.2亿元，同比增长18.3%。",
    "sources": [
      {
        "fileId": "file-uuid",
        "chunkContent": "2024年度净利润达到5.2亿元...",
        "relevance": 0.95
      }
    ],
    "tokenUsage": {
      "promptTokens": 3892,
      "completionTokens": 36,
      "totalTokens": 3928
    }
  }
}
```

### 8.6 内容生成

```
POST /api/v1/ai/generate
```

**请求**：

```json
{
  "fileIds": ["file-uuid-1", "file-uuid-2"],
  "generateType": "summary",
  "params": {
    "maxLength": 200,
    "style": "concise"
  },
  "userId": "user-uuid"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "generateType": "summary",
    "content": "生成的摘要/报告内容...",
    "tokenUsage": {
      "promptTokens": 5000,
      "completionTokens": 200,
      "totalTokens": 5200
    },
    "processingTime": 4500
  }
}
```

### 8.7 敏感检测

```
POST /api/v1/ai/security/detect
```

**请求**：

```json
{
  "fileId": "file-uuid",
  "content": "文件文本内容",
  "userId": "user-uuid"
}
```

**响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sensitiveLevel": "high",
    "sensitiveItems": [
      {
        "type": "id_card",
        "content": "110101****1234",
        "position": "第3页第2段",
        "riskLevel": "high"
      }
    ]
  }
}
```

---

## 9. 实时通信接口（WebSocket/SSE）

### 9.1 SSE（Server-Sent Events）接口

SSE 用于 Java后端向客户端推送单向实时消息，适用于AI处理进度通知和流式问答响应。

**连接端点**：

```
GET /api/v1/events/connect
```

**请求头**：

| 头 | 值 | 说明 |
|-----|-----|------|
| Authorization | Bearer {jwt-token} | JWT认证Token |
| Accept | text/event-stream | 声明SSE连接 |

**事件格式**：

```
event: {eventType}
data: {json数据}
id: {eventId}
retry: 3000
```

**事件类型定义**：

| 事件类型 | 触发场景 | data格式 |
|---------|---------|---------|
| `file.processing` | 文件上传后AI开始处理 | `{"fileId":"uuid","status":"processing","progress":0}` |
| `file.progress` | AI处理进度更新 | `{"fileId":"uuid","status":"processing","progress":45}` |
| `file.completed` | AI处理完成 | `{"fileId":"uuid","status":"completed","aiResult":{...}}` |
| `file.failed` | AI处理失败 | `{"fileId":"uuid","status":"failed","error":"原因"}` |
| `qa.stream` | 流式问答响应 | `{"fileId":"uuid","chunk":"部分回答内容","done":false}` |
| `qa.done` | 流式问答结束 | `{"fileId":"uuid","chunk":"","done":true}` |

**连接管理**：
- 客户端登录后建立SSE连接
- 连接超时：30分钟无活动自动断开
- 断线重连：客户端自动重连，服务端返回 `Last-Event-ID`

### 9.2 WebSocket 接口（备用）

当需要双向通信时（如协同编辑），使用WebSocket替代SSE。

**连接端点**：

```
ws://api.nebulamind.com/ws
```

**认证方式**：连接时通过URL参数传递Token

```
ws://api.nebulamind.com/ws?token={jwt-token}
```

**消息格式**：

```json
{
  "type": "messageType",
  "payload": {},
  "timestamp": "2026-07-11T10:00:00Z",
  "messageId": "msg-uuid"
}
```

---

## 10. Swagger/OpenAPI 规范

### 10.1 规范说明

API规范文档采用 **OpenAPI 3.0** 标准，通过以下方式集成到项目中：

| 集成方式 | 说明 | 实现工具 |
|---------|------|---------|
| 代码注解 | 使用注解在Controller中定义API信息 | springdoc-openapi |
| 自动文档 | 启动项目后自动生成OpenAPI JSON | springdoc-openapi-ui |
| 交互式测试 | 提供Swagger UI页面在线调试 | Swagger UI |

### 10.2 springdoc-openapi 配置

**Maven依赖**：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**Java配置**：

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("云盘智能体 API")
                .version("v1.0")
                .description("云盘智能体应用后端API接口文档"))
            .addSecurityItem(new SecurityRequirement()
                .addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
```

### 10.3 访问方式

| 环境 | Swagger UI 地址 | OpenAPI JSON 地址 |
|------|----------------|-------------------|
| 开发环境 | http://localhost:8080/swagger-ui.html | http://localhost:8080/v3/api-docs |
| 测试环境 | https://test-api.nebulamind.com/swagger-ui.html | https://test-api.nebulamind.com/v3/api-docs |
| 生产环境 | 不对外开放（内网访问） | 不对外开放 |

### 10.4 注解规范

| 场景 | 注解 | 示例 |
|------|------|------|
| 接口描述 | @Operation | `@Operation(summary = "上传文件", description = "支持分片上传")` |
| 参数描述 | @Parameter | `@Parameter(description = "文件ID", required = true)` |
| 响应描述 | @ApiResponse | `@ApiResponse(responseCode = "200", description = "上传成功")` |
| 数据模型 | @Schema | `@Schema(description = "文件实体")` |

---

## 11. 日志系统设计

### 11.1 日志架构

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  应用日志     │    │  AI调用日志   │    │  安全审计日志  │
│  (业务操作)   │    │  (模型调用)   │    │  (敏感操作)   │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                   │
       ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────┐
│                  日志存储层                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐│
│  │  控制台输出   │  │  文件滚动     │  │  数据库存储   ││
│  │  (开发环境)   │  │  (生产环境)   │  │  (AI调用/审计) ││
│  └──────────────┘  └──────────────┘  └──────────────┘│
└─────────────────────────────────────────────────────┘
```

### 11.2 日志分类

| 日志类型 | 输出目标 | 保留周期 | 包含内容 |
|---------|---------|---------|---------|
| **应用日志** | 控制台 + 文件 | 30天 | 请求、响应、业务处理、异常堆栈 |
| **AI调用日志** | 文件 + 数据库 | 90天 | 模型、Token用量、延迟、成功/失败 |
| **安全审计日志** | 数据库 | 180天 | 敏感操作、登录、权限变更、文件删除 |

### 11.3 日志级别配置

| 环境 | 根日志级别 | Spring框架 | 业务代码 |
|------|-----------|-----------|---------|
| 开发(dev) | INFO | INFO | DEBUG |
| 测试(test) | INFO | WARN | INFO |
| 生产(prod) | WARN | WARN | INFO |

### 11.4 日志框架配置

**技术栈**：SLF4J + Logback

**logback-spring.xml 关键配置**：

```xml
<!-- 控制台输出 -->
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>

<!-- 文件滚动输出 -->
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/nebulamind.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/nebulamind.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

---

## 12. 错误处理

### 12.1 全局异常处理

| 异常类型 | HTTP状态码 | 错误码 | 说明 |
|---------|-----------|--------|------|
| MethodArgumentNotValidException | 400 | 400 | 参数校验失败 |
| BindException | 400 | 400 | 参数绑定失败 |
| MissingServletRequestParameterException | 400 | 400 | 缺少必填参数 |
| AuthenticationException | 401 | 401 | 认证失败 |
| AccessDeniedException | 403 | 403 | 权限不足 |
| NoSuchElementException | 404 | 404 | 资源不存在 |
| FileNotFoundException | 404 | 404 | 文件不存在 |
| FileSizeLimitExceededException | 413 | 413 | 文件大小超限 |
| TooManyRequestsException | 429 | 429 | 请求频率过高 |
| RuntimeException | 500 | 500 | 服务器内部错误 |
| AiServiceException | 502 | 502 | AI服务调用失败 |
| TimeoutException | 503 | 503 | 服务超时 |

### 12.2 错误响应示例

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": {
    "errors": [
      {
        "field": "fileName",
        "message": "文件名不能为空"
      },
      {
        "field": "fileSize",
        "message": "文件大小不能超过100MB"
      }
    ]
  },
  "requestId": "req-uuid",
  "timestamp": "2026-07-11T10:00:00Z"
}
```

---

## 13. 附录

### 13.1 API版本管理

| 版本 | 状态 | 说明 |
|------|------|------|
| v1 | 当前版本 | 初始版本，稳定后将冻结 |

### 13.2 接口变更流程

1. 提交API变更提案（含变更内容、理由、兼容性分析）
2. 团队评审确认
3. 更新API规范文档
4. 实现变更
5. 通知前端/测试团队

### 13.3 相关文档

| 文档 | 说明 |
|------|------|
| [系统架构设计文档.md](系统架构设计文档.md) | 系统架构整体设计 |
| [双服务架构方案.md](双服务架构方案.md) | 双服务架构详细设计 |
| [技术选型文档.md](技术选型文档.md) | 技术选型详细分析 |