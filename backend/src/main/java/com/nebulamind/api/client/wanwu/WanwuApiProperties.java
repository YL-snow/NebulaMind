package com.nebulamind.api.client.wanwu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "nebulamind.api.wanwu")
public class WanwuApiProperties {

    private String baseUrl = "http://localhost:8081";
    private String apiKey;
    private String apiSecret;
    private int timeout = 30000;
    private int tokenExpireMinutes = 60;

}
