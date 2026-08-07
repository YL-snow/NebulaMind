"""语义搜索 API"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional
from app.services.semantic_search import SemanticSearchService

logger = logging.getLogger(__name__); router = APIRouter()

class SearchRequest(BaseModel): query: str; file_ids: Optional[List[str]] = None; top_k: int = Field(default=10, ge=1, le=100)
class SearchResult(BaseModel): file_id: str; file_name: str = ""; snippet: str = ""; score: float = 0.0; category: str = ""
class SearchResponse(BaseModel): query: str; results: List[SearchResult]

@router.post("/api/v1/search", response_model=SearchResponse)
async def semantic_search(request: SearchRequest):
    try:
        result = SemanticSearchService.search(query=request.query, file_ids=request.file_ids, top_k=request.top_k)
        return SearchResponse(**result)
    except Exception as e:
        logger.error(f"Search failed: {e}"); raise HTTPException(status_code=500, detail=str(e))
