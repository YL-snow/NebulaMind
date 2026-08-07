package com.nebulamind.api.client.maas;

import lombok.Data;

import java.util.List;

@Data
public class MaasEmbeddingResponse {

    private String object;
    private List<EmbeddingData> data;
    private String model;
    private Usage usage;

    @Data
    public static class EmbeddingData {
        private int index;
        private List<Double> embedding;
        private String object;
    }

    @Data
    public static class Usage {
        private int prompt_tokens;
        private int total_tokens;
    }

}
