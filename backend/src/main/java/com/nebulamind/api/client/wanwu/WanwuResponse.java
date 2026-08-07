package com.nebulamind.api.client.wanwu;

import lombok.Data;

@Data
public class WanwuResponse<T> {

    private int code;
    private T data;
    private String msg;

    public boolean isSuccess() {
        return code == 0;
    }

}
