"""文档分块策略 - 语义分块 + 重叠"""
import re, logging
from typing import List, Dict, Any
logger = logging.getLogger(__name__)

class TextSplitter:
    def __init__(self, min_chunk_size=512, max_chunk_size=2048, overlap_size=256):
        self.min = min_chunk_size; self.max = max_chunk_size; self.overlap = overlap_size

    def split(self, text, metadata=None):
        if not text or not text.strip(): return []
        paras = [p.strip() for p in re.split(r'\n\s*\n', text) if p.strip()]
        chunks = self._merge_and_split(paras)
        chunks = self._add_overlap(chunks)
        base_meta = metadata or {}
        return [{"text": c, "metadata": {**base_meta, "chunk_index": i, "total_chunks": len(chunks)}, "index": i} for i, c in enumerate(chunks)]

    def _merge_and_split(self, paragraphs):
        chunks = []; current = ""
        for para in paragraphs:
            if len(para) > self.max:
                if current: chunks.append(current.strip()); current = ""
                chunks.extend(self._split_long(para)); continue
            if current and len(current) + len(para) + 1 > self.max:
                chunks.append(current.strip()); current = para
            else: current = (current + "\n\n" + para).strip() if current else para
        if current.strip(): chunks.append(current.strip())
        return chunks

    def _split_long(self, para):
        sentences = [s.strip() for s in re.split(r'(?<=[。！？.!?\n])\s*', para) if s.strip()]
        chunks = []; current = ""
        for s in sentences:
            if len(s) > self.max:
                if current: chunks.append(current.strip()); current = ""
                for i in range(0, len(s), self.max - self.overlap): chunks.append(s[i:i+self.max].strip())
                continue
            if current and len(current) + len(s) > self.max:
                chunks.append(current.strip()); current = s
            else: current = (current + " " + s).strip() if current else s
        if current.strip(): chunks.append(current.strip())
        return chunks

    def _add_overlap(self, chunks):
        if len(chunks) <= 1: return chunks
        result = []
        for i, c in enumerate(chunks):
            if i > 0:
                prev = chunks[i-1]; prefix = prev[-self.overlap:] if len(prev) > self.overlap else prev
                c = prefix + "\n\n" + c
            if i < len(chunks) - 1:
                nxt = chunks[i+1]; suffix = nxt[:self.overlap] if len(nxt) > self.overlap else nxt
                c = c + "\n\n" + suffix
            result.append(c)
        return result

    def estimate_tokens(self, text):
        cn = len(re.findall(r'[一-鿿]', text)); other = len(text) - cn
        return int(cn / 1.5 + other / 4)
