package com.nebulamind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultItem {
    private String fileId;
    private String fileName;
    private String fileType;
    private Long size;
    private Double relevance;
    private String summary;
    private List<String> highlights;
    private List<Object> matchedChunks;
}
