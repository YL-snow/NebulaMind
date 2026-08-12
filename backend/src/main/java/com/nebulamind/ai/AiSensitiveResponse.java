package com.nebulamind.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.util.List;

/**
 * AI 服务敏感检测响应
 * 对应 Python sensitive.py POST /api/v1/sensitive/detect
 */
@Data
public class AiSensitiveResponse {

    @JsonAlias({"sensitive_level"})
    private String sensitiveLevel;

    @JsonAlias({"level_score"})
    private int levelScore;

    private String summary;

    private List<SensitiveMatchItem> matches;

    @JsonAlias({"masked_content"})
    private String maskedContent;

    @JsonAlias({"detection_method"})
    private String detectionMethod;

    private String warning;

    @Data
    public static class SensitiveMatchItem {
        private String type;
        private String content;
        private int position;
        private double confidence;
    }
}
