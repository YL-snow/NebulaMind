"""语义搜索服务 - 混合检索 + 重排序"""
import re, logging
from typing import List, Dict, Optional
from app.core.llm_client import llm_client
from app.services.vector_store import VectorStoreService
from app.utils.cache import CacheManager

logger = logging.getLogger(__name__)

class SemanticSearchService:

    # 意图识别关键词模式
    _QUERY_INTENT_PATTERNS = {
        "search_file": [r"搜索.*文件", r"查找.*文档", r"找.*文件", r"文件.*在哪", r"哪里.*文件"],
        "search_content": [r"关于.*内容", r"包含.*的.*文档", r"什么.*提到", r"谁.*说过", r"文档.*说"],
        "search_category": [r"分[类组].*文件", r"属于.*类[别型]", r"显示.*类[别型]"],
        "search_recent": [r"最近.*(文件|修改)", r"最新.*文件", r"今天.*上传"],
        "general_knowledge": [r"什么是", r"解释一下", r"怎么.*做", r"如何.*操作"],
    }

    @staticmethod
    def _analyze_intent(query: str) -> Dict:
        """分析查询意图"""
        intent = {"type": "file_search", "keywords": [], "file_type": None}
        for intent_type, patterns in SemanticSearchService._QUERY_INTENT_PATTERNS.items():
            for pattern in patterns:
                m = re.search(pattern, query)
                if m:
                    intent["type"] = intent_type
                    break
        # 提取关键词（去除停用词）
        stop_words = {"的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都",
                      "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你",
                      "会", "着", "没有", "看", "好", "自己", "这", "那", "什么",
                      "怎么", "吗", "吧", "啊", "呢", "哦", "啦", "文件", "文档"}
        words = re.findall(r'[一-鿿]{2,}|[a-zA-Z]{3,}', query)
        intent["keywords"] = [w for w in words if w not in stop_words]
        return intent

    @staticmethod
    def search(query, file_ids=None, top_k=10):
        cache_key = CacheManager.get_search_cache_key(query, file_ids)
        cached = CacheManager.get(cache_key)
        if cached:
            return cached

        # 1) 查询意图分析
        intent = SemanticSearchService._analyze_intent(query)
        logger.info(f"Search intent: {intent['type']}, keywords: {intent['keywords'][:5]}")

        # 根据意图动态调整检索参数
        if intent["type"] in ("search_recent", "search_category"):
            # 这类查询用 BM25 权重更高
            bm25_weight = 0.5
            vector_weight = 0.5
        else:
            bm25_weight = 0.3
            vector_weight = 0.7

        # 2) 混合检索
        vector_results = VectorStoreService.search(query, file_ids, top_k=15)
        bm25_results = VectorStoreService.bm25_search(query, file_ids, top_k=15)

        merged = {}
        mxv = max((r.get("score", 0) for r in vector_results), default=1)
        mxb = max((r.get("score", 0) for r in bm25_results), default=1)
        for r in vector_results:
            cid = f"{r.get('file_id')}_{r.get('chunk_index')}"
            merged[cid] = {**r, "score": r.get("score", 0) / max(mxv, 0.01) * vector_weight}
        for r in bm25_results:
            cid = f"{r.get('file_id')}_{r.get('chunk_index')}"
            ns = r.get("score", 0) / max(mxb, 0.01) * bm25_weight
            if cid in merged:
                merged[cid]["score"] += ns
            else:
                merged[cid] = {**r, "score": ns}
        merged_list = sorted(merged.values(), key=lambda x: x["score"], reverse=True)

        # 3) Rerank 重排序（修复：按 chunk_id 去重而非 file_id）
        if merged_list:
            docs = [r.get("chunk_text", "") for r in merged_list[:30]]
            reranked = llm_client.rerank(query, docs, top_n=top_k)
            seen_chunks = set()
            final = []
            for rr in reranked:
                if rr.index < len(merged_list):
                    item = merged_list[rr.index]
                    chunk_id = f"{item.get('file_id')}_{item.get('chunk_index')}"
                    if chunk_id not in seen_chunks:
                        seen_chunks.add(chunk_id)
                        item["score"] = rr.score
                        final.append(item)
                        if len(final) >= top_k:
                            break
            merged_list = final or merged_list[:top_k]
        else:
            merged_list = merged_list[:top_k]

        # 4) 构建结果
        results = []
        for item in merged_list:
            results.append({
                "file_id": item.get("file_id", ""),
                "file_name": item.get("file_name", ""),
                "snippet": item.get("snippet", "")[:200],
                "score": round(item.get("score", 0.5), 4),
                "category": item.get("metadata", {}).get("category", ""),
            })

        response = {"query": query, "results": results}
        CacheManager.set(cache_key, response, ttl=180)
        return response
