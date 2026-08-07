package com.nebulamind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequest {

    @NotBlank(message = "File name is required")
    private String fileName;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @NotNull(message = "File size is required")
    private Long fileSize;

    @NotNull(message = "Chunk index is required")
    private Integer chunkIndex;

    @NotNull(message = "Total chunks is required")
    private Integer totalChunks;

    @NotBlank(message = "Upload ID is required")
    private String uploadId;

    @NotBlank(message = "File hash is required")
    private String fileHash;
}
