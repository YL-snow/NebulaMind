"""RAG 文档问答服务 - 支持 Map-Reduce、相关性截断、摘要压缩三种溢出策略"""
import logging
from typing import List, Dict, Optional
from app.core.llm_client import llm_client
from app.prompts.templates import PromptManager
from app.services.vector_store import VectorStoreService
from app.utils.cache import CacheManager
from app.utils.file_parser import FileParser

logger = logging.getLogger(__name__)


class RAGQAService:
    """RAG 问答服务，支持智能上下文窗口溢出的三种策略"""

    # ==================== 公共入口 ====================

    @staticmethod
    def answer_single(file_id: str, question: str, file_content: Optional[str] = None,
                      file_path: Optional[str] = None, file_content_base64: Optional[str] = None,
                      file_type: Optional[str] = None) -> Dict:
        """单文件问答 - 自动选择最优溢出策略"""
        cache_key = CacheManager.get_qa_cache_key(file_id, question)
        cached = CacheManager.get(cache_key)
        if cached:
            return cached

        results = VectorStoreService.search(question, file_ids=[file_id], top_k=5)

        # ---- 策略选择 ----
        strategy = "auto"
        strategy_used = "direct"

        if results and len(results) > 0:
            snippets = [r.get("chunk_text", "") for r in results]
            context = "\n\n---\n\n".join(snippets)

            if len(context) > 16000:
                # 上下文溢出 → 使用相关性排序截断（最低成本）
                context, strategy_used = RAGQAService._relevance_truncation(results, max_chars=16000)
                strategy = "relevance"

                # 如果截断后仍然很长（>14000字符说明截断效果有限），改用摘要压缩
                if len(context) > 14000:
                    context, strategy_used = RAGQAService._summary_compression(results, question, max_chars=16000)
                    strategy = "summary"

                # 如果摘要压缩后仍然很长，使用 Map-Reduce（成本最高但保真度最高）
                if len(context) > 15000:
                    logger.info(f"Context still large ({len(context)} chars) after summary, using Map-Reduce")
                    return RAGQAService._map_reduce_answer(results, file_id, question, cache_key)
        else:
            # 没有检索结果，使用原始文件内容
            file_content = FileParser.ensure_text(file_content or "", file_path, file_type, file_content_base64)
            context = file_content[:6000] if file_content else ""
            if len(context) > 16000:
                context, strategy_used = RAGQAService._basic_truncation(context, 16000)
                strategy = "truncate"

        if not context.strip():
            file_content = FileParser.ensure_text(file_content or "", file_path, file_type, file_content_base64)
            context = file_content[:6000] if file_content else ""
            if len(context) > 16000:
                context, _ = RAGQAService._basic_truncation(context, 16000)

        # ---- 正常单轮问答 ----
        try:
            messages = PromptManager.format("qa", document_content=context, question=question)
            response = llm_client.chat(messages, module="document_qa", file_id=file_id, temperature=0.5)
            answer = response.content
        except Exception as e:
            logger.warning(f"LLM QA failed, returning fallback: {e}")
            answer = "AI 问答服务暂时不可用，请稍后重试。如果问题持续，请联系管理员。"

        scores = [r.get("score", 0) for r in (results or [])[:5]]
        avg = sum(scores) / len(scores) if scores else 0.3
        snippets = [r.get("chunk_text", "") for r in (results or [])[:3]]
        result = {
            "question": question,
            "answer": answer,
            "source_file_id": file_id,
            "source_snippets": snippets,
            "confidence": round(min(avg + sum(1 for s in scores if s > 0.7) * 0.1, 0.99), 4),
            "strategy": strategy_used,
        }
        CacheManager.set(cache_key, result, ttl=300)
        return result

    @staticmethod
    def answer_cross(file_ids: List[str], question: str, file_contents: Optional[Dict[str, str]] = None,
                     file_paths: Optional[Dict[str, str]] = None,
                     file_contents_base64: Optional[Dict[str, str]] = None) -> Dict:
        """跨文件问答 - 自动选择最优溢出策略"""
        results = VectorStoreService.search(question, file_ids=file_ids, top_k=8)

        # ---- 按文件分组 ----
        by_file = {}
        for r in results:
            fid = r.get("file_id", "")
            if fid not in by_file:
                by_file[fid] = []
            by_file[fid].append(r)

        # ---- 策略选择 ----
        parts = []
        for fid, file_results in by_file.items():
            file_texts = [fr.get("chunk_text", "") for fr in file_results[:3]]
            parts.append(f"[来源: {fid}]\n" + "\n".join(file_texts))

        # ---- 向量库无结果时使用传入的文件内容 ----
        if not parts and file_contents:
            for fid, content in file_contents.items():
                if content:
                    fp = file_paths.get(fid) if file_paths else None
                    b64 = file_contents_base64.get(fid) if file_contents_base64 else None
                    content = FileParser.ensure_text(content, fp, file_content_base64=b64)
                    parts.append(f"[来源: {fid}]\n{content[:3000]}")

        combined = "\n\n=====\n\n".join(parts)
        strategy_used = "direct"

        if len(combined) > 16000:
            combined, strategy_used = RAGQAService._relevance_truncation(
                results, max_chars=16000, context=combined
            )

            if len(combined) > 14000:
                # 跨文件场景优先使用 Map-Reduce（多文档整合需要全面信息）
                logger.info(f"Cross-file context large ({len(combined)} chars), using Map-Reduce")
                return RAGQAService._map_reduce_answer(results, file_ids, question, None, cross_file=True)

        messages = PromptManager.format("cross_qa", combined_documents=combined, question=question)
        response = llm_client.chat(messages, module="document_qa", temperature=0.5)

        snippets = [r.get("chunk_text", "")[:200] for r in results[:3]]
        scores = [r.get("score", 0) for r in results[:5]]
        avg = sum(scores) / len(scores) if scores else 0.3
        return {
            "question": question,
            "answer": response.content,
            "source_file_id": results[0].get("file_id", "") if results else "",
            "source_snippets": snippets,
            "confidence": round(min(avg + sum(1 for s in scores if s > 0.7) * 0.1, 0.99), 4),
            "strategy": strategy_used,
        }

    # ==================== 策略1: 相关性排序截断 ====================

    @staticmethod
    def _relevance_truncation(results: List[Dict], max_chars: int = 16000,
                               context: Optional[str] = None) -> tuple:
        """按相关性排序后截取前 N 个文档块"""
        if not results:
            return (context or "")[:max_chars], "relevance"

        # 按分数降序排列
        sorted_results = sorted(results, key=lambda r: r.get("score", 0), reverse=True)

        # 从高分到低分累积，直到达到 max_chars
        selected = []
        total = 0
        for r in sorted_results:
            text = r.get("chunk_text", "")
            if total + len(text) > max_chars and total > 0:
                break
            selected.append(text)
            total += len(text)

        result = "\n\n---\n\n".join(selected)
        logger.info(
            f"Relevance truncation: {len(results)} chunks → {len(selected)} chunks "
            f"({total}/{max_chars} chars)"
        )
        return result, "relevance"

    # ==================== 策略2: 摘要压缩 ====================

    @staticmethod
    def _summary_compression(results: List[Dict], question: str,
                              max_chars: int = 16000) -> tuple:
        """对检索结果先做摘要压缩，再用压缩后的内容问答"""
        # 按分数排序
        sorted_results = sorted(results, key=lambda r: r.get("score", 0), reverse=True)

        summaries = []
        total = 0
        for r in sorted_results:
            text = r.get("chunk_text", "")
            # 对每个文档块生成简短摘要
            summary = RAGQAService._quick_summarize(text, question)
            if total + len(summary) > max_chars and total > 0:
                break
            summaries.append(summary)
            total += len(summary)

        result = "\n\n---\n\n".join(summaries)
        logger.info(f"Summary compression: {len(sorted_results)} chunks → {len(summaries)} summaries ({total}/{max_chars} chars)")
        return result, "summary"

    @staticmethod
    def _quick_summarize(text: str, question: str, max_summary: int = 500) -> str:
        """对单段文本快速摘要，聚焦与问题相关的内容"""
        if len(text) <= max_summary:
            return text

        try:
            messages = [
                {"role": "system", "content": "你是一个文档摘要助手。请用中文简洁总结以下文本中与问题相关的关键信息。"},
                {"role": "user", "content": f"问题：{question}\n\n文本：{text[:3000]}\n\n请用{max_summary}字以内总结与问题相关的内容："}
            ]
            resp = llm_client.chat(messages, module="summary_compress", temperature=0.3)
            summary = resp.content.strip()
            if len(summary) > max_summary:
                summary = summary[:max_summary]
            return summary
        except Exception as e:
            logger.warning(f"Quick summarize failed: {e}, using truncation")
            return text[:max_summary]

    # ==================== 策略3: Map-Reduce ====================

    @staticmethod
    def _map_reduce_answer(results: List[Dict], file_identifier, question: str,
                            cache_key: Optional[str] = None,
                            cross_file: bool = False) -> Dict:
        """Map-Reduce 策略：分段生成部分答案 → 汇总为最终答案

        - Map: 将检索结果分成若干组，每组生成部分答案（含引用）
        - Reduce: 将所有部分答案汇总，生成完整一致的最终答案
        """
        # ---- Map 阶段：分组生成部分答案 ----
        sorted_results = sorted(results, key=lambda r: r.get("score", 0), reverse=True)

        # 按相关性分组：高分(>0.7) / 中分(0.4~0.7) / 低分(<0.4)
        groups = {"high": [], "medium": [], "low": []}
        for r in sorted_results:
            score = r.get("score", 0)
            if score > 0.7:
                groups["high"].append(r.get("chunk_text", ""))
            elif score > 0.4:
                groups["medium"].append(r.get("chunk_text", ""))
            else:
                groups["low"].append(r.get("chunk_text", ""))

        # 每个组内限制大小，避免单组溢出
        partial_answers = []
        for level, texts in groups.items():
            if not texts:
                continue
            # 如果组内文本太长，进一步拆分
            chunks = RAGQAService._chunk_texts(texts, max_chars=4000)
            for i, chunk in enumerate(chunks):
                chunk_context = "\n\n".join(chunk) if isinstance(chunk, list) else chunk
                if not chunk_context.strip():
                    continue
                try:
                    partial = RAGQAService._map_single(chunk_context, question, level)
                    if partial:
                        partial_answers.append(partial)
                except Exception as e:
                    logger.warning(f"Map阶段失败 ({level} 组第{i}块): {e}")

        if not partial_answers:
            # Map 全部失败，回退到基本截断
            context = "\n\n".join(r.get("chunk_text", "") for r in sorted_results)
            context, _ = RAGQAService._basic_truncation(context, 16000)
            messages = PromptManager.format("qa", document_content=context, question=question)
            response = llm_client.chat(messages, module="document_qa", temperature=0.5)
            file_id = file_identifier if isinstance(file_identifier, str) else (
                file_identifier[0] if isinstance(file_identifier, list) else "")
            result = {
                "question": question,
                "answer": response.content,
                "source_file_id": file_id,
                "source_snippets": [r.get("chunk_text", "")[:200] for r in sorted_results[:3]],
                "confidence": 0.5,
                "strategy": "map_reduce_fallback",
            }
            if cache_key:
                CacheManager.set(cache_key, result, ttl=300)
            return result

        # ---- Reduce 阶段：汇总最终答案 ----
        final_answer = RAGQAService._reduce_answers(partial_answers, question)

        # 构建结果
        scores = [r.get("score", 0) for r in sorted_results[:5]]
        avg = sum(scores) / len(scores) if scores else 0.3
        snippets = [r.get("chunk_text", "")[:200] for r in sorted_results[:3]]

        file_id = file_identifier if isinstance(file_identifier, str) else (
            file_identifier[0] if isinstance(file_identifier, list) else ""
        )
        result = {
            "question": question,
            "answer": final_answer,
            "source_file_id": file_id,
            "source_snippets": snippets,
            "confidence": round(min(avg + 0.2, 0.99), 4),
            "strategy": "map_reduce",
            "map_detail": {
                "groups": {k: len(v) for k, v in groups.items()},
                "partial_count": len(partial_answers),
            },
        }
        if cache_key:
            CacheManager.set(cache_key, result, ttl=300)
        return result

    @staticmethod
    def _map_single(chunk_context: str, question: str, level: str) -> Optional[str]:
        """Map 单块处理：基于一块上下文生成部分答案"""
        level_prompt = {
            "high": "以下是**高度相关**的文档内容，请仔细分析并提取所有相关信息。",
            "medium": "以下是**中度相关**的文档内容，请提取可能与问题相关的信息。",
            "low": "以下是**低度相关**的文档内容，如果有任何相关信息请提取，否则注明'无相关信息'。",
        }
        system_msg = (
            "你是文档分析助手。请基于提供的文档内容，针对用户问题提取关键信息。\n"
            "要求：\n"
            "1. 只基于给定内容回答\n"
            "2. 如果内容中没有相关信息，明确说'无相关信息'\n"
            f"3. {level_prompt.get(level, '')}"
        )
        messages = [
            {"role": "system", "content": system_msg},
            {"role": "user", "content": f"文档内容：\n{chunk_context}\n\n用户问题：{question}\n\n请提取与该问题相关的关键信息（保留原文中的关键数据）："},
        ]
        resp = llm_client.chat(messages, module="map_reduce_map", temperature=0.3)
        content = resp.content.strip()
        if "无相关信息" in content and level == "low":
            return None
        return content

    @staticmethod
    def _reduce_answers(partial_answers: List[str], question: str) -> str:
        """Reduce 汇总：合并多个部分答案为一致完整的最终答案"""
        combined = "\n\n---\n\n".join(
            f"部分答案{i + 1}:\n{ans}" for i, ans in enumerate(partial_answers)
        )

        messages = [
            {"role": "system", "content": (
                "你是多源信息整合专家。请综合以下多个部分答案，为用户提供一个完整、一致、准确的最终答案。\n"
                "要求：\n"
                "1. 整合所有部分答案中的关键信息，去除重复\n"
                "2. 保持逻辑连贯和一致性\n"
                "3. 如果存在矛盾，指出不同来源的差异\n"
                "4. 保留重要数据和原文引用\n"
                "5. 标注关键信息的来源\n"
                "6. 如果所有部分答案都说'无相关信息'，明确告知用户文档中未找到相关信息"
            )},
            {"role": "user", "content": f"用户问题：{question}\n\n部分答案汇总：\n{combined}\n\n请整合以上信息，给出完整、一致的最终答案："},
        ]
        resp = llm_client.chat(messages, module="map_reduce_reduce", temperature=0.3)
        return resp.content.strip()

    # ==================== 辅助方法 ====================

    @staticmethod
    def _basic_truncation(context: str, max_chars: int = 16000) -> tuple:
        """基础截断 - 保底方案"""
        if len(context) <= max_chars:
            return context, "none"
        t = context[:max_chars]
        lb = max(t.rfind("\n\n"), t.rfind("。"), t.rfind("\n"))
        truncated = t[:lb + 1] if lb > max_chars // 2 else t
        logger.warning(f"Basic truncation: {len(context)} chars → {len(truncated)} chars")
        return truncated, "truncate"

    @staticmethod
    def _chunk_texts(texts: List[str], max_chars: int = 4000) -> List[List[str]]:
        """将文本列表分成若干组，每组总长度不超过 max_chars"""
        chunks = []
        current = []
        current_len = 0
        for t in texts:
            if current_len + len(t) > max_chars and current:
                chunks.append(current)
                current = []
                current_len = 0
            current.append(t)
            current_len += len(t)
        if current:
            chunks.append(current)
        return chunks
