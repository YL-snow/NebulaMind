package com.nebulamind.api.client.wanwu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WanwuApiClient {

    private final OkHttpClient client;
    private final WanwuApiProperties properties;
    private final ObjectMapper objectMapper;
    private volatile String cachedToken;

    public WanwuApiClient(WanwuApiProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    public WanwuResponse<AuthResponse> login(String username, String password) {
        String url = properties.getBaseUrl() + "/api/v1/auth/login";
        
        try {
            String json = objectMapper.writeValueAsString(new LoginRequest(username, password));
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    WanwuResponse<AuthResponse> result = objectMapper.readValue(responseBody, 
                            objectMapper.getTypeFactory().constructParametricType(WanwuResponse.class, AuthResponse.class));
                    
                    if (result.isSuccess()) {
                        cachedToken = result.getData().getToken();
                        log.info("万悟平台登录成功，获取Token");
                    }
                    return result;
                }
                return buildErrorResponse(response.code(), "登录失败");
            }
        } catch (IOException e) {
            log.error("万悟平台登录异常", e);
            return buildErrorResponse(-1, e.getMessage());
        }
    }

    public WanwuResponse<ChatResponse> chat(String agentId, ChatRequest request) {
        String url = properties.getBaseUrl() + "/api/v1/agent/" + agentId + "/chat";
        
        try {
            String json = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            
            Request okRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            try (Response response = client.newCall(okRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody,
                            objectMapper.getTypeFactory().constructParametricType(WanwuResponse.class, ChatResponse.class));
                }
                return buildErrorResponse(response.code(), "对话请求失败");
            }
        } catch (IOException e) {
            log.error("万悟平台对话异常", e);
            return buildErrorResponse(-1, e.getMessage());
        }
    }

    public WanwuResponse<Object> createAgent(String name, String description, String model) {
        String url = properties.getBaseUrl() + "/api/v1/agent";
        
        try {
            AgentCreateRequest request = new AgentCreateRequest(name, description, model);
            String json = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            
            Request okRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            try (Response response = client.newCall(okRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody,
                            objectMapper.getTypeFactory().constructParametricType(WanwuResponse.class, Object.class));
                }
                return buildErrorResponse(response.code(), "创建智能体失败");
            }
        } catch (IOException e) {
            log.error("万悟平台创建智能体异常", e);
            return buildErrorResponse(-1, e.getMessage());
        }
    }

    public WanwuResponse<Object> createKnowledge(String name, String description) {
        String url = properties.getBaseUrl() + "/api/v1/knowledge";
        
        try {
            KnowledgeCreateRequest request = new KnowledgeCreateRequest(name, description);
            String json = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            
            Request okRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            try (Response response = client.newCall(okRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody,
                            objectMapper.getTypeFactory().constructParametricType(WanwuResponse.class, Object.class));
                }
                return buildErrorResponse(response.code(), "创建知识库失败");
            }
        } catch (IOException e) {
            log.error("万悟平台创建知识库异常", e);
            return buildErrorResponse(-1, e.getMessage());
        }
    }

    public WanwuResponse<Object> knowledgeQA(String knowledgeId, String question, int topK, double scoreThreshold) {
        String url = properties.getBaseUrl() + "/api/v1/knowledge/" + knowledgeId + "/qa";
        
        try {
            KnowledgeQARequest request = new KnowledgeQARequest(question, topK, scoreThreshold);
            String json = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            
            Request okRequest = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            try (Response response = client.newCall(okRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody,
                            objectMapper.getTypeFactory().constructParametricType(WanwuResponse.class, Object.class));
                }
                return buildErrorResponse(response.code(), "知识库问答失败");
            }
        } catch (IOException e) {
            log.error("万悟平台知识库问答异常", e);
            return buildErrorResponse(-1, e.getMessage());
        }
    }

    public boolean testConnection() {
        String url = properties.getBaseUrl() + "/api/v1/auth/login";
        
        try {
            String json = objectMapper.writeValueAsString(new LoginRequest("test", "test"));
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            log.error("万悟平台连接测试失败", e);
            return false;
        }
    }

    private String getToken() {
        if (cachedToken == null || cachedToken.isEmpty()) {
            throw new IllegalStateException("未登录万悟平台，请先调用login方法");
        }
        return cachedToken;
    }

    private <T> WanwuResponse<T> buildErrorResponse(int code, String message) {
        WanwuResponse<T> response = new WanwuResponse<>();
        response.setCode(code);
        response.setMsg(message);
        return response;
    }

    private static class LoginRequest {
        private String username;
        private String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }

    private static class AgentCreateRequest {
        private String name;
        private String description;
        private String model;

        public AgentCreateRequest(String name, String description, String model) {
            this.name = name;
            this.description = description;
            this.model = model;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getModel() { return model; }
    }

    private static class KnowledgeCreateRequest {
        private String name;
        private String description;

        public KnowledgeCreateRequest(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    private static class KnowledgeQARequest {
        private String question;
        private int topK;
        private double scoreThreshold;

        public KnowledgeQARequest(String question, int topK, double scoreThreshold) {
            this.question = question;
            this.topK = topK;
            this.scoreThreshold = scoreThreshold;
        }

        public String getQuestion() { return question; }
        public int getTopK() { return topK; }
        public double getScoreThreshold() { return scoreThreshold; }
    }

}
