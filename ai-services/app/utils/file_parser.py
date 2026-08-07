"""文件解析器 - PDF/Word/Excel/PPT/图片/TXT"""
import io, logging
logger = logging.getLogger(__name__)

class FileParser:

    # 需要解析的二进制文件扩展名
    BINARY_EXTENSIONS = {"pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt",
                        "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp"}

    @staticmethod
    def ensure_text(content, file_path=None, file_type=None, file_content_base64=None):
        """确保获取到真实的文本内容。

        优先级：
        1. file_content_base64 - base64 编码的原始文件字节（兼容所有存储后端）
        2. file_path - 从共享文件系统中解析二进制文件
        3. content - 原始文本（用于 txt/md 等纯文本格式）

        当文件是二进制格式（pdf/docx/xlsx/pptx）时，使用 FileParser 提取纯文本。
        """
        # 优先使用 base64 编码的原始字节
        if file_content_base64:
            try:
                import base64
                raw_bytes = base64.b64decode(file_content_base64)
                parsed = FileParser.parse(file_path or "file", file_content=raw_bytes)
                if parsed and len(parsed.strip()) > 20:
                    logger.info(f"FileParser extracted {len(parsed)} chars from base64 content")
                    return parsed
                logger.warning("FileParser returned empty/short content from base64, using raw content")
            except Exception as e:
                logger.warning(f"FileParser failed for base64 content: {e}, using raw content")

        # 其次尝试从文件路径解析
        if file_path:
            ext = (file_path.rsplit(".", 1)[-1].lower() if "." in file_path else ""
                   ) if not file_type else file_type.lower()
            if ext in FileParser.BINARY_EXTENSIONS:
                try:
                    parsed = FileParser.parse(file_path)
                    if parsed and len(parsed.strip()) > 20:
                        logger.info(f"FileParser extracted {len(parsed)} chars from {file_path}")
                        return parsed
                    logger.warning(f"FileParser returned empty/short content for {file_path}, using raw content")
                except Exception as e:
                    logger.warning(f"FileParser failed for {file_path}: {e}, using raw content")
        return content

    @staticmethod
    def parse(file_path, file_content=None, mime_type=None):
        ext = file_path.rsplit(".", 1)[-1].lower() if "." in file_path else ""
        parsers = {
            "pdf": FileParser._parse_pdf, "docx": FileParser._parse_docx,
            "doc": FileParser._parse_docx, "xlsx": FileParser._parse_xlsx,
            "xls": FileParser._parse_xlsx, "pptx": FileParser._parse_pptx,
            "ppt": FileParser._parse_pptx,
            # 图片格式 - 使用 OCR
            "jpg": FileParser._parse_image, "jpeg": FileParser._parse_image,
            "png": FileParser._parse_image, "gif": FileParser._parse_image,
            "bmp": FileParser._parse_image, "tiff": FileParser._parse_image,
            "tif": FileParser._parse_image, "webp": FileParser._parse_image,
        }
        # 也通过 mime_type 判断
        if mime_type and ext not in parsers:
            mime_map = {
                "image/jpeg": FileParser._parse_image, "image/png": FileParser._parse_image,
                "image/gif": FileParser._parse_image, "image/bmp": FileParser._parse_image,
                "image/tiff": FileParser._parse_image, "image/webp": FileParser._parse_image,
            }
            parser = mime_map.get(mime_type, FileParser._parse_txt)
        else:
            parser = parsers.get(ext, FileParser._parse_txt)
        try:
            if file_content: return parser(file_content)
            else:
                with open(file_path, "rb") as f: return parser(f.read())
        except Exception as e: logger.error(f"Parse failed {file_path}: {e}"); return ""

    @staticmethod
    def _parse_pdf(content):
        try:
            from PyPDF2 import PdfReader
            return "\n\n".join(p.extract_text() or "" for p in PdfReader(io.BytesIO(content)).pages)
        except: return ""

    @staticmethod
    def _parse_docx(content):
        try:
            from docx import Document
            return "\n\n".join(p.text for p in Document(io.BytesIO(content)).paragraphs if p.text.strip())
        except: return ""

    @staticmethod
    def _parse_xlsx(content):
        try:
            from openpyxl import load_workbook
            wb = load_workbook(io.BytesIO(content), data_only=True)
            texts = []
            for sn in wb.sheetnames:
                texts.append(f"## Sheet: {sn}")
                texts.append("\n".join("\t".join(str(c or "") for c in row) for row in wb[sn].iter_rows(values_only=True)))
            return "\n\n".join(texts)
        except: return ""

    @staticmethod
    def _parse_pptx(content):
        try:
            from pptx import Presentation
            texts = []
            for i, slide in enumerate(Presentation(io.BytesIO(content)).slides, 1):
                st = [f"## Slide {i}"]
                for shape in slide.shapes:
                    if shape.has_text_frame:
                        for para in shape.text_frame.paragraphs:
                            if para.text.strip(): st.append(para.text)
                texts.append("\n".join(st))
            return "\n\n".join(texts)
        except: return ""

    @staticmethod
    def _parse_image(content):
        """OCR 图片文字识别"""
        try:
            from PIL import Image
            import pytesseract
            image = Image.open(io.BytesIO(content))
            # 尝试中文识别，回退到英文
            try:
                text = pytesseract.image_to_string(image, lang='chi_sim+eng')
            except Exception:
                text = pytesseract.image_to_string(image, lang='eng')
            result = text.strip()
            logger.info(f"OCR extracted {len(result)} chars from image")
            return result if result else "[图片文件 - OCR未识别到文字]"
        except ImportError:
            logger.warning("pytesseract/Pillow not installed, cannot OCR image")
            return "[图片文件 - 缺少OCR依赖]"
        except Exception as e:
            logger.error(f"Image OCR failed: {e}")
            return "[图片文件 - OCR识别失败]"

    @staticmethod
    def _parse_txt(content):
        try: return content.decode("utf-8")
        except:
            try: return content.decode("gbk")
            except: return content.decode("utf-8", errors="replace")
