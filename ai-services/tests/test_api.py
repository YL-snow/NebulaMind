"""AI服务集成测试 - 19个测试用例"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import pytest
from fastapi.testclient import TestClient
from main import app
client = TestClient(app)

class TestHealthCheck:
    def test_health(self):
        r = client.get("/health"); assert r.status_code == 200; assert r.json()["status"] == "healthy"
    def test_stats(self):
        r = client.get("/api/v1/stats"); assert r.status_code == 200; assert "total_calls" in r.json()

class TestClassification:
    def test_classify_document(self):
        r = client.post("/api/v1/classify", json={"file_id": "t1", "content": "基于大模型技术的云盘智能体应用开发方案。采用Spring Boot+Python双服务架构。"})
        assert r.status_code == 200; d = r.json(); assert d["file_id"] == "t1"; assert "category" in d; assert len(d["tags"]) > 0
    def test_classify_empty(self):
        r = client.post("/api/v1/classify", json={"file_id": "t2", "content": "空"}); assert r.status_code == 200

class TestSemanticSearch:
    def test_search_basic(self):
        r = client.post("/api/v1/search", json={"query": "技术方案", "top_k": 5}); assert r.status_code == 200; assert r.json()["query"] == "技术方案"
    def test_search_with_file_ids(self):
        r = client.post("/api/v1/search", json={"query": "云盘", "file_ids": ["f1", "f2"], "top_k": 3}); assert r.status_code == 200

class TestDocumentQA:
    def test_single_qa(self):
        r = client.post("/api/v1/qa", json={"file_id": "t1", "question": "系统采用什么架构？"}); assert r.status_code == 200; assert "answer" in r.json()
    def test_cross_qa(self):
        r = client.post("/api/v1/qa/cross", json={"file_ids": ["f1", "f2"], "question": "核心功能有哪些？"}); assert r.status_code == 200; assert "answer" in r.json()
    def test_qa_missing_id(self):
        r = client.post("/api/v1/qa", json={"question": "test"}); assert r.status_code != 200

class TestContentGeneration:
    def test_summary(self):
        r = client.post("/api/v1/generate/summary", json={"file_id": "t1", "content": "测试文档内容。" * 3, "max_length": 100}); assert r.status_code == 200
    def test_extract(self):
        r = client.post("/api/v1/generate/extract", json={"file_id": "t1", "content": "项目背景：测试。核心功能：测试。"}); assert r.status_code == 200
    def test_report(self):
        r = client.post("/api/v1/generate/report", json={"file_ids": ["f1", "f2"], "topic": "技术分析"}); assert r.status_code == 200
    def test_ppt(self):
        r = client.post("/api/v1/generate/ppt", json={"file_ids": ["f1"], "topic": "项目演示"}); assert r.status_code == 200

class TestCallLogs:
    def test_export_logs(self):
        r = client.get("/api/v1/logs/export"); assert r.status_code == 200; assert "logs" in r.json()
    def test_list_prompts(self):
        r = client.get("/api/v1/prompts"); assert r.status_code == 200; assert len(r.json()["templates"]) >= 6

class TestFileProcessor:
    def test_file_parser_pdf(self):
        from app.utils.file_parser import FileParser
        c = b"%PDF-1.4\n1 0 obj\n<<>>\nendobj\nxref\n0 1\ntrailer\n<<>>\nstartxref\n9\n%%EOF"
        assert isinstance(FileParser.parse("t.pdf", c), str)
    def test_file_parser_txt(self):
        from app.utils.file_parser import FileParser
        assert "Hello" in FileParser.parse("t.txt", "Hello 世界".encode("utf-8"))
    def test_text_splitter(self):
        from app.utils.text_splitter import TextSplitter
        chunks = TextSplitter(max_chunk_size=500).split("第一段。" * 50 + "\n\n" + "第二段。" * 50)
        assert len(chunks) > 0; assert "text" in chunks[0]
    def test_embedding_cache(self):
        from app.utils.cache import CacheManager
        CacheManager.set("test", {"v": 42}, ttl=60); assert CacheManager.get("test")["v"] == 42

if __name__ == "__main__":
    pytest.main([__file__, "-v"])
