package com.nebulamind.controller;

import com.nebulamind.entity.FileVersion;
import com.nebulamind.entity.File;
import com.nebulamind.entity.User;
import com.nebulamind.api.client.maas.MaasApiClient;
import com.nebulamind.api.client.maas.MaasApiProperties;
import com.nebulamind.api.client.maas.MaasChatResponse;
import com.nebulamind.repository.FileRepository;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.FileVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final MaasApiClient maasApiClient;
    private final MaasApiProperties maasApiProperties;

    /**
     * 获取文件的版本历史列表
     */
    @GetMapping
    public ResponseEntity<?> getVersionHistory(
            @PathVariable UUID fileId,
            Authentication authentication) {
        return ResponseEntity.ok(fileVersionService.getVersionHistoryItems(fileId));
    }

    /**
     * 获取特定版本详情
     */
    @GetMapping("/{versionNumber}")
    public ResponseEntity<?> getVersion(
            @PathVariable UUID fileId,
            @PathVariable int versionNumber,
            Authentication authentication) {

        Map<String, Object> version = fileVersionService.getVersionItem(fileId, versionNumber);
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

        String contentStr = (String) request.get("content");
        String comment = (String) request.getOrDefault("comment", "");
        if (contentStr == null || contentStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "内容不能为空"));
        }

        UUID userId = getUserId(authentication);
        File updated = fileVersionService.saveTextVersion(fileId, contentStr, comment, userId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 上传文件新版本（适用于所有格式）
     */
    @PostMapping("/upload")
    public ResponseEntity<File> uploadNewVersion(
            @PathVariable UUID fileId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestParam(value = "encrypted", required = false, defaultValue = "false") boolean encrypted,
            Authentication authentication) {
        UUID userId = getUserId(authentication);
        File updated = fileVersionService.uploadNewVersion(fileId, file, comment, userId, encrypted);
        return ResponseEntity.ok(updated);
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
     * 生成最新两个版本的变化摘要
     */
    @PostMapping("/summary")
    public ResponseEntity<?> summarizeVersionChange(
            @PathVariable UUID fileId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        UUID userId = getUserId(authentication);

        File fileEntity = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new com.nebulamind.exception.ResourceNotFoundException("File", fileId.toString()));
        if (File.EncryptionMode.CLIENT.equals(fileEntity.getEncryptionMode())) {
            return ResponseEntity.ok(Map.of(
                    "fileId", fileId.toString(),
                    "summary", "该文件已开启端到端加密，服务器无法读取内容生成版本变化摘要。"
            ));
        }

        Integer versionA = null;
        Integer versionB = null;
        if (request.get("versionA") != null) {
            versionA = ((Number) request.get("versionA")).intValue();
        }
        if (request.get("versionB") != null) {
            versionB = ((Number) request.get("versionB")).intValue();
        }

        if (versionA == null || versionB == null) {
            List<FileVersion> latest = fileVersionService.getVersionHistory(fileId);
            if (latest.size() < 2) {
                return ResponseEntity.ok(Map.of(
                        "fileId", fileId.toString(),
                        "summary", "暂无足够的版本记录用于生成变化摘要"
                ));
            }
            versionB = latest.get(0).getVersionNumber();
            versionA = latest.get(1).getVersionNumber();
        }

        Map<String, Object> diff = fileVersionService.diffVersions(fileId, versionA, versionB);
        String summary;
        if ("binary".equals(diff.get("diffFormat"))) {
            summary = "本次版本变化为文件替换或二进制内容，无法基于文本差异生成摘要。";
        } else {
            String diffText = (String) diff.get("diff");
            if (diffText == null || diffText.isBlank()) {
                summary = "两个版本内容相同，没有明显变化。";
            } else {
                String prompt = "请用一到两句话概括以下文件版本差异中发生了哪些实质变化，不要输出代码块或额外说明。\n\n"
                        + truncate(diffText, 4000);
                MaasChatResponse chatResponse = maasApiClient.chat(
                        maasApiProperties.getLlmModel(),
                        List.of(new MaasApiClient.Message("user", prompt)),
                        0.3,
                        300);
                if (chatResponse != null
                        && chatResponse.getChoices() != null
                        && !chatResponse.getChoices().isEmpty()
                        && chatResponse.getChoices().get(0).getMessage() != null
                        && chatResponse.getChoices().get(0).getMessage().getContent() != null) {
                    summary = chatResponse.getChoices().get(0).getMessage().getContent().trim();
                } else {
                    summary = "AI 变化摘要生成失败，请稍后重试。";
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "fileId", fileId.toString(),
                "versionA", versionA,
                "versionB", versionB,
                "summary", summary
        ));
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

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
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
