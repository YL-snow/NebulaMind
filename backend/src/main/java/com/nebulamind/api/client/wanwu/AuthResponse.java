package com.nebulamind.api.client.wanwu;

import lombok.Data;

@Data
public class AuthResponse {

    private String token;
    private String refreshToken;
    private long expireTime;

}
