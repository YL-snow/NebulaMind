"""
大模型客户端 - 多提供商切换 (OpenAI/Wanwu)
仅使用真实大模型服务，不支持Mock模式
"""
import time, json, uuid, logging, threading, os
from abc import ABC, abstractmethod
from collections import deque
from typing import Optional, List, Dict, Any
from config import settings

logger = logging.getLogger(__name__)

class LLMResponse:
    def __init__(self, content, model, prompt_tokens=0, completion_tokens=0, total_tokens=0, latency_ms=0):
        self.content = content; self.model = model
        self.prompt_tokens = prompt_tokens; self.completion_tokens = completion_tokens
        self.total_tokens = total_tokens; self.latency_ms = latency_ms

class EmbeddingResponse:
    def __init__(self, embedding, model, total_tokens=0, latency_ms=0):
        self.embedding = embedding; self.model = model
        self.total_tokens = total_tokens; self.latency_ms = latency_ms

class RerankResult:
    def __init__(self, index, score, document=""):
        self.index = index; self.score = score; self.document = document

class CallLogEntry:
    def __init__(self, request_id, module, model, prompt, response, token_usage, latency_ms, success, error_message=None, file_id=None, user_id=None):
        self.request_id = request_id; self.module = module; self.model = model
        self.prompt = prompt; self.response = response
        self.prompt_tokens = token_usage.get("prompt_tokens", 0)
        self.completion_tokens = token_usage.get("completion_tokens", 0)
        self.total_tokens = token_usage.get("total_tokens", 0)
        self.latency_ms = latency_ms; self.success = success
        self.error_message = error_message; self.file_id = file_id; self.user_id = user_id

class BaseLLMProvider(ABC):
    @abstractmethod
    def chat(self, messages, **kwargs) -> LLMResponse: ...
    @abstractmethod
    def get_embedding(self, text) -> EmbeddingResponse: ...
    @abstractmethod
    def rerank(self, query, documents, top_n=5) -> List[RerankResult]: ...

class WanwuProvider(BaseLLMProvider):
    def __init__(self):
        import httpx
        self.base_url = settings.wanwu_base_url; self.api_key = settings.wanwu_api_key
        self.llm_model = settings.wanwu_llm_model; self.embedding_model = settings.wanwu_embedding_model
        self.reranker_model = settings.wanwu_reranker_model
        self._client = httpx.Client(timeout=60.0); self._available = bool(self.api_key)
        if not self._available:
            raise RuntimeError("Wanwu API key not configured")

    def _headers(self): return {"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"}

    def chat(self, messages, **kwargs):
        start = time.time()
        resp = self._client.post(f"{self.base_url}/chat/completions", headers=self._headers(), json={"model": kwargs.get("model", self.llm_model), "messages": messages, "temperature": kwargs.get("temperature", 0.7), "max_tokens": kwargs.get("max_tokens", 4096)})
        data = resp.json(); elapsed = int((time.time() - start) * 1000)
        choice = data["choices"][0]; usage = data.get("usage", {})
        return LLMResponse(content=choice["message"]["content"], model=data.get("model", self.llm_model), prompt_tokens=usage.get("prompt_tokens", 0), completion_tokens=usage.get("completion_tokens", 0), total_tokens=usage.get("total_tokens", 0), latency_ms=elapsed)

    def get_embedding(self, text):
        start = time.time()
        resp = self._client.post(f"{self.base_url}/embeddings", headers=self._headers(), json={"model": self.embedding_model, "input": text})
        data = resp.json(); elapsed = int((time.time() - start) * 1000)
        return EmbeddingResponse(embedding=data["data"][0]["embedding"], model=data.get("model", self.embedding_model), total_tokens=data.get("usage", {}).get("total_tokens", 0), latency_ms=elapsed)

    def rerank(self, query, documents, top_n=5):
        resp = self._client.post(f"{self.base_url}/rerank", headers=self._headers(), json={"model": self.reranker_model, "query": query, "documents": documents})
        data = resp.json()
        results = sorted(data.get("results", []), key=lambda x: x.get("score", 0), reverse=True)
        return [RerankResult(index=r.get("index", 0), score=r.get("score", 0.0), document=documents[r.get("index", 0)] if r.get("index", 0) < len(documents) else "") for r in results[:top_n]]

