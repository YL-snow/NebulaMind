package com.nebulamind.api.client.unicom;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "nebulamind.api.unicom-cloud-drive")
public class UnicomCloudDriveProperties {

    private String baseUrl;
    private String appId;
    private String appSecret;
    private int timeout = 30000;

}
