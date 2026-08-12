package com.nebulamind.controller;

import com.nebulamind.cloud.CloudStorageDriveService;
import com.nebulamind.entity.CloudStorageConfig;
import com.nebulamind.entity.User;
import com.nebulamind.repository.CloudStorageConfigRepository;
import com.nebulamind.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 云存储配置管理。用户可在此配置对接多种云存储服务（S3 兼容、WebDAV 云盘等）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/storage-config")
public class CloudStorageConfigController {

    private final CloudStorageConfigRepository configRepository;
    private final UserRepository userRepository;
    private final CloudStorageDriveService driveService;

    public CloudStorageConfigController(CloudStorageConfigRepository configRepository,
                                        UserRepository userRepository,
                                        CloudStorageDriveService driveService) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.driveService = driveService;
    }

    /** 获取当前用户的所有云存储配置 */
    @GetMapping
    public ResponseEntity<List<CloudStorageConfig>> listConfigs(Authentication auth) {
        UUID userId = getUserId(auth);
        return ResponseEntity.ok(configRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    /** 新建云存储配置 */
    @PostMapping
    public ResponseEntity<CloudStorageConfig> createConfig(Authentication auth,
                                                           @RequestBody CloudStorageConfig config) {
        UUID userId = getUserId(auth);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        config.setUser(user);
        config.setId(null);
        if (config.getIsActive() == null) {
            config.setIsActive(false);
        }
        return ResponseEntity.ok(configRepository.save(config));
    }

    /** 更新云存储配置 */
    @PutMapping("/{id}")
    public ResponseEntity<CloudStorageConfig> updateConfig(Authentication auth,
                                                           @PathVariable UUID id,
                                                           @RequestBody CloudStorageConfig updates) {
        UUID userId = getUserId(auth);
        CloudStorageConfig existing = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        if (!existing.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getProviderType() != null) existing.setProviderType(updates.getProviderType());
        if (updates.getEndpointUrl() != null) existing.setEndpointUrl(updates.getEndpointUrl());
        if (updates.getAccessKey() != null) existing.setAccessKey(updates.getAccessKey());
        if (updates.getSecretKey() != null && !updates.getSecretKey().isBlank()) {
            existing.setSecretKey(updates.getSecretKey());
        }
        if (updates.getBucketName() != null) existing.setBucketName(updates.getBucketName());
        if (updates.getRegion() != null) existing.setRegion(updates.getRegion());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        if (updates.getExtraConfig() != null) existing.setExtraConfig(updates.getExtraConfig());

        return ResponseEntity.ok(configRepository.save(existing));
    }

    /** 删除云存储配置 */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteConfig(Authentication auth, @PathVariable UUID id) {
        UUID userId = getUserId(auth);
        CloudStorageConfig config = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        if (!config.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        configRepository.delete(config);
        return ResponseEntity.ok().build();
    }

    /**
     * 测试云存储连接。支持 S3 兼容存储和 WebDAV 云盘。
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(Authentication auth, @PathVariable UUID id) {
        UUID userId = getUserId(auth);
        CloudStorageConfig config = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        if (!config.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        CloudStorageDriveService.TestResult result;
        try {
            result = driveService.testConnection(config);
        } catch (Exception e) {
            log.error("测试存储连接异常: {}", e.getMessage());
            result = new CloudStorageDriveService.TestResult(false, "连接异常: " + e.getMessage());
        }

        config.setLastTestSuccess(result.success());
        config.setLastTestAt(LocalDateTime.now());
        configRepository.save(config);

        return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "message", result.message(),
                "testedAt", LocalDateTime.now().toString()
        ));
    }

    private UUID getUserId(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
