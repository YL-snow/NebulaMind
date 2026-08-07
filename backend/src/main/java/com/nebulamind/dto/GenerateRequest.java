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
public class GenerateRequest {

    private String fileId;
    private List<String> fileIds;
    private String topic;
    private Integer maxLength = 500;
    private String targetFormat;
    private String sourceFormat;
}
