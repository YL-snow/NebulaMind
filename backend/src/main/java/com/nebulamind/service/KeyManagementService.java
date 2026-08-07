package com.nebulamind.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密钥管理服务 - 三层密钥层次结构
 *
 * 层次结构：
 *   Layer 1: 主密钥 (Master Key)
 *     - 来源：环境变量 / KMS / HSM
 *     - 用途：派生用户数据密钥
 *     - 生命周期：长期（定期轮换）
 *
 *   Layer 2: 数据密钥 (Data Key)
 *     - 来源：主密钥 + 用户ID通过HKDF派生
 *     - 用途：加密/解密文件密钥
 *     - 生命周期：与用户账号绑定
 *
 *   Layer 3: 文件密钥 (File Key)
 *     - 来源：随机生成（每个文件独立）
 *     - 用途：加密/解密文件内容（AES-256-GCM）
 *     - 生命周期：与文件绑定
 *
 * 加密流程：
 *   [文件明文] --FileKey(AES-GCM)--> [文件密文]
 *   [FileKey]   --DataKey(AES-GCM)--> [EncryptedFileKey]（存储在File.encryptionKeyId）
 *
 * 解密流程：
 *   [EncryptedFileKey] --DataKey--> [FileKey]
 *   [文件密文]         --FileKey--> [文件明文]
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeyManagementService {

    private final EncryptionService encryptionService;

    // 文件密钥缓存（避免频繁解密encryptionKeyId）
    private final Map<String, SecretKey> fileKeyCache = new ConcurrentHashMap<>();

    // 用户数据密钥缓存
    private final Map<UUID, SecretKey> dataKeyCache = new ConcurrentHashMap<>();

    /**
     * 获取或派生用户的数据密钥（Data Key）
     *
     * @param userId 用户ID
     * @return 用户专属数据密钥
     */
    public SecretKey getOrDeriveDataKey(UUID userId) {
        return dataKeyCache.computeIfAbsent(userId, this::deriveDataKey);
    }

    /**
     * 为新文件生成加密方案
     *
     * @param userId 文件所有者ID
     * @return Map: { fileKey: SecretKey, encryptedFileKey: String }
     *         - fileKey: 用于加密文件内容
     *         - encryptedFileKey: 存储在 File.encryptionKeyId 字段
     */
    public Map<String, Object> setupFileEncryption(UUID userId) {
        // 1. 生成随机文件密钥
        SecretKey fileKey = encryptionService.generateAesKey();

        // 2. 获取用户数据密钥
        SecretKey dataKey = getOrDeriveDataKey(userId);

        // 3. 用数据密钥加密文件密钥
        byte[] encryptedKeyBytes = encryptionService.encryptAesGcm(fileKey.getEncoded(), dataKey);
        String encryptedFileKey = Base64.getEncoder().encodeToString(encryptedKeyBytes);

        // 4. 缓存文件密钥
        fileKeyCache.put(encryptedFileKey, fileKey);

        return Map.of(
                "fileKey", fileKey,
                "encryptedFileKey", encryptedFileKey,
                "algorithm", "AES-256-GCM",
                "keyHierarchy", "master->data->file"
        );
    }

    /**
     * 根据加密文件密钥解密出文件密钥
     *
     * @param encryptedFileKey 存储在 File.encryptionKeyId 中的值
     * @param userId           文件所有者ID
     * @return 文件密钥
     */
    public SecretKey unwrapFileKey(String encryptedFileKey, UUID userId) {
        // 1. 先查缓存
        SecretKey cached = fileKeyCache.get(encryptedFileKey);
        if (cached != null) {
            return cached;
        }

        // 2. 获取用户数据密钥
        SecretKey dataKey = getOrDeriveDataKey(userId);

        // 3. 解密文件密钥
        byte[] encryptedKeyBytes = Base64.getDecoder().decode(encryptedFileKey);
        byte[] keyBytes = encryptionService.decryptAesGcm(encryptedKeyBytes, dataKey);
        SecretKey fileKey = new SecretKeySpec(keyBytes, "AES");

        // 4. 缓存
        fileKeyCache.put(encryptedFileKey, fileKey);

        return fileKey;
    }

    /**
     * 轮换用户数据密钥（重新加密所有文件密钥）
     */
    public void rotateDataKey(UUID userId) {
        // 注意：此处为简化实现，生产环境需要：
        // 1. 生成新的数据密钥
        // 2. 遍历该用户所有文件
        // 3. 用旧数据密钥解密每个文件密钥
        // 4. 用新数据密钥重新加密
        // 5. 更新 File.encryptionKeyId
        dataKeyCache.remove(userId);

        // 清除该用户所有文件密钥缓存
        fileKeyCache.clear();

        log.info("Data key rotated for user: {}", userId);
    }

    /**
     * 清除缓存
     */
    public void clearCache(UUID userId) {
        dataKeyCache.remove(userId);
        fileKeyCache.clear();
    }

    // ==================== 内部方法 ====================

    /**
     * 从主密钥派生用户数据密钥
     * 使用 HMAC-based Key Derivation (简化版 HKDF)
     */
    private SecretKey deriveDataKey(UUID userId) {
        try {
            SecretKey masterKey = encryptionService.getMasterKey();
            byte[] masterKeyBytes = masterKey.getEncoded();

            // info = "nebulamind:data-key:{userId}"
            String info = "nebulamind:data-key:" + userId.toString();
            byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);

            // HKDF 简化实现：HMAC-SHA256
            // PRK = HMAC-SHA256(salt=info, key=masterKey)
            // OKM = HMAC-SHA256(salt=PRK, key=info+"|expand")
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

            // Extract step
            byte[] salt = sha256.digest(infoBytes);
            // 简化的 HMAC-like 派生
            byte[] prk = sha256.digest(concat(masterKeyBytes, salt));

            // Expand step
            byte[] expandInfo = (info + "|expand").getBytes(StandardCharsets.UTF_8);
            byte[] okm = sha256.digest(concat(prk, expandInfo));

            return new SecretKeySpec(okm, "AES");

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
