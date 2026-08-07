package com.nebulamind.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
    }

    // ==================== AES-256-GCM 加密/解密 ====================

    @Test
    void testAesGcmEncryptDecrypt_RoundTrip() {
        byte[] plaintext = "Hello, NebulaMind! This is a test message.".getBytes(StandardCharsets.UTF_8);
        SecretKey key = encryptionService.generateAesKey();

        byte[] encrypted = encryptionService.encryptAesGcm(plaintext, key);
        byte[] decrypted = encryptionService.decryptAesGcm(encrypted, key);

        assertArrayEquals(plaintext, decrypted, "解密后的内容应与原始明文一致");
    }

    @Test
    void testAesGcmEncryptDecrypt_EmptyContent() {
        byte[] plaintext = new byte[0];
        SecretKey key = encryptionService.generateAesKey();

        byte[] encrypted = encryptionService.encryptAesGcm(plaintext, key);
        byte[] decrypted = encryptionService.decryptAesGcm(encrypted, key);

        assertArrayEquals(plaintext, decrypted, "空内容加密解密后应为空");
    }

    @Test
    void testAesGcmEncryptDecrypt_LargeContent() {
        // 测试大内容（1MB）
        byte[] plaintext = new byte[1024 * 1024];
        for (int i = 0; i < plaintext.length; i++) {
            plaintext[i] = (byte) (i % 256);
        }
        SecretKey key = encryptionService.generateAesKey();

        byte[] encrypted = encryptionService.encryptAesGcm(plaintext, key);
        byte[] decrypted = encryptionService.decryptAesGcm(encrypted, key);

        assertArrayEquals(plaintext, decrypted, "大内容加密解密后应与原始内容一致");
    }

    @Test
    void testAesGcm_DifferentKeysProduceDifferentCiphertext() {
        byte[] plaintext = "Test message".getBytes(StandardCharsets.UTF_8);
        SecretKey key1 = encryptionService.generateAesKey();
        SecretKey key2 = encryptionService.generateAesKey();

        byte[] encrypted1 = encryptionService.encryptAesGcm(plaintext, key1);
        byte[] encrypted2 = encryptionService.encryptAesGcm(plaintext, key2);

        assertNotEquals(
                Base64.getEncoder().encodeToString(encrypted1),
                Base64.getEncoder().encodeToString(encrypted2),
                "不同密钥加密结果应不同"
        );
    }

    @Test
    void testAesGcm_WrongKeyFails() {
        byte[] plaintext = "Secret data".getBytes(StandardCharsets.UTF_8);
        SecretKey key1 = encryptionService.generateAesKey();
        SecretKey key2 = encryptionService.generateAesKey();

        byte[] encrypted = encryptionService.encryptAesGcm(plaintext, key1);

        assertThrows(RuntimeException.class, () -> {
            encryptionService.decryptAesGcm(encrypted, key2);
        }, "使用错误密钥解密应抛出异常");
    }

    @Test
    void testAesGcm_TamperedCiphertextFails() {
        byte[] plaintext = "Important data".getBytes(StandardCharsets.UTF_8);
        SecretKey key = encryptionService.generateAesKey();

        byte[] encrypted = encryptionService.encryptAesGcm(plaintext, key);
        // 篡改密文
        encrypted[encrypted.length - 1] ^= 0x01;

        assertThrows(RuntimeException.class, () -> {
            encryptionService.decryptAesGcm(encrypted, key);
        }, "篡改后的密文解密应抛出异常");
    }

    @Test
    void testAesGcm_UniqueIVPerEncryption() {
        byte[] plaintext = "Same message".getBytes(StandardCharsets.UTF_8);
        SecretKey key = encryptionService.generateAesKey();

        byte[] encrypted1 = encryptionService.encryptAesGcm(plaintext, key);
        byte[] encrypted2 = encryptionService.encryptAesGcm(plaintext, key);

        // 每次加密应产生不同的IV，因此密文不同
        assertNotEquals(
                Base64.getEncoder().encodeToString(encrypted1),
                Base64.getEncoder().encodeToString(encrypted2),
                "每次加密应产生不同的密文（不同的IV）"
        );
    }

    // ==================== 字符串加密/解密 ====================

    @Test
    void testEncryptDecryptString_RoundTrip() {
        String plaintext = "敏感信息测试字符串";
        SecretKey key = encryptionService.generateAesKey();

        String encrypted = encryptionService.encryptString(plaintext, key);
        String decrypted = encryptionService.decryptString(encrypted, key);

        assertEquals(plaintext, decrypted, "字符串加密解密后应一致");
    }

    @Test
    void testEncryptString_OutputIsBase64() {
        String plaintext = "test";
        SecretKey key = encryptionService.generateAesKey();

        String encrypted = encryptionService.encryptString(plaintext, key);

        // 输出应为Base64格式
        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted));
    }

    // ==================== RSA 加密/解密 ====================

    @Test
    void testRsaEncryptDecrypt_RoundTrip() {
        byte[] data = "RSA test data".getBytes(StandardCharsets.UTF_8);
        KeyPair keyPair = encryptionService.generateRsaKeyPair();

        byte[] encrypted = encryptionService.encryptRsa(data, keyPair.getPublic());
        byte[] decrypted = encryptionService.decryptRsa(encrypted, keyPair.getPrivate());

        assertArrayEquals(data, decrypted, "RSA加密解密后数据应一致");
    }

    @Test
    void testRsa_WrongKeyFails() {
        byte[] data = "Secret".getBytes(StandardCharsets.UTF_8);
        KeyPair keyPair1 = encryptionService.generateRsaKeyPair();
        KeyPair keyPair2 = encryptionService.generateRsaKeyPair();

        byte[] encrypted = encryptionService.encryptRsa(data, keyPair1.getPublic());

        assertThrows(RuntimeException.class, () -> {
            encryptionService.decryptRsa(encrypted, keyPair2.getPrivate());
        }, "使用错误私钥解密应抛出异常");
    }

    // ==================== AES密钥的RSA包装/解包 ====================

    @Test
    void testWrapUnwrapAesKeyWithRsa_RoundTrip() {
        SecretKey aesKey = encryptionService.generateAesKey();
        KeyPair rsaKeyPair = encryptionService.generateRsaKeyPair();

        String wrappedKey = encryptionService.wrapAesKeyWithRsa(aesKey, rsaKeyPair.getPublic());
        SecretKey unwrappedKey = encryptionService.unwrapAesKeyWithRsa(wrappedKey, rsaKeyPair.getPrivate());

        assertArrayEquals(aesKey.getEncoded(), unwrappedKey.getEncoded(), "RSA包装解包后的AES密钥应一致");
    }

    // ==================== 密钥序列化/反序列化 ====================

    @Test
    void testEncodeDecodeAesKey_RoundTrip() {
        SecretKey originalKey = encryptionService.generateAesKey();

        String encoded = encryptionService.encodeKey(originalKey);
        SecretKey decodedKey = encryptionService.decodeAesKey(encoded);

        assertArrayEquals(originalKey.getEncoded(), decodedKey.getEncoded(), "编码解码后的AES密钥应一致");
    }

    @Test
    void testEncodeDecodePublicKey_RoundTrip() {
        KeyPair keyPair = encryptionService.generateRsaKeyPair();
        PublicKey originalKey = keyPair.getPublic();

        String encoded = encryptionService.encodePublicKey(originalKey);
        PublicKey decodedKey = encryptionService.decodePublicKey(encoded);

        assertEquals(originalKey.getAlgorithm(), decodedKey.getAlgorithm(), "编码解码后的公钥算法应一致");
        assertArrayEquals(originalKey.getEncoded(), decodedKey.getEncoded(), "编码解码后的公钥字节应一致");
    }

    @Test
    void testEncodeDecodePrivateKey_RoundTrip() {
        KeyPair keyPair = encryptionService.generateRsaKeyPair();
        PrivateKey originalKey = keyPair.getPrivate();

        String encoded = encryptionService.encodePrivateKey(originalKey);
        PrivateKey decodedKey = encryptionService.decodePrivateKey(encoded);

        assertEquals(originalKey.getAlgorithm(), decodedKey.getAlgorithm(), "编码解码后的私钥算法应一致");
        assertArrayEquals(originalKey.getEncoded(), decodedKey.getEncoded(), "编码解码后的私钥字节应一致");
    }

    // ==================== 主密钥 ====================

    @Test
    void testGetMasterKey_DevMode() {
        // 不设置masterKeyBase64，使用开发模式派生密钥
        SecretKey masterKey = encryptionService.getMasterKey();

        assertNotNull(masterKey, "主密钥不应为空");
        assertEquals("AES", masterKey.getAlgorithm(), "主密钥算法应为AES");
        assertEquals(32, masterKey.getEncoded().length, "主密钥长度应为256位(32字节)");
    }

    @Test
    void testGetMasterKey_DevModeIsDeterministic() {
        SecretKey key1 = encryptionService.getMasterKey();
        SecretKey key2 = encryptionService.getMasterKey();

        assertArrayEquals(key1.getEncoded(), key2.getEncoded(), "开发模式下多次获取的主密钥应一致");
    }

    @Test
    void testGetMasterKey_ConfiguredKey() {
        // 通过反射设置配置的主密钥
        SecretKey configKey = encryptionService.generateAesKey();
        String encodedKey = Base64.getEncoder().encodeToString(configKey.getEncoded());
        ReflectionTestUtils.setField(encryptionService, "masterKeyBase64", encodedKey);

        SecretKey masterKey = encryptionService.getMasterKey();

        assertArrayEquals(configKey.getEncoded(), masterKey.getEncoded(), "配置的主密钥应与设置的一致");
    }

    @Test
    void testGetMasterKey_InvalidBase64() {
        ReflectionTestUtils.setField(encryptionService, "masterKeyBase64", "invalid-base64!!!");
        assertThrows(Exception.class, () -> {
            encryptionService.getMasterKey();
        }, "无效的Base64编码应抛出异常");
    }

    // ==================== 密钥生成 ====================

    @Test
    void testGenerateAesKey_ValidLength() {
        SecretKey key = encryptionService.generateAesKey();

        assertNotNull(key, "生成的AES密钥不应为空");
        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length, "AES密钥长度应为256位");
    }

    @Test
    void testGenerateRsaKeyPair_ValidLength() {
        KeyPair keyPair = encryptionService.generateRsaKeyPair();

        assertNotNull(keyPair.getPublic(), "RSA公钥不应为空");
        assertNotNull(keyPair.getPrivate(), "RSA私钥不应为空");
        assertEquals("RSA", keyPair.getPublic().getAlgorithm());
        assertEquals("RSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    void testGenerateAesKey_UniqueKeys() {
        SecretKey key1 = encryptionService.generateAesKey();
        SecretKey key2 = encryptionService.generateAesKey();

        assertNotEquals(
                Base64.getEncoder().encodeToString(key1.getEncoded()),
                Base64.getEncoder().encodeToString(key2.getEncoded()),
                "每次生成的AES密钥应不同"
        );
    }
}
