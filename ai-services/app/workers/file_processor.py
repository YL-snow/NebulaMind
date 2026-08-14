"""RabbitMQ Worker - 文件处理消费者"""
import json
import logging
import threading
from config import settings
logger = logging.getLogger(__name__)

ARCHIVE_EXTENSIONS = {"zip", "rar", "7z", "gz", "tar", "bz2", "xz", "tgz"}

class FileProcessorWorker:
    def __init__(self):
        self._running = False
        self._thread = None
        self._redis = None
        self._local_locks = set()
        self._local_locks_guard = threading.Lock()

    def start(self):
        try:
            import pika
            self._running = True
            self._thread = threading.Thread(target=self._run, daemon=True, name="FileProcessorWorker")
            self._thread.start()
            logger.info("File processor worker started")
        except ImportError:
            logger.warning("pika not installed, MQ consumer disabled")

    def stop(self):
        self._running = False
        if self._thread: self._thread.join(timeout=10)
        logger.info("File processor worker stopped")

    def _run(self):
        try:
            import pika
            creds = pika.PlainCredentials(settings.rabbitmq_username, settings.rabbitmq_password)
            params = pika.ConnectionParameters(host=settings.rabbitmq_host, port=settings.rabbitmq_port, credentials=creds, heartbeat=600)
            conn = pika.BlockingConnection(params); ch = conn.channel()
            ch.exchange_declare(exchange=settings.rabbitmq_exchange, exchange_type='direct', durable=True)
            ch.queue_declare(queue=settings.rabbitmq_upload_queue, durable=True)
            ch.queue_declare(queue=settings.rabbitmq_delete_queue, durable=True)
            ch.queue_bind(queue=settings.rabbitmq_upload_queue, exchange=settings.rabbitmq_exchange, routing_key='file.upload')
            ch.queue_bind(queue=settings.rabbitmq_delete_queue, exchange=settings.rabbitmq_exchange, routing_key='file.delete')
            ch.basic_qos(prefetch_count=1)
            ch.basic_consume(queue=settings.rabbitmq_upload_queue, on_message_callback=self._handle_upload, auto_ack=False)
            ch.basic_consume(queue=settings.rabbitmq_delete_queue, on_message_callback=self._handle_delete, auto_ack=False)
            logger.info("RabbitMQ consumer started"); ch.start_consuming()
        except ImportError: pass
        except Exception as e: logger.error(f"RabbitMQ error: {e}"); self._running = False

    def _handle_upload(self, ch, method, props, body):
        try:
            event = json.loads(body)
            fid = str(event.get("fileId", ""))
            path = event.get("filePath", "")
            uid = str(event.get("userId", ""))
            logger.info(f"Processing: {fid} - {path}")
            if not fid:
                ch.basic_ack(delivery_tag=method.delivery_tag)
                return
            if not self._try_acquire_lock(fid):
                logger.info(f"Duplicate message skipped: {fid}")
                ch.basic_ack(delivery_tag=method.delivery_tag)
                return
            try:
                self._process_file(fid, path, uid)
            except Exception as e:
                logger.error(f"Process failed {fid}: {e}")
                self._send_callback(fid, status="FAILED", error_message=str(e))
            finally:
                self._release_lock(fid)
                ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception as e:
            logger.error(f"Upload handler error: {e}")
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def _handle_delete(self, ch, method, props, body):
        try:
            event = json.loads(body); fid = str(event.get("fileId", ""))
            from app.services.vector_store import VectorStoreService
            VectorStoreService.delete_file(fid)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception as e: logger.error(f"Delete handler error: {e}"); ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def _try_acquire_lock(self, file_id):
        redis = self._get_redis()
        if redis:
            try:
                return bool(redis.set(f"ai:processing:{file_id}", "1", nx=True, ex=3600))
            except Exception as e:
                logger.warning(f"Dedupe lock Redis set failed, using local fallback: {e}")
        with self._local_locks_guard:
            if file_id in self._local_locks:
                return False
            self._local_locks.add(file_id)
            return True

    def _release_lock(self, file_id):
        redis = self._get_redis()
        if redis:
            try:
                redis.delete(f"ai:processing:{file_id}")
            except Exception as e:
                logger.warning(f"Dedupe lock Redis delete failed: {e}")
        with self._local_locks_guard:
            self._local_locks.discard(file_id)

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
            logger.info("Dedupe lock connected to Redis")
        except Exception as e:
            logger.warning(f"Dedupe lock Redis unavailable, using local fallback: {e}")
            self._redis = False
        return self._redis

    def _is_archive(self, file_name, file_path):
        name = file_name or str(file_path or "")
        lower = name.lower()
        return any(lower.endswith("." + ext) for ext in ARCHIVE_EXTENSIONS)

    def _download_from_minio(self, object_path):
        import os, tempfile
        try:
            from minio import Minio
            endpoint = settings.minio_endpoint.replace("http://", "").replace("https://", "")
            secure = settings.minio_endpoint.startswith("https://")
            client = Minio(endpoint, access_key=settings.minio_access_key, secret_key=settings.minio_secret_key, secure=secure)
            local_path = os.path.join(tempfile.gettempdir(), object_path.replace("/", "_"))
            client.fget_object(settings.minio_bucket_name, object_path, local_path)
            logger.info(f"Downloaded MinIO object {object_path} to {local_path}")
            return local_path
        except Exception as e:
            logger.warning(f"MinIO download failed for {object_path}: {e}")
            return object_path

    def _process_file(self, file_id, file_path, user_id):
        from app.utils.file_parser import FileParser
        from app.services.file_understanding import FileUnderstandingService
        from app.services.vector_store import VectorStoreService
        fname = str(file_path).split("/")[-1] if file_path else ""
        if self._is_archive(fname, file_path):
            logger.info(f"Archive skipped without AI: {file_path}")
            self._send_callback(
                file_id,
                status="SKIPPED",
                category="压缩文件",
                tags=json.dumps(["压缩"], ensure_ascii=False),
            )
            return
        local_path = self._download_from_minio(file_path)
        try: content = FileParser.parse(local_path)
        except:
            try:
                with open(local_path, "r", encoding="utf-8") as f: content = f.read()
            except: content = f"[Binary: {file_path}]"
        if not content: self._send_callback(file_id, status="FAILED", error_message="No text content"); return
        cr = FileUnderstandingService.classify_and_tag(file_id, content)
        sr = FileUnderstandingService.generate_summary(file_id, content)
        fname = file_path.split("/")[-1] if "/" in file_path else file_path
        VectorStoreService.index_file(file_id, fname, content)
        # 构建敏感检测详情JSON
        sensitive_items_json = json.dumps(cr.get("sensitive_items", []), ensure_ascii=False)
        self._send_callback(file_id, status="COMPLETED",
            category=cr.get("category", ""),
            tags=json.dumps(cr.get("tags", []), ensure_ascii=False),
            summary=sr.get("content", ""),
            sensitive_level=cr.get("sensitive_level", "normal"),
            sensitive_items=sensitive_items_json)

    def _send_callback(self, file_id, status, category="", tags="", summary="", sensitive_level="normal", sensitive_items="", error_message=None):
        try:
            import httpx
            url = f"{settings.backend_base_url}/api/v1/files/{file_id}/process-callback"
            payload = {"status": status, "category": category, "tags": tags, "summary": summary, "sensitiveLevel": sensitive_level.upper(), "sensitiveItems": sensitive_items, "errorMessage": error_message}
            headers = {}
            if settings.backend_api_key: headers["X-Internal-Api-Key"] = settings.backend_api_key
            with httpx.Client(timeout=30.0) as client:
                resp = client.post(url, json=payload, headers=headers)
                if resp.status_code in (200, 201, 204): logger.info(f"Callback OK: {file_id} {status}")
                else: logger.error(f"Callback failed: {resp.status_code}")
        except Exception as e: logger.error(f"Callback error: {e}")

file_processor = FileProcessorWorker()
