"""敏感信息检测API"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, List

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1", tags=["sensitive"])


class SensitiveDetectRequest(BaseModel):
    """敏感检测请求"""
    content: str = Field(..., description="待检测的文本内容", min_length=1)
    file_id: Optional[str] = Field(None, description="关联的文件ID")
    file_path: Optional[str] = Field(None, description="文件存储路径，用于解析二进制格式")
    use_llm: bool = Field(True, description="是否启用LLM NER检测")


class SensitiveDetectResponse(BaseModel):
    """敏感检测响应"""
    file_id: Optional[str] = None
    sensitive_level: str  # high / medium / low / normal
    level_score: int      # 0-100
    summary: str          # 人类可读摘要
    detection_method: str # regex / llm / keyword / hybrid
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
        content = FileParser.ensure_text(request.content, request.file_path)

        report = sensitive_detector.detect_all(
            file_id=request.file_id or "api_call",
            content=content,
            use_llm=request.use_llm,
        )

        # 生成脱敏内容
        masked = sensitive_detector.mask_content(request.content, report.matches)

        return SensitiveDetectResponse(
            file_id=request.file_id,
            sensitive_level=report.sensitive_level,
            level_score=report.level_score,
            summary=report.summary,
            detection_method=report.detection_method,
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
