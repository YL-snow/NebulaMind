"""Markdown format cleaner: turn AI text output into plain text."""
import re

_PREAMBLE_PREFIXES = (
    "好的，", "好的,", "好的。", "好的！", "好的!",
    "作为一名", "作为专业的",
    "下面", "以下是", "以下为",
    "我将", "我来", "让我",
)


def to_plain_text(text):
    if not text:
        return text
    lines = text.split("\n")
    cleaned = []
    for raw in lines:
        stripped = raw.strip()
        if not stripped:
            cleaned.append("")
            continue
        if stripped.startswith("```") or stripped.startswith("~~~"):
            continue
        if re.fullmatch(r"[-*_=]{3,}", stripped):
            continue
        line = re.sub(r"^#{1,6}\s*", "", raw)
        line = re.sub(r"^>\s?", "", line)
        line = re.sub(r"^\s*[-*+]\s+", "", line)
        line = re.sub(r"\*\*([^*]+)\*\*", r"\1", line)
        line = re.sub(r"__([^_]+)__", r"\1", line)
        line = re.sub(r"(?<!\*)\*([^*\n]+)\*(?!\*)", r"\1", line)
        line = re.sub(r"(?<!_)_([^_\n]+)_(?!_)", r"\1", line)
        line = re.sub(r"`([^`]+)`", r"\1", line)
        cleaned.append(line)
    lines = cleaned
    while lines:
        first = lines[0].strip()
        if not first:
            lines.pop(0)
            continue
        remaining = [ln for ln in lines[1:] if ln.strip()]
        if first.startswith(_PREAMBLE_PREFIXES) and remaining:
            lines.pop(0)
        else:
            break
    text = "\n".join(lines).strip()
    return re.sub(r"\n{3,}", "\n\n", text)
