"""
敏感信息检测服务 - 正则 + NER + 敏感词库 + 级别评估
支持：身份证、手机号、银行卡号、邮箱、地址、公司机密、个人隐私
"""
import re
import json
import logging
from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


@dataclass
class SensitiveMatch:
    """单条敏感信息匹配结果"""
    type: str           # id_card / phone / bank_card / email / address / company_secret / personal_info
    content: str        # 匹配到的内容（脱敏后）
    original: str       # 原始内容
    position: int       # 字符位置
    confidence: float   # 置信度 0-1


@dataclass
class SensitiveReport:
    """敏感检测完整报告"""
    file_id: str
    sensitive_level: str         # high / medium / low / normal
    level_score: int             # 0-100 量化分数
    matches: List[SensitiveMatch] = field(default_factory=list)
    summary: str = ""            # 人类可读摘要
    detection_method: str = ""   # regex / llm / keyword / hybrid
    warning: str = ""            # 限流/降级等提示


# ============================================================
# 正则表达式规则库
# ============================================================
SENSITIVE_PATTERNS = {
    "id_card": {
        "pattern": r'(?<![0-9])([1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx])(?![0-9])',
        "display": "身份证号",
        "severity": "high",
        "mask": lambda m: m[:3] + "*" * max(2, len(m) - 6) + m[-3:],  # 与后端脱敏规则保持一致
    },
    "phone": {
        "pattern": r'(?<![0-9])(1[3-9]\d{9})(?![0-9])',
        "display": "手机号",
        "severity": "medium",
        "mask": lambda m: m[:3] + "*" * max(2, len(m) - 6) + m[-3:],
    },
    "bank_card": {
        "pattern": r'(?<![0-9])(\d{16,19})(?![0-9])',
        "display": "银行卡号",
        "severity": "high",
        "mask": lambda m: m[:3] + "*" * max(2, len(m) - 6) + m[-3:],
    },
    "email": {
        "pattern": r'([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,})',
        "display": "邮箱地址",
        "severity": "low",
        "mask": lambda m: m[:3] + "*" * max(2, len(m) - 6) + m[-3:] if len(m) > 6 else "***",
    },
    "address": {
        "pattern": r'(?:北京市|天津市|上海市|重庆市|河北省|山西省|辽宁省|吉林省|黑龙江省|江苏省|浙江省|安徽省|福建省|江西省|山东省|河南省|湖北省|湖南省|广东省|海南省|四川省|贵州省|云南省|陕西省|甘肃省|青海省|内蒙古|广西|西藏|宁夏|新疆|香港|澳门)[一-龥]{0,10}(?:市|区|县|镇|乡|路|街|道|巷|弄|号|楼|室|层|栋|单元|小区|花园|大厦|公寓|广场|中心)[一-龥\d]{0,20}(?:号|楼|室|层|栋|单元)?',
        "display": "地址信息",
        "severity": "medium",
        "mask": lambda m: m[:3] + "***",
    },
}


# ============================================================
# 敏感词库
# ============================================================
SENSITIVE_KEYWORDS = {
    "high": [
        "军事机密", "国家秘密", "国防科技", "武器系统",
        "内部绝密", "最高机密", "TOP SECRET",
        "root密码", "数据库密码", "production密钥",
        "生产环境配置", "线上数据库", "主密钥",
    ],
    "medium": [
        "员工薪资", "劳动合同", "个人档案",
        "客户名单", "供应商合同", "商业计划书",
        "未发布产品", "内部定价", "竞标方案",
        "股权结构", "融资协议", "并购计划",
    ],
    "low": [
        "会议纪要", "内部通知", "组织架构",
        "项目排期", "技术方案", "代码规范",
    ],
}


