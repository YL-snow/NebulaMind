package com.nebulamind.api.client.wanwu;

import lombok.Data;

@Data
public class ChatResponse {

    private String answer;
    private String sessionId;
    private long timestamp;
    private String finishReason;

}
