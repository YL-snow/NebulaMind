# NebulaMind 云盘智能体 - 部署与测试指南

## 一、项目概述

### 1.1 项目背景

本项目是基于**联通元景万悟**大模型平台开发的云盘智能体应用，旨在将传统云盘从"被动存储"升级为"主动管理"，打造懂内容、会整理、能协作的智能云盘助手。

### 1.2 核心功能模块

| 模块 | 功能描述 | 状态 |
|------|----------|------|
| **智能文件管理** | 文件上传/下载、自动分类、标签管理、重复文件检测 | ✅ 已实现 |
| **语义检索与问答** | 自然语言搜索、文档问答、跨文件信息整合 | ✅ 已实现 |
| **文档处理与生成** | 自动摘要、关键词提取、报告生成、PPT生成 | ✅ 已实现 |
| **安全与协作** | 敏感文件识别、加密存储、权限管理、版本控制 | ✅ 部分实现 |

### 1.3 竞赛要求对齐

- ✅ 基于智能体开发平台及大模型技术构建四大核心模块
- ✅ 支持对接主流云存储协议（MinIO S3兼容）
- ✅ 预留联通云盘API对接接口（因个人用户无法获取API凭证）
- ✅ 设置数据安全机制，敏感文件自动识别与加密
- ✅ 支持隐私文件加密存储（AES-256-GCM）

---

