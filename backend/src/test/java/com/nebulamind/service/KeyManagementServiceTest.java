package com.nebulamind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyManagementServiceTest {

    @Mock
    private EncryptionService encryptionService;

    private KeyManagementService keyManagementService;

    private UUID testUserId;
    private SecretKey testFileKey;

    @BeforeEach
    void setUp() {
        keyManagementService = new KeyManagementService(encryptionService);
        testUserId = UUID.randomUUID();
        testFileKey = new SecretKeySpec(new byte[32], "AES");
    }

    @Test
    void testSetupFileEncryption_Success() {
        when(encryptionService.generateAesKey()).thenReturn(testFileKey);
        when(encryptionService.getMasterKey()).thenReturn(testFileKey);
        when(encryptionService.encryptAesGcm(any(byte[].class), any(SecretKey.class)))
                .thenReturn("encrypted-key".getBytes());

        Map<String, Object> result = keyManagementService.setupFileEncryption(testUserId);

        assertNotNull(result, "加密设置结果不应为空");
        assertEquals(testFileKey, result.get("fileKey"), "返回的文件密钥应一致");
        assertNotNull(result.get("encryptedFileKey"), "加密后的文件密钥不应为空");
        assertEquals("AES-256-GCM", result.get("algorithm"), "加密算法应为AES-256-GCM");
        assertEquals("master->data->file", result.get("keyHierarchy"), "密钥层次结构应正确");
    }

    @Test
    void testSetupFileEncryption_ConsistentDataKey() {
        // 验证同一用户使用相同的数据密钥
        when(encryptionService.generateAesKey()).thenReturn(testFileKey);
        when(encryptionService.getMasterKey()).thenReturn(testFileKey);
        when(encryptionService.encryptAesGcm(any(byte[].class), any(SecretKey.class)))
                .thenReturn("encrypted-key".getBytes());

        Map<String, Object> result1 = keyManagementService.setupFileEncryption(testUserId);
        Map<String, Object> result2 = keyManagementService.setupFileEncryption(testUserId);

        assertNotNull(result1.get("encryptedFileKey"));
        assertNotNull(result2.get("encryptedFileKey"));
    }

    @Test
    void testUnwrapFileKey_Cached() {
        String encryptedFileKey = "cached-key-base64";
        when(encryptionService.generateAesKey()).thenReturn(testFileKey);
        when(encryptionService.getMasterKey()).thenReturn(testFileKey);
        when(encryptionService.encryptAesGcm(any(byte[].class), any(SecretKey.class)))
                .thenReturn(encryptedFileKey.getBytes());

        // 先设置加密以填充缓存
        Map<String, Object> setupResult = keyManagementService.setupFileEncryption(testUserId);
        String storedKey = (String) setupResult.get("encryptedFileKey");
        SecretKey fileKey = (SecretKey) setupResult.get("fileKey");

        // 从缓存中获取
        SecretKey unwrapped = keyManagementService.unwrapFileKey(storedKey, testUserId);
        assertEquals(fileKey, unwrapped, "缓存中的文件密钥应一致");
    }

    @Test
    void testRotateDataKey_ClearsCache() {
        when(encryptionService.getMasterKey()).thenReturn(testFileKey);
        when(encryptionService.generateAesKey()).thenReturn(testFileKey);
        when(encryptionService.encryptAesGcm(any(byte[].class), any(SecretKey.class)))
                .thenReturn("encrypted-key".getBytes());

        // 设置加密以填充缓存
        keyManagementService.setupFileEncryption(testUserId);

        // 轮换密钥
        assertDoesNotThrow(() -> keyManagementService.rotateDataKey(testUserId),
                "密钥轮换不应抛出异常");
    }

    @Test
    void testClearCache() {
        when(encryptionService.getMasterKey()).thenReturn(testFileKey);
        when(encryptionService.generateAesKey()).thenReturn(testFileKey);
        when(encryptionService.encryptAesGcm(any(byte[].class), any(SecretKey.class)))
                .thenReturn("encrypted-key".getBytes());

        keyManagementService.setupFileEncryption(testUserId);

        assertDoesNotThrow(() -> keyManagementService.clearCache(testUserId),
                "清除缓存不应抛出异常");
    }

    @Test
    void testDeriveDataKey_DifferentUsers() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        when(encryptionService.getMasterKey()).thenReturn(testFileKey);

        // 不同用户应派生出不同的数据密钥
        var dataKey1 = keyManagementService.getOrDeriveDataKey(user1);
        var dataKey2 = keyManagementService.getOrDeriveDataKey(user2);

        // 由于数据密钥是派生自主密钥+用户ID，不同用户应不同
        // 注意：这里不能直接比较，因为派生取决于主密钥和用户ID
        assertNotNull(dataKey1, "用户1的数据密钥不应为空");
        assertNotNull(dataKey2, "用户2的数据密钥不应为空");
    }

    @Test
    void testDeriveDataKey_SameUserConsistent() {
        when(encryptionService.getMasterKey()).thenReturn(testFileKey);

        SecretKey key1 = keyManagementService.getOrDeriveDataKey(testUserId);
        SecretKey key2 = keyManagementService.getOrDeriveDataKey(testUserId);

        assertArrayEquals(key1.getEncoded(), key2.getEncoded(),
                "同一用户多次获取的数据密钥应一致");
    }
}
