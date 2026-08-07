package com.nebulamind.controller;

import com.nebulamind.entity.CloudStorageConfig;
import com.nebulamind.entity.User;
import com.nebulamind.repository.CloudStorageConfigRepository;
import com.nebulamind.repository.UserRepository;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.ListBucketsArgs;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 云存储配置管理。
 * 用户可在此配置对接多种云存储服务（S3 兼容、联通云盘等）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/storage-config")
public class CloudStorageConfigController {

    private final CloudStorageConfigRepository configRepository;
    private final UserRepository userRepository;

    public CloudStorageConfigController(CloudStorageConfigRepository configRepository,
                                         UserRepository userRepository) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
    }

    /** 获取当前用户的所有云存储配置 */
    @GetMapping
    public ResponseEntity<List<CloudStorageConfig>> listConfigs(Authentication auth) {
        UUID userId = getUserId(auth);
        return ResponseEntity.ok(configRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    /** 新增云存储配置 */
    @PostMapping
    public ResponseEntity<CloudStorageConfig> createConfig(Authentication auth,
                                                            @RequestBody CloudStorageConfig config) {
        UUID userId = getUserId(auth);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        config.setUser(user);
        config.setId(null);
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
        if (updates.getSecretKey() != null) existing.setSecretKey(updates.getSecretKey());
        if (updates.getBucketName() != null) existing.setBucketName(updates.getBucketName());
        if (updates.getRegion() != null) existing.setRegion(updates.getRegion());
        if (updates.getRedirectUri() != null) existing.setRedirectUri(updates.getRedirectUri());
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
     * 测试云存储连接。
     * 支持 S3 兼容存储和联通云盘两种类型。
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(Authentication auth, @PathVariable UUID id) {
        UUID userId = getUserId(auth);
        CloudStorageConfig config = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        if (!config.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        boolean success;
        String message;
        try {
            switch (config.getProviderType().toUpperCase()) {
                case "S3":
                    var result = testS3Connection(config);
                    success = result.success;
                    message = result.message;
                    break;
                case "UNICOM":
                    result = testUnicomConnection(config);
                    success = result.success;
                    message = result.message;
                    break;
                default:
                    success = false;
                    message = "不支持的存储类型: " + config.getProviderType();
            }
        } catch (Exception e) {
            success = false;
            message = "连接异常: " + e.getMessage();
            log.error("测试存储连接异常: {}", e.getMessage());
        }

        // 更新测试结果
        config.setLastTestSuccess(success);
        config.setLastTestAt(LocalDateTime.now());
        configRepository.save(config);

        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", message,
                "testedAt", LocalDateTime.now().toString()
        ));
    }

    // ========== 私有方法 ==========

    private TestResult testS3Connection(CloudStorageConfig config) {
        String endpoint = config.getEndpointUrl();
        if (endpoint == null || endpoint.isEmpty()) {
            return new TestResult(false, "Endpoint URL 不能为空");
        }

        try {
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(config.getAccessKey(), config.getSecretKey())
                    .region(config.getRegion())
                    .build();

            minioClient.listBuckets(ListBucketsArgs.builder().build());
            return new TestResult(true, "S3 连接成功，已列出存储桶");
        } catch (Exception e) {
            return new TestResult(false, "S3 连接失败: " + e.getMessage());
        }
    }

    private TestResult testUnicomConnection(CloudStorageConfig config) {
        String baseUrl = config.getEndpointUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            return new TestResult(false, "Endpoint URL 不能为空");
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        try {
            String url = baseUrl.endsWith("/health") ? baseUrl : baseUrl + "/health";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return new TestResult(true, "联通云盘连接成功");
                }
                // 有些云盘没有 /health 端点，尝试根路径
                String rootUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                Request rootReq = new Request.Builder().url(rootUrl).get().build();
                try (Response rootResp = client.newCall(rootReq).execute()) {
                    return new TestResult(rootResp.isSuccessful(),
                            rootResp.isSuccessful() ? "联通云盘连接成功" : "联通云盘返回状态码: " + rootResp.code());
                }
            }
        } catch (Exception e) {
            return new TestResult(false, "联通云盘连接失败: " + e.getMessage());
        }
    }

    private UUID getUserId(Authentication auth) {
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private record TestResult(boolean success, String message) {}
}
