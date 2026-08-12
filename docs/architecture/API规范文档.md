# NebulaMind API 规范文档

**文档版本**：v2.0
**最后更新**：2026-08-12
**说明**：本文档与当前代码实现保持一致。若实现发生变化，请同步更新本文档。

---

## 1. 接口约定

### 1.1 服务地址

| 服务 | 本地地址 | 说明 |
|------|---------|------|
| 后端 API | `http://localhost:8080` | Spring Boot，业务接口统一使用 `/api/v1` 前缀 |
| AI 服务 | `http://localhost:8081` | Python FastAPI，仅供后端内部调用 |
| 前端开发服务 | `http://localhost:5173` | Vite Dev Server，`/api/v1` 由 Vite 代理到后端 |

### 1.2 认证方式

- 除免认证接口外，请求头必须携带 `Authorization: Bearer <accessToken>`。
- 免认证接口：`/api/v1/auth/**`、`/api/v1/public/**`、`/api/v1/files/*/process-callback`、`/webdav/**`、`/s3/**`、`/sse/**`。
- 管理员接口：`/api/v1/admin/**` 需要 `ADMIN` 角色。

### 1.3 响应格式

成功响应直接返回实体、`Page<T>`、`List<T>` 或 `Map`，不包一层 `{code, message, data}`。

分页接口使用 Spring Data `Page<T>` 结构，页码从 0 开始：

```json
{
  "content": [],
  "pageable": { "page": 0, "size": 20 },
  "totalElements": 100,
  "totalPages": 5,
  "last": false,
  "first": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 0,
  "empty": false
}
```

### 1.4 错误响应

全局异常统一返回：

```json
{
  "timestamp": "2026-08-12T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "校验失败的具体原因"
}
```

部分接口在业务层直接返回 `{"error": "..."}`（如安全加密、短信验证码等），HTTP 状态码语义相同。

---

## 2. 认证接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/v1/auth/register` | 否 | 邮箱注册 |
| POST | `/api/v1/auth/login` | 否 | 邮箱密码登录 |
| POST | `/api/v1/auth/refresh` | 否 | 刷新 Token，query 参数传 `token` |
| POST | `/api/v1/auth/logout` | 否 | 登出，query 参数传 `token` |
| POST | `/api/v1/auth/sms/send` | 否 | 发送短信验证码 |
| POST | `/api/v1/auth/sms/login` | 否 | 短信验证码登录 |
| POST | `/api/v1/auth/sms/register` | 否 | 短信验证码注册 |
| POST | `/api/v1/auth/change-password` | 是 | 修改密码 |

### 2.1 注册 / 登录

`POST /api/v1/auth/register`

```json
{
  "username": "zhangsan",
  "email": "zhangsan@example.com",
  "password": "your-password",
  "displayName": "张三"
}
```

校验规则：用户名 2-100 位，邮箱合法，密码至少 6 位，显示名不超过 100 位。

`POST /api/v1/auth/login`

```json
{
  "email": "admin@nebulamind.com",
  "password": "your-password"
}
```

注册和登录均返回：

```json
{
  "userId": "a1000001-0000-0000-0000-000000000001",
  "email": "admin@nebulamind.com",
  "displayName": "系统管理员",
  "role": "admin",
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token"
}
```

### 2.2 刷新 / 登出

```text
POST /api/v1/auth/refresh?token=<refreshToken>
POST /api/v1/auth/logout?token=<refreshToken>
```

刷新成功返回新的 `AuthResponse`。

### 2.3 短信验证码

- `POST /api/v1/auth/sms/send`：body `{"phone": "13800138000"}`，返回 `{"success": true, "message": "验证码已发送"}`。
- `POST /api/v1/auth/sms/login`：body `{"phone": "...", "code": "..."}`，成功返回 `AuthResponse`。
- `POST /api/v1/auth/sms/register`：body `{"phone": "...", "code": "...", "username": "...", "displayName": "...", "password": "..."}`，成功返回 `AuthResponse`。

### 2.4 修改密码

`POST /api/v1/auth/change-password`

