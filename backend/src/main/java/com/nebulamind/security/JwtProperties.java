package com.nebulamind.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "nebulamind.security.jwt")
public class JwtProperties {

    private String secret = "nebulamind-jwt-secret-key-2026";
    private int expireHours = 24;
    private int refreshExpireHours = 72;
}
