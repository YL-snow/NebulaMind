package com.nebulamind.api.client.maas;

import lombok.Data;

import java.util.List;

@Data
public class MaasRerankResponse {

    private String model;
    private List<Result> results;

    @Data
    public static class Result {
        private int index;
        private double score;
    }

}
