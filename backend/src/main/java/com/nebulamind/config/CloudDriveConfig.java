package com.nebulamind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nebulamind.api.unicom-cloud-drive")
public class CloudDriveConfig {

    private String baseUrl;
    private String appId;
    private String appSecret;
    private String redirectUri;
    private int timeout = 30000;

    private OAuth2 oauth2 = new OAuth2();

    @Data
    public static class OAuth2 {
        private String authorizeUrl;
        private String tokenUrl;
        private String scope;
    }
}