class OpenAICompatibleProvider(BaseLLMProvider):
    def __init__(self):
        try:
            from openai import OpenAI
            self.client = OpenAI(base_url=settings.openai_base_url, api_key=settings.openai_api_key)
            self.llm_model = settings.openai_llm_model; self.embedding_model = settings.openai_embedding_model
            self._available = True
            # 速率限制：5次/分钟（MaaS API 限流策略）
            self._rate_limit_max = int(os.getenv("MaaS_RATE_LIMIT_MAX", "10"))
            self._rate_limit_window = int(os.getenv("MaaS_RATE_LIMIT_WINDOW", "60"))  # seconds
            # 分别为 chat 和 embedding 维护独立的速率限制
            self._chat_timestamps = deque()
            self._embed_timestamps = deque()
            self._chat_lock = threading.Lock()
            self._embed_lock = threading.Lock()
        except Exception as e:
            raise RuntimeError(f"Failed to initialize OpenAI provider: {e}")

    def _wait_rate_limit(self, timestamps, lock, name="chat"):
        """主动速率限制：每分钟最多5次调用。超出时等待窗口重置"""
        with lock:
            now = time.time()
            while timestamps and timestamps[0] <= now - self._rate_limit_window:
                timestamps.popleft()

            if len(timestamps) >= self._rate_limit_max:
                wait = timestamps[0] + self._rate_limit_window - now + 1.0
                if wait > 0:
                    logger.info(f"[{name}] 速率限制：已达{self._rate_limit_max}次/分钟上限，等待{wait:.1f}s")
                    time.sleep(wait)
                timestamps.clear()

            timestamps.append(time.time())

    def _chat_with_retry(self, messages, max_retries=6, **kwargs):
        """带速率限制和指数退避重试的 chat 调用（MaaS QPM 最大 5次/分钟）"""
        last_exception = None
        for attempt in range(max_retries):
            try:
                self._wait_rate_limit(self._chat_timestamps, self._chat_lock, "chat")
                start = time.time()
                resp = self.client.chat.completions.create(
                    model=kwargs.get("model", self.llm_model),
                    messages=messages,
                    temperature=kwargs.get("temperature", 0.7),
                    max_tokens=kwargs.get("max_tokens", 4096)
                )
                elapsed = int((time.time() - start) * 1000)
                return LLMResponse(
                    content=resp.choices[0].message.content,
                    model=resp.model,
                    prompt_tokens=resp.usage.prompt_tokens,
                    completion_tokens=resp.usage.completion_tokens,
                    total_tokens=resp.usage.total_tokens,
                    latency_ms=elapsed
                )
            except Exception as e:
                last_exception = e
                error_str = str(e)
                if "429" in error_str or "Too Many Requests" in error_str or "QPM" in error_str:
                    wait = 10 * (2 ** attempt)  # 10, 20, 40, 80, 160, 320s
                    logger.warning(f"MaaS API 429 限流 (chat, attempt {attempt+1}/{max_retries}), 等待{wait}s: {e}")
                    with self._chat_lock:
                        self._chat_timestamps.clear()
                    if attempt < max_retries - 1:
                        time.sleep(wait)
                else:
                    raise
        logger.warning(f"MaaS API 429 重试耗尽({max_retries}次)，返回限流提示")
        return LLMResponse(content="RATE_LIMITED:API调用次数已达上限，请等待1分钟后再试。当前限制：每分钟最多{0}次调用。".format(self._rate_limit_max),
                          model=self.llm_model, latency_ms=0)

    def chat(self, messages, **kwargs):
        return self._chat_with_retry(messages, **kwargs)

    def get_embedding(self, text):
        self._wait_rate_limit(self._embed_timestamps, self._embed_lock, "embedding")
        start = time.time()
        resp = self.client.embeddings.create(model=self.embedding_model, input=text)
        elapsed = int((time.time() - start) * 1000)
        return EmbeddingResponse(embedding=resp.data[0].embedding, model=resp.model, total_tokens=resp.usage.total_tokens, latency_ms=elapsed)

    def rerank(self, query, documents, top_n=5):
        """LLM-based重排序：MaaS平台无独立Rerank端点，使用Chat模型打分"""
        results = []
        for i, doc in enumerate(documents):
            try:
                self._wait_rate_limit(self._chat_timestamps, self._chat_lock, "rerank")
                resp = self.client.chat.completions.create(model=self.llm_model, messages=[{"role": "system", "content": "评估查询与文档的相关性，仅返回0到1之间的数字。"}, {"role": "user", "content": f"查询: {query}\n文档: {doc[:1000]}\n相关性分数(0-1):"}], temperature=0, max_tokens=10)
                score = float(resp.choices[0].message.content.strip().strip("."))
                score = max(0.0, min(1.0, score))
            except Exception:
                score = 0.0
            results.append(RerankResult(index=i, score=score, document=doc))
        results.sort(key=lambda x: x.score, reverse=True)
        return results[:top_n]

