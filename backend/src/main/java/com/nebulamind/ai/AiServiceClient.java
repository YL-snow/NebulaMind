package com.nebulamind.ai;

import com.nebulamind.config.AiServiceConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final AiServiceConfig config;
    private final ObjectMapper objectMapper;

    private OkHttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    public AiClassifyResponse classifyFile(String fileId, String content) throws IOException {
        return classifyFile(fileId, content, null);
    }

    public AiClassifyResponse classifyFile(String fileId, String content, String filePath) throws IOException {
        String url = config.getBaseUrl() + config.getClassification().getClassifyUrl();
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_id", fileId);
        body.put("content", content);
        if (filePath != null && !filePath.isEmpty()) {
            body.put("file_path", filePath);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiClassifyResponse.class);
    }

    public AiSearchResponse semanticSearch(String query, List<String> fileIds, int topK) throws IOException {
        String url = config.getBaseUrl() + config.getSearch().getSemanticSearchUrl();
        
        Map<String, Object> body = Map.of(
                "query", query,
                "file_ids", fileIds,
                "top_k", topK
        );

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiSearchResponse.class);
    }

    public AiQAResponse documentQA(String fileId, String question) throws IOException {
        return documentQA(fileId, question, null, null);
    }

    public AiQAResponse documentQA(String fileId, String question, String fileContent) throws IOException {
        return documentQA(fileId, question, fileContent, null);
    }

    public AiQAResponse documentQA(String fileId, String question, String fileContent, String filePath) throws IOException {
        String url = config.getBaseUrl() + config.getQa().getDocumentQaUrl();
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_id", fileId);
        body.put("question", question);
        if (fileContent != null && !fileContent.isEmpty()) {
            body.put("file_content", fileContent.length() > 12000 ? fileContent.substring(0, 12000) : fileContent);
        }
        if (filePath != null && !filePath.isEmpty()) {
            body.put("file_path", filePath);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiQAResponse.class);
    }

    public AiQAResponse crossDocumentQA(List<String> fileIds, String question) throws IOException {
        return crossDocumentQA(fileIds, question, null, null);
    }

    public AiQAResponse crossDocumentQA(List<String> fileIds, String question, java.util.Map<String, String> fileContents) throws IOException {
        return crossDocumentQA(fileIds, question, fileContents, null);
    }

    public AiQAResponse crossDocumentQA(List<String> fileIds, String question, java.util.Map<String, String> fileContents,
                                        java.util.Map<String, String> filePaths) throws IOException {
        String url = config.getBaseUrl() + config.getQa().getCrossDocumentQaUrl();
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_ids", fileIds);
        body.put("question", question);
        if (fileContents != null && !fileContents.isEmpty()) {
            body.put("file_contents", fileContents);
        }
        if (filePaths != null && !filePaths.isEmpty()) {
            body.put("file_paths", filePaths);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiQAResponse.class);
    }

    public AiGenerateResponse generateSummary(String fileId, String content) throws IOException {
        return generateSummary(fileId, content, null, null, null);
    }

    public AiGenerateResponse generateSummary(String fileId, String content, String filePath) throws IOException {
        return generateSummary(fileId, content, filePath, null, null);
    }

    public AiGenerateResponse generateSummary(String fileId, String content, String filePath,
                                               String fileContentBase64, String fileType) throws IOException {
        String url = config.getBaseUrl() + config.getGenerate().getSummaryUrl();
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_id", fileId);
        body.put("content", content);
        body.put("max_length", 300);
        if (filePath != null && !filePath.isEmpty()) {
            body.put("file_path", filePath);
        }
        if (fileContentBase64 != null && !fileContentBase64.isEmpty()) {
            body.put("file_content_base64", fileContentBase64);
        }
        if (fileType != null && !fileType.isEmpty()) {
            body.put("file_type", fileType);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiGenerateResponse.class);
    }

    public AiGenerateResponse extractContent(String fileId, String content) throws IOException {
        return extractContent(fileId, content, null, null, null);
    }

    public AiGenerateResponse extractContent(String fileId, String content, String filePath) throws IOException {
        return extractContent(fileId, content, filePath, null, null);
    }

    public AiGenerateResponse extractContent(String fileId, String content, String filePath,
                                              String fileContentBase64, String fileType) throws IOException {
        String url = config.getBaseUrl() + config.getGenerate().getExtractUrl();
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_id", fileId);
        body.put("content", content);
        if (filePath != null && !filePath.isEmpty()) {
            body.put("file_path", filePath);
        }
        if (fileContentBase64 != null && !fileContentBase64.isEmpty()) {
            body.put("file_content_base64", fileContentBase64);
        }
        if (fileType != null && !fileType.isEmpty()) {
            body.put("file_type", fileType);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiGenerateResponse.class);
    }

    public AiGenerateResponse generateReport(List<String> fileIds, String topic) throws IOException {
        return generateReport(fileIds, topic, null, null, null);
    }

    public AiGenerateResponse generateReport(List<String> fileIds, String topic, Map<String, String> contents) throws IOException {
        return generateReport(fileIds, topic, contents, null, null);
    }

    public AiGenerateResponse generateReport(List<String> fileIds, String topic, Map<String, String> contents,
                                             Map<String, String> filePaths) throws IOException {
        return generateReport(fileIds, topic, contents, filePaths, null);
    }

    public AiGenerateResponse generateReport(List<String> fileIds, String topic, Map<String, String> contents,
                                             Map<String, String> filePaths,
                                             Map<String, String> fileContentsBase64) throws IOException {
        String url = config.getBaseUrl() + config.getGenerate().getReportUrl();
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_ids", fileIds);
        body.put("topic", topic);
        if (contents != null && !contents.isEmpty()) {
            body.put("contents", contents);
        }
        if (filePaths != null && !filePaths.isEmpty()) {
            body.put("file_paths", filePaths);
        }
        if (fileContentsBase64 != null && !fileContentsBase64.isEmpty()) {
            body.put("file_contents_base64", fileContentsBase64);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiGenerateResponse.class);
    }

    public AiGenerateResponse generatePPT(List<String> fileIds, String topic) throws IOException {
        return generatePPT(fileIds, topic, null, null, null);
    }

    public AiGenerateResponse generatePPT(List<String> fileIds, String topic, Map<String, String> contents) throws IOException {
        return generatePPT(fileIds, topic, contents, null, null);
    }

    public AiGenerateResponse generatePPT(List<String> fileIds, String topic, Map<String, String> contents,
                                          Map<String, String> filePaths) throws IOException {
        return generatePPT(fileIds, topic, contents, filePaths, null);
    }

    public AiGenerateResponse generatePPT(List<String> fileIds, String topic, Map<String, String> contents,
                                          Map<String, String> filePaths,
                                          Map<String, String> fileContentsBase64) throws IOException {
        String url = config.getBaseUrl() + config.getGenerate().getPptUrl();
        
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_ids", fileIds);
        body.put("topic", topic);
        if (contents != null && !contents.isEmpty()) {
            body.put("contents", contents);
        }
        if (filePaths != null && !filePaths.isEmpty()) {
            body.put("file_paths", filePaths);
        }
        if (fileContentsBase64 != null && !fileContentsBase64.isEmpty()) {
            body.put("file_contents_base64", fileContentsBase64);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiGenerateResponse.class);
    }

    public AiGenerateResponse convertFormat(String fileId, String content, String targetFormat) throws IOException {
        return convertFormat(fileId, content, targetFormat, null, null, null);
    }

    public AiGenerateResponse convertFormat(String fileId, String content, String targetFormat, String filePath) throws IOException {
        return convertFormat(fileId, content, targetFormat, filePath, null, null);
    }

    public AiGenerateResponse convertFormat(String fileId, String content, String targetFormat, String filePath,
                                            String fileContentBase64, String fileType) throws IOException {
        String url = config.getBaseUrl() + config.getGenerate().getConvertUrl();

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_id", fileId);
        body.put("content", content);
        body.put("target_format", targetFormat);
        if (filePath != null && !filePath.isEmpty()) {
            body.put("file_path", filePath);
        }
        if (fileContentBase64 != null && !fileContentBase64.isEmpty()) {
            body.put("file_content_base64", fileContentBase64);
        }
        if (fileType != null && !fileType.isEmpty()) {
            body.put("file_type", fileType);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiGenerateResponse.class);
    }

    /**
     * 调用 AI 服务进行 LLM 增强的敏感信息检测
     * POST /api/v1/sensitive/detect
     */
    public AiSensitiveResponse detectSensitive(String fileId, String content, boolean useLlm) throws IOException {
        return detectSensitive(fileId, content, useLlm, null);
    }

    public AiSensitiveResponse detectSensitive(String fileId, String content, boolean useLlm, String filePath) throws IOException {
        String url = config.getBaseUrl() + config.getSensitive().getDetectUrl();

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("file_id", fileId);
        body.put("content", content.length() > 6000 ? content.substring(0, 6000) : content);
        body.put("use_llm", useLlm);
        if (filePath != null && !filePath.isEmpty()) {
            body.put("file_path", filePath);
        }

        String response = postJson(url, body);
        return objectMapper.readValue(response, AiSensitiveResponse.class);
    }

    private String postJson(String url, Map<String, Object> body) throws IOException {
        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.parse("application/json")
        );

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(requestBody);

        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            requestBuilder.header("X-API-Key", config.getApiKey());
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("AI service request failed: {} - {}", response.code(), errorBody);
                throw new IOException("AI service request failed: " + response.code());
            }

            return response.body().string();
        }
    }
}
