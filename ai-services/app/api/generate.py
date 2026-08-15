"""内容生成 API"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional
from app.services.content_generation import ContentGenerationService

logger = logging.getLogger(__name__); router = APIRouter()

class SummaryRequest(BaseModel): file_id: str; content: str; file_path: Optional[str] = None; file_content_base64: Optional[str] = None; file_type: Optional[str] = None; max_length: int = Field(default=300, ge=50, le=2000)
class ExtractRequest(BaseModel): file_id: str; content: str; file_path: Optional[str] = None; file_content_base64: Optional[str] = None; file_type: Optional[str] = None
class ReportRequest(BaseModel): file_ids: List[str]; topic: str; contents: Optional[dict] = None; file_paths: Optional[dict] = None; file_contents_base64: Optional[dict] = None  # {file_id: base64_content}
class PPTRequest(BaseModel): file_ids: List[str]; topic: str; contents: Optional[dict] = None; file_paths: Optional[dict] = None; file_contents_base64: Optional[dict] = None  # {file_id: base64_content}
class ConvertRequest(BaseModel): file_id: str; content: str; target_format: str; file_path: Optional[str] = None; file_content_base64: Optional[str] = None; file_type: Optional[str] = None; source_format: Optional[str] = None
class GenerateResponse(BaseModel): file_id: str = ""; content: str; key_points: List[str] = []; format: str = "text"

@router.post("/api/v1/generate/summary", response_model=GenerateResponse)
async def generate_summary(request: SummaryRequest):
    try:
        result = ContentGenerationService.generate_summary(request.file_id, request.content, request.max_length, file_path=request.file_path, file_content_base64=request.file_content_base64, file_type=request.file_type)
        return GenerateResponse(**result)
    except Exception as e: logger.error(f"Summary failed: {e}"); raise HTTPException(status_code=500, detail=str(e))

@router.post("/api/v1/generate/extract", response_model=GenerateResponse)
async def extract_content(request: ExtractRequest):
    try:
        result = ContentGenerationService.extract_content(request.file_id, request.content, file_path=request.file_path, file_content_base64=request.file_content_base64, file_type=request.file_type)
        return GenerateResponse(**result)
    except Exception as e: logger.error(f"Extract failed: {e}"); raise HTTPException(status_code=500, detail=str(e))

@router.post("/api/v1/generate/report", response_model=GenerateResponse)
async def generate_report(request: ReportRequest):
    try:
        result = ContentGenerationService.generate_report(request.file_ids, request.topic, request.contents, file_paths=request.file_paths, file_contents_base64=request.file_contents_base64)
        return GenerateResponse(**result)
    except Exception as e: logger.error(f"Report failed: {e}"); raise HTTPException(status_code=500, detail=str(e))

@router.post("/api/v1/generate/ppt", response_model=GenerateResponse)
async def generate_ppt(request: PPTRequest):
    try:
        result = ContentGenerationService.generate_ppt(request.file_ids, request.topic, request.contents, file_paths=request.file_paths, file_contents_base64=request.file_contents_base64)
        return GenerateResponse(**result)
    except Exception as e: logger.error(f"PPT failed: {e}"); raise HTTPException(status_code=500, detail=str(e))

@router.post("/api/v1/generate/convert", response_model=GenerateResponse)
async def convert_format(request: ConvertRequest):
    try:
        result = ContentGenerationService.convert_format(request.file_id, request.content, request.target_format, request.source_format, file_path=request.file_path, file_content_base64=request.file_content_base64, file_type=request.file_type)
        return GenerateResponse(**result)
    except Exception as e: logger.error(f"Convert failed: {e}"); raise HTTPException(status_code=500, detail=str(e))