```json
{
  "currentPassword": "your-password",
  "newPassword": "NewPass@2026"
}
```

成功返回 `{"success": true, "message": "密码修改成功"}`。

---

## 3. 文件管理接口

### 3.1 文件列表

```text
GET /api/v1/files?page=0&size=20
```

仅返回当前用户的文件，按创建时间倒序。响应为 `Page<File>`。

### 3.2 文件详情 / 下载

```text
GET /api/v1/files/{fileId}
GET /api/v1/files/{fileId}/download
```

- 详情返回 `File` 实体，包含 `id/name/path/size/mimeType/fileType/hash/status/aiStatus/category/tags/summary/sensitiveLevel/isEncrypted/encryptionMode/version` 等字段。
- 下载返回文件二进制流；服务端 AES-256-GCM 加密的文件会自动解密，端到端加密文件下载后需在本地解密。

### 3.3 普通上传

```text
POST /api/v1/files/upload
```

`multipart/form-data` 参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传文件 |
| encrypted | Boolean | 否 | `true` 表示标记为端到端加密文件，服务器无法分析内容 |

响应为 `File` 实体。

### 3.4 JSON 创建文件

```text
POST /api/v1/files
```

```json
{
  "name": "document.pdf",
  "path": "optional/path",
  "size": 1024000,
  "mimeType": "application/pdf",
  "content": "base64 或文本内容"
}
```

响应为 `File` 实体。

### 3.5 分片上传（后端已实现，前端待接入）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/files/upload/init` | 初始化分片上传 |
| POST | `/api/v1/files/upload/chunk` | 上传单个分片，最后一片自动合并 |
| POST | `/api/v1/files/upload/cancel` | 取消上传 |

`POST /api/v1/files/upload/init`

```json
{
  "fileName": "large-file.zip",
  "contentType": "application/zip",
  "fileSize": 104857600,
  "chunkIndex": 0,
  "totalChunks": 20,
  "uploadId": "client-upload-id",
  "fileHash": "sha256-of-file"
}
```

返回：

```json
{
  "uploadId": "server-upload-id",
  "totalChunks": "20"
}
```

若检测到重复文件，返回 `{"message": "Duplicate file detected", "existingFileId": "..."}`。

`POST /api/v1/files/upload/chunk`，`multipart/form-data`：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| uploadId | String | 是 | init 返回的 uploadId |
| chunkIndex | Integer | 是 | 分片序号，从 0 开始 |
| chunk | File | 是 | 分片内容，单片上限 5MB |

返回：

```json
{
  "fileId": "merge 完成后返回",
  "fileName": "large-file.zip",
  "uploadId": "server-upload-id",
  "chunkIndex": 19,
  "totalChunks": 20,
  "completed": true,
  "message": "合并完成"
}
```

### 3.6 更新 / 删除

```text
PUT /api/v1/files/{fileId}
DELETE /api/v1/files/{fileId}
```

更新支持 `{"name": "新名称", "tags": "标签文本"}`。

### 3.7 智能分类与重复检测

```text
POST /api/v1/files/{fileId}/classify
GET /api/v1/files/duplicates?hash=<sha256>
```

分类返回：

```json
{
  "fileId": "uuid",
  "category": "Word文档",
  "tags": ["Word", "文档"],
  "confidence": 0.85,
  "processingTime": 120
}
```

重复检测返回重复分组数组，每组包含 `hash` 和 `files: [{id, name, size}]`。

### 3.8 处理回调

```text
POST /api/v1/files/{fileId}/process-callback
```

供 AI 服务或异步任务回调更新文件处理结果，免认证。

---

