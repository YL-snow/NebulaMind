package com.nebulamind.api.client.maas;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "nebulamind.api.maas")
public class MaasApiProperties {

    /** 文本生成模型的基础 URL */
    private String baseUrl = "https://maas.ai-yuanjing.com";
    private String apiKey;
    private int timeout = 60000;

    /** 文本生成模型 */
    private String llmModel = "yuanjing-70b-chat";

    /** 向量嵌入模型 */
    private String embeddingModel = "qwen3-vl-embedding-8b";

    /**
     * 多模态视觉模型的基础 URL。
     * 元景-图文问答-80B (YuanjingVL) 使用独立的端点：
     * https://maas-gz-api.ai-yuanjing.com/openapi/v1/yuanjingvl_plus
     */
    private String visionBaseUrl = "https://maas-gz-api.ai-yuanjing.com/openapi/v1/yuanjingvl_plus";

    /** 多模态视觉模型名称 */
    private String visionModel = "YuanjingVL";

    /** 视觉模型回退列表（主模型不可用时依次尝试） */
    private List<String> fallbackVisionModels = List.of("deepseek-v3");

}
