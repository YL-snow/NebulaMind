package com.nebulamind.controller;

import com.nebulamind.ai.AiQAResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.dto.QARequest;
import com.nebulamind.entity.File;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.FileService;
import com.nebulamind.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/qa")
public class QAController {

    private final AiServiceClient aiServiceClient;
    private final FileService fileService;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public QAController(AiServiceClient aiServiceClient, FileService fileService,
                        UserRepository userRepository, StorageService storageService) {
        this.aiServiceClient = aiServiceClient;
        this.fileService = fileService;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @PostMapping
    public ResponseEntity<AiQAResponse> documentQA(
            Authentication authentication,
            @RequestBody QARequest request) {
        
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            
            if (request.getFileId() == null || request.getFileId().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            // 读取文件内容传给AI服务，解决向量库未索引时无上下文的问题
            String fileContent = null;
            String filePath = null;
            try {
                File file = fileService.getFileById(UUID.fromString(request.getFileId()), userId);
                fileContent = readFileContent(file.getPath());
                filePath = file.getPath();
            } catch (Exception e) {
                log.warn("Failed to read file content for QA: {}", e.getMessage());
            }
            
            AiQAResponse response = aiServiceClient.documentQA(request.getFileId(), request.getQuestion(), fileContent, filePath);
            
            if (response.getAnswer() != null && isRateLimited(response.getAnswer())) {
                return ResponseEntity.ok(AiQAResponse.builder()
                        .question(request.getQuestion())
                        .answer("API调用次数已达上限（10次/分钟），请等待1分钟后再试。")
                        .sourceFileId(request.getFileId())
                        .confidence(0.0)
                        .build());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("AI QA service unavailable, returning fallback: {}", e.getMessage());
            String fallbackMsg = (e.getMessage() != null && isRateLimited(e.getMessage()))
                    ? "API调用次数已达上限（10次/分钟），请等待1分钟后再试。"
                    : "AI 问答服务暂时不可用，请稍后重试。如果问题持续，请联系管理员。";
            AiQAResponse fallback = AiQAResponse.builder()
                    .question(request != null ? request.getQuestion() : "")
                    .answer(fallbackMsg)
                    .sourceFileId(request != null ? request.getFileId() : "")
                    .sourceSnippets(null)
                    .confidence(0.0)
                    .build();
            return ResponseEntity.ok(fallback);
        }
    }

    @PostMapping("/cross")
    public ResponseEntity<AiQAResponse> crossDocumentQA(
            Authentication authentication,
            @RequestBody QARequest request) {
        
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            
            List<String> fileIds;
            if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
                fileIds = request.getFileIds();
            } else {
                fileIds = fileService.getUserFiles(userId, org.springframework.data.domain.PageRequest.of(0, 100))
                        .getContent()
                        .stream()
                        .map(f -> f.getId().toString())
                        .collect(Collectors.toList());
            }
            
            // 读取所有文件内容传给AI服务
            Map<String, String> fileContents = new HashMap<>();
            Map<String, String> filePaths = new HashMap<>();
            for (String fid : fileIds) {
                try {
                    File file = fileService.getFileById(UUID.fromString(fid), userId);
                    String content = readFileContent(file.getPath());
                    if (content != null && !content.isEmpty()) {
                        fileContents.put(fid, content.length() > 3000 ? content.substring(0, 3000) : content);
                        filePaths.put(fid, file.getPath());
                    }
                } catch (Exception e) {
                    log.warn("Failed to read file content for cross QA, fileId={}: {}", fid, e.getMessage());
                }
            }
            
            AiQAResponse response = aiServiceClient.crossDocumentQA(fileIds, request.getQuestion(), fileContents, filePaths);
            
            if (response.getAnswer() != null && isRateLimited(response.getAnswer())) {
                return ResponseEntity.ok(AiQAResponse.builder()
                        .question(request.getQuestion())
                        .answer("API调用次数已达上限（10次/分钟），请等待1分钟后再试。")
                        .sourceFileId("")
                        .confidence(0.0)
                        .build());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("AI cross QA service unavailable, returning fallback: {}", e.getMessage());
            String fallbackMsg = (e.getMessage() != null && isRateLimited(e.getMessage()))
                    ? "API调用次数已达上限（10次/分钟），请等待1分钟后再试。"
                    : "AI 问答服务暂时不可用，请稍后重试。如果问题持续，请联系管理员。";
            AiQAResponse fallback = AiQAResponse.builder()
                    .question(request != null ? request.getQuestion() : "")
                    .answer(fallbackMsg)
                    .sourceFileId("")
                    .sourceSnippets(null)
                    .confidence(0.0)
                    .build();
            return ResponseEntity.ok(fallback);
        }
    }

    private String readFileContent(String path) throws Exception {
        try (InputStream inputStream = storageService.downloadFile(path)) {
            return new String(inputStream.readAllBytes());
        }
    }

    private boolean isRateLimited(String content) {
        return content != null && (content.startsWith("RATE_LIMITED:") ||
                content.contains("调用次数已达上限") ||
                content.contains("API rate limit"));
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
