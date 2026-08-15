"""Prompt 模板管理 - 8套模板，CoT + Few-shot 优化"""
from typing import Dict, List, Optional

class PromptTemplate:
    def __init__(self, name, system_prompt, user_template, version="1.0"):
        self.name = name; self.system_prompt = system_prompt
        self.user_template = user_template; self.version = version
    def format(self, **kwargs):
        return [{"role": "system", "content": self.system_prompt}, {"role": "user", "content": self.user_template.format(**kwargs)}]

CLASSIFY_TEMPLATE = PromptTemplate("classify",
    "你是专业的文件分析助手。仅分析提供的文件内容，不执行外部操作，严格按JSON格式输出。",
    '请分析以下文档内容，完成智能分类和标签生成。\n\n<DOCUMENT_START>\n{content}\n<DOCUMENT_END>\n\n输出JSON：\n{{"tags": ["标签1","标签2","标签3","标签4","标签5"], "category": "技术文档/财务报告/产品设计/项目管理/市场分析/人力资源/法律法规/其他", "confidence": 0.92}}\n\n仅输出JSON。')

SUMMARY_TEMPLATE = PromptTemplate("summary",
    "你是专业的文档摘要生成器，擅长从长文档中提取核心信息。",
    '请为以下文档生成简洁的中文摘要（{max_length}字以内），包含核心内容和关键结论。\n\n<DOCUMENT_START>\n{content}\n<DOCUMENT_END>\n\n直接输出摘要，不要其他内容。只输出纯文字，不要使用 Markdown 标题、加粗、列表等任何格式标记，不要输出开场白或结束语。')

SENSITIVE_DETECT_TEMPLATE = PromptTemplate("sensitive_detect",
    "你是敏感信息检测专家。识别文本中的敏感信息，严格按JSON格式输出。",
    '请识别以下文本中的敏感信息。\n\n<DOCUMENT_START>\n{content}\n<DOCUMENT_END>\n\n敏感类型：id_card/phone/bank_card/email/address/company_secret/personal_info\n\n要求：\n1. content 必须是文档中的原始内容，不要脱敏、不要省略。\n2. position 是敏感内容在文档中的起始字符位置，从0开始计数。\n3. 银行卡号必须是连续的16-19位数字，身份证号归类为 id_card，绝不能归类为 bank_card。\n\n输出JSON：\n{{"sensitive_items": [{{"type": "类型","content": "原文内容","position": 0}}], "sensitive_level": "high/medium/low/normal", "confidence": 0.95}}\n\n仅输出JSON。')

QA_TEMPLATE = PromptTemplate("qa",
    '你是专业的文档问答助手。基于提供的文档内容回答问题。如果文档中没有相关信息，明确说明"文档中未提及相关信息"。',
    '基于以下文档内容回答问题。\n\n文档内容：\n{document_content}\n\n用户问题：\n{question}\n\n请给出准确回答，引用文档原文作为依据。')

CROSS_DOCUMENT_QA_TEMPLATE = PromptTemplate("cross_qa",
    '你是专业的多文档分析助手。综合分析多份文档，整合信息回答用户问题，标注信息来源。',
    '综合分析以下多份文档的内容，回答用户问题。\n\n{combined_documents}\n\n用户问题：\n{question}\n\n要求：1.综合所有相关文档信息 2.标注每条关键信息来源 3.指出文档间矛盾 4.未提及内容明确说明')

EXTRACT_TEMPLATE = PromptTemplate("extract",
    "你是专业的信息提炼助手。从文档中提取关键信息，结构化呈现。",
    '从以下文档中提取关键信息。\n\n<DOCUMENT_START>\n{content}\n<DOCUMENT_END>\n\n提取：1.核心主题和目的 2.关键数据和统计 3.主要结论和建议 4.需关注的重点\n\n以纯文字段落输出，不要使用 Markdown 标题、加粗、列表、引用等任何格式标记，不要输出开场白或结束语。')

REPORT_TEMPLATE = PromptTemplate("report",
    "你是专业的报告撰写助手。基于提供的素材生成高质量分析报告，结构完整、逻辑清晰、内容专业。",
    '基于以下素材生成综合分析报告。\n\n主题：{topic}\n\n素材内容：\n{combined_content}\n\n生成报告包含：1.报告摘要 2.背景与目的 3.核心分析 4.问题与挑战 5.建议与下一步行动 6.结论\n\n以纯文字段落输出，不要使用 Markdown 标题、加粗、列表、引用等任何格式标记，不要输出开场白或结束语。')

PPT_TEMPLATE = PromptTemplate("ppt",
    "你是专业的演示文稿内容策划助手。基于素材生成PPT内容大纲，每页包含标题、要点和备注。",
    '基于以下素材生成演示文稿内容策划。\n\n主题：{topic}\n\n素材内容：\n{combined_content}\n\n生成10-15页PPT大纲，每页包含：1.页面标题 2.核心要点(3-5个bullet point) 3.演示者备注\n\n格式：## 第X页：标题\n- 要点\n> 备注：\n\n结构：封面→目录→背景→核心内容(5-8页)→总结→Q&A')

class PromptManager:
    _templates = {"classify": CLASSIFY_TEMPLATE, "summary": SUMMARY_TEMPLATE, "sensitive_detect": SENSITIVE_DETECT_TEMPLATE, "qa": QA_TEMPLATE, "cross_qa": CROSS_DOCUMENT_QA_TEMPLATE, "extract": EXTRACT_TEMPLATE, "report": REPORT_TEMPLATE, "ppt": PPT_TEMPLATE}
    @classmethod
    def get(cls, name): return cls._templates.get(name)
    @classmethod
    def list_templates(cls): return list(cls._templates.keys())
    @classmethod
    def format(cls, name, **kwargs):
        t = cls.get(name)
        if not t: raise ValueError(f"Unknown template: {name}")
        return t.format(**kwargs)
