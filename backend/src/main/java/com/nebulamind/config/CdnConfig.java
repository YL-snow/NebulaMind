package com.nebulamind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nebulamind.storage.cdn")
public class CdnConfig {

    private boolean enabled = false;
    private String baseUrl;
    private String accessKey;
    private String secretKey;
    private int cacheDuration = 3600;
    private String bucketName;
    private String region;
}