## 4. 文件版本接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/files/{fileId}/versions` | 版本历史列表 |
| GET | `/api/v1/files/{fileId}/versions/{versionNumber}` | 指定版本详情 |
| POST | `/api/v1/files/{fileId}/versions` | 创建文本版本，body `{"content": "...", "comment": "..."}` |
| POST | `/api/v1/files/{fileId}/versions/upload` | 上传新版本，multipart `file`、可选 `comment`、`encrypted` |
| GET | `/api/v1/files/{fileId}/versions/diff?versionA=1&versionB=2` | 版本 diff |
| POST | `/api/v1/files/{fileId}/versions/summary` | AI 生成版本变化摘要，body 可选 `versionA/versionB` |
| POST | `/api/v1/files/{fileId}/versions/rollback/{targetVersion}` | 回滚到指定版本 |
| GET | `/api/v1/files/{fileId}/versions/history` | 编辑痕迹追踪 |

回滚返回：

```json
{
  "fileId": "uuid",
  "currentVersion": 3,
  "rolledBackFrom": 1,
  "message": "文件已回滚到版本 1"
}
```

---

## 5. 语义检索与问答

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/search` | 语义搜索，AI 不可用时按文件名/标签回退 |
| POST | `/api/v1/qa` | 单文档问答 |
| POST | `/api/v1/qa/cross` | 跨文档问答 |

`POST /api/v1/search`

```json
{
  "query": "去年销售数据",
  "fileIds": ["uuid-1", "uuid-2"],
  "topK": 10,
  "page": 0,
  "pageSize": 10
}
```

`fileIds` 不传时默认检索当前用户最近 500 个文件。响应：

```json
{
  "query": "去年销售数据",
  "items": [
    {
      "fileId": "uuid",
      "fileName": "销售数据分析.xlsx",
      "fileType": "xlsx",
      "size": 512000,
      "relevance": 0.92,
      "summary": "命中片段",
      "highlights": ["片段"],
      "matchedChunks": []
    }
  ],
  "totalCount": 15,
  "page": 0,
  "pageSize": 10
}
```

`POST /api/v1/qa` body：`{"question": "...", "fileId": "..."}`。
`POST /api/v1/qa/cross` body：`{"question": "...", "fileIds": ["uuid-1", "uuid-2"]}`。

问答响应：

```json
{
  "question": "文档中的关键技术是什么？",
  "answer": "基于检索增强生成的回答",
  "sourceFileId": "uuid",
  "sourceSnippets": ["引用片段"],
  "confidence": 0.92
}
```

---

## 6. 内容生成

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/generate/summary` | 生成摘要，body `{"fileId": "..."}` |
| POST | `/api/v1/generate/extract` | 内容提炼，body `{"fileId": "..."}` |
| POST | `/api/v1/generate/report` | 生成报告，body `{"fileIds": [...], "topic": "..."}` |
| POST | `/api/v1/generate/ppt` | 生成 PPT 大纲，body `{"fileIds": [...], "topic": "..."}` |
| POST | `/api/v1/generate/convert` | 格式转换，body `{"fileId": "...", "targetFormat": "docx"}` |

> 说明：`/api/v1/generate/ppt` 与 `/api/v1/generate/convert` 为后端接口，前端生成页入口待接入。

响应统一为：

```json
{
  "fileId": "uuid",
  "content": "生成的 markdown 内容",
  "keyPoints": ["要点1", "要点2"],
  "format": "markdown"
}
```

注意事项：

- 图片文件使用视觉模型（元景 YuanjingVL）直接分析。
- 压缩包（zip/rar/7z/gz 等）返回提示：请先解压后上传再生成。
- 端到端加密文件返回提示：服务器无法读取内容，请本地解密后再使用。
- MaaS 限流（5 次/分钟）时返回明确的限流提示。

---

