package com.nebulamind.api.client.maas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MaasApiClient {

    private final OkHttpClient client;
    private final MaasApiProperties properties;
    private final ObjectMapper objectMapper;

    public MaasApiClient(MaasApiProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    public MaasChatResponse chat(String model, List<Message> messages, double temperature, int maxTokens) {
        String url = properties.getBaseUrl() + "/chat/completions";

        try {
            ChatCompletionRequest request = new ChatCompletionRequest(model, messages, temperature, maxTokens);
            String json = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

            Request okRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(okRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody, MaasChatResponse.class);
                }
                log.error("MaaS文本生成失败，状态码: {}", response.code());
                return null;
            }
        } catch (IOException e) {
            log.error("MaaS文本生成异常", e);
            return null;
        }
    }

    /**
     * 多模态对话（支持图片 + 文字）。
     * 依次尝试主模型和回退模型列表，直到成功为止。
     *
     * @param textPrompt  文字提示
     * @param imageBase64 图片的 base64 编码数据（不含 data:image/... 前缀）
     * @param mimeType    图片 MIME 类型（如 image/jpeg、image/png）
     * @param temperature 温度参数
     * @param maxTokens   最大 token 数
     * @return VisionResult，包含成功/失败状态、模型名、响应内容或错误信息
     */
    public VisionResult chatVision(String textPrompt, String imageBase64, String mimeType,
                                    double temperature, int maxTokens) {
        // 尝试主模型 + 回退模型列表
        List<String> modelsToTry = new ArrayList<>();
        modelsToTry.add(properties.getVisionModel());
        if (properties.getFallbackVisionModels() != null) {
            modelsToTry.addAll(properties.getFallbackVisionModels());
        }

        List<String> errors = new ArrayList<>();
        for (String model : modelsToTry) {
            VisionResult result = tryChatVision(model, textPrompt, imageBase64, mimeType, temperature, maxTokens);
            if (result.isSuccess()) {
                return result;
            }
            errors.add(model + ": " + result.getErrorMessage());
            log.warn("视觉模型 {} 失败，尝试下一个", model);
        }

        // 全部失败
        String errorSummary = String.join(" | ", errors);
        log.error("所有视觉模型均失败: {}", errorSummary);
        return VisionResult.failure("所有视觉模型均失败： " + errorSummary);
    }

    /**
     * 使用指定模型尝试一次多模态调用。
     * 视觉模型使用独立的 visionBaseUrl（YuanjingVL 专用端点），
     * 主模型与回退模型统一使用 visionBaseUrl（元景视觉端点）。
     */
    private VisionResult tryChatVision(String model, String textPrompt, String imageBase64, String mimeType,
                                        double temperature, int maxTokens) {
        // 当前账号的视觉模型统一走元景视觉端点，回退模型也使用该端点
        String baseUrl = properties.getVisionBaseUrl();
        String url = baseUrl + "/chat/completions";
        log.info("尝试视觉模型: {} (端点: {})", model, url);

        try {
            // 构建多模态 content 数组
            List<ContentPart> contentParts = new ArrayList<>();
            contentParts.add(new ContentPart("text", textPrompt, null));
            contentParts.add(new ContentPart("image_url", null,
                    new ImageUrl("data:" + mimeType + ";base64," + imageBase64)));

            List<Message> messages = new ArrayList<>();
            messages.add(new Message("user", contentParts));

            ChatCompletionRequest request = new ChatCompletionRequest(model, messages, temperature, maxTokens);
            String json = objectMapper.writeValueAsString(request);

            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request okRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(okRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "null";
                if (response.isSuccessful()) {
                    MaasChatResponse chatResponse = objectMapper.readValue(responseBody, MaasChatResponse.class);
                    if (chatResponse != null && chatResponse.getChoices() != null
                            && !chatResponse.getChoices().isEmpty()
                            && chatResponse.getChoices().get(0).getMessage() != null
                            && chatResponse.getChoices().get(0).getMessage().getContent() != null) {
                        log.info("视觉模型 {} 成功", model);
                        return VisionResult.success(model, chatResponse);
                    }
                    log.warn("视觉模型 {} 返回空内容: {}", model, responseBody);
                    return VisionResult.failure(model + " 返回空内容");
                }
                String errorMsg = String.format("模型 %s 返回 %d: %s", model, response.code(),
                        responseBody.length() > 200 ? responseBody.substring(0, 200) : responseBody);
                log.warn(errorMsg);
                return VisionResult.failure(errorMsg);
            }
        } catch (IOException e) {
            String errorMsg = model + " 请求异常: " + e.getMessage();
            log.warn(errorMsg);
            return VisionResult.failure(errorMsg);
        }
    }

    public MaasEmbeddingResponse embeddings(String model, List<String> input) {
        String url = properties.getBaseUrl() + "/embeddings";

        try {
            EmbeddingRequest request = new EmbeddingRequest(model, input);
            String json = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

            Request okRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(okRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody, MaasEmbeddingResponse.class);
                }
                log.error("MaaS文本向量化失败，状态码: {}", response.code());
                return null;
            }
        } catch (IOException e) {
            log.error("MaaS文本向量化异常", e);
            return null;
        }
    }

    /**
     * 基于LLM的文档重排序。
     * 注意：MaaS平台未提供独立Rerank端点，因此使用Chat模型对每个文档进行相关性评分。
     * 使用配置的 LLM 模型，通过Prompt让模型评估查询与文档的相关性。
     */
    public MaasRerankResponse rerank(String model, String query, List<String> documents) {
        MaasRerankResponse response = new MaasRerankResponse();
        response.setModel(model != null ? model : properties.getLlmModel());
        List<MaasRerankResponse.Result> results = new ArrayList<>();

        for (int i = 0; i < documents.size(); i++) {
            String doc = documents.get(i);
            try {
                String prompt = "你是一个文档相关性评估专家。请评估以下查询与文档的相关性，仅返回一个0到1之间的数字（保留两位小数），不要有任何其他文字。\n\n"
                        + "查询: " + query + "\n"
                        + "文档: " + (doc.length() > 1000 ? doc.substring(0, 1000) : doc) + "\n"
                        + "相关性分数:";

                List<Message> messages = new ArrayList<>();
                messages.add(new Message("user", prompt));

                MaasChatResponse chatResponse = chat(properties.getLlmModel(), messages, 0.0, 10);
                double score = 0.0;
                if (chatResponse != null && chatResponse.getChoices() != null
                        && !chatResponse.getChoices().isEmpty()
                        && chatResponse.getChoices().get(0).getMessage() != null) {
                    String content = chatResponse.getChoices().get(0).getMessage().getContent().trim();
                    score = Double.parseDouble(content.replaceAll("[^0-9.]", ""));
                    score = Math.max(0.0, Math.min(1.0, score));
                }

                MaasRerankResponse.Result result = new MaasRerankResponse.Result();
                result.setIndex(i);
                result.setScore(score);
                results.add(result);
            } catch (Exception e) {
                log.warn("重排序文档 {} 失败: {}", i, e.getMessage());
                MaasRerankResponse.Result result = new MaasRerankResponse.Result();
                result.setIndex(i);
                result.setScore(0.0);
                results.add(result);
            }
        }

        // 按分数降序排序
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        response.setResults(results);
        return response;
    }

    public boolean testConnection() {
        String url = properties.getBaseUrl() + "/chat/completions";

        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("user", "test"));
            ChatCompletionRequest request = new ChatCompletionRequest(properties.getLlmModel(), messages, 0.7, 10);

            String json = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

            Request okRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(okRequest).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            log.error("MaaS平台连接测试失败", e);
            return false;
        }
    }

    // ========== 内部类 ==========

    /**
     * 聊天消息。content 可以是普通字符串（纯文本），也可以是 List&lt;ContentPart&gt;（多模态）。
     */
    public static class Message {
        private String role;
        private Object content; // String 或 List<ContentPart>

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public Message(String role, List<ContentPart> contentParts) {
            this.role = role;
            this.content = contentParts;
        }

        public String getRole() { return role; }
        public Object getContent() { return content; }
    }

    /**
     * 多模态内容片段，遵循 OpenAI Vision API 格式：
     * - text: {"type": "text", "text": "..."}
     * - image_url: {"type": "image_url", "image_url": {"url": "data:..."}}
     */
    public static class ContentPart {
        private String type;
        private String text;
        private ImageUrl image_url;

        public ContentPart(String type, String text, ImageUrl imageUrl) {
            this.type = type;
            this.text = text;
            this.image_url = imageUrl;
        }

        public String getType() { return type; }
        public String getText() { return text; }
        public ImageUrl getImage_url() { return image_url; }
    }

    /**
     * 图片 URL（data URL 格式）
     */
    public static class ImageUrl {
        private String url;

        public ImageUrl(String url) { this.url = url; }
        public String getUrl() { return url; }
    }

    /**
     * 多模态调用结果
     */
    public static class VisionResult {
        private final boolean success;
        private final String model;
        private final MaasChatResponse response;
        private final String errorMessage;

        private VisionResult(boolean success, String model, MaasChatResponse response, String errorMessage) {
            this.success = success;
            this.model = model;
            this.response = response;
            this.errorMessage = errorMessage;
        }

        public static VisionResult success(String model, MaasChatResponse response) {
            return new VisionResult(true, model, response, null);
        }

        public static VisionResult failure(String errorMessage) {
            return new VisionResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getModel() { return model; }
        public MaasChatResponse getResponse() { return response; }
        public String getErrorMessage() { return errorMessage; }
    }

    private static class ChatCompletionRequest {
        private String model;
        private List<Message> messages;
        private double temperature;
        private int max_tokens;

        public ChatCompletionRequest(String model, List<Message> messages, double temperature, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
            this.max_tokens = maxTokens;
        }

        public String getModel() { return model; }
        public List<Message> getMessages() { return messages; }
        public double getTemperature() { return temperature; }
        public int getMax_tokens() { return max_tokens; }
    }

    private static class EmbeddingRequest {
        private String model;
        private List<String> input;

        public EmbeddingRequest(String model, List<String> input) {
            this.model = model;
            this.input = input;
        }

        public String getModel() { return model; }
        public List<String> getInput() { return input; }
    }

}
