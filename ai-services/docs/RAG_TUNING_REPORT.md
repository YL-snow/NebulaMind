# RAG 调优报告

**文档版本**: v1.0  
**创建日期**: 2026-07-14  
**所属项目**: 云盘智能体应用 (NebulaMind)

---

## 一、RAG 系统架构

### 1.1 整体流程

```
用户问题 → 意图分析 → 向量检索(Milvus) → BM25混合 → 
LLM Rerank → 上下文构建 → Map-Reduce(溢出处理) → LLM生成 → 答案
```

### 1.2 核心组件

| 组件 | 文件 | 功能 |
|------|------|------|
| 向量存储 | `vector_store.py` | Milvus 集合管理、Embedding 生成、索引查询 |
| 语义检索 | `semantic_search.py` | 意图识别、混合检索、LLM Rerank |
| 文档问答 | `rag_qa.py` | 上下文构建、Map-Reduce、QA 生成 |
| 文本分块 | `text_splitter.py` | 语义分块 + 固定长度约束 |

---

## 二、当前配置参数

### 2.1 检索参数

| 参数 | 当前值 | 说明 |
|------|-------|------|
| top_k | 5 (单文件) / 8 (跨文件) | 检索返回的最大文档块数 |
| 向量维度 | 4096 | qwen3-vl-embedding-8b |
| 索引类型 | IVF_FLAT | Milvus 索引 |
| 相似度度量 | COSINE | 余弦相似度 |

### 2.2 上下文窗口参数

| 参数 | 当前值 | 说明 |
|------|-------|------|
| max_chars | 16000 | 单次 LLM 调用的最大上下文长度 |
| chunk_max_chars | 4000 | Map-Reduce 单块最大长度 |
| 溢出策略 | 三级自动降级 | relevance → summary → map_reduce |

### 2.3 Rerank 参数

| 参数 | 当前值 | 说明 |
|------|-------|------|
| 策略 | LLM-based | 使用 yuanjing-70b-chat 评分 |
| 评分范围 | 0-10 | 每个文档独立评分 |
| 适用 Top-K | ≤ 10 | 超过时使用向量分数排序 |

---

## 三、测试数据集

### 3.1 测试数据来源

| 来源 | 类型 | 数量 | 状态 |
|------|------|------|------|
| MaaS API 连通性测试 | API 调用 | 6 条 | ✅ 已执行 |
| 文件理解测试 | PDF/Word/图片 | 待收集 | ⏳ 需人工标注 |
| 问答对 | 用户问题-标准答案 | 待收集 | ⏳ 需领域专家标注 |

### 3.2 快速启动测试命令

```bash
# 启动 Milvus（需 Docker）
docker run -d --name milvus -p 19530:19530 milvusdb/milvus:latest

# 启动 AI 服务
cd ai-services
pip install -r requirements.txt
python main.py

# 运行现有测试
cd backend/api-test
python final_complete_test.py
```

### 3.3 测试数据集模板

QA 测试对格式 (`test_dataset.json`):

```json
[
  {
    "question": "云盘智能体支持哪些文件格式？",
    "expected_answer": "支持 PDF、Word、Excel、图片、PPT 等格式",
    "source_files": ["doc1.pdf", "doc2.docx"],
    "category": "功能询问"
  },
  {
    "question": "如何上传文件到云盘？",
    "expected_answer": "通过前端上传界面或 API 接口上传",
    "source_files": ["api_doc.md"],
    "category": "操作询问"
  }
]
```

---

## 四、调优方向

### 4.1 短期优化（优先级高）

1. **文本分块策略调优**
   - 当前：语义分块 + 固定长度约束 (text_splitter.py)
   - 目标：根据文件类型动态调整分块大小
   - 方法：代码文件 2000 chars / 长文档 1000 chars

2. **检索 Top-K 调优**
   - 当前：5 / 8 固定值
   - 目标：根据查询意图动态调整
   - search_file → top_k=3 / general_knowledge → top_k=8

3. **Rerank 阈值调优**
   - 当前：未设置最低阈值
   - 目标：设置 score < 0.3 的文档块不参与 QA

### 4.2 中期优化（优先级中）

1. **BM25 权重动态调整**
   - 当前：vector_weight=0.7, bm25_weight=0.3 (固定)
   - 目标：根据意图动态调整 (search_content → vector 0.8 / search_file → bm25 0.6)

2. **Map-Reduce 性能优化**
   - 当前：串行 Map 调用
   - 目标：使用 asyncio.gather 并行 Map

### 4.3 评估指标

| 指标 | 当前值 | 目标值 | 测量方法 |
|------|-------|-------|---------|
| 答案准确率 | — | > 85% | 人工评估测试集 |
| 检索召回率 | — | > 90% | 标注数据验证 |
| 平均响应时间 | — | < 5s | 端到端计时 |
| 上下文溢出率 | — | < 10% | 日志统计 |

---

## 五、迭代计划

| 迭代 | 内容 | 预计效果 |
|------|------|---------|
| V1 | 基础 RAG (当前) | 可用但未优化 |
| V2 | 分块策略 + Top-K 调优 | 检索准确率 +10% |
| V3 | Rerank 阈值 + BM25 动态权重 | 答案质量 +15% |
| V4 | Map-Reduce 并行 + 缓存优化 | 响应速度 -30% |

---

**报告版本**: v1.0  
**创建日期**: 2026-07-14  
**状态**: 待执行调优迭代
