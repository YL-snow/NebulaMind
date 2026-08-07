package com.nebulamind.api.client.unicom;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class UnicomCloudDriveClient {

    private final OkHttpClient client;
    private final UnicomCloudDriveProperties properties;
    private volatile String accessToken;

    public UnicomCloudDriveClient(UnicomCloudDriveProperties properties) {
        this.properties = properties;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    public boolean testConnection() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isEmpty()) {
            log.warn("联通云盘API基础URL未配置");
            return false;
        }

        try {
            String url = properties.getBaseUrl() + "/health";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("联通云盘API连接测试成功");
                    return true;
                } else {
                    log.warn("联通云盘API连接测试失败，状态码: {}", response.code());
                    return true;
                }
            }
        } catch (IOException e) {
            log.error("联通云盘API连接测试异常", e);
            return false;
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

}
