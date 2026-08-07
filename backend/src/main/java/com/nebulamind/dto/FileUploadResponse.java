package com.nebulamind.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private UUID fileId;
    private String fileName;
    private String uploadId;
    private Integer chunkIndex;
    private Integer totalChunks;
    private Boolean completed;
    private String message;
}
