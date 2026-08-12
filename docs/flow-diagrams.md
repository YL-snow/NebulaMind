# 核心流程与状态图

本文用图说明 NebulaMind 的 AI 摘要、文档问答、文件版本状态与用户交互流程。

## 1. AI 摘要时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant FE as 前端
    participant BE as 后端
    participant ST as 对象存储
    participant AI as AI 服务 / 元景 MaaS

    U->>FE: 打开文件详情
    FE->>BE: POST /api/v1/generate/summary
    BE->>ST: 读取文件内容或图片
    ST-->>BE: 返回文件数据
    alt 图片 / 图片型 PPT
        BE->>AI: 视觉模型分析图片
    else 文本 / Office / PDF
        BE->>AI: 文本解析或 OCR 后生成摘要
    end
    AI-->>BE: 返回摘要内容
    BE->>BE: 保存 summary，AiStatus 更新为 COMPLETED
    BE-->>FE: 返回摘要
    FE-->>U: 展示摘要
    Note over BE,AI: 限流 5 次 / 分钟时返回 RATE_LIMITED 提示
```

## 2. 文档问答 / 检索时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant FE as 前端
    participant BE as 后端
    participant ST as 对象存储
    participant AI as AI 服务 / 元景 MaaS

    U->>FE: 输入问题
    FE->>BE: POST /api/v1/qa 或 /api/v1/qa/cross
    BE->>ST: 读取单文件或多文件内容
    ST-->>BE: 返回文件内容
    BE->>AI: 携带问题与文件内容调用问答服务
    AI-->>BE: 返回答案、来源片段与置信度
    BE-->>FE: 返回回答
    FE-->>U: 展示答案与来源
    Note over BE,AI: 端到端加密文件无法被服务端读取，返回明确提示
```

## 3. 文件处理状态机

文件上传与解析状态：

```mermaid
stateDiagram-v2
    [*] --> UPLOADING: 开始上传
    UPLOADING --> PROCESSING: 上传完成
    PROCESSING --> COMPLETED: 解析完成
    PROCESSING --> FAILED: 处理失败
    COMPLETED --> PROCESSING: 上传新版本后重新处理
    FAILED --> COMPLETED: 重试成功
```

AI 处理状态：

```mermaid
stateDiagram-v2
    [*] --> PENDING: 新文件或新版本
    PENDING --> PROCESSING: 生成摘要 / 问答
    PROCESSING --> COMPLETED: 成功并保存结果
    PROCESSING --> FAILED: 失败或限流
    COMPLETED --> PENDING: 上传新版本后重新生成
    FAILED --> PROCESSING: 用户重试
```

## 4. 用户交互流程

```mermaid
flowchart TD
    A[登录 / 注册] --> B[文件概览]
    B -->|上传文件| C[上传 / 分片上传]
    C --> D[文件卡片与类型识别]
    B --> E[文件详情]
    E --> F[AI 摘要]
    E --> G[文档问答]
    E --> H[版本历史 / 对比 / 回滚]
    E --> I[安全检测 / 加密]
    B --> J[内容生成]
    B --> K[云存储配置]
```

核心页面：文件概览、文件详情、内容生成、安全管理、云存储配置。用户上传文件后可进入文件详情完成摘要、问答、版本管理和安全操作。
