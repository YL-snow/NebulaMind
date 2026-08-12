"""文件理解服务 - 分类、标签、摘要、敏感检测"""
import json, re, logging
from typing import Dict, Any, List, Optional
from app.core.llm_client import llm_client
from app.prompts.templates import PromptManager
from app.services.sensitive_detector import sensitive_detector, SensitiveReport
from app.utils.file_parser import FileParser

logger = logging.getLogger(__name__)
# MaaS API limit is 5 calls/minute; keep a long summary within that budget.
LONG_SUMMARY_SINGLE_MAX_CHARS = 30000
MAP_REDUCE_MAX_CHUNKS = 3

class FileUnderstandingService:
    @staticmethod
    def classify_and_tag(file_id, content, file_path=None, file_content_base64=None, file_type=None):
        content = FileParser.ensure_text(content, file_path, file_type, file_content_base64)
        truncated = content[:8000] if len(content) > 8000 else content
        try:
            messages = PromptManager.format("classify", content=truncated)
            response = llm_client.chat(messages, module="file_understanding", file_id=file_id, temperature=0.3)
            try: result = json.loads(response.content)
            except json.JSONDecodeError:
                m = re.search(r'\{[\s\S]*\}', response.content)
                result = json.loads(m.group()) if m else None
            if not result: result = {"tags": ["未分类"], "category": "其他", "confidence": 0.5}
        except Exception as e:
            logger.warning(f"LLM classify failed, using fallback: {e}")
            result = {"tags": ["文档"], "category": "文档", "confidence": 0.5}
        # 使用增强的 SensitiveDetector 进行综合检测
        sensitive_report = FileUnderstandingService._detect_sensitive(file_id, content)
        return {"file_id": file_id, "category": result.get("category", "其他"), "tags": result.get("tags", []), "sensitive_level": sensitive_report.sensitive_level, "sensitive_score": sensitive_report.level_score, "sensitive_summary": sensitive_report.summary, "sensitive_items": [{"type": m.type, "content": m.content, "confidence": m.confidence} for m in sensitive_report.matches[:10]], "confidence": result.get("confidence", 0.7)}

    @staticmethod
    def generate_summary(file_id, content, max_length=300, file_path=None, file_content_base64=None, file_type=None):
        content = FileParser.ensure_text(content, file_path, file_type, file_content_base64)
        if FileParser.NO_EXTRACTABLE_TEXT in content:
            logger.info(f"No extractable text for {file_id}, skipping LLM call")
            return {"file_id": file_id, "content": content, "key_points": [], "format": "text"}
        try:
            if len(content) > 6000:
                return FileUnderstandingService._generate_long_summary(file_id, content, max_length)
            messages = PromptManager.format("summary", content=content, max_length=max_length)
            response = llm_client.chat(messages, module="file_understanding", file_id=file_id, temperature=0.5, max_tokens=max_length*2)
            return {"file_id": file_id, "content": response.content, "key_points": FileUnderstandingService._extract_key_points(response.content), "format": "markdown"}
        except Exception as e:
            logger.warning(f"LLM summary failed, returning fallback: {e}")
            return {"file_id": file_id, "content": "AI 摘要服务暂时不可用，请稍后重试", "key_points": [], "format": "markdown"}

    @staticmethod
    def _generate_long_summary(file_id, content, max_length=300):
        if len(content) <= LONG_SUMMARY_SINGLE_MAX_CHARS:
            messages = PromptManager.format("summary", content=content, max_length=max_length)
            r = llm_client.chat(messages, module="file_understanding", file_id=file_id, temperature=0.5, max_tokens=max_length*2)
            return {"file_id": file_id, "content": r.content, "key_points": FileUnderstandingService._extract_key_points(r.content), "format": "markdown"}
        from app.utils.text_splitter import TextSplitter
        splitter = TextSplitter(max_chunk_size=4000)
        chunks = splitter.split(content)[:MAP_REDUCE_MAX_CHUNKS]
        summaries = []
        for chunk in chunks:
            messages = PromptManager.format("summary", content=chunk["text"][:3000], max_length=150)
            r = llm_client.chat(messages, module="file_understanding", file_id=file_id, temperature=0.5, max_tokens=300)
            summaries.append(r.content)
        combined = "\n\n---\n\n".join(summaries)
        r = llm_client.chat([{"role": "system", "content": "将以下分段摘要合并为完整摘要。"}, {"role": "user", "content": f"分段摘要：\n{combined}\n\n合并为{max_length}字以内的完整摘要。"}], module="file_understanding", file_id=file_id, temperature=0.5, max_tokens=max_length*2)
        return {"file_id": file_id, "content": r.content, "key_points": FileUnderstandingService._extract_key_points(r.content), "format": "markdown"}

    @staticmethod
    def _detect_sensitive(file_id, content) -> SensitiveReport:
        """使用增强的 SensitiveDetector 进行综合敏感检测（正则+关键词+LLM NER）"""
        return sensitive_detector.detect_all(file_id, content, use_llm=True)

    @staticmethod
    def _extract_key_points(text):
        return [line.strip().lstrip('-•*0123456789.、) ') for line in text.split('\n') if re.match(r'^[-•*\d]+[\.\)、]?\s', line.strip())][:10]
