"""文档问答 API"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional
from app.services.rag_qa import RAGQAService

logger = logging.getLogger(__name__); router = APIRouter()

class QARequest(BaseModel):
    file_id: Optional[str] = None
    file_ids: Optional[List[str]] = None
    question: str = Field(..., min_length=1)
    file_content: Optional[str] = None
    file_path: Optional[str] = None  # 用于 FileParser 解析二进制文件
    file_content_base64: Optional[str] = None  # base64 编码的原始文件字节
    file_type: Optional[str] = None  # 文件扩展名
    file_contents: Optional[dict] = None  # {file_id: content}
    file_paths: Optional[dict] = None  # {file_id: file_path}
    file_contents_base64: Optional[dict] = None  # {file_id: base64_content}

class QAResponse(BaseModel): question: str; answer: str; source_file_id: str = ""; source_snippets: List[str] = []; confidence: float = 0.0

@router.post("/api/v1/qa", response_model=QAResponse)
async def document_qa(request: QARequest):
    try:
        if not request.file_id: raise HTTPException(status_code=400, detail="file_id is required")
        result = RAGQAService.answer_single(
            file_id=request.file_id,
            question=request.question,
            file_content=request.file_content,
            file_path=request.file_path,
            file_content_base64=request.file_content_base64,
            file_type=request.file_type
        )
        return QAResponse(**result)
    except HTTPException: raise
    except Exception as e:
        logger.error(f"QA failed: {e}"); raise HTTPException(status_code=500, detail=str(e))

@router.post("/api/v1/qa/cross", response_model=QAResponse)
async def cross_document_qa(request: QARequest):
    try:
        if not request.file_ids or len(request.file_ids) == 0: raise HTTPException(status_code=400, detail="file_ids is required")
        result = RAGQAService.answer_cross(
            file_ids=request.file_ids,
            question=request.question,
            file_contents=request.file_contents,
            file_paths=request.file_paths,
            file_contents_base64=request.file_contents_base64
        )
        return QAResponse(**result)
    except HTTPException: raise
    except Exception as e:
        logger.error(f"Cross-document QA failed: {e}"); raise HTTPException(status_code=500, detail=str(e))
