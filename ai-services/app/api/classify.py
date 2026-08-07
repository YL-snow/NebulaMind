"""分类与标签 API"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from app.services.file_understanding import FileUnderstandingService

logger = logging.getLogger(__name__); router = APIRouter()

class ClassifyRequest(BaseModel): file_id: str; content: str; file_path: Optional[str] = None; file_content_base64: Optional[str] = None; file_type: Optional[str] = None
class ClassifyResponse(BaseModel): file_id: str; category: str; tags: List[str]; sensitive_level: str = "normal"; confidence: float = 0.0

@router.post("/api/v1/classify", response_model=ClassifyResponse)
async def classify_file(request: ClassifyRequest):
    try:
        result = FileUnderstandingService.classify_and_tag(request.file_id, request.content, file_path=request.file_path, file_content_base64=request.file_content_base64, file_type=request.file_type)
        return ClassifyResponse(**result)
    except Exception as e:
        logger.error(f"Classification failed: {e}"); raise HTTPException(status_code=500, detail=str(e))