## 7. 安全与加密

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/security/detect` | 敏感信息两级检测，高风险可自动加密 |
| POST | `/api/v1/security/encrypt` | 文件加密（服务端 AES-256-GCM 或标记端到端加密） |
| POST | `/api/v1/security/decrypt` | 文件解密 |
| GET | `/api/v1/e2ee/key` | 获取当前用户端到端密钥 blob |
| PUT | `/api/v1/e2ee/key` | 保存当前用户端到端密钥 blob |

`POST /api/v1/security/detect`

```json
{
  "fileId": "uuid",
  "useLlm": true,
  "autoEncrypt": true
}
```

响应：

```json
{
  "fileId": "uuid",
  "sensitiveLevel": "high",
  "sensitiveItems": [
    {
      "type": "id_card",
      "typeName": "身份证号",
      "content": "110101****1234",
      "position": 0,
      "riskLevel": "high",
      "source": "regex"
    }
  ],
  "scannedAt": "2026-08-12T10:00:00",
  "detectionMethod": "regex+ai",
  "autoEncrypted": true,
  "encryptionAlgorithm": "AES-256-GCM",
  "message": "文件包含高风险敏感信息，已自动加密存储"
}
```

检测策略：本地正则（身份证/手机号/银行卡/邮箱）+ LLM NER + 图片 OCR 兜底，检测结果会写回文件 `sensitiveLevel`。

`POST /api/v1/security/encrypt` body：`{"fileId": "...", "reason": "...", "clientEncrypted": false}`。
`POST /api/v1/security/decrypt` body：`{"fileId": "...", "clientDecrypted": false}`。

服务端加密使用每文件独立密钥，密钥用当前用户的密钥包装后保存；解密后明文写回存储并清除密钥引用。已加密文件不可重复加密。

---

## 8. 云存储对接

### 8.1 配置管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/storage-config` | 当前用户的云存储配置列表 |
| POST | `/api/v1/storage-config` | 新建配置（S3 / WebDAV） |
| PUT | `/api/v1/storage-config/{id}` | 更新配置 |
| DELETE | `/api/v1/storage-config/{id}` | 删除配置 |
| POST | `/api/v1/storage-config/{id}/test` | 测试连接 |

配置字段包括：`name/providerType/endpointUrl/accessKey/secretKey/bucketName/region/isActive/extraConfig`。

测试连接返回：

```json
{
  "success": true,
  "message": "连接成功",
  "testedAt": "2026-08-12T10:00:00"
}
```

### 8.2 远端文件操作

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/storage-config/{configId}/drive/files?path=/` | 浏览远端文件 |
| GET | `/api/v1/storage-config/{configId}/drive/download?path=/a.pdf` | 下载远端文件 |
| POST | `/api/v1/storage-config/{configId}/drive/files/upload` | 上传文件到远端，multipart `file`、可选 `path`、`name` |
| DELETE | `/api/v1/storage-config/{configId}/drive/files?path=/a.pdf` | 删除远端文件 |
| POST | `/api/v1/storage-config/{configId}/drive/files/import?path=/a.pdf` | 导入远端文件到 NebulaMind |

---

## 9. 审计与管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/audit/user/{userId}` | 按用户查询审计日志 |
| GET | `/api/v1/admin/audit/action/{action}` | 按操作类型查询 |
| GET | `/api/v1/admin/audit/resource/{resourceType}` | 按资源类型查询 |
| GET | `/api/v1/admin/audit/timerange` | 按时间范围查询 |
| GET | `/api/v1/admin/audit/recent` | 最近日志 |
| GET | `/api/v1/admin/audit/alerts/summary` | 告警汇总 |
| GET | `/api/v1/admin/audit/export` | 导出日志 |

以上接口需要 `ADMIN` 角色。

---

## 10. 健康检查

```text
GET /api/v1/public/health
GET /api/v1/public/info
```

无需认证，用于探活与服务信息展示。

---

