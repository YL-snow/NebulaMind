package com.nebulamind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 文件加密服务
 *
 * 提供两层加密：
 *   - AES-256-GCM：对称加密文件内容（速度快，适合大文件）
 *   - RSA-2048：非对称加密，用于安全传输 AES 密钥
 *
 * 加密流程：
 *   上传：明文 → AES-256-GCM加密 → 密文存储到MinIO
 *   下载：MinIO密文 → AES-256-GCM解密 → 明文返回
 *
 * 密钥层次结构：
 *   主密钥（Master Key）→ 数据密钥（Data Key）→ 文件加密密钥（File Key）
 *     - 主密钥：存储在环境变量/密钥管理服务中
 *     - 数据密钥：由主密钥派生，每个用户一个
 *     - 文件密钥：随机生成，每个文件一个，使用AES-256-GCM
 */
@Slf4j
@Service
public class EncryptionService {

    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;  // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int RSA_KEY_SIZE = 2048;

    @Value("${nebulamind.encryption.master-key:}")
    private String masterKeyBase64;

    // ==================== AES-256-GCM 对称加密 ====================

    /**
     * 生成随机的 AES-256 密钥
     */
    public SecretKey generateAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_SIZE, new SecureRandom());
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES key generation failed", e);
        }
    }

    /**
     * AES-256-GCM 加密
     *
     * @param plaintext 明文
     * @param key       密钥
     * @return 加密结果（IV + 密文），格式: [IV(12字节)][密文]
     */
    public byte[] encryptAesGcm(byte[] plaintext, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            byte[] ciphertext = cipher.doFinal(plaintext);

            // 前置 IV
            return ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();

        } catch (Exception e) {
            log.error("AES-GCM encryption failed", e);
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * AES-256-GCM 解密
     *
     * @param encryptedData 加密数据（IV + 密文格式）
     * @param key           密钥
     * @return 明文
     */
    public byte[] decryptAesGcm(byte[] encryptedData, SecretKey key) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            return cipher.doFinal(ciphertext);

        } catch (Exception e) {
            log.error("AES-GCM decryption failed", e);
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 加密字符串（便捷方法）
     */
    public String encryptString(String plaintext, SecretKey key) {
        byte[] encrypted = encryptAesGcm(plaintext.getBytes(StandardCharsets.UTF_8), key);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 解密字符串（便捷方法）
     */
    public String decryptString(String encryptedBase64, SecretKey key) {
        byte[] encrypted = Base64.getDecoder().decode(encryptedBase64);
        return new String(decryptAesGcm(encrypted, key), StandardCharsets.UTF_8);
    }

    // ==================== RSA-2048 非对称加密 ====================

    /**
     * 生成 RSA-2048 密钥对
     */
    public KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(RSA_KEY_SIZE, new SecureRandom());
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA key generation failed", e);
        }
    }

    /**
     * RSA 公钥加密（用于安全传输 AES 密钥）
     */
    public byte[] encryptRsa(byte[] data, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            log.error("RSA encryption failed", e);
            throw new RuntimeException("RSA加密失败", e);
        }
    }

    /**
     * RSA 私钥解密
     */
    public byte[] decryptRsa(byte[] encryptedData, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            log.error("RSA decryption failed", e);
            throw new RuntimeException("RSA解密失败", e);
        }
    }

    /**
     * 用 RSA 公钥加密 AES 密钥（密钥传输）
     *
     * @param aesKey     AES 密钥
     * @param publicKey  RSA 公钥
     * @return Base64 编码的加密结果
     */
    public String wrapAesKeyWithRsa(SecretKey aesKey, PublicKey publicKey) {
        byte[] wrapped = encryptRsa(aesKey.getEncoded(), publicKey);
        return Base64.getEncoder().encodeToString(wrapped);
    }

    /**
     * 用 RSA 私钥解密 AES 密钥
     *
     * @param wrappedKeyBase64 Base64 编码的加密密钥
     * @param privateKey       RSA 私钥
     * @return AES SecretKey
     */
    public SecretKey unwrapAesKeyWithRsa(String wrappedKeyBase64, PrivateKey privateKey) {
        byte[] wrapped = Base64.getDecoder().decode(wrappedKeyBase64);
        byte[] keyBytes = decryptRsa(wrapped, privateKey);
        return new SecretKeySpec(keyBytes, "AES");
    }

    // ==================== 密钥序列化 ====================

    /**
     * 将 SecretKey 编码为 Base64
     */
    public String encodeKey(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从 Base64 解码 SecretKey
     */
    public SecretKey decodeAesKey(String encodedKey) {
        byte[] decoded = Base64.getDecoder().decode(encodedKey);
        return new SecretKeySpec(decoded, "AES");
    }

    /**
     * 将 PublicKey 编码为 Base64
     */
    public String encodePublicKey(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从 Base64 解码 PublicKey
     */
    public PublicKey decodePublicKey(String encodedKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode public key", e);
        }
    }

    /**
     * 将 PrivateKey 编码为 Base64
     */
    public String encodePrivateKey(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从 Base64 解码 PrivateKey
     */
    public PrivateKey decodePrivateKey(String encodedKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode private key", e);
        }
    }

    /**
     * 获取或派生出主密钥
     */
    public SecretKey getMasterKey() {
        if (masterKeyBase64 != null && !masterKeyBase64.isBlank()) {
            return decodeAesKey(masterKeyBase64);
        }
        // 开发模式：从固定种子生成密钥
        log.warn("No master key configured. Using dev-mode derived key. Set NEBULAMIND_ENCRYPTION_MASTER_KEY for production.");
        try {
            byte[] seed = "NebulaMind-Dev-MasterKey-2024".getBytes(StandardCharsets.UTF_8);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] derived = sha256.digest(seed);
            return new SecretKeySpec(derived, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
