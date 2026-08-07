package com.nebulamind.controller;

import com.nebulamind.ai.AiGenerateResponse;
import com.nebulamind.ai.AiServiceClient;
import com.nebulamind.api.client.maas.MaasApiClient;
import com.nebulamind.api.client.maas.MaasChatResponse;
import com.nebulamind.dto.GenerateRequest;
import com.nebulamind.entity.File;
import com.nebulamind.repository.UserRepository;
import com.nebulamind.service.FileService;
import com.nebulamind.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/generate")
public class GenerateController {

    private final AiServiceClient aiServiceClient;
    private final MaasApiClient maasApiClient;
    private final FileService fileService;
    private final UserRepository userRepository;
    private final StorageService storageService;

    /** 图片扩展名 → MIME 类型映射 */
    private static final Map<String, String> IMAGE_MIME_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif"
    );
    private static final Set<String> IMAGE_EXTENSIONS = IMAGE_MIME_TYPES.keySet();

    public GenerateController(AiServiceClient aiServiceClient, MaasApiClient maasApiClient,
                               FileService fileService, UserRepository userRepository,
                               StorageService storageService) {
        this.aiServiceClient = aiServiceClient;
        this.maasApiClient = maasApiClient;
        this.fileService = fileService;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @PostMapping("/summary")
    public ResponseEntity<AiGenerateResponse> generateSummary(
            Authentication authentication,
            @RequestBody GenerateRequest request) {

        try {
            UUID userId = getUserIdFromAuthentication(authentication);

            if (request.getFileId() == null || request.getFileId().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            File file = fileService.getFileById(UUID.fromString(request.getFileId()), userId);
            AiGenerateResponse response;

            if (isImageFile(file)) {
                String base64 = readImageAsBase64(file.getPath());
                String mimeType = IMAGE_MIME_TYPES.get(file.getFileType().toLowerCase());
                String prompt = "请详细描述这张图片的内容，包括图片中的文字信息（如有），并给出摘要。";
                MaasApiClient.VisionResult result = maasApiClient.chatVision(prompt, base64, mimeType, 0.7, 1000);
                if (result.isSuccess()) {
                    response = toGenerateResponse(request.getFileId(), result.getResponse());
                } else {
                    response = AiGenerateResponse.builder()
                            .fileId(request.getFileId())
                            .content("图片分析失败（已尝试多个模型均不可用）：" + result.getErrorMessage())
                            .build();
                }
            } else {
                try {
                    String content = readFileContent(file.getPath());
                    String base64 = readFileBase64(file.getPath());
                    response = aiServiceClient.generateSummary(request.getFileId(), content, file.getPath(), base64, file.getFileType());
                    if (isRateLimited(response.getContent())) {
                        return ResponseEntity.ok(rateLimitResponse(request.getFileId()));
                    }
                } catch (Exception e) {
                    log.warn("AI summary service unavailable, using fallback: {}", e.getMessage());
                    if (e.getMessage() != null && isRateLimited(e.getMessage())) {
                        return ResponseEntity.ok(rateLimitResponse(request.getFileId()));
                    }
                    response = AiGenerateResponse.builder()
                            .fileId(request.getFileId())
                            .content("AI 摘要服务暂时不可用，请稍后重试")
                            .keyPoints(null)
                            .format("markdown")
                            .build();
                }
            }

            // 保存摘要到文件实体
            if (response != null && response.getContent() != null && !response.getContent().contains("图片分析失败") && !response.getContent().contains("暂时不可用")) {
                file.setSummary(response.getContent());
                file.setAiStatus(File.AiStatus.COMPLETED);
                fileService.saveFile(file);
                log.info("Summary saved for file: {}", request.getFileId());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            return ResponseEntity.ok(AiGenerateResponse.builder()
                    .fileId(request.getFileId())
                    .content("摘要生成失败：" + e.getMessage())
                    .build());
        }
    }

    @PostMapping("/extract")
    public ResponseEntity<AiGenerateResponse> extractContent(
            Authentication authentication,
            @RequestBody GenerateRequest request) {

        try {
            UUID userId = getUserIdFromAuthentication(authentication);

            if (request.getFileId() == null || request.getFileId().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            File file = fileService.getFileById(UUID.fromString(request.getFileId()), userId);

            if (isImageFile(file)) {
                String base64 = readImageAsBase64(file.getPath());
                String mimeType = IMAGE_MIME_TYPES.get(file.getFileType().toLowerCase());
                String prompt = "请仔细分析这张图片，提取其中的所有关键信息，包括文字内容、数据、图表信息等。以结构化方式输出。";
                MaasApiClient.VisionResult result = maasApiClient.chatVision(prompt, base64, mimeType, 0.7, 1500);
                if (result.isSuccess()) {
                    return ResponseEntity.ok(toGenerateResponse(request.getFileId(), result.getResponse()));
                }
                return ResponseEntity.ok(AiGenerateResponse.builder()
                        .fileId(request.getFileId())
                        .content("图片分析失败（已尝试多个模型均不可用）：" + result.getErrorMessage())
                        .build());
            } else {
                String content = readFileContent(file.getPath());
                String base64 = readFileBase64(file.getPath());
                AiGenerateResponse response = aiServiceClient.extractContent(request.getFileId(), content, file.getPath(), base64, file.getFileType());
                if (isRateLimited(response.getContent())) {
                    return ResponseEntity.ok(rateLimitResponse(request.getFileId()));
                }
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("内容提取失败", e);
            if (e.getMessage() != null && isRateLimited(e.getMessage())) {
                return ResponseEntity.ok(rateLimitResponse(request.getFileId() != null ? request.getFileId() : ""));
            }
            return ResponseEntity.ok(AiGenerateResponse.builder()
                    .fileId(request.getFileId())
                    .content("内容提取失败：" + e.getMessage())
                    .build());
        }
    }

    @PostMapping("/report")
    public ResponseEntity<AiGenerateResponse> generateReport(
            Authentication authentication,
            @RequestBody GenerateRequest request) {

        try {
            UUID userId = getUserIdFromAuthentication(authentication);

            List<String> fileIds;
            if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
                fileIds = request.getFileIds();
            } else {
                fileIds = fileService.getUserFiles(userId, org.springframework.data.domain.PageRequest.of(0, 50))
                        .getContent()
                        .stream()
                        .map(f -> f.getId().toString())
                        .collect(Collectors.toList());
            }

            String topic = request.getTopic() != null ? request.getTopic() : "综合分析报告";

            List<File> selectedFiles = fileIds.stream()
                    .map(id -> {
                        try {
                            return fileService.getFileById(UUID.fromString(id), userId);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(f -> f != null)
                    .collect(Collectors.toList());

            if (!selectedFiles.isEmpty() && selectedFiles.stream().allMatch(this::isImageFile)) {
                return handleImageReport(selectedFiles, topic, userId);
            }

            // 读取所有文件内容传给AI服务
            Map<String, String> contents = new java.util.HashMap<>();
            Map<String, String> filePaths = new java.util.HashMap<>();
            Map<String, String> fileBases64 = new java.util.HashMap<>();
            for (File f : selectedFiles) {
                try {
                    String content = readFileContent(f.getPath());
                    if (content != null && !content.isEmpty()) {
                        contents.put(f.getId().toString(), content);
                        filePaths.put(f.getId().toString(), f.getPath());
                        fileBases64.put(f.getId().toString(), readFileBase64(f.getPath()));
                    }
                } catch (Exception ex) {
                    log.warn("Failed to read content for report, file={}: {}", f.getId(), ex.getMessage());
                }
            }

            AiGenerateResponse response = aiServiceClient.generateReport(fileIds, topic, contents, filePaths, fileBases64);
            if (isRateLimited(response.getContent())) {
                return ResponseEntity.ok(rateLimitResponse(null));
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("报告生成失败", e);
            if (e.getMessage() != null && isRateLimited(e.getMessage())) {
                return ResponseEntity.ok(rateLimitResponse(null));
            }
            return ResponseEntity.ok(AiGenerateResponse.builder()
                    .content("报告生成失败：" + e.getMessage())
                    .build());
        }
    }

    @PostMapping("/ppt")
    public ResponseEntity<AiGenerateResponse> generatePPT(
            Authentication authentication,
            @RequestBody GenerateRequest request) {

        try {
            UUID userId = getUserIdFromAuthentication(authentication);

            List<String> fileIds;
            if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
                fileIds = request.getFileIds();
            } else {
                fileIds = fileService.getUserFiles(userId, org.springframework.data.domain.PageRequest.of(0, 50))
                        .getContent()
                        .stream()
                        .map(f -> f.getId().toString())
                        .collect(Collectors.toList());
            }

            String topic = request.getTopic() != null ? request.getTopic() : "演示文稿";

            List<File> selectedFiles = fileIds.stream()
                    .map(id -> {
                        try {
                            return fileService.getFileById(UUID.fromString(id), userId);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(f -> f != null)
                    .collect(Collectors.toList());

            if (!selectedFiles.isEmpty() && selectedFiles.stream().allMatch(this::isImageFile)) {
                return handleImagePPT(selectedFiles, topic, userId);
            }

            // 读取所有文件内容传给AI服务
            Map<String, String> contents = new java.util.HashMap<>();
            Map<String, String> filePaths = new java.util.HashMap<>();
            Map<String, String> fileBases64 = new java.util.HashMap<>();
            for (File f : selectedFiles) {
                try {
                    String content = readFileContent(f.getPath());
                    if (content != null && !content.isEmpty()) {
                        contents.put(f.getId().toString(), content);
                        filePaths.put(f.getId().toString(), f.getPath());
                        fileBases64.put(f.getId().toString(), readFileBase64(f.getPath()));
                    }
                } catch (Exception ex) {
                    log.warn("Failed to read content for PPT, file={}: {}", f.getId(), ex.getMessage());
                }
            }

            AiGenerateResponse response = aiServiceClient.generatePPT(fileIds, topic, contents, filePaths, fileBases64);
            if (isRateLimited(response.getContent())) {
                return ResponseEntity.ok(rateLimitResponse(null));
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("PPT生成失败", e);
            if (e.getMessage() != null && isRateLimited(e.getMessage())) {
                return ResponseEntity.ok(rateLimitResponse(null));
            }
            return ResponseEntity.ok(AiGenerateResponse.builder()
                    .content("PPT生成失败：" + e.getMessage())
                    .build());
        }
    }

    @PostMapping("/convert")
    public ResponseEntity<AiGenerateResponse> convertFormat(
            Authentication authentication,
            @RequestBody GenerateRequest request) {

        try {
            UUID userId = getUserIdFromAuthentication(authentication);

            if (request.getFileId() == null || request.getFileId().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            File file = fileService.getFileById(UUID.fromString(request.getFileId()), userId);
            String content = readFileContent(file.getPath());
            String targetFormat = request.getTargetFormat() != null ? request.getTargetFormat() : "docx";

            AiGenerateResponse response = aiServiceClient.convertFormat(request.getFileId(), content, targetFormat, file.getPath());
            if (isRateLimited(response.getContent())) {
                return ResponseEntity.ok(rateLimitResponse(request.getFileId()));
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("格式转换失败", e);
            if (e.getMessage() != null && isRateLimited(e.getMessage())) {
                return ResponseEntity.ok(rateLimitResponse(request.getFileId()));
            }
            return ResponseEntity.ok(AiGenerateResponse.builder()
                    .fileId(request.getFileId())
                    .content("格式转换失败：" + e.getMessage())
                    .build());
        }
    }

    // ========== 私有方法 ==========

    private boolean isImageFile(File file) {
        return file.getFileType() != null && IMAGE_EXTENSIONS.contains(file.getFileType().toLowerCase());
    }

    private boolean isRateLimited(String content) {
        return content != null && (content.startsWith("RATE_LIMITED:") ||
                content.contains("调用次数已达上限") ||
                content.contains("API rate limit"));
    }

    private AiGenerateResponse rateLimitResponse(String fileId) {
        return AiGenerateResponse.builder()
                .fileId(fileId)
                .content("API调用次数已达上限（10次/分钟），请等待1分钟后再试。如需调整限制，请联系管理员。")
                .format("text")
                .build();
    }

    private String readFileContent(String path) throws Exception {
        InputStream inputStream = storageService.downloadFile(path);
        try (inputStream) {
            return new String(inputStream.readAllBytes());
        }
    }

    private String readFileBase64(String path) throws Exception {
        try (InputStream inputStream = storageService.downloadFile(path)) {
            return Base64.getEncoder().encodeToString(inputStream.readAllBytes());
        }
    }

    private String readImageAsBase64(String path) throws Exception {
        try (InputStream inputStream = storageService.downloadFile(path)) {
            byte[] bytes = inputStream.readAllBytes();
            return Base64.getEncoder().encodeToString(bytes);
        }
    }

    private AiGenerateResponse toGenerateResponse(String fileId, MaasChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getChoices() == null || chatResponse.getChoices().isEmpty()) {
            return AiGenerateResponse.builder()
                    .fileId(fileId)
                    .content("生成失败，AI 服务无返回结果")
                    .build();
        }
        String content = chatResponse.getChoices().get(0).getMessage().getContent();
        return AiGenerateResponse.builder()
                .fileId(fileId)
                .content(content)
                .format("markdown")
                .build();
    }

    private ResponseEntity<AiGenerateResponse> handleImageReport(List<File> imageFiles, String topic, UUID userId) {
        try {
            StringBuilder combined = new StringBuilder();
            for (File file : imageFiles) {
                String base64 = readImageAsBase64(file.getPath());
                String mimeType = IMAGE_MIME_TYPES.get(file.getFileType().toLowerCase());
                String prompt = "请详细描述这张图片的内容，包括所有文字信息。";
                MaasApiClient.VisionResult vr = maasApiClient.chatVision(prompt, base64, mimeType, 0.7, 1000);
                if (vr.isSuccess() && vr.getResponse() != null
                        && vr.getResponse().getChoices() != null
                        && !vr.getResponse().getChoices().isEmpty()) {
                    combined.append("【文件：").append(file.getName()).append("】\n")
                            .append(vr.getResponse().getChoices().get(0).getMessage().getContent()).append("\n\n");
                } else {
                    combined.append("【文件：").append(file.getName()).append("】\n")
                            .append("[图片分析失败：").append(vr.getErrorMessage()).append("]\n\n");
                }
            }

            String summaryPrompt = "根据以下多张图片的描述内容，生成一份关于「" + topic + "」的综合分析报告，要求格式规范、内容完整。\n\n"
                    + combined.toString();
            List<MaasApiClient.Message> msgs = List.of(new MaasApiClient.Message("user", summaryPrompt));
            MaasChatResponse finalResp = maasApiClient.chat("yuanjing-70b-chat", msgs, 0.7, 2000);

            String fileId = imageFiles.stream().map(f -> f.getId().toString()).collect(Collectors.joining(","));
            if (finalResp == null) {
                return ResponseEntity.ok(AiGenerateResponse.builder()
                        .content("图片分析失败，MaaS 平台无返回结果").build());
            }
            return ResponseEntity.ok(toGenerateResponse(fileId, finalResp));
        } catch (Exception e) {
            log.error("图片报告生成失败", e);
            return ResponseEntity.ok(AiGenerateResponse.builder()
                    .content("图片分析失败: " + e.getMessage()).build());
        }
    }

    private ResponseEntity<AiGenerateResponse> handleImagePPT(List<File> imageFiles, String topic, UUID userId) {
        try {
            StringBuilder combined = new StringBuilder();
            for (File file : imageFiles) {
                String base64 = readImageAsBase64(file.getPath());
                String mimeType = IMAGE_MIME_TYPES.get(file.getFileType().toLowerCase());
                String prompt = "请详细描述这张图片的内容，包括所有文字信息。";
                MaasApiClient.VisionResult vr = maasApiClient.chatVision(prompt, base64, mimeType, 0.7, 1000);
                if (vr.isSuccess() && vr.getResponse() != null
                        && vr.getResponse().getChoices() != null
                        && !vr.getResponse().getChoices().isEmpty()) {
                    combined.append("【文件：").append(file.getName()).append("】\n")
                            .append(vr.getResponse().getChoices().get(0).getMessage().getContent()).append("\n\n");
                } else {
                    combined.append("【文件：").append(file.getName()).append("】\n")
                            .append("[图片分析失败：").append(vr.getErrorMessage()).append("]\n\n");
                }
            }

            String pptPrompt = "根据以下对图片内容的描述，生成一份关于「" + topic + "」的 PPT 演示文稿内容大纲，"
                    + "包含标题、每页要点，使用 markdown 格式输出。\n\n" + combined.toString();
            List<MaasApiClient.Message> msgs = List.of(new MaasApiClient.Message("user", pptPrompt));
            MaasChatResponse finalResp = maasApiClient.chat("yuanjing-70b-chat", msgs, 0.7, 2000);

            String fileId = imageFiles.stream().map(f -> f.getId().toString()).collect(Collectors.joining(","));
            if (finalResp == null) {
                return ResponseEntity.ok(AiGenerateResponse.builder()
                        .content("图片分析失败，MaaS 平台无返回结果").build());
            }
            return ResponseEntity.ok(toGenerateResponse(fileId, finalResp));
        } catch (Exception e) {
            log.error("图片 PPT 生成失败", e);
            return ResponseEntity.ok(AiGenerateResponse.builder()
                    .content("图片分析失败: " + e.getMessage()).build());
        }
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
