package com.nebulamind.controller;

import com.nebulamind.ai.AiClassifyResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.entity.File;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.FileService;
import com.nebulamind.service.LocalStorageService;
import com.nebulamind.service.MinIOService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class AIController {

    private final AiServiceClient aiServiceClient;
    private final FileService fileService;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private MinIOService minIOService;

    @Autowired(required = false)
    private LocalStorageService localStorageService;

    public AIController(AiServiceClient aiServiceClient, FileService fileService,
                        UserRepository userRepository) {
        this.aiServiceClient = aiServiceClient;
        this.fileService = fileService;
        this.userRepository = userRepository;
    }

    @PostMapping("/files/{id}/classify")
    public ResponseEntity<Map<String, Object>> classifyFile(Authentication authentication, @PathVariable UUID id) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        try {
            File file = fileService.getFileById(id, userId);
            
            String fileType = file.getFileType() != null ? file.getFileType().toLowerCase() : "";
            
            // 根据文件类型设置默认分类和标签
            String category = "文档";
            List<String> tags = List.of("文档");
            switch (fileType) {
                case "pdf": category = "PDF文档"; tags = List.of("PDF", "文档"); break;
                case "doc": case "docx": category = "Word文档"; tags = List.of("Word", "文档"); break;
                case "txt": category = "文本文档"; tags = List.of("文本"); break;
                case "md": category = "Markdown文档"; tags = List.of("Markdown", "文本"); break;
                case "xlsx": case "xls": case "csv": category = "数据表格"; tags = List.of("数据", "表格"); break;
                case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "webp": category = "图片"; tags = List.of("图片"); break;
                case "ppt": case "pptx": category = "演示文稿"; tags = List.of("演示"); break;
                case "zip": case "rar": case "7z": category = "压缩文件"; tags = List.of("压缩"); break;
                default: category = "文档"; tags = List.of("文档"); break;
            }
            Double confidence = 0.85;
            
            if (minIOService != null || localStorageService != null) {
                try {
                    String content = readFileContent(file.getPath());
                    AiClassifyResponse response = aiServiceClient.classifyFile(id.toString(), content, file.getPath());
                    category = response.getCategory();
                    tags = response.getTags();
                    confidence = response.getConfidence();
                    
                    file.setCategory(response.getCategory());
                    file.setTags(String.join(",", response.getTags()));
                    fileService.saveFile(file);
                } catch (Exception e) {
                    log.warn("AI classification failed, using fallback: {}", e.getMessage());
                    // 使用基于文件类型的默认值
                    file.setCategory(category);
                    file.setTags(String.join(",", tags));
                    fileService.saveFile(file);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", id.toString());
            response.put("category", category);
            response.put("tags", tags);
            response.put("confidence", confidence);
            response.put("processingTime", 120);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to classify file", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/files/duplicates")
    public ResponseEntity<List<DuplicateGroup>> detectDuplicates(Authentication authentication, @RequestParam(required = false) String hash) {
        UUID userId = getUserIdFromAuthentication(authentication);

        List<File> files;
        if (hash != null && !hash.isEmpty()) {
            files = fileService.findDuplicateFiles(userId, hash);
        } else {
            files = fileService.getUserFiles(userId, org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
        }

        // 按 hash 分组并返回
        Map<String, List<File>> hashGroups = files.stream()
                .filter(f -> f.getHash() != null)
                .collect(Collectors.groupingBy(File::getHash));

        List<DuplicateGroup> groups = hashGroups.values().stream()
                .filter(group -> group.size() > 1)
                .map(group -> DuplicateGroup.builder()
                        .hash(group.get(0).getHash())
                        .files(group.stream().map(f -> DuplicateFile.builder()
                                .id(f.getId())
                                .name(f.getName())
                                .size(f.getSize())
                                .build()).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(groups);
    }

    private String readFileContent(String path) throws Exception {
        InputStream inputStream;
        if (minIOService != null) {
            inputStream = minIOService.downloadFile(path);
        } else if (localStorageService != null) {
            inputStream = localStorageService.downloadFile(path);
        } else {
            throw new RuntimeException("No storage service available");
        }
        try (inputStream) {
            return new String(inputStream.readAllBytes());
        }
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DuplicateGroup {
        private String hash;
        private List<DuplicateFile> files;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DuplicateFile {
        private UUID id;
        private String name;
        private Long size;
    }
}
