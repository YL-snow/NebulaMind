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
public class AiGenerateResponse {

    @JsonAlias({"file_id"})
    private String fileId;

    private String content;

    @JsonAlias({"key_points"})
    private List<String> keyPoints;

    private String format;
}
