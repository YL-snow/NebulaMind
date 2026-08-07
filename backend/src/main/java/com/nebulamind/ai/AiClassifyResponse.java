package com.nebulamind.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClassifyResponse {

    @JsonAlias({"file_id"})
    private String fileId;

    private String category;
    private List<String> tags;

    @JsonAlias({"sensitive_level"})
    private String sensitiveLevel;

    private Double confidence;
}
