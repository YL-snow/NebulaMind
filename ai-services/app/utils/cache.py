"""Redis + 内存缓存"""
import json, hashlib, logging, time as _time
from typing import Optional, Any
from config import settings
logger = logging.getLogger(__name__)
_redis_client = None

def _get_redis():
    global _redis_client
    if _redis_client is not None: return _redis_client
    try:
        import redis
        _redis_client = redis.Redis(host=settings.redis_host, port=settings.redis_port, password=settings.redis_password or None, decode_responses=True, socket_connect_timeout=3)
        _redis_client.ping(); logger.info("Redis connected")
    except Exception as e:
        logger.warning(f"Redis unavailable: {e}, using memory"); _redis_client = False
    return _redis_client

class CacheManager:
    _memory_cache = {}; _memory_ttl = {}
    @classmethod
    def get(cls, key):
        redis = _get_redis()
        if redis:
            try:
                v = redis.get(f"ai:{key}")
                if v: return json.loads(v)
            except: pass
        ck = f"ai:{key}"
        if ck in cls._memory_cache:
            if cls._memory_ttl.get(ck, 0) > _time.time() or cls._memory_ttl.get(ck, 0) == 0: return cls._memory_cache[ck]
            del cls._memory_cache[ck]
        return None
    @classmethod
    def set(cls, key, value, ttl=None):
        if ttl is None: ttl = settings.redis_cache_ttl
        redis = _get_redis()
        if redis:
            try: redis.setex(f"ai:{key}", ttl, json.dumps(value, ensure_ascii=False, default=str)); return
            except: pass
        ck = f"ai:{key}"; cls._memory_cache[ck] = value; cls._memory_ttl[ck] = _time.time() + ttl
    @classmethod
    def delete(cls, key):
        redis = _get_redis()
        if redis:
            try: redis.delete(f"ai:{key}")
            except: pass
        cls._memory_cache.pop(f"ai:{key}", None)
    @classmethod
    def get_embedding_cache_key(cls, text): return f"emb:{hashlib.md5(text.encode()).hexdigest()}"
    @classmethod
    def get_search_cache_key(cls, query, file_ids): return f"search:{hashlib.md5((query + ','.join(sorted(file_ids or []))).encode()).hexdigest()}"
    @classmethod
    def get_qa_cache_key(cls, file_id, question): return f"qa:{hashlib.md5((file_id + question).encode()).hexdigest()}"
