package com.nebulamind.api.client.wanwu;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRequest {

    private String message;
    private String sessionId;
    private boolean stream;

}
