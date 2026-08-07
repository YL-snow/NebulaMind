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
public class AiSearchResponse {

    private String query;
    private List<SearchResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {

        @JsonAlias({"file_id"})
        private String fileId;

        @JsonAlias({"file_name"})
        private String fileName;

        private String snippet;
        private Double score;
        private String category;
    }
}
