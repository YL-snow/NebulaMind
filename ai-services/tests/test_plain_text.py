"""Plain text cleaner tests."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.utils.plain_text import to_plain_text


def test_strips_markdown_markers():
    text = "# 标题\n\n**加粗** 和 `代码`\n\n- 列表项\n\n1. 数字项\n\n> 引用\n\n---"
    out = to_plain_text(text)
    assert "# 标题" not in out
    assert "**加粗**" not in out
    assert "`代码`" not in out
    assert "- 列表项" not in out
    assert "> 引用" not in out
    assert "标题" in out and "加粗" in out and "代码" in out
    assert "列表项" in out and "数字项" in out and "引用" in out


def test_keeps_multiply_sign():
    text = "环境存在 N*M 矩阵依赖问题"
    assert to_plain_text(text) == "环境存在 N*M 矩阵依赖问题"


def test_removes_greeting_line():
    text = "好的，作为专业的报告撰写助手，我将基于素材生成报告。\n\n# 报告标题\n正文"
    out = to_plain_text(text)
    assert not out.startswith("好的")
    assert "报告标题" in out


if __name__ == "__main__":
    test_strips_markdown_markers()
    test_keeps_multiply_sign()
    test_removes_greeting_line()
    print("plain_text tests passed")
