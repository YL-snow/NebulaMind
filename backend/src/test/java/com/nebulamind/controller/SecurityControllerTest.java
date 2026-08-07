package com.nebulamind.controller;

import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityController.class)
class SecurityControllerTest {

    @TestConfiguration
    static class SecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileService fileService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private EncryptionService encryptionService;

    @MockBean
    private KeyManagementService keyManagementService;

    @MockBean
    private MinIOService minIOService;

    @MockBean
    private LocalStorageService localStorageService;

    @MockBean
    private AiServiceClient aiServiceClient;

    @MockBean
    private AuditLogService auditLogService;

    private User testUser;
    private File testFile;
    private SecretKey testFileKey;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("password")
                .build();

        testFile = File.builder()
                .id(UUID.randomUUID())
                .name("test.txt")
                .path("test/test.txt")
                .hash("test-hash")
                .size(100L)
                .mimeType("text/plain")
                .user(testUser)
                .sensitiveLevel(File.SensitiveLevel.NORMAL)
                .isEncrypted(false)
                .build();

        testFileKey = new SecretKeySpec(new byte[32], "AES");
    }

    // ===================== 敏感检测测试 =====================

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_NormalContent() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream("Hello, this is a normal document with no sensitive data.".getBytes()));

        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\",\"useLlm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(testFile.getId().toString()))
                .andExpect(jsonPath("$.sensitiveLevel").value("normal"))
                .andExpect(jsonPath("$.sensitiveItems").isArray())
                .andExpect(jsonPath("$.sensitiveItems.length()").value(0))
                .andExpect(jsonPath("$.detectionMethod").value("regex"))
                .andExpect(jsonPath("$.autoEncrypted").value(false));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_WithPhoneNumber() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream(
                "请联系电话 13800138000 进行咨询。".getBytes()));

        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\",\"useLlm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensitiveLevel").value("medium"))
                .andExpect(jsonPath("$.sensitiveItems.length()").value(1))
                .andExpect(jsonPath("$.sensitiveItems[0].type").value("phone"))
                .andExpect(jsonPath("$.sensitiveItems[0].riskLevel").value("medium"))
                .andExpect(jsonPath("$.detectionMethod").value("regex"))
                .andExpect(jsonPath("$.autoEncrypted").value(false));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_WithIdCard_HighRiskAutoEncrypt() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        // 使用含X的身份证号，避免同时被银行卡正则匹配
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream(
                "身份证号: 11010119900101123X".getBytes()));
        when(encryptionService.generateAesKey()).thenReturn(testFileKey);
        when(encryptionService.encryptAesGcm(any(byte[].class), any(SecretKey.class)))
                .thenReturn("encrypted-data".getBytes());
        when(keyManagementService.setupFileEncryption(any(UUID.class)))
                .thenReturn(Map.of("fileKey", testFileKey, "encryptedFileKey", Base64.getEncoder().encodeToString("mock-key".getBytes())));

        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\",\"useLlm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensitiveLevel").value("high"))
                .andExpect(jsonPath("$.sensitiveItems.length()").value(1))
                .andExpect(jsonPath("$.sensitiveItems[0].type").value("id_card"))
                .andExpect(jsonPath("$.sensitiveItems[0].riskLevel").value("high"))
                .andExpect(jsonPath("$.autoEncrypted").value(true))
                .andExpect(jsonPath("$.encryptionAlgorithm").value("AES-256-GCM"))
                .andExpect(jsonPath("$.message").value("文件包含高风险敏感信息，已自动加密存储"))
                .andExpect(jsonPath("$.detectionMethod").value("regex"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_WithBankCard() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream(
                "银行卡号: 6222021234567890123".getBytes()));

        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\",\"useLlm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensitiveLevel").value("high"))
                .andExpect(jsonPath("$.sensitiveItems.length()").value(1))
                .andExpect(jsonPath("$.sensitiveItems[0].type").value("bank_card"))
                .andExpect(jsonPath("$.sensitiveItems[0].riskLevel").value("high"))
                .andExpect(jsonPath("$.autoEncrypted").value(true));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_WithEmail() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream(
                "联系邮箱: test.user@example.com".getBytes()));

        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\",\"useLlm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensitiveLevel").value("low"))
                .andExpect(jsonPath("$.sensitiveItems.length()").value(1))
                .andExpect(jsonPath("$.sensitiveItems[0].type").value("email"))
                .andExpect(jsonPath("$.sensitiveItems[0].riskLevel").value("low"))
                .andExpect(jsonPath("$.autoEncrypted").value(false));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_MissingFileId() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"useLlm\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("fileId is required"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_FileNotFound() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class)))
                .thenThrow(new RuntimeException("File not found"));

        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + UUID.randomUUID() + "\",\"useLlm\":false}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Sensitive detection failed: File not found"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_MultipleSensitiveTypes() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream(
                ("用户信息：\n" +
                 "身份证: 11010119900101123X\n" +
                 "手机: 13800138000\n" +
                 "邮箱: user@test.com\n" +
                 "银行卡: 6222021234567890123").getBytes()));

        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\",\"useLlm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensitiveLevel").value("high"))
                .andExpect(jsonPath("$.sensitiveItems.length()").value(4))
                .andExpect(jsonPath("$.autoEncrypted").value(true));
    }

    // ===================== 文件加密测试 =====================

    @Test
    @WithMockUser(username = "test@example.com")
    void testEncryptFile_Success() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream("plain content".getBytes()));
        when(encryptionService.generateAesKey()).thenReturn(testFileKey);
        when(encryptionService.encryptAesGcm(any(byte[].class), any(SecretKey.class)))
                .thenReturn("encrypted".getBytes());
        when(keyManagementService.setupFileEncryption(any(UUID.class)))
                .thenReturn(Map.of("fileKey", testFileKey, "encryptedFileKey", "mock-encrypted-key"));

        mockMvc.perform(post("/api/v1/security/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\",\"reason\":\"manual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(testFile.getId().toString()))
                .andExpect(jsonPath("$.isEncrypted").value(true))
                .andExpect(jsonPath("$.algorithm").value("AES-256-GCM"))
                .andExpect(jsonPath("$.encryptedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testEncryptFile_MissingFileId() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        mockMvc.perform(post("/api/v1/security/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("fileId is required"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testEncryptFile_FileNotFound() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class)))
                .thenThrow(new RuntimeException("File not found"));

        mockMvc.perform(post("/api/v1/security/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("File encryption failed: File not found"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testEncryptFile_NoStorageService() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        // 不mock downloadFile，让它返回null模拟存储服务不可用

        mockMvc.perform(post("/api/v1/security/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\"}"))
                .andExpect(status().isInternalServerError());
    }

    // ===================== 文件解密测试 =====================

    @Test
    @WithMockUser(username = "test@example.com")
    void testDecryptFile_Success() throws Exception {
        File encryptedFile = File.builder()
                .id(testFile.getId())
                .name("encrypted.txt")
                .path("test/encrypted.txt")
                .hash("encrypted-hash")
                .size(200L)
                .mimeType("text/plain")
                .user(testUser)
                .sensitiveLevel(File.SensitiveLevel.HIGH)
                .isEncrypted(true)
                .encryptionKeyId(Base64.getEncoder().encodeToString("mock-key".getBytes()))
                .build();

        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(encryptedFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream("encrypted-bytes".getBytes()));
        when(keyManagementService.unwrapFileKey(anyString(), any(UUID.class))).thenReturn(testFileKey);
        when(encryptionService.decryptAesGcm(any(byte[].class), any(SecretKey.class)))
                .thenReturn("decrypted content".getBytes());

        mockMvc.perform(post("/api/v1/security/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + encryptedFile.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(encryptedFile.getId().toString()))
                .andExpect(jsonPath("$.isEncrypted").value(false))
                .andExpect(jsonPath("$.decryptedAt").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDecryptFile_MissingKeyId() throws Exception {
        File noKeyFile = File.builder()
                .id(testFile.getId())
                .name("no-key.txt")
                .path("test/no-key.txt")
                .hash("hash")
                .size(100L)
                .mimeType("text/plain")
                .user(testUser)
                .sensitiveLevel(File.SensitiveLevel.HIGH)
                .isEncrypted(true)
                .encryptionKeyId(null)
                .build();

        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(noKeyFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream("encrypted".getBytes()));

        mockMvc.perform(post("/api/v1/security/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + noKeyFile.getId() + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("File decryption failed: File is not encrypted or encryption key is missing"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testDecryptFile_MissingFileId() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        mockMvc.perform(post("/api/v1/security/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("fileId is required"));
    }

    // ===================== 敏感检测mask验证测试 =====================

    @Test
    @WithMockUser(username = "test@example.com")
    void testDetectSensitive_ContentMasking() throws Exception {
        when(userRepository.findByEmail(anyString())).thenReturn(java.util.Optional.of(testUser));
        when(fileService.getFileById(any(UUID.class), any(UUID.class))).thenReturn(testFile);
        when(minIOService.downloadFile(anyString())).thenReturn(new ByteArrayInputStream(
                "身份证: 110101199001011234".getBytes()));

        mockMvc.perform(post("/api/v1/security/detect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"" + testFile.getId() + "\",\"useLlm\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensitiveItems[0].content").value("110************234"));
    }
}
