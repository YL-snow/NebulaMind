package com.nebulamind.controller;

import com.nebulamind.cloud.CloudStorageDriveService;
import com.nebulamind.dto.CloudStorageItem;
import com.nebulamind.entity.CloudStorageConfig;
import com.nebulamind.repository.CloudStorageConfigRepository;
import com.nebulamind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage-config/{configId}/drive")
@RequiredArgsConstructor
public class CloudStorageDriveController {

    private final CloudStorageConfigRepository configRepository;
    private final UserRepository userRepository;
    private final CloudStorageDriveService driveService;

    @GetMapping("/files")
    public ResponseEntity<List<CloudStorageItem>> listFiles(
            Authentication auth,
            @PathVariable UUID configId,
            @RequestParam(required = false) String path) throws Exception {
        UUID userId = getUserId(auth);
        CloudStorageConfig config = getOwnedConfig(configId, userId);
        return ResponseEntity.ok(driveService.listFiles(config, path));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(
            Authentication auth,
            @PathVariable UUID configId,
            @RequestParam String path) throws Exception {
        UUID userId = getUserId(auth);
        CloudStorageConfig config = getOwnedConfig(configId, userId);
        byte[] content = driveService.downloadFile(config, path);

        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(content);
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(
            Authentication auth,
            @PathVariable UUID configId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "name", required = false) String name) throws Exception {
        UUID userId = getUserId(auth);
        CloudStorageConfig config = getOwnedConfig(configId, userId);
        String fileName = (name != null && !name.isBlank()) ? name : file.getOriginalFilename();
        driveService.uploadFile(config, path, fileName,
                file.getBytes(), file.getContentType());
        return ResponseEntity.ok(Map.of("message", "上传成功"));
    }

    @DeleteMapping("/files")
    public ResponseEntity<Map<String, String>> deleteFile(
            Authentication auth,
            @PathVariable UUID configId,
            @RequestParam String path) throws Exception {
        UUID userId = getUserId(auth);
        CloudStorageConfig config = getOwnedConfig(configId, userId);
        driveService.deleteFile(config, path);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @PostMapping("/files/import")
    public ResponseEntity<Map<String, Object>> importFile(
            Authentication auth,
            @PathVariable UUID configId,
            @RequestParam String path,
            @RequestParam(value = "name", required = false) String name) throws Exception {
        UUID userId = getUserId(auth);
        CloudStorageConfig config = getOwnedConfig(configId, userId);
        return ResponseEntity.ok(driveService.importFile(config, userId, path, name));
    }

    private CloudStorageConfig getOwnedConfig(UUID configId, UUID userId) {
        CloudStorageConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "云存储配置不存在"));
        if (!config.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该云存储配置");
        }
        return config;
    }

    private UUID getUserId(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(com.nebulamind.entity.User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }
}
