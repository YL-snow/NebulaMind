# 模型调用日志分析报告

**文档版本**: v1.0  
**创建日期**: 2026-07-14  
**所属项目**: 云盘智能体应用 (NebulaMind)

---

## 一、日志系统概述

### 1.1 日志记录能力

模型调用日志系统已实现在 `llm_client.py` 中，所有 LLM API 调用自动记录：

```python
class CallLogEntry:
    def __init__(self, request_id, module, model, prompt, response, 
                 token_usage, latency_ms, success, error_message=None,
                 file_id=None, user_id=None):
        # 完整记录每次调用的详细信息
```

### 1.2 核心接口

```python
# 导出所有日志（JSON 格式）
llm_client.export_call_logs()

# 获取统计摘要
llm_client.get_statistics()
# 返回: {
#   "total_calls": N,
#   "success_rate": 0.95,
#   "total_tokens": 50000,
#   "avg_latency_ms": 2500,
#   "by_module": {
#       "document_qa": {"count": 10, "tokens": 5000, "latency": 25000},
#       ...
#   }
# }
```

---

## 二、日志字段说明

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| requestId | string | 唯一请求 ID | uuid-v4 |
| module | string | 调用模块 | document_qa / file_classify / summary / sensitive_detect |
| model | string | 使用的模型 | yuanjing-70b-chat |
| prompt | string | 输入 Prompt（前 5000 chars） | ... |
| response | string | 模型输出（前 5000 chars） | ... |
| tokenUsage.promptTokens | int | Prompt Token 数 | 1500 |
| tokenUsage.completionTokens | int | 生成 Token 数 | 300 |
| tokenUsage.totalTokens | int | 总 Token 数 | 1800 |
| latencyMs | int | 延迟（毫秒） | 2860 |
| success | bool | 是否成功 | true |
| errorMessage | string | 错误信息（失败时） | Connection timeout |
| fileId | string | 关联文件 ID | file-xxx |
| userId | string | 关联用户 ID | user-xxx |

---

## 三、Module 分类

| Module | 说明 | 涉及的模型 |
|--------|------|-----------|
| `document_qa` | 文档问答 | yuanjing-70b-chat |
| `file_classify` | 文件分类 + 标签 | yuanjing-70b-chat |
| `summary_compress` | 摘要压缩 | yuanjing-70b-chat |
| `sensitive_detect` | 敏感信息检测 | yuanjing-70b-chat |
| `map_reduce_map` | Map-Reduce Map 阶段 | yuanjing-70b-chat |
| `map_reduce_reduce` | Map-Reduce Reduce 阶段 | yuanjing-70b-chat |
| `content_generate` | 报告/PPT 生成 | yuanjing-70b-chat |
| `embedding` | 文本向量化 | qwen3-vl-embedding-8b |
| `rerank` | 语义重排序 | yuanjing-70b-chat |

---

## 四、日志导出和使用

### 4.1 导出日志

```python
from app.core.llm_client import llm_client
import json

# 导出所有调用日志
logs = llm_client.export_call_logs()
with open("model_call_logs.json", "w", encoding="utf-8") as f:
    json.dump(logs, f, ensure_ascii=False, indent=2)

# 统计信息
stats = llm_client.get_statistics()
print(f"总调用: {stats['total_calls']}")
print(f"成功率: {stats['success_rate']*100:.1f}%")
print(f"总 Tokens: {stats['total_tokens']}")
print(f"平均延迟: {stats['avg_latency_ms']}ms")

# 各模块详情
for module, data in stats['by_module'].items():
    print(f"  {module}: {data['count']}次, {data['tokens']} tokens, {data['latency']//data['count']}ms/次")
```

### 4.2 日志分析示例

```python
# 分析慢查询
logs = llm_client.export_call_logs()
slow_queries = [log for log in logs if log["latencyMs"] > 5000]
print(f"慢查询 (>5s): {len(slow_queries)} 次")

# 分析各模块 Token 消耗
from collections import Counter
module_tokens = Counter()
for log in logs:
    module_tokens[log["module"]] += log["tokenUsage"]["totalTokens"]
print("Token 消耗 Top 模块:", module_tokens.most_common(5))
```

---

## 五、API 调用限制

根据 MaaS 平台邮件通知，当前 API 调用限制如下：

| 限制项 | 限制值 | 影响分析 |
|-------|-------|---------|
| 调用频率 | 每个接口 1 分钟 5 次 | 并发场景需限流 |
| Token 限制 | Chat: 24K tokens | 上下文窗口需控制 |

### 5.1 限流实现建议

```python
import asyncio
from datetime import datetime, timedelta

class RateLimiter:
    def __init__(self, max_calls=5, period=60):
        self.max_calls = max_calls
        self.period = period
        self.calls = []
    
    async def acquire(self):
        now = datetime.now()
        self.calls = [t for t in self.calls if now - t < timedelta(seconds=self.period)]
        if len(self.calls) >= self.max_calls:
            wait = timedelta(seconds=self.period) - (now - self.calls[0])
            await asyncio.sleep(wait.total_seconds())
        self.calls.append(now)
```

---

## 六、日志分析报告模板

### 6.1 报告结构

1. **调用总览**: 总调用次数、成功率、总 Tokens、平均延迟
2. **模块分布**: 各模块调用占比、Token 消耗占比
3. **模型分布**: 各模型调用次数、成功率对比
4. **性能分析**: P50/P95/P99 延迟、慢查询分布
5. **错误分析**: 错误类型、错误率趋势
6. **成本估算**: Token 消耗趋势、预估成本
7. **优化建议**: 基于日志数据的优化方向

### 6.2 实际运行后生成报告的命令

```bash
# 运行 AI 服务生成测试数据
cd ai-services
python main.py &
sleep 5

# 运行一系列测试
python -c "
from app.core.llm_client import llm_client
import json

# 模拟一些调用
llm_client.chat([{'role':'user','content':'测试'}], module='test')
llm_client.get_embedding('测试文本')

# 生成报告
stats = llm_client.get_statistics()
with open('call_log_report.json', 'w') as f:
    json.dump(stats, f, ensure_ascii=False, indent=2)
print('报告已生成')
"
```

---

## 七、当前状态

| 项目 | 状态 |
|------|------|
| 日志记录代码 | ✅ 已实现 (`llm_client.py`) |
| 日志导出接口 | ✅ `export_call_logs()` |
| 统计摘要接口 | ✅ `get_statistics()` |
| 日志存储 | ⏳ 日志仅存储于内存，重启后丢失 |
| 持久化存储 | ⏳ 待接入文件/数据库存储 |
| 可视化仪表盘 | ⏳ 待开发 |

---

**报告版本**: v1.0  
**创建日期**: 2026-07-14  
**状态**: 日志基础设施已就绪，待运行收集实际数据