class LLMClient:
    def __init__(self):
        self.provider = self._create_provider(); self.call_logs = []
        logger.info(f"LLM Client initialized: {settings.llm_provider}")

    def _create_provider(self):
        if settings.llm_provider == "openai":
            return OpenAICompatibleProvider()
        elif settings.llm_provider == "wanwu":
            return WanwuProvider()
        else:
            raise ValueError(f"Unsupported LLM provider: {settings.llm_provider}. Must be 'openai' or 'wanwu'.")

    def chat(self, messages, module="general", file_id=None, user_id=None, **kwargs):
        request_id = str(uuid.uuid4())
        is_fallback = False
        try:
            response = self.provider.chat(messages, **kwargs)
            success = True
            error_msg = None
            # 检测是否为降级响应
            if response and "暂时不可用" in response.content:
                is_fallback = True
                success = False
                error_msg = "服务暂时不可用（降级响应）"
        except Exception as e:
            logger.error(f"LLM chat failed: {e}")
            success = False
            error_msg = str(e)
            response = LLMResponse(content="AI 服务暂时不可用，请稍后重试。如果问题持续，请联系管理员。",
                                  model="unknown", latency_ms=0)
            is_fallback = True
        self.call_logs.append(CallLogEntry(request_id=request_id, module=module, model=response.model if response else "unknown", prompt=json.dumps(messages, ensure_ascii=False)[:5000], response=(response.content if response else "")[:5000], token_usage={"prompt_tokens": response.prompt_tokens if response else 0, "completion_tokens": response.completion_tokens if response else 0, "total_tokens": response.total_tokens if response else 0}, latency_ms=response.latency_ms if response else 0, success=success, error_message=error_msg, file_id=file_id, user_id=user_id))
        return response

    def get_embedding(self, text, file_id=None):
        return self.provider.get_embedding(text)

    def rerank(self, query, documents, top_n=5):
        return self.provider.rerank(query, documents, top_n)

    def get_statistics(self):
        total = len(self.call_logs)
        if total == 0: return {"total_calls": 0, "success_rate": 0.0, "total_tokens": 0, "avg_latency_ms": 0, "by_module": {}}
        sc = sum(1 for l in self.call_logs if l.success)
        tt = sum(l.total_tokens for l in self.call_logs)
        tl = sum(l.latency_ms for l in self.call_logs)
        ms = {}
        for l in self.call_logs:
            if l.module not in ms: ms[l.module] = {"count": 0, "tokens": 0, "latency": 0}
            ms[l.module]["count"] += 1; ms[l.module]["tokens"] += l.total_tokens; ms[l.module]["latency"] += l.latency_ms
        return {"total_calls": total, "success_rate": round(sc/max(total,1), 4), "total_tokens": tt, "avg_latency_ms": tl//max(total,1), "by_module": ms}

    def export_call_logs(self):
        return [{"id": l.request_id, "requestId": l.request_id, "module": l.module, "model": l.model, "prompt": l.prompt, "response": l.response, "tokenUsage": {"promptTokens": l.prompt_tokens, "completionTokens": l.completion_tokens, "totalTokens": l.total_tokens}, "latencyMs": l.latency_ms, "success": l.success, "errorMessage": l.error_message, "fileId": l.file_id, "userId": l.user_id} for l in self.call_logs]

llm_client = LLMClient()
