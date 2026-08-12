"""敏感信息检测API"""
import logging
import hashlib
import os
import threading
import time
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1", tags=["sensitive"])

# 相同内容的敏感检测结果缓存，避免重复调用 MaaS 消耗每分钟 5 次的配额
SENSITIVE_CACHE_TTL = int(os.getenv("SENSITIVE_CACHE_TTL", "600"))
_sensitive_cache = { }
_sensitive_cache_lock = threading.Lock()

def _sensitive_cache_key(content: str, use_llm: bool) -> str:
    digest = hashlib.sha256((content or "").encode("utf-8", errors="ignore")).hexdigest()
    return f"{digest}:{str(use_llm).lower()}"

def _get_cached_report(key: str):
    with _sensitive_cache_lock:
        item = _sensitive_cache.get(key)
        if item and time.time() - item[0] < SENSITIVE_CACHE_TTL:
            return item[1]
    return None

def _set_cached_report(key: str, report):
    with _sensitive_cache_lock:
        _sensitive_cache[key] = (time.time(), report)


class SensitiveDetectRequest(BaseModel):
    """敏感检测请求"""
    content: str = Field("", description="待检测的文本内容；二进制文件可为空，由 file_content_base64 解析")
    file_id: Optional[str] = Field(None, description="关联的文件ID")
    file_path: Optional[str] = Field(None, description="文件存储路径，用于解析二进制格式")
    file_content_base64: Optional[str] = Field(None, description="base64编码的原始文件字节，兼容所有存储后端")
    file_type: Optional[str] = Field(None, description="文件扩展名")
    use_llm: bool = Field(True, description="是否启用LLM NER检测")


class SensitiveDetectResponse(BaseModel):
    """敏感检测响应"""
    file_id: Optional[str] = None
    sensitive_level: str  # high / medium / low / normal
    level_score: int      # 0-100
    summary: str          # 人类可读摘要
    detection_method: str # regex / llm / keyword / hybrid
    warning: Optional[str] = None  # 限流/降级提示
    matches: List[dict] = []
    masked_content: Optional[str] = None  # 脱敏后的内容


@router.post("/sensitive/detect", response_model=SensitiveDetectResponse)
async def detect_sensitive(request: SensitiveDetectRequest):
    """
    综合敏感信息检测接口

    使用混合策略检测文本中的敏感信息：
    - 正则表达式（身份证/手机号/银行卡/邮箱/地址）
    - 敏感词库匹配
    - LLM NER实体识别（可选）

    返回敏感级别、量化分数、匹配详情和脱敏内容
    """
    try:
        from app.services.sensitive_detector import sensitive_detector
        from app.utils.file_parser import FileParser

        # 对于二进制文件，先从存储中解析文本
        content = FileParser.ensure_text(request.content, request.file_path, request.file_type, request.file_content_base64)
        cache_key = _sensitive_cache_key(content, request.use_llm)
        report = _get_cached_report(cache_key)
        if report is None:
            report = sensitive_detector.detect_all(
                file_id=request.file_id or "api_call",
                content=content,
                use_llm=request.use_llm,
            )
            # 限流/降级结果不缓存，避免配额恢复后仍返回旧提示
            if not report.warning:
                _set_cached_report(cache_key, report)

        # 生成脱敏内容
        masked = sensitive_detector.mask_content(content, report.matches)

        return SensitiveDetectResponse(
            file_id=request.file_id,
            sensitive_level=report.sensitive_level,
            level_score=report.level_score,
            summary=report.summary,
            detection_method=report.detection_method,
            warning=report.warning,
            matches=[
                {
                    "type": m.type,
                    "content": m.content,
                    "position": m.position,
                    "confidence": m.confidence,
                }
                for m in report.matches
            ],
            masked_content=masked,
        )
    except Exception as e:
        logger.error(f"Sensitive detection failed: {e}")
        raise HTTPException(status_code=500, detail=f"检测失败: {str(e)}")


@router.post("/sensitive/mask")
async def mask_sensitive(request: SensitiveDetectRequest):
    """
    对文本进行脱敏处理（自动检测并替换敏感信息）
    """
    try:
        from app.services.sensitive_detector import sensitive_detector

        report = sensitive_detector.detect_all(
            file_id=request.file_id or "api_call",
            content=request.content,
            use_llm=False,  # 脱敏场景优先速度，不使用LLM
        )
        masked = sensitive_detector.mask_content(request.content, report.matches)

        return {
            "file_id": request.file_id,
            "original_length": len(request.content),
            "masked_content": masked,
            "redacted_count": len(report.matches),
            "sensitive_level": report.sensitive_level,
        }
    except Exception as e:
        logger.error(f"Sensitive mask failed: {e}")
        raise HTTPException(status_code=500, detail=f"脱敏失败: {str(e)}")
