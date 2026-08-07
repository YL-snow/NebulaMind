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
public class AiQAResponse {

    private String question;
    private String answer;

    @JsonAlias({"source_file_id"})
    private String sourceFileId;

    @JsonAlias({"source_snippets"})
    private List<String> sourceSnippets;

    private Double confidence;
}
