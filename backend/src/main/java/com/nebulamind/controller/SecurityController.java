package com.nebulamind.controller;

import com.nebulamind.ai.AiSensitiveResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.entity.AuditLog;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.AuditLogService;
import com.nebulamind.service.EncryptionService;
import com.nebulamind.service.FileService;
import com.nebulamind.service.KeyManagementService;
import com.nebulamind.service.LocalStorageService;
import com.nebulamind.service.MinIOService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 安全检测与加密 Controller
 *
 * 端点：
 *   - POST /api/v1/security/detect    敏感信息检测（前端 /security/detect 通过 vite proxy 转发）
 *   - POST /api/v1/security/encrypt   文件加密
 *   - POST /api/v1/security/decrypt   文件解密
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class SecurityController {

    private final FileService fileService;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final KeyManagementService keyManagementService;

    @Autowired(required = false)
    private MinIOService minIOService;

    @Autowired(required = false)
    private LocalStorageService localStorageService;

    @Autowired(required = false)
    private AiServiceClient aiServiceClient;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    /**
     * 敏感信息检测（两级检测：本地正则 + AI LLM 增强）
     * 高风险文件自动加密
     * POST /api/v1/security/detect
     * Body: { "fileId": "...", "useLlm": true }
     */
    @PostMapping("/detect")
    public ResponseEntity<Map<String, Object>> detectSensitive(
            Authentication authentication,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        UUID userId = getUserIdFromAuthentication(authentication);
        String fileId = request.get("fileId");
        boolean useLlm = Boolean.parseBoolean(request.getOrDefault("useLlm", "true"));

        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileId is required"));
        }

        try {
            File file = fileService.getFileById(UUID.fromString(fileId), userId);
            String content = readFileContent(file.getPath());

            // ======== 第1层：本地正则检测 ========
            List<Map<String, Object>> items = new ArrayList<>();
            items.addAll(detectWithRegex(content, "id_card", "身份证号",
                    Pattern.compile("\\b\\d{17}[\\dXx]\\b"), "high"));
            items.addAll(detectWithRegex(content, "phone", "手机号",
                    Pattern.compile("\\b1[3-9]\\d{9}\\b"), "medium"));
            items.addAll(detectWithRegex(content, "bank_card", "银行卡号",
                    Pattern.compile("\\b\\d{16,19}\\b"), "high"));
            items.addAll(detectWithRegex(content, "email", "邮箱",
                    Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+"), "low"));

            // ======== 第2层：AI LLM 敏感检测 ========
            String llmSensitiveLevel = null;
            if (useLlm && aiServiceClient != null && content != null && !content.isBlank()) {
                try {
                    AiSensitiveResponse aiResponse = aiServiceClient.detectSensitive(fileId, content, true, file.getPath());
                    if (aiResponse != null && aiResponse.getMatches() != null) {
                        for (AiSensitiveResponse.SensitiveMatchItem m : aiResponse.getMatches()) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("type", m.getType());
                            item.put("typeName", m.getType());
                            item.put("content", m.getContent());
                            item.put("position", m.getPosition());
                            item.put("confidence", m.getConfidence());
                            // LLM 检测到的映射到风险等级
                            String risk = "medium";
                            if (m.getType() != null) {
                                if (m.getType().contains("id_card") || m.getType().contains("bank_card")
                                        || m.getType().contains("company_secret")) {
                                    risk = "high";
                                } else if (m.getType().contains("phone") || m.getType().contains("address")) {
                                    risk = "medium";
                                }
                            }
                            item.put("riskLevel", risk);
                            item.put("source", "llm");
                            // 去重：如果正则已匹配同类型的同一内容，跳过
                            boolean duplicate = items.stream().anyMatch(
                                    exist -> exist.get("type").equals(item.get("type"))
                                            && exist.get("content").equals(item.get("content")));
                            if (!duplicate) {
                                items.add(item);
                            }
                        }
                        llmSensitiveLevel = aiResponse.getSensitiveLevel();
                    }
                } catch (Exception e) {
                    log.warn("AI LLM sensitive detection failed, falling back to regex only: {}", e.getMessage());
                }
            }

            // ======== 风险等级综合评估 ========
            String sensitiveLevel = "normal";
            // AI检测结果优先（更全面）
            if (llmSensitiveLevel != null && !"normal".equals(llmSensitiveLevel)) {
                sensitiveLevel = llmSensitiveLevel;
            } else {
                if (items.stream().anyMatch(i -> "high".equals(i.get("riskLevel")))) {
                    sensitiveLevel = "high";
                } else if (items.stream().anyMatch(i -> "medium".equals(i.get("riskLevel")))) {
                    sensitiveLevel = "medium";
                } else if (!items.isEmpty()) {
                    sensitiveLevel = "low";
                }
            }

            // ======== 更新文件敏感级别 ========
            file.setSensitiveLevel(File.SensitiveLevel.valueOf(sensitiveLevel.toUpperCase()));
            fileService.updateFile(file.getId(), null, userId);

            // ======== 记录审计日志 ========
            if (auditLogService != null) {
                auditLogService.log(userId, AuditLog.Action.CLASSIFY,
                        AuditLog.ResourceType.FILE, fileId,
                        String.format("{\"sensitiveLevel\":\"%s\",\"items\":%d,\"detection\":\"%s\"}",
                                sensitiveLevel, items.size(), llmSensitiveLevel != null ? "regex+llm" : "regex"),
                        httpRequest);
            }

            // ======== 高风险自动加密 ========
            boolean autoEncrypted = false;
            if ("high".equals(sensitiveLevel) && !Boolean.TRUE.equals(file.getIsEncrypted())) {
                try {
                    byte[] plainContent = readFileBytes(file.getPath());
                    Map<String, Object> keyInfo = keyManagementService.setupFileEncryption(userId);
                    SecretKey fileKey = (SecretKey) keyInfo.get("fileKey");
                    String encryptedFileKey = (String) keyInfo.get("encryptedFileKey");
                    byte[] encryptedContent = encryptionService.encryptAesGcm(plainContent, fileKey);

                    String objectName = file.getPath();
                    if (minIOService != null) {
                        minIOService.uploadFile(objectName, encryptedContent, "application/octet-stream");
                    } else if (localStorageService != null) {
                        localStorageService.uploadFile(objectName, encryptedContent, "application/octet-stream");
                    }

                    file.setIsEncrypted(true);
                    file.setEncryptionKeyId(encryptedFileKey);
                    fileService.updateFile(file.getId(), null, userId);
                    autoEncrypted = true;

                    log.info("Auto-encrypted HIGH sensitive file: {} (user: {})", fileId, userId);
                    if (auditLogService != null) {
                        auditLogService.log(userId, AuditLog.Action.ENCRYPT,
                                AuditLog.ResourceType.FILE, fileId,
                                "{\"reason\":\"auto_encrypt_high_sensitive\",\"algorithm\":\"AES-256-GCM\"}",
                                httpRequest);
                    }
                } catch (Exception e) {
                    log.error("Auto-encryption failed for HIGH sensitive file {}: {}", fileId, e.getMessage());
                }
            }

            // ======== 构建响应 ========
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("sensitiveLevel", sensitiveLevel);
            response.put("sensitiveItems", items);
            response.put("scannedAt", LocalDateTime.now().toString());
            response.put("detectionMethod", llmSensitiveLevel != null ? "regex+llm" : "regex");
            response.put("autoEncrypted", autoEncrypted);
            if (autoEncrypted) {
                response.put("encryptionAlgorithm", "AES-256-GCM");
                response.put("message", "文件包含高风险敏感信息，已自动加密存储");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Sensitive detection failed for file {}", fileId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Sensitive detection failed: " + e.getMessage()));
        }
    }

    /**
     * 文件加密
     * POST /api/v1/security/encrypt
     * Body: { "fileId": "...", "reason": "..." }
     */
    @PostMapping("/encrypt")
    public ResponseEntity<Map<String, Object>> encryptFile(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        UUID userId = getUserIdFromAuthentication(authentication);
        String fileId = request.get("fileId");

        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileId is required"));
        }

        try {
            File file = fileService.getFileById(UUID.fromString(fileId), userId);
            byte[] plainContent = readFileBytes(file.getPath());

            // 建立文件密钥
            Map<String, Object> keyInfo = keyManagementService.setupFileEncryption(userId);
            SecretKey fileKey = (SecretKey) keyInfo.get("fileKey");
            String encryptedFileKey = (String) keyInfo.get("encryptedFileKey");

            // 加密内容
            byte[] encryptedContent = encryptionService.encryptAesGcm(plainContent, fileKey);

            // 写回存储
            String objectName = file.getPath();
            if (minIOService != null) {
                minIOService.uploadFile(objectName, encryptedContent, "application/octet-stream");
            } else if (localStorageService != null) {
                localStorageService.uploadFile(objectName, encryptedContent, "application/octet-stream");
            } else {
                throw new RuntimeException("No storage service available");
            }

            // 更新文件元数据
            file.setIsEncrypted(true);
            file.setEncryptionKeyId(encryptedFileKey);
            fileService.updateFile(file.getId(), null, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("isEncrypted", true);
            response.put("encryptedAt", LocalDateTime.now().toString());
            response.put("algorithm", "AES-256-GCM");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("File encryption failed for file {}", fileId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "File encryption failed: " + e.getMessage()));
        }
    }

    /**
     * 文件解密
     * POST /api/v1/security/decrypt
     * Body: { "fileId": "..." }
     */
    @PostMapping("/decrypt")
    public ResponseEntity<Map<String, Object>> decryptFile(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        UUID userId = getUserIdFromAuthentication(authentication);
        String fileId = request.get("fileId");

        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileId is required"));
        }

        try {
            File file = fileService.getFileById(UUID.fromString(fileId), userId);
            byte[] encryptedContent = readFileBytes(file.getPath());

            // 取出文件密钥
            if (file.getEncryptionKeyId() == null || file.getEncryptionKeyId().isBlank()) {
                throw new RuntimeException("File is not encrypted or encryption key is missing");
            }
            SecretKey fileKey = keyManagementService.unwrapFileKey(file.getEncryptionKeyId(), userId);

            // 解密内容
            byte[] plainContent = encryptionService.decryptAesGcm(encryptedContent, fileKey);

            // 写回存储
            String objectName = file.getPath();
            if (minIOService != null) {
                minIOService.uploadFile(objectName, plainContent, file.getMimeType());
            } else if (localStorageService != null) {
                localStorageService.uploadFile(objectName, plainContent, file.getMimeType());
            } else {
                throw new RuntimeException("No storage service available");
            }

            file.setIsEncrypted(false);
            file.setEncryptionKeyId(null);
            fileService.updateFile(file.getId(), null, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("isEncrypted", false);
            response.put("decryptedAt", LocalDateTime.now().toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("File decryption failed for file {}", fileId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "File decryption failed: " + e.getMessage()));
        }
    }

    // ============== 私有辅助方法 ==============

    private List<Map<String, Object>> detectWithRegex(
            String content, String type, String typeName, Pattern pattern, String defaultRisk) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (content == null) {
            return results;
        }
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find() && count < 5) {
            String matched = matcher.group();
            Map<String, Object> item = new HashMap<>();
            item.put("type", type);
            item.put("typeName", typeName);
            item.put("content", maskContent(matched));
            item.put("position", matcher.start());
            item.put("riskLevel", defaultRisk);
            results.add(item);
            count++;
        }
        return results;
    }

    private String maskContent(String content) {
        if (content == null || content.length() <= 6) {
            return "***";
        }
        int maskLen = Math.max(2, content.length() - 6);
        StringBuilder sb = new StringBuilder();
        sb.append(content, 0, 3);
        for (int i = 0; i < maskLen; i++) sb.append("*");
        sb.append(content, content.length() - 3, content.length());
        return sb.toString();
    }

    private String readFileContent(String path) throws Exception {
        return new String(readFileBytes(path));
    }

    private byte[] readFileBytes(String path) throws Exception {
        InputStream inputStream;
        if (minIOService != null) {
            inputStream = minIOService.downloadFile(path);
        } else if (localStorageService != null) {
            inputStream = localStorageService.downloadFile(path);
        } else {
            throw new RuntimeException("No storage service available");
        }
        try (inputStream) {
            return inputStream.readAllBytes();
        }
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
