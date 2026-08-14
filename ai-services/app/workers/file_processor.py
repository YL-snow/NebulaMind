"""RabbitMQ Worker - 文件处理消费者"""
import json, logging, threading
from config import settings
logger = logging.getLogger(__name__)

class FileProcessorWorker:
    def __init__(self): self._running = False; self._thread = None

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
            event = json.loads(body); fid = str(event.get("fileId", "")); path = event.get("filePath", ""); uid = str(event.get("userId", ""))
            logger.info(f"Processing: {fid} - {path}")
            try: self._process_file(fid, path, uid)
            except Exception as e: logger.error(f"Process failed {fid}: {e}"); self._send_callback(fid, status="FAILED", error_message=str(e))
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception as e: logger.error(f"Upload handler error: {e}"); ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def _handle_delete(self, ch, method, props, body):
        try:
            event = json.loads(body); fid = str(event.get("fileId", ""))
            from app.services.vector_store import VectorStoreService
            VectorStoreService.delete_file(fid)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception as e: logger.error(f"Delete handler error: {e}"); ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def _process_file(self, file_id, file_path, user_id):
        from app.utils.file_parser import FileParser
        from app.services.file_understanding import FileUnderstandingService
        from app.services.vector_store import VectorStoreService
        try: content = FileParser.parse(file_path)
        except:
            try:
                with open(file_path, "r", encoding="utf-8") as f: content = f.read()
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
            tags=",".join(cr.get("tags", [])),
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
