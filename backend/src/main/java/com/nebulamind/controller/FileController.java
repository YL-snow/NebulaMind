package com.nebulamind.controller;

import com.nebulamind.dto.*;
import com.nebulamind.entity.File;
import com.nebulamind.entity.AuditLog;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private KeyManagementService keyManagementService;

    // Prod only (not available in dev profile)
    @Autowired(required = false)
    private CachedFileService cachedFileService;

    @Autowired(required = false)
    private FileUploadService fileUploadService;

    @Autowired(required = false)
    private MinIOService minIOService;

    @Autowired(required = false)
    private LocalStorageService localStorageService;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    public FileController(FileService fileService, UserRepository userRepository) {
        this.fileService = fileService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Page<File>> listFiles(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = getUserIdFromAuthentication(authentication);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (cachedFileService != null) {
            return ResponseEntity.ok(cachedFileService.getUserFiles(userId, pageable));
        }
        return ResponseEntity.ok(fileService.getUserFiles(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<File> getFile(Authentication authentication, @PathVariable UUID id) {
        UUID userId = getUserIdFromAuthentication(authentication);
        if (cachedFileService != null) {
            return ResponseEntity.ok(cachedFileService.getFileById(id, userId));
        }
        return ResponseEntity.ok(fileService.getFileById(id, userId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(Authentication authentication, @PathVariable UUID id,
            HttpServletRequest httpRequest) throws Exception {
        UUID userId = getUserIdFromAuthentication(authentication);
        InputStream inputStream;
        File file;
        if (cachedFileService != null) {
            inputStream = cachedFileService.downloadFile(id, userId);
            file = cachedFileService.getFileById(id, userId);
        } else {
            inputStream = fileService.downloadFile(id, userId);
            file = fileService.getFileById(id, userId);
        }

        byte[] content = inputStream.readAllBytes();

        // 记录下载审计（自动解密也算一次下载）
        if (auditLogService != null) {
            try {
                auditLogService.log(userId, AuditLog.Action.DOWNLOAD,
                        AuditLog.ResourceType.FILE, id.toString(),
                        String.format("{\"size\":%d,\"encrypted\":%b}", content.length,
                                Boolean.TRUE.equals(file.getIsEncrypted())),
                        httpRequest);
            } catch (Exception logEx) {
                log.warn("Failed to write audit log for download: {}", logEx.getMessage());
            }
        }

        // 如果文件已加密，自动解密
        if (Boolean.TRUE.equals(file.getIsEncrypted())
                && !File.EncryptionMode.CLIENT.equals(file.getEncryptionMode())
                && file.getEncryptionKeyId() != null) {
            try {
                SecretKey fileKey = keyManagementService.unwrapFileKey(file.getEncryptionKeyId(), userId);
                content = encryptionService.decryptAesGcm(content, fileKey);
                log.info("Decrypted file {} for download (user: {})", id, userId);
            } catch (Exception e) {
                log.error("Failed to decrypt file {} for download: {}", id, e.getMessage());
                return ResponseEntity.status(500).body(new InputStreamResource(
                        new ByteArrayInputStream(("文件解密失败: " + e.getMessage()).getBytes())));
            }
        }

        String encodedFileName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"; filename*=UTF-8''" + encodedFileName)
                .body(new InputStreamResource(new ByteArrayInputStream(content)));
    }

    @PostMapping("/upload/init")
    public ResponseEntity<Map<String, String>> initUpload(
            Authentication authentication,
            @Valid @RequestBody FileUploadRequest request) {
        if (fileUploadService == null) {
            throw new UnsupportedOperationException("Chunked upload is not available in dev mode. Use POST /api/v1/files to upload.");
        }
        UUID userId = getUserIdFromAuthentication(authentication);

        List<File> duplicates;
        if (cachedFileService != null) {
            duplicates = cachedFileService.findDuplicateFiles(userId, request.getFileHash());
        } else {
            duplicates = fileService.findDuplicateFiles(userId, request.getFileHash());
        }
        if (!duplicates.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Duplicate file detected");
            response.put("existingFileId", duplicates.get(0).getId().toString());
            return ResponseEntity.ok(response);
        }

        String uploadId = fileUploadService.initUpload(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize(),
                request.getFileHash(),
                userId);

        Map<String, String> response = new HashMap<>();
        response.put("uploadId", uploadId);
        response.put("totalChunks", String.valueOf((int) Math.ceil((double) request.getFileSize() / (5 * 1024 * 1024))));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload/chunk")
    public ResponseEntity<FileUploadResponse> uploadChunk(
            Authentication authentication,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) throws Exception {
        if (fileUploadService == null) {
            throw new UnsupportedOperationException("Chunked upload is not available in dev mode.");
        }
        UUID userId = getUserIdFromAuthentication(authentication);
        return ResponseEntity.ok(fileUploadService.uploadChunk(uploadId, chunkIndex, chunk));
    }

    @PostMapping("/upload/cancel")
    public ResponseEntity<Void> cancelUpload(@RequestParam("uploadId") String uploadId) {
        if (fileUploadService == null) {
            return ResponseEntity.ok().build();
        }
        fileUploadService.cancelUpload(uploadId);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<File> createFile(Authentication authentication, @Valid @RequestBody FileRequest request) {
        UUID userId = getUserIdFromAuthentication(authentication);
        if (cachedFileService != null) {
            return ResponseEntity.ok(cachedFileService.createFile(request, userId));
        }
        return ResponseEntity.ok(fileService.createFile(request, userId));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<File> uploadFile(Authentication authentication, @RequestParam("file") MultipartFile file,
                                                       @RequestParam(value = "encrypted", required = false, defaultValue = "false") boolean encrypted) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        try {
            FileRequest request = FileRequest.builder()
                    .name(file.getOriginalFilename())
                    .mimeType(file.getContentType())
                    .size(file.getSize())
                    .content(file.getBytes())
                    .build();
            
            File createdFile;
            if (cachedFileService != null) {
                createdFile = cachedFileService.createFile(request, userId, encrypted);
            } else {
                createdFile = fileService.createFile(request, userId, encrypted);
            }
            if (encrypted && createdFile != null) {
                createdFile.setEncryptionMode(File.EncryptionMode.CLIENT);
                createdFile.setIsEncrypted(true);
                createdFile.setAiStatus(File.AiStatus.COMPLETED);
                createdFile.setAiErrorMessage("end-to-end encrypted file, server cannot analyze");
                createdFile = fileService.saveFile(createdFile);
            }
            return ResponseEntity.ok(createdFile);
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            throw new RuntimeException("File upload failed", e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<File> updateFile(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody Map<String, String> updates) {
        UUID userId = getUserIdFromAuthentication(authentication);
        File file;
        if (cachedFileService != null) {
            file = cachedFileService.getFileById(id, userId);
        } else {
            file = fileService.getFileById(id, userId);
        }

        if (updates.containsKey("name")) {
            file.setName(updates.get("name"));
        }

        if (updates.containsKey("tags")) {
            file.setTags(updates.get("tags"));
        }

        File saved = fileService.saveFile(file);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(Authentication authentication, @PathVariable UUID id) {
        UUID userId = getUserIdFromAuthentication(authentication);
        if (cachedFileService != null) {
            cachedFileService.deleteFile(id, userId);
        } else {
            fileService.deleteFile(id, userId);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/process-callback")
    public ResponseEntity<File> processCallback(@PathVariable UUID id, @RequestBody FileProcessCallbackRequest request) {
        request.setFileId(id);
        if (cachedFileService != null) {
            return ResponseEntity.ok(cachedFileService.processCallback(request));
        }
        return ResponseEntity.ok(fileService.processCallback(request));
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
