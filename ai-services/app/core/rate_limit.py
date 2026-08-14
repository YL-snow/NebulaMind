"""Redis-backed rate limiter shared by all AI workers."""
import threading
import time
import logging

from config import settings

logger = logging.getLogger(__name__)


class RateLimitTimeout(Exception):
    """Raised when the shared rate limit budget cannot be acquired in time."""


class SharedRateLimiter:
    def __init__(self):
        self._redis = None
        self._lock = threading.Lock()
        self._local = {}

    def _get_redis(self):
        if self._redis is not None:
            return self._redis
        try:
            import redis
            client = redis.Redis(
                host=settings.redis_host,
                port=settings.redis_port,
                password=settings.redis_password or None,
                decode_responses=True,
                socket_connect_timeout=3,
            )
            client.ping()
            self._redis = client
            logger.info("Shared rate limiter connected to Redis")
        except Exception as e:
            logger.warning(f"Shared rate limiter Redis unavailable, using local fallback: {e}")
            self._redis = False
        return self._redis

    def acquire(self, name, max_wait=300):
        limit = settings.rate_limit_max
        window = settings.rate_limit_window
        redis = self._get_redis()
        if redis:
            return self._acquire_redis(redis, name, limit, window, max_wait)
        return self._acquire_local(name, limit, window, max_wait)

    def _acquire_redis(self, redis, name, limit, window, max_wait):
        deadline = time.time() + max_wait
        while True:
            now = time.time()
            bucket = int(now // window)
            key = f"ai:ratelimit:{name}:{bucket}"
            try:
                count = redis.incr(key)
                if count == 1:
                    redis.expire(key, window * 2)
            except Exception as e:
                logger.warning(f"Redis rate limit incr failed, using local fallback: {e}")
                return self._acquire_local(name, limit, window, max_wait)

            if count <= limit:
                return True

            redis.decr(key)
            next_start = (bucket + 1) * window
            wait = next_start - now + 1.0
            remaining = deadline - time.time()
            if wait > remaining or wait > max_wait:
                logger.warning(f"[{name}] rate limit reached, needs {wait:.1f}s, over budget")
                return False
            logger.info(f"[{name}] rate limit {limit}/{window}s, waiting {wait:.1f}s")
            time.sleep(min(wait, remaining))

    def _acquire_local(self, name, limit, window, max_wait):
        with self._lock:
            now = time.time()
            timestamps = self._local.setdefault(name, [])
            while timestamps and timestamps[0] <= now - window:
                timestamps.pop(0)
            if len(timestamps) >= limit:
                wait = timestamps[0] + window - now + 1.0
                if wait > max_wait:
                    logger.warning(f"[{name}] local rate limit reached, needs {wait:.1f}s, over budget")
                    return False
                logger.info(f"[{name}] local rate limit {limit}/{window}s, waiting {wait:.1f}s")
                time.sleep(wait)
                timestamps.clear()
            timestamps.append(time.time())
            return True


shared_limiter = SharedRateLimiter()
