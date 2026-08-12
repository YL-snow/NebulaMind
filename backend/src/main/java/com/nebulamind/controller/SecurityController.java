package com.nebulamind.controller;

import com.nebulamind.ai.AiSensitiveResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.api.client.maas.MaasApiClient;
import com.nebulamind.entity.AuditLog;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.AuditLogService;
import com.nebulamind.service.EncryptionService;
import com.nebulamind.service.FileService;
import com.nebulamind.service.KeyManagementService;
import com.nebulamind.service.StorageService;
import com.nebulamind.util.FileTypeDetector;
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
import java.util.Base64;

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

    private final StorageService storageService;

    @Autowired(required = false)
    private AiServiceClient aiServiceClient;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Autowired(required = false)
    private MaasApiClient maasApiClient;

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
            String fileType = FileTypeDetector.normalize(file.getFileType());
            if (File.EncryptionMode.CLIENT.equals(file.getEncryptionMode())) {
                Map<String, Object> e2eeResponse = new HashMap<>();
                e2eeResponse.put("fileId", fileId);
                e2eeResponse.put("sensitiveLevel", "normal");
                e2eeResponse.put("sensitiveItems", List.of());
                e2eeResponse.put("scannedAt", LocalDateTime.now().toString());
                e2eeResponse.put("detectionMethod", "e2ee_skipped");
                e2eeResponse.put("autoEncrypted", false);
                e2eeResponse.put("message", "该文件已开启端到端加密，服务器无法读取内容进行敏感检测，请在本地解密后检测。");
                e2eeResponse.put("warning", "该文件已开启端到端加密，服务器无法读取内容进行敏感检测，请在本地解密后检测。");
                return ResponseEntity.ok(e2eeResponse);
            }
            byte[] plainBytes = readPlainFileBytes(file, userId);
            String content = isBinaryFileType(fileType) ? "" : new String(plainBytes);

            // 第1层：文本类文件使用本地正则检测
            List<Map<String, Object>> items = new ArrayList<>();
            if (!content.isBlank()) {
                items.addAll(detectWithRegex(content, "id_card", "身份证号",
                        Pattern.compile("\\b\\d{17}[\\dXx]\\b"), "high"));
                items.addAll(detectWithRegex(content, "phone", "手机号",
                        Pattern.compile("\\b1[3-9]\\d{9}\\b"), "medium"));
                items.addAll(detectBankCards(content));
                items.addAll(detectWithRegex(content, "email", "邮箱",
                        Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+"), "low"));
            }

            // 第2层：AI 检测，二进制文件由 AI 服务解析内容
            String aiSensitiveLevel = null;
            boolean aiUsed = false;
            String base64 = Base64.getEncoder().encodeToString(plainBytes);
            String aiWarning = null;
            if (useLlm && aiServiceClient != null && !base64.isEmpty()) {
                try {
                    AiSensitiveResponse aiResponse = aiServiceClient.detectSensitive(
                            fileId, content, true, file.getPath(), base64, fileType);
                    aiUsed = true;
                    if (aiResponse != null && aiResponse.getWarning() != null
                            && !aiResponse.getWarning().isBlank()) {
                        aiWarning = aiResponse.getWarning();
                    }
                    if (aiResponse != null && aiResponse.getMatches() != null) {
                        for (AiSensitiveResponse.SensitiveMatchItem m : aiResponse.getMatches()) {
                            if (m.getType() == null || m.getContent() == null) {
                                continue;
                            }
                            Map<String, Object> item = new HashMap<>();
                            item.put("type", m.getType());
                            item.put("typeName", m.getType());
                            item.put("content", m.getContent());
                            item.put("position", m.getPosition());
                            item.put("confidence", m.getConfidence());
                            String risk = "medium";
                            if (m.getType().contains("id_card") || m.getType().contains("bank_card")
                                    || m.getType().contains("company_secret")) {
                                risk = "high";
                            } else if (m.getType().contains("phone") || m.getType().contains("address")) {
                                risk = "medium";
                            }
                            item.put("riskLevel", risk);
                            item.put("source", "llm");
                            boolean duplicate = items.stream().anyMatch(
                                    exist -> m.getType().equals(exist.get("type"))
                                            && m.getContent().equals(exist.get("content")));
                            if (!duplicate) {
                                items.add(item);
                            }
                        }
                    }
                    aiSensitiveLevel = aiResponse != null ? aiResponse.getSensitiveLevel() : null;
                } catch (Exception e) {
                    log.warn("AI sensitive detection failed, falling back to regex only: {}", e.getMessage());
                    aiWarning = "AI 检测服务暂时不可用，本次仅完成本地正则检测，请稍后重试。";
                }
            }

            // 风险等级综合评估：AI 结果优先，失败时使用本地正则结果
            // 第2.5层：图片 OCR 未识别出内容时，用视觉模型提取图片文字后再检测
            boolean visionUsed = false;
            if (items.isEmpty() && isImageFileType(fileType) && maasApiClient != null && !base64.isEmpty()) {
                String mimeType = IMAGE_MIME_TYPES.get(fileType);
                try {
                    String visionText = extractTextWithVision(plainBytes, mimeType);
                    if (visionText != null && !visionText.isBlank()) {
                        visionUsed = true;
                        List<Map<String, Object>> visionItems = new ArrayList<>();
                        visionItems.addAll(detectWithRegex(visionText, "id_card", "身份证号",
                                Pattern.compile("\\b\\d{17}[\\dXx]\\b"), "high"));
                        visionItems.addAll(detectWithRegex(visionText, "phone", "手机号",
                                Pattern.compile("\\b1[3-9]\\d{9}\\b"), "medium"));
                        visionItems.addAll(detectBankCards(visionText));
                        visionItems.addAll(detectWithRegex(visionText, "email", "邮箱",
                                Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+"), "low"));
                        for (Map<String, Object> item : visionItems) {
                            item.put("source", "vision");
                        }
                        items.addAll(visionItems);
                    }
                } catch (Exception e) {
                    log.warn("Vision OCR fallback failed for file {}: {}", fileId, e.getMessage());
                }
            }

            String detectionMethod = aiUsed && visionUsed ? "regex+ai+vision"
                    : aiUsed ? "regex+ai"
                    : visionUsed ? "regex+vision"
                    : items.isEmpty() ? "none" : "regex";

            String sensitiveLevel = "normal";
            if (aiSensitiveLevel != null && !"normal".equals(aiSensitiveLevel)) {
                sensitiveLevel = aiSensitiveLevel;
            } else {
                if (items.stream().anyMatch(i -> "high".equals(i.get("riskLevel")))) {
                    sensitiveLevel = "high";
                } else if (items.stream().anyMatch(i -> "medium".equals(i.get("riskLevel")))) {
                    sensitiveLevel = "medium";
                } else if (!items.isEmpty()) {
                    sensitiveLevel = "low";
                }
            }

            // 更新文件敏感级别
            file.setSensitiveLevel(File.SensitiveLevel.valueOf(sensitiveLevel.toUpperCase()));
            fileService.saveFile(file);

            if (auditLogService != null) {
                try {
                    auditLogService.log(userId, AuditLog.Action.CLASSIFY,
                        AuditLog.ResourceType.FILE, fileId,
                        String.format("{\"sensitiveLevel\":\"%s\",\"items\":%d,\"detection\":\"%s\"}",
                                sensitiveLevel, items.size(), detectionMethod),
                        httpRequest);
                } catch (Exception e) {
                    log.warn("Failed to write audit log for sensitive detection: {}", e.getMessage());
                }
            }

            // 高风险自动加密，开关由前端安全设置传入
            boolean autoEncrypt = Boolean.parseBoolean(request.getOrDefault("autoEncrypt", "true"));
            boolean autoEncrypted = false;
            if ("high".equals(sensitiveLevel) && autoEncrypt && !Boolean.TRUE.equals(file.getIsEncrypted())) {
                try {
                    Map<String, Object> keyInfo = keyManagementService.setupFileEncryption(userId);
                    SecretKey fileKey = (SecretKey) keyInfo.get("fileKey");
                    String encryptedFileKey = (String) keyInfo.get("encryptedFileKey");
                    byte[] encryptedContent = encryptionService.encryptAesGcm(plainBytes, fileKey);
                    storageService.uploadFile(file.getPath(), encryptedContent, "application/octet-stream");
                    file.setEncryptionMode(File.EncryptionMode.SERVER);
                    file.setIsEncrypted(true);
                    file.setEncryptionKeyId(encryptedFileKey);
                    fileService.saveFile(file);
                    autoEncrypted = true;
                    log.info("Auto-encrypted HIGH sensitive file: {} (user: {})", fileId, userId);
                    if (auditLogService != null) {
                        try {
                            auditLogService.log(userId, AuditLog.Action.ENCRYPT,
                                AuditLog.ResourceType.FILE, fileId,
                                "{\"reason\":\"auto_encrypt_high_sensitive\",\"algorithm\":\"AES-256-GCM\"}",
                                httpRequest);
                        } catch (Exception logEx) {
                            log.warn("Failed to write audit log for auto-encryption: {}", logEx.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.error("Auto-encryption failed for HIGH sensitive file {}: {}", fileId, e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("sensitiveLevel", sensitiveLevel);
            response.put("sensitiveItems", items);
            response.put("scannedAt", LocalDateTime.now().toString());
            response.put("detectionMethod", detectionMethod);
            response.put("autoEncrypted", autoEncrypted);
            if (aiWarning != null && !aiWarning.isBlank()) {
                response.put("warning", aiWarning);
            }
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
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        UUID userId = getUserIdFromAuthentication(authentication);
        String fileId = request.get("fileId");

        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileId is required"));
        }

        try {
            File file = fileService.getFileById(UUID.fromString(fileId), userId);
            if (Boolean.TRUE.equals(file.getIsEncrypted())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "文件已经加密，不能重复加密"));
            }
            boolean clientEncrypted = Boolean.parseBoolean(request.getOrDefault("clientEncrypted", "false"));
            if (clientEncrypted) {
                file.setIsEncrypted(true);
                file.setEncryptionMode(File.EncryptionMode.CLIENT);
                fileService.saveFile(file);
                if (auditLogService != null) {
                    try {
                        auditLogService.log(userId, AuditLog.Action.ENCRYPT,
                                AuditLog.ResourceType.FILE, fileId,
                                "{\"reason\":\"client_e2ee_encrypt\",\"algorithm\":\"AES-256-GCM\"}",
                                httpRequest);
                    } catch (Exception logEx) {
                        log.warn("Failed to write audit log for client encryption: {}", logEx.getMessage());
                    }
                }
                Map<String, Object> response = new HashMap<>();
                response.put("fileId", fileId);
                response.put("isEncrypted", true);
                response.put("encryptedAt", LocalDateTime.now().toString());
                response.put("algorithm", "AES-256-GCM");
                response.put("encryptionMode", "CLIENT");
                return ResponseEntity.ok(response);
            }
            byte[] plainContent = readPlainFileBytes(file, userId);

            // 建立文件密钥
            Map<String, Object> keyInfo = keyManagementService.setupFileEncryption(userId);
            SecretKey fileKey = (SecretKey) keyInfo.get("fileKey");
            String encryptedFileKey = (String) keyInfo.get("encryptedFileKey");

            // 加密内容并写回存储
            byte[] encryptedContent = encryptionService.encryptAesGcm(plainContent, fileKey);
            storageService.uploadFile(file.getPath(), encryptedContent, "application/octet-stream");
            file.setEncryptionMode(File.EncryptionMode.SERVER);

            // 更新文件元数据
            file.setIsEncrypted(true);
            file.setEncryptionKeyId(encryptedFileKey);
            fileService.saveFile(file);

            if (auditLogService != null) {
                try {
                    auditLogService.log(userId, AuditLog.Action.ENCRYPT,
                            AuditLog.ResourceType.FILE, fileId,
                            "{\"reason\":\"manual_encrypt\",\"algorithm\":\"AES-256-GCM\"}",
                            httpRequest);
                } catch (Exception logEx) {
                    log.warn("Failed to write audit log for encryption: {}", logEx.getMessage());
                }
            }

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
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        UUID userId = getUserIdFromAuthentication(authentication);
        String fileId = request.get("fileId");

        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileId is required"));
        }

        try {
            File file = fileService.getFileById(UUID.fromString(fileId), userId);
            if (!Boolean.TRUE.equals(file.getIsEncrypted())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "文件未加密，无需解密"));
            }
            boolean clientDecrypted = Boolean.parseBoolean(request.getOrDefault("clientDecrypted", "false"));
            if (clientDecrypted) {
                file.setIsEncrypted(false);
                file.setEncryptionMode(File.EncryptionMode.NONE);
                file.setEncryptionKeyId(null);
                fileService.saveFile(file);
                if (auditLogService != null) {
                    try {
                        auditLogService.log(userId, AuditLog.Action.DECRYPT,
                                AuditLog.ResourceType.FILE, fileId,
                                "{\"reason\":\"client_e2ee_decrypt\",\"algorithm\":\"AES-256-GCM\"}",
                                httpRequest);
                    } catch (Exception logEx) {
                        log.warn("Failed to write audit log for client decryption: {}", logEx.getMessage());
                    }
                }
                Map<String, Object> response = new HashMap<>();
                response.put("fileId", fileId);
                response.put("isEncrypted", false);
                response.put("decryptedAt", LocalDateTime.now().toString());
                response.put("encryptionMode", "NONE");
                return ResponseEntity.ok(response);
            }
            if (file.getEncryptionKeyId() == null || file.getEncryptionKeyId().isBlank()) {
                throw new RuntimeException("加密密钥缺失，无法解密");
            }

            // 取出文件密钥并解密内容
            SecretKey fileKey = keyManagementService.unwrapFileKey(file.getEncryptionKeyId(), userId);
            byte[] plainContent = encryptionService.decryptAesGcm(readFileBytes(file.getPath()), fileKey);

            // 明文写回存储
            storageService.uploadFile(file.getPath(), plainContent, file.getMimeType());
            file.setIsEncrypted(false);
            file.setEncryptionKeyId(null);
            file.setEncryptionMode(File.EncryptionMode.NONE);
            fileService.saveFile(file);

            if (auditLogService != null) {
                try {
                    auditLogService.log(userId, AuditLog.Action.DECRYPT,
                            AuditLog.ResourceType.FILE, fileId,
                            "{\"algorithm\":\"AES-256-GCM\"}",
                            httpRequest);
                } catch (Exception logEx) {
                    log.warn("Failed to write audit log for decryption: {}", logEx.getMessage());
                }
            }

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
    private static final Map<String, String> IMAGE_MIME_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "png", "image/png", "gif", "image/gif",
            "bmp", "image/bmp", "tiff", "image/tiff",
            "tif", "image/tiff", "webp", "image/webp");

    private boolean isImageFileType(String fileType) {
        return fileType != null && IMAGE_MIME_TYPES.containsKey(fileType.toLowerCase());
    }

    private String extractTextWithVision(byte[] imageBytes, String mimeType) {
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
        MaasApiClient.VisionResult result = maasApiClient.chatVision(
                "请逐字提取图片中的全部文字，只输出提取到的文字内容，不要解释或补充。",
                imageBase64, mimeType, 0.1, 1500);
        if (result == null || !result.isSuccess() || result.getResponse() == null
                || result.getResponse().getChoices() == null || result.getResponse().getChoices().isEmpty()
                || result.getResponse().getChoices().get(0).getMessage() == null) {
            if (result != null && result.getErrorMessage() != null) {
                log.warn("Vision OCR fallback returned failure: {}", result.getErrorMessage());
            }
            return null;
        }
        String text = result.getResponse().getChoices().get(0).getMessage().getContent();
        return text == null ? null : text.trim();
    }

    private List<Map<String, Object>> detectBankCards(String content) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (content == null) {
            return results;
        }
        Matcher matcher = Pattern.compile("\\b\\d{16,19}\\b").matcher(content);
        int count = 0;
        while (matcher.find() && count < 5) {
            String matched = matcher.group();
            // 银行卡号使用 Luhn 校验，避免把 18 位身份证号误判为银行卡
            if (!isLuhnValid(matched)) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("type", "bank_card");
            item.put("typeName", "银行卡号");
            item.put("content", maskContent(matched));
            item.put("position", matcher.start());
            item.put("riskLevel", "high");
            results.add(item);
            count++;
        }
        return results;
    }

    private boolean isLuhnValid(String digits) {
        if (digits == null || digits.length() < 16 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                return false;
            }
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

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

    private byte[] readFileBytes(String path) throws Exception {
        try (InputStream inputStream = storageService.downloadFile(path)) {
            return inputStream.readAllBytes();
        }
    }

    private byte[] readPlainFileBytes(File file, UUID userId) throws Exception {
        byte[] bytes = readFileBytes(file.getPath());
        if (!Boolean.TRUE.equals(file.getIsEncrypted())) {
            return bytes;
        }
        if (file.getEncryptionKeyId() == null || file.getEncryptionKeyId().isBlank()) {
            throw new RuntimeException("加密密钥缺失，无法读取明文");
        }
        SecretKey fileKey = keyManagementService.unwrapFileKey(file.getEncryptionKeyId(), userId);
        return encryptionService.decryptAesGcm(bytes, fileKey);
    }

    private boolean isBinaryFileType(String fileType) {
        return Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp").contains(fileType);
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
