package com.nebulamind.cloud;

import com.nebulamind.config.CloudDriveConfig;
import com.nebulamind.service.RedisCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class CloudDriveClient {

    private final CloudDriveConfig config;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_CACHE_PREFIX = "cloud_drive:token:";
    private static final long TOKEN_REFRESH_BUFFER = 300;

    private OkHttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    private OkHttpClient getHttpClient() {
        return httpClient;
    }

    public String buildAuthorizeUrl(String state) {
        return String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s",
                config.getOauth2().getAuthorizeUrl(),
                config.getAppId(),
                config.getRedirectUri(),
                config.getOauth2().getScope(),
                state);
    }

    public CloudDriveToken exchangeCodeForToken(String code) throws IOException {
        String url = config.getOauth2().getTokenUrl();

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", config.getAppId())
                .add("client_secret", config.getAppSecret())
                .add("redirect_uri", config.getRedirectUri())
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to exchange code: " + response);
            }

            CloudDriveToken token = objectMapper.readValue(response.body().string(), CloudDriveToken.class);
            token.setIssuedAt(LocalDateTime.now());
            return token;
        }
    }

    public CloudDriveToken refreshToken(String refreshToken) throws IOException {
        String url = config.getOauth2().getTokenUrl();

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", config.getAppId())
                .add("client_secret", config.getAppSecret())
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to refresh token: " + response);
            }

            CloudDriveToken token = objectMapper.readValue(response.body().string(), CloudDriveToken.class);
            token.setIssuedAt(LocalDateTime.now());
            return token;
        }
    }

    public void saveToken(UUID userId, CloudDriveToken token) {
        try {
            String json = objectMapper.writeValueAsString(token);
            redisCacheService.set(TOKEN_CACHE_PREFIX + userId, json, token.getExpiresIn() - TOKEN_REFRESH_BUFFER, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("Failed to save token", e);
        }
    }

    public CloudDriveToken getToken(UUID userId) throws IOException {
        Object result = redisCacheService.get(TOKEN_CACHE_PREFIX + userId);
        if (result == null) {
            return null;
        }

        String json = (String) result;
        CloudDriveToken token = objectMapper.readValue(json, CloudDriveToken.class);

        if (token.isExpired(TOKEN_REFRESH_BUFFER)) {
            token = refreshToken(token.getRefreshToken());
            saveToken(userId, token);
        }

        return token;
    }

    public void deleteToken(UUID userId) {
        redisCacheService.delete(TOKEN_CACHE_PREFIX + userId);
    }

    public List<CloudDriveFile> listFiles(UUID userId, String parentId, Integer page, Integer size) throws IOException {
        CloudDriveToken token = getToken(userId);
        if (token == null) {
            throw new IllegalStateException("No valid token found for user: " + userId);
        }

        String url = String.format("%s/api/v1/files?parent_id=%s&page=%d&size=%d",
                config.getBaseUrl(),
                parentId != null ? parentId : "",
                page != null ? page : 1,
                size != null ? size : 20);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token.getAccessToken())
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to list files: " + response);
            }

            return objectMapper.readValue(response.body().string(), new TypeReference<List<CloudDriveFile>>() {});
        }
    }

    public CloudDriveFile getFile(UUID userId, String fileId) throws IOException {
        CloudDriveToken token = getToken(userId);
        if (token == null) {
            throw new IllegalStateException("No valid token found for user: " + userId);
        }

        String url = String.format("%s/api/v1/files/%s", config.getBaseUrl(), fileId);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token.getAccessToken())
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get file: " + response);
            }

            return objectMapper.readValue(response.body().string(), CloudDriveFile.class);
        }
    }

    public InputStream downloadFile(UUID userId, String fileId) throws IOException {
        CloudDriveToken token = getToken(userId);
        if (token == null) {
            throw new IllegalStateException("No valid token found for user: " + userId);
        }

        String url = String.format("%s/api/v1/files/%s/download", config.getBaseUrl(), fileId);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token.getAccessToken())
                .build();

        Response response = getHttpClient().newCall(request).execute();
        if (!response.isSuccessful()) {
            response.close();
            throw new IOException("Failed to download file: " + response);
        }

        return new CloseableInputStream(response);
    }

    private static class CloseableInputStream extends java.io.FilterInputStream {
        private final Response response;

        public CloseableInputStream(Response response) {
            super(response.body().byteStream());
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }

    public CloudDriveFile uploadFile(UUID userId, String fileName, String parentId, byte[] content, String contentType) throws IOException {
        CloudDriveToken token = getToken(userId);
        if (token == null) {
            throw new IllegalStateException("No valid token found for user: " + userId);
        }

        String url = String.format("%s/api/v1/files/upload?parent_id=%s",
                config.getBaseUrl(),
                parentId != null ? parentId : "");

        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName,
                        RequestBody.create(content, MediaType.parse(contentType)))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token.getAccessToken())
                .post(body)
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to upload file: " + response);
            }

            return objectMapper.readValue(response.body().string(), CloudDriveFile.class);
        }
    }

    public void deleteFile(UUID userId, String fileId) throws IOException {
        CloudDriveToken token = getToken(userId);
        if (token == null) {
            throw new IllegalStateException("No valid token found for user: " + userId);
        }

        String url = String.format("%s/api/v1/files/%s", config.getBaseUrl(), fileId);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token.getAccessToken())
                .delete()
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to delete file: " + response);
            }
        }
    }

    public CloudDriveFile shareFile(UUID userId, String fileId, String shareType, List<String> users) throws IOException {
        CloudDriveToken token = getToken(userId);
        if (token == null) {
            throw new IllegalStateException("No valid token found for user: " + userId);
        }

        String url = String.format("%s/api/v1/files/%s/share", config.getBaseUrl(), fileId);

        Map<String, Object> body = Map.of(
                "type", shareType,
                "users", users
        );

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token.getAccessToken())
                .post(requestBody)
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to share file: " + response);
            }

            return objectMapper.readValue(response.body().string(), CloudDriveFile.class);
        }
    }
}