## 二、技术架构

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (React + TypeScript)              │
│   [Home] [Search] [Generate] [Security] [FileDetail]      │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP/HTTPS
┌────────────────────────────▼────────────────────────────────┐
│                  Nginx 反向代理                            │
│   /api/* → backend:8080   / → 静态资源                     │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│              后端服务 (Spring Boot)                         │
│   [Auth] [File] [AI] [Search] [QA] [Generate] [Security]   │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────┼────────────────────────────────┐
│          ┌─────────────────┴─────────────────┐             │
│          ▼                                   ▼             │
│   ┌─────────────┐                   ┌─────────────┐        │
│   │ PostgreSQL  │                   │    Redis    │        │
│   │ (数据库)    │                   │  (缓存)     │        │
│   └─────────────┘                   └─────────────┘        │
│          ▼                                   ▼             │
│   ┌─────────────┐                   ┌─────────────┐        │
│   │ RabbitMQ    │                   │   MinIO     │        │
│   │ (消息队列)  │                   │  (对象存储)  │        │
│   └─────────────┘                   └─────────────┘        │
│          ▼                                   ▼             │
│   ┌─────────────┐                   ┌─────────────┐        │
│   │  AI Service │                   │   Milvus    │        │
│   │ (Python)    │                   │  (向量库)   │        │
│   └─────────────┘                   └─────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **前端** | React | 19.x |
| | TypeScript | 5.x |
| | Ant Design | 5.x |
| | Vite | 6.x |
| **后端** | Spring Boot | 3.2.x |
| | Java | 21 |
| | PostgreSQL | 15 |
| | Redis | 7 |
| | RabbitMQ | 3.12 |
| | MinIO | latest |
| **AI服务** | Python | 3.10 |
| | FastAPI | latest |
| | Milvus | 2.4 |
| **部署** | Docker | 24.x |
| | Docker Compose | v2 |

---

## 三、项目结构

```
NebulaMind/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/nebulamind/
│   │   ├── controller/         # REST API 控制器
│   │   ├── service/            # 业务逻辑层
│   │   ├── repository/         # 数据访问层
│   │   ├── entity/             # 数据库实体
│   │   ├── dto/                # 数据传输对象
│   │   ├── config/             # 配置类
│   │   └── security/           # 安全相关
│   ├── src/main/resources/
│   │   ├── application.yml     # 默认配置
│   │   ├── application-dev.yml # 开发环境配置
│   │   └── application-prod.yml # 生产环境配置
│   ├── Dockerfile              # 后端镜像构建
│   └── pom.xml                 # Maven 依赖
├── frontend/                   # React 前端
│   ├── src/
│   │   ├── pages/              # 页面组件
│   │   ├── components/         # UI组件
│   │   ├── api/                # API调用
│   │   ├── stores/             # 状态管理
│   │   └── utils/              # 工具函数
│   ├── Dockerfile              # 前端镜像构建
│   ├── nginx.conf              # Nginx配置
│   └── package.json            # npm依赖
├── ai-services/                # Python AI服务
│   ├── app/                    # 应用代码
│   ├── Dockerfile              # AI服务镜像构建
│   └── requirements.txt        # Python依赖
├── docker-compose.yml          # 基础服务编排
├── docker-compose.prod.yml     # 生产环境编排（含所有服务）
├── deploy-to-ecs.sh            # ECS部署脚本
└── .env.example                # 环境变量示例
```

---

## 四、已完成工作

### 4.1 后端功能

| 功能 | 文件 | 状态 |
|------|------|------|
| 用户认证（JWT） | [AuthController.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/controller/AuthController.java) | ✅ |
| 文件上传/下载 | [FileController.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/controller/FileController.java) | ✅ |
| 文件分类 | [AIController.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/controller/AIController.java) | ✅ |
| 重复文件检测 | [FileService.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/service/FileService.java) | ✅ |
| 语义搜索 | [SearchController.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/controller/SearchController.java) | ✅ |
| 文档问答 | [QAController.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/controller/QAController.java) | ✅ |
| 内容生成 | [GenerateController.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/controller/GenerateController.java) | ✅ |
| 敏感检测 | [SecurityController.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/controller/SecurityController.java) | ✅ |
| 文件加密 | [EncryptionService.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/service/EncryptionService.java) | ✅ |
| 版本管理 | [FileVersionService.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/service/FileVersionService.java) | ✅ |
| MinIO存储 | [MinIOService.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/service/MinIOService.java) | ✅ |
| 存储服务抽象 | [StorageService.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/service/StorageService.java) | ✅ |
| 联通云盘接口预留 | [UnicomCloudDriveClient.java](file:///c:/projects/NebulaMind2/NebulaMind/backend/src/main/java/com/nebulamind/api/client/unicom/UnicomCloudDriveClient.java) | ✅ |

### 4.2 前端功能

| 功能 | 文件 | 状态 |
|------|------|------|
| 用户登录 | [Login.tsx](file:///c:/projects/NebulaMind2/NebulaMind/frontend/src/pages/Login.tsx) | ✅ |
| 文件管理首页 | [Home.tsx](file:///c:/projects/NebulaMind2/NebulaMind/frontend/src/pages/Home.tsx) | ✅ |
| 文件详情 | [FileDetail.tsx](file:///c:/projects/NebulaMind2/NebulaMind/frontend/src/pages/FileDetail.tsx) | ✅ |
| 语义搜索 | [Search.tsx](file:///c:/projects/NebulaMind2/NebulaMind/frontend/src/pages/Search.tsx) | ✅ |
| 内容生成 | [Generate.tsx](file:///c:/projects/NebulaMind2/NebulaMind/frontend/src/pages/Generate.tsx) | ✅ |
| 安全管理 | [Security.tsx](file:///c:/projects/NebulaMind2/NebulaMind/frontend/src/pages/Security.tsx) | ✅ |
| 文件上传组件 | [Uploader.tsx](file:///c:/projects/NebulaMind2/NebulaMind/frontend/src/components/business/Uploader.tsx) | ✅ |
| 文件卡片组件 | [FileCard.tsx](file:///c:/projects/NebulaMind2/NebulaMind/frontend/src/components/business/FileCard.tsx) | ✅ |

### 4.3 容器化部署

| 组件 | 状态 |
|------|------|
| Dockerfile (后端) | ✅ 已创建 |
| Dockerfile (前端) | ✅ 已创建 |
| Dockerfile (AI服务) | ✅ 已创建 |
| docker-compose.prod.yml | ✅ 已创建 |
| 预构建镜像 tar | ✅ 已生成 |
| ECS部署脚本 | ✅ 已创建 |

---

## 五、待完成工作

### 5.1 功能完善

| 优先级 | 功能 | 描述 | 涉及文件 |
|--------|------|------|----------|
| 🔴 高 | 用户注册 | 前端注册页面 + 后端注册API | `frontend/src/pages/Register.tsx`, `AuthController.java` |
| 🔴 高 | 文件分享 | 分享链接生成、权限设置、分享管理 | `frontend/src/pages/Share.tsx`, `ShareController.java` |
| 🔴 高 | 协作编辑 | 编辑痕迹追踪、多人协作 | `CollabController.java`, `FileVersionService.java` |
| 🟡 中 | 实时通知 | SSE实时推送文件处理进度 | `SseController.java`, `frontend/src/hooks/useSSE.ts` |
| 🟡 中 | 邮件通知 | 敏感文件告警、分享通知 | `EmailService.java` |
| 🟢 低 | 移动端适配 | 响应式布局优化 | 前端全局CSS |

### 5.2 安全加固

| 优先级 | 项目 | 描述 |
|--------|------|------|
| 🔴 高 | HTTPS配置 | Nginx配置SSL证书 |
| 🔴 高 | JWT密钥轮换 | 生产环境使用独立密钥 |
| 🔴 高 | 数据库密码 | PostgreSQL/Redis密码修改 |
| 🟡 中 | API限流 | Sentinel限流配置 |
| 🟡 中 | 日志审计 | 完善审计日志记录 |

### 5.3 性能优化

| 优先级 | 项目 | 描述 |
|--------|------|------|
| 🟡 中 | 文件分片上传 | 大文件分片上传支持 |
| 🟡 中 | 缓存优化 | Redis缓存策略优化 |
| 🟢 低 | CDN加速 | 静态资源CDN配置 |

---

## 六、部署步骤

### 6.1 部署环境要求

| 项目 | 要求 |
|------|------|
| 操作系统 | Ubuntu 22.04 LTS |
| CPU | 2核及以上 |
| 内存 | 4GB及以上 |
| 磁盘 | 50GB及以上 |
| Docker | 24.x 及以上 |
| Docker Compose | v2 及以上 |

### 6.2 ECS服务器信息

```
主机名: iZbp1gjjojhy45za3fij4cZ
公网IP: 121.41.224.122
区域: 华东1（杭州）
操作系统: Ubuntu 22.04 64位
```

### 6.3 部署步骤

所有操作在云服务器上完成：

```bash
# 1. 登录ECS
ssh root@121.41.224.122

# 2. 创建项目目录
mkdir -p /opt/nebulamind/{backend,frontend,ai-services,docs/database,images}

# 3. 从本地上传项目源代码和配置文件到ECS（在本地执行）
scp -r backend frontend ai-services docker-compose.prod.yml docs/database/DDL脚本.sql docs/database/seed_data.sql root@121.41.224.122:/opt/nebulamind/

# 4. 在ECS上构建Docker镜像
ssh root@121.41.224.122

cd /opt/nebulamind

# 构建后端镜像
docker build -t nebulamind-backend:latest ./backend

# 构建前端镜像
docker build -t nebulamind-frontend:latest ./frontend

# 构建AI服务镜像
docker build -t nebulamind-ai-service:latest ./ai-services

# 5. 创建环境变量文件
cat > /opt/nebulamind/.env << 'EOF'
MAAS_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
JWT_SECRET=your-production-jwt-secret-key-must-be-at-least-32-bytes-long
INTERNAL_API_KEY=your-internal-api-key
EOF

# 6. 启动服务
docker compose -f docker-compose.prod.yml up -d

# 7. 查看服务状态
docker ps

# 8. 查看日志
docker logs nebulamind-backend -f
docker logs nebulamind-frontend -f
```

### 6.4 重新构建镜像

如果需要更新镜像，登录ECS后执行：

```bash
cd /opt/nebulamind

# 重新构建所有镜像
docker build -t nebulamind-backend:latest ./backend
docker build -t nebulamind-frontend:latest ./frontend
docker build -t nebulamind-ai-service:latest ./ai-services

# 重启服务
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml up -d
```

### 6.5 服务端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端应用 | 80 | Nginx反向代理 |
| 后端API | 8080 | Spring Boot服务 |
| AI服务 | 8081 | Python FastAPI |
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| RabbitMQ | 5672 | 消息队列 |
| RabbitMQ管理 | 15672 | Web管理界面 |
| MinIO | 9000 | 对象存储API |
| MinIO控制台 | 9091 | Web管理界面 |
| Milvus | 19530 | 向量数据库 |

---

## 七、环境变量配置

### 7.1 必需配置

```bash
# JWT密钥（生产环境必须修改）
JWT_SECRET=your-production-jwt-secret-key-must-be-at-least-32-bytes-long

# 元景MaaS API Key
MAAS_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 内部API密钥
INTERNAL_API_KEY=your-internal-api-key
```

### 7.2 数据库配置

```bash
# PostgreSQL
DB_URL=jdbc:postgresql://postgres:5432/erDiagram
DB_USERNAME=root
DB_PASSWORD=<your-password>

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=
```

### 7.3 存储配置

```bash
# MinIO
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=nebulamind
MINIO_SECRET_KEY=your-minio-password
MINIO_BUCKET_NAME=nebulamind-files

# 存储类型: minio | local
NEBULAMIND_STORAGE_ENABLED=minio
```

### 7.4 消息队列配置

```bash
RABBITMQ_HOST=rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
```

---

## 八、测试计划

### 8.1 功能测试清单

| 模块 | 测试项 | 预期结果 |
|------|--------|----------|
| **认证** | 用户登录 | 返回JWT令牌 |
| | 用户登出 | Token失效 |
| **文件管理** | 上传文件 | 文件存储到MinIO |
| | 下载文件 | 正确返回文件内容 |
| | 删除文件 | 文件从存储和数据库删除 |
| | 重复检测 | 相同文件提示已存在 |
| **语义搜索** | 搜索文件 | 返回相关文件列表 |
| **文档问答** | 单文档问答 | 返回基于文档的答案 |
| | 跨文档问答 | 返回综合多文档的答案 |
| **内容生成** | 生成摘要 | 返回文档摘要 |
| | 提取关键词 | 返回关键词列表 |
| | 生成报告 | 返回结构化报告 |
| **安全** | 敏感检测 | 识别敏感信息并分级 |
| | 文件加密 | 文件加密存储 |

### 8.2 API测试示例

```bash
# 登录
curl -X POST http://121.41.224.122/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 上传文件
curl -X POST http://121.41.224.122/api/v1/files/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@test.pdf"

# 获取文件列表
curl -X GET http://121.41.224.122/api/v1/files \
  -H "Authorization: Bearer <token>"

# 语义搜索
curl -X POST http://121.41.224.122/api/v1/search \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"query":"项目进度"}'

# 文档问答
curl -X POST http://121.41.224.122/api/v1/qa \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"question":"文档中提到的关键技术是什么？","fileId":"<file-id>"}'
```

### 8.3 性能测试

| 测试项 | 指标 |
|--------|------|
| 文件上传速度 | > 1MB/s |
| API响应时间 | < 500ms（非AI操作） |
| AI响应时间 | < 30s（摘要/问答） |
| 并发用户数 | 支持100+用户同时在线 |

---

## 九、常见问题排查

### 9.1 服务启动失败

```bash
# 查看容器日志
docker logs nebulamind-backend

# 检查端口占用
ss -tlnp | grep 8080

# 检查数据库连接
docker exec nebulamind-postgres pg_isready -U root -d erDiagram

# 检查MinIO连接
curl http://localhost:9000/minio/health/live
```

### 9.2 文件上传失败

- 检查MinIO服务是否正常运行
- 检查存储空间是否充足
- 检查文件大小是否超过100MB限制
- 检查网络连接是否正常

### 9.3 AI服务调用失败

- 检查MAAS_API_KEY是否配置正确
- 检查网络是否能访问元景MaaS平台
- 检查AI服务容器是否正常运行

### 9.4 前端页面白屏

- 检查Nginx配置是否正确
- 检查后端API是否正常响应
- 检查浏览器控制台错误信息

---

## 十、后续开发建议

### 10.1 功能扩展

1. **联通云盘对接**：获取联通云盘API凭证后，实现 `UnicomCloudDriveClient` 的具体逻辑
2. **移动端App**：开发iOS/Android原生应用或使用Flutter跨端开发
3. **团队协作**：实现团队空间、共享文件夹、协作编辑功能

### 10.2 技术优化

1. **微服务拆分**：将AI服务、文件服务拆分为独立微服务
2. **Kubernetes部署**：使用K8s进行容器编排和自动扩缩容
3. **CI/CD流水线**：搭建GitHub Actions或Jenkins自动化部署流水线

### 10.3 安全增强

1. **多因素认证**：实现短信/邮箱/APP验证器多因素认证
2. **数据脱敏**：敏感数据展示时自动脱敏
3. **访问审计**：完善操作日志和访问审计功能

---

## 十一、联系方式

| 角色 | 职责 |
|------|------|
| 架构师/后端负责人 | 后端架构设计、核心功能开发 |
| 前端负责人 | 前端页面开发、用户体验优化 |
| 大模型应用工程师 | AI服务开发、模型调优 |
| 安全与协作工程师 | 安全功能开发、权限管理 |
| 测试与运维工程师 | 测试、部署、运维 |

---

**文档版本**: v1.0  
**创建日期**: 2026-07-16  
**项目状态**: 开发完成，待部署测试