"""
加密服务 - Python端 AES-256-GCM 加密/解密
用于在AI处理前解密文件内容，处理后加密回传

与Java端 EncryptionService 保持算法一致:
  - AES-256-GCM (GCM_IV=12字节, GCM_TAG=128位)
  - IV前置格式: [IV(12字节)][密文]
"""
import os
import logging
import base64
from typing import Tuple, Optional

logger = logging.getLogger(__name__)

# 常量（与Java端一致）
GCM_IV_LENGTH = 12   # 96 bits
GCM_TAG_LENGTH = 16  # 128 bits (实际tag包含在密文中)


class CryptoService:
    """AES-256-GCM 加密服务（Python端）"""

    @staticmethod
    def generate_key() -> bytes:
        """生成随机的 AES-256 密钥（32字节）"""
        return os.urandom(32)

    @staticmethod
    def encrypt(plaintext: bytes, key: bytes) -> bytes:
        """
        AES-256-GCM 加密

        Args:
            plaintext: 明文数据
            key: 32字节AES密钥

        Returns:
            IV(12字节) + 密文(含GCM tag)
        """
        try:
            from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        except ImportError:
            # 回退到纯Python实现（使用PyCryptodome或内置）
            logger.error("cryptography library not installed. Run: pip install cryptography")
            raise ImportError(
                "需要 cryptography 库支持加密功能。请运行: pip install cryptography"
            )

        iv = os.urandom(GCM_IV_LENGTH)
        aesgcm = AESGCM(key)
        ciphertext = aesgcm.encrypt(iv, plaintext, None)
        return iv + ciphertext

    @staticmethod
    def decrypt(encrypted_data: bytes, key: bytes) -> bytes:
        """
        AES-256-GCM 解密

        Args:
            encrypted_data: IV(12字节) + 密文
            key: 32字节AES密钥

        Returns:
            明文数据
        """
        try:
            from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        except ImportError:
            logger.error("cryptography library not installed. Run: pip install cryptography")
            raise ImportError(
                "需要 cryptography 库支持解密功能。请运行: pip install cryptography"
            )

        iv = encrypted_data[:GCM_IV_LENGTH]
        ciphertext = encrypted_data[GCM_IV_LENGTH:]
        aesgcm = AESGCM(key)
        return aesgcm.decrypt(iv, ciphertext, None)

    @staticmethod
    def encrypt_string(plaintext: str, key: bytes) -> str:
        """加密字符串，返回 Base64 编码"""
        data = plaintext.encode("utf-8")
        encrypted = CryptoService.encrypt(data, key)
        return base64.b64encode(encrypted).decode("ascii")

    @staticmethod
    def decrypt_string(encrypted_base64: str, key: bytes) -> str:
        """解密 Base64 编码的加密字符串"""
        encrypted = base64.b64decode(encrypted_base64)
        data = CryptoService.decrypt(encrypted, key)
        return data.decode("utf-8")

    @staticmethod
    def derive_key_from_password(password: str, salt: bytes = None) -> Tuple[bytes, bytes]:
        """
        从密码派生出 AES-256 密钥（用于密钥层次结构）
        使用 PBKDF2-SHA256

        Args:
            password: 用户密码/主密码
            salt: 随机盐值（None则自动生成）

        Returns:
            (key: 32字节, salt: 16字节)
        """
        import hashlib
        if salt is None:
            salt = os.urandom(16)

        # PBKDF2 简化实现
        key = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 100000, dklen=32)
        return key, salt


# 全局单例
crypto_service = CryptoService()