class SensitiveDetector:
    """敏感信息检测器 - 混合策略（正则 + 关键词 + LLM NER）"""

    def __init__(self):
        self._llm_warning = ""
        self._compiled_patterns = {}
        for key, config in SENSITIVE_PATTERNS.items():
            self._compiled_patterns[key] = re.compile(config["pattern"])
        self._keyword_map = {}  # keyword -> level
        for level, keywords in SENSITIVE_KEYWORDS.items():
            for kw in keywords:
                self._keyword_map[kw.lower()] = level

    # ---- 正则检测 ----
    def detect_by_regex(self, content: str) -> List[SensitiveMatch]:
        """基于正则表达式的敏感信息检测"""
        matches = []
        for ptype, config in SENSITIVE_PATTERNS.items():
            pattern = self._compiled_patterns[ptype]
            for m in pattern.finditer(content):
                original = m.group(0)
                # 跳过明显不是银行卡号的数字（如文件大小、时间戳）
                if ptype == "bank_card" and (self._is_false_bank_card(content, m.start(), original)
                        or not self._is_luhn_valid(original)):
                    continue
                matches.append(SensitiveMatch(
                    type=ptype,
                    content=config["mask"](original),
                    original=original,
                    position=m.start(),
                    confidence=0.95,
                ))
        # 最多返回 20 条，去重
        seen = set()
        unique = []
        for m in matches:
            key = (m.type, m.original)
            if key not in seen:
                seen.add(key)
                unique.append(m)
        return unique[:20]

    def _is_false_bank_card(self, content: str, pos: int, matched: str) -> bool:
        """过滤误识别：检查前后文是否为文件大小、ID等"""
        ctx_start = max(0, pos - 30)
        ctx_end = min(len(content), pos + len(matched) + 30)
        ctx = content[ctx_start:ctx_end].lower()
        false_keywords = ["size", "大小", "bytes", "id", "编号", "timestamp", "时间戳",
                          "width", "height", "length", "offset", "count", "total"]
        # 如果上下文只有纯数字环境（如表格列），可能是误判
        if re.search(r'\b(?:size|大小|bytes)\s*[:=]?\s*' + re.escape(matched), ctx, re.IGNORECASE):
            return True
        return False

    @staticmethod
    def _is_luhn_valid(digits: str) -> bool:
        """银行卡号 Luhn 校验，避免把身份证号等长数字误判为银行卡"""
        if not digits or not digits.isdigit() or not 16 <= len(digits) <= 19:
            return False
        total = 0
        for index, ch in enumerate(reversed(digits)):
            digit = int(ch)
            if index % 2 == 1:
                digit *= 2
                if digit > 9:
                    digit -= 9
            total += digit
        return total % 10 == 0

    # ---- 敏感词检测 ----
    def detect_by_keywords(self, content: str) -> List[SensitiveMatch]:
        """基于敏感词库的检测"""
        matches = []
        content_lower = content.lower()
        for keyword, level in self._keyword_map.items():
            idx = content_lower.find(keyword)
            if idx >= 0:
                original = content[idx:idx + len(keyword)]
                matches.append(SensitiveMatch(
                    type="company_secret",
                    content=original[:2] + "***" + original[-1:] if len(original) > 3 else "***",
                    original=original,
                    position=idx,
                    confidence=0.80,
                ))
        return matches[:10]

    # ---- LLM NER 检测 ----
    def detect_by_llm(self, file_id: str, content: str) -> List[SensitiveMatch]:
        """基于LLM的命名实体识别检测敏感信息"""
        truncated = content[:6000]  # LLM 上下文限制
        try:
            from app.core.llm_client import llm_client
            from app.prompts.templates import PromptManager

            messages = PromptManager.format("sensitive_detect", content=truncated)
            response = llm_client.chat(
                messages,
                module="sensitive_detector",
                file_id=file_id,
                temperature=0.1,
            )

            if response.content and (
                "RATE_LIMITED" in response.content
                or "暂时不可用" in response.content
            ):
                self._llm_warning = "AI 检测服务当前繁忙或已达调用上限，本次仅完成本地正则检测，请稍后重试。"
                return []

            # 解析 LLM 返回的 JSON
            result = self._parse_llm_response(response.content)
            matches = []
            for item in result.get("sensitive_items", []):
                ptype = item.get("type", "personal_info")
                original = str(item.get("content", ""))[:200]
                position = item.get("position", 0)
                try:
                    position = int(position)
                except (TypeError, ValueError):
                    position = 0
                recovered = self._recover_llm_original(content, original, position)
                if recovered is not None:
                    original, position = recovered
                if ptype == "bank_card" and not self._is_luhn_valid(original):
                    continue
                masked = self._mask_for_type(ptype, original)
                matches.append(SensitiveMatch(
                    type=ptype,
                    content=masked,
                    original=original,
                    position=position,
                    confidence=float(item.get("confidence", 0.7)),
                ))
            return matches
        except Exception as e:
            logger.warning(f"LLM sensitive detection failed: {e}")
            return []

    def _recover_llm_original(self, content: str, raw: str, position: int) -> Optional[Tuple[str, int]]:
        """当 LLM 返回脱敏内容时，尽量从原文按位置恢复完整敏感值"""
        if content and position is not None and 0 <= position < len(content):
            window = content[position:position + 60]
            digit_match = re.match(r'\d{16,19}', window)
            if digit_match:
                return digit_match.group(), position
        return None

    @staticmethod
    def _mask_for_type(ptype: str, original: str) -> str:
        """按类型对敏感值脱敏；类型未知时使用通用规则"""
        if not original:
            return ""
        config = SENSITIVE_PATTERNS.get(ptype)
        if config:
            try:
                return config["mask"](original)
            except Exception:
                pass
        if len(original) <= 6:
            return "***"
        return original[:3] + "***" + original[-3:]

    def _parse_llm_response(self, content: str) -> Dict:
        """鲁棒解析 LLM 返回的 JSON"""
        try:
            return json.loads(content)
        except json.JSONDecodeError:
            m = re.search(r'\{[\s\S]*\}', content)
            if m:
                try:
                    return json.loads(m.group())
                except json.JSONDecodeError:
                    pass
        return {"sensitive_items": [], "sensitive_level": "normal"}

    # ---- 综合检测 ----
    def detect_all(self, file_id: str, content: str, use_llm: bool = True) -> SensitiveReport:
        """
        执行综合敏感信息检测（正则 + 关键词 + 可选LLM）
        返回完整报告
        """
        all_matches: List[SensitiveMatch] = []
        methods = []
        self._llm_warning = ""

        # 1. 正则检测
        regex_matches = self.detect_by_regex(content)
        all_matches.extend(regex_matches)
        if regex_matches:
            methods.append("regex")

        # 2. 敏感词检测
        kw_matches = self.detect_by_keywords(content)
        all_matches.extend(kw_matches)
        if kw_matches:
            methods.append("keyword")

        # 3. LLM NER 检测
        llm_matches = []
        if use_llm:
            llm_matches = self.detect_by_llm(file_id, content)
            # 去重：LLM 可能重复匹配正则已发现的
            regex_originals = {m.original for m in regex_matches}
            llm_matches = [m for m in llm_matches if m.original not in regex_originals]
            all_matches.extend(llm_matches)
            if llm_matches:
                methods.append("llm")

        # ---- 敏感级别评估 ----
        level, score = self._evaluate_level(all_matches)

        # ---- 生成摘要 ----
        summary = self._generate_summary(all_matches, level)

        return SensitiveReport(
            file_id=file_id,
            sensitive_level=level,
            level_score=score,
            matches=all_matches,
            summary=summary,
            detection_method="+".join(methods) if methods else "hybrid",
            warning=self._llm_warning,
        )

    # ---- 敏感级别评估 ----
    def _evaluate_level(self, matches: List[SensitiveMatch]) -> Tuple[str, int]:
        """
        评估敏感级别
        算法：
          - id_card 每个 +25分, bank_card 每个 +25分
          - phone 每个 +10分, address 每个 +10分
          - email 每个 +5分, keyword_high 每个 +20分
          - keyword_medium 每个 +10分, keyword_low 每个 +5分
          - 总分 >= 60 → high, >= 30 → medium, >= 10 → low, < 10 → normal
        """
        if not matches:
            return "normal", 0

        type_scores = {
            "id_card": 30,
            "bank_card": 30,
            "phone": 10,
            "address": 10,
            "email": 5,
            "company_secret": 20,
            "personal_info": 10,
        }

        total_score = 0
        for m in matches:
            score = type_scores.get(m.type, 5)
            total_score += int(score * m.confidence)

        total_score = min(total_score, 100)

        if total_score >= 35:
            return "high", total_score
        elif total_score >= 20:
            return "medium", total_score
        elif total_score >= 5:
            return "low", total_score
        else:
            return "normal", total_score

    # ---- 摘要生成 ----
    def _generate_summary(self, matches: List[SensitiveMatch], level: str) -> str:
        """生成人类可读的检测摘要"""
        if not matches:
            return "未检测到敏感信息"

        type_counts = {}
        for m in matches:
            type_name = SENSITIVE_PATTERNS.get(m.type, {}).get("display", m.type)
            type_counts[type_name] = type_counts.get(type_name, 0) + 1

        parts = [f"检测到 {len(matches)} 处敏感信息"]
        for tname, count in type_counts.items():
            parts.append(f"{tname} {count}处")

        level_cn = {"high": "高", "medium": "中", "low": "低", "normal": "正常"}
        parts.append(f"综合级别: {level_cn.get(level, level)}")

        return "，".join(parts)

    # ---- 脱敏工具 ----
    @staticmethod
    def mask_content(content: str, matches: List[SensitiveMatch]) -> str:
        """对内容中的敏感信息进行脱敏替换"""
        # 按位置倒序替换，避免偏移
        sorted_matches = sorted(matches, key=lambda m: m.position, reverse=True)
        result = content
        for m in sorted_matches:
            if m.position < len(result):
                result = result[:m.position] + m.content + result[m.position + len(m.original):]
        return result


# 全局单例
sensitive_detector = SensitiveDetector()
