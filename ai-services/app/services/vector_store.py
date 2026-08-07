"""向量存储服务 - Milvus + 内存回退"""
import re, logging
from typing import List, Dict, Any, Optional
from collections import defaultdict
from app.core.llm_client import llm_client
from app.utils.text_splitter import TextSplitter
from app.utils.cache import CacheManager

logger = logging.getLogger(__name__)
_milvus_client = None

MILVUS_DIM = 4096  # 默认向量维度（qwen3-vl-embedding-8b）

def _get_milvus():
    global _milvus_client
    if _milvus_client is not None:
        return _milvus_client
    try:
        from pymilvus import connections, Collection, CollectionSchema, FieldSchema, DataType, utility
        from config import settings
        connections.connect(alias="default", host=settings.milvus_host, port=settings.milvus_port)
        collection_name = settings.milvus_collection
        # 自动建集合：若不存在则创建
        if not utility.has_collection(collection_name):
            logger.info(f"Creating Milvus collection: {collection_name}")
            fields = [
                FieldSchema(name="chunk_id", dtype=DataType.VARCHAR, is_primary=True, max_length=128),
                FieldSchema(name="file_id", dtype=DataType.VARCHAR, max_length=64),
                FieldSchema(name="chunk_index", dtype=DataType.INT64),
                FieldSchema(name="chunk_text", dtype=DataType.VARCHAR, max_length=65535),
                FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=MILVUS_DIM),
            ]
            schema = CollectionSchema(fields, description="NebulaMind semantic index")
            _milvus_client = Collection(name=collection_name, schema=schema)
            # 创建 IVF_FLAT 索引
            _milvus_client.create_index(field_name="embedding",
                                         index_params={"metric_type": "COSINE",
                                                       "index_type": "IVF_FLAT",
                                                       "params": {"nlist": 128}})
            logger.info(f"Milvus collection created: {collection_name}")
        else:
            _milvus_client = Collection(collection_name)
        _milvus_client.load()
        logger.info(f"Milvus loaded: {collection_name}")
    except Exception as e:
        logger.warning(f"Milvus unavailable: {e}, using in-memory")
        _milvus_client = False
    return _milvus_client


class VectorStoreService:
    _memory_vectors = {}
    _memory_texts = {}
    _splitter = TextSplitter()

    @classmethod
    def index_file(cls, file_id, file_name, content):
        chunks = cls._splitter.split(content, metadata={"file_id": file_id, "file_name": file_name})
        if not chunks:
            return []
        for chunk in chunks:
            # 使用 Embedding 缓存
            cache_key = CacheManager.get_embedding_cache_key(chunk["text"])
            cached_emb = CacheManager.get(cache_key)
            if cached_emb:
                embedding_vec = cached_emb
            else:
                embedding = llm_client.get_embedding(chunk["text"], file_id=file_id)
                embedding_vec = embedding.embedding
                CacheManager.set(cache_key, embedding_vec, ttl=86400)  # 缓存 24h
            chunk["embedding"] = embedding_vec
            chunk_id = f"{file_id}_{chunk['index']}"
            milvus = _get_milvus()
            if milvus:
                try:
                    milvus.insert([{
                        "chunk_id": chunk_id, "file_id": file_id,
                        "chunk_index": chunk["index"],
                        "chunk_text": chunk["text"][:65535],
                        "embedding": embedding_vec
                    }])
                except Exception as e:
                    logger.warning(f"Milvus insert failed: {e}")
            cls._memory_vectors[chunk_id] = embedding_vec
            cls._memory_texts[chunk_id] = {
                "file_id": file_id, "file_name": file_name,
                "chunk_index": chunk["index"], "text": chunk["text"],
                "metadata": chunk["metadata"]
            }
        logger.info(f"Indexed {len(chunks)} chunks for {file_id}")
        return chunks

    @classmethod
    def delete_file(cls, file_id):
        milvus = _get_milvus()
        if milvus:
            try:
                milvus.delete(f'file_id == "{file_id}"')
            except:
                pass
        for k in list(cls._memory_vectors.keys()):
            if k.startswith(file_id):
                del cls._memory_vectors[k]
                del cls._memory_texts[k]

    @classmethod
    def search(cls, query, file_ids=None, top_k=10):
        query_emb = llm_client.get_embedding(query)
        milvus = _get_milvus()
        if milvus:
            try:
                expr = None
                if file_ids:
                    expr = f'file_id in [{", ".join(chr(34)+fid+chr(34) for fid in file_ids)}]'
                results = milvus.search(
                    data=[query_emb.embedding], anns_field="embedding",
                    param={"metric_type": "COSINE", "params": {"nprobe": 10}},
                    limit=top_k, expr=expr,
                    output_fields=["file_id", "chunk_index", "chunk_text"]
                )
                if results and results[0]:
                    return [{
                        "file_id": h.entity.get("file_id", ""),
                        "chunk_text": h.entity.get("chunk_text", ""),
                        "chunk_index": h.entity.get("chunk_index", 0),
                        "score": float(h.distance),
                        "snippet": h.entity.get("chunk_text", "")[:200]
                    } for h in results[0]]
            except Exception as e:
                logger.warning(f"Milvus search failed: {e}")
        return cls._memory_search(query_emb.embedding, file_ids, top_k)

    @classmethod
    def _memory_search(cls, query_emb, file_ids=None, top_k=10):
        scored = []
        for cid, emb in cls._memory_vectors.items():
            if file_ids and not any(cid.startswith(fid) for fid in file_ids):
                continue
            dot = sum(x * y for x, y in zip(query_emb, emb))
            na = sum(x * x for x in query_emb) ** 0.5
            nb = sum(x * x for x in emb) ** 0.5
            score = dot / (na * nb) if na and nb else 0
            scored.append((cid, score))
        scored.sort(key=lambda x: x[1], reverse=True)
        return [{
            "file_id": cls._memory_texts.get(cid, {}).get("file_id", ""),
            "file_name": cls._memory_texts.get(cid, {}).get("file_name", ""),
            "chunk_text": cls._memory_texts.get(cid, {}).get("text", ""),
            "chunk_index": cls._memory_texts.get(cid, {}).get("chunk_index", 0),
            "score": float(s), "snippet": cls._memory_texts.get(cid, {}).get("text", "")[:200]
        } for cid, s in scored[:top_k]]

    @classmethod
    def bm25_search(cls, query, file_ids=None, top_k=10):
        query_terms = set(re.findall(r'[一-鿿]+|[a-zA-Z]+', query.lower()))
        scores = defaultdict(float)
        for cid, info in cls._memory_texts.items():
            if file_ids and info.get("file_id") not in file_ids:
                continue
            text_lower = info.get("text", "").lower()
            dl = max(len(text_lower), 1)
            for term in query_terms:
                scores[cid] += text_lower.count(term) / dl
        ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:top_k]
        return [{
            "file_id": cls._memory_texts.get(cid, {}).get("file_id", ""),
            "file_name": cls._memory_texts.get(cid, {}).get("file_name", ""),
            "chunk_text": cls._memory_texts.get(cid, {}).get("text", ""),
            "score": float(s),
            "snippet": cls._memory_texts.get(cid, {}).get("text", "")[:200]
        } for cid, s in ranked]
