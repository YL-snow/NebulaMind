"""模型调用日志管理"""
import json, logging, os
from datetime import datetime
from typing import List, Dict, Any
logger = logging.getLogger(__name__)

class ModelCallLogger:
    def __init__(self, log_dir="logs"):
        self.log_dir = log_dir; os.makedirs(log_dir, exist_ok=True); self._calls = []

    def log_call(self, request_id, module, model, prompt, response, token_usage, latency_ms, success, error_message=None, file_id=None, user_id=None):
        self._calls.append({"id": request_id, "requestId": request_id, "module": module, "model": model, "prompt": prompt[:5000] if prompt else "", "response": response[:5000] if response else "", "tokenUsage": {"promptTokens": token_usage.get("prompt_tokens", 0), "completionTokens": token_usage.get("completion_tokens", 0), "totalTokens": token_usage.get("total_tokens", 0)}, "latencyMs": latency_ms, "success": success, "errorMessage": error_message, "fileId": file_id, "userId": user_id, "createdAt": datetime.now().isoformat()})

    def export_json(self, filepath=None):
        if not filepath: filepath = os.path.join(self.log_dir, f"model_calls_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
        with open(filepath, "w", encoding="utf-8") as f: json.dump(self._calls, f, ensure_ascii=False, indent=2)
        return filepath

    def get_statistics(self):
        total = len(self._calls)
        if total == 0: return {"total_calls": 0}
        sc = sum(1 for c in self._calls if c["success"]); tt = sum(c["tokenUsage"]["totalTokens"] for c in self._calls); tl = sum(c["latencyMs"] for c in self._calls)
        by_module = {}
        for c in self._calls:
            m = c["module"]
            if m not in by_module: by_module[m] = {"count": 0, "tokens": 0, "latency": 0, "success": 0, "failed": 0}
            by_module[m]["count"] += 1; by_module[m]["tokens"] += c["tokenUsage"]["totalTokens"]; by_module[m]["latency"] += c["latencyMs"]
            if c["success"]: by_module[m]["success"] += 1
            else: by_module[m]["failed"] += 1
        return {"total_calls": total, "success_rate": round(sc/total, 4), "total_tokens": tt, "avg_latency_ms": tl//total, "by_module": by_module}

call_logger = ModelCallLogger()
