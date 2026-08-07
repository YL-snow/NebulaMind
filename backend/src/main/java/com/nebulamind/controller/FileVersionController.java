package com.nebulamind.controller;

import com.nebulamind.entity.FileVersion;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.repository.FileRepository;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.FileVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件版本管理 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files/{fileId}/versions")
@RequiredArgsConstructor
public class FileVersionController {

    private final FileVersionService fileVersionService;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    /**
     * 获取文件的版本历史列表
     */
    @GetMapping
    public ResponseEntity<?> getVersionHistory(
            @PathVariable UUID fileId,
            Authentication authentication,
            Pageable pageable) {

        if (pageable.isPaged()) {
            Page<FileVersion> page = fileVersionService.getVersionHistory(fileId, pageable);
            return ResponseEntity.ok(page);
        } else {
            List<FileVersion> versions = fileVersionService.getVersionHistory(fileId);
            return ResponseEntity.ok(versions);
        }
    }

    /**
     * 获取特定版本详情
     */
    @GetMapping("/{versionNumber}")
    public ResponseEntity<?> getVersion(
            @PathVariable UUID fileId,
            @PathVariable int versionNumber,
            Authentication authentication) {

        FileVersion version = fileVersionService.getVersion(fileId, versionNumber);
        return ResponseEntity.ok(version);
    }

    /**
     * 创建新版本
     */
    @PostMapping
    public ResponseEntity<?> createVersion(
            @PathVariable UUID fileId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("文件不存在"));

        String contentStr = (String) request.get("content");
        String comment = (String) request.getOrDefault("comment", "");
        if (contentStr == null || contentStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "内容不能为空"));
        }

        UUID userId = getUserId(authentication);
        byte[] content = contentStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FileVersion version = fileVersionService.createVersion(file, content, comment, userId);

        return ResponseEntity.ok(Map.of(
                "versionId", version.getId().toString(),
                "versionNumber", version.getVersionNumber(),
                "fileHash", version.getFileHash(),
                "createdAt", version.getCreatedAt().toString()
        ));
    }

    /**
     * 对比两个版本差异
     */
    @GetMapping("/diff")
    public ResponseEntity<?> diffVersions(
            @PathVariable UUID fileId,
            @RequestParam int versionA,
            @RequestParam int versionB,
            Authentication authentication) {

        Map<String, Object> diff = fileVersionService.diffVersions(fileId, versionA, versionB);
        return ResponseEntity.ok(diff);
    }

    /**
     * 版本回滚
     */
    @PostMapping("/rollback/{targetVersion}")
    public ResponseEntity<?> rollbackToVersion(
            @PathVariable UUID fileId,
            @PathVariable int targetVersion,
            Authentication authentication) {

        UUID userId = getUserId(authentication);
        File file = fileVersionService.rollbackToVersion(fileId, targetVersion, userId);

        return ResponseEntity.ok(Map.of(
                "fileId", file.getId().toString(),
                "currentVersion", file.getVersion(),
                "rolledBackFrom", targetVersion,
                "message", "文件已回滚到版本 " + targetVersion
        ));
    }

    /**
     * 获取编辑痕迹追踪
     */
    @GetMapping("/history")
    public ResponseEntity<?> getEditHistory(
            @PathVariable UUID fileId,
            Authentication authentication) {

        List<Map<String, Object>> history = fileVersionService.getEditHistory(fileId);
        return ResponseEntity.ok(Map.of(
                "fileId", fileId.toString(),
                "totalRevisions", history.size(),
                "history", history
        ));
    }

    private UUID getUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("未认证");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return user.getId();
    }
}
