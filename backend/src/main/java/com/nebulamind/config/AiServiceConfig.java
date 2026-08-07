package com.nebulamind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nebulamind.api.ai-service")
public class AiServiceConfig {

    private String baseUrl = "http://localhost:8081";
    private int timeout = 60000;
    private String apiKey;

    private Classification classification = new Classification();
    private Search search = new Search();
    private QA qa = new QA();
    private Generate generate = new Generate();
    private Sensitive sensitive = new Sensitive();

    @Data
    public static class Sensitive {
        private String detectUrl = "/api/v1/sensitive/detect";
        private String maskUrl = "/api/v1/sensitive/mask";
    }

    @Data
    public static class Classification {
        private String classifyUrl = "/api/v1/classify";
        private String detectDuplicateUrl = "/api/v1/duplicates";
    }

    @Data
    public static class Search {
        private String semanticSearchUrl = "/api/v1/search";
    }

    @Data
    public static class QA {
        private String documentQaUrl = "/api/v1/qa";
        private String crossDocumentQaUrl = "/api/v1/qa/cross";
    }

    @Data
    public static class Generate {
        private String summaryUrl = "/api/v1/generate/summary";
        private String extractUrl = "/api/v1/generate/extract";
        private String reportUrl = "/api/v1/generate/report";
        private String pptUrl = "/api/v1/generate/ppt";
        private String convertUrl = "/api/v1/generate/convert";
    }
}