## 11. 实时通信（SSE）

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/sse/subscribe/{userId}` | 否 | 按用户 ID 订阅，前端当前使用 |
| GET | `/api/v1/events/connect` | 是 | 认证后自动按当前用户订阅 |

响应类型：`text/event-stream`，连接超时 5 分钟。

事件类型：

| 事件 | 数据 | 说明 |
|------|------|------|
| `connected` | `{"message": "...", "userId": "..."}` | 连接建立 |
| `progress` | `{"fileId": "...", "progress": 45, "message": "...", "timestamp": 123}` | 处理进度 |
| `task` | `{"taskId": "...", "status": "...", "message": "...", "timestamp": 123}` | 任务状态 |

---

## 12. WebDAV / S3 兼容接口

### 12.1 WebDAV

基础路径 `/webdav/**`，实现 `GET/HEAD/PUT/DELETE`，可作为 WebDAV 客户端挂载路径。

### 12.2 S3 兼容

基础路径 `/s3/**`：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/s3/{bucket}/objects` | 对象列表 |
| GET | `/s3/{bucket}/objects/{fileId}` | 下载对象 |
| HEAD | `/s3/{bucket}/objects/{fileId}` | 元数据查询 |
| PUT | `/s3/{bucket}/objects/{fileId}` | 上传对象 |
| DELETE | `/s3/{bucket}/objects/{fileId}` | 删除对象 |

---

## 13. AI 服务内部接口（FastAPI）

Java 后端默认通过 `http://localhost:8081` 调用 AI 服务；配置 `AI_SERVICE_API_KEY` 后请求头携带 `X-API-Key`，未配置或调试模式下不做强校验。业务端点共 11 个，另有 4 个辅助端点：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/classify` | 文件分类与标签 |
| POST | `/api/v1/search` | 语义搜索 |
| POST | `/api/v1/qa` | 单文档问答 |
| POST | `/api/v1/qa/cross` | 跨文档问答 |
| POST | `/api/v1/generate/summary` | 摘要生成 |
| POST | `/api/v1/generate/extract` | 内容提炼 |
| POST | `/api/v1/generate/report` | 报告生成 |
| POST | `/api/v1/generate/ppt` | PPT 大纲生成 |
| POST | `/api/v1/generate/convert` | 格式转换 |
| POST | `/api/v1/sensitive/detect` | 敏感检测 |
| POST | `/api/v1/sensitive/mask` | 敏感脱敏 |

辅助端点：`GET /health`、`GET /api/v1/stats`、`GET /api/v1/logs/export`、`GET /api/v1/prompts`。

AI 服务回调后端 `/api/v1/files/{fileId}/process-callback` 时使用 `X-Internal-Api-Key`（由后端 `INTERNAL_API_KEY` 配置），与对外调用 AI 服务的 `X-API-Key` 不同。

AI 服务响应模型：

- 分类：`{file_id, category, tags, sensitive_level, confidence}`
- 搜索：`{query, results: [{file_id, file_name, snippet, score, category}]}`
- 问答：`{question, answer, source_file_id, source_snippets, confidence}`
- 生成：`{file_id, content, key_points, format}`
- 敏感检测：`{file_id, sensitive_level, level_score, summary, detection_method, warning, matches, masked_content}`

AI 服务内部会对 MaaS 接口限流（每分钟 5 次）做降级处理，敏感检测结果缓存 10 分钟以减少重复调用。

---

## 14. 错误码

| HTTP 状态 | 场景 | 说明 |
|-----------|------|------|
| 400 | 参数校验失败、非法参数、重复加密等 | `MethodArgumentNotValidException`、`IllegalArgumentException` |
| 401 | 未认证或密码错误 | `BadCredentialsException`、`AuthenticationException` |
| 403 | 无权限 | 非本人资源、非管理员访问管理接口 |
| 404 | 资源不存在 | `ResourceNotFoundException` |
| 429 | 触发 Sentinel 限流 | 返回 `Rate limit exceeded, please try again later` |
| 500 | 服务器内部错误 | 通用异常兜底 |

---

## 15. 附录：接口与代码对应

| 模块 | 主要代码 |
|------|---------|
| 认证 | `backend/src/main/java/com/nebulamind/controller/AuthController.java` |
| 文件 | `backend/src/main/java/com/nebulamind/controller/FileController.java` |
| 版本 | `backend/src/main/java/com/nebulamind/controller/FileVersionController.java` |
| 搜索/问答 | `SearchController.java`、`QAController.java` |
| 生成 | `GenerateController.java` |
| 安全 | `SecurityController.java`、`E2eeController.java` |
| 云存储 | `CloudStorageConfigController.java`、`CloudStorageDriveController.java` |
| SSE | `sse/SseController.java`、`controller/SseEventController.java` |
| AI 服务 | `ai-services/app/api/*.py` |
